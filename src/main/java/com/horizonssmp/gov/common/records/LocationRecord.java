package com.horizonssmp.gov.common.records;

import net.minecraft.nbt.CompoundTag;

/**
 * A block position with its dimension id.
 *
 * <p>The dimension is stored as a string id rather than a level reference so a record survives a
 * restart, a dimension removal and a mod update. Coordinates are block coordinates.
 *
 * @param dimension the dimension id, for example {@code minecraft:overworld}
 * @param x block x
 * @param y block y
 * @param z block z
 */
public record LocationRecord(String dimension, int x, int y, int z) {

    /** An unset location: empty dimension, all coordinates zero. */
    public static final LocationRecord NONE = new LocationRecord("", 0, 0, 0);

    public LocationRecord {
        dimension = dimension == null ? "" : dimension;
    }

    /** True when no dimension has been recorded. */
    public boolean isSet() {
        return !dimension.isEmpty();
    }

    /** Serializes this location into a fresh tag. */
    public CompoundTag write() {
        final CompoundTag tag = new CompoundTag();
        tag.putString("dim", dimension);
        tag.putInt("x", x);
        tag.putInt("y", y);
        tag.putInt("z", z);
        return tag;
    }

    /** Reads a location, returning {@link #NONE} when the tag is missing or empty. */
    public static LocationRecord read(final CompoundTag tag) {
        if (tag == null || tag.isEmpty()) {
            return NONE;
        }
        return new LocationRecord(
                NbtSupport.getString(tag, "dim", ""),
                NbtSupport.getInt(tag, "x", 0),
                NbtSupport.getInt(tag, "y", 0),
                NbtSupport.getInt(tag, "z", 0));
    }
}
