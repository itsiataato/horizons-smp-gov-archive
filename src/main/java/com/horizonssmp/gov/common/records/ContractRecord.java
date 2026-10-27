package com.horizonssmp.gov.common.records;

import java.util.UUID;

import com.horizonssmp.gov.common.enums.ContractState;

import net.minecraft.nbt.CompoundTag;

/**
 * One Government building contract.
 *
 * <p>A contract is a materials budget, a labour fee, a written order and a named builder. The
 * record is the authority; the written book handed to the builder is informational and is never
 * parsed as input. {@code total = materials + labor}, and the total is the amount held in
 * {@code economyMeta.contractEscrow} from posting until approval, void or expiry.
 *
 * <p>{@code materials} and {@code labor} are each {@code 1..99} ingots, so the total is at most
 * {@code 198}. All five timestamps are epoch milliseconds and {@code 0} means "not yet". The state
 * pins which of them may be non-zero, but that cross-field rule belongs to the service that owns
 * the transition table; this record only refuses impossible values.
 *
 * <p>{@code poster} and {@code builder} are UUIDs, never display names. {@code bookTitle} is the
 * title written into the builder's book, or empty when the book could not be issued.
 */
public record ContractRecord(
        String id,
        UUID poster,
        UUID builder,
        long materials,
        long labor,
        long total,
        String description,
        ContractState state,
        long createdAt,
        long acceptedAt,
        long submittedAt,
        long approvedAt,
        long expiresAt,
        String bookTitle) {

    /** The inclusive lower bound for the materials budget and the labour fee. */
    public static final long MIN_COMPONENT = 1L;
    /** The inclusive upper bound for the materials budget and the labour fee. */
    public static final long MAX_COMPONENT = 99L;
    /** The largest possible contract total. */
    public static final long MAX_TOTAL = 198L;

    public ContractRecord {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("ContractRecord requires a non-blank id");
        }
        if (poster == null) {
            throw new IllegalArgumentException("ContractRecord requires a poster");
        }
        if (builder == null) {
            throw new IllegalArgumentException("ContractRecord requires a builder");
        }
        if (materials < MIN_COMPONENT || materials > MAX_COMPONENT) {
            throw new IllegalArgumentException(
                    "ContractRecord materials must be between " + MIN_COMPONENT + " and "
                            + MAX_COMPONENT + " inclusive, got " + materials);
        }
        if (labor < MIN_COMPONENT || labor > MAX_COMPONENT) {
            throw new IllegalArgumentException(
                    "ContractRecord labor must be between " + MIN_COMPONENT + " and "
                            + MAX_COMPONENT + " inclusive, got " + labor);
        }
        final long computed = materials + labor;
        if (computed > MAX_TOTAL) {
            throw new IllegalArgumentException(
                    "ContractRecord total must not exceed " + MAX_TOTAL + ", got " + computed);
        }
        if (total != computed) {
            throw new IllegalArgumentException(
                    "ContractRecord total must equal materials + labor, got total " + total
                            + " but materials + labor is " + computed);
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("ContractRecord requires a non-blank description");
        }
        state = state == null ? ContractState.VOIDED : state;
        createdAt = nonNegative(createdAt, "createdAt");
        acceptedAt = nonNegative(acceptedAt, "acceptedAt");
        submittedAt = nonNegative(submittedAt, "submittedAt");
        approvedAt = nonNegative(approvedAt, "approvedAt");
        expiresAt = nonNegative(expiresAt, "expiresAt");
        bookTitle = bookTitle == null ? "" : bookTitle;
    }

    private static long nonNegative(final long value, final String field) {
        if (value < 0L) {
            throw new IllegalArgumentException(
                    "ContractRecord " + field + " must not be negative, got " + value);
        }
        return value;
    }

    /** True when the contract can no longer change state. */
    public boolean terminal() {
        return state.terminal();
    }

    /** Serializes this contract into a fresh tag. */
    public CompoundTag toTag() {
        final CompoundTag tag = new CompoundTag();
        tag.putString("id", id);
        NbtSupport.putUuid(tag, "poster", poster);
        NbtSupport.putUuid(tag, "builder", builder);
        tag.putLong("materials", materials);
        tag.putLong("labor", labor);
        tag.putLong("total", total);
        tag.putString("description", description);
        tag.putString("state", state.name());
        tag.putLong("createdAt", createdAt);
        tag.putLong("acceptedAt", acceptedAt);
        tag.putLong("submittedAt", submittedAt);
        tag.putLong("approvedAt", approvedAt);
        tag.putLong("expiresAt", expiresAt);
        tag.putString("bookTitle", bookTitle);
        return tag;
    }

    /**
     * Reads a contract from a tag. The identity, parties and description are required and a
     * missing one is refused with a clear message; timestamps default to zero and the state
     * defaults to {@link ContractState#VOIDED}.
     */
    public static ContractRecord fromTag(final CompoundTag tag) {
        return new ContractRecord(
                NbtSupport.getString(tag, "id", ""),
                NbtSupport.getUuid(tag, "poster"),
                NbtSupport.getUuid(tag, "builder"),
                NbtSupport.getLong(tag, "materials", 0L),
                NbtSupport.getLong(tag, "labor", 0L),
                NbtSupport.getLong(tag, "total", 0L),
                NbtSupport.getString(tag, "description", ""),
                ContractState.fromName(NbtSupport.getString(tag, "state", "")),
                Math.max(0L, NbtSupport.getLong(tag, "createdAt", 0L)),
                Math.max(0L, NbtSupport.getLong(tag, "acceptedAt", 0L)),
                Math.max(0L, NbtSupport.getLong(tag, "submittedAt", 0L)),
                Math.max(0L, NbtSupport.getLong(tag, "approvedAt", 0L)),
                Math.max(0L, NbtSupport.getLong(tag, "expiresAt", 0L)),
                NbtSupport.getString(tag, "bookTitle", ""));
    }
}
