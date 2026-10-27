package com.horizonssmp.gov.server;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import com.horizonssmp.gov.common.GovCapability;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.GovCapabilities;
import com.horizonssmp.gov.common.enums.DutyState;
import com.horizonssmp.gov.common.enums.FineStatus;
import com.horizonssmp.gov.common.records.FineRecord;
import com.horizonssmp.gov.common.records.PlayerRecord;

final class GovStartupCheckFinesPart2 {

    private GovStartupCheckFinesPart2() {
    }

    static void run(final GovStartupCheckFinesContext ctx) {
        final List<String> failures = ctx.failures;
        final long now = ctx.now;
        final FineService.ReceptionPoint receptionPoint = ctx.receptionPoint;
        final UUID alice = ctx.alice;
        final UUID bob = ctx.bob;
        final FineService.Actor officer = ctx.officer;

            final GovSavedData pay = new GovSavedData();
            pay.seedIfNeeded(now, false);
            pay.putPlayer(PlayerRecord.blank(alice, "StartupCheck Fine Alice", now));
            pay.putPlayer(PlayerRecord.blank(bob, "StartupCheck Fine Bob", now));
            pay.putPlayer(PlayerRecord.blank(officer.uuid(), officer.name(), now)
                    .withDuty(DutyState.ON_DUTY));
            final FineService.Actor aliceActor = new FineService.Actor(
                    alice, "StartupCheck Fine Alice", EnumSet.noneOf(GovCapability.class));
            final FineService.Actor bobActor = new FineService.Actor(
                    bob, "StartupCheck Fine Bob", EnumSet.noneOf(GovCapability.class));

            final FineService.Outcome payTarget = FineService.issue(
                    pay, officer, alice, aliceActor.name(), "PETTY", "9.11", "0", "", "",
                    "StartupCheck unpaid fine", now);
            ctx.checks++;
            if (!payTarget.ok()) {
                failures.add("could not issue the payment test fine: " + payTarget.message());
            }

            final GovStartupCheckFinesGateway wallet = new GovStartupCheckFinesGateway();
            wallet.gold(alice, 10L);
            wallet.present(alice, true);
            wallet.atReception(alice, true);
            ctx.checks++;
            if (FineService.paySelf(pay, aliceActor, payTarget.fineId(), wallet,
                    Optional.empty(), now).code() != ErrorCode.INVALID_STATE) {
                failures.add("a self-payment with no configured reception was accepted");
            }
            wallet.atReception(alice, false);
            ctx.checks++;
            if (FineService.paySelf(pay, aliceActor, payTarget.fineId(), wallet,
                    Optional.of(receptionPoint), now).code() != ErrorCode.INVALID_STATE) {
                failures.add("a self-payment outside the reception radius was accepted");
            }
            wallet.atReception(alice, true);
            final FineService.Outcome paid = FineService.paySelf(
                    pay, aliceActor, payTarget.fineId(), wallet, Optional.of(receptionPoint), now);
            ctx.checks++;
            if (!paid.ok()) {
                failures.add("a self-payment at reception failed: " + paid.message());
            }
            final FineRecord paidFine = pay.fine(payTarget.fineId());
            ctx.checks++;
            if (paidFine == null
                    || paidFine.status() != FineStatus.PAID
                    || paidFine.paidAt() != now
                    || !paidFine.paidAtReceptionRef().startsWith(FineService.RECEIPT_PREFIX)
                    || !"StartupCheck Fine Alice".equals(paidFine.collectedBy())
                    || !paidFine.selfPaid()
                    || wallet.goldOf(alice) != 2L) {
                failures.add("the paid fine did not record its state, receipt, collector or gold removal");
            }
            ctx.checks++;
            if (FineService.paySelf(pay, aliceActor, payTarget.fineId(), wallet,
                    Optional.of(receptionPoint), now).code() != ErrorCode.INVALID_STATE) {
                failures.add("a double payment was accepted");
            }

            final FineService.Outcome bobFine = FineService.issue(
                    pay, officer, bob, bobActor.name(), "PETTY", "9.11", "0", "", "",
                    "StartupCheck bob fine", now);
            ctx.checks++;
            if (FineService.paySelf(pay, aliceActor, bobFine.fineId(), wallet,
                    Optional.of(receptionPoint), now).code() != ErrorCode.FORBIDDEN) {
                failures.add("paying someone else's fine was accepted");
            }
            wallet.gold(bob, 3L);
            wallet.present(bob, true);
            wallet.atReception(bob, true);
            ctx.checks++;
            if (FineService.paySelf(pay, bobActor, bobFine.fineId(), wallet,
                    Optional.of(receptionPoint), now).code() != ErrorCode.INVALID_STATE) {
                failures.add("a payment with insufficient gold was accepted");
            }
            ctx.checks++;
            if (pay.fine(bobFine.fineId()).status() != FineStatus.UNPAID
                    || wallet.goldOf(bob) != 3L) {
                failures.add("an insufficient payment changed the fine or the gold");
            }
            ctx.checks++;
            if (FineService.paySelf(pay, aliceActor, "f-00000000", wallet,
                    Optional.of(receptionPoint), now).code() != ErrorCode.BAD_FINE) {
                failures.add("paying an unknown fine did not report BAD_FINE");
            }

            // Receipt references are unique and well formed across every paid fine.
            wallet.gold(alice, 20L);
            final FineService.Outcome secondPay = FineService.issue(
                    pay, officer, alice, aliceActor.name(), "PETTY", "9.11", "0", "", "",
                    "StartupCheck second receipt", now + 1);
            ctx.checks++;
            if (!FineService.paySelf(pay, aliceActor, secondPay.fineId(), wallet,
                    Optional.of(receptionPoint), now + 1).ok()) {
                failures.add("the second receipt payment failed");
            }
            int paidCount = 0;
            final Set<String> receipts = new HashSet<>();
            for (final FineRecord fine : pay.fines().values()) {
                if (fine.status() == FineStatus.PAID) {
                    paidCount++;
                    receipts.add(fine.paidAtReceptionRef());
                }
            }
            ctx.checks++;
            if (paidCount < 2 || receipts.size() != paidCount) {
                failures.add("two paid fines share a receipt reference");
            }
            ctx.checks++;
            for (final String receipt : receipts) {
                if (!receipt.matches("R-[0-9a-f]{8}")) {
                    failures.add("a receipt reference is not R- plus eight hex characters: " + receipt);
                    break;
                }
            }

            // Waivers: reason required, record kept, paid fines not waivable.
            ctx.checks++;
            if (FineService.waive(pay, officer, payTarget.fineId(),
                    "StartupCheck late waiver", now).code() != ErrorCode.INVALID_STATE) {
                failures.add("a paid fine was waived");
            }
            final FineService.Outcome waiveTarget = FineService.issue(
                    pay, officer, bob, bobActor.name(), "MISDEMEANOR", "9.11", "0", "", "",
                    "StartupCheck waivable fine", now);
            final FineService.Outcome waived = FineService.waive(
                    pay, officer, waiveTarget.fineId(), "StartupCheck waived for the test", now);
            ctx.checks++;
            if (!waived.ok()) {
                failures.add("a waiver with a reason failed: " + waived.message());
            }
            final FineRecord waivedFine = pay.fine(waiveTarget.fineId());
            ctx.checks++;
            if (waivedFine == null
                    || waivedFine.status() != FineStatus.WAIVED
                    || !officer.uuid().equals(waivedFine.waivedBy())
                    || !waivedFine.waivedReason().contains("StartupCheck")
                    || waivedFine.paidAt() != 0L
                    || !waivedFine.paidAtReceptionRef().isEmpty()) {
                failures.add("a waiver did not keep the record, official and reason");
            }
            final FineService.Outcome waiveReasonTarget = FineService.issue(
                    pay, officer, bob, bobActor.name(), "MISDEMEANOR", "9.11", "0", "", "",
                    "StartupCheck waiver reason test", now);
            ctx.checks++;
            if (FineService.waive(pay, officer, waiveReasonTarget.fineId(), "   ", now)
                    .code() != ErrorCode.BAD_PAYLOAD) {
                failures.add("a waiver with no reason was accepted");
            }
            ctx.checks++;
            if (FineService.waive(pay, bobActor, waiveReasonTarget.fineId(), "nope", now)
                    .code() != ErrorCode.FORBIDDEN) {
                failures.add("a waiver without fine.waive was accepted");
            }
            ctx.checks++;
            if (FineService.waive(pay, officer, "f-00000000", "nope", now)
                    .code() != ErrorCode.BAD_FINE) {
                failures.add("waiving an unknown fine did not report BAD_FINE");
            }
            ctx.checks++;
            if (FineService.waive(pay, officer, waiveTarget.fineId(), "again", now)
                    .code() != ErrorCode.INVALID_STATE) {
                failures.add("waiving an already waived fine was accepted");
            }

            // Collection: capability, reception for both people, collector name, gold from payer.
            final FineService.Outcome collectTarget = FineService.issue(
                    pay, officer, alice, aliceActor.name(), "PETTY", "9.11", "0", "", "",
                    "StartupCheck collectable fine", now);
            wallet.gold(alice, 20L);
            wallet.present(alice, true);
            wallet.atReception(alice, true);
            wallet.atReception(officer.uuid(), true);
            final FineService.Outcome collected = FineService.collect(
                    pay, officer, collectTarget.fineId(), alice, aliceActor.name(), wallet,
                    Optional.of(receptionPoint), now);
            ctx.checks++;
            if (!collected.ok()) {
                failures.add("a collection at reception failed: " + collected.message());
            }
            final FineRecord collectedFine = pay.fine(collectTarget.fineId());
            ctx.checks++;
            if (collectedFine == null
                    || collectedFine.status() != FineStatus.PAID
                    || collectedFine.selfPaid()
                    || !officer.name().equals(collectedFine.collectedBy())
                    || wallet.goldOf(alice) != 12L) {
                failures.add("a collected payment did not name the collector or take the payer's gold");
            }
            ctx.checks++;
            if (FineService.collect(pay, officer, collectTarget.fineId(), alice,
                    aliceActor.name(), wallet, Optional.of(receptionPoint), now)
                    .code() != ErrorCode.INVALID_STATE) {
                failures.add("collecting an already paid fine was accepted");
            }

            final FineService.Outcome collectOutside = FineService.issue(
                    pay, officer, alice, aliceActor.name(), "PETTY", "9.11", "0", "", "",
                    "StartupCheck collector outside", now);
            wallet.gold(alice, 20L);
            wallet.atReception(officer.uuid(), false);
            ctx.checks++;
            if (FineService.collect(pay, officer, collectOutside.fineId(), alice,
                    aliceActor.name(), wallet, Optional.of(receptionPoint), now)
                    .code() != ErrorCode.INVALID_STATE) {
                failures.add("a collection by an official outside the reception was accepted");
            }
            wallet.atReception(officer.uuid(), true);
            wallet.atReception(alice, false);
            ctx.checks++;
            if (FineService.collect(pay, officer, collectOutside.fineId(), alice,
                    aliceActor.name(), wallet, Optional.of(receptionPoint), now)
                    .code() != ErrorCode.INVALID_STATE) {
                failures.add("a collection with the payer outside the reception was accepted");
            }
            wallet.atReception(alice, true);

            final FineService.Outcome officerFine = FineService.issue(
                    pay, officer, officer.uuid(), officer.name(), "PETTY", "9.11", "0", "", "",
                    "StartupCheck officer own fine", now);
            ctx.checks++;
            if (FineService.collect(pay, officer, officerFine.fineId(), officer.uuid(),
                    officer.name(), wallet, Optional.of(receptionPoint), now)
                    .code() != ErrorCode.FORBIDDEN) {
                failures.add("an official collected their own fine instead of self-paying");
            }

            pay.putPlayer(pay.player(officer.uuid()).withDuty(DutyState.OFF_DUTY));
            final FineService.Outcome dutyTarget = FineService.issue(
                    pay, officer, alice, aliceActor.name(), "PETTY", "9.11", "0", "", "",
                    "StartupCheck off duty collect", now);
            wallet.gold(alice, 20L);
            wallet.atReception(alice, true);
            wallet.atReception(officer.uuid(), true);
            ctx.checks++;
            if (FineService.collect(pay, officer, dutyTarget.fineId(), alice,
                    aliceActor.name(), wallet, Optional.of(receptionPoint), now)
                    .code() != ErrorCode.FORBIDDEN) {
                failures.add("an off-duty official accepted a fine payment");
            }
            pay.putPlayer(pay.player(officer.uuid()).withDuty(DutyState.ON_DUTY));
            ctx.checks++;
            if (FineService.collect(pay, bobActor, dutyTarget.fineId(), alice,
                    aliceActor.name(), wallet, Optional.of(receptionPoint), now)
                    .code() != ErrorCode.FORBIDDEN) {
                failures.add("a collection without fine.waive was accepted");
            }

            // ------------------------------------------------------------- reads

        ctx.pay = pay;
        ctx.collectedFine = collectedFine;
    }
}
