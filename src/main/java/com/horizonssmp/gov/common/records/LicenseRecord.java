package com.horizonssmp.gov.common.records;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.horizonssmp.gov.common.GovTime;
import com.horizonssmp.gov.common.enums.LicenseClass;
import com.horizonssmp.gov.common.enums.LicenseStatus;

import net.minecraft.nbt.CompoundTag;

/**
 * One person's licence for one vehicle class.
 *
 * <p>There is exactly one record per {@code (player, class)} pair, keyed by
 * {@code <uuid>:<class-id>}. The record is never erased: revoking sets
 * {@link LicenseStatus#REVOKED} and records who and why, and issuing again reuses the same key with
 * a fresh issue stamp and an empty point history. Keeping the key means an audit line that names a
 * licence still resolves after the licence has been revoked or reissued.
 *
 * <p>Points are stored as individual entries with the time, the official and the reason. The
 * 30-in-game-day window from Article 6.09 is <b>computed at read time</b> by
 * {@link #activePoints(long)}; an old entry is not deleted, because the raw history is what lets a
 * later reader check the arithmetic. The suspension threshold and duration live in the vehicle
 * service, which is the only place that turns points into a suspension.
 *
 * <p>The current status is stored, but the effective status is {@link #stateAt(long)}: a suspension
 * whose {@code suspendedUntil} has passed is valid again without a write, and a revoked licence is
 * never revived by time.
 *
 * @param id the stable licence id: {@code <player-uuid>:<class-id>}
 * @param playerUuid the holder
 * @param licenseClass the class this licence authorizes
 * @param status the stored status
 * @param issuedAt epoch milliseconds the licence was issued
 * @param issuedBy the registrar who issued it, or {@code null} if unknown
 * @param revokedAt epoch milliseconds of the revocation, {@code 0} when never
 * @param revokedBy the official who revoked it, or {@code null}
 * @param revokeReason the recorded revocation reason, or empty
 * @param suspendedAt epoch milliseconds of the most recent suspension, {@code 0} when never
 * @param suspendedUntil epoch milliseconds the suspension ends, {@code 0} when never
 * @param suspendedBy the official who imposed the most recent suspension, or {@code null}
 * @param suspendReason the recorded suspension reason, or empty
 * @param points every point entry ever recorded, oldest first
 */
public record LicenseRecord(
        String id,
        UUID playerUuid,
        LicenseClass licenseClass,
        LicenseStatus status,
        long issuedAt,
        UUID issuedBy,
        long revokedAt,
        UUID revokedBy,
        String revokeReason,
        long suspendedAt,
        long suspendedUntil,
        UUID suspendedBy,
        String suspendReason,
        List<PointEntry> points) {

    /** Article 6.09: points expire after 30 in-game days. */
    public static final long POINT_WINDOW_MS = GovTime.days(30);

    /** Safety cap on stored point entries; the audit log is the full history. */
    public static final int MAX_POINT_ENTRIES = 256;

    /**
     * One recorded point assessment.
     *
     * @param points the points assessed: 2, 4 or 6 under Article 6.09
     * @param at epoch milliseconds of the assessment
     * @param by the official who assessed them, or {@code null} if unknown
     * @param reason the recorded reason, or empty
     */
    public record PointEntry(int points, long at, UUID by, String reason) {

        public PointEntry {
            reason = reason == null ? "" : reason;
        }

        /** Serializes this entry into a fresh tag. */
        public CompoundTag write() {
            final CompoundTag tag = new CompoundTag();
            tag.putInt("points", points);
            tag.putLong("at", at);
            NbtSupport.putUuid(tag, "by", by);
            tag.putString("reason", reason);
            return tag;
        }

        /** Reads an entry, returning {@code null} when it has no positive point value. */
        public static PointEntry read(final CompoundTag tag) {
            final int points = NbtSupport.getInt(tag, "points", 0);
            if (points <= 0) {
                return null;
            }
            return new PointEntry(
                    points,
                    NbtSupport.getLong(tag, "at", 0L),
                    NbtSupport.getUuid(tag, "by"),
                    NbtSupport.getString(tag, "reason", ""));
        }
    }

    public LicenseRecord {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("LicenseRecord requires an id");
        }
        if (playerUuid == null) {
            throw new IllegalArgumentException("LicenseRecord requires a player uuid");
        }
        if (licenseClass == null) {
            throw new IllegalArgumentException("LicenseRecord requires a license class");
        }
        status = status == null ? LicenseStatus.REVOKED : status;
        revokeReason = revokeReason == null ? "" : revokeReason;
        suspendReason = suspendReason == null ? "" : suspendReason;
        points = points == null ? List.of() : List.copyOf(points);
    }

    /** The key for one holder and class, the same form used as the data-file key. */
    public static String key(final UUID playerUuid, final LicenseClass licenseClass) {
        return (playerUuid == null ? "" : playerUuid.toString())
                + ":" + (licenseClass == null ? "" : licenseClass.id());
    }

    /** A fresh licence with no points and no suspensions. */
    public static LicenseRecord issued(
            final UUID playerUuid, final LicenseClass licenseClass,
            final UUID issuedBy, final long now) {
        return new LicenseRecord(
                key(playerUuid, licenseClass), playerUuid, licenseClass, LicenseStatus.VALID,
                now, issuedBy, 0L, null, "", 0L, 0L, null, "", List.of());
    }

    /**
     * The effective status at {@code now}.
     *
     * <p>Revoked wins over everything. A suspension whose end has passed reads as valid, which is
     * the read-time half of "points expire after 30 in-game days".
     */
    public LicenseStatus stateAt(final long now) {
        if (status == LicenseStatus.REVOKED) {
            return LicenseStatus.REVOKED;
        }
        if (suspendedUntil > now) {
            return LicenseStatus.SUSPENDED;
        }
        return LicenseStatus.VALID;
    }

    /** True when the licence authorizes its class right now. */
    public boolean validAt(final long now) {
        return stateAt(now) == LicenseStatus.VALID;
    }

    /** True when this licence authorizes driving or piloting the requested class right now. */
    public boolean authorizesAt(final long now, final LicenseClass requested) {
        return validAt(now) && licenseClass.covers(requested);
    }

    /**
     * The sum of point entries still inside the 30-in-game-day window at {@code now}.
     *
     * <p>Computed, never stored: editing a clock or loading an old file cannot leave a stale total.
     * The sum is clamped to {@link Integer#MAX_VALUE}; a real record has a handful of entries.
     */
    public int activePoints(final long now) {
        final long cutoff = now - POINT_WINDOW_MS;
        long total = 0L;
        for (final PointEntry entry : points) {
            if (entry != null && entry.at() >= cutoff) {
                total += entry.points();
                if (total >= Integer.MAX_VALUE) {
                    return Integer.MAX_VALUE;
                }
            }
        }
        return (int) total;
    }

    /** The most recent point entry, or {@code null} when the licence has none. */
    public PointEntry latestPoint() {
        return points.isEmpty() ? null : points.get(points.size() - 1);
    }

    /** Returns a copy with a new point entry appended. */
    public LicenseRecord withPoint(final PointEntry entry) {
        final List<PointEntry> next = new ArrayList<>(points);
        if (entry != null) {
            next.add(entry);
        }
        if (next.size() > MAX_POINT_ENTRIES) {
            // Keep the newest entries. The audit log carries the full action history; this cap
            // keeps one hand-edited or scripted record from growing the save without bound.
            next.subList(0, next.size() - MAX_POINT_ENTRIES).clear();
        }
        return new LicenseRecord(
                id, playerUuid, licenseClass, status, issuedAt, issuedBy,
                revokedAt, revokedBy, revokeReason,
                suspendedAt, suspendedUntil, suspendedBy, suspendReason,
                next);
    }

    /** Returns a copy suspended until {@code until}, with the reason and the official recorded. */
    public LicenseRecord suspended(
            final UUID by, final String reason, final long now, final long until) {
        return new LicenseRecord(
                id, playerUuid, licenseClass, LicenseStatus.SUSPENDED, issuedAt, issuedBy,
                revokedAt, revokedBy, revokeReason,
                now, Math.max(now, until), by, reason == null ? "" : reason,
                points);
    }

    /** Returns a copy revoked now, with the official and the reason recorded. */
    public LicenseRecord revoked(final UUID by, final String reason, final long now) {
        return new LicenseRecord(
                id, playerUuid, licenseClass, LicenseStatus.REVOKED, issuedAt, issuedBy,
                now, by, reason == null ? "" : reason,
                suspendedAt, suspendedUntil, suspendedBy, suspendReason,
                points);
    }

    /** Serializes this licence into a fresh tag. */
    public CompoundTag write() {
        final CompoundTag tag = new CompoundTag();
        tag.putString("id", id);
        NbtSupport.putUuid(tag, "playerUuid", playerUuid);
        tag.putString("licenseClass", licenseClass.id());
        tag.putString("status", status.name());
        tag.putLong("issuedAt", issuedAt);
        NbtSupport.putUuid(tag, "issuedBy", issuedBy);
        tag.putLong("revokedAt", revokedAt);
        NbtSupport.putUuid(tag, "revokedBy", revokedBy);
        tag.putString("revokeReason", revokeReason);
        tag.putLong("suspendedAt", suspendedAt);
        tag.putLong("suspendedUntil", suspendedUntil);
        NbtSupport.putUuid(tag, "suspendedBy", suspendedBy);
        tag.putString("suspendReason", suspendReason);
        NbtSupport.putCompoundList(tag, "points",
                points.stream().map(PointEntry::write).toList());
        return tag;
    }

    /**
     * Reads a licence.
     *
     * <p>Tolerant by design: missing fields get defaults, an unknown status reads as revoked, and a
     * point entry without a positive value is skipped. An unknown <b>class</b> returns {@code null}
     * because there is no safe default class, and a Quad licence silently becoming a Car licence is
     * worse than one damaged record being skipped with a warning by the loader.
     *
     * @param tag the stored tag
     * @param fallbackId the key from the map, used when the tag lost its own id
     * @return the record, or {@code null} when the tag names no known class
     */
    public static LicenseRecord read(final CompoundTag tag, final String fallbackId) {
        final UUID playerUuid = NbtSupport.getUuid(tag, "playerUuid");
        final LicenseClass licenseClass =
                LicenseClass.parse(NbtSupport.getString(tag, "licenseClass", ""));
        if (playerUuid == null || licenseClass == null) {
            return null;
        }
        String id = NbtSupport.getString(tag, "id", "");
        if (id.isBlank()) {
            id = fallbackId == null || fallbackId.isBlank()
                    ? key(playerUuid, licenseClass) : fallbackId;
        }
        final List<PointEntry> points = new ArrayList<>();
        for (final CompoundTag element : NbtSupport.getCompoundList(tag, "points")) {
            final PointEntry entry = PointEntry.read(element);
            if (entry != null) {
                points.add(entry);
            }
        }
        return new LicenseRecord(
                id,
                playerUuid,
                licenseClass,
                LicenseStatus.parse(NbtSupport.getString(tag, "status", "")),
                NbtSupport.getLong(tag, "issuedAt", 0L),
                NbtSupport.getUuid(tag, "issuedBy"),
                NbtSupport.getLong(tag, "revokedAt", 0L),
                NbtSupport.getUuid(tag, "revokedBy"),
                NbtSupport.getString(tag, "revokeReason", ""),
                NbtSupport.getLong(tag, "suspendedAt", 0L),
                NbtSupport.getLong(tag, "suspendedUntil", 0L),
                NbtSupport.getUuid(tag, "suspendedBy"),
                NbtSupport.getString(tag, "suspendReason", ""),
                points);
    }
}
