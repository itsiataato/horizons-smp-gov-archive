package com.horizonssmp.gov.common.records;

import java.util.UUID;

import com.horizonssmp.gov.common.enums.AccountStatus;
import com.horizonssmp.gov.common.enums.AccountType;

import net.minecraft.nbt.CompoundTag;

/**
 * One custodial economy account.
 *
 * <p>An account is identified by its stable {@code id}, never by a display name: display names are
 * resolved live and are never persisted. A player account names its {@code owner} UUID, a business
 * account names its {@code business} id, and the Government and Police singletons name neither.
 * The record is immutable; every change produces a copy from the service that owns the rule.
 *
 * <p>{@code balance} is a whole number of gold ingots in {@code 0..10,000,000}. That cap is the
 * per-account cap from the specification, so a record holding a value outside it is a programming
 * error and is refused at construction rather than clamped. {@code lastSeq} is the newest ledger
 * sequence that touched this account, {@code 0} before the first entry.
 *
 * <p>Reads are tolerant of shape, not of value: a missing type reads as {@link AccountType#PLAYER}
 * and a missing status as {@link AccountStatus#CLOSED}, while a balance outside the cap is still
 * refused because it cannot mean anything valid.
 */
public record AccountRecord(
        String id,
        AccountType type,
        UUID owner,
        String business,
        long balance,
        AccountStatus status,
        long createdAt,
        long lastSeq) {

    /** The largest balance one account may hold, in gold ingots. */
    public static final long MAX_BALANCE = 10_000_000L;

    public AccountRecord {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("AccountRecord requires a non-blank id");
        }
        if (balance < 0L || balance > MAX_BALANCE) {
            throw new IllegalArgumentException(
                    "AccountRecord balance must be between 0 and " + MAX_BALANCE
                            + " inclusive, got " + balance);
        }
        type = type == null ? AccountType.PLAYER : type;
        status = status == null ? AccountStatus.CLOSED : status;
        business = business == null || business.isBlank() ? null : business;
    }

    /** True when this account belongs to the Government or Police singleton. */
    public boolean singleton() {
        return type == AccountType.GOVERNMENT || type == AccountType.POLICE;
    }

    /** True when this account's balance may be increased. */
    public boolean acceptsCredit() {
        return status != AccountStatus.CLOSED;
    }

    /** True when this account's balance may be decreased. */
    public boolean acceptsDebit() {
        return status == AccountStatus.OPEN;
    }

    /** Serializes this account into a fresh tag. */
    public CompoundTag toTag() {
        final CompoundTag tag = new CompoundTag();
        tag.putString("id", id);
        tag.putString("type", type.name());
        NbtSupport.putUuid(tag, "owner", owner);
        tag.putString("business", business == null ? "" : business);
        tag.putLong("balance", balance);
        tag.putString("status", status.name());
        tag.putLong("createdAt", createdAt);
        tag.putLong("lastSeq", lastSeq);
        return tag;
    }

    /**
     * Reads an account from a tag. A tag without an id cannot be keyed and is refused with a clear
     * message; every other missing field takes its documented default.
     */
    public static AccountRecord fromTag(final CompoundTag tag) {
        final String business = NbtSupport.getString(tag, "business", "");
        return new AccountRecord(
                NbtSupport.getString(tag, "id", ""),
                AccountType.fromName(NbtSupport.getString(tag, "type", "")),
                NbtSupport.getUuid(tag, "owner"),
                business.isBlank() ? null : business,
                NbtSupport.getLong(tag, "balance", 0L),
                AccountStatus.fromName(NbtSupport.getString(tag, "status", "")),
                NbtSupport.getLong(tag, "createdAt", 0L),
                NbtSupport.getLong(tag, "lastSeq", 0L));
    }
}
