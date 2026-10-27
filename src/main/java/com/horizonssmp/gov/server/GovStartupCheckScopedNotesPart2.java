package com.horizonssmp.gov.server;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.GovCapability;
import com.horizonssmp.gov.common.GovCapabilities;
import com.horizonssmp.gov.common.NoteKeys;
import com.horizonssmp.gov.common.NoteScope;
import com.horizonssmp.gov.common.PersonKeys;
import com.horizonssmp.gov.common.records.NoteRecord;
import com.horizonssmp.gov.common.records.PlayerRecord;
import static com.horizonssmp.gov.server.GovStartupCheckScopedNotesSupport.findByText;
import static com.horizonssmp.gov.server.GovStartupCheckScopedNotesSupport.scopedActor;

final class GovStartupCheckScopedNotesPart2 {

    private GovStartupCheckScopedNotesPart2() {
    }

    static void run(final GovStartupCheckScopedNotesContext ctx) {
        final List<String> failures = ctx.failures;
        final long now = ctx.now;
        final UUID alice = ctx.alice;
        final UUID bob = ctx.bob;
        final UUID carol = ctx.carol;
        final UUID dave = ctx.dave;
        final UUID owner = ctx.owner;
        final UUID manager = ctx.manager;
        final UUID worker = ctx.worker;
        final UUID outsider = ctx.outsider;
        final UUID manageHolder = ctx.manageHolder;
        final UUID unrelatedOwner = ctx.unrelatedOwner;
        final NoteRecord future = ctx.future;

            // ------------------------------------------------------- job scopes

            final GovSavedData scratch = ctx.scratch;
            scratch.seedIfNeeded(now, false);
            scratch.putPlayer(PlayerRecord.blank(alice, "StartupCheck M11 Alice", now)
                    .withJob(GovCapabilities.POLICE_JOB_ID, "Officer", now, null, true));
            scratch.putPlayer(PlayerRecord.blank(bob, "StartupCheck M11 Bob", now)
                    .withJob(GovCapabilities.POLICE_JOB_ID, "Officer", now, null, true));
            scratch.putPlayer(PlayerRecord.blank(carol, "StartupCheck M11 Carol", now));
            scratch.putPlayer(PlayerRecord.blank(dave, "StartupCheck M11 Dave", now));
            scratch.putPlayer(PlayerRecord.blank(owner, "StartupCheck M11 Owner", now));
            scratch.putPlayer(PlayerRecord.blank(manager, "StartupCheck M11 Manager", now));
            scratch.putPlayer(PlayerRecord.blank(worker, "StartupCheck M11 Worker", now));
            scratch.putPlayer(PlayerRecord.blank(outsider, "StartupCheck M11 Outsider", now));
            scratch.putPlayer(PlayerRecord.blank(manageHolder, "StartupCheck M11 Registrar", now));
            scratch.putPlayer(PlayerRecord.blank(unrelatedOwner, "StartupCheck M11 Stranger", now));

            final ScopedNoteService.Actor memberReader =
                    scopedActor(alice, "StartupCheck M11 Alice");
            final ScopedNoteService.Actor memberWriter = ctx.memberWriter =
                    scopedActor(alice, "StartupCheck M11 Alice", GovCapability.NOTE_WRITE);
            final ScopedNoteService.Actor bobWriter =
                    scopedActor(bob, "StartupCheck M11 Bob", GovCapability.NOTE_WRITE);
            final ScopedNoteService.Actor nonMemberWriter =
                    scopedActor(carol, "StartupCheck M11 Carol", GovCapability.NOTE_WRITE);
            final ScopedNoteService.Actor jobManager = ctx.jobManager =
                    scopedActor(carol, "StartupCheck M11 Carol", GovCapability.JOB_MANAGE);
            final ScopedNoteService.Actor jobViewer =
                    scopedActor(carol, "StartupCheck M11 Carol", GovCapability.NOTE_VIEW_ALL);

            ctx.checks++;
            if (!ScopedNoteService.jobMember(scratch, memberReader,
                            GovCapabilities.POLICE_JOB_ID)
                    || ScopedNoteService.jobMember(scratch, nonMemberWriter,
                            GovCapabilities.POLICE_JOB_ID)) {
                failures.add("job membership is wrong for a member or a non-member");
            }
            ctx.checks++;
            if (!ScopedNoteService.jobCanRead(scratch, memberReader,
                            GovCapabilities.POLICE_JOB_ID)
                    || ScopedNoteService.jobCanRead(scratch, nonMemberWriter,
                            GovCapabilities.POLICE_JOB_ID)
                    || !ScopedNoteService.jobCanRead(scratch, jobManager,
                            GovCapabilities.POLICE_JOB_ID)
                    || !ScopedNoteService.jobCanRead(scratch, jobViewer,
                            GovCapabilities.POLICE_JOB_ID)) {
                failures.add("a job read cell is wrong");
            }
            ctx.checks++;
            if (!ScopedNoteService.jobCanPost(scratch, memberWriter,
                            GovCapabilities.POLICE_JOB_ID)
                    || ScopedNoteService.jobCanPost(scratch, memberReader,
                            GovCapabilities.POLICE_JOB_ID)
                    || ScopedNoteService.jobCanPost(scratch, nonMemberWriter,
                            GovCapabilities.POLICE_JOB_ID)) {
                failures.add("a job post cell is wrong");
            }
            ctx.checks++;
            if (!ScopedNoteService.jobCanModerate(scratch, memberWriter,
                            GovCapabilities.POLICE_JOB_ID)
                    || !ScopedNoteService.jobCanModerate(scratch, jobManager,
                            GovCapabilities.POLICE_JOB_ID)
                    || ScopedNoteService.jobCanModerate(scratch, jobViewer,
                            GovCapabilities.POLICE_JOB_ID)) {
                failures.add("a job delete cell is wrong");
            }

            ctx.checks++;
            final ScopedNoteService.Outcome jobAdd = ScopedNoteService.add(
                    scratch, memberWriter, NoteScope.JOB.wireId(),
                    GovCapabilities.POLICE_JOB_ID, alice, "StartupCheck M11 Alice",
                    "Job observation.", now);
            if (!jobAdd.ok()) {
                failures.add("a member with note.write could not add a job note");
            }
            final List<NoteRecord> policeNotes =
                    scratch.notesInScope(NoteScope.JOB, GovCapabilities.POLICE_JOB_ID);
            ctx.checks++;
            final NoteRecord jobNote = policeNotes.isEmpty() ? null : policeNotes.get(0);
            if (jobNote == null || !GovSavedData.isScope(jobNote, NoteScope.JOB)
                    || !GovCapabilities.POLICE_JOB_ID.equals(jobNote.scopeId())
                    || !alice.equals(jobNote.targetUuid())
                    || !alice.equals(jobNote.authorUuid())) {
                failures.add("the added job note does not carry its scope, target and author");
            }

            // The global views must not see the job note, and the person's global count is zero.
            ctx.checks++;
            if (!scratch.notesFor(alice).isEmpty() || !scratch.globalNotes().isEmpty()) {
                failures.add("a job note leaked into the GLOBAL note lookup");
            }
            ctx.checks++;
            if (PersonService.notesPage(scratch, alice, 0).total() != 0
                    || PersonService.notesPage(scratch, null, 0).total() != 0) {
                failures.add("a job note leaked into a GLOBAL note page");
            }
            ctx.checks++;
            final PersonService.Search jobSearch =
                    PersonService.search(scratch, List.of(), "StartupCheck M11 Alice");
            if (jobSearch.total() != 1 || jobSearch.hits().get(0).noteCount() != 0) {
                failures.add("a job note leaked into a person's search note count");
            }
            ctx.checks++;
            if (!"0".equals(PersonService.detail(
                    scratch, alice, "StartupCheck M11 Alice", false,
                    EnumSet.allOf(GovCapability.class)).get(PersonKeys.NOTES))) {
                failures.add("a job note leaked into a person's card note count");
            }

            ctx.checks++;
            scratch.putNote(future);
            if (!scratch.globalNotes().isEmpty() || !scratch.notesFor(bob).isEmpty()
                    || scratch.notesInScope(NoteScope.JOB, GovCapabilities.POLICE_JOB_ID)
                            .stream().anyMatch(note -> note.id().equals(future.id()))) {
                failures.add("an unknown scope was listed or counted by a known scope");
            }
            ctx.checks++;
            if (scratch.scopedNoteCount() != 1 || scratch.storedScopedNoteCount() != 2) {
                failures.add("an unknown scope was counted as a known scoped note");
            }

            ctx.checks++;
            final ScopedNoteService.ListResult memberList = ScopedNoteService.list(
                    scratch, memberReader, NoteScope.JOB.wireId(),
                    GovCapabilities.POLICE_JOB_ID, null, "", 0);
            if (!memberList.ok() || memberList.page().total() != 1
                    || memberList.page().rows().size() != 1
                    || !"Police".equals(memberList.scope().label())
                    || !"member".equals(memberList.scope().role())
                    || memberList.scope().canPost()) {
                failures.add("a member's job note list is wrong or exposed a write flag");
            }
            ctx.checks++;
            if (!"StartupCheck M11 Alice".equals(
                    memberList.page().rows().get(0).targetName())) {
                failures.add("a job note row lost its target display name");
            }
            ctx.checks++;
            if (ScopedNoteService.list(scratch, nonMemberWriter, NoteScope.JOB.wireId(),
                    GovCapabilities.POLICE_JOB_ID, null, "", 0).code() != ErrorCode.FORBIDDEN) {
                failures.add("a non-member could list a job's notes");
            }
            ctx.checks++;
            if (!ScopedNoteService.list(scratch, jobManager, NoteScope.JOB.wireId(),
                            GovCapabilities.POLICE_JOB_ID, null, "", 0).ok()
                    || !ScopedNoteService.list(scratch, jobViewer, NoteScope.JOB.wireId(),
                            GovCapabilities.POLICE_JOB_ID, null, "", 0).ok()) {
                failures.add("job.manage or note.view.all could not read a job's notes");
            }

            ctx.checks++;
            if (ScopedNoteService.add(scratch, memberReader, NoteScope.JOB.wireId(),
                    GovCapabilities.POLICE_JOB_ID, bob, "StartupCheck M11 Bob", "no write", now)
                    .code() != ErrorCode.FORBIDDEN) {
                failures.add("a member without note.write could post a job note");
            }
            ctx.checks++;
            if (ScopedNoteService.add(scratch, nonMemberWriter, NoteScope.JOB.wireId(),
                    GovCapabilities.POLICE_JOB_ID, bob, "StartupCheck M11 Bob", "outsider", now)
                    .code() != ErrorCode.FORBIDDEN) {
                failures.add("a non-member with note.write could post a job note");
            }
            ctx.checks++;
            if (ScopedNoteService.add(scratch, jobManager, NoteScope.JOB.wireId(),
                    GovCapabilities.POLICE_JOB_ID, bob, "StartupCheck M11 Bob", "supervisor", now)
                    .code() != ErrorCode.FORBIDDEN) {
                failures.add("job.manage alone could post a job note");
            }
            ctx.checks++;
            if (ScopedNoteService.add(scratch, memberWriter, NoteScope.JOB.wireId(),
                    GovCapabilities.POLICE_JOB_ID, null, "", "no target", now)
                    .code() != ErrorCode.BAD_PAYLOAD) {
                failures.add("a job note without a target was accepted");
            }

            ctx.checks++;
            if (jobNote == null || !ScopedNoteService.edit(
                    scratch, bobWriter, NoteScope.JOB.wireId(),
                    GovCapabilities.POLICE_JOB_ID, null, jobNote.id(),
                    "Corrected job observation.", now + 1).ok()) {
                failures.add("a note.write member could not edit a job note");
            }
            final NoteRecord jobEdited = jobNote == null ? null : scratch.note(jobNote.id());
            ctx.checks++;
            if (jobEdited == null || !alice.equals(jobEdited.authorUuid())
                    || jobEdited.editedAt() != now + 1
                    || !"Corrected job observation.".equals(jobEdited.text())) {
                failures.add("editing a job note lost its original author or edit stamp");
            }
            ctx.checks++;
            if (jobNote == null || ScopedNoteService.edit(scratch, jobManager,
                    NoteScope.JOB.wireId(), GovCapabilities.POLICE_JOB_ID, null, jobNote.id(),
                    "supervisor edit", now + 2).code() != ErrorCode.FORBIDDEN) {
                failures.add("job.manage without note.write could edit a job note");
            }
            ctx.checks++;
            if (jobNote == null || ScopedNoteService.edit(scratch, nonMemberWriter,
                    NoteScope.JOB.wireId(), GovCapabilities.POLICE_JOB_ID, null, jobNote.id(),
                    "outsider edit", now + 2).code() != ErrorCode.FORBIDDEN) {
                failures.add("a non-member could edit a job note");
            }
            ctx.checks++;
            if (jobNote == null || ScopedNoteService.edit(scratch, memberReader,
                    NoteScope.JOB.wireId(), GovCapabilities.POLICE_JOB_ID, null, jobNote.id(),
                    "reader edit", now + 2).code() != ErrorCode.FORBIDDEN) {
                failures.add("a member without note.write could edit a job note");
            }

            ctx.checks++;
            if (jobNote == null || ScopedNoteService.delete(scratch, jobViewer,
                    NoteScope.JOB.wireId(), GovCapabilities.POLICE_JOB_ID, null, jobNote.id())
                    .code() != ErrorCode.FORBIDDEN) {
                failures.add("note.view.all alone could delete a job note");
            }
            ctx.checks++;
            if (jobNote == null || !ScopedNoteService.delete(scratch, jobManager,
                    NoteScope.JOB.wireId(), GovCapabilities.POLICE_JOB_ID, null, jobNote.id())
                    .ok()) {
                failures.add("job.manage could not moderate-delete a job note");
            }

            // Per-(job, target) cap: the twentieth is accepted, the twenty-first is refused.
            for (int i = 0; i < NoteKeys.MAX_JOB_NOTES_PER_TARGET; i++) {
                final ScopedNoteService.Outcome filled = ScopedNoteService.add(
                        scratch, memberWriter, NoteScope.JOB.wireId(),
                        GovCapabilities.POLICE_JOB_ID, bob, "StartupCheck M11 Bob",
                        "job cap filler " + i, now + 10 + i);
                if (!filled.ok()) {
                    failures.add("filling a job's per-person notes failed at " + i);
                    break;
                }
            }
            ctx.checks++;
            if (scratch.notesInScopeFor(NoteScope.JOB, GovCapabilities.POLICE_JOB_ID, bob).size()
                    != NoteKeys.MAX_JOB_NOTES_PER_TARGET) {
                failures.add("a job's per-person notes did not reach the cap");
            }
            ctx.checks++;
            if (ScopedNoteService.add(scratch, memberWriter, NoteScope.JOB.wireId(),
                    GovCapabilities.POLICE_JOB_ID, bob, "StartupCheck M11 Bob",
                    "one too many", now + 100).code() != ErrorCode.INVALID_STATE) {
                failures.add("a job note beyond the cap was accepted");
            }


    }
}
