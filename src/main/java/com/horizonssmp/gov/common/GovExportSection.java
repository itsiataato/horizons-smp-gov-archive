package com.horizonssmp.gov.common;

import java.util.Locale;
import java.util.Optional;

/**
 * The registry sections {@code /gov admin export <section>} and the Admin picker can write.
 *
 * <p>This enum is the allow-list that makes path traversal impossible: the section never becomes
 * a path segment. It is parsed to a constant here and the export service derives the filename from
 * {@link #id()} alone. A raw string such as {@code ../../world} therefore has no representation
 * and is refused with {@code BAD_PAYLOAD}.
 *
 * <p>Ids are stable wire and command values. {@code area-permits} is the one multi-word id; the
 * parser also accepts {@code area permits}, {@code area_permits} and {@code areas}, because a
 * human typing a command should not have to remember the punctuation.
 */
public enum GovExportSection {

    AUDIT("audit", "audit"),
    PLAYERS("players", "players"),
    JOBS("jobs", "jobs"),
    FINES("fines", "fines"),
    NOTES("notes", "notes"),
    BUSINESSES("businesses", "businesses"),
    VEHICLES("vehicles", "vehicles"),
    WEAPONS("weapons", "weapons"),
    PERMITS("permits", "weaponPermits"),
    AREA_PERMITS("area-permits", "areaPermits"),
    DETENTIONS("detentions", "detentions");

    private final String id;
    private final String jsonKey;

    GovExportSection(final String id, final String jsonKey) {
        this.id = id;
        this.jsonKey = jsonKey;
    }

    /** The stable section id used in commands, payloads and export filenames. */
    public String id() {
        return id;
    }

    /** The key this section's records use inside the export's {@code data} object. */
    public String jsonKey() {
        return jsonKey;
    }

    /** A translation key for the Admin picker. */
    public String labelKey() {
        return "gui.gov.audit.section." + id.replace('-', '_');
    }

    /** Parses a section id tolerantly; separators and case do not matter. */
    public static Optional<GovExportSection> byId(final String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        final String needle = raw.trim().toLowerCase(Locale.ROOT)
                .replace('_', '-')
                .replace(' ', '-');
        if ("areas".equals(needle) || "area-permit".equals(needle)) {
            return Optional.of(AREA_PERMITS);
        }
        for (final GovExportSection section : values()) {
            if (section.id.equals(needle)) {
                return Optional.of(section);
            }
        }
        return Optional.empty();
    }

    /** The comma-separated id list, for a usage line or a refusal message. */
    public static String ids() {
        final StringBuilder out = new StringBuilder();
        for (final GovExportSection section : values()) {
            if (out.length() > 0) {
                out.append(", ");
            }
            out.append(section.id);
        }
        return out.toString();
    }
}
