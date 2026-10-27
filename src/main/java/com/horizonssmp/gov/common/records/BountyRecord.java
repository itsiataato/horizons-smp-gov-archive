package com.horizonssmp.gov.common.records;

import java.util.UUID;

import net.minecraft.nbt.CompoundTag;


public record BountyRecord(
        String id,
        UUID targetUuid,
        String targetName,
        UUID posterUuid,
        String posterName,
        long amount,
        long createdAt,
        long expiresAt,
        String status,
        UUID claimedBy,
        String claimedByName,
        long claimedAt) {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_CLAIMED = "CLAIMED";
    public static final String STATUS_CANCELLED = "CANCELLED";
    public static final String STATUS_EXPIRED = "EXPIRED";

    public BountyRecord {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("BountyRecord requires an id");
        }
        targetName = targetName == null ? "" : targetName;
        posterName = posterName == null ? "" : posterName;
        claimedByName = claimedByName == null ? "" : claimedByName;
        status = status == null || status.isBlank() ? STATUS_ACTIVE : status;
    }

    public boolean activeAt(final long now) {
        return STATUS_ACTIVE.equals(status) && (expiresAt <= 0L || now < expiresAt);
    }

    public CompoundTag write() {
        final CompoundTag tag = new CompoundTag();
        tag.putString("id", id);
        NbtSupport.putUuid(tag, "targetUuid", targetUuid);
        tag.putString("targetName", targetName);
        NbtSupport.putUuid(tag, "posterUuid", posterUuid);
        tag.putString("posterName", posterName);
        tag.putLong("amount", amount);
        tag.putLong("createdAt", createdAt);
        tag.putLong("expiresAt", expiresAt);
        tag.putString("status", status);
        NbtSupport.putUuid(tag, "claimedBy", claimedBy);
        tag.putString("claimedByName", claimedByName);
        tag.putLong("claimedAt", claimedAt);
        return tag;
    }

    public static BountyRecord read(final CompoundTag tag, final String fallbackId) {
        final String stored = NbtSupport.getString(tag, "id", "");
        final String id = stored.isBlank() ? fallbackId : stored;
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("BountyRecord tag has no id and no fallback");
        }
        return new BountyRecord(
                id,
                NbtSupport.getUuid(tag, "targetUuid"),
                NbtSupport.getString(tag, "targetName", ""),
                NbtSupport.getUuid(tag, "posterUuid"),
                NbtSupport.getString(tag, "posterName", ""),
                NbtSupport.getLong(tag, "amount", 0L),
                NbtSupport.getLong(tag, "createdAt", 0L),
                NbtSupport.getLong(tag, "expiresAt", 0L),
                NbtSupport.getString(tag, "status", STATUS_ACTIVE),
                NbtSupport.getUuid(tag, "claimedBy"),
                NbtSupport.getString(tag, "claimedByName", ""),
                NbtSupport.getLong(tag, "claimedAt", 0L));
    }
}
