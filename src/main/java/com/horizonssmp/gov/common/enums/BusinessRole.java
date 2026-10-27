package com.horizonssmp.gov.common.enums;

import java.util.Locale;
import java.util.Optional;

/**
 * The employee role vocabulary of a business card.
 *
 * <p>A role is a label on the business's own staff list, not an authority. It grants no capability
 * and is never consulted by the authorization path: a {@code manager} of a shop holds exactly the
 * capabilities their job grants, and nothing here. That is deliberate, and it is why a role can be
 * validated against a closed vocabulary without any risk of becoming a second permission system.
 *
 * <p>The string ids are persisted verbatim in {@code gov.dat}. Do not rename an id without a schema
 * migration. An unknown stored id is kept verbatim by the reader and shown as-is.
 */
public enum BusinessRole {
    MANAGER("manager", "Manager"),
    CASHIER("cashier", "Cashier"),
    WORKER("worker", "Worker"),
    GUARD("guard", "Guard"),
    OTHER("other", "Other");

    private final String id;
    private final String displayName;

    BusinessRole(final String id, final String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    /** The stable lowercase id, as stored and as sent on the wire. */
    public String id() {
        return id;
    }

    /** The human-readable role name. */
    public String displayName() {
        return displayName;
    }

    /**
     * Parses a role from its id or name, case-insensitively. An unknown role returns empty: a
     * request that names a role this build does not know is refused, never stored as {@code other},
     * so a typo cannot quietly become a real staff entry.
     */
    public static Optional<BusinessRole> byId(final String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        final String needle = raw.trim().toLowerCase(Locale.ROOT);
        for (final BusinessRole value : values()) {
            if (value.id.equals(needle) || value.name().equalsIgnoreCase(needle)) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }

    /** Parses a role, or {@code null} when nothing matches. */
    public static BusinessRole parse(final String raw) {
        return byId(raw).orElse(null);
    }

    /** A comma-separated list of the ids, for help text. */
    public static String ids() {
        final StringBuilder out = new StringBuilder();
        for (final BusinessRole value : values()) {
            if (out.length() > 0) {
                out.append(", ");
            }
            out.append(value.id);
        }
        return out.toString();
    }
}
