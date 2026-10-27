package com.horizonssmp.gov.server;

import com.horizonssmp.gov.common.NoteScope;
import com.horizonssmp.gov.server.Sanitizer;

/** Wire scope + id pair used while resolving a scoped note action. Package-private helper. */
record ScopedNoteScopeKey(NoteScope type, String id) {

    /** Parses a wire scope and id, or {@code null} when either is not usable. */
    static ScopedNoteScopeKey parse(final String rawScope, final String rawScopeId) {
        final NoteScope type = NoteScope.fromWire(rawScope).orElse(null);
        if (type == null || !type.scoped()) {
            return null;
        }
        final String id = Sanitizer.id(rawScopeId);
        return id.isBlank() ? null : new ScopedNoteScopeKey(type, id);
    }
}