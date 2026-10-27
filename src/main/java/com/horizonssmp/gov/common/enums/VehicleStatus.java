package com.horizonssmp.gov.common.enums;

import java.util.Locale;

/**
 * Registration state of a vehicle.
 *
 * <p>{@link #IMPOUNDED} is a real status, not a flag: a vehicle holds exactly one status, and
 * {@code VehicleRecord.impound} records the details of the current or last impound. Releasing a
 * vehicle returns it to {@link #VALID}; the release itself stays visible in the inactive impound
 * block (who released it, when, and the fee that was paid).
 *
 * <p>{@link #FLAGGED_FOR_DISPOSAL} is the Article 6.15 end state of an unclaimed impound: after 30
 * in-game days the registry may mark the vehicle so staff can dispose of it with notice. The mark
 * is never a deletion; the record and its history stay exactly where they are.
 *
 * <p>Names are persisted verbatim in {@code gov.dat}. Do not rename constants without a schema
 * migration. New constants are safe: {@link #parse(String)} falls back to {@link #VALID} for an
 * older file, and a newer build's status is unknown only to an older build.
 */
public enum VehicleStatus {
    VALID,
    EXPIRED,
    SUSPENDED,
    IMPOUNDED,
    STOLEN,
    FLAGGED_FOR_DISPOSAL;

    /** Returns the status for a stored name, defaulting to {@link #VALID} when unknown. */
    public static VehicleStatus parse(final String name) {
        if (name == null || name.isBlank()) {
            return VALID;
        }
        try {
            return valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException e) {
            return VALID;
        }
    }
}
