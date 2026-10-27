package com.horizonssmp.gov.server;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import com.horizonssmp.gov.common.BusinessKeys;
import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.server.GovActionDispatcher;
import java.util.Map;
import com.horizonssmp.gov.common.GovCapability;
import com.horizonssmp.gov.common.enums.DutyState;
import com.horizonssmp.gov.common.records.EmploymentRecord;
import com.horizonssmp.gov.common.records.JailRecord;
import com.horizonssmp.gov.common.records.PlayerRecord;
import com.horizonssmp.gov.common.records.SuspectRecord;
import com.horizonssmp.gov.common.GovCapabilities;
import com.horizonssmp.gov.common.GovFields;
import com.horizonssmp.gov.common.OpCode;
import com.horizonssmp.gov.common.enums.BusinessPermission;
import com.horizonssmp.gov.common.enums.EmploymentStatus;
import com.horizonssmp.gov.common.records.BusinessPositionRecord;
import com.horizonssmp.gov.common.records.BusinessRecord;
import com.horizonssmp.gov.common.records.EmploymentContractRecord;
import static com.horizonssmp.gov.server.GovStartupCheckBusinessEmploymentServiceSupport.*;

final class GovStartupCheckBusinessEmploymentServicePart1 {

    private GovStartupCheckBusinessEmploymentServicePart1() {
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
        final String ownerName = ctx.ownerName;
        final String managerName = ctx.managerName;
        final String workerName = ctx.workerName;
        final String otherName = ctx.otherName;
        final String other2Name = ctx.other2Name;
        final BusinessService.Actor registrarBusiness = ctx.registrarBusiness;
        final BusinessEmploymentService.Actor registrarEmployment = ctx.registrarEmployment;
        final BusinessEmploymentService.Actor ownerActor = ctx.ownerActor;
        final BusinessEmploymentService.Actor managerActor = ctx.managerActor;
        final BusinessEmploymentService.Actor workerActor = ctx.workerActor;
        final BusinessEmploymentService.Actor otherActor = ctx.otherActor;

            ctx.checks++;
            if (BusinessEmploymentService.REAL_DAY_MS != 86_400_000L
                    || BusinessEmploymentService.MAX_PAYROLL_DAYS_PER_RUN != 7
                    || BusinessEmploymentService.COMPLAINT_THRESHOLD != 3
                    || BusinessEmploymentService.COMPLAINT_FINE_GOLD != 64L
                    || FineService.MAX_COMPLAINT_RESTITUTION_GOLD != 1_000_000L
                    || BusinessKeys.EMPLOYMENT_PAGE_SIZE != 6
                    || GovFields.MAX_ENCODED != 4000) {
                failures.add("a business employment constant is not the documented value");
            }
            ctx.checks++;
            final PlayerRecord dualJob = new PlayerRecord(
                    new UUID(0L, 9699L), "StartupCheck M12 Dual Job", now, now,
                    GovCapabilities.POLICE_JOB_ID, now, null, "Officer",
                    EnumSet.noneOf(GovCapability.class), SuspectRecord.NONE, DutyState.N_A,
                    new EmploymentRecord(GovCapabilities.JUDGE_JOB_ID, "Judge", now, null),
                    JailRecord.NONE, true);
            final Set<GovCapability> dualCaps = GovCapabilities.effective(dualJob, data::job);
            if (!GovCapabilities.POLICE_JOB_ID.equals(GovCapabilities.jobIdOf(dualJob))
                    || dualCaps.contains(GovCapability.AUDIT_VIEW)
                    || !dualCaps.contains(GovCapability.JAIL_COMMIT)
                    || GovCapabilities.holds(dualJob, GovCapabilities.JUDGE_JOB_ID)) {
                failures.add("a file naming two jobs granted more than the one authoritative job");
            }
            ctx.checks++;
            final OpCode[] employmentOps = {
                OpCode.BIZ_RENAME, OpCode.BIZ_POSITION_LIST, OpCode.BIZ_POSITION_CREATE,
                OpCode.BIZ_POSITION_EDIT, OpCode.BIZ_POSITION_DELETE,
                OpCode.BIZ_POSITION_MOVE, OpCode.BIZ_OFFER_LIST, OpCode.BIZ_OFFER_SEND,
                OpCode.BIZ_OFFER_RESPOND, OpCode.BIZ_CONTRACT_LIST,
                OpCode.BIZ_CONTRACT_END, OpCode.BIZ_CONTRACT_PROMOTE,
                OpCode.BIZ_PAYROLL_RUN, OpCode.BIZ_COMPLAINT_LIST,
                OpCode.BIZ_COMPLAINT_FILE, OpCode.BIZ_CLOSE_REQUEST, OpCode.HISTORY_LIST,
                OpCode.BIZ_CLOSE_APPROVE, OpCode.BIZ_CLOSE_REJECT
            };
            for (final OpCode op : employmentOps) {
                if (!GovActionDispatcher.isRegistered(op)) {
                    failures.add("M12 action has no handler registered: " + op);
                }
                if (op.audited() != !isEmploymentRead(op)) {
                    failures.add("M12 read/mutation audit flag is wrong: " + op);
                }
            }
            ctx.checks++;
            if (GovCapabilities.requiredFor(OpCode.BIZ_CLOSE_APPROVE)
                            != GovCapability.BUSINESS_CLOSE_APPROVE
                    || GovCapabilities.requiredFor(OpCode.BIZ_CLOSE_REJECT)
                            != GovCapability.BUSINESS_CLOSE_APPROVE
                    || GovCapabilities.requiredFor(OpCode.BIZ_OFFER_SEND)
                            != GovCapability.PLAYER_LOOKUP_SELF
                    || GovCapabilities.requiredFor(OpCode.HISTORY_LIST)
                            != GovCapability.PLAYER_LOOKUP_SELF) {
                failures.add("an M12 action does not require the documented capability");
            }

            // Registration creates the owner's seat and contract; a second standing role is refused.
            final BusinessService.Outcome registered = BusinessService.register(
                    data, registrarBusiness, "StartupCheck M12 Employment Shop", "shop",
                    owner, ownerName, now);
            final BusinessRecord business = data.business(registered.businessId());
            ctx.checks++;
            if (!registered.ok() || business == null
                    || business.positions().size() != 1
                    || business.ceoPosition() == null
                    || business.activeContractFor(owner) == null
                    || business.activeContractFor(owner).oneTime()
                    || !business.hasActiveBusinessRole(owner)
                    || BusinessEmploymentService.standingRole(data, owner) == null) {
                failures.add("registration did not create the CEO seat and active CEO contract");
            }
            ctx.checks++;
            if (BusinessService.register(data, registrarBusiness,
                    "StartupCheck M12 Second Owner Shop", "shop", owner, ownerName, now).code()
                    != ErrorCode.INVALID_STATE) {
                failures.add("a second business was registered to an owner who already holds a role");
            }
            final String businessId = business.id();

            // The manager position and the first written offer.
            ctx.checks++;
            final BusinessEmploymentService.Outcome managerCreated =
                    BusinessEmploymentService.createPosition(
                            data, ownerActor, businessId, "Manager", "ceo", "10",
                            "hire,promote,positions.manage,payroll,fire,demote", "0", now);
            final BusinessRecord withManager = data.business(businessId);
            final BusinessPositionRecord managerPosition =
                    findPosition(withManager, "Manager");
            if (!managerCreated.ok() || managerPosition == null
                    || !managerPosition.has(BusinessPermission.POSITIONS_MANAGE)) {
                failures.add("the owner could not create the manager position");
            }
            ctx.checks++;
            final BusinessEmploymentService.Outcome managerOffer =
                    BusinessEmploymentService.sendOffer(data, ownerActor, businessId,
                            manager, managerName, managerPosition == null ? "" : managerPosition.id(),
                            "10", "false", now);
            final EmploymentContractRecord managerPending =
                    findOffer(data.business(businessId), manager);
            final BusinessEmploymentService.Outcome managerAccepted = managerPending == null
                    ? BusinessEmploymentService.Outcome.refused(
                            ErrorCode.INVALID_STATE, "no offer")
                    : BusinessEmploymentService.respondOffer(
                            data, managerActor, businessId, managerPending.id(), true, now);
            if (!managerOffer.ok() || managerPending == null || !managerAccepted.ok()) {
                failures.add("a written offer to the manager could not be accepted");
            }

            // A subordinate position created by the manager.
            ctx.checks++;
            final BusinessEmploymentService.Outcome workerCreated =
                    BusinessEmploymentService.createPosition(
                            data, managerActor, businessId, "Worker",
                            managerPosition == null ? "" : managerPosition.id(), "5", "notes",
                            "1", now);
            final BusinessPositionRecord workerPosition =
                    findPosition(data.business(businessId), "Worker");
            if (!workerCreated.ok() || workerPosition == null) {
                failures.add("a manager could not create a position under their own seat");
            }
            ctx.checks++;
            if (BusinessEmploymentService.createPosition(
                    data, managerActor, businessId, "Above", "ceo", "5", "", "2", now).code()
                    != ErrorCode.FORBIDDEN) {
                failures.add("a manager created a position above their own seat");
            }
            ctx.checks++;
            final BusinessEmploymentService.Outcome workerOffer =
                    BusinessEmploymentService.sendOffer(data, ownerActor, businessId,
                            worker, workerName, workerPosition == null ? "" : workerPosition.id(),
                            "5", "false", now);
            final EmploymentContractRecord workerPending =
                    findOffer(data.business(businessId), worker);
            final BusinessEmploymentService.Outcome workerAccepted = workerPending == null
                    ? BusinessEmploymentService.Outcome.refused(
                            ErrorCode.INVALID_STATE, "no offer")
                    : BusinessEmploymentService.respondOffer(
                            data, workerActor, businessId, workerPending.id(), true, now);
            if (!workerOffer.ok() || workerPending == null || !workerAccepted.ok()) {
                failures.add("a written offer to the worker could not be accepted");
            }
            final EmploymentContractRecord workerContract =
                    activeStanding(data.business(businessId), worker);

            // Duplicate offers, one-role enforcement, CEO protection and tree cycles.
            ctx.checks++;
            if (BusinessEmploymentService.sendOffer(data, ownerActor, businessId,
                    worker, workerName, workerPosition == null ? "" : workerPosition.id(),
                    "5", "false", now).code() != ErrorCode.INVALID_STATE
                    || BusinessEmploymentService.sendOffer(data, ownerActor, businessId,
                            manager, managerName, managerPosition == null ? "" : managerPosition.id(),
                            "10", "false", now).code() != ErrorCode.INVALID_STATE) {
                failures.add("a duplicate offer or a second standing offer was accepted");
            }
            ctx.checks++;
            if (BusinessEmploymentService.createPosition(data, workerActor, businessId,
                    "Worker Made", workerPosition == null ? "" : workerPosition.id(),
                    "1", "", "0", now).code() != ErrorCode.FORBIDDEN) {
                failures.add("a position without positions.manage created another position");
            }
            ctx.checks++;
            if (BusinessEmploymentService.editPosition(data, ownerActor, businessId,
                    BusinessPositionRecord.CEO_POSITION_ID, "Chief", null, null, null, now).code()
                            != ErrorCode.INVALID_STATE
                    || BusinessEmploymentService.deletePosition(data, ownerActor, businessId,
                            BusinessPositionRecord.CEO_POSITION_ID, now).code()
                            != ErrorCode.INVALID_STATE
                    || BusinessEmploymentService.movePosition(data, ownerActor, businessId,
                            BusinessPositionRecord.CEO_POSITION_ID, "ceo", now).code()
                            != ErrorCode.INVALID_STATE) {
                failures.add("the CEO root could be edited, deleted or moved");
            }
            ctx.checks++;
            if (BusinessEmploymentService.deletePosition(data, ownerActor, businessId,
                    managerPosition == null ? "" : managerPosition.id(), now).code()
                            != ErrorCode.INVALID_STATE
                    || BusinessEmploymentService.deletePosition(data, ownerActor, businessId,
                            workerPosition == null ? "" : workerPosition.id(), now).code()
                            != ErrorCode.INVALID_STATE) {
                failures.add("a position with a child or a contract was deleted");
            }
            ctx.checks++;
            if (BusinessEmploymentService.movePosition(data, ownerActor, businessId,
                    managerPosition == null ? "" : managerPosition.id(),
                    workerPosition == null ? "" : workerPosition.id(), now).code()
                    != ErrorCode.INVALID_STATE) {
                failures.add("a position move that creates a cycle was accepted");
            }

            // Position page: true total, depth, edit flags and a block under the field budget.
            ctx.checks++;
            final BusinessEmploymentService.PositionPage positionPage =
                    BusinessEmploymentService.positionPage(
                            data, data.business(businessId), ownerActor, Set.of(), now, 0);
            final Map<String, String> positionFields = BusinessEmploymentService.positionFields(
                    data, data.business(businessId), ownerActor, Set.of(), now, 0);
            if (positionPage.total() != 3 || positionPage.rows().size() != 3
                    || positionPage.rows().get(0).depth() != 0
                    || !positionPage.rows().get(0).position().id()
                            .equals(BusinessPositionRecord.CEO_POSITION_ID)
                    || positionPage.rows().get(0).canEdit()
                    || !positionPage.rows().get(1).canEdit()
                    || positionFields.size() < 10
                    || GovFields.encode(positionFields).length() > GovFields.MAX_ENCODED) {
                failures.add("the position page is wrong or exceeds the field block budget");
            }



        ctx.business = business;
        ctx.businessId = businessId;
        ctx.managerPosition = managerPosition;
        ctx.managerPending = managerPending;
        ctx.workerPosition = workerPosition;
    }
}
