package com.horizonssmp.gov.server;

import com.horizonssmp.gov.common.records.BusinessRecord;
import com.horizonssmp.gov.common.records.NoteRecord;

/** Resolved note for an edit/delete, or a refusal. Package-private helper. */
record ScopedNoteLookup(
        ScopedNoteScopeKey key,
        BusinessRecord business,
        NoteRecord note,
        ScopedNoteService.Outcome refusal) {

    static ScopedNoteLookup refused(final ScopedNoteService.Outcome refusal) {
        return new ScopedNoteLookup(null, null, null, refusal);
    }
}