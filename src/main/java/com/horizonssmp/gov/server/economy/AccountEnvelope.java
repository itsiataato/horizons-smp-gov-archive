package com.horizonssmp.gov.server.economy;

import java.util.UUID;
import java.util.function.Supplier;

import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.OpCode;
import com.horizonssmp.gov.common.RateLimitPolicy;
import com.horizonssmp.gov.common.economy.EconomyMath;
import com.horizonssmp.gov.common.economy.EconomyResult;
import com.horizonssmp.gov.common.records.EconomyMeta;

final class AccountEnvelope {

    private static final int RATE_SWEEP_EVERY = 256;
    private static final int RATE_SOFT_LIMIT = 4096;
    private static final int RATE_HARD_LIMIT = 8192;

    private AccountEnvelope() {
    }

    public record RateKey(UUID actor, OpCode op) {
    }

    public record Bucket(long start, int remaining) {
    }

    static EconomyResult takeEconomyToken(final AccountService service, final UUID actor, final OpCode op) {
        if (op == null || !RateLimitPolicy.usesEconomyBucket(op)) {
            return EconomyResult.success();
        }
        if (actor == null) {
            return EconomyResult.failure(
                    ErrorCode.BAD_PAYLOAD, "An economy action requires an actor.");
        }
        final long now = service.now();
        final RateKey key = new RateKey(actor, op);
        final boolean[] granted = {false};
        service.economyBuckets.compute(key, (ignored, bucket) -> {
            if (bucket == null
                    || now - bucket.start() >= RateLimitPolicy.ECONOMY_WINDOW_MILLIS) {
                granted[0] = true;
                return new Bucket(now, RateLimitPolicy.ECONOMY_BUCKET_TOKENS - 1);
            }
            if (bucket.remaining() <= 0) {
                return bucket;
            }
            granted[0] = true;
            return new Bucket(bucket.start(), bucket.remaining() - 1);
        });
        sweepRateLimits(service, now);
        return granted[0]
                ? EconomyResult.success()
                : EconomyResult.failure(ErrorCode.RATE_LIMITED);
    }

    static EconomyResult acceptNonce(final AccountService service, final UUID actor, final String nonce) {
        if (actor == null) {
            return EconomyResult.failure(
                    ErrorCode.BAD_PAYLOAD, "An economy action requires an actor.");
        }
        if (!EconomyMath.validNonce(nonce)) {
            return EconomyResult.failure(
                    ErrorCode.BAD_PAYLOAD, "The request nonce is not valid.");
        }
        final EconomyMeta meta = service.data().economyMeta();
        if (meta.hasNonce(actor, nonce)) {
            return EconomyResult.failure(ErrorCode.DUPLICATE_REQUEST);
        }
        service.data().setEconomyMeta(meta.addNonce(actor, nonce));
        return EconomyResult.success();
    }

    static EconomyResult withActorLock(
            final AccountService service, final UUID actor, final Supplier<EconomyResult> work) {
        if (work == null) {
            throw new IllegalArgumentException("withActorLock requires work");
        }
        final java.util.concurrent.locks.ReentrantLock lock = actor == null
                ? service.systemLock
                : service.actorLocks.computeIfAbsent(actor, key -> new java.util.concurrent.locks.ReentrantLock());
        if (!lock.tryLock()) {
            return EconomyResult.failure(ErrorCode.BUSY);
        }
        try {
            return AccountUtil.requireResult(work.get());
        } finally {
            lock.unlock();
        }
    }

    static EconomyResult withBusinessLock(
            final AccountService service, final String businessId, final Supplier<EconomyResult> work) {
        if (work == null) {
            throw new IllegalArgumentException("withBusinessLock requires work");
        }
        if (businessId == null || businessId.isBlank()) {
            return AccountUtil.requireResult(work.get());
        }
        final java.util.concurrent.locks.ReentrantLock lock =
                service.businessLocks.computeIfAbsent(businessId.strip(), key -> new java.util.concurrent.locks.ReentrantLock());
        if (!lock.tryLock()) {
            return EconomyResult.failure(ErrorCode.BUSY);
        }
        try {
            return AccountUtil.requireResult(work.get());
        } finally {
            lock.unlock();
        }
    }

    static EconomyResult withLocks(
            final AccountService service,
            final UUID actor,
            final String businessId,
            final Supplier<EconomyResult> work) {
        if (work == null) {
            throw new IllegalArgumentException("withLocks requires work");
        }
        return withActorLock(service, actor, () -> withBusinessLock(service, businessId, work));
    }

    private static void sweepRateLimits(final AccountService service, final long now) {
        service.rateCalls++;
        if (service.rateCalls % RATE_SWEEP_EVERY != 0 && service.economyBuckets.size() <= RATE_SOFT_LIMIT) {
            return;
        }
        service.economyBuckets.entrySet().removeIf(
                entry -> now - entry.getValue().start()
                        >= RateLimitPolicy.ECONOMY_WINDOW_MILLIS);
        if (service.economyBuckets.size() > RATE_HARD_LIMIT) {
            service.economyBuckets.clear();
        }
    }
}