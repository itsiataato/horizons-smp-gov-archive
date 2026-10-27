package com.horizonssmp.gov.server;

import java.util.List;
import java.util.Map;
import com.horizonssmp.gov.Gov;

final class GovStartupCheckSanitizer {

    private GovStartupCheckSanitizer() {
    }

    static int run(final List<String> failures) {
        int checks = 0;

        checks++;
        if (!"alert".equals(Sanitizer.singleLine("\u00a7c\u00a7lalert\u00a7r", 64))) {
            failures.add("sanitizer kept a section sign");
        }

        checks++;
        if (Sanitizer.singleLine("line\nbreak\ttab", 64).contains("\n")) {
            failures.add("sanitizer kept a newline in single-line text");
        }

        checks++;
        final String capped = Sanitizer.singleLine("x".repeat(Sanitizer.MAX_TEXT * 2), Sanitizer.MAX_TEXT);
        if (capped.length() > Sanitizer.MAX_TEXT + Sanitizer.TRUNCATION_MARKER.length()) {
            failures.add("sanitizer did not cap a long value");
        }

        checks++;
        if (!"gov_worker".equals(Sanitizer.id("Gov Worker!!"))) {
            failures.add("sanitizer did not normalize an id: " + Sanitizer.id("Gov Worker!!"));
        }

        checks++;
        if (!Sanitizer.payloadSummary(Map.of("b", "2", "a", "1")).startsWith("a=1")) {
            failures.add("payload summary is not key-sorted");
        }

        checks++;
        if (!Sanitizer.multiLine("first\nsecond", 64).contains("\n")) {
            failures.add("sanitizer dropped a newline from multi-line text");
        }

        checks++;
        if (Sanitizer.singleLine("carriage\rreturn", 64).contains("\r")) {
            failures.add("sanitizer kept a carriage return");
        }
        return checks;
    }

    /** The law index must cover the same articles as the law it wraps. */

}
