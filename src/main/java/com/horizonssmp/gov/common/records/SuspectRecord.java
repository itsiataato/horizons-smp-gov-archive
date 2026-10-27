package com.horizonssmp.gov.common.records;

import java.util.UUID;

import net.minecraft.nbt.CompoundTag;

/**
 * The suspect flag on a player's card.
 *
 * <p>A cleared flag is not erased: {@code active} goes false while {@code reason}, {@code setBy}
 * and {@code setAt} stay, so the dashboard can show "flagged by X on Y, since cleared" and the
 * audit log has something to point at.
 *
 * @param active whether the flag currently applies
 * @param reason the officer's stated reason, already sanitized by the service that set it
 * @param setBy the officer who set the flag, or {@code null} if unknown
 * @param setAt epoch milliseconds the flag was set, {@code 0} when never set
 */
public record SuspectRecord(boolean active, String reason, UUID setBy, long setAt) {

    /** The empty flag: not a suspect, nothing recorded. */
    public static final SuspectRecord NONE = new SuspectRecord(false, "", null, 0L);

    public SuspectRecord {
        reason = reason == null ? "" : reason;
    }

    /** Serializes this flag into a fresh tag. */
    public CompoundTag write() {
        final CompoundTag tag = new CompoundTag();
        tag.putBoolean("active", active);
        tag.putString("reason", reason);
        NbtSupport.putUuid(tag, "setBy", setBy);
        tag.putLong("setAt", setAt);
        return tag;
    }

    /** Reads a flag, returning {@link #NONE} when the tag is missing or empty. */
    public static SuspectRecord read(final CompoundTag tag) {
        if (tag == null || tag.isEmpty()) {
            return NONE;
        }
        return new SuspectRecord(
                NbtSupport.getBoolean(tag, "active", false),
                NbtSupport.getString(tag, "reason", ""),
                NbtSupport.getUuid(tag, "setBy"),
                NbtSupport.getLong(tag, "setAt", 0L));
    }
}
