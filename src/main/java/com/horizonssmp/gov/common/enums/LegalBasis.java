package com.horizonssmp.gov.common.enums;

import java.util.Locale;

/**
 * Why the state was allowed to hold or search a person. Every coercive action carries one of these
 * so the audit trail can answer "under what authority" without reading the free-text reason.
 *
 * <p>Names are persisted verbatim in {@code gov.dat}. Do not rename constants without a schema
 * migration.
 */
public enum LegalBasis {
    /** The subject agreed, for example at the government building reception. */
    CONSENT,
    /** A violent offence was observed or is being investigated. */
    VIOLENT_OFFENSE,
    /** A warrant covers this action. */
    WARRANT;

    /** Returns the basis for a stored name, defaulting to {@link #CONSENT} when unknown. */
    public static LegalBasis parse(final String name) {
        if (name == null || name.isBlank()) {
            return CONSENT;
        }
        try {
            return valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException e) {
            return CONSENT;
        }
    }
}
