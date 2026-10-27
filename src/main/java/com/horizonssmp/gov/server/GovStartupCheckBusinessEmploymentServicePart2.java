package com.horizonssmp.gov.server;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.horizonssmp.gov.common.BusinessKeys;
import com.horizonssmp.gov.config.GovConfig;
import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.enums.BusinessPermission;
import com.horizonssmp.gov.common.enums.EmploymentStatus;
import com.horizonssmp.gov.common.records.BusinessPositionRecord;
import com.horizonssmp.gov.common.records.BusinessRecord;
import com.horizonssmp.gov.common.records.EmploymentContractRecord;
import static com.horizonssmp.gov.server.GovStartupCheckBusinessEmploymentServiceSupport.*;

final class GovStartupCheckBusinessEmploymentServicePart2 {

    private GovStartupCheckBusinessEmploymentServicePart2() {
    }

    static void run(final GovStartupCheckBusinessEmploymentServiceContext ctx) {
        final List<String> failures = ctx.failures;
        final long now = ctx.now;
        final long day = ctx.day;
        final GovSavedData data = ctx.data;
        final UUID owner = ctx.owner;
        final UUID manager = ctx.manager;
        final UUID worker = ctx.worker;
        final UUID other = ctx.other;
        final UUID other2 = ctx.other2;
        final String otherName = ctx.otherName;
        final String other2Name = ctx.other2Name;
        final String workerName = ctx.workerName;
        final BusinessService.Actor registrarBusiness = ctx.registrarBusiness;
        final BusinessEmploymentService.Actor ownerActor = ctx.ownerActor;
        final BusinessEmploymentService.Actor managerActor = ctx.managerActor;
        final BusinessEmploymentService.Actor workerActor = ctx.workerActor;
        final BusinessEmploymentService.Actor otherActor = ctx.otherActor;
        final String businessId = ctx.businessId;
        final BusinessPositionRecord managerPosition = ctx.managerPosition;
        final BusinessPositionRecord workerPosition = ctx.workerPosition;
        final EmploymentContractRecord managerPending = ctx.managerPending;

            // One-time contracts are exempt from the standing slot; a second standing role is not.
            final BusinessService.Outcome otherShopOutcome = BusinessService.register(
                    data, registrarBusiness, "StartupCheck M12 Other Shop", "shop",
                    other, otherName, now);
            final BusinessRecord otherShop = data.business(otherShopOutcome.businessId());
            ctx.checks++;
            if (!otherShopOutcome.ok() || otherShop == null
                    || BusinessEmploymentService.sendOffer(data, ownerActor, businessId,
                            other, otherName, workerPosition == null ? "" : workerPosition.id(),
                            "5", "false", now).code() != ErrorCode.INVALID_STATE) {
                failures.add("a player with a standing role elsewhere could be offered another");
            }
            ctx.checks++;
            final BusinessEmploymentService.Outcome oneTimeOffer =
                    BusinessEmploymentService.sendOffer(data, ownerActor, businessId,
                            worker, workerName, workerPosition == null ? "" : workerPosition.id(),
                            "5", "true", now);
            final EmploymentContractRecord oneTimePending =
                    findOffer(data.business(businessId), worker);
            final BusinessEmploymentService.Outcome oneTimeAccepted = oneTimePending == null
                    ? BusinessEmploymentService.Outcome.refused(
                            ErrorCode.INVALID_STATE, "no one-time offer")
                    : BusinessEmploymentService.respondOffer(
                            data, workerActor, businessId, oneTimePending.id(), true, now);
            int activeForWorker = 0;
            for (final EmploymentContractRecord contract : data.business(businessId).contracts()) {
                if (contract.status() == EmploymentStatus.ACTIVE
                        && worker.equals(contract.employeeUuid())) {
                    activeForWorker++;
                }
            }
            if (!oneTimeOffer.ok() || oneTimePending == null || !oneTimeAccepted.ok()
                    || activeForWorker != 2) {
                failures.add("a one-time contract did not coexist with the standing role");
            }
            ctx.checks++;
            final EmploymentContractRecord standingNow = activeStanding(
                    data.business(businessId), worker);
            if (standingNow == null || oneTimePending == null) {
                failures.add("the standing role disappeared after the one-time acceptance");
            } else {
                final BusinessRecord orderCheck = data.business(businessId).withContracts(
                        List.of(data.business(businessId).contract(oneTimePending.id()),
                                standingNow));
                if (!orderCheck.hasActiveBusinessRole(worker)
                        || orderCheck.standingContractFor(worker) == null
                        || !standingNow.id().equals(
                                orderCheck.standingContractFor(worker).id())) {
                    failures.add("the standing role depends on contract list order");
                }
            }
            ctx.checks++;
            final BusinessEmploymentService.Outcome completed = oneTimePending == null
                    ? BusinessEmploymentService.Outcome.refused(
                            ErrorCode.INVALID_STATE, "no one-time contract")
                    : BusinessEmploymentService.endContract(data, workerActor, businessId,
                            oneTimePending.id(), "complete", "task done", now + 1_000L);
            if (!completed.ok()
                    || oneTimePending == null
                    || data.business(businessId).contract(oneTimePending.id()).status()
                            != EmploymentStatus.COMPLETED) {
                failures.add("a one-time contract could not be completed");
            }
            ctx.checks++;
            final BusinessEmploymentService.Outcome declined = BusinessEmploymentService.sendOffer(
                    data, ownerActor, businessId, other2, other2Name,
                    workerPosition == null ? "" : workerPosition.id(), "5", "true", now);
            final EmploymentContractRecord declinedPending =
                    findOffer(data.business(businessId), other2);
            final BusinessEmploymentService.Outcome declinedResult = declinedPending == null
                    ? BusinessEmploymentService.Outcome.refused(
                            ErrorCode.INVALID_STATE, "no offer")
                    : BusinessEmploymentService.respondOffer(
                            data, new BusinessEmploymentService.Actor(
                                    other2, other2Name, Set.of()),
                            businessId, declinedPending.id(), false, now);
            if (!declined.ok() || declinedPending == null || !declinedResult.ok()
                    || data.business(businessId).contract(declinedPending.id()).status()
                            != EmploymentStatus.DECLINED) {
                failures.add("a declined offer did not become DECLINED");
            }

            // Contract list scoping and promotion/demotion.
            ctx.checks++;
            final BusinessEmploymentService.ContractPage ownerContracts =
                    BusinessEmploymentService.contractPage(data, ownerActor, businessId, 0);
            final BusinessEmploymentService.ContractPage workerContracts =
                    BusinessEmploymentService.contractPage(data, workerActor, businessId, 0);
            if (ownerContracts.total() < 4 || workerContracts.total() != 2) {
                failures.add("contract list scoping did not show all for the owner and own for the worker");
            }
            ctx.checks++;
            final BusinessEmploymentService.Outcome seniorCreated =
                    BusinessEmploymentService.createPosition(
                            data, ownerActor, businessId, "Senior", managerPosition == null
                                    ? "" : managerPosition.id(), "20", "notes", "2", now);
            final BusinessPositionRecord seniorPosition =
                    findPosition(data.business(businessId), "Senior");
            final EmploymentContractRecord standingContract =
                    activeStanding(data.business(businessId), worker);
            if (!seniorCreated.ok() || seniorPosition == null || standingContract == null) {
                failures.add("the senior position or the standing contract is missing");
            }
            ctx.checks++;
            final BusinessEmploymentService.Outcome ownerPromotion =
                    BusinessEmploymentService.promoteContract(
                            data, ownerActor, businessId,
                            standingContract == null ? "" : standingContract.id(),
                            seniorPosition == null ? "" : seniorPosition.id(), now);
            final BusinessEmploymentService.Outcome managerDemotion =
                    BusinessEmploymentService.promoteContract(
                            data, managerActor, businessId,
                            standingContract == null ? "" : standingContract.id(),
                            workerPosition == null ? "" : workerPosition.id(), now);
            if (!ownerPromotion.ok() || !managerDemotion.ok()
                    || standingContract == null || workerPosition == null
                    || !workerPosition.id().equals(
                            data.business(businessId).contract(standingContract.id()).positionId())) {
                failures.add("owner or manager promotion did not move the contract");
            }
            ctx.checks++;
            if (BusinessEmploymentService.promoteContract(data, workerActor, businessId,
                    standingContract == null ? "" : standingContract.id(),
                    seniorPosition == null ? "" : seniorPosition.id(), now).code()
                    != ErrorCode.FORBIDDEN
                    || BusinessEmploymentService.promoteContract(data, managerActor, businessId,
                            managerPending == null ? "" : managerPending.id(),
                            seniorPosition == null ? "" : seniorPosition.id(), now).code()
                            != ErrorCode.FORBIDDEN) {
                failures.add("a worker promoted themselves or a manager promoted themselves");
            }

            // Payroll: elapsed real days, frozen wage, cap and authority. With banking enabled the
            // legacy run is refused, so the contract counters stay untouched.
            ctx.checks++;
            final EmploymentContractRecord workerBefore = activeStanding(
                    data.business(businessId), worker);
            final EmploymentContractRecord managerBefore = activeStanding(
                    data.business(businessId), manager);
            EmploymentContractRecord workerAfter4 = workerBefore == null ? null
                    : data.business(businessId).contract(workerBefore.id());
            if (GovConfig.bankEnabled()) {
                if (BusinessEmploymentService.runPayroll(
                        data, ownerActor, businessId, now + 3L * day + 1_000L).code()
                        != ErrorCode.INVALID_STATE) {
                    failures.add("banking did not retire the legacy payroll run");
                }
            } else {
                final BusinessEmploymentService.Outcome payroll1 =
                        BusinessEmploymentService.runPayroll(
                                data, ownerActor, businessId, now + 3L * day + 1_000L);
                final EmploymentContractRecord workerAfter1 = workerBefore == null ? null
                        : data.business(businessId).contract(workerBefore.id());
                final EmploymentContractRecord managerAfter1 = managerBefore == null ? null
                        : data.business(businessId).contract(managerBefore.id());
                if (!payroll1.ok() || workerAfter1 == null || managerAfter1 == null
                        || workerAfter1.paidTotal() != 15L
                        || managerAfter1.paidTotal() != 30L
                        || workerAfter1.lastPaidAt() != workerBefore.startedAt() + 3L * day
                        || managerAfter1.lastPaidAt() != managerBefore.startedAt() + 3L * day
                        || data.business(businessId).payroll().paymentsRecorded() != 1) {
                    failures.add("payroll did not pay three full real days at each frozen wage");
                }
                ctx.checks++;
                if (BusinessEmploymentService.runPayroll(
                        data, ownerActor, businessId, now + 3L * day + 1_000L).ok()) {
                    failures.add("payroll paid a second time without a new full day");
                }
                ctx.checks++;
                final BusinessEmploymentService.Outcome managerPayroll =
                        BusinessEmploymentService.runPayroll(
                                data, managerActor, businessId, now + 4L * day + 1_000L);
                final EmploymentContractRecord workerAfter2 = workerBefore == null ? null
                        : data.business(businessId).contract(workerBefore.id());
                final EmploymentContractRecord managerAfter2 = managerBefore == null ? null
                        : data.business(businessId).contract(managerBefore.id());
                if (!managerPayroll.ok() || workerAfter2 == null || managerAfter2 == null
                        || workerAfter2.paidTotal() != 20L
                        || managerAfter2.paidTotal() != 30L) {
                    failures.add(
                            "a manager's payroll run paid themselves or failed to pay a subordinate");
                }
                ctx.checks++;
                if (BusinessEmploymentService.runPayroll(
                        data, workerActor, businessId, now).code()
                        != ErrorCode.FORBIDDEN) {
                    failures.add("a contract without PAYROLL ran payroll");
                }
                ctx.checks++;
                BusinessEmploymentService.runPayroll(
                        data, ownerActor, businessId, now + 12L * day);
                final EmploymentContractRecord workerAfter3 = workerBefore == null ? null
                        : data.business(businessId).contract(workerBefore.id());
                final EmploymentContractRecord managerAfter3 = managerBefore == null ? null
                        : data.business(businessId).contract(managerBefore.id());
                if (workerAfter3 == null || managerAfter3 == null
                        || workerAfter3.paidTotal() != 55L
                        || managerAfter3.paidTotal() != 100L
                        || workerAfter3.lastPaidAt() != workerBefore.startedAt() + 11L * day
                        || managerAfter3.lastPaidAt() != managerBefore.startedAt() + 10L * day) {
                    failures.add(
                            "the payroll cap did not pay seven days and advance by only those days");
                }
                ctx.checks++;
                BusinessEmploymentService.runPayroll(
                        data, ownerActor, businessId, now + 30L * day);
                workerAfter4 = workerBefore == null ? null
                        : data.business(businessId).contract(workerBefore.id());
                final EmploymentContractRecord managerAfter4 = managerBefore == null ? null
                        : data.business(businessId).contract(managerBefore.id());
                if (workerAfter4 == null || managerAfter4 == null
                        || workerAfter4.paidTotal() != 90L
                        || managerAfter4.paidTotal() != 170L
                        || workerAfter4.lastPaidAt() != workerBefore.startedAt() + 18L * day
                        || managerAfter4.lastPaidAt() != managerBefore.startedAt() + 17L * day) {
                    failures.add(
                            "the payroll catch-up did not advance by exactly seven more days");
                }
            }



        ctx.otherShop = otherShop;
        ctx.workerBefore = workerBefore;
        ctx.workerAfter4 = workerAfter4;
    }
}
