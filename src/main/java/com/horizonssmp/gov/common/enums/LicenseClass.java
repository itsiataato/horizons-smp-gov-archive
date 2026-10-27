package com.horizonssmp.gov.common.enums;

import java.util.Locale;
import java.util.Optional;

/**
 * The five vehicle licence classes of the Season 10 law.
 *
 * <p>Article 6.01 names exactly these five: Quad, Car, Truck, Helicopter and Plane. Article 6.09
 * gives each a letter and says a Truck licence also authorizes a Car; that single overlap is
 * expressed by {@link #covers(LicenseClass)} and nowhere else, so a future overlap is one line of
 * law to add rather than a search through the service.
 *
 * <p>The string ids are persisted verbatim in {@code gov.dat} (licence keys and audit payloads).
 * Do not rename an id without a schema migration. The one-letter {@link #code()} is display and
 * command input only; it is not the storage key.
 */
public enum LicenseClass {
    QUAD("quad", "Q", "Quad"),
    CAR("car", "C", "Car"),
    TRUCK("truck", "T", "Truck"),
    HELICOPTER("helicopter", "H", "Helicopter"),
    PLANE("plane", "P", "Plane");

    private final String id;
    private final String code;
    private final String displayName;

    LicenseClass(final String id, final String code, final String displayName) {
        this.id = id;
        this.code = code;
        this.displayName = displayName;
    }

    /** The stable lowercase id, as stored and as sent on the wire. */
    public String id() {
        return id;
    }

    /** The single letter Article 6.09 prints in parentheses. */
    public String code() {
        return code;
    }

    /** The human-readable class name. */
    public String displayName() {
        return displayName;
    }

    /**
     * True when holding this licence authorizes the requested class.
     *
     * <p>Article 6.09: "Truck License (T) ... also authorizes a Car." Every other licence covers
     * only its own class.
     */
    public boolean covers(final LicenseClass requested) {
        if (requested == null) {
            return false;
        }
        return this == requested || (this == TRUCK && requested == CAR);
    }

    /**
     * Parses a class from an id, a name or the single letter, case-insensitively.
     *
     * <p>Tolerant about the three forms a command or a client may send, strict about anything
     * else: an unknown class returns empty rather than silently becoming a Car licence.
     */
    public static Optional<LicenseClass> byId(final String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        final String needle = raw.trim().toLowerCase(Locale.ROOT);
        for (final LicenseClass value : values()) {
            if (value.id.equals(needle) || value.code.equalsIgnoreCase(needle)
                    || value.name().equalsIgnoreCase(needle)) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }

    /** Parses a class, or {@code null} when nothing matches. */
    public static LicenseClass parse(final String raw) {
        return byId(raw).orElse(null);
    }

    /** A comma-separated list of the ids, for help text. */
    public static String ids() {
        final StringBuilder out = new StringBuilder();
        for (final LicenseClass value : values()) {
            if (out.length() > 0) {
                out.append(", ");
            }
            out.append(value.id);
        }
        return out.toString();
    }
}
