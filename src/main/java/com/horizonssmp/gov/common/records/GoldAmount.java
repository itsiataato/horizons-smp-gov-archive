package com.horizonssmp.gov.common.records;

import net.minecraft.nbt.CompoundTag;

/**
 * An amount of gold ingots.
 *
 * <p>The currency of this server is physical gold ingots. This type exists so that every amount
 * carries its unit explicitly, which is what makes the locked-currency rule checkable: the only
 * value {@code currency} may hold in this build is {@link #GOLD_INGOT}, and the config refuses to
 * load any other mode. Bank accounts are a separate custodial ledger of gold; this record
 * describes physical items that changed hands, not an account.
 *
 * <p>The unit is written next to the number rather than assumed, so a future unit (a different
 * item, or a stack count) cannot be silently interpreted as ingots by an old build.
 *
 * @param currency the unit id, {@link #GOLD_INGOT} in this build
 * @param count the number of units
 */
public record GoldAmount(String currency, long count) {

    /** The only currency this build accepts. Matches the default of {@code gov.fine.currencyMode}. */
    public static final String GOLD_INGOT = "GOLD_INGOT";

    /** Zero ingots. */
    public static final GoldAmount ZERO = new GoldAmount(GOLD_INGOT, 0L);

    public GoldAmount {
        currency = currency == null || currency.isBlank() ? GOLD_INGOT : currency;
        count = Math.max(0L, count);
    }

    /** An amount of gold ingots. */
    public static GoldAmount of(final long count) {
        return count <= 0L ? ZERO : new GoldAmount(GOLD_INGOT, count);
    }

    /** True when this amount is nothing. */
    public boolean isZero() {
        return count <= 0L;
    }

    /** Serializes this amount into a fresh tag. */
    public CompoundTag write() {
        final CompoundTag tag = new CompoundTag();
        tag.putString("currency", currency);
        tag.putLong("count", count);
        return tag;
    }

    /** Reads an amount, returning {@link #ZERO} when the tag is missing or empty. */
    public static GoldAmount read(final CompoundTag tag) {
        if (tag == null || tag.isEmpty()) {
            return ZERO;
        }
        return new GoldAmount(
                NbtSupport.getString(tag, "currency", GOLD_INGOT),
                NbtSupport.getLong(tag, "count", 0L));
    }
}
