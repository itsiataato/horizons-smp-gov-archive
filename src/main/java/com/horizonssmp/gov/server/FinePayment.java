package com.horizonssmp.gov.server;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.enums.DutyState;
import com.horizonssmp.gov.common.enums.FineStatus;
import com.horizonssmp.gov.common.enums.HistoryKind;
import com.horizonssmp.gov.common.records.FineRecord;
import com.horizonssmp.gov.common.records.GoldAmount;
import com.horizonssmp.gov.common.records.PlayerRecord;
import com.horizonssmp.gov.config.GovConfig;

public final class FinePayment {

    private FinePayment() {
    }

    public static FineService.Outcome paySelf(
            final GovSavedData data, final FineService.Actor actor, final String rawFineId,
            final FineService.Gateway gateway, final Optional<FineService.ReceptionPoint> reception, final long now) {
        if (data == null || actor == null || actor.uuid() == null || gateway == null) {
            return FineService.Outcome.refused(ErrorCode.INVALID_STATE, "No payment to make.");
        }
        final FineRecord fine = FineLookups.find(data, rawFineId);
        if (fine == null) {
            return FineService.Outcome.refused(ErrorCode.BAD_FINE, FineLookups.noSuchFineMessage(rawFineId));
        }
        if (!fine.targetUuid().equals(actor.uuid())) {
            return FineService.Outcome.refused(
                    ErrorCode.FORBIDDEN, "You can only pay your own fines.");
        }
        return settle(data, fine, actor, actor.uuid(), actor.name(), gateway, reception, now, true);
    }

    public static FineService.Outcome collect(
            final GovSavedData data, final FineService.Actor actor, final String rawFineId,
            final UUID payerUuid, final String payerName, final FineService.Gateway gateway,
            final Optional<FineService.ReceptionPoint> reception, final long now) {
        if (data == null || actor == null || actor.uuid() == null
                || payerUuid == null || gateway == null) {
            return FineService.Outcome.refused(ErrorCode.INVALID_STATE, "No payment to collect.");
        }
        if (!actor.capabilities().contains(com.horizonssmp.gov.common.GovCapability.FINE_WAIVE)) {
            return FineService.Outcome.refused(
                    ErrorCode.FORBIDDEN,
                    "You need " + com.horizonssmp.gov.common.GovCapability.FINE_WAIVE.id() + " to accept a fine payment.");
        }
        if (actor.uuid().equals(payerUuid)) {
            return FineService.Outcome.refused(
                    ErrorCode.FORBIDDEN, "Use the self-payment path for your own fine.");
        }
        final PlayerRecord collector = data.player(actor.uuid());
        if (collector != null && collector.duty() == DutyState.OFF_DUTY) {
            return FineService.Outcome.refused(
                    ErrorCode.FORBIDDEN, "You are off duty and cannot accept a fine payment.");
        }

        final FineRecord fine = FineLookups.find(data, rawFineId);
        if (fine == null) {
            return FineService.Outcome.refused(ErrorCode.BAD_FINE, FineLookups.noSuchFineMessage(rawFineId));
        }
        if (!fine.targetUuid().equals(payerUuid)) {
            return FineService.Outcome.refused(
                    ErrorCode.BAD_TARGET, "That is not the person this fine names.");
        }
        final String payer = Sanitizer.name(payerName).isBlank()
                ? fine.targetUuid().toString() : Sanitizer.name(payerName);
        return settle(data, fine, actor, payerUuid, payer, gateway, reception, now, false);
    }

    private static FineService.Outcome settle(
            final GovSavedData data, final FineRecord fine, final FineService.Actor actor,
            final UUID payerUuid, final String payerName, final FineService.Gateway gateway,
            final Optional<FineService.ReceptionPoint> reception, final long now, final boolean selfPaid) {
        if (fine.status() == FineStatus.PAID) {
            return FineService.Outcome.refused(
                    ErrorCode.INVALID_STATE,
                    "Fine " + fine.id() + " is already paid (receipt "
                            + fine.paidAtReceptionRef() + ").");
        }
        if (fine.status() == FineStatus.WAIVED) {
            return FineService.Outcome.refused(
                    ErrorCode.INVALID_STATE,
                    "Fine " + fine.id() + " was waived and expects no gold.");
        }
        if (fine.status() != FineStatus.UNPAID) {
            return FineService.Outcome.refused(
                    ErrorCode.INVALID_STATE, "Fine " + fine.id() + " is not payable.");
        }
        if (!GoldAmount.GOLD_INGOT.equals(fine.amount().currency())
                || !GoldAmount.GOLD_INGOT.equals(fine.restitution().currency())) {
            return FineService.Outcome.refused(
                    ErrorCode.INVALID_STATE,
                    "Fine " + fine.id() + " is not denominated in gold ingots.");
        }
        if (reception == null || reception.isEmpty()) {
            return FineService.Outcome.refused(ErrorCode.INVALID_STATE, FineReception.receptionMissingMessage());
        }
        if (!gateway.present(payerUuid)) {
            return FineService.Outcome.refused(
                    ErrorCode.INVALID_STATE, payerName + " is not online to pay in person.");
        }
        if (!gateway.atReception(actor.uuid())) {
            return FineService.Outcome.refused(ErrorCode.INVALID_STATE, FineReception.notAtReceptionMessage());
        }
        if (!selfPaid && !gateway.atReception(payerUuid)) {
            return FineService.Outcome.refused(
                    ErrorCode.INVALID_STATE,
                    payerName + " must be at the " + GovConfig.paymentPoint()
                            + " with the gold ingots.");
        }

        final long due = fine.totalDue().count();
        final long carried = gateway.goldIngots(payerUuid);
        if (carried < due) {
            return FineService.Outcome.refused(
                    ErrorCode.INVALID_STATE,
                    payerName + " needs " + due + " gold ingots but carries " + carried + ".");
        }
        if (!gateway.removeGoldIngots(payerUuid, due)) {
            return FineService.Outcome.refused(
                    ErrorCode.INVALID_STATE,
                    "The gold could not be taken; the fine was not marked paid.");
        }

        final String receipt = FineIds.newReceiptRef(data);
        final String collector = selfPaid ? payerName : Sanitizer.name(actor.name());
        FineRecord settled = fine.paid(now, receipt, collector, selfPaid);

        final boolean compensationPending;
        if (settled.restitution().count() > 0L && settled.victimUuid() != null) {
            if (gateway.present(settled.victimUuid())
                    && gateway.addGoldIngots(settled.victimUuid(), settled.restitution().count())) {
                settled = settled.withCompensationPaidAt(now);
                compensationPending = false;
                HistoryService.append(data, settled.victimUuid(), HistoryKind.COMPENSATION_PAID,
                        "Restitution of " + settled.restitution().count()
                                + " gold ingots from fine " + settled.id() + " was paid to you.",
                        actor.uuid(), actor.name(), now, settled.id());
            } else {
                compensationPending = true;
                HistoryService.append(data, settled.victimUuid(), HistoryKind.COMPENSATION_PAID,
                        "Restitution of " + settled.restitution().count()
                                + " gold ingots from fine " + settled.id()
                                + " is held until your next settlement.",
                        actor.uuid(), actor.name(), now, settled.id());
            }
        } else {
            compensationPending = false;
        }
        data.putFine(settled);
        HistoryService.append(data, payerUuid, HistoryKind.FINE_PAID,
                (selfPaid ? "You paid fine " : "Fine ") + settled.id() + " ("
                        + due + " gold ingots)"
                        + (selfPaid ? "." : " was collected from you."),
                actor.uuid(), actor.name(), now, settled.id());

        deliverPendingCompensation(data, payerUuid, gateway, now);

        final StringBuilder breakdown = new StringBuilder(due + " gold ingots");
        if (fine.restitution().count() > 0L) {
            breakdown.append(" including ").append(fine.restitution().count())
                    .append(" restitution to ").append(fine.victim());
            if (compensationPending) {
                breakdown.append(" (held until the victim's next settlement)");
            }
        }
        final String message = (selfPaid
                ? "paid fine " + fine.id() + " (" + breakdown + ")"
                : "collected fine " + fine.id() + " from " + payerName + " (" + breakdown + ")")
                + "; receipt " + receipt + " at " + GovConfig.paymentPoint() + ".";
        return FineService.Outcome.ok(message, fine.id());
    }

    private static long deliverPendingCompensation(
            final GovSavedData data, final UUID playerUuid,
            final FineService.Gateway gateway, final long now) {
        if (data == null || playerUuid == null || gateway == null || !gateway.present(playerUuid)) {
            return 0L;
        }
        long delivered = 0L;
        for (final FineRecord fine : List.copyOf(data.fines().values())) {
            if (!fine.compensationPending() || !playerUuid.equals(fine.victimUuid())) {
                continue;
            }
            final long amount = fine.restitution().count();
            if (amount <= 0L || !gateway.addGoldIngots(playerUuid, amount)) {
                continue;
            }
            data.putFine(fine.withCompensationPaidAt(now));
            delivered += amount;
            HistoryService.append(data, playerUuid, HistoryKind.COMPENSATION_PAID,
                    "Held restitution of " + amount + " gold ingots from fine " + fine.id()
                            + " was paid to you.",
                    null, "", now, fine.id());
        }
        return delivered;
    }
}
