package com.horizonssmp.gov.server;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.GovCapability;
import com.horizonssmp.gov.common.NoteKeys;
import com.horizonssmp.gov.common.NoteScope;
import com.horizonssmp.gov.common.enums.BusinessRole;
import com.horizonssmp.gov.common.records.BusinessEmployee;
import com.horizonssmp.gov.common.records.BusinessRecord;
import com.horizonssmp.gov.common.records.NoteRecord;
import com.horizonssmp.gov.common.records.PayrollInfo;
import static com.horizonssmp.gov.server.GovStartupCheckScopedNotesSupport.findByText;
import static com.horizonssmp.gov.server.GovStartupCheckScopedNotesSupport.scopedActor;

final class GovStartupCheckScopedNotesPart3 {

    private GovStartupCheckScopedNotesPart3() {
    }

    static void run(final GovStartupCheckScopedNotesContext ctx) {
        final List<String> failures = ctx.failures;
        final long now = ctx.now;
        final GovSavedData scratch = ctx.scratch;
        final UUID owner = ctx.owner;
        final UUID manager = ctx.manager;
        final UUID worker = ctx.worker;
        final UUID outsider = ctx.outsider;
        final UUID manageHolder = ctx.manageHolder;
        final UUID unrelatedOwner = ctx.unrelatedOwner;

            // ---------------------------------------------------- business scopes

            final BusinessRecord bakery = new BusinessRecord(
                    "b-m11-bakery", "StartupCheck M11 Bakery", owner, "shop",
                    BusinessRecord.STATUS_ACTIVE, now, owner, "",
                    List.of(new BusinessEmployee(manager, BusinessRole.MANAGER.id(), now),
                            new BusinessEmployee(worker, BusinessRole.WORKER.id(), now)),
                    PayrollInfo.NONE, "existing card note", "", 0L, null);
            scratch.putBusiness(bakery);
            scratch.putBusiness(new BusinessRecord(
                    "b-m11-unrelated", "StartupCheck M11 Unrelated", unrelatedOwner, "shop",
                    BusinessRecord.STATUS_ACTIVE, now, unrelatedOwner, "", List.of(),
                    PayrollInfo.NONE, "", "", 0L, null));

            final ScopedNoteService.Actor ownerActor =
                    scopedActor(owner, "StartupCheck M11 Owner");
            final ScopedNoteService.Actor managerActor =
                    scopedActor(manager, "StartupCheck M11 Manager");
            final ScopedNoteService.Actor workerActor =
                    scopedActor(worker, "StartupCheck M11 Worker");
            final ScopedNoteService.Actor outsiderActor =
                    scopedActor(outsider, "StartupCheck M11 Outsider");
            final ScopedNoteService.Actor manageActor =
                    scopedActor(manageHolder, "StartupCheck M11 Registrar",
                            GovCapability.BUSINESS_MANAGE);

            ctx.checks++;
            if (!ScopedNoteService.boardCanRead(bakery, ownerActor)
                    || !ScopedNoteService.boardCanRead(bakery, managerActor)
                    || !ScopedNoteService.boardCanRead(bakery, workerActor)
                    || ScopedNoteService.boardCanRead(bakery, outsiderActor)
                    || !ScopedNoteService.boardCanRead(bakery, manageActor)) {
                failures.add("a team-board read cell is wrong");
            }
            ctx.checks++;
            if (!ScopedNoteService.boardCanPost(bakery, ownerActor)
                    || !ScopedNoteService.boardCanPost(bakery, workerActor)
                    || ScopedNoteService.boardCanPost(bakery, outsiderActor)) {
                failures.add("a team-board post cell is wrong");
            }
            ctx.checks++;
            if (!ScopedNoteService.perPlayerCanRead(bakery, ownerActor)
                    || !ScopedNoteService.perPlayerCanRead(bakery, managerActor)
                    || ScopedNoteService.perPlayerCanRead(bakery, workerActor)
                    || ScopedNoteService.perPlayerCanRead(bakery, outsiderActor)
                    || !ScopedNoteService.perPlayerCanRead(bakery, manageActor)) {
                failures.add("a per-player business read cell is wrong");
            }
            ctx.checks++;
            if (!ScopedNoteService.perPlayerCanPost(bakery, ownerActor, worker)
                    || !ScopedNoteService.perPlayerCanPost(bakery, managerActor, owner)
                    || ScopedNoteService.perPlayerCanPost(bakery, workerActor, owner)
                    || ScopedNoteService.perPlayerCanPost(bakery, ownerActor, outsider)) {
                failures.add("a per-player business post cell is wrong");
            }

            ctx.checks++;
            final ScopedNoteService.Outcome boardAdd = ScopedNoteService.add(
                    scratch, ownerActor, NoteScope.BUSINESS.wireId(), bakery.id(), null, "",
                    "Team board note.", now + 1);
            if (!boardAdd.ok()) {
                failures.add("the owner could not post on an ACTIVE team board");
            }
            final NoteRecord boardNote = findByText(
                    scratch, NoteScope.BUSINESS, bakery.id(), null, "Team board note.");
            ctx.checks++;
            if (boardNote == null || boardNote.targetUuid() != null
                    || !GovSavedData.isScope(boardNote, NoteScope.BUSINESS)
                    || !bakery.id().equals(boardNote.scopeId())) {
                failures.add("the team-board note is not a scoped note with an empty target");
            }

            ctx.checks++;
            final ScopedNoteService.ListResult boardList = ScopedNoteService.list(
                    scratch, workerActor, NoteScope.BUSINESS.wireId(), bakery.id(),
                    null, "", 0);
            if (!boardList.ok() || boardList.page().total() != 1
                    || !"StartupCheck M11 Bakery".equals(boardList.scope().label())
                    || !boardList.scope().canPost() || boardList.scope().canModerate()) {
                failures.add("an employee's team-board list is wrong");
            }
            ctx.checks++;
            final Map<String, String> boardFields =
                    ScopedNoteService.listFields(boardList);
            if (!NoteScope.BUSINESS.wireId().equals(boardFields.get(NoteKeys.SCOPE_TYPE))
                    || !bakery.id().equals(boardFields.get(NoteKeys.SCOPE_ID))
                    || !"StartupCheck M11 Bakery".equals(boardFields.get(NoteKeys.SCOPE_LABEL))
                    || !"true".equals(boardFields.get(NoteKeys.CAN_POST_NOTES))
                    || !"false".equals(boardFields.get(NoteKeys.CAN_MODERATE_NOTES))
                    || !"true".equals(boardFields.get(NoteKeys.CAN_MANAGE))
                    || !"1".equals(boardFields.get(NoteKeys.TOTAL))
                    || !"".equals(boardFields.get(NoteKeys.ROW_PREFIX + "0"
                            + NoteKeys.ROW_TARGET))
                    || !"".equals(boardFields.get(NoteKeys.ROW_PREFIX + "0"
                            + NoteKeys.ROW_TARGET_UUID))) {
                failures.add("the team-board field block is missing a scope, flag or empty target");
            }
            ctx.checks++;
            if (ScopedNoteService.list(scratch, outsiderActor, NoteScope.BUSINESS.wireId(),
                    bakery.id(), null, "", 0).code() != ErrorCode.FORBIDDEN) {
                failures.add("an outsider could read a business team board");
            }
            ctx.checks++;
            if (!ScopedNoteService.add(scratch, managerActor, NoteScope.BUSINESS.wireId(),
                    bakery.id(), null, "", "Manager board note.", now + 2).ok()) {
                failures.add("a manager could not post on an ACTIVE team board");
            }

            ctx.checks++;
            if (ScopedNoteService.edit(scratch, workerActor, NoteScope.BUSINESS.wireId(),
                    bakery.id(), null, boardNote == null ? "" : boardNote.id(),
                    "worker edit", now + 3).code() != ErrorCode.FORBIDDEN) {
                failures.add("a non-author employee could edit a team-board note");
            }
            ctx.checks++;
            if (boardNote == null || !ScopedNoteService.edit(
                    scratch, ownerActor, NoteScope.BUSINESS.wireId(), bakery.id(), null,
                    boardNote.id(), "Owner corrected board note.", now + 3).ok()) {
                failures.add("the author could not edit an ACTIVE team-board note");
            }

            final BusinessRecord suspended = bakery.withStatus(
                    BusinessRecord.STATUS_SUSPENDED, "StartupCheck suspension", owner, now);
            scratch.putBusiness(suspended);
            ctx.checks++;
            if (!ScopedNoteService.boardCanRead(suspended, workerActor)
                    || ScopedNoteService.boardCanPost(suspended, ownerActor)
                    || ScopedNoteService.boardCanPost(suspended, manageActor)) {
                failures.add("a suspended business did not keep board read or lost its post block");
            }
            ctx.checks++;
            if (ScopedNoteService.add(scratch, ownerActor, NoteScope.BUSINESS.wireId(),
                    bakery.id(), null, "", "suspended post", now + 4)
                    .code() != ErrorCode.FORBIDDEN) {
                failures.add("a suspended business accepted a team-board post");
            }
            ctx.checks++;
            if (boardNote == null || ScopedNoteService.edit(
                    scratch, ownerActor, NoteScope.BUSINESS.wireId(), bakery.id(), null,
                    boardNote.id(), "suspended edit", now + 4).code() != ErrorCode.FORBIDDEN) {
                failures.add("a suspended business accepted a team-board edit");
            }
            ctx.checks++;
            if (!ScopedNoteService.list(scratch, workerActor, NoteScope.BUSINESS.wireId(),
                    bakery.id(), null, "", 0).ok()) {
                failures.add("a suspended business lost its team-board read");
            }

            final NoteRecord suspendedManagerNote = findByText(
                    scratch, NoteScope.BUSINESS, bakery.id(), null, "Manager board note.");
            ctx.checks++;
            if (suspendedManagerNote == null || !ScopedNoteService.delete(
                    scratch, managerActor, NoteScope.BUSINESS.wireId(), bakery.id(), null,
                    suspendedManagerNote.id()).ok()) {
                failures.add("a manager could not moderate-delete a board note while suspended");
            }
            scratch.putBusiness(bakery);

            ctx.checks++;
            if (!ScopedNoteService.add(scratch, workerActor, NoteScope.BUSINESS.wireId(),
                    bakery.id(), null, "", "Worker board note.", now + 5).ok()) {
                failures.add("an employee could not post on an ACTIVE team board");
            }
            final NoteRecord workerBoardNote = findByText(
                    scratch, NoteScope.BUSINESS, bakery.id(), null, "Worker board note.");
            ctx.checks++;
            if (workerBoardNote == null || !ScopedNoteService.delete(
                    scratch, workerActor, NoteScope.BUSINESS.wireId(), bakery.id(), null,
                    workerBoardNote.id()).ok()) {
                failures.add("the author could not delete their own team-board note");
            }
            ctx.checks++;
            final ScopedNoteService.Outcome ownerBoardAdd = ScopedNoteService.add(
                    scratch, ownerActor, NoteScope.BUSINESS.wireId(), bakery.id(), null, "",
                    "Owner board note.", now + 6);
            final NoteRecord ownerBoardNote = findByText(
                    scratch, NoteScope.BUSINESS, bakery.id(), null, "Owner board note.");
            if (!ownerBoardAdd.ok() || ownerBoardNote == null) {
                failures.add("the owner could not add a second board note");
            }
            ctx.checks++;
            if (ownerBoardNote == null || ScopedNoteService.delete(
                    scratch, workerActor, NoteScope.BUSINESS.wireId(), bakery.id(), null,
                    ownerBoardNote.id()).code() != ErrorCode.FORBIDDEN) {
                failures.add("a non-author employee could delete someone else's board note");
            }
            ctx.checks++;
            if (ownerBoardNote == null || !ScopedNoteService.delete(
                    scratch, managerActor, NoteScope.BUSINESS.wireId(), bakery.id(), null,
                    ownerBoardNote.id()).ok()) {
                failures.add("a manager could not moderate-delete a board note");
            }
            ctx.checks++;
            final ScopedNoteService.Outcome manageBoardAdd = ScopedNoteService.add(
                    scratch, manageActor, NoteScope.BUSINESS.wireId(), bakery.id(), null, "",
                    "Registrar board note.", now + 7);
            final NoteRecord manageBoardNote = findByText(
                    scratch, NoteScope.BUSINESS, bakery.id(), null, "Registrar board note.");
            if (!manageBoardAdd.ok() || manageBoardNote == null) {
                failures.add("a business.manage holder could not add a board note");
            }
            ctx.checks++;
            if (manageBoardNote == null || !ScopedNoteService.delete(
                    scratch, manageActor, NoteScope.BUSINESS.wireId(), bakery.id(), null,
                    manageBoardNote.id()).ok()) {
                failures.add("a business.manage holder could not moderate-delete a board note");
            }

            ScopedNoteService.add(scratch, workerActor, NoteScope.BUSINESS.wireId(),
                    bakery.id(), null, "", "Closed board note.", now + 9);
            final NoteRecord closedBoardNote = findByText(
                    scratch, NoteScope.BUSINESS, bakery.id(), null, "Closed board note.");
            final BusinessRecord closed = bakery.withStatus(
                    BusinessRecord.STATUS_CLOSED, "", owner, now);
            scratch.putBusiness(closed);
            ctx.checks++;
            if (!ScopedNoteService.boardCanRead(closed, workerActor)
                    || ScopedNoteService.boardCanPost(closed, workerActor)) {
                failures.add("a closed business did not keep board read or lost its post block");
            }
            ctx.checks++;
            if (ScopedNoteService.add(scratch, ownerActor, NoteScope.BUSINESS.wireId(),
                    bakery.id(), null, "", "closed post", now + 8)
                    .code() != ErrorCode.FORBIDDEN) {
                failures.add("a closed business accepted a team-board post");
            }
            ctx.checks++;
            if (closedBoardNote == null || !ScopedNoteService.delete(
                    scratch, ownerActor, NoteScope.BUSINESS.wireId(), bakery.id(), null,
                    closedBoardNote.id()).ok()) {
                failures.add("moderation delete did not work on a closed business board");
            }
            scratch.putBusiness(bakery);



        ctx.bakery = bakery;
        ctx.suspended = suspended;
        ctx.ownerActor = ownerActor;
        ctx.managerActor = managerActor;
        ctx.workerActor = workerActor;
        ctx.outsiderActor = outsiderActor;
        ctx.manageActor = manageActor;
    }
}
