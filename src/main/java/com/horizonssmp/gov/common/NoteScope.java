package com.horizonssmp.gov.common;

import java.util.Locale;
import java.util.Optional;

/**
 * The scope a note belongs to.
 *
 * <p>A note used to be one thing: an observation on a person's file. M11 adds two scoped kinds,
 * because a job's officers and a business's team each keep notes that must not appear in the
 * public note list. The scope is stored as a string in {@code gov.dat} and travels on the wire as
 * the lower-case {@link #wireId()}.
 *
 * <p><b>The stored value is tolerant and the unknown case fails closed.</b> A version-1 note has no
 * scope key at all and reads as {@link #GLOBAL}. An unrecognised stored value is <i>preserved
 * verbatim</i> by {@link com.horizonssmp.gov.common.records.NoteRecord} so a downgrade and upgrade
 * cannot silently rewrite it, but {@link #fromStored(String)} returns empty for it and no list,
 * count or lookup in this mod will ever return such a note. That is the difference between
 * preserving data and honouring it: a note from a newer build is kept, never shown to someone this
 * build cannot authorize.
 *
 * <p>Recognition is case-insensitive so a hand-edited {@code job} still means {@link #JOB}; the
 * record keeps the bytes it loaded. Writing always uses the canonical upper-case id.
 */
public enum NoteScope {

    /** The existing person-file note. Every version-1 note is one of these. */
    GLOBAL("GLOBAL", "global"),
    /** A note about a person, kept by one job. */
    JOB("JOB", "job"),
    /** A business note: a per-player note, or the team board when the target is absent. */
    BUSINESS("BUSINESS", "business");

    private final String id;
    private final String wireId;

    NoteScope(final String id, final String wireId) {
        this.id = id;
        this.wireId = wireId;
    }

    /** The canonical stored id, written into {@code gov.dat}. */
    public String id() {
        return id;
    }

    /** The stable lower-case id sent in a payload and echoed in a response. */
    public String wireId() {
        return wireId;
    }

    /** True for the two scopes the M11 scoped actions may open. */
    public boolean scoped() {
        return this != GLOBAL;
    }

    /**
     * Parses a wire scope id ({@code job} or {@code business}), case-insensitively.
     *
     * <p>{@code global} parses too, so the caller can give one specific refusal for "use the
     * existing note actions" instead of "unknown scope".
     */
    public static Optional<NoteScope> fromWire(final String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        final String needle = raw.trim().toLowerCase(Locale.ROOT);
        for (final NoteScope scope : values()) {
            if (scope.wireId.equals(needle)) {
                return Optional.of(scope);
            }
        }
        return Optional.empty();
    }

    /**
     * Parses a stored scope id, case-insensitively. Empty input is {@link #GLOBAL} because a
     * version-1 tag has no key; anything else that is not one of the three ids returns empty and
     * must be treated as unreadable by every list and count.
     */
    public static Optional<NoteScope> fromStored(final String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.of(GLOBAL);
        }
        final String needle = raw.trim().toUpperCase(Locale.ROOT);
        for (final NoteScope scope : values()) {
            if (scope.id.equals(needle)) {
                return Optional.of(scope);
            }
        }
        return Optional.empty();
    }
}
