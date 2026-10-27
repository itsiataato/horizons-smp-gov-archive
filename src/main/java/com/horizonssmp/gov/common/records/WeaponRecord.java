package com.horizonssmp.gov.common.records;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.horizonssmp.gov.common.enums.WeaponCategory;

import net.minecraft.nbt.CompoundTag;

/**
 * A registered firearm.
 *
 * <p>Two names are stored on purpose. {@code exactName} is what the officer saw and typed;
 * {@code taczId} is the optional mod-provided identifier when the weapon comes from a gun mod.
 * Classifying by exact display name is fragile across mod updates, so the identifier is preferred
 * when present and the name is the fallback that always exists. {@code type} is the registry's
 * free-text category: an item id when the classifier supplied one, or the officer's description.
 *
 * <p>{@code category} and {@code militaryGrade} are separate: the category is the law's schedule,
 * the flag is the hard registration block, and every reader checks the flag so a category can
 * never be used as the only guard. {@code permitId} is a reference to the holder's valid carry
 * permit at registration time; it is empty for a state-held weapon.
 *
 * <p>Transfer history is immutable data: each accepted ownership change appends one entry with
 * both the requested and the completed stamp, so the Article 5.09 one-in-game-day recording window
 * can be checked after the fact. The list is capped; the audit log keeps the complete action
 * history.
 *
 * @param id the stable record id
 * @param serial the canonical serial number, unique among registered weapons, case-insensitive
 * @param exactName the display name as recorded
 * @param taczId the optional mod identifier, or empty
 * @param category the law schedule category
 * @param militaryGrade true when the weapon is military grade and may not be civilian-registered
 * @param classifiedBy the official who classified it, or {@code null} when still unclassified
 * @param classifiedAt epoch milliseconds of classification, {@code 0} when still unclassified
 * @param ownerUuid the registered holder, or {@code null} for a state-held weapon
 * @param dealerUuid the dealer who supplied it, or {@code null} if unknown
 * @param source how the weapon entered the register, or empty
 * @param registeredAt epoch milliseconds the weapon was registered
 * @param registeredBy the official who registered it, or {@code null} if unknown
 * @param expiresAt epoch milliseconds the registration expires, {@code 0} for no expiry
 * @param status one of {@link #STATUS_ACTIVE}, {@link #STATUS_REVOKED}, {@link #STATUS_LOST}
 * @param type the registry category or item id, or empty
 * @param permitId the permit reference at registration, or empty
 * @param soldAt epoch milliseconds of a dealer sale, {@code 0} when not a dealer sale
 * @param transfers every accepted ownership change, oldest first
 */
public record WeaponRecord(
        String id,
        String serial,
        String exactName,
        String taczId,
        WeaponCategory category,
        boolean militaryGrade,
        UUID classifiedBy,
        long classifiedAt,
        UUID ownerUuid,
        UUID dealerUuid,
        String source,
        long registeredAt,
        UUID registeredBy,
        long expiresAt,
        String status,
        String type,
        String permitId,
        long soldAt,
        List<TransferEntry> transfers) {

    /** Registered and valid. */
    public static final String STATUS_ACTIVE = "ACTIVE";
    /** Withdrawn by the government; the holder may no longer keep it. */
    public static final String STATUS_REVOKED = "REVOKED";
    /** Reported lost or stolen. The record stays so the weapon can be matched later. */
    public static final String STATUS_LOST = "LOST";
    /** The registration lapsed. */
    public static final String STATUS_EXPIRED = "EXPIRED";

    /** Safety cap on stored transfer entries; the audit log is the full history. */
    public static final int MAX_TRANSFER_ENTRIES = 64;

    /**
     * One recorded ownership change.
     *
     * @param fromUuid the previous owner, or {@code null} for a state-held weapon
     * @param toUuid the new owner
     * @param requestedAt epoch milliseconds the transfer was requested
     * @param completedAt epoch milliseconds the transfer completed, {@code 0} when still pending
     * @param by the official who completed it, or {@code null}
     * @param note an optional note, or empty
     */
    public record TransferEntry(
            UUID fromUuid, UUID toUuid, long requestedAt, long completedAt, UUID by, String note) {

        public TransferEntry {
            note = note == null ? "" : note;
        }

        /** True when the completed stamp is inside the Article 5.09 one-day window. */
        public boolean recordedWithinWindow() {
            return completedAt > 0L && requestedAt > 0L
                    && completedAt >= requestedAt
                    && completedAt - requestedAt <= com.horizonssmp.gov.common.GovTime.days(1);
        }

        public CompoundTag write() {
            final CompoundTag tag = new CompoundTag();
            NbtSupport.putUuid(tag, "fromUuid", fromUuid);
            NbtSupport.putUuid(tag, "toUuid", toUuid);
            tag.putLong("requestedAt", requestedAt);
            tag.putLong("completedAt", completedAt);
            NbtSupport.putUuid(tag, "by", by);
            tag.putString("note", note);
            return tag;
        }

        public static TransferEntry read(final CompoundTag tag) {
            final UUID toUuid = NbtSupport.getUuid(tag, "toUuid");
            if (toUuid == null) {
                return null;
            }
            return new TransferEntry(
                    NbtSupport.getUuid(tag, "fromUuid"),
                    toUuid,
                    NbtSupport.getLong(tag, "requestedAt", 0L),
                    NbtSupport.getLong(tag, "completedAt", 0L),
                    NbtSupport.getUuid(tag, "by"),
                    NbtSupport.getString(tag, "note", ""));
        }
    }

    public WeaponRecord {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("WeaponRecord requires an id");
        }
        serial = serial == null ? "" : serial;
        exactName = exactName == null ? "" : exactName;
        taczId = taczId == null ? "" : taczId;
        category = category == null ? WeaponCategory.UNCLASSIFIED : category;
        source = source == null ? "" : source;
        status = status == null || status.isBlank() ? STATUS_ACTIVE : status;
        type = type == null ? "" : type;
        permitId = permitId == null ? "" : permitId;
        transfers = transfers == null ? List.of() : List.copyOf(transfers);
    }

    /** True when the weapon is still classified as unclassified. */
    public boolean unclassified() {
        return category == WeaponCategory.UNCLASSIFIED;
    }

    /** True when the registration is active and not reported lost. */
    public boolean active() {
        return STATUS_ACTIVE.equals(status);
    }

    /** Returns a copy with a new owner and one appended transfer entry. */
    public WeaponRecord withOwner(
            final UUID newOwner, final UUID by, final long now, final String note) {
        final List<TransferEntry> next = new ArrayList<>(transfers);
        next.add(new TransferEntry(ownerUuid, newOwner, now, now, by, note));
        if (next.size() > MAX_TRANSFER_ENTRIES) {
            next.subList(0, next.size() - MAX_TRANSFER_ENTRIES).clear();
        }
        return new WeaponRecord(
                id, serial, exactName, taczId, category, militaryGrade,
                classifiedBy, classifiedAt, newOwner, dealerUuid, source,
                registeredAt, registeredBy, expiresAt, status, type, permitId, soldAt, next);
    }

    /** Returns a copy with the permit reference refreshed. */
    public WeaponRecord withPermit(final String nextPermitId) {
        return new WeaponRecord(
                id, serial, exactName, taczId, category, militaryGrade,
                classifiedBy, classifiedAt, ownerUuid, dealerUuid, source,
                registeredAt, registeredBy, expiresAt, status, type, nextPermitId, soldAt, transfers);
    }

    /** Returns a copy revoked now, with the official and the reason recorded. */
    public WeaponRecord revoked(final UUID by, final long now) {
        return new WeaponRecord(
                id, serial, exactName, taczId, category, militaryGrade,
                classifiedBy, classifiedAt, ownerUuid, dealerUuid, source,
                registeredAt, registeredBy, expiresAt, STATUS_REVOKED, type, permitId, soldAt, transfers);
    }

    /** Returns a copy reported lost. */
    public WeaponRecord lost() {
        return new WeaponRecord(
                id, serial, exactName, taczId, category, militaryGrade,
                classifiedBy, classifiedAt, ownerUuid, dealerUuid, source,
                registeredAt, registeredBy, expiresAt, STATUS_LOST, type, permitId, soldAt, transfers);
    }

    /** Serializes this weapon into a fresh tag. */
    public CompoundTag write() {
        final CompoundTag tag = new CompoundTag();
        tag.putString("id", id);
        tag.putString("serial", serial);
        tag.putString("exactName", exactName);
        tag.putString("taczId", taczId);
        tag.putString("category", category.name());
        tag.putBoolean("militaryGrade", militaryGrade);
        NbtSupport.putUuid(tag, "classifiedBy", classifiedBy);
        tag.putLong("classifiedAt", classifiedAt);
        NbtSupport.putUuid(tag, "ownerUuid", ownerUuid);
        NbtSupport.putUuid(tag, "dealerUuid", dealerUuid);
        tag.putString("source", source);
        tag.putLong("registeredAt", registeredAt);
        NbtSupport.putUuid(tag, "registeredBy", registeredBy);
        tag.putLong("expiresAt", expiresAt);
        tag.putString("status", status);
        tag.putString("type", type);
        tag.putString("permitId", permitId);
        tag.putLong("soldAt", soldAt);
        NbtSupport.putCompoundList(tag, "transfers",
                transfers.stream().map(TransferEntry::write).toList());
        return tag;
    }

    /** Reads a weapon. A tag without an id cannot be keyed, so the caller supplies the key. */
    public static WeaponRecord read(final CompoundTag tag, final String fallbackId) {
        final String stored = NbtSupport.getString(tag, "id", "");
        final String id = stored.isBlank() ? fallbackId : stored;
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("WeaponRecord tag has no id and no fallback");
        }
        final List<TransferEntry> transfers = new ArrayList<>();
        for (final CompoundTag element : NbtSupport.getCompoundList(tag, "transfers")) {
            final TransferEntry entry = TransferEntry.read(element);
            if (entry != null) {
                transfers.add(entry);
            }
        }
        return new WeaponRecord(
                id,
                NbtSupport.getString(tag, "serial", ""),
                NbtSupport.getString(tag, "exactName", ""),
                NbtSupport.getString(tag, "taczId", ""),
                WeaponCategory.parse(NbtSupport.getString(tag, "category", "")),
                NbtSupport.getBoolean(tag, "militaryGrade", false),
                NbtSupport.getUuid(tag, "classifiedBy"),
                NbtSupport.getLong(tag, "classifiedAt", 0L),
                NbtSupport.getUuid(tag, "ownerUuid"),
                NbtSupport.getUuid(tag, "dealerUuid"),
                NbtSupport.getString(tag, "source", ""),
                NbtSupport.getLong(tag, "registeredAt", 0L),
                NbtSupport.getUuid(tag, "registeredBy"),
                NbtSupport.getLong(tag, "expiresAt", 0L),
                NbtSupport.getString(tag, "status", STATUS_ACTIVE),
                NbtSupport.getString(tag, "type", ""),
                NbtSupport.getString(tag, "permitId", ""),
                NbtSupport.getLong(tag, "soldAt", 0L),
                transfers);
    }
}
