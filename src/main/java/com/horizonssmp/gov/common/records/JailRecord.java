package com.horizonssmp.gov.common.records;

import java.util.UUID;

import com.horizonssmp.gov.common.enums.LegalBasis;

import net.minecraft.nbt.CompoundTag;

/**
 * The detention block of a player's card.
 *
 * <p>A release is recorded, not erased: {@code active} goes false and {@code releasedAt} /
 * {@code releasedBy} are filled in, so the card can answer "why was this person held and who let
 * them out" after the fact. The same shape serves a future warrant-backed detention and a
 * consent-based one, because the authority is stored as {@link LegalBasis} rather than inferred.
 *
 * @param active whether the player is currently detained
 * @param cellRegion the configured cell region tag they were placed in, or empty
 * @param since epoch milliseconds the detention started, {@code 0} when never
 * @param until epoch milliseconds the detention is due to end, {@code 0} when open-ended
 * @param legalBasis the authority for the detention
 * @param reason the stated reason, already sanitized by the service that committed it
 * @param committedBy the official who committed the player, or {@code null} if unknown
 * @param releasedAt epoch milliseconds of the release, {@code 0} while still held
 * @param releasedBy the official who released the player, or {@code null} if unknown
 */
public record JailRecord(
        boolean active,
        String cellRegion,
        long since,
        long until,
        LegalBasis legalBasis,
        String reason,
        UUID committedBy,
        long releasedAt,
        UUID releasedBy) {

    /** Not detained, nothing recorded. */
    public static final JailRecord NONE =
            new JailRecord(false, "", 0L, 0L, LegalBasis.CONSENT, "", null, 0L, null);

    public JailRecord {
        cellRegion = cellRegion == null ? "" : cellRegion;
        reason = reason == null ? "" : reason;
        legalBasis = legalBasis == null ? LegalBasis.CONSENT : legalBasis;
    }

    /** True when the detention has an expiry that has already passed. */
    public boolean expiredAt(final long now) {
        return until > 0L && now >= until;
    }

    /** Serializes this block into a fresh tag. */
    public CompoundTag write() {
        final CompoundTag tag = new CompoundTag();
        tag.putBoolean("active", active);
        tag.putString("cellRegion", cellRegion);
        tag.putLong("since", since);
        tag.putLong("until", until);
        tag.putString("legalBasis", legalBasis.name());
        tag.putString("reason", reason);
        NbtSupport.putUuid(tag, "committedBy", committedBy);
        tag.putLong("releasedAt", releasedAt);
        NbtSupport.putUuid(tag, "releasedBy", releasedBy);
        return tag;
    }

    /** Reads the block, returning {@link #NONE} when the tag is missing or empty. */
    public static JailRecord read(final CompoundTag tag) {
        if (tag == null || tag.isEmpty()) {
            return NONE;
        }
        return new JailRecord(
                NbtSupport.getBoolean(tag, "active", false),
                NbtSupport.getString(tag, "cellRegion", ""),
                NbtSupport.getLong(tag, "since", 0L),
                NbtSupport.getLong(tag, "until", 0L),
                LegalBasis.parse(NbtSupport.getString(tag, "legalBasis", "")),
                NbtSupport.getString(tag, "reason", ""),
                NbtSupport.getUuid(tag, "committedBy"),
                NbtSupport.getLong(tag, "releasedAt", 0L),
                NbtSupport.getUuid(tag, "releasedBy"));
    }
}
