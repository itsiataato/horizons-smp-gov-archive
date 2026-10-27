package com.horizonssmp.gov.common.enums;

import java.util.Locale;

/**
 * The lifecycle of one detention record.
 *
 * <p>Names are persisted verbatim in {@code gov.dat}. Do not rename constants without a schema
 * migration.
 *
 * <p>The tolerant parser is deliberately <b>fail-safe</b>: an unreadable status reads back as
 * {@link #RELEASED}, never {@link #ACTIVE}. A malformed record must not be able to hold a player or
 * keep cuffs on; the worst case of the safe default is that an operator has to re-detain someone,
 * while the worst case of the other default is false imprisonment.
 */
public enum DetentionStatus {
    /** The player is currently held and the enforcement timer is counting down. */
    ACTIVE,
    /** An official released the player, or the release command was accepted. */
    RELEASED,
    /** The recorded term ran out and the server released the player automatically. */
    EXPIRED;

    /** Returns the status for a stored name, defaulting to the safe {@link #RELEASED}. */
    public static DetentionStatus parse(final String name) {
        if (name == null || name.isBlank()) {
            return RELEASED;
        }
        try {
            return valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException e) {
            return RELEASED;
        }
    }
}
