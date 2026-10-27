package com.horizonssmp.gov.common.enums;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * One authority a business position may carry.
 *
 * <p>These are <b>business-internal</b> permissions, deliberately separate from
 * {@link com.horizonssmp.gov.common.GovCapability}. A government capability answers "may this
 * player act for the state"; a business permission answers "may this employee act for this
 * business". Keeping the two vocabularies apart is what prevents a business manager's role from
 * silently becoming a government office, and what lets a player hold a government job and a
 * business position at the same time without either grant implying the other.
 *
 * <p>The CEO position is the fixed root of a business and holds <b>every</b> permission
 * implicitly. It does not need a stored set, but the canonical CEO factory stores the full set
 * anyway so a reader that only looks at the set — a client, an export, a test — sees the truth.
 *
 * <p>The string ids are persisted in {@code gov.dat} and in the business wire keys. Do not rename
 * an id without a schema migration. Parsing is tolerant in the read direction: an unknown id is
 * dropped from a stored set (the action it would have allowed is then refused), never treated as
 * an alias for a real permission.
 */
public enum BusinessPermission {

    HIRE("hire"),
    FIRE("fire"),
    PROMOTE("promote"),
    DEMOTE("demote"),
    POSITIONS_MANAGE("positions.manage"),
    PAYROLL("payroll"),
    NOTES("notes");

    private final String id;

    BusinessPermission(final String id) {
        this.id = id;
    }

    /** The stable lowercase id, as stored and as sent on the wire. */
    public String id() {
        return id;
    }

    /**
     * Parses a permission by its stable id, or by its enum name, case-insensitively.
     *
     * <p>Unknown values return {@link Optional#empty()}. That is the fail-closed direction: a
     * permission this build does not know must never be guessed at, because guessing would grant
     * an authority the file's author did not name in this build's vocabulary.
     */
    public static Optional<BusinessPermission> byId(final String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        final String needle = raw.trim().toLowerCase(Locale.ROOT);
        for (final BusinessPermission value : values()) {
            if (value.id.equals(needle) || value.name().equalsIgnoreCase(needle)) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }

    /** Every permission, in declaration order. */
    public static Set<BusinessPermission> all() {
        return EnumSet.allOf(BusinessPermission.class);
    }

    /**
     * A tolerant copy of a permission collection.
     *
     * <p>{@link EnumSet#copyOf(Collection)} throws for an empty non-{@link EnumSet}, which is a
     * trap in a record constructor; null elements are dropped for the same reason an unknown stored
     * id is dropped. The returned set is an {@link EnumSet}, so equality with
     * {@link #all()} is stable.
     */
    public static Set<BusinessPermission> copyOf(
            final Collection<BusinessPermission> permissions) {
        final Set<BusinessPermission> result = EnumSet.noneOf(BusinessPermission.class);
        if (permissions != null) {
            for (final BusinessPermission permission : permissions) {
                if (permission != null) {
                    result.add(permission);
                }
            }
        }
        return result;
    }

    /** The comma-separated ids of a collection, sorted so the stored text is stable. */
    public static String format(final Collection<BusinessPermission> permissions) {
        final List<String> ids = new ArrayList<>();
        if (permissions != null) {
            for (final BusinessPermission permission : permissions) {
                if (permission != null) {
                    ids.add(permission.id);
                }
            }
        }
        ids.sort(String::compareTo);
        return String.join(",", ids);
    }

    /**
     * Parses a comma-separated permission list.
     *
     * <p>Empty entries and unknown ids are dropped; the caller receives the permissions this build
     * understands. An empty string is a valid empty set, which is what a custom position with no
     * authority is stored as.
     */
    public static Set<BusinessPermission> parseCsv(final String raw) {
        final Set<BusinessPermission> result = EnumSet.noneOf(BusinessPermission.class);
        if (raw == null || raw.isBlank()) {
            return result;
        }
        for (final String part : raw.split(",")) {
            byId(part).ifPresent(result::add);
        }
        return result;
    }

    /** A comma-and-space list of the ids, for help text and error messages. */
    public static String ids() {
        final StringBuilder out = new StringBuilder();
        for (final BusinessPermission value : values()) {
            if (out.length() > 0) {
                out.append(", ");
            }
            out.append(value.id);
        }
        return out.toString();
    }
}
