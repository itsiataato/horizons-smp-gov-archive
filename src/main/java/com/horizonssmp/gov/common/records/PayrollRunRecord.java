package com.horizonssmp.gov.common.records;

import java.util.UUID;

import net.minecraft.nbt.CompoundTag;

/**
 * One completed payroll run, used to enforce "one run per scope per 24 hours".
 *
 * <p>{@code scope} is {@code gov}, {@code police} or {@code business:<businessId>}.
 * {@code dayBucket} is {@code at / 86_400_000}; the record is keyed by the scope and the bucket,
 * so a second run in the same bucket can be refused without scanning the ledger.
 * {@code count} is the number of employees paid, {@code gross} the total debited from the source
 * and {@code tax} the total credited to Government. The derived net is {@code gross - tax}.
 *
 * <p>The record holds no display names; {@code actor} is the player who started the run. It is
 * immutable and is never deleted.
 */
public record PayrollRunRecord(
        String scope,
        long dayBucket,
        long at,
        UUID actor,
        int count,
        long gross,
        long tax) {

    public PayrollRunRecord {
        if (scope == null || scope.isBlank()) {
            throw new IllegalArgumentException("PayrollRunRecord requires a non-blank scope");
        }
        if (actor == null) {
            throw new IllegalArgumentException("PayrollRunRecord requires an actor");
        }
        if (dayBucket < 0L) {
            throw new IllegalArgumentException(
                    "PayrollRunRecord dayBucket must not be negative, got " + dayBucket);
        }
        if (at < 0L) {
            throw new IllegalArgumentException(
                    "PayrollRunRecord at must not be negative, got " + at);
        }
        if (count < 0) {
            throw new IllegalArgumentException(
                    "PayrollRunRecord count must not be negative, got " + count);
        }
        if (gross < 0L) {
            throw new IllegalArgumentException(
                    "PayrollRunRecord gross must not be negative, got " + gross);
        }
        if (tax < 0L) {
            throw new IllegalArgumentException(
                    "PayrollRunRecord tax must not be negative, got " + tax);
        }
        if (tax > gross) {
            throw new IllegalArgumentException(
                    "PayrollRunRecord tax must not exceed gross, got tax " + tax
                            + " and gross " + gross);
        }
    }

    /** The total paid to employees: {@code gross - tax}. */
    public long net() {
        return gross - tax;
    }

    /** Serializes this run into a fresh tag. */
    public CompoundTag toTag() {
        final CompoundTag tag = new CompoundTag();
        tag.putString("scope", scope);
        tag.putLong("dayBucket", dayBucket);
        tag.putLong("at", at);
        NbtSupport.putUuid(tag, "actor", actor);
        tag.putInt("count", count);
        tag.putLong("gross", gross);
        tag.putLong("tax", tax);
        return tag;
    }

    /**
     * Reads a run from a tag. A run without a scope or an actor cannot be keyed or attributed and
     * is refused with a clear message; the remaining fields default to zero.
     */
    public static PayrollRunRecord fromTag(final CompoundTag tag) {
        return new PayrollRunRecord(
                NbtSupport.getString(tag, "scope", ""),
                Math.max(0L, NbtSupport.getLong(tag, "dayBucket", 0L)),
                Math.max(0L, NbtSupport.getLong(tag, "at", 0L)),
                NbtSupport.getUuid(tag, "actor"),
                Math.max(0, NbtSupport.getInt(tag, "count", 0)),
                Math.max(0L, NbtSupport.getLong(tag, "gross", 0L)),
                Math.max(0L, NbtSupport.getLong(tag, "tax", 0L)));
    }
}
