package com.horizonssmp.gov.common.records;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.horizonssmp.gov.common.GovCapabilities;
import com.horizonssmp.gov.common.GovCapability;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;

/**
 * Shared, deliberately tolerant NBT helpers for the {@code gov.dat} records.
 *
 * <p>Every reader has a default and never throws: a missing key, a key of the wrong tag type, or a
 * truncated list must not fail a world load. That is the whole point of this class. The government
 * data file is the only copy of issued fines, warrants and registrations, so a single malformed
 * field must degrade to a default value instead of aborting {@code SavedData} deserialization.
 *
 * <p>Writers always write the key, even for empty values, so the file stays self-describing: a
 * human reading {@code gov.dat} sees the shape of the schema. UUIDs are the one exception, because
 * NBT has no null UUID; a null UUID is written as an absent key and read back as {@code null}.
 */
public final class NbtSupport {

    private NbtSupport() {
    }

    /** Reads a string, returning {@code fallback} for a missing or non-string key. */
    public static String getString(final CompoundTag tag, final String key, final String fallback) {
        if (tag.contains(key, Tag.TAG_STRING)) {
            return tag.getString(key);
        }
        return fallback;
    }

    /** Reads a long, returning {@code fallback} for a missing or non-numeric key. */
    public static long getLong(final CompoundTag tag, final String key, final long fallback) {
        if (tag.contains(key, Tag.TAG_LONG)) {
            return tag.getLong(key);
        }
        if (tag.contains(key, Tag.TAG_INT)) {
            return tag.getInt(key);
        }
        return fallback;
    }

    /** Reads an int, returning {@code fallback} for a missing or non-numeric key. */
    public static int getInt(final CompoundTag tag, final String key, final int fallback) {
        if (tag.contains(key, Tag.TAG_INT)) {
            return tag.getInt(key);
        }
        if (tag.contains(key, Tag.TAG_LONG)) {
            // A long written by a newer build, or by a hand edit, must saturate rather than wrap:
            // (int) 4_294_967_296L is 0, which would silently turn a huge schema or count into a
            // valid-looking tiny value. Saturation is visible and callers already validate ranges.
            final long value = tag.getLong(key);
            if (value > Integer.MAX_VALUE) {
                return Integer.MAX_VALUE;
            }
            if (value < Integer.MIN_VALUE) {
                return Integer.MIN_VALUE;
            }
            return (int) value;
        }
        return fallback;
    }

    /** Reads a boolean, returning {@code fallback} for a missing or non-boolean key. */
    public static boolean getBoolean(final CompoundTag tag, final String key, final boolean fallback) {
        if (tag.contains(key, Tag.TAG_BYTE)) {
            return tag.getBoolean(key);
        }
        return fallback;
    }

    /** Reads a UUID, returning {@code null} when the key is absent or malformed. */
    public static UUID getUuid(final CompoundTag tag, final String key) {
        if (!tag.contains(key, Tag.TAG_INT_ARRAY)) {
            return null;
        }
        try {
            return NbtUtils.loadUUID(tag.get(key));
        } catch (final RuntimeException e) {
            // A hand-edited or truncated UUID array must not fail the whole load.
            return null;
        }
    }

    /** Writes a UUID, skipping a null value so the key is simply absent. */
    public static void putUuid(final CompoundTag tag, final String key, final UUID value) {
        if (value != null) {
            tag.putUUID(key, value);
        }
    }

    /** Reads a list of strings, skipping entries of any other type. */
    public static List<String> getStringList(final CompoundTag tag, final String key) {
        if (!tag.contains(key, Tag.TAG_LIST)) {
            return List.of();
        }
        final ListTag list = tag.getList(key, Tag.TAG_STRING);
        final List<String> result = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            result.add(list.getString(i));
        }
        return List.copyOf(result);
    }

    /** Writes a list of strings; a null or empty collection writes an empty list. */
    public static void putStringList(
            final CompoundTag tag, final String key, final Collection<String> values) {
        final ListTag list = new ListTag();
        if (values != null) {
            for (final String value : values) {
                if (value != null) {
                    list.add(net.minecraft.nbt.StringTag.valueOf(value));
                }
            }
        }
        tag.put(key, list);
    }

    /**
     * Reads a set of strings. Insertion order follows the file, which keeps a rewrite stable. An
     * empty set is returned for a missing key, never {@code null}.
     */
    public static Set<String> getStringSet(final CompoundTag tag, final String key) {
        return new LinkedHashSet<>(getStringList(tag, key));
    }

    /** Writes a set of strings in iteration order. Callers pass a sorted set for stable output. */
    public static void putStringSet(
            final CompoundTag tag, final String key, final Collection<String> values) {
        putStringList(tag, key, values);
    }

    /** Reads a list of sub-compounds, skipping entries that are not compounds. */
    public static List<CompoundTag> getCompoundList(final CompoundTag tag, final String key) {
        if (!tag.contains(key, Tag.TAG_LIST)) {
            return List.of();
        }
        final ListTag list = tag.getList(key, Tag.TAG_COMPOUND);
        final List<CompoundTag> result = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            result.add(list.getCompound(i));
        }
        return List.copyOf(result);
    }

    /** Writes a list of sub-compounds; a null or empty collection writes an empty list. */
    public static void putCompoundList(
            final CompoundTag tag, final String key, final Collection<CompoundTag> values) {
        final ListTag list = new ListTag();
        if (values != null) {
            for (final CompoundTag value : values) {
                if (value != null) {
                    list.add(value);
                }
            }
        }
        tag.put(key, list);
    }

    /**
     * Reads a capability set from its stored string ids. Ids that this build does not know are
     * dropped rather than fatal, so a data file written by a newer build still loads; the unknown
     * capability simply has no effect until the build that knows it runs.
     *
     * <p>The mapping between a capability and its id lives in {@link GovCapabilities#fromIds} and
     * {@link GovCapabilities#ids} and nowhere else, so the id a job is stored with is by
     * construction the id the parser looks for.
     *
     * <p>The returned set is an {@link EnumSet}, ordered by declaration. Callers that display or
     * diff a capability set should sort by {@link GovCapability#id()}, which is what the writer
     * does, so a read-write round trip of a file produces identical text.
     */
    public static Set<GovCapability> getCapabilities(final CompoundTag tag, final String key) {
        return GovCapabilities.fromIds(getStringList(tag, key));
    }

    /**
     * Writes a capability set as its string ids, sorted by id so the on-disk text is stable across
     * saves. A null or empty collection writes an empty list.
     */
    public static void putCapabilities(
            final CompoundTag tag, final String key, final Collection<GovCapability> values) {
        putStringList(tag, key, GovCapabilities.ids(values));
    }

    /**
     * Reads a nested record, returning {@code fallback} when the key is absent. Used for the
     * {@code suspect}, {@code employment}, {@code jail}, {@code impound} and {@code location}
     * blocks, which are always present in memory so callers never null-check.
     */
    public static CompoundTag getCompoundOr(final CompoundTag tag, final String key) {
        if (tag.contains(key, Tag.TAG_COMPOUND)) {
            return tag.getCompound(key);
        }
        return new CompoundTag();
    }
}
