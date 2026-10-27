package com.horizonssmp.gov.common.enums;

import java.util.Locale;

/**
 * The lifecycle state of an economy account.
 *
 * <p>{@link #FROZEN} forbids debits but still allows credits; {@link #CLOSED} forbids both and is
 * retained with its history forever. The status is stored by {@link #name()} in {@code gov.dat}.
 *
 * <p>An unknown or missing status reads as {@link #CLOSED}, never {@link #OPEN}. This is the
 * fail-closed default: a typo in a data file must not re-open an account that an operator closed,
 * and the worst case is that an operator has to re-open a damaged account explicitly.
 */
public enum AccountStatus {
    /** Normal operation: debits and credits are both allowed. */
    OPEN,
    /** Debits refused, credits allowed. */
    FROZEN,
    /** Both refused. Terminal; the record is kept for audit. */
    CLOSED;

    /**
     * Returns the status for a stored name, defaulting to {@link #CLOSED} when the value is blank
     * or unknown. Both the enum name and the lower-case name are accepted.
     */
    public static AccountStatus fromName(final String raw) {
        if (raw == null || raw.isBlank()) {
            return CLOSED;
        }
        final String needle = raw.trim().toUpperCase(Locale.ROOT);
        for (final AccountStatus value : values()) {
            if (value.name().equals(needle)) {
                return value;
            }
        }
        return CLOSED;
    }

    /** Alias for {@link #fromName(String)} in the style of the older stored enums. */
    public static AccountStatus parse(final String raw) {
        return fromName(raw);
    }
}
