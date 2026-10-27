package com.horizonssmp.gov.common.enums;

import java.util.Locale;

/**
 * Lifecycle state of a permit.
 *
 * <p>As with {@code LicenseStatus}, the stored status is only half of the answer: the effective
 * state is computed at read time by {@code PermitRecord.stateAt(long)}, so an expiry or a
 * suspension end never needs a background job and a stale record can never grant authority.
 * {@link #REVOKED} is permanent until a new permit is issued.
 *
 * <p>Names are persisted verbatim in {@code gov.dat}. An unknown stored value reads as
 * {@link #REVOKED}: a damaged permit must not authorize anything.
 */
public enum PermitStatus {
    VALID,
    SUSPENDED,
    REVOKED,
    EXPIRED;

    /** Parses a stored status, defaulting to {@link #REVOKED} when unknown. */
    public static PermitStatus parse(final String name) {
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
