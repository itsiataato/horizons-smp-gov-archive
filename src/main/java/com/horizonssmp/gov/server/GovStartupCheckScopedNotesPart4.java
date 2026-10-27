package com.horizonssmp.gov.server;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.NoteKeys;
import com.horizonssmp.gov.common.NoteScope;
import com.horizonssmp.gov.common.records.BusinessRecord;
import com.horizonssmp.gov.common.records.NoteRecord;
import static com.horizonssmp.gov.server.GovStartupCheckScopedNotesSupport.findByText;

final class GovStartupCheckScopedNotesPart4 {

    private GovStartupCheckScopedNotesPart4() {
    }

    static void run(final GovStartupCheckScopedNotesContext ctx) {
        final List<String> failures = ctx.failures;
        final long now = ctx.now;
        final GovSavedData scratch = ctx.scratch;
        final UUID worker = ctx.worker;
        final UUID owner = ctx.owner;
        final UUID outsider = ctx.outsider;
        final BusinessRecord bakery = ctx.bakery;
        final BusinessRecord suspended = ctx.suspended;
        final ScopedNoteService.Actor ownerActor = ctx.ownerActor;
        final ScopedNoteService.Actor managerActor = ctx.managerActor;
        final ScopedNoteService.Actor workerActor = ctx.workerActor;
        final ScopedNoteService.Actor outsiderActor = ctx.outsiderActor;
        final ScopedNoteService.Actor manageActor = ctx.manageActor;

            // ------------------------------------------- business per-player notes

            ctx.checks++;
            final ScopedNoteService.Outcome ownerPlayerAdd = ScopedNoteService.add(
                    scratch, ownerActor, NoteScope.BUSINESS.wireId(), bakery.id(), worker,
                    "StartupCheck M11 Worker", "Worker observation.", now + 20);
            final NoteRecord ownerPlayerNote = findByText(
                    scratch, NoteScope.BUSINESS, bakery.id(), worker, "Worker observation.");
            if (!ownerPlayerAdd.ok() || ownerPlayerNote == null
                    || !worker.equals(ownerPlayerNote.targetUuid())) {
                failures.add("the owner could not add a per-player business note");
            }
            ctx.checks++;
            final ScopedNoteService.ListResult playerList = ScopedNoteService.list(
                    scratch, ownerActor, NoteScope.BUSINESS.wireId(), bakery.id(),
                    worker, "StartupCheck M11 Worker", 0);
            if (!playerList.ok() || playerList.page().total() != 1
                    || !"StartupCheck M11 Worker".equals(
                            playerList.page().rows().get(0).targetName())
                    || !playerList.scope().canPost() || !playerList.scope().canModerate()) {
                failures.add("the owner's per-player list is wrong");
            }
            ctx.checks++;
            if (!ScopedNoteService.list(scratch, managerActor, NoteScope.BUSINESS.wireId(),
                            bakery.id(), worker, "StartupCheck M11 Worker", 0).ok()
                    || !ScopedNoteService.list(scratch, manageActor, NoteScope.BUSINESS.wireId(),
                            bakery.id(), worker, "StartupCheck M11 Worker", 0).ok()) {
                failures.add("a manager or manage holder could not read per-player notes");
            }
            ctx.checks++;
            if (ScopedNoteService.list(scratch, workerActor, NoteScope.BUSINESS.wireId(),
                    bakery.id(), worker, "StartupCheck M11 Worker", 0).code()
                            != ErrorCode.FORBIDDEN
                    || ScopedNoteService.list(scratch, outsiderActor,
                            NoteScope.BUSINESS.wireId(), bakery.id(), worker,
                            "StartupCheck M11 Worker", 0).code() != ErrorCode.FORBIDDEN) {
                failures.add("a non-manager employee or an outsider could read per-player notes");
            }

            ctx.checks++;
            if (ScopedNoteService.add(scratch, workerActor, NoteScope.BUSINESS.wireId(),
                    bakery.id(), owner, "StartupCheck M11 Owner", "worker pp", now + 21)
                    .code() != ErrorCode.FORBIDDEN) {
                failures.add("a non-manager employee could add a per-player note");
            }
            ctx.checks++;
            if (ScopedNoteService.add(scratch, ownerActor, NoteScope.BUSINESS.wireId(),
                    bakery.id(), outsider, "StartupCheck M11 Outsider", "outsider target", now + 21)
                    .code() != ErrorCode.FORBIDDEN) {
                failures.add("a per-player note about a non-employee was accepted");
            }
            ctx.checks++;
            final ScopedNoteService.Outcome managerPlayerAdd = ScopedNoteService.add(
                    scratch, managerActor, NoteScope.BUSINESS.wireId(), bakery.id(), owner,
                    "StartupCheck M11 Owner", "Manager observation.", now + 22);
            final NoteRecord managerPlayerNote = findByText(
                    scratch, NoteScope.BUSINESS, bakery.id(), owner, "Manager observation.");
            if (!managerPlayerAdd.ok() || managerPlayerNote == null) {
                failures.add("a manager could not add a per-player note");
            }

            ctx.checks++;
            if (ownerPlayerNote == null || !ScopedNoteService.edit(
                    scratch, ownerActor, NoteScope.BUSINESS.wireId(), bakery.id(), null,
                    ownerPlayerNote.id(), "Owner corrected observation.", now + 23).ok()) {
                failures.add("the author could not edit a per-player note");
            }
            ctx.checks++;
            if (managerPlayerNote == null || ScopedNoteService.edit(
                    scratch, ownerActor, NoteScope.BUSINESS.wireId(), bakery.id(), null,
                    managerPlayerNote.id(), "owner edits another", now + 23).code()
                            != ErrorCode.FORBIDDEN) {
                failures.add("the owner could edit someone else's per-player note");
            }
            ctx.checks++;
            if (managerPlayerNote == null || !ScopedNoteService.edit(
                    scratch, managerActor, NoteScope.BUSINESS.wireId(), bakery.id(), null,
                    managerPlayerNote.id(), "Manager corrected observation.", now + 24).ok()) {
                failures.add("a manager could not edit their own per-player note");
            }

            final ScopedNoteService.Outcome suspensionDeleteAdd = ScopedNoteService.add(
                    scratch, ownerActor, NoteScope.BUSINESS.wireId(), bakery.id(), worker,
                    "StartupCheck M11 Worker", "Suspension delete fixture.", now + 24);
            final NoteRecord suspensionDeleteNote = findByText(
                    scratch, NoteScope.BUSINESS, bakery.id(), worker,
                    "Suspension delete fixture.");
            if (!suspensionDeleteAdd.ok() || suspensionDeleteNote == null) {
                failures.add("the suspension-delete fixture could not be created");
            }
            scratch.putBusiness(suspended);
            ctx.checks++;
            if (suspensionDeleteNote == null || !ScopedNoteService.delete(
                    scratch, ownerActor, NoteScope.BUSINESS.wireId(), bakery.id(), null,
                    suspensionDeleteNote.id()).ok()) {
                failures.add("moderation delete did not work on a suspended per-player note");
            }
            ctx.checks++;
            if (ownerPlayerNote == null || ScopedNoteService.edit(
                    scratch, ownerActor, NoteScope.BUSINESS.wireId(), bakery.id(), null,
                    ownerPlayerNote.id(), "suspended pp edit", now + 25).code()
                            != ErrorCode.FORBIDDEN) {
                failures.add("a suspended business accepted a per-player edit");
            }
            ctx.checks++;
            if (ScopedNoteService.add(scratch, managerActor, NoteScope.BUSINESS.wireId(),
                    bakery.id(), worker, "StartupCheck M11 Worker", "suspended pp", now + 25)
                    .code() != ErrorCode.FORBIDDEN) {
                failures.add("a suspended business accepted a per-player note");
            }
            ctx.checks++;
            if (!ScopedNoteService.list(scratch, ownerActor, NoteScope.BUSINESS.wireId(),
                    bakery.id(), worker, "StartupCheck M11 Worker", 0).ok()) {
                failures.add("a suspended business lost its per-player read");
            }
            scratch.putBusiness(bakery);

            ctx.checks++;
            if (ownerPlayerNote == null || ScopedNoteService.delete(
                    scratch, managerActor, NoteScope.BUSINESS.wireId(), bakery.id(), null,
                    ownerPlayerNote.id()).code() != ErrorCode.FORBIDDEN) {
                failures.add("a manager could delete someone else's per-player note");
            }
            ctx.checks++;
            if (managerPlayerNote == null || !ScopedNoteService.delete(
                    scratch, ownerActor, NoteScope.BUSINESS.wireId(), bakery.id(), null,
                    managerPlayerNote.id()).ok()) {
                failures.add("the owner could not delete a per-player note");
            }
            ctx.checks++;
            if (ownerPlayerNote == null || !ScopedNoteService.delete(
                    scratch, manageActor, NoteScope.BUSINESS.wireId(), bakery.id(), null,
                    ownerPlayerNote.id()).ok()) {
                failures.add("a business.manage holder could not delete a per-player note");
            }


    }
}
