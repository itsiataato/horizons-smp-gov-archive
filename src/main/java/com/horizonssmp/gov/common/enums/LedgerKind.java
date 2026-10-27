package com.horizonssmp.gov.common.enums;

import java.util.Locale;

/**
 * The business reason for one ledger entry.
 *
 * <p>The kind is descriptive, never authoritative: authorization and tax are decided by the
 * service before an entry is built. It is stored by {@link #name()} in {@code gov.dat} so a human
 * can read the history without resolving a numeric code.
 *
 * <p>An unknown or missing kind reads as {@link #TRANSFER}. That default changes no balance and
 * grants no exemption: a damaged tag must not turn into a {@link #TAX} or {@link #FINE} entry that
 * implies Government revenue.
 */
public enum LedgerKind {
    /** Physical gold entered an account from the world. */
    DEPOSIT,
    /** Physical gold left an account into the world. */
    WITHDRAW,
    /** An account-to-account transfer by a bank official. */
    TRANSFER,
    /** A player-to-player payment. */
    PAY,
    /** The tax share of a taxed transaction, credited to Government. */
    TAX,
    /** A fine principal credited to Government. */
    FINE,
    /** A Government Store purchase. */
    STORE,
    /** One payroll payout, or the batch's aggregate entry. */
    PAYROLL,
    /** A contract escrow movement, release or penalty. */
    CONTRACT,
    /** A business dissolution remainder paid to its CEO. */
    DISSOLUTION;

    /**
     * Returns the kind for a stored name, defaulting to {@link #TRANSFER} when the value is blank
     * or unknown. Both the enum name and the lower-case name are accepted.
     */
    public static LedgerKind fromName(final String raw) {
        if (raw == null || raw.isBlank()) {
            return TRANSFER;
        }
        final String needle = raw.trim().toUpperCase(Locale.ROOT);
        for (final LedgerKind value : values()) {
            if (value.name().equals(needle)) {
                return value;
            }
        }
        return TRANSFER;
    }

    /** Alias for {@link #fromName(String)} in the style of the older stored enums. */
    public static LedgerKind parse(final String raw) {
        return fromName(raw);
    }
}
