package com.horizonssmp.gov.server;

import java.util.EnumSet;
import java.util.List;
import com.horizonssmp.gov.common.GovCapability;
import com.horizonssmp.gov.common.records.JobRecord;
import java.util.Map;
import java.util.UUID;
import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.GovCapabilities;
import com.horizonssmp.gov.common.NoteKeys;
import com.horizonssmp.gov.common.NoteScope;
import com.horizonssmp.gov.common.enums.BusinessRole;
import com.horizonssmp.gov.common.records.BusinessEmployee;
import com.horizonssmp.gov.common.records.BusinessRecord;
import com.horizonssmp.gov.common.records.NoteRecord;
import com.horizonssmp.gov.common.records.PayrollInfo;
import com.horizonssmp.gov.common.records.PlayerRecord;
import static com.horizonssmp.gov.server.GovStartupCheckScopedNotesSupport.fillScoped;
import static com.horizonssmp.gov.server.GovStartupCheckScopedNotesSupport.findByText;
import static com.horizonssmp.gov.server.GovStartupCheckScopedNotesSupport.scopedActor;

final class GovStartupCheckScopedNotesPart5 {

    private GovStartupCheckScopedNotesPart5() {
    }

    static void run(final GovStartupCheckScopedNotesContext ctx) {
        final List<String> failures = ctx.failures;
        final long now = ctx.now;
        final GovSavedData scratch = ctx.scratch;
        final UUID alice = ctx.alice;
        final UUID bob = ctx.bob;
        final UUID owner = ctx.owner;
        final UUID capWorker = ctx.capWorker;
        final UUID orphanMember = ctx.orphanMember;
        final BusinessRecord bakery = ctx.bakery;
        final ScopedNoteService.Actor ownerActor = ctx.ownerActor;
        final ScopedNoteService.Actor memberWriter = ctx.memberWriter;
        final ScopedNoteService.Actor jobManager = ctx.jobManager;

            // ------------------------------------------------------- cap fixtures

            final BusinessRecord targetCap = new BusinessRecord(
                    "b-m11-targetcap", "StartupCheck M11 Target Cap", owner, "shop",
                    BusinessRecord.STATUS_ACTIVE, now, owner, "",
                    List.of(new BusinessEmployee(capWorker, BusinessRole.WORKER.id(), now)),
                    PayrollInfo.NONE, "", "", 0L, null);
            scratch.putBusiness(targetCap);
            fillScoped(scratch, NoteScope.BUSINESS, targetCap.id(), capWorker, owner,
                    NoteKeys.MAX_BUSINESS_NOTES_PER_TARGET, now + 200);
            ctx.checks++;
            if (ScopedNoteService.add(scratch, ownerActor, NoteScope.BUSINESS.wireId(),
                    targetCap.id(), capWorker, "StartupCheck M11 Cap Worker",
                    "one too many", now + 300)
                    .code() != ErrorCode.INVALID_STATE) {
                failures.add("a business per-player note beyond its cap was accepted");
            }

            final BusinessRecord boardCap = new BusinessRecord(
                    "b-m11-boardcap", "StartupCheck M11 Board Cap", owner, "shop",
                    BusinessRecord.STATUS_ACTIVE, now, owner, "", List.of(),
                    PayrollInfo.NONE, "", "", 0L, null);
            scratch.putBusiness(boardCap);
            fillScoped(scratch, NoteScope.BUSINESS, boardCap.id(), null, owner,
                    NoteKeys.MAX_BUSINESS_BOARD_NOTES, now + 400);
            ctx.checks++;
            if (ScopedNoteService.add(scratch, ownerActor, NoteScope.BUSINESS.wireId(),
                    boardCap.id(), null, "", "one too many", now + 500)
                    .code() != ErrorCode.INVALID_STATE) {
                failures.add("a team-board note beyond its cap was accepted");
            }

            final GovSavedData ceiling = new GovSavedData();
            ceiling.seedIfNeeded(now, false);
            ceiling.putPlayer(PlayerRecord.blank(owner, "StartupCheck M11 Owner", now));
            ceiling.putBusiness(new BusinessRecord(
                    "b-m11-ceiling", "StartupCheck M11 Ceiling", owner, "shop",
                    BusinessRecord.STATUS_ACTIVE, now, owner, "", List.of(),
                    PayrollInfo.NONE, "", "", 0L, null));
            fillScoped(ceiling, NoteScope.BUSINESS, "b-m11-ceiling", null, owner,
                    NoteKeys.MAX_SCOPED_NOTES, now + 600);
            ctx.checks++;
            if (ceiling.scopedNoteCount() != NoteKeys.MAX_SCOPED_NOTES) {
                failures.add("the total scoped-note fixture did not reach the ceiling");
            }
            ctx.checks++;
            final ScopedNoteService.Outcome overTotal = ScopedNoteService.add(
                    ceiling, ownerActor, NoteScope.BUSINESS.wireId(), "b-m11-ceiling",
                    null, "", "one too many", now + 700);
            if (overTotal.code() != ErrorCode.INVALID_STATE
                    || !overTotal.message().contains(Integer.toString(NoteKeys.MAX_SCOPED_NOTES))) {
                failures.add("a scoped note beyond the total ceiling was accepted or misreported");
            }

            // --------------------------------------------- cross-scope id refusal

            ctx.checks++;
            final ScopedNoteService.Outcome probeAdd = ScopedNoteService.add(
                    scratch, memberWriter, NoteScope.JOB.wireId(),
                    GovCapabilities.POLICE_JOB_ID, alice, "StartupCheck M11 Alice",
                    "Cross-scope probe.", now + 30);
            final NoteRecord probeNote = findByText(
                    scratch, NoteScope.JOB, GovCapabilities.POLICE_JOB_ID, alice,
                    "Cross-scope probe.");
            if (!probeAdd.ok() || probeNote == null) {
                failures.add("the cross-scope probe note could not be created");
            }
            ctx.checks++;
            if (probeNote == null || ScopedNoteService.edit(
                    scratch, memberWriter, NoteScope.BUSINESS.wireId(), bakery.id(), null,
                    probeNote.id(), "wrong scope", now + 31).code() != ErrorCode.BAD_NOTE) {
                failures.add("a job note id was usable through a business scope");
            }
            ctx.checks++;
            final ScopedNoteService.Outcome judgeProbe = probeNote == null
                    ? null
                    : ScopedNoteService.delete(
                            scratch, jobManager, NoteScope.JOB.wireId(),
                            GovCapabilities.JUDGE_JOB_ID, null, probeNote.id());
            if (probeNote == null || judgeProbe == null
                    || judgeProbe.code() != ErrorCode.BAD_NOTE) {
                failures.add("a job note id was usable through another job id (got "
                        + (judgeProbe == null ? "no note" : judgeProbe.code())
                        + ", note scope=" + (probeNote == null ? "-" : probeNote.scopeId())
                        + ", judge job=" + (scratch.job(GovCapabilities.JUDGE_JOB_ID) != null)
                        + ")");
            }
            ctx.checks++;
            if (probeNote == null || ScopedNoteService.edit(
                    scratch, memberWriter, NoteScope.JOB.wireId(),
                    GovCapabilities.POLICE_JOB_ID, bob, probeNote.id(),
                    "wrong target", now + 31).code() != ErrorCode.BAD_NOTE) {
                failures.add("a job note id was usable with another target");
            }
            ctx.checks++;
            if (ScopedNoteService.delete(scratch, memberWriter, NoteScope.BUSINESS.wireId(),
                    "b-m11-missing", null, "n-m11-missing")
                    .code() != ErrorCode.BAD_NOTE) {
                failures.add("a note id through a missing business scope did not report BAD_NOTE");
            }
            ctx.checks++;
            if (!PersonService.noSuchNoteMessage("n-m11-missing").equals(
                    ScopedNoteService.edit(scratch, memberWriter, NoteScope.BUSINESS.wireId(),
                            "b-m11-missing", null, "n-m11-missing", "x", now).message())) {
                failures.add("the cross-scope refusal does not use the unknown-note wording");
            }

            // --------------------------------------------------- unknown scopes

            ctx.checks++;
            if (ScopedNoteService.list(scratch, memberWriter, NoteScope.GLOBAL.wireId(),
                    GovCapabilities.POLICE_JOB_ID, null, "", 0).code()
                            != ErrorCode.BAD_PAYLOAD
                    || ScopedNoteService.list(scratch, memberWriter, "job", "", null, "", 0)
                            .code() != ErrorCode.BAD_PAYLOAD
                    || ScopedNoteService.list(scratch, memberWriter, "nonsense", "police",
                            null, "", 0).code() != ErrorCode.BAD_PAYLOAD) {
                failures.add("a scoped list accepted GLOBAL, a blank id or an unknown scope");
            }

            // ---------------------------------------------------- orphaned jobs

            scratch.putJob(new JobRecord(
                    "m11-orphan", "StartupCheck M11 Orphan", "", "M11", "Member",
                    false, EnumSet.noneOf(GovCapability.class), now, null));
            scratch.putPlayer(PlayerRecord.blank(orphanMember, "StartupCheck M11 Orphan", now)
                    .withJob("m11-orphan", "Member", now, null, true));
            final ScopedNoteService.Actor orphanWriter =
                    scopedActor(orphanMember, "StartupCheck M11 Orphan", GovCapability.NOTE_WRITE);
            ctx.checks++;
            final ScopedNoteService.Outcome orphanAdd = ScopedNoteService.add(
                    scratch, orphanWriter, NoteScope.JOB.wireId(), "m11-orphan", alice,
                    "StartupCheck M11 Alice", "Orphan observation.", now + 40);
            final NoteRecord orphanNote = findByText(
                    scratch, NoteScope.JOB, "m11-orphan", alice, "Orphan observation.");
            if (!orphanAdd.ok() || orphanNote == null) {
                failures.add("a job note could not be created for the orphan test");
            }
            scratch.putPlayer(PlayerRecord.blank(orphanMember, "StartupCheck M11 Orphan", now)
                    .withJob(GovCapabilities.CITIZEN_JOB_ID, "Citizen", now, null, true));
            ctx.checks++;
            if (!scratch.removeJob("m11-orphan")) {
                failures.add("the orphan-test job could not be deleted after its holder left");
            }
            ctx.checks++;
            if (orphanNote == null || scratch.note(orphanNote.id()) == null) {
                failures.add("deleting a job also deleted its notes");
            }
            ctx.checks++;
            if (ScopedNoteService.list(scratch, jobManager, NoteScope.JOB.wireId(),
                    "m11-orphan", null, "", 0).code() != ErrorCode.BAD_JOB) {
                failures.add("an orphaned job scope was still listable");
            }
            ctx.checks++;
            if (orphanNote == null || ScopedNoteService.delete(
                    scratch, jobManager, NoteScope.JOB.wireId(), "m11-orphan", null,
                    orphanNote.id()).code() != ErrorCode.BAD_NOTE) {
                failures.add("an orphaned job note was reachable by note id");
            }
            ctx.checks++;
            if (ScopedNoteService.scopes(scratch, jobManager).stream()
                    .anyMatch(view -> "m11-orphan".equals(view.id()))) {
                failures.add("an orphaned job was still offered by NOTE_SCOPES");
            }


    }
}
