package com.horizonssmp.gov.common.records;

import java.util.UUID;

import net.minecraft.nbt.CompoundTag;

/**
 * One staff entry on a business card: who, in what role, since when.
 *
 * <p>The role is a label on the business's own list. It grants no capability and no authority:
 * a business card never changes what a player may do. The {@code uuid} is the identity; the
 * display name is resolved at read time from the stored player file, so a rename never leaves a
 * stale entry.
 *
 * @param uuid the employee's UUID, never {@code null}
 * @param role the role id from the role vocabulary
 * @param since epoch milliseconds the employee joined
 */
public record BusinessEmployee(UUID uuid, String role, long since) {

    public BusinessEmployee {
        if (uuid == null) {
            throw new IllegalArgumentException("BusinessEmployee requires a uuid");
        }
        role = role == null ? "" : role;
    }

    /** Serializes this entry into a fresh tag. */
    public CompoundTag write() {
        final CompoundTag tag = new CompoundTag();
        NbtSupport.putUuid(tag, "uuid", uuid);
        tag.putString("role", role);
        tag.putLong("since", since);
        return tag;
    }

    /** Reads one entry, returning {@code null} when it has no usable identity. */
    public static BusinessEmployee read(final CompoundTag tag) {
        final UUID uuid = NbtSupport.getUuid(tag, "uuid");
        if (uuid == null) {
            return null;
        }
        return new BusinessEmployee(
                uuid,
                NbtSupport.getString(tag, "role", ""),
                NbtSupport.getLong(tag, "since", 0L));
    }
}
