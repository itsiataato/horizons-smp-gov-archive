package com.horizonssmp.gov.common.records;

import java.util.UUID;

import com.horizonssmp.gov.common.enums.LedgerKind;

import net.minecraft.nbt.CompoundTag;

/**
 * One immutable line of the economy ledger.
 *
 * <p>Every balance mutation produces exactly one entry. {@code seq} is allocated from
 * {@code economyMeta.nextLedgerSeq} with no gaps and no reuse; {@code txn} groups the entries of one
 * committed transaction. {@code from} is debited by {@code gross}, {@code to} is credited by the
 * derived net ({@code gross - tax}) and {@code government} is credited by {@code tax}. Either side
 * may be the marker {@code physical}, which represents gold entering or leaving the world and is
 * excluded from the balance sum.
 *
 * <p>{@code gross == net + tax} is a caller invariant. This record proves the part it can: both
 * amounts are non-negative and {@code tax <= gross}, so the derived net can never be negative.
 *
 * <p>No display name is stored. {@code actor} is the UUID of the official or player who caused the
 * entry, or {@code null} when the entry is system-generated.
 */
public record LedgerEntry(
        long seq,
        long at,
        String txn,
        LedgerKind kind,
        String from,
        String to,
        long gross,
        long tax,
        UUID actor,
        String reason) {

    public LedgerEntry {
        if (gross < 0L) {
            throw new IllegalArgumentException(
                    "LedgerEntry gross must not be negative, got " + gross);
        }
        if (tax < 0L) {
            throw new IllegalArgumentException(
                    "LedgerEntry tax must not be negative, got " + tax);
        }
        if (tax > gross) {
            throw new IllegalArgumentException(
                    "LedgerEntry tax must not exceed gross, got tax " + tax
                            + " and gross " + gross);
        }
        txn = txn == null ? "" : txn;
        kind = kind == null ? LedgerKind.TRANSFER : kind;
        from = from == null ? "" : from;
        to = to == null ? "" : to;
        reason = reason == null ? "" : reason;
    }

    /** The amount credited to {@code to}: {@code gross - tax}. */
    public long net() {
        return gross - tax;
    }

    /** Serializes this entry into a fresh tag. */
    public CompoundTag toTag() {
        final CompoundTag tag = new CompoundTag();
        tag.putLong("seq", seq);
        tag.putLong("at", at);
        tag.putString("txn", txn);
        tag.putString("kind", kind.name());
        tag.putString("from", from);
        tag.putString("to", to);
        tag.putLong("gross", gross);
        tag.putLong("tax", tax);
        NbtSupport.putUuid(tag, "actor", actor);
        tag.putString("reason", reason);
        return tag;
    }

    /** Reads one entry. Every field has a documented default, so a short tag still loads. */
    public static LedgerEntry fromTag(final CompoundTag tag) {
        return new LedgerEntry(
                NbtSupport.getLong(tag, "seq", 0L),
                NbtSupport.getLong(tag, "at", 0L),
                NbtSupport.getString(tag, "txn", ""),
                LedgerKind.fromName(NbtSupport.getString(tag, "kind", "")),
                NbtSupport.getString(tag, "from", ""),
                NbtSupport.getString(tag, "to", ""),
                NbtSupport.getLong(tag, "gross", 0L),
                NbtSupport.getLong(tag, "tax", 0L),
                NbtSupport.getUuid(tag, "actor"),
                NbtSupport.getString(tag, "reason", ""));
    }
}
