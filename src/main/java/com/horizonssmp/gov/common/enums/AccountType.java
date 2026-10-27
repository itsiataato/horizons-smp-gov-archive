package com.horizonssmp.gov.common.enums;

import java.util.Locale;

/**
 * The four kinds of custodial account defined by the economy specification.
 *
 * <p>{@link #PLAYER} and {@link #BUSINESS} are owned accounts and are taxable;
 * {@link #GOVERNMENT} and {@link #POLICE} are singletons that are never taxed and are never
 * deleted. The type is stored by {@link #name()} in {@code gov.dat}; do not rename a constant
 * without a schema migration.
 *
 * <p>An unknown or missing type reads as {@link #PLAYER}. That is the least privileged type: a
 * player account still requires an owner and an {@code OPEN} status before it can be debited, so a
 * damaged record cannot silently become a Government account that spends without a reason.
 */
public enum AccountType {
    /** A player's own custodial account, id {@code player:<uuid>}. */
    PLAYER,
    /** The Government singleton, id {@code government}. */
    GOVERNMENT,
    /** The Police singleton, id {@code police}. */
    POLICE,
    /** A registered business's account, id {@code business:<businessId>}. */
    BUSINESS;

    /**
     * Returns the type for a stored name, defaulting to {@link #PLAYER} when the value is blank or
     * unknown. Both the enum name and the lower-case name are accepted.
     */
    public static AccountType fromName(final String raw) {
        if (raw == null || raw.isBlank()) {
            return PLAYER;
        }
        final String needle = raw.trim().toUpperCase(Locale.ROOT);
        for (final AccountType value : values()) {
            if (value.name().equals(needle)) {
                return value;
            }
        }
        return PLAYER;
    }

    /** Alias for {@link #fromName(String)} in the style of the older stored enums. */
    public static AccountType parse(final String raw) {
        return fromName(raw);
    }
}
