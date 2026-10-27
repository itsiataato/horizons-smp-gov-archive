package com.horizonssmp.gov.common.enums;

import java.util.Locale;

/**
 * Lifecycle state of one person's licence for one class.
 *
 * <p>{@link #SUSPENDED} is stored with a {@code suspendedUntil} stamp. The effective state is
 * computed at read time by {@code LicenseRecord.stateAt(long)}: once the stamp has passed, the
 * licence is valid again without a write. {@link #REVOKED} is permanent until the registrar issues
 * a new licence, and the revocation reason is part of the record.
 *
 * <p>Names are persisted verbatim in {@code gov.dat}. Do not rename constants without a schema
 * migration. An unknown stored status reads as {@link #REVOKED}: a damaged record must not grant
 * a licence it cannot prove.
 */
public enum LicenseStatus {
    VALID,
    SUSPENDED,
    REVOKED;

    /** Returns the status for a stored name, defaulting to {@link #REVOKED} when unknown. */
    public static LicenseStatus parse(final String name) {
        if (name == null || name.isBlank()) {
            return REVOKED;
        }
        try {
            return valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException e) {
            return REVOKED;
        }
    }
}
