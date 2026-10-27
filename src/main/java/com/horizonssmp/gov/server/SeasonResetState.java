package com.horizonssmp.gov.server;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.horizonssmp.gov.server.SeasonResetService.Actor;
import com.horizonssmp.gov.server.SeasonResetService.Plan;

/**
 * Holds the in-memory pending reset tokens. Package-private helper for
 * {@link SeasonResetService}; no behaviour change.
 */
public final class SeasonResetState {

    static final java.security.SecureRandom RANDOM = new java.security.SecureRandom();
    static final Map<String, Pending> PENDING = new ConcurrentHashMap<>();

    private SeasonResetState() {
    }

    /** A pending (issued but not consumed) reset token. */
    public record Pending(
            UUID actor, UUID dataId, boolean clearBusinesses,
            Plan plan, long expiresAt) {
    }

    public static void pruneExpired(final long now) {
        PENDING.entrySet().removeIf(entry -> entry.getValue().expiresAt() < now);
    }

    public static long pendingExpiry(
            final com.horizonssmp.gov.server.GovSavedData data,
            final Actor actor, final long now) {
        if (data == null || actor == null) {
            return 0L;
        }
        pruneExpired(now);
        long latest = 0L;
        for (final Pending pending : PENDING.values()) {
            if (actor.uuid().equals(pending.actor())
                    && pending.dataId().equals(data.instanceId())
                    && pending.expiresAt() >= now) {
                latest = Math.max(latest, pending.expiresAt());
            }
        }
        return latest;
    }

    public static String newToken() {
        final byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        final StringBuilder out = new StringBuilder(bytes.length * 2);
        for (final byte b : bytes) {
            out.append(String.format(java.util.Locale.ROOT, "%02x", b));
        }
        return out.toString();
    }
}