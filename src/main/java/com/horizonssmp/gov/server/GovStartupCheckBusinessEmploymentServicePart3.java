package com.horizonssmp.gov.server;

import java.util.EnumSet;
import java.util.List;
import com.horizonssmp.gov.common.GovCapability;
import com.horizonssmp.gov.common.records.GoldAmount;
import java.util.Optional;
import java.util.Set;
import com.horizonssmp.gov.common.enums.FineStatus;
import com.horizonssmp.gov.config.GovConfig;
import java.util.Map;
import java.util.UUID;
import com.horizonssmp.gov.common.BusinessKeys;
import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.enums.EmploymentStatus;
import com.horizonssmp.gov.common.enums.FineBand;
import com.horizonssmp.gov.common.records.BusinessComplaintRecord;
import com.horizonssmp.gov.common.records.BusinessRecord;
import com.horizonssmp.gov.common.records.EmploymentContractRecord;
import com.horizonssmp.gov.common.records.FineRecord;
import static com.horizonssmp.gov.server.GovStartupCheckBusinessEmploymentServiceSupport.*;

final class GovStartupCheckBusinessEmploymentServicePart3 {

    private GovStartupCheckBusinessEmploymentServicePart3() {
    }

    static void run(final GovStartupCheckBusinessEmploymentServiceContext ctx) {
        final List<String> failures = ctx.failures;
        final long now = ctx.now;
        final long day = ctx.day;
        final GovSavedData data = ctx.data;
        final UUID owner = ctx.owner;
        final UUID manager = ctx.manager;
        final UUID worker = ctx.worker;
        final UUID other2 = ctx.other2;
        final UUID officerId = ctx.officerId;
        final String ownerName = ctx.ownerName;
        final String workerName = ctx.workerName;
        final String other2Name = ctx.other2Name;
        final BusinessEmploymentService.Actor ownerActor = ctx.ownerActor;
        final BusinessEmploymentService.Actor managerActor = ctx.managerActor;
        final BusinessEmploymentService.Actor workerActor = ctx.workerActor;
        final BusinessEmploymentService.Actor otherActor = ctx.otherActor;
        final BusinessEmploymentService.Actor govworkerActor = ctx.govworkerActor;
        final BusinessEmploymentService.Actor execActor = ctx.execActor;
        final String businessId = ctx.businessId;
        final BusinessRecord otherShop = ctx.otherShop;
        final EmploymentContractRecord workerBefore = ctx.workerBefore;
        final EmploymentContractRecord workerAfter4 = ctx.workerAfter4;

            // Complaints: cooldown, CEO-only, third upheld ends the contract and issues the fine.
            final long complaintAt = now + 40L * day;
            ctx.checks++;
            final BusinessEmploymentService.Outcome firstComplaint =
                    BusinessEmploymentService.fileComplaint(
                            data, ownerActor, businessId, worker, workerName,
                            "StartupCheck first absence", complaintAt);
            final BusinessRecord afterFirst = data.business(businessId);
            if (!firstComplaint.ok() || afterFirst == null
                    || afterFirst.complaints().size() != 1
                    || !afterFirst.complaints().get(0).upheld()
                    || !afterFirst.complaints().get(0).fineId().isEmpty()) {
                failures.add("the first complaint was not recorded upheld without a fine");
            }
            ctx.checks++;
            if (BusinessEmploymentService.fileComplaint(data, ownerActor, businessId,
                    worker, workerName, "StartupCheck too soon", complaintAt).code()
                    != ErrorCode.RATE_LIMITED) {
                failures.add("the complaint cooldown was not enforced");
            }
            ctx.checks++;
            if (BusinessEmploymentService.fileComplaint(data, ownerActor, businessId,
                    owner, ownerName, "StartupCheck self", complaintAt).code()
                    != ErrorCode.INVALID_STATE
                    || BusinessEmploymentService.fileComplaint(data, managerActor, businessId,
                            worker, workerName, "StartupCheck not ceo", complaintAt).code()
                            != ErrorCode.FORBIDDEN) {
                failures.add("a self-complaint or a manager's complaint was accepted");
            }
            ctx.checks++;
            final BusinessEmploymentService.Outcome secondComplaint =
                    BusinessEmploymentService.fileComplaint(
                            data, ownerActor, businessId, worker, workerName,
                            "StartupCheck second absence", complaintAt + day);
            if (!secondComplaint.ok() || data.business(businessId).complaints().size() != 2) {
                failures.add("the second complaint was not recorded");
            }
            ctx.checks++;
            final long wagesBeforeThird = workerAfter4 == null ? 0L : workerAfter4.paidTotal();
            final BusinessEmploymentService.Outcome thirdComplaint =
                    BusinessEmploymentService.fileComplaint(
                            data, ownerActor, businessId, worker, workerName,
                            "StartupCheck third absence", complaintAt + 2L * day);
            final BusinessRecord afterThird = data.business(businessId);
            final BusinessComplaintRecord thirdRecord = afterThird == null
                    || afterThird.complaints().size() < 3
                    ? null : afterThird.complaints().get(2);
            final FineRecord complaintFine = thirdRecord == null
                    || thirdRecord.fineId().isEmpty() ? null : data.fine(thirdRecord.fineId());
            final EmploymentContractRecord workerTerminated = workerBefore == null
                    ? null : afterThird == null ? null
                            : afterThird.contract(workerBefore.id());
            if (!thirdComplaint.ok() || thirdRecord == null || complaintFine == null
                    || workerTerminated == null
                    || workerTerminated.status() != EmploymentStatus.TERMINATED
                    || !owner.equals(complaintFine.victimUuid())
                    || complaintFine.band() != FineBand.AGGRAVATED
                    || complaintFine.amount().count() != 64L
                    || complaintFine.restitution().count() != wagesBeforeThird
                    || complaintFine.exileDays() != 0
                    || complaintFine.status() != FineStatus.UNPAID
                    || !FineService.DEFAULT_ARTICLE.equals(complaintFine.lawArticle())
                    || BusinessEmploymentService.standingRole(data, worker) != null) {
                failures.add("the third upheld complaint did not terminate and create the 64-gold fine");
            }
            ctx.checks++;
            if (BusinessEmploymentService.fileComplaint(data, ownerActor, businessId,
                    worker, workerName, "StartupCheck after dismissal", complaintAt + 3L * day)
                    .code() != ErrorCode.INVALID_STATE) {
                failures.add("a complaint against a terminated contract was accepted");
            }
            ctx.checks++;
            final BusinessEmploymentService.ComplaintPage workerComplaints =
                    BusinessEmploymentService.complaintPage(
                            data, workerActor, businessId, 0);
            final BusinessEmploymentService.ComplaintPage ownerComplaints =
                    BusinessEmploymentService.complaintPage(
                            data, ownerActor, businessId, 0);
            if (workerComplaints.total() != 3 || ownerComplaints.total() != 3) {
                failures.add("complaint list scoping is wrong for the employee or owner");
            }

            // Compensation: offline victim holds, next settlement pays exactly once, old fines do not.
            ctx.checks++;
            final FineService.ReceptionPoint reception =
                    new FineService.ReceptionPoint("minecraft:overworld", 0, 0, 0, 100);
            final GovStartupCheckFinesGateway wallet = new GovStartupCheckFinesGateway();
            final long due = complaintFine == null ? 0L : complaintFine.totalDue().count();
            wallet.gold(worker, due + 100L);
            wallet.present(worker, true);
            wallet.atReception(worker, true);
            wallet.gold(owner, 20L);
            wallet.present(owner, false);
            final FineService.Actor workerFineActor =
                    new FineService.Actor(worker, workerName, Set.of());
            final FineService.Outcome complaintPaid = complaintFine == null
                    ? FineService.Outcome.refused(ErrorCode.INVALID_STATE, "no fine")
                    : FineService.paySelf(data, workerFineActor, complaintFine.id(), wallet,
                            Optional.of(reception), complaintAt + 3L * day);
            final FineRecord paidComplaint = complaintFine == null
                    ? null : data.fine(complaintFine.id());
            if (!complaintPaid.ok() || paidComplaint == null
                    || paidComplaint.status() != FineStatus.PAID
                    || (wagesBeforeThird > 0L && !paidComplaint.compensationPending())
                    || wallet.goldOf(worker) != 100L
                    || wallet.goldOf(owner) != 20L) {
                failures.add("offline restitution was not held as a pending compensation");
            }
            ctx.checks++;
            wallet.present(owner, true);
            wallet.atReception(owner, true);
            final FineService.Actor fineOfficer = new FineService.Actor(
                    officerId, "StartupCheck M12 Fine Officer",
                    EnumSet.of(GovCapability.FINE_ISSUE));
            final FineService.Outcome trigger = FineService.issue(
                    data, fineOfficer, owner, ownerName, "PETTY", "9.11", "0", "", "",
                    "StartupCheck compensation trigger", complaintAt + 4L * day);
            final FineService.Actor ownerFineActor =
                    new FineService.Actor(owner, ownerName, Set.of());
            final FineService.Outcome triggerPaid = trigger.ok()
                    ? FineService.paySelf(data, ownerFineActor, trigger.fineId(), wallet,
                            Optional.of(reception), complaintAt + 4L * day)
                    : FineService.Outcome.refused(trigger.code(), trigger.message());
            final FineRecord delivered = paidComplaint == null
                    ? null : data.fine(paidComplaint.id());
            if (!triggerPaid.ok() || delivered == null
                    || (wagesBeforeThird > 0L && (delivered.compensationPending()
                            || delivered.compensationPaidAt() <= 0L))
                    || wallet.goldOf(owner) != 20L - FineService.PETTY_GOLD
                            + wagesBeforeThird) {
                failures.add("the pending restitution was not delivered on the victim's next settlement");
            }
            ctx.checks++;
            final long ownerAfterCompensation = wallet.goldOf(owner);
            final FineService.Outcome trigger2 = FineService.issue(
                    data, fineOfficer, owner, ownerName, "PETTY", "9.11", "0", "", "",
                    "StartupCheck no double compensation", complaintAt + 5L * day);
            final FineService.Outcome trigger2Paid = trigger2.ok()
                    ? FineService.paySelf(data, ownerFineActor, trigger2.fineId(), wallet,
                            Optional.of(reception), complaintAt + 5L * day)
                    : FineService.Outcome.refused(trigger2.code(), trigger2.message());
            if (!trigger2Paid.ok()
                    || wallet.goldOf(owner) != ownerAfterCompensation - FineService.PETTY_GOLD) {
                failures.add("restitution was delivered twice on a later settlement");
            }
            ctx.checks++;
            final FineRecord legacyFine = new FineRecord(
                    "f-m12-legacy", other2, officerId, "9.11", FineBand.FELONY,
                    GoldAmount.of(FineService.FELONY_GOLD), GoldAmount.of(10L),
                    "StartupCheck legacy display-only restitution", complaintAt, 0L,
                    FineStatus.UNPAID, 0L, "", null, "", 0, ownerName, "", false);
            data.putFine(legacyFine);
            wallet.gold(other2, 100L);
            wallet.present(other2, true);
            wallet.atReception(other2, true);
            final long ownerBeforeLegacy = wallet.goldOf(owner);
            final FineService.Outcome legacyPaid = FineService.paySelf(
                    data, new FineService.Actor(other2, other2Name, Set.of()),
                    legacyFine.id(), wallet, Optional.of(reception), complaintAt + 6L * day);
            final FineRecord legacyAfter = data.fine(legacyFine.id());
            if (!legacyPaid.ok() || legacyAfter == null
                    || legacyAfter.victimUuid() != null
                    || legacyAfter.compensationPending()
                    || wallet.goldOf(owner) != ownerBeforeLegacy) {
                failures.add("an old fine without a victim UUID invented a compensation recipient");
            }

            // Soft close: request, executive decision, records retained, worker cannot approve.
            ctx.checks++;
            final BusinessEmploymentService.Outcome closeRequest =
                    BusinessEmploymentService.requestClose(
                            data, govworkerActor, otherShop == null ? "" : otherShop.id(),
                            "StartupCheck closing", now);
            final BusinessRecord pendingClose = otherShop == null ? null
                    : data.business(otherShop.id());
            if (!closeRequest.ok() || pendingClose == null || !pendingClose.pendingClose()
                    || !pendingClose.active()
                    || !govworkerActor.uuid().equals(pendingClose.closeRequestedBy())) {
                failures.add("a government worker could not request a soft close");
            }
            ctx.checks++;
            if (BusinessEmploymentService.approveClose(
                    data, govworkerActor, otherShop == null ? "" : otherShop.id(), "", now).code()
                    != ErrorCode.FORBIDDEN) {
                failures.add("a government worker without business.close.approve approved a close");
            }
            ctx.checks++;
            final BusinessEmploymentService.Outcome approved = BusinessEmploymentService.approveClose(
                    data, execActor, otherShop == null ? "" : otherShop.id(), "StartupCheck approved",
                    now);
            final BusinessRecord closedOther = otherShop == null ? null
                    : data.business(otherShop.id());
            boolean allOtherContractsTerminal = true;
            if (closedOther != null) {
                for (final EmploymentContractRecord contract : closedOther.contracts()) {
                    if (contract.status() == EmploymentStatus.ACTIVE
                            || contract.status() == EmploymentStatus.OFFERED) {
                        allOtherContractsTerminal = false;
                    }
                }
            }
            if (!approved.ok() || closedOther == null
                    || !closedOther.closed() || closedOther.pendingClose()
                    || !allOtherContractsTerminal
                    || closedOther.positions().isEmpty()) {
                failures.add("the approved close did not close, end contracts and retain positions");
            }



        ctx.complaintFine = complaintFine;
        ctx.pendingClose = pendingClose;
        ctx.wagesBeforeThird = wagesBeforeThird;
    }
}
