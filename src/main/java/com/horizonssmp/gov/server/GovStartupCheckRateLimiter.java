package com.horizonssmp.gov.server;

import java.util.List;
import java.util.UUID;
import com.horizonssmp.gov.config.GovConfig;

final class GovStartupCheckRateLimiter {

    private GovStartupCheckRateLimiter() {
    }

    static int run(final List<String> failures) {
        int checks = 0;
        final int allowance = Math.max(1, GovConfig.rateLimitActionsPer10s());
        final RateLimiter limiter = new RateLimiter();
        final UUID probe = new UUID(0L, 1L);

        int allowed = 0;
        while (allowed <= allowance && limiter.allow(probe)) {
            allowed++;
        }
        checks++;
        if (allowed != allowance) {
            failures.add("rate limiter allowed " + allowed + " actions, expected " + allowance);
        }

        checks++;
        if (!limiter.allow(new UUID(0L, 2L))) {
            failures.add("rate limiter refused a fresh player");
        }

        checks++;
        if (!limiter.allow(null)) {
            failures.add("rate limiter refused a system action");
        }
        return checks;
    }

    /** The sanitizer must remove what it documents and cap what it documents. */

}
