package com.horizonssmp.gov.common.records;

import java.util.UUID;

import com.horizonssmp.gov.common.enums.ImpoundReason;

import net.minecraft.nbt.CompoundTag;

/**
 * The impound block of a vehicle record.
 *
 * <p>A release is recorded rather than erased: {@code active} goes false and the release fields are
 * filled in, so the card can answer "who towed it, why, and who paid to get it back". The fee is
 * copied from config when the impound is created, so changing {@code
 * gov.vehicle.impoundReleaseFeeGold} never rewrites the price someone already paid.
 *
 * <p>{@code reasonText} is the official's free-text reason and is authoritative. {@code reason} is a
 * typed index derived from it for grouping and filtering; it is never the only record of why the
 * tow happened. The law's grounds (no licence, no plate, impaired driving, blocking, unlicensed
 * operation) are expressed in the text the officer writes.
 *
 * <p>{@code disposalFlaggedAt} is the Article 6.15 30-in-game-day mark. It is set by the service on
 * a read or a release attempt once the deadline has passed, and it is a flag only: this mod never
 * deletes the vehicle, its contents or the record.
 *
 * @param active whether the vehicle is currently held
 * @param reason the typed impound ground for grouping
 * @param reasonText the free-text reason the official recorded, or empty
 * @param impoundedAt epoch milliseconds the impound happened, {@code 0} when never
 * @param impoundedBy the official who impounded it, or {@code null} if unknown
 * @param location where the vehicle was taken from, never {@code null}
 * @param releaseFeeGold the fee in ingots that applied to this impound
 * @param releasedAt epoch milliseconds of the release, {@code 0} while still held
 * @param releasedBy the official who released it, or {@code null} if unknown
 * @param disposalFlaggedAt epoch milliseconds the 30-day disposal mark was set, {@code 0} when not
 */
public record ImpoundRecord(
        boolean active,
        ImpoundReason reason,
        String reasonText,
        long impoundedAt,
        UUID impoundedBy,
        LocationRecord location,
        long releaseFeeGold,
        long releasedAt,
        UUID releasedBy,
        long disposalFlaggedAt) {

    /** Never impounded. */
    public static final ImpoundRecord NONE = new ImpoundRecord(
            false, ImpoundReason.BLOCKING, "", 0L, null, LocationRecord.NONE, 0L, 0L, null, 0L);

    public ImpoundRecord {
        reason = reason == null ? ImpoundReason.BLOCKING : reason;
        reasonText = reasonText == null ? "" : reasonText;
        location = location == null ? LocationRecord.NONE : location;
        releaseFeeGold = Math.max(0L, releaseFeeGold);
    }

    /** True when this block describes an impound that happened, active or released. */
    public boolean everImpounded() {
        return impoundedAt > 0L;
    }

    /** True when the vehicle is held and the 30-day disposal mark has been set. */
    public boolean flaggedForDisposal() {
        return active && disposalFlaggedAt > 0L;
    }

    /** Returns a copy marked for disposal at {@code now}. */
    public ImpoundRecord flaggedForDisposal(final long now) {
        return new ImpoundRecord(
                active, reason, reasonText, impoundedAt, impoundedBy, location,
                releaseFeeGold, releasedAt, releasedBy,
                disposalFlaggedAt > 0L ? disposalFlaggedAt : now);
    }

    /** Returns a copy released by {@code by} at {@code now}. */
    public ImpoundRecord released(final UUID by, final long now) {
        return new ImpoundRecord(
                false, reason, reasonText, impoundedAt, impoundedBy, location,
                releaseFeeGold, now, by, disposalFlaggedAt);
    }

    /** Serializes this block into a fresh tag. */
    public CompoundTag write() {
        final CompoundTag tag = new CompoundTag();
        tag.putBoolean("active", active);
        tag.putString("reason", reason.name());
        tag.putString("reasonText", reasonText);
        tag.putLong("impoundedAt", impoundedAt);
        NbtSupport.putUuid(tag, "impoundedBy", impoundedBy);
        tag.put("location", location.write());
        tag.putLong("releaseFeeGold", releaseFeeGold);
        tag.putLong("releasedAt", releasedAt);
        NbtSupport.putUuid(tag, "releasedBy", releasedBy);
        tag.putLong("disposalFlaggedAt", disposalFlaggedAt);
        return tag;
    }

    /** Reads the block, returning {@link #NONE} when the tag is missing or empty. */
    public static ImpoundRecord read(final CompoundTag tag) {
        if (tag == null || tag.isEmpty()) {
            return NONE;
        }
        return new ImpoundRecord(
                NbtSupport.getBoolean(tag, "active", false),
                ImpoundReason.parse(NbtSupport.getString(tag, "reason", "")),
                NbtSupport.getString(tag, "reasonText", ""),
                NbtSupport.getLong(tag, "impoundedAt", 0L),
                NbtSupport.getUuid(tag, "impoundedBy"),
                LocationRecord.read(NbtSupport.getCompoundOr(tag, "location")),
                NbtSupport.getLong(tag, "releaseFeeGold", 0L),
                NbtSupport.getLong(tag, "releasedAt", 0L),
                NbtSupport.getUuid(tag, "releasedBy"),
                NbtSupport.getLong(tag, "disposalFlaggedAt", 0L));
    }
}
