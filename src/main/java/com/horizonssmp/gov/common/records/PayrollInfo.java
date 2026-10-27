package com.horizonssmp.gov.common.records;

import java.util.UUID;

import net.minecraft.nbt.CompoundTag;

/**
 * The payroll block of a business card: a display-only record of what the business reports about
 * wages. It is deliberately not a bank account.
 *
 * <p>There is no balance here and there never will be one. {@code ratePerDayGold} is a stated
 * intention, {@code scheduleNote} is the stated payment time Article 7.10 requires an employment
 * agreement to name, and {@code lastPaidAt}/{@code lastPaidBy}/{@code lastPaidAmountGold} record a
 * <b>physical</b> gold payment that a {@code business.manage} holder wrote down in roleplay. The
 * mod never holds, mints, transfers or deducts an item from any of these fields, and
 * {@code paymentsRecorded} is a count of written-down payments, never a sum owed.
 *
 * <p>Every field is tolerant on read: a missing key takes a safe default, so a hand-edited or
 * older tag loads instead of failing the whole world.
 *
 * @param ratePerDayGold the stated wage in gold ingots per in-game day, {@code 0} when unstated
 * @param scheduleNote the stated payment time, or empty
 * @param lastPaidAt epoch milliseconds of the last recorded physical payment, {@code 0} when none
 * @param lastPaidBy the official who recorded it, or {@code null} if unknown
 * @param lastPaidAmountGold the amount of that payment in gold ingots, {@code 0} when none
 * @param lastPaymentNote the note recorded with that payment, or empty
 * @param paymentsRecorded how many physical payments have been written down
 */
public record PayrollInfo(
        long ratePerDayGold,
        String scheduleNote,
        long lastPaidAt,
        UUID lastPaidBy,
        long lastPaidAmountGold,
        String lastPaymentNote,
        int paymentsRecorded) {

    /** Nothing stated and nothing recorded. */
    public static final PayrollInfo NONE = new PayrollInfo(0L, "", 0L, null, 0L, "", 0);

    public PayrollInfo {
        ratePerDayGold = Math.max(0L, ratePerDayGold);
        scheduleNote = scheduleNote == null ? "" : scheduleNote;
        lastPaidAmountGold = Math.max(0L, lastPaidAmountGold);
        lastPaymentNote = lastPaymentNote == null ? "" : lastPaymentNote;
        paymentsRecorded = Math.max(0, paymentsRecorded);
    }

    /** True when nothing at all has been recorded. */
    public boolean empty() {
        return ratePerDayGold <= 0L && scheduleNote.isEmpty() && paymentsRecorded <= 0;
    }

    /** Returns a copy with a new stated rate and schedule note, keeping the payment history. */
    public PayrollInfo withRate(final long rateGold, final String note) {
        return new PayrollInfo(
                rateGold, note, lastPaidAt, lastPaidBy, lastPaidAmountGold, lastPaymentNote,
                paymentsRecorded);
    }

    /**
     * Returns a copy with one more recorded physical payment.
     *
     * <p>The count moves by exactly one and the previous payment is replaced, not appended: the
     * block is a summary, and the audit log is where the history of every payment lives.
     */
    public PayrollInfo withPayment(
            final long amountGold, final UUID by, final long at, final String note) {
        return new PayrollInfo(
                ratePerDayGold,
                scheduleNote,
                at,
                by,
                amountGold,
                note,
                paymentsRecorded + 1);
    }

    /** Serializes this block into a fresh tag. */
    public CompoundTag write() {
        final CompoundTag tag = new CompoundTag();
        tag.putLong("ratePerDayGold", ratePerDayGold);
        tag.putString("scheduleNote", scheduleNote);
        tag.putLong("lastPaidAt", lastPaidAt);
        NbtSupport.putUuid(tag, "lastPaidBy", lastPaidBy);
        tag.putLong("lastPaidAmountGold", lastPaidAmountGold);
        tag.putString("lastPaymentNote", lastPaymentNote);
        tag.putInt("paymentsRecorded", paymentsRecorded);
        return tag;
    }

    /** Reads the block, returning {@link #NONE} when the tag is missing or empty. */
    public static PayrollInfo read(final CompoundTag tag) {
        if (tag == null || tag.isEmpty()) {
            return NONE;
        }
        return new PayrollInfo(
                NbtSupport.getLong(tag, "ratePerDayGold", 0L),
                NbtSupport.getString(tag, "scheduleNote", ""),
                NbtSupport.getLong(tag, "lastPaidAt", 0L),
                NbtSupport.getUuid(tag, "lastPaidBy"),
                NbtSupport.getLong(tag, "lastPaidAmountGold", 0L),
                NbtSupport.getString(tag, "lastPaymentNote", ""),
                NbtSupport.getInt(tag, "paymentsRecorded", 0));
    }
}
