package com.horizonssmp.gov.common.records;

import java.util.UUID;

import com.horizonssmp.gov.common.enums.ScanResult;

import net.minecraft.nbt.CompoundTag;

/**
 * One plate scan.
 *
 * <p>Scans are recorded even when nothing was wrong. The point of the log is that a plate which was
 * checked at a roadblock and reported clean can be shown to have been checked, and that the same
 * plate being scanned twenty times in a minute is visible as such.
 *
 * @param id the stable scan id
 * @param plate the scanned plate, normalized the same way as a registered plate
 * @param scannedBy the official who scanned it, or {@code null} if unknown
 * @param at epoch milliseconds of the scan
 * @param result what the register said
 * @param actionTaken a short note of what the officer did, or empty
 */
public record PlateScanRecord(
        String id,
        String plate,
        UUID scannedBy,
        long at,
        ScanResult result,
        String actionTaken) {

    public PlateScanRecord {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("PlateScanRecord requires an id");
        }
        plate = plate == null ? "" : plate;
        result = result == null ? ScanResult.UNREGISTERED : result;
        actionTaken = actionTaken == null ? "" : actionTaken;
    }

    /** Serializes this scan into a fresh tag. */
    public CompoundTag write() {
        final CompoundTag tag = new CompoundTag();
        tag.putString("id", id);
        tag.putString("plate", plate);
        NbtSupport.putUuid(tag, "scannedBy", scannedBy);
        tag.putLong("at", at);
        tag.putString("result", result.name());
        tag.putString("actionTaken", actionTaken);
        return tag;
    }

    /** Reads a scan. A tag without an id cannot be keyed, so the caller supplies the key. */
    public static PlateScanRecord read(final CompoundTag tag, final String fallbackId) {
        final String stored = NbtSupport.getString(tag, "id", "");
        final String id = stored.isBlank() ? fallbackId : stored;
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("PlateScanRecord tag has no id and no fallback");
        }
        return new PlateScanRecord(
                id,
                NbtSupport.getString(tag, "plate", ""),
                NbtSupport.getUuid(tag, "scannedBy"),
                NbtSupport.getLong(tag, "at", 0L),
                ScanResult.parse(NbtSupport.getString(tag, "result", "")),
                NbtSupport.getString(tag, "actionTaken", ""));
    }
}
