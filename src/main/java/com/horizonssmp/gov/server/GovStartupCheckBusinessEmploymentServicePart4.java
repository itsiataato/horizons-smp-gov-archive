package com.horizonssmp.gov.server;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import com.horizonssmp.gov.common.GovCapability;
import com.horizonssmp.gov.common.GovFields;
import com.horizonssmp.gov.common.records.BusinessPositionRecord;
import com.horizonssmp.gov.network.GovNetwork;
import java.util.Map;
import java.util.UUID;
import com.horizonssmp.gov.common.BusinessKeys;
import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.GovCapabilities;
import com.horizonssmp.gov.common.enums.EmploymentStatus;
import com.horizonssmp.gov.common.enums.HistoryKind;
import com.horizonssmp.gov.common.records.BusinessRecord;
import com.horizonssmp.gov.common.records.EmploymentContractRecord;
import com.horizonssmp.gov.common.records.FineRecord;
import com.horizonssmp.gov.common.records.HistoryEvent;
import net.minecraft.nbt.CompoundTag;
import static com.horizonssmp.gov.server.GovStartupCheckBusinessEmploymentServiceSupport.*;

final class GovStartupCheckBusinessEmploymentServicePart4 {

    private GovStartupCheckBusinessEmploymentServicePart4() {
    }

    static void run(final GovStartupCheckBusinessEmploymentServiceContext ctx) {
        final List<String> failures = ctx.failures;
        final long now = ctx.now;
        final long day = ctx.day;
        final long wagesBeforeThird = ctx.wagesBeforeThird;
        final GovSavedData data = ctx.data;
        final UUID owner = ctx.owner;
        final UUID manager = ctx.manager;
        final UUID worker = ctx.worker;
        final UUID exec = ctx.exec;
        final UUID transferTarget = ctx.transferTarget;
        final UUID rejectOwner = ctx.rejectOwner;
        final UUID other3 = ctx.other3;
        final UUID officerId = ctx.officerId;
        final String managerName = ctx.managerName;
        final String workerName = ctx.workerName;
        final BusinessService.Actor registrarBusiness = ctx.registrarBusiness;
        final BusinessEmploymentService.Actor registrarEmployment = ctx.registrarEmployment;
        final BusinessEmploymentService.Actor ownerActor = ctx.ownerActor;
        final BusinessEmploymentService.Actor workerActor = ctx.workerActor;
        final BusinessEmploymentService.Actor execActor = ctx.execActor;
        final BusinessEmploymentService.Actor govworkerActor = ctx.govworkerActor;
        final String businessId = ctx.businessId;
        final BusinessRecord pendingClose = ctx.pendingClose;
        final FineRecord complaintFine = ctx.complaintFine;

            // Reject leaves the business trading; rename authority is owner or government only.
            ctx.checks++;
            final BusinessService.Outcome rejectShopOutcome = BusinessService.register(
                    data, registrarBusiness, "StartupCheck M12 Reject Shop", "shop",
                    rejectOwner, "StartupCheck M12 Reject Owner", now);
            final BusinessRecord rejectShop = data.business(rejectShopOutcome.businessId());
            final BusinessEmploymentService.Actor rejectOwnerActor =
                    new BusinessEmploymentService.Actor(
                            rejectOwner, "StartupCheck M12 Reject Owner", Set.of());
            final BusinessEmploymentService.Outcome rejectRequest =
                    BusinessEmploymentService.requestClose(
                            data, rejectOwnerActor,
                            rejectShop == null ? "" : rejectShop.id(), "", now);
            final BusinessEmploymentService.Outcome rejected = BusinessEmploymentService.rejectClose(
                    data, execActor, rejectShop == null ? "" : rejectShop.id(), "", now);
            final BusinessRecord afterReject = rejectShop == null ? null
                    : data.business(rejectShop.id());
            if (!rejectRequest.ok() || !rejected.ok() || afterReject == null
                    || afterReject.closed() || afterReject.pendingClose()
                    || !afterReject.active()) {
                failures.add("a rejected close did not leave the business trading");
            }
            ctx.checks++;
            final BusinessEmploymentService.Outcome renameOwner =
                    BusinessEmploymentService.rename(data, rejectOwnerActor,
                            rejectShop == null ? "" : rejectShop.id(),
                            "StartupCheck M12 Renamed Shop", now);
            final BusinessEmploymentService.Outcome renameGov =
                    BusinessEmploymentService.rename(data, govworkerActor,
                            rejectShop == null ? "" : rejectShop.id(),
                            "StartupCheck M12 Renamed Again", now);
            if (!renameOwner.ok() || !renameGov.ok()) {
                failures.add("the owner or a government manager could not rename a business");
            }
            ctx.checks++;
            if (BusinessEmploymentService.rename(data, workerActor,
                            rejectShop == null ? "" : rejectShop.id(), "StartupCheck Stranger", now)
                            .code() != ErrorCode.FORBIDDEN
                    || BusinessEmploymentService.rename(data, rejectOwnerActor,
                            rejectShop == null ? "" : rejectShop.id(),
                            "StartupCheck M12 Employment Shop", now).code()
                            != ErrorCode.NAME_TAKEN) {
                failures.add("a stranger renamed a business or a duplicate name was accepted");
            }
            ctx.checks++;
            if (BusinessService.close(data, new BusinessService.Actor(
                    govworkerActor.uuid(), govworkerActor.name(),
                    govworkerActor.capabilities()), rejectShop == null ? "" : rejectShop.id(), now)
                    .code() != ErrorCode.FORBIDDEN) {
                failures.add("the old direct close bypassed business.close.approve");
            }
            ctx.checks++;
            final BusinessService.Outcome directClose = BusinessService.close(
                    data, new BusinessService.Actor(
                            exec, "StartupCheck M12 Executive",
                            EnumSet.of(GovCapability.BUSINESS_MANAGE,
                                    GovCapability.BUSINESS_CLOSE_APPROVE)),
                    rejectShop == null ? "" : rejectShop.id(), now);
            if (!directClose.ok()
                    || !BusinessRecord.STATUS_CLOSED.equals(
                            data.business(rejectShop.id()).status())) {
                failures.add("an executive direct close did not store CLOSED");
            }

            // Transfer: old CEO ends, new CEO starts, tree preserved, one role target.
            ctx.checks++;
            final BusinessService.Outcome thirdShopOutcome = BusinessService.register(
                    data, registrarBusiness, "StartupCheck M12 Third Shop", "shop",
                    other3, "StartupCheck M12 Other Three", now);
            final BusinessRecord thirdShop = data.business(thirdShopOutcome.businessId());
            if (!thirdShopOutcome.ok() || thirdShop == null) {
                failures.add("could not create the third business for the transfer test");
            }
            ctx.checks++;
            if (BusinessEmploymentService.transferOwnership(
                    data, registrarEmployment, data.business(businessId), other3,
                    "StartupCheck M12 Other Three", now).code() != ErrorCode.INVALID_STATE) {
                failures.add("a transfer to a player with a standing role elsewhere was accepted");
            }
            ctx.checks++;
            final int positionsBeforeTransfer = data.business(businessId).positions().size();
            final BusinessEmploymentService.Outcome transferred =
                    BusinessEmploymentService.transferOwnership(
                            data, registrarEmployment, data.business(businessId),
                            transferTarget, "StartupCheck M12 Transfer Target", now);
            final BusinessRecord afterTransfer = data.business(businessId);
            int activeCeos = 0;
            if (afterTransfer != null) {
                for (final EmploymentContractRecord contract : afterTransfer.contracts()) {
                    if (contract.status() == EmploymentStatus.ACTIVE
                            && BusinessPositionRecord.CEO_POSITION_ID.equals(
                                    contract.positionId())) {
                        activeCeos++;
                    }
                }
            }
            if (!transferred.ok() || afterTransfer == null
                    || !transferTarget.equals(afterTransfer.ownerUuid())
                    || afterTransfer.positions().size() != positionsBeforeTransfer
                    || activeCeos != 1
                    || BusinessEmploymentService.standingRole(data, owner) != null
                    || !afterTransfer.id().equals(
                            BusinessEmploymentService.standingRole(data, transferTarget) == null
                                    ? "" : BusinessEmploymentService.standingRole(
                                            data, transferTarget).id())) {
                failures.add("ownership transfer left the wrong owner, role or CEO count");
            }

            // History: writer, reads, cap, job/vehicle/permit integration and one whole-file load.
            ctx.checks++;
            if (!HistoryService.canRead(Set.of(), worker, worker)
                    || HistoryService.canRead(Set.of(), worker, manager)
                    || !HistoryService.canRead(EnumSet.of(GovCapability.PLAYER_LOOKUP),
                            worker, manager)) {
                failures.add("history read authorization is not self-or-player.lookup");
            }
            ctx.checks++;
            final Set<GovCapability> jobOfficerCaps = EnumSet.of(GovCapability.JOB_ASSIGN);
            jobOfficerCaps.addAll(GovCapabilities.BASELINE);
            final JobService.Actor jobOfficer = new JobService.Actor(
                    officerId, "StartupCheck M12 Job Officer", jobOfficerCaps);
            final int managerHistoryBefore = data.historyFor(manager).size();
            final JobService.Outcome jobAssigned = JobService.assign(
                    data, jobOfficer, manager, managerName,
                    GovCapabilities.CITIZEN_JOB_ID, now + 60L * day);
            final List<HistoryEvent> managerHistoryAfter = data.historyFor(manager);
            if (!jobAssigned.ok() || managerHistoryAfter.isEmpty()
                    || managerHistoryAfter.get(0).kind() != HistoryKind.GOV_JOB_ASSIGNED) {
                failures.add("a government job assignment did not append history");
            }
            final int managerHistoryCount = managerHistoryAfter.size();
            JobService.assign(data, jobOfficer, manager, managerName,
                    GovCapabilities.CITIZEN_JOB_ID, now + 60L * day);
            if (data.historyFor(manager).size() != managerHistoryCount) {
                failures.add("an idempotent job assignment appended a second history row");
            }
            ctx.checks++;
            final JobService.Actor assignmentOnly = new JobService.Actor(
                    new UUID(0L, 9613L), "StartupCheck M12 Assignment Only",
                    EnumSet.of(GovCapability.JOB_ASSIGN));
            if (JobService.assign(data, assignmentOnly, worker, workerName,
                    GovCapabilities.GOVEXECUTIVE_JOB_ID, now + 60L * day).code()
                    != ErrorCode.FORBIDDEN) {
                failures.add("a job.assign-only actor appointed another player as executive");
            }
            ctx.checks++;
            final VehicleService.Actor vehicleOfficer = new VehicleService.Actor(
                    officerId, "StartupCheck M12 Vehicle Officer",
                    EnumSet.of(GovCapability.VEHICLE_REGISTER));
            final VehicleService.Outcome vehicleRegistered = VehicleService.register(
                    data, vehicleOfficer, manager, managerName, "MAA-001", "car",
                    false, now + 60L * day);
            if (!vehicleRegistered.ok()
                    || data.historyFor(manager).isEmpty()
                    || data.historyFor(manager).get(0).kind()
                            != HistoryKind.VEHICLE_REGISTERED) {
                failures.add("a vehicle registration did not append history");
            }
            ctx.checks++;
            final WeaponService.Actor weaponOfficer = new WeaponService.Actor(
                    officerId, "StartupCheck M12 Weapon Officer",
                    EnumSet.of(GovCapability.WEAPON_REGISTER));
            final WeaponService.Outcome permitIssued = WeaponService.issuePermit(
                    data, weaponOfficer, manager, managerName, "L",
                    "StartupCheck M12 permit", 0L, now + 60L * day);
            if (!permitIssued.ok()
                    || data.historyFor(manager).isEmpty()
                    || data.historyFor(manager).get(0).kind()
                            != HistoryKind.WEAPON_PERMIT_ISSUED) {
                failures.add("a weapon permit issue did not append history");
            }
            ctx.checks++;
            final long historyCount = data.historyFor(manager).size();
            final HistoryService.Page managerPage = HistoryService.page(data, manager, 0);
            final Map<String, String> historyFields = HistoryService.listFields(data, manager, 0);
            if (managerPage.total() != (int) historyCount
                    || managerPage.rows().size() != BusinessKeys.EMPLOYMENT_PAGE_SIZE
                    || managerPage.pages() != 2
                    || !Integer.toString((int) historyCount).equals(
                            historyFields.get(BusinessKeys.HISTORY_COUNT))
                    || !"0".equals(historyFields.get(BusinessKeys.PAGE))
                    || !"2".equals(historyFields.get(BusinessKeys.PAGES))
                    || historyFields.get(BusinessKeys.HISTORY_PREFIX + "0"
                            + BusinessKeys.HISTORY_ROW_KIND) == null
                    || GovFields.encode(historyFields).length() > GovFields.MAX_ENCODED) {
                failures.add("the history page, cap or field block is wrong");
            }
            ctx.checks++;
            final CompoundTag tag = new CompoundTag();
            data.save(tag, null);
            final GovSavedData reloaded = GovSavedData.load(tag, null);
            final BusinessRecord reloadedBusiness = reloaded.business(businessId);
            final FineRecord reloadedFine = complaintFine == null
                    ? null : reloaded.fine(complaintFine.id());
            if (reloadedBusiness == null
                    || !transferTarget.equals(reloadedBusiness.ownerUuid())
                    || reloadedBusiness.positions().size() != positionsBeforeTransfer
                    || reloadedBusiness.complaints().size() != 3
                    || reloadedFine == null
                    || !owner.equals(reloadedFine.victimUuid())
                    || (wagesBeforeThird > 0L && reloadedFine.compensationPaidAt() <= 0L)
                    || reloaded.historyFor(manager).size() != (int) historyCount
                    || !reloaded.historyFor(worker).equals(data.historyFor(worker))
                    || GovNetwork.PROTOCOL_VERSION != 4) {
                failures.add("the business employment data did not survive a whole-file save and load");
            }
            ctx.checks++;
            if (!HistoryKind.byId(HistoryKind.BUSINESS_TRANSFERRED.id()).isPresent()
                    || !HistoryKind.byId(HistoryKind.BUSINESS_CLOSE_REJECTED.id()).isPresent()
                    || !HistoryKind.byId(HistoryKind.CONTRACT_TRANSFERRED.id()).isPresent()
                    || !HistoryKind.byId(HistoryKind.COMPENSATION_PAID.id()).isPresent()) {
                failures.add("a new history kind does not round-trip through its parser");
            }

    }
}
