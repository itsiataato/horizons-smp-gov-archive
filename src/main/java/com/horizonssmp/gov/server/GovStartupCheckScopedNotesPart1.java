package com.horizonssmp.gov.server;

import java.util.List;
import java.util.UUID;
import com.horizonssmp.gov.common.GovCapability;
import com.horizonssmp.gov.common.GovCapabilities;
import com.horizonssmp.gov.common.NoteKeys;
import com.horizonssmp.gov.common.NoteScope;
import com.horizonssmp.gov.common.OpCode;
import com.horizonssmp.gov.common.records.NbtSupport;
import com.horizonssmp.gov.common.records.NoteRecord;
import com.horizonssmp.gov.common.records.PlayerRecord;
import com.horizonssmp.gov.network.GovHandshakePayload;
import com.horizonssmp.gov.network.GovNetwork;
import net.minecraft.nbt.CompoundTag;

final class GovStartupCheckScopedNotesPart1 {

    private GovStartupCheckScopedNotesPart1() {
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
        final UUID orphanMember = ctx.orphanMember;
        final UUID capWorker = ctx.capWorker;


            // ------------------------------------------------- constants and ops

            ctx.checks++;
            if (NoteKeys.MAX_JOB_NOTES_PER_TARGET != 20
                    || NoteKeys.MAX_BUSINESS_NOTES_PER_TARGET != 20
                    || NoteKeys.MAX_BUSINESS_BOARD_NOTES != 200
                    || NoteKeys.MAX_SCOPED_NOTES != 10_000
                    || NoteKeys.MAX_NOTES_PER_PERSON != 50
                    || NoteKeys.PAGE_SIZE != 6
                    || ScopedNoteService.MAX_JOB_NOTES_PER_TARGET != 20
                    || ScopedNoteService.MAX_BUSINESS_NOTES_PER_TARGET != 20
                    || ScopedNoteService.MAX_BUSINESS_BOARD_NOTES != 200
                    || ScopedNoteService.MAX_SCOPED_NOTES != 10_000) {
                failures.add("a scoped-note limit is not the documented value");
            }

            final OpCode[] scopedOps = {
                OpCode.NOTE_SCOPES, OpCode.NOTE_SCOPED_LIST, OpCode.NOTE_SCOPED_ADD,
                OpCode.NOTE_SCOPED_EDIT, OpCode.NOTE_SCOPED_DELETE};
            ctx.checks++;
            for (final OpCode op : scopedOps) {
                if (GovCapabilities.requiredFor(op) != GovCapability.PLAYER_LOOKUP_SELF) {
                    failures.add("a scoped-note action does not require the baseline capability: " + op);
                }
            }
            ctx.checks++;
            if (OpCode.NOTE_SCOPES.audited() || OpCode.NOTE_SCOPED_LIST.audited()) {
                failures.add("a scoped-note read op is marked as audited");
            }
            ctx.checks++;
            for (final OpCode op : new OpCode[] {
                OpCode.NOTE_SCOPED_ADD, OpCode.NOTE_SCOPED_EDIT, OpCode.NOTE_SCOPED_DELETE}) {
                if (!op.audited() || !op.dispatcherAudited()) {
                    failures.add("a scoped-note mutation is not dispatcher-audited: " + op);
                }
            }
            ctx.checks++;
            for (final OpCode op : scopedOps) {
                if (!GovActionDispatcher.isRegistered(op)) {
                    failures.add("a scoped-note action has no handler registered: " + op);
                }
                if (op.requiresPlayerTarget()) {
                    failures.add("a scoped-note action wrongly requires a dispatcher target: " + op);
                }
            }
            ctx.checks++;
            if (GovNetwork.PROTOCOL_VERSION != 4
                    || GovHandshakePayload.current().protocol() != 4) {
                failures.add("the wire protocol version was not bumped to 4");
            }

            // -------------------------------------------------- record tolerance


            ctx.checks++;
            final NoteRecord v1 = new NoteRecord(
                    "n-m11-v1", alice, alice, "StartupCheck Writer", "version one", now, 0L);
            if (!v1.global() || !NoteScope.GLOBAL.id().equals(v1.scope())
                    || !v1.scopeId().isEmpty()) {
                failures.add("the seven-argument note constructor did not create a GLOBAL note");
            }
            ctx.checks++;
            if (!v1.equals(NoteRecord.read(v1.write(), null))) {
                failures.add("a GLOBAL note did not round-trip through its own encoding");
            }
            ctx.checks++;
            if (!NoteScope.GLOBAL.id().equals(v1.write().getString("scope"))
                    || !"".equals(v1.write().getString("scopeId"))) {
                failures.add("a GLOBAL note did not write its additive scope keys");
            }

            ctx.checks++;
            final CompoundTag legacyTag = new CompoundTag();
            legacyTag.putString("id", "n-m11-legacy");
            NbtSupport.putUuid(legacyTag, "targetUuid", alice);
            legacyTag.putString("text", "from the pre-M11 file");
            legacyTag.putLong("createdAt", now);
            final NoteRecord legacy = NoteRecord.read(legacyTag, null);
            if (!legacy.global() || !legacy.scopeId().isEmpty()
                    || !"from the pre-M11 file".equals(legacy.text())) {
                failures.add("a version-1 note tag without scope keys did not load as GLOBAL");
            }

            ctx.checks++;
            final NoteRecord future = new NoteRecord(
                    "n-m11-future", bob, alice, "StartupCheck Writer", "from the future",
                    now, 0L, "FROM_THE_FUTURE", "whatever");
            final NoteRecord futureRead = NoteRecord.read(future.write(), null);
            if (futureRead.knownScope().isPresent()
                    || !"FROM_THE_FUTURE".equals(futureRead.scope())
                    || !"whatever".equals(futureRead.scopeId())) {
                failures.add("an unknown note scope was not preserved verbatim or was treated as known");
            }
            ctx.checks++;
            final NoteRecord editedFuture = futureRead.withText("changed", now + 1);
            if (!"FROM_THE_FUTURE".equals(editedFuture.scope())
                    || !"whatever".equals(editedFuture.scopeId())) {
                failures.add("editing a note lost its scope");
            }

            // The whole data set, not just one tag: a GLOBAL note and a scoped note survive a save
            // and load, so a restart does not lose or reclassify either kind.
            final GovSavedData persisted = new GovSavedData();
            persisted.putPlayer(PlayerRecord.blank(alice, "StartupCheck M11 Alice", now));
            persisted.putNote(v1);
            persisted.putNote(new NoteRecord(
                    "n-m11-persist-job", alice, alice, "StartupCheck Writer", "persisted job",
                    now, 0L, NoteScope.JOB.id(), GovCapabilities.POLICE_JOB_ID));
            persisted.putNote(new NoteRecord(
                    "n-m11-persist-board", null, alice, "StartupCheck Writer", "persisted board",
                    now, 0L, NoteScope.BUSINESS.id(), "b-m11-persist"));
            final CompoundTag savedTag = new CompoundTag();
            persisted.save(savedTag, null);
            final GovSavedData reloaded = GovSavedData.load(savedTag, null);
            ctx.checks++;
            if (!v1.equals(reloaded.note(v1.id()))) {
                failures.add("a GLOBAL note did not survive a whole-data save and load");
            }
            ctx.checks++;
            final NoteRecord reloadedJob = reloaded.note("n-m11-persist-job");
            final NoteRecord reloadedBoard = reloaded.note("n-m11-persist-board");
            if (reloadedJob == null || reloadedBoard == null
                    || !GovSavedData.isScope(reloadedJob, NoteScope.JOB)
                    || !GovCapabilities.POLICE_JOB_ID.equals(reloadedJob.scopeId())
                    || !GovSavedData.isScope(reloadedBoard, NoteScope.BUSINESS)
                    || !"b-m11-persist".equals(reloadedBoard.scopeId())
                    || reloadedBoard.targetUuid() != null) {
                failures.add("a scoped note did not survive a whole-data save and load");
            }


        ctx.future = future;
    }
}
