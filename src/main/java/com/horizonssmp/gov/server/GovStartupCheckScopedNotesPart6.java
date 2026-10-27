package com.horizonssmp.gov.server;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Locale;
import java.util.EnumSet;
import com.horizonssmp.gov.common.GovCapability;
import com.horizonssmp.gov.common.records.JobRecord;
import java.util.UUID;
import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.GovCapabilities;
import com.horizonssmp.gov.common.NoteKeys;
import com.horizonssmp.gov.common.NoteScope;
import com.horizonssmp.gov.common.records.BusinessRecord;
import com.horizonssmp.gov.common.records.NoteRecord;
import com.horizonssmp.gov.common.records.PlayerRecord;
import static com.horizonssmp.gov.server.GovStartupCheckScopedNotesSupport.fillScoped;
import static com.horizonssmp.gov.server.GovStartupCheckScopedNotesSupport.scopedActor;

final class GovStartupCheckScopedNotesPart6 {

    private GovStartupCheckScopedNotesPart6() {
    }

    static void run(final GovStartupCheckScopedNotesContext ctx) {
        final List<String> failures = ctx.failures;
        final long now = ctx.now;
        final GovSavedData scratch = ctx.scratch;
        final UUID alice = ctx.alice;
        final UUID owner = ctx.owner;
        final UUID outsider = ctx.outsider;
        final BusinessRecord bakery = ctx.bakery;
        final ScopedNoteService.Actor ownerActor = ctx.ownerActor;
        final ScopedNoteService.Actor outsiderActor = ctx.outsiderActor;
        final ScopedNoteService.Actor memberWriter = ctx.memberWriter;
        final ScopedNoteService.Actor jobManager = ctx.jobManager;
        final ScopedNoteService.Actor workerActor = ctx.workerActor;
        final ScopedNoteService.Actor manageActor = ctx.manageActor;
        final UUID worker = ctx.worker;

            // ---------------------------------------------------- scope listing

            ctx.checks++;
            final List<ScopedNoteService.ScopeView> memberScopes =
                    ScopedNoteService.scopes(scratch, memberWriter);
            if (memberScopes.size() != 1
                    || memberScopes.get(0).type() != NoteScope.JOB
                    || !GovCapabilities.POLICE_JOB_ID.equals(memberScopes.get(0).id())
                    || !"Police".equals(memberScopes.get(0).label())
                    || !"member".equals(memberScopes.get(0).role())
                    || !memberScopes.get(0).canPost()
                    || !memberScopes.get(0).canModerate()) {
                failures.add("a member's NOTE_SCOPES answer is wrong");
            }
            final Map<String, String> scopeFields =
                    ScopedNoteService.scopesFields(memberScopes);
            ctx.checks++;
            if (!"1".equals(scopeFields.get(NoteKeys.SCOPES))
                    || !NoteScope.JOB.wireId().equals(scopeFields.get(
                            NoteKeys.SCOPE_PREFIX + "0" + NoteKeys.SCOPE_ROW_TYPE))
                    || !GovCapabilities.POLICE_JOB_ID.equals(scopeFields.get(
                            NoteKeys.SCOPE_PREFIX + "0" + NoteKeys.SCOPE_ROW_ID))
                    || !"Police".equals(scopeFields.get(
                            NoteKeys.SCOPE_PREFIX + "0" + NoteKeys.SCOPE_ROW_LABEL))
                    || !"member".equals(scopeFields.get(
                            NoteKeys.SCOPE_PREFIX + "0" + NoteKeys.SCOPE_ROW_ROLE))
                    || !"true".equals(scopeFields.get(
                            NoteKeys.SCOPE_PREFIX + "0" + NoteKeys.SCOPE_ROW_CAN_POST))
                    || !"true".equals(scopeFields.get(
                            NoteKeys.SCOPE_PREFIX + "0" + NoteKeys.SCOPE_ROW_CAN_MODERATE))) {
                failures.add("the NOTE_SCOPES field block is missing a documented row key");
            }
            ctx.checks++;
            final List<ScopedNoteService.ScopeView> workerScopes =
                    ScopedNoteService.scopes(scratch, workerActor);
            if (workerScopes.size() != 1
                    || workerScopes.get(0).type() != NoteScope.BUSINESS
                    || !bakery.id().equals(workerScopes.get(0).id())
                    || !"employee".equals(workerScopes.get(0).role())
                    || !workerScopes.get(0).canPost()) {
                failures.add("an employee's NOTE_SCOPES answer is wrong (" + workerScopes + ")");
            }
            ctx.checks++;
            if (!ScopedNoteService.scopes(scratch, outsiderActor).isEmpty()) {
                failures.add("NOTE_SCOPES leaked a scope to an outsider");
            }
            ctx.checks++;
            final List<ScopedNoteService.ScopeView> manageScopes =
                    ScopedNoteService.scopes(scratch, manageActor);
            if (manageScopes.isEmpty()
                    || manageScopes.stream().anyMatch(view -> view.type() != NoteScope.BUSINESS)
                    || manageScopes.stream().noneMatch(view -> bakery.id().equals(view.id()))
                    || manageScopes.stream().anyMatch(view -> !"manage".equals(view.role()))) {
                failures.add("a business.manage holder's NOTE_SCOPES answer is wrong");
            }
            ctx.checks++;
            if (manageScopes.stream().noneMatch(view -> "b-m11-unrelated".equals(view.id()))) {
                failures.add("a business.manage holder could not see a registered business");
            }

            // A job supervisor listing every job is capped defensively.
            for (int i = 0; i < 15; i++) {
                scratch.putJob(new JobRecord(
                        String.format(Locale.ROOT, "m11-extra-%02d", i),
                        String.format(Locale.ROOT, "StartupCheck Extra %02d", i),
                        "", "M11", "Member", false,
                        EnumSet.noneOf(GovCapability.class), now, null));
            }
            ctx.checks++;
            final List<ScopedNoteService.ScopeView> supervisorScopes =
                    ScopedNoteService.scopes(scratch, jobManager);
            if (supervisorScopes.size() != ScopedNoteService.MAX_LISTED_JOB_SCOPES
                    || supervisorScopes.size() > 12
                    || supervisorScopes.stream().anyMatch(view -> view.type() != NoteScope.JOB)) {
                failures.add("a job supervisor's job listing was not capped or leaked a business");
            }

            // ------------------------------------------------------ season reset

            final GovSavedData season = new GovSavedData();
            season.seedIfNeeded(now, false);
            season.putPlayer(PlayerRecord.blank(alice, "StartupCheck M11 Alice", now));
            season.putPlayer(PlayerRecord.blank(worker, "StartupCheck M11 Worker", now));
            season.putNote(new NoteRecord(
                    "n-m11-season-global", alice, alice, "StartupCheck Writer",
                    "global", now, 0L));
            fillScoped(season, NoteScope.JOB, GovCapabilities.POLICE_JOB_ID, alice,
                    alice, 1, now + 1);
            fillScoped(season, NoteScope.BUSINESS, "b-m11-season", null, alice, 1, now + 2);
            ctx.checks++;
            if (season.scopedNoteCount() != 2) {
                failures.add("the season-reset fixture did not hold two scoped notes");
            }
            season.clearSeason(false, now);
            ctx.checks++;
            if (!season.notes().isEmpty() || season.scopedNoteCount() != 0) {
                failures.add("the season reset did not clear the scoped notes");
            }

            // ---------------------------------------------------------- wire

            ctx.checks++;
            final ScopedNoteService.ListResult wireList = ScopedNoteService.list(
                    scratch, memberWriter, NoteScope.JOB.wireId(),
                    GovCapabilities.POLICE_JOB_ID, null, "", 0);
            final Map<String, String> wireFields = ScopedNoteService.listFields(wireList);
            if (!wireList.ok()
                    || !NoteScope.JOB.wireId().equals(wireFields.get(NoteKeys.SCOPE_TYPE))
                    || !GovCapabilities.POLICE_JOB_ID.equals(wireFields.get(NoteKeys.SCOPE_ID))
                    || !"Police".equals(wireFields.get(NoteKeys.SCOPE_LABEL))
                    || !"true".equals(wireFields.get(NoteKeys.CAN_POST_NOTES))
                    || !"true".equals(wireFields.get(NoteKeys.CAN_MODERATE_NOTES))
                    || !"true".equals(wireFields.get(NoteKeys.CAN_MANAGE))
                    || !wireFields.containsKey(NoteKeys.TOTAL)
                    || !wireFields.containsKey(NoteKeys.PAGE)
                    || !wireFields.containsKey(NoteKeys.PAGES)
                    || !wireFields.containsKey(NoteKeys.ROW_PREFIX + "0" + NoteKeys.ROW_ID)) {
                failures.add("the NOTE_SCOPED_LIST field block is missing a documented key");
            }
            ctx.checks++;
            if (!"0".equals(wireFields.get(NoteKeys.PAGE))
                    || wireList.page().rows().size() > NoteKeys.PAGE_SIZE) {
                failures.add("the scoped list ignored its page size or page bound");
            }

    }
}
