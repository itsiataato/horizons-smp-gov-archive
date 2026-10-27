package com.horizonssmp.gov.common.records;

import java.util.UUID;

import com.horizonssmp.gov.common.JailKeys;
import com.horizonssmp.gov.common.enums.DetentionStatus;
import com.horizonssmp.gov.common.enums.LegalBasis;

import net.minecraft.nbt.CompoundTag;

/**
 * One detention: who was held, by whom, under what authority, for how long, and how it ended.
 *
 * <p>A release is recorded, not erased. {@code status} moves from {@link DetentionStatus#ACTIVE} to
 * {@link DetentionStatus#RELEASED} or {@link DetentionStatus#EXPIRED}, the release fields are filled
 * in, and the row stays in the history. That is what lets the Jail tab answer "why was this person
 * held and who let them out" after the fact.
 *
 * <p><b>No unlimited detention.</b> {@code maxDurationMinutes} is always written and is always
 * positive. There is no open-ended state: the service derives the term from the legal basis (the
 * law's ten-minute cap without a warrant, or the warrant's own stated duration bounded by config).
 *
 * <p><b>No inventory.</b> This record never names an item, a stack or an inventory slot, and the
 * service that writes it never touches one. Cuffs are a movement and interaction state; they are
 * not a search or a seizure.
 *
 * <p><b>Fail-safe read.</b> An unknown status reads back as {@link DetentionStatus#RELEASED}, and a
 * non-active record can never be read back as cuffed. A damaged file must not be able to keep
 * someone in a cell or in cuffs.
 *
 * @param id the stable detention id
 * @param targetUuid the held player, never {@code null} in a stored record
 * @param targetName the held player's display name at detention time
 * @param officerUuid the detaining official
 * @param officerName the detaining official's display name at detention time
 * @param legalBasis the authority for the detention
 * @param reason the stated reason, already sanitized by the service that committed it
 * @param warrantRef the warrant reference, or empty for a non-warrant basis
 * @param startedAt epoch milliseconds the detention started
 * @param maxDurationMinutes the recorded term in real minutes, always positive
 * @param releasedAt epoch milliseconds the record ended, {@code 0} while active
 * @param releasedBy the official who ended it, or {@code null} for a timer expiry
 * @param releasedByName the display name of the closer, or {@code "system"} for an expiry
 * @param releaseReason the release or expiry reason
 * @param status the lifecycle state
 * @param cell the native cell the player was placed in, or {@link LocationRecord#NONE}
 * @param from the native position the player was taken from, or {@link LocationRecord#NONE}
 * @param cuffed whether native cuffs were applied; always false on a non-active read
 * @param jailAdapter the adapter that handled confinement: {@code plugin}, {@code native} or
 *     {@code none}
 * @param extensionCount how many times an official extended the term
 * @param extendedAt epoch milliseconds of the last extension, {@code 0} when none
 * @param extendedBy the last extending official, or {@code null}
 * @param extendedByName the last extending official's display name, or empty
 * @param extensionReason the last extension reason, or empty
 */
public record DetentionRecord(
        String id,
        UUID targetUuid,
        String targetName,
        UUID officerUuid,
        String officerName,
        LegalBasis legalBasis,
        String reason,
        String warrantRef,
        long startedAt,
        int maxDurationMinutes,
        long releasedAt,
        UUID releasedBy,
        String releasedByName,
        String releaseReason,
        DetentionStatus status,
        LocationRecord cell,
        LocationRecord from,
        boolean cuffed,
        String jailAdapter,
        int extensionCount,
        long extendedAt,
        UUID extendedBy,
        String extendedByName,
        String extensionReason) {

    /** Prefix of a detention id. */
    public static final String ID_PREFIX = "d-";

    /** The adapter id for a native cell. */
    public static final String ADAPTER_NATIVE = "native";
    /** The adapter id for a plugin jail. */
    public static final String ADAPTER_PLUGIN = "plugin";
    /** The adapter id when no confinement is possible. */
    public static final String ADAPTER_NONE = "none";

    /** The display name recorded for an automatic expiry. */
    public static final String SYSTEM_NAME = "system";

    public DetentionRecord {
        id = id == null ? "" : id;
        targetName = targetName == null ? "" : targetName;
        officerName = officerName == null ? "" : officerName;
        legalBasis = legalBasis == null ? LegalBasis.CONSENT : legalBasis;
        reason = reason == null ? "" : reason;
        warrantRef = warrantRef == null ? "" : warrantRef;
        status = status == null ? DetentionStatus.RELEASED : status;
        cell = cell == null ? LocationRecord.NONE : cell;
        from = from == null ? LocationRecord.NONE : from;
        jailAdapter = jailAdapter == null ? ADAPTER_NONE : jailAdapter;
        releasedByName = releasedByName == null ? "" : releasedByName;
        releaseReason = releaseReason == null ? "" : releaseReason;
        extensionReason = extensionReason == null ? "" : extensionReason;
        extendedByName = extendedByName == null ? "" : extendedByName;
        maxDurationMinutes = Math.max(0,
                Math.min(maxDurationMinutes, JailKeys.HARD_MAX_WARRANT_MINUTES));
        extensionCount = Math.max(0, extensionCount);
        // A record that already carries a release stamp is not active, whatever a hand-edited
        // status says. The safe direction is released.
        if (status == DetentionStatus.ACTIVE && releasedAt > 0L) {
            status = DetentionStatus.RELEASED;
        }
        // Cuffs are a live state; a stored non-active record cannot keep them on.
        if (status != DetentionStatus.ACTIVE) {
            cuffed = false;
        }
    }

    /** True while the record is the player's current detention. */
    /** Returns a copy of this record with a different native cell. */
    public DetentionRecord withCell(final LocationRecord newCell) {
        return new DetentionRecord(
                id, targetUuid, targetName, officerUuid, officerName, legalBasis, reason, warrantRef,
                startedAt, maxDurationMinutes, releasedAt, releasedBy, releasedByName, releaseReason,
                status, newCell == null ? LocationRecord.NONE : newCell, from, cuffed, jailAdapter,
                extensionCount, extendedAt, extendedBy, extendedByName, extensionReason);
    }

    public boolean active() {
        return status == DetentionStatus.ACTIVE;
    }

    /** The epoch millisecond at which the recorded term ends. */
    public long expiresAt() {
        return startedAt + maxDurationMinutes * 60_000L;
    }

    /** Remaining milliseconds, never negative; {@code 0} when not active. */
    public long remainingMs(final long now) {
        if (!active()) {
            return 0L;
        }
        return Math.max(0L, expiresAt() - now);
    }

    /** True when an active record's term has passed. */
    public boolean expiredAt(final long now) {
        return active() && now >= expiresAt();
    }

    /**
     * The status a reader should show at {@code now}: an active record whose term has passed reads
     * as expired before the tick that persists it. The stored status is only changed by the timer.
     */
    public DetentionStatus effectiveStatus(final long now) {
        return active() && expiredAt(now) ? DetentionStatus.EXPIRED : status;
    }

    /** Returns a copy released by an official. */
    public DetentionRecord released(
            final UUID by, final String byName, final String reason, final long now) {
        return new DetentionRecord(
                id, targetUuid, targetName, officerUuid, officerName, legalBasis, this.reason,
                warrantRef, startedAt, maxDurationMinutes, now, by,
                byName == null ? "" : byName, reason == null ? "" : reason, DetentionStatus.RELEASED,
                cell, from, false, jailAdapter, extensionCount, extendedAt, extendedBy,
                extendedByName, extensionReason);
    }

    /** Returns a copy ended by the enforcement timer or the warrant term. */
    public DetentionRecord expired(final String reason, final long now) {
        return new DetentionRecord(
                id, targetUuid, targetName, officerUuid, officerName, legalBasis, this.reason,
                warrantRef, startedAt, maxDurationMinutes, now, null, SYSTEM_NAME,
                reason == null ? "" : reason, DetentionStatus.EXPIRED,
                cell, from, false, jailAdapter, extensionCount, extendedAt, extendedBy,
                extendedByName, extensionReason);
    }

    /** Returns a copy with the cuff state changed. */
    public DetentionRecord withCuffed(final boolean cuffed) {
        return new DetentionRecord(
                id, targetUuid, targetName, officerUuid, officerName, legalBasis, reason,
                warrantRef, startedAt, maxDurationMinutes, releasedAt, releasedBy, releasedByName,
                releaseReason, status, cell, from, cuffed, jailAdapter, extensionCount, extendedAt,
                extendedBy, extendedByName, extensionReason);
    }

    /** Returns a copy with the term extended by an official. */
    public DetentionRecord extended(
            final int minutes, final UUID by, final String byName, final String reason,
            final long now) {
        return new DetentionRecord(
                id, targetUuid, targetName, officerUuid, officerName, legalBasis, this.reason,
                warrantRef, startedAt, maxDurationMinutes + Math.max(0, minutes),
                releasedAt, releasedBy, releasedByName, releaseReason, status, cell, from, cuffed,
                jailAdapter, extensionCount + 1, now, by, byName, reason);
    }

    /** Serializes this block into a fresh tag. */
    public CompoundTag write() {
        final CompoundTag tag = new CompoundTag();
        tag.putString("id", id);
        NbtSupport.putUuid(tag, "targetUuid", targetUuid);
        tag.putString("targetName", targetName);
        NbtSupport.putUuid(tag, "officerUuid", officerUuid);
        tag.putString("officerName", officerName);
        tag.putString("legalBasis", legalBasis.name());
        tag.putString("reason", reason);
        tag.putString("warrantRef", warrantRef);
        tag.putLong("startedAt", startedAt);
        tag.putInt("maxDurationMinutes", maxDurationMinutes);
        tag.putLong("releasedAt", releasedAt);
        NbtSupport.putUuid(tag, "releasedBy", releasedBy);
        tag.putString("releasedByName", releasedByName);
        tag.putString("releaseReason", releaseReason);
        tag.putString("status", status.name());
        tag.put("cell", cell.write());
        tag.put("from", from.write());
        tag.putBoolean("cuffed", cuffed);
        tag.putString("jailAdapter", jailAdapter);
        tag.putInt("extensionCount", extensionCount);
        tag.putLong("extendedAt", extendedAt);
        NbtSupport.putUuid(tag, "extendedBy", extendedBy);
        tag.putString("extendedByName", extendedByName);
        tag.putString("extensionReason", extensionReason);
        return tag;
    }

    /**
     * Reads a detention block, returning {@code null} when it cannot be keyed or has no target.
     *
     * <p>The only hard requirement is an id and a target UUID: a record without either cannot be
     * matched to a player, released, or indexed in the history. Everything else degrades to the
     * documented safe default rather than failing the world load.
     */
    public static DetentionRecord read(final CompoundTag tag) {
        if (tag == null || tag.isEmpty()) {
            return null;
        }
        final String id = NbtSupport.getString(tag, "id", "");
        final UUID target = NbtSupport.getUuid(tag, "targetUuid");
        if (id.isBlank() || target == null) {
            return null;
        }
        return new DetentionRecord(
                id,
                target,
                NbtSupport.getString(tag, "targetName", ""),
                NbtSupport.getUuid(tag, "officerUuid"),
                NbtSupport.getString(tag, "officerName", ""),
                LegalBasis.parse(NbtSupport.getString(tag, "legalBasis", "")),
                NbtSupport.getString(tag, "reason", ""),
                NbtSupport.getString(tag, "warrantRef", ""),
                NbtSupport.getLong(tag, "startedAt", 0L),
                NbtSupport.getInt(tag, "maxDurationMinutes", 0),
                NbtSupport.getLong(tag, "releasedAt", 0L),
                NbtSupport.getUuid(tag, "releasedBy"),
                NbtSupport.getString(tag, "releasedByName", ""),
                NbtSupport.getString(tag, "releaseReason", ""),
                DetentionStatus.parse(NbtSupport.getString(tag, "status", "")),
                LocationRecord.read(NbtSupport.getCompoundOr(tag, "cell")),
                LocationRecord.read(NbtSupport.getCompoundOr(tag, "from")),
                NbtSupport.getBoolean(tag, "cuffed", false),
                NbtSupport.getString(tag, "jailAdapter", ADAPTER_NONE),
                NbtSupport.getInt(tag, "extensionCount", 0),
                NbtSupport.getLong(tag, "extendedAt", 0L),
                NbtSupport.getUuid(tag, "extendedBy"),
                NbtSupport.getString(tag, "extendedByName", ""),
                NbtSupport.getString(tag, "extensionReason", ""));
    }
}
