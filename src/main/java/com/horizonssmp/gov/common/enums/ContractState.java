package com.horizonssmp.gov.common.enums;

import java.util.Locale;

/**
 * The lifecycle of a Government building contract.
 *
 * <p>Only {@link #POSTED}, {@link #ACCEPTED} and {@link #SUBMITTED} are non-terminal. The state is
 * stored by {@link #name()} in {@code gov.dat}; do not rename a constant without a schema
 * migration.
 *
 * <p>An unknown or missing state reads as {@link #VOIDED}, never as an open state. A damaged
 * record must not tie up escrow or let a builder submit against a contract whose real state is
 * unreadable; an operator can post a fresh contract instead.
 */
public enum ContractState {
    /** Published by Government and awaiting the named builder. */
    POSTED,
    /** The builder accepted; the acceptance deadline is now running. */
    ACCEPTED,
    /** The builder delivered and awaits Government review. */
    SUBMITTED,
    /** Government approved and the escrow was released. Terminal. */
    APPROVED,
    /** Government voided the contract with a reason. Terminal. */
    VOIDED,
    /** The deadline passed without completion. Terminal. */
    EXPIRED;

    /**
     * Returns the state for a stored name, defaulting to {@link #VOIDED} when the value is blank
     * or unknown. Both the enum name and the lower-case name are accepted.
     */
    public static ContractState fromName(final String raw) {
        if (raw == null || raw.isBlank()) {
            return VOIDED;
        }
        final String needle = raw.trim().toUpperCase(Locale.ROOT);
        for (final ContractState value : values()) {
            if (value.name().equals(needle)) {
                return value;
            }
        }
        return VOIDED;
    }

    /** Alias for {@link #fromName(String)} in the style of the older stored enums. */
    public static ContractState parse(final String raw) {
        return fromName(raw);
    }

    /** True when this state can never change again. */
    public boolean terminal() {
        return this == APPROVED || this == VOIDED || this == EXPIRED;
    }
}
