package com.horizonssmp.gov.server;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;

import com.horizonssmp.gov.config.GovConfig;
import com.horizonssmp.gov.common.OpCode;
import com.horizonssmp.gov.common.RateLimitPolicy;

/**
 * Per-player token bucket, plus a per-tick global backstop.
 *
 * <p>A token bucket rather than a fixed window: a player who has been idle for a minute can burst
 * without being punished for the shape of the window, while a client in a loop still runs out
 * after the configured number of actions and then refills at a steady rate. The bucket is per UUID,
 * so one player cannot exhaust another's allowance, and it is keyed by UUID rather than by
 * connection so a reconnect does not hand out a fresh allowance.
 *
 * <p>Every action has a {@link RateLimitPolicy}: reads and ordinary mutations cost one token, an
 * export costs three (it writes and hashes a file) and a season-reset step costs five (it writes
 * and verifies a full backup). The policy is per actor; the only shared counter is
 * {@link #allowGlobal()}, a final per-tick backstop that defends the server against many actors
 * asking at once. That counter is reset by {@link #beginTick()} from the server tick listener, and
 * a request over the cap gets the same {@code RATE_LIMITED} refusal as a private bucket failure.
 *
 * <p>The limiter is deliberately in memory. It is an abuse guard, not a quota: a restart clearing it
 * is correct, and persisting it would put a write on every action.
 */
public final class RateLimiter {

    /** The window the configured allowance is measured over. */
    public static final long WINDOW_NANOS = 10_000_000_000L;

    /** Buckets are pruned once the map grows past this size. */
    private static final int PRUNE_THRESHOLD = 512;

    /** A bucket untouched for this long is dropped during a prune. */
    private static final long IDLE_PRUNE_NANOS = 600_000_000_000L;

    private static final class Bucket {
        private double tokens;
        private long lastRefillNanos;
        /**
         * Volatile because {@link #prune(long)} reads it without holding the bucket's monitor.
         * Every other field is only touched while synchronized on the bucket. A stale read could
         * prune a bucket that was used a moment ago, handing that actor a fresh full allowance.
         */
        private volatile long lastUseNanos;
    }

    private final Map<UUID, Bucket> buckets = new ConcurrentHashMap<>();
    private final LongSupplier clock;
    /** {@code -1} means read the config; a test may pin a small cap. */
    private final int globalCapOverride;
    private final AtomicInteger tickRequests = new AtomicInteger();

    public RateLimiter() {
        this(System::nanoTime, -1);
    }

    /**
     * A limiter with a replaceable nanosecond clock, for deterministic boundary assertions.
     *
     * <p>Package-private: the startup check lives in this package. Production always uses the
     * monotonic system clock.
     */
    RateLimiter(final LongSupplier clock) {
        this(clock, -1);
    }

    /** A limiter with a pinned global cap, for the startup boundary assertions. */
    RateLimiter(final LongSupplier clock, final int globalCapOverride) {
        this.clock = clock == null ? System::nanoTime : clock;
        this.globalCapOverride = globalCapOverride;
    }

    /**
     * Takes the default cost for the actor. Used by the unknown-op path, which has no {@link OpCode}.
     *
     * @param actor the acting player's UUID, or {@code null} for a system action, which is never
     *     limited
     * @return true when the action may proceed
     */
    public boolean allow(final UUID actor) {
        return allow(actor, 1);
    }

    /**
     * Takes the policy cost for one action from the actor's bucket.
     *
     * @param actor the acting player's UUID, or {@code null} for a system action
     * @param op the action, or {@code null} for the default cost
     * @return true when the action may proceed
     */
    public boolean allow(final UUID actor, final OpCode op) {
        return allow(actor, RateLimitPolicy.forOp(op).cost());
    }

    /**
     * Takes a specific number of tokens from the actor's bucket.
     *
     * <p>The bucket capacity is the configured per-ten-second allowance. A cost larger than the
     * capacity is charged as one full bucket rather than refused outright: a server owner who sets
     * a very small allowance still gets one expensive action per window instead of an export or a
     * season step that can never run. The policy cost is unchanged for reporting and for the
     * normal case.
     */
    public boolean allow(final UUID actor, final int cost) {
        if (actor == null) {
            return true;
        }
        final int safeCost = Math.max(1, cost);
        final int capacity = Math.max(1, GovConfig.rateLimitActionsPer10s());
        final int effectiveCost = Math.min(safeCost, capacity);
        final double refillPerNano = capacity / (double) WINDOW_NANOS;
        final long now = clock.getAsLong();

        if (buckets.size() > PRUNE_THRESHOLD) {
            prune(now);
        }

        final Bucket bucket = buckets.computeIfAbsent(actor, key -> {
            final Bucket fresh = new Bucket();
            fresh.tokens = capacity;
            fresh.lastRefillNanos = now;
            fresh.lastUseNanos = now;
            return fresh;
        });

        synchronized (bucket) {
            final long elapsed = Math.max(0L, now - bucket.lastRefillNanos);
            bucket.tokens = Math.min(capacity, bucket.tokens + elapsed * refillPerNano);
            bucket.lastRefillNanos = now;
            bucket.lastUseNanos = now;
            if (bucket.tokens < effectiveCost) {
                return false;
            }
            bucket.tokens -= effectiveCost;
            return true;
        }
    }

    /**
     * Takes one slot from the per-tick global backstop.
     *
     * <p>Independent of the per-actor buckets: a request must pass both. The cap is
     * {@code gov.security.maxRequestsPerTick} unless a test pinned one. {@link #beginTick()} is the
     * only reset; a server that forgets to call it fails closed after the first tick's cap, which is
     * the safe direction for a backstop.
     */
    public boolean allowGlobal() {
        final int cap = globalCapOverride > 0
                ? globalCapOverride : Math.max(1, GovConfig.maxRequestsPerTick());
        return tickRequests.incrementAndGet() <= cap;
    }

    /** Starts a new server tick. Called from the game-bus tick listener. */
    public void beginTick() {
        tickRequests.set(0);
    }

    /** The number of requests seen in the current tick. Diagnostic and assertion only. */
    public int tickRequests() {
        return tickRequests.get();
    }

    /** Drops the bucket for one player. Intended for an explicit administrative reset. */
    public void forget(final UUID actor) {
        if (actor != null) {
            buckets.remove(actor);
        }
    }

    /** Number of buckets currently held. Diagnostic only. */
    public int size() {
        return buckets.size();
    }

    private void prune(final long now) {
        final Iterator<Map.Entry<UUID, Bucket>> iterator = buckets.entrySet().iterator();
        while (iterator.hasNext()) {
            final Bucket bucket = iterator.next().getValue();
            if (now - bucket.lastUseNanos > IDLE_PRUNE_NANOS) {
                iterator.remove();
            }
        }
    }
}
