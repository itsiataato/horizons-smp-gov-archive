package com.horizonssmp.gov.common.enums;

import java.util.Locale;
import java.util.Optional;

/**
 * The outcome recorded on one audit line.
 *
 * <p>The change log is not only a list of successes: a safety-critical refusal (a season reset
 * without a valid token, an export that could not be written safely) is itself evidence and must
 * be readable later. Ordinary refusals are still logged at DEBUG and are not appended, because a
 * client in a loop must not be able to fill the log; the service that owns a safety-critical
 * operation writes its own refusal line with this outcome.
 *
 * <p>The stored form is the constant name, so it is part of the file format. Unknown values read
 * as {@link #ACCEPTED}: a line written by an older build had no outcome and described an accepted
 * change.
 */
public enum AuditOutcome {

    /** The action was accepted and performed. */
    ACCEPTED,
    /** The action was refused; the reason field says why. */
    REFUSED;

    /**
     * Parses a stored or requested outcome, defaulting to {@link #ACCEPTED}.
     *
     * <p>Tolerant on purpose. An audit line from a build that did not write the field must still
     * load, and a client filter this build does not know must still produce a list rather than an
     * error. Use {@link #byId(String)} when the caller must tell "unknown" from "accepted".
     */
    public static AuditOutcome parse(final String name) {
        return byId(name).orElse(ACCEPTED);
    }

    /** Parses an outcome, or returns empty when the value is not one of the constants. */
    public static Optional<AuditOutcome> byId(final String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        final String needle = name.trim().toUpperCase(Locale.ROOT);
        for (final AuditOutcome outcome : values()) {
            if (outcome.name().equals(needle)) {
                return Optional.of(outcome);
            }
        }
        return Optional.empty();
    }
}
