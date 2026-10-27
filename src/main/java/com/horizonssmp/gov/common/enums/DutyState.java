package com.horizonssmp.gov.common.enums;

import java.util.Locale;

/**
 * Whether a player is currently acting as an official.
 *
 * <p>{@link #N_A} is a real value, not "missing": a player whose job has no duty concept (citizen,
 * or any job without {@code duty.toggle}) is stored as {@code N_A} so the dashboard never has to
 * guess between "off duty" and "this job has no duty".
 *
 * <p>Names are persisted verbatim in {@code gov.dat}. Do not rename constants without a schema
 * migration.
 */
public enum DutyState {
    ON_DUTY,
    OFF_DUTY,
    N_A;

    /** Returns the state for a stored name, defaulting to {@link #N_A} when unknown. */
    public static DutyState parse(final String name) {
        if (name == null || name.isBlank()) {
            return N_A;
        }
        try {
            return valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException e) {
            return N_A;
        }
    }
}
