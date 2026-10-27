package com.horizonssmp.gov.server;

import java.util.List;
import java.util.Map;

import com.horizonssmp.gov.common.GovFields;

final class GovSafetyUnicodeChecks {

    private GovSafetyUnicodeChecks() {
    }

    /**
     * Text that leaves this server must be well-formed UTF-16: a lone surrogate becomes U+FFFD in
     * {@link Sanitizer}, and a length cap never cuts a surrogate pair in half. Every string sent on
     * the wire or written to disk is later encoded as UTF-8, where a lone half would be replaced
     * lossily instead of rejected.
     */
    static int checkUnicodeWellFormed(final List<String> failures) {
        int checks = 0;

        checks++;
        final String filtered = Sanitizer.text("\ud83d\ude00");
        if (!"\ud83d\ude00".equals(filtered) || GovSafetyHelpers.hasLoneSurrogate(filtered)) {
            failures.add("Sanitizer split a well-formed surrogate pair");
        }
        checks++;
        if (GovSafetyHelpers.hasLoneSurrogate(Sanitizer.text("\ud800"))
                || GovSafetyHelpers.hasLoneSurrogate(Sanitizer.text("\udfff"))) {
            failures.add("Sanitizer left a lone surrogate in the output");
        }
        checks++;
        // 506 'a's put the cut boundary exactly on the high half of the pair.
        final String capped = Sanitizer.text("a".repeat(Sanitizer.MAX_TEXT - 6)
                + "\ud83d\ude00" + "b".repeat(10));
        if (GovSafetyHelpers.hasLoneSurrogate(capped) || !capped.endsWith(Sanitizer.TRUNCATION_MARKER)) {
            failures.add("Sanitizer.text cut a surrogate pair at the truncation boundary");
        }
        checks++;
        // The first pair in a block is cut at MAX_ENCODED; the pair must not be split there either.
        final String oversized = "a".repeat(GovFields.MAX_ENCODED - 3)
                + "\ud83d\ude00" + "b";
        final String block = GovFields.encode(Map.of("k", oversized));
        final Map<String, String> decoded = GovFields.decode(block);
        if (GovSafetyHelpers.hasLoneSurrogate(block) || decoded.get("k") == null
                || GovSafetyHelpers.hasLoneSurrogate(decoded.get("k"))) {
            failures.add("GovFields.encode cut a surrogate pair at the truncation boundary");
        }
        return checks;
    }
}
