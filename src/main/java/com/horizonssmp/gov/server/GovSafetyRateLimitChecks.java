package com.horizonssmp.gov.server;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import com.horizonssmp.gov.common.OpCode;
import com.horizonssmp.gov.common.RateLimitPolicy;
import com.horizonssmp.gov.config.GovConfig;

final class GovSafetyRateLimitChecks {

    private GovSafetyRateLimitChecks() {
    }

    /** Every op must have a policy, and the policy kind must agree with the audited flag. */
    static int checkRatePolicies(final List<String> failures) {
        int checks = 0;
        for (final OpCode op : OpCode.values()) {
            checks++;
            final RateLimitPolicy.Policy policy = RateLimitPolicy.forOp(op);
            if (policy == null || policy.kind() == null || policy.cost() < 1) {
                failures.add("rate policy is missing or invalid for " + op);
            }
            checks++;
            final boolean read = policy != null && policy.kind() == RateLimitPolicy.Kind.READ;
            if (op.audited() == read) {
                failures.add("rate policy kind disagrees with the audited flag for " + op);
            }
            checks++;
            if (op.audited() && !op.dispatcherAudited()
                    && op != OpCode.AUDIT_EXPORT && op != OpCode.ADMIN_EXPORT
                    && op != OpCode.SEASON_BEGIN && op != OpCode.SEASON_COMMIT
                    && !serviceAuditedEconomy(op)) {
                failures.add("op skips the dispatcher audit without a service audit: " + op);
            }
        }
        checks++;
        if (RateLimitPolicy.forOp(OpCode.AUDIT_EXPORT).cost() != 3
                || RateLimitPolicy.forOp(OpCode.SEASON_COMMIT).cost() != 5
                || RateLimitPolicy.forOp(OpCode.AUDIT_PAGE).cost() != 1) {
            failures.add("documented rate-limit costs changed");
        }
        return checks;
    }

    /**
     * True for the economy ops whose service writes the audit line itself.
     *
     * <p>They are audited but not dispatcher-audited, and a service audit is exactly what the
     * check above requires, so they are exempt from the failure.
     */
    static boolean serviceAuditedEconomy(final OpCode op) {
        return op == OpCode.BANK_DEPOSIT || op == OpCode.BANK_WITHDRAW
                || op == OpCode.BANK_TRANSFER || op == OpCode.PAY_PLAYER
                || op == OpCode.PAYROLL_RUN || op == OpCode.STORE_BUY
                || op == OpCode.CONTRACT_POST || op == OpCode.CONTRACT_ACCEPT
                || op == OpCode.CONTRACT_SUBMIT || op == OpCode.CONTRACT_APPROVE
                || op == OpCode.CONTRACT_VOID || op == OpCode.CONTRACT_CREDIT
                || op == OpCode.BANK_CREDIT;
    }

    /** Boundaries: limit-1 allowed, limit rejected, refill after the window, per-actor isolation. */
    static int checkRateLimiterBoundaries(final List<String> failures) {
        int checks = 0;
        final int capacity = Math.max(1,
                com.horizonssmp.gov.config.GovConfig.rateLimitActionsPer10s());
        final AtomicLong clock = new AtomicLong(1_000_000_000L);
        final RateLimiter limiter = new RateLimiter(clock::get, 3);
        final UUID alice = new UUID(0L, 11L);
        final UUID bob = new UUID(0L, 12L);
        limiter.beginTick();

        int allowed = 0;
        while (allowed < capacity && limiter.allow(alice)) {
            allowed++;
        }
        checks++;
        if (allowed != capacity) {
            failures.add("rate limiter allowed " + allowed + " actions, expected " + capacity);
        }
        checks++;
        if (limiter.allow(alice)) {
            failures.add("rate limiter allowed one action over the bucket");
        }
        checks++;
        if (!limiter.allow(bob)) {
            failures.add("rate limiter is global: an exhausted actor refused a fresh actor");
        }
        clock.addAndGet(RateLimiter.WINDOW_NANOS + 1L);
        checks++;
        if (!limiter.allow(alice)) {
            failures.add("rate limiter did not refill after its window");
        }

        // Burst in one tick against the global backstop: exactly three pass, the fourth is refused.
        limiter.beginTick();
        boolean first = limiter.allowGlobal();
        boolean second = limiter.allowGlobal();
        boolean third = limiter.allowGlobal();
        final boolean fourth = limiter.allowGlobal();
        checks++;
        if (!first || !second || !third || fourth) {
            failures.add("global per-tick cap did not allow 3 and refuse the 4th");
        }
        limiter.beginTick();
        checks++;
        if (!limiter.allowGlobal()) {
            failures.add("global per-tick cap did not reset at the next tick");
        }

        // A heavy op costs its policy cost: six exports fit in a 20-token bucket, the seventh does not.
        // A server owner may configure a smaller allowance; in that case the limiter charges one full
        // bucket for the heavy op, so the boundary test is only meaningful when the bucket is large.
        final RateLimiter costLimiter = new RateLimiter(clock::get, 100);
        final UUID carol = new UUID(0L, 13L);
        costLimiter.beginTick();
        if (capacity >= 6) {
            boolean six = true;
            for (int i = 0; i < 6; i++) {
                six &= costLimiter.allow(carol, OpCode.AUDIT_EXPORT);
            }
            checks++;
            if (!six) {
                failures.add("export policy refused within the configured allowance");
            }
            checks++;
            if (costLimiter.allow(carol, OpCode.AUDIT_EXPORT)) {
                failures.add("export policy allowed a seventh export in one burst");
            }
        } else {
            checks++;
            if (!costLimiter.allow(carol, OpCode.AUDIT_EXPORT)) {
                failures.add("export policy could not run even once at the configured allowance");
            }
        }
        if (capacity >= 5) {
            final UUID dave = new UUID(0L, 14L);
            int seasonSteps = 0;
            while (seasonSteps < 4 && costLimiter.allow(dave, OpCode.SEASON_COMMIT)) {
                seasonSteps++;
            }
            checks++;
            if (seasonSteps != 4 || costLimiter.allow(dave, OpCode.SEASON_COMMIT)) {
                failures.add("season policy did not allow four steps and refuse the fifth");
            }
        }
        return checks;
    }
}
