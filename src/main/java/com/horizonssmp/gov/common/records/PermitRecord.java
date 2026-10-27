package com.horizonssmp.gov.common.records;

import java.util.UUID;

import com.horizonssmp.gov.common.enums.PermitClass;
import com.horizonssmp.gov.common.enums.PermitStatus;

import net.minecraft.nbt.CompoundTag;

/**
 * One permit in the Article 8.12 registry: a weapon permit, a Class B blasting permit, or a mining
 * permit. They share a record because the law puts them in one public registry and because the
 * lifecycle (issue, suspend, revoke, expire) is the same.
 *
 * <p>There is exactly one record per {@code (holder, class)} pair, keyed by
 * {@code <uuid>:<class-id>}. Re-issuing an expired or revoked permit reuses the key, so an old
 * audit line still resolves. The record is never erased.
 *
 * <p>The effective state is {@link #stateAt(long)}. A suspension whose end has passed is valid
 * again without a write; an expiry is computed from the stored stamp; revocation is permanent.
 * This mirrors {@code LicenseRecord}, intentionally: an official should not have to learn two
 * different lifecycle rules.
 *
 * @param id the stable record id, {@code <uuid>:<class-id>}
 * @param holderUuid the permit holder
 * @param holderName the display name copied at issue time, for an offline list
 * @param permitClass the class this permit authorizes
 * @param status the stored status
 * @param issuedBy the official who issued it, or {@code null} if unknown
 * @param issuedAt epoch milliseconds the permit was issued
 * @param expiresAt epoch milliseconds the permit expires, {@code 0} for no expiry
 * @param suspendedAt epoch milliseconds of the most recent suspension, {@code 0} when never
 * @param suspendedUntil epoch milliseconds the suspension ends, {@code 0} when never
 * @param suspendedBy the official who imposed the most recent suspension, or {@code null}
 * @param suspendReason the recorded suspension reason, or empty
 * @param revokedAt epoch milliseconds of the revocation, {@code 0} when never
 * @param revokedBy the official who revoked it, or {@code null}
 * @param revokeReason the recorded revocation reason, or empty
 * @param note the optional issue note or reason recorded by the registrar, or empty
 */
public record PermitRecord(
        String id,
        UUID holderUuid,
        String holderName,
        PermitClass permitClass,
        PermitStatus status,
        UUID issuedBy,
        long issuedAt,
        long expiresAt,
        long suspendedAt,
        long suspendedUntil,
        UUID suspendedBy,
        String suspendReason,
        long revokedAt,
        UUID revokedBy,
        String revokeReason,
        String note) {

    public PermitRecord {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("PermitRecord requires an id");
        }
        if (holderUuid == null) {
            throw new IllegalArgumentException("PermitRecord requires a holder uuid");
        }
        if (permitClass == null) {
            throw new IllegalArgumentException("PermitRecord requires a permit class");
        }
        holderName = holderName == null ? "" : holderName;
        status = status == null ? PermitStatus.REVOKED : status;
        suspendReason = suspendReason == null ? "" : suspendReason;
        revokeReason = revokeReason == null ? "" : revokeReason;
        note = note == null ? "" : note;
    }

    /** The key for one holder and class, the same form used as the data-file key. */
    public static String key(final UUID holderUuid, final PermitClass permitClass) {
        return (holderUuid == null ? "" : holderUuid.toString())
                + ":" + (permitClass == null ? "" : permitClass.id());
    }

    /** A fresh permit with no suspension and the given expiry ({@code 0} for none). */
    public static PermitRecord issued(
            final UUID holderUuid, final String holderName, final PermitClass permitClass,
            final UUID issuedBy, final long now, final long expiresAt, final String note) {
        return new PermitRecord(
                key(holderUuid, permitClass), holderUuid, holderName, permitClass,
                PermitStatus.VALID, issuedBy, now, Math.max(0L, expiresAt),
                0L, 0L, null, "", 0L, null, "", note);
    }

    /** The effective status at {@code now}. Revoked wins over everything; expiry beats suspension. */
    public PermitStatus stateAt(final long now) {
        if (status == PermitStatus.REVOKED) {
            return PermitStatus.REVOKED;
        }
        if (expiresAt > 0L && now >= expiresAt) {
            return PermitStatus.EXPIRED;
        }
        if (suspendedUntil > now) {
            return PermitStatus.SUSPENDED;
        }
        return PermitStatus.VALID;
    }

    /** True when the permit authorizes its class right now. */
    public boolean validAt(final long now) {
        return stateAt(now) == PermitStatus.VALID;
    }

    /** Returns a copy suspended until {@code until}, with the official and the reason recorded. */
    public PermitRecord suspended(
            final UUID by, final String reason, final long now, final long until) {
        return new PermitRecord(
                id, holderUuid, holderName, permitClass, PermitStatus.SUSPENDED,
                issuedBy, issuedAt, expiresAt,
                now, Math.max(now, until), by, reason == null ? "" : reason,
                revokedAt, revokedBy, revokeReason, note);
    }

    /** Returns a copy revoked now, with the official and the reason recorded. */
    public PermitRecord revoked(final UUID by, final String reason, final long now) {
        return new PermitRecord(
                id, holderUuid, holderName, permitClass, PermitStatus.REVOKED,
                issuedBy, issuedAt, expiresAt,
                suspendedAt, suspendedUntil, suspendedBy, suspendReason,
                now, by, reason == null ? "" : reason, note);
    }

    /** True when this permit is one of the carry classes. */
    public boolean weaponPermit() {
        return permitClass.isWeaponPermit();
    }

    /** Serializes this permit into a fresh tag. */
    public CompoundTag write() {
        final CompoundTag tag = new CompoundTag();
        tag.putString("id", id);
        NbtSupport.putUuid(tag, "holderUuid", holderUuid);
        tag.putString("holderName", holderName);
        tag.putString("permitClass", permitClass.id());
        tag.putString("status", status.name());
        NbtSupport.putUuid(tag, "issuedBy", issuedBy);
        tag.putLong("issuedAt", issuedAt);
        tag.putLong("expiresAt", expiresAt);
        tag.putLong("suspendedAt", suspendedAt);
        tag.putLong("suspendedUntil", suspendedUntil);
        NbtSupport.putUuid(tag, "suspendedBy", suspendedBy);
        tag.putString("suspendReason", suspendReason);
        tag.putLong("revokedAt", revokedAt);
        NbtSupport.putUuid(tag, "revokedBy", revokedBy);
        tag.putString("revokeReason", revokeReason);
        tag.putString("note", note);
        return tag;
    }

    /**
     * Reads a permit. A tag without a holder or a known class has no safe default and returns
     * {@code null}, so the loader skips one damaged record instead of inventing a permit.
     */
    public static PermitRecord read(final CompoundTag tag, final String fallbackId) {
        final UUID holderUuid = NbtSupport.getUuid(tag, "holderUuid");
        final PermitClass permitClass =
                PermitClass.parse(NbtSupport.getString(tag, "permitClass", ""));
        if (holderUuid == null || permitClass == null) {
            return null;
        }
        String id = NbtSupport.getString(tag, "id", "");
        if (id.isBlank()) {
            id = fallbackId == null || fallbackId.isBlank()
                    ? key(holderUuid, permitClass) : fallbackId;
        }
        return new PermitRecord(
                id,
                holderUuid,
                NbtSupport.getString(tag, "holderName", ""),
                permitClass,
                PermitStatus.parse(NbtSupport.getString(tag, "status", "")),
                NbtSupport.getUuid(tag, "issuedBy"),
                NbtSupport.getLong(tag, "issuedAt", 0L),
                NbtSupport.getLong(tag, "expiresAt", 0L),
                NbtSupport.getLong(tag, "suspendedAt", 0L),
                NbtSupport.getLong(tag, "suspendedUntil", 0L),
                NbtSupport.getUuid(tag, "suspendedBy"),
                NbtSupport.getString(tag, "suspendReason", ""),
                NbtSupport.getLong(tag, "revokedAt", 0L),
                NbtSupport.getUuid(tag, "revokedBy"),
                NbtSupport.getString(tag, "revokeReason", ""),
                NbtSupport.getString(tag, "note", ""));
    }
}
