package com.horizonssmp.gov.common.enums;

import java.util.Locale;

/**
 * Severity band of a fine. The band drives the gold amount in the M4 fine service; the dispatcher
 * and the records only store the band by name.
 *
 * <p>Names are persisted verbatim in {@code gov.dat}. Do not rename constants without a schema
 * migration: {@link #parse(String)} falls back to {@link #PETTY} for unknown input so an old or
 * hand-edited file can never fail to load, but a rename silently re-bands existing fines.
 */
public enum FineBand {
    PETTY,
    MISDEMEANOR,
    FELONY,
    AGGRAVATED;

    /** Returns the band for a stored name, defaulting to {@link #PETTY} when unknown. */
    public static FineBand parse(final String name) {
        if (name == null || name.isBlank()) {
            return PETTY;
        }
        try {
            return valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException e) {
            return PETTY;
        }
    }
}
