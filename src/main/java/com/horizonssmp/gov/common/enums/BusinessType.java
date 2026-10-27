package com.horizonssmp.gov.common.enums;

import java.util.Locale;
import java.util.Optional;

/**
 * The business type vocabulary of the registry.
 *
 * <p>Article 8.12 lists businesses among the public registries the registrar keeps, and the law
 * names several kinds of undertaking in passing: shops (Article 7.06), banks (Article 7.08,
 * defined in Article 1.11 as lawful businesses that take deposits under written receipt), factories
 * and industrial undertakings (Article 8.07), mines (Article 8.09) and farms. This vocabulary is
 * the registry's short label for those, plus {@code other} so no lawful undertaking is refused a
 * registration for want of a word.
 *
 * <p>The string ids are persisted verbatim in {@code gov.dat}. Do not rename an id without a
 * schema migration. The type is stored as its id on the record, exactly as a vehicle stores its
 * class id; an unknown stored id is kept verbatim by the reader and shown as-is, never guessed.
 */
public enum BusinessType {
    SHOP("shop", "Shop"),
    BANK("bank", "Bank"),
    FACTORY("factory", "Factory"),
    FARM("farm", "Farm"),
    MINE("mine", "Mine"),
    SERVICE("service", "Service"),
    OTHER("other", "Other");

    private final String id;
    private final String displayName;

    BusinessType(final String id, final String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    /** The stable lowercase id, as stored and as sent on the wire. */
    public String id() {
        return id;
    }

    /** The human-readable type name. */
    public String displayName() {
        return displayName;
    }

    /**
     * Parses a type from its id or name, case-insensitively. An unknown type returns empty rather
     * than silently becoming {@code other}, because a registration that quietly changes its type
     * is a registry entry that lies.
     */
    public static Optional<BusinessType> byId(final String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        final String needle = raw.trim().toLowerCase(Locale.ROOT);
        for (final BusinessType value : values()) {
            if (value.id.equals(needle) || value.name().equalsIgnoreCase(needle)) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }

    /** Parses a type, or {@code null} when nothing matches. */
    public static BusinessType parse(final String raw) {
        return byId(raw).orElse(null);
    }

    /** A comma-separated list of the ids, for help text. */
    public static String ids() {
        final StringBuilder out = new StringBuilder();
        for (final BusinessType value : values()) {
            if (out.length() > 0) {
                out.append(", ");
            }
            out.append(value.id);
        }
        return out.toString();
    }
}
