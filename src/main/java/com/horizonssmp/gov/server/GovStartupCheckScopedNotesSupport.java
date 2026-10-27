package com.horizonssmp.gov.server;

import java.util.EnumSet;
import java.util.UUID;
import com.horizonssmp.gov.common.GovCapability;
import com.horizonssmp.gov.common.NoteScope;
import com.horizonssmp.gov.common.records.NoteRecord;

final class GovStartupCheckScopedNotesSupport {

    private GovStartupCheckScopedNotesSupport() {
    }

    static ScopedNoteService.Actor scopedActor(
            final UUID uuid, final String name, final GovCapability... capabilities) {
        final EnumSet<GovCapability> set = EnumSet.noneOf(GovCapability.class);
        for (final GovCapability capability : capabilities) {
            if (capability != null) {
                set.add(capability);
            }
        }
        return new ScopedNoteService.Actor(uuid, name, set);
    }

    static void fillScoped(
            final GovSavedData data,
            final NoteScope scope,
            final String scopeId,
            final UUID targetUuid,
            final UUID authorUuid,
            final int count,
            final long now) {
        for (int i = 0; i < count; i++) {
            final String id = "n-m11-fill-" + scopeId + "-" + i;
            data.putNote(new NoteRecord(
                    id, targetUuid, authorUuid, "StartupCheck M11 Filler",
                    "filler " + i, now + i, 0L, scope.id(), scopeId));
        }
    }

    static NoteRecord findByText(
            final GovSavedData data,
            final NoteScope scope,
            final String scopeId,
            final UUID targetUuid,
            final String text) {
        for (final NoteRecord note : data.notesInScopeFor(scope, scopeId, targetUuid)) {
            if (text.equals(note.text())) {
                return note;
            }
        }
        return null;
    }
}
