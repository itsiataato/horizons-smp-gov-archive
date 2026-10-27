package com.horizonssmp.gov.common.enums;

import java.util.Locale;

/**
 * Payment state of a fine.
 *
 * <p>{@link #VOID} exists for fines that were issued in error and withdrawn: the record stays in the
 * data file with its full history, but the fine no longer counts against the target and no gold
 * changes hands. {@link #WAIVED} is the reception-desk clearance (see {@code fine.waive}) and keeps
 * the identity of the official who waived it.
 *
 * <p>Names are persisted verbatim in {@code gov.dat}. Do not rename constants without a schema
 * migration.
 */
public enum FineStatus {
    UNPAID,
    PAID,
    WAIVED,
    VOID;

    /** Returns the status for a stored name, defaulting to {@link #UNPAID} when unknown. */
    public static FineStatus parse(final String name) {
        if (name == null || name.isBlank()) {
            return UNPAID;
        }
        try {
            return valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException e) {
            return UNPAID;
        }
    }

    /** True when the fine no longer expects gold from the target. */
    public boolean isSettled() {
        return this != UNPAID;
    }
}
