package com.horizonssmp.gov.common.enums;

import java.util.Locale;

/**
 * Outcome of a plate scan.
 *
 * <p>The scan result is what the officer is shown and what is written to
 * {@code PlateScanRecord.result}. It is deliberately distinct from {@link VehicleStatus}: a scan of
 * an unregistered plate has no vehicle record at all, and a stolen vehicle may be physically
 * present while its registration is valid.
 *
 * <p>Names are persisted verbatim in {@code gov.dat}. Do not rename constants without a schema
 * migration.
 */
public enum ScanResult {
    VALID,
    UNREGISTERED,
    EXPIRED,
    IMPOUNDED,
    STOLEN,
    SUSPENDED,
    /** The vehicle is registered but its owner holds no valid licence for its class. */
    LICENSE_INVALID,
    /** The impound has passed 30 in-game days and staff may dispose of the vehicle. */
    DISPOSAL_FLAGGED;

    /** Returns the result for a stored name, defaulting to {@link #UNREGISTERED} when unknown. */
    public static ScanResult parse(final String name) {
        if (name == null || name.isBlank()) {
            return UNREGISTERED;
        }
        try {
            return valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException e) {
            return UNREGISTERED;
        }
    }
}
