package com.horizonssmp.gov.common.enums;

import java.util.Locale;

/**
 * Reason a vehicle was impounded. The release fee in {@code ImpoundRecord.releaseFeeGold} is read
 * from config when the impound is created, so changing the config never rewrites history.
 *
 * <p>Names are persisted verbatim in {@code gov.dat}. Do not rename constants without a schema
 * migration.
 */
public enum ImpoundReason {
    NO_LICENSE,
    NO_PLATE,
    IMPAIRED,
    BLOCKING,
    /** Article 6.14: driving with a suspended, forged or borrowed licence. */
    UNLICENSED_OPERATION;

    /** Returns the reason for a stored name, defaulting to {@link #BLOCKING} when unknown. */
    public static ImpoundReason parse(final String name) {
        if (name == null || name.isBlank()) {
            return BLOCKING;
        }
        try {
            return valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException e) {
            return BLOCKING;
        }
    }

    /**
     * A typed index for a free-text impound reason, or {@link #BLOCKING} when nothing matches.
     *
     * <p>The free text recorded on the impound is authoritative; this classification exists only so
     * a list can group impounds by the grounds Article 6.11/6.14 name. The match is deliberately
     * conservative: a reason that says "no plate" is {@link #NO_PLATE}, and an unclassifiable
     * reason is not guessed into a more specific ground than blocking.
     */
    public static ImpoundReason classify(final String reasonText) {
        final String text = reasonText == null ? "" : reasonText.toLowerCase(Locale.ROOT);
        if (text.contains("suspended") || text.contains("forged") || text.contains("borrowed")
                || text.contains("unlicensed")) {
            return UNLICENSED_OPERATION;
        }
        if (text.contains("plate")) {
            return NO_PLATE;
        }
        if (text.contains("impair") || text.contains("drunk") || text.contains("intoxic")) {
            return IMPAIRED;
        }
        if (text.contains("licen") || text.contains("licence")) {
            return NO_LICENSE;
        }
        return BLOCKING;
    }
}
