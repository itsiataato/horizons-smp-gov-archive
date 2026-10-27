package com.horizonssmp.gov.server;

import java.util.Comparator;
import java.util.UUID;

import com.horizonssmp.gov.common.NoteScope;
import com.horizonssmp.gov.common.records.NoteRecord;
import com.horizonssmp.gov.common.records.PlayerRecord;

/** Small shared helpers for scoped notes. Package-private. */
final class ScopedNoteSupport {

    private ScopedNoteSupport() {
    }

    static boolean usable(final GovSavedData data, final ScopedNoteService.Actor actor) {
        return data != null && actor != null && actor.uuid() != null;
    }

    static String targetName(final GovSavedData data, final UUID targetUuid) {
        final PlayerRecord record = data == null || targetUuid == null ? null : data.player(targetUuid);
        if (record != null && !record.lastName().isBlank()) {
            return record.lastName();
        }
        return targetUuid == null ? "" : targetUuid.toString();
    }

    static Comparator<NoteRecord> newestFirst() {
        return Comparator
                .comparingLong(NoteRecord::createdAt).reversed()
                .thenComparing(NoteRecord::id);
    }

    static String scopeMessage(final String rawScope) {
        final NoteScope parsed = NoteScope.fromWire(rawScope).orElse(null);
        if (parsed == NoteScope.GLOBAL) {
            return "GLOBAL notes use the ordinary note actions; a scoped action names a job or a business.";
        }
        return "A scoped note needs 'scope' = job or business and a non-empty 'scopeId'.";
    }

    static String label(final String stored, final String fallback, final String id) {
        final String name = Sanitizer.singleLine(stored, ScopedNoteService.MAX_SCOPE_LABEL);
        if (name.isBlank() || name.equals(id == null ? "" : id)) {
            return Sanitizer.singleLine(fallback, ScopedNoteService.MAX_SCOPE_LABEL);
        }
        return name;
    }
}