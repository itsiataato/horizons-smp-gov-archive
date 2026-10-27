package com.horizonssmp.gov.common.enums;

import java.util.Locale;

/**
 * The lifecycle of one employment contract.
 *
 * <p>{@link #OFFERED} is a pending offer: the player has not accepted it and it grants no business
 * position. {@link #ACTIVE} is the only status that occupies a business slot or authorizes anything.
 * The remaining statuses are terminal; a terminated contract is kept forever so the complaint,
 * payroll and history records that point at it still resolve.
 *
 * <p>Names are persisted in {@code gov.dat}. Do not rename a constant without a schema migration.
 *
 * <p>The tolerant parser is deliberately fail-safe: an unreadable status reads as
 * {@link #TERMINATED}, never {@link #ACTIVE}. A typo in a file must not create an employee with a
 * live wage and a live business slot. The worst case is that an operator re-hires someone; the
 * other default would grant authority from a damaged record.
 */
public enum EmploymentStatus {
    /** Sent to the player and awaiting an accept or decline. Grants no position. */
    OFFERED,
    /** Accepted and in force. The only status that occupies the one business-position slot. */
    ACTIVE,
    /** The player refused the offer. Terminal. */
    DECLINED,
    /** The employer ended the contract. Terminal. */
    TERMINATED,
    /** The employee ended the contract. Terminal. */
    RESIGNED,
    /** The contract's term ran out. Terminal. */
    EXPIRED,
    /** A one-time task was finished and the contract closed. Terminal. */
    COMPLETED;

    /**
     * Parses a stored status, defaulting to the safe {@link #TERMINATED}.
     *
     * <p>Both the enum name and the lowercase name are accepted, because a hand-edited file may use
     * either. Blank and unknown values are terminated rather than offered or active.
     */
    public static EmploymentStatus parse(final String raw) {
        if (raw == null || raw.isBlank()) {
            return TERMINATED;
        }
        final String needle = raw.trim().toUpperCase(Locale.ROOT);
        for (final EmploymentStatus value : values()) {
            if (value.name().equals(needle)) {
                return value;
            }
        }
        return TERMINATED;
    }

    /** True when this status has ended and can never become active again. */
    public boolean terminal() {
        return this != OFFERED && this != ACTIVE;
    }

    /** True when the contract is still awaiting an answer or in force. */
    public boolean open() {
        return this == OFFERED || this == ACTIVE;
    }

    /** True when this status occupies the player's one business-position slot. */
    public boolean occupiesPosition() {
        return this == ACTIVE;
    }
}
