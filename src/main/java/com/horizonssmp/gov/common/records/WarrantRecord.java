package com.horizonssmp.gov.common.records;

import java.util.List;
import java.util.UUID;

import net.minecraft.nbt.CompoundTag;

/**
 * A warrant.
 *
 * <p>A warrant is the legal basis that unlocks the actions the law allows only with one, which is
 * why {@code probableCause} is a required, stored field rather than a log line: the dispatcher and
 * the audit trail both need to point at a reason that existed before the action.
 *
 * <p>{@code witnesses} stores names, not UUIDs. A witness may be anyone present, including someone
 * who never joins the server again, and a warrant must not lose its witnesses because an account
 * was renamed or a UUID could not be resolved at issue time.
 *
 * @param id the stable warrant id
 * @param subjectUuid the person the warrant is against, or {@code null} for a place-only warrant
 * @param place the place the warrant covers, or empty
 * @param items the items the warrant covers, or empty
 * @param probableCause the stated probable cause, required by the issuing service
 * @param issuedBy the official who issued it, or {@code null} if unknown
 * @param issuedAt epoch milliseconds the warrant was issued
 * @param expiresAt epoch milliseconds the warrant lapses, {@code 0} for no expiry
 * @param witnesses the names of those present at issue
 * @param status one of {@link #STATUS_ACTIVE}, {@link #STATUS_SERVED}, {@link #STATUS_EXPIRED},
 *     {@link #STATUS_WITHDRAWN}
 */
public record WarrantRecord(
        String id,
        UUID subjectUuid,
        String place,
        List<String> items,
        String probableCause,
        UUID issuedBy,
        long issuedAt,
        long expiresAt,
        List<String> witnesses,
        String status) {

    /** Issued and in force. */
    public static final String STATUS_ACTIVE = "ACTIVE";
    /** Executed; the warrant has been used. */
    public static final String STATUS_SERVED = "SERVED";
    /** Lapsed by its own expiry. */
    public static final String STATUS_EXPIRED = "EXPIRED";
    /** Withdrawn by the government before execution. */
    public static final String STATUS_WITHDRAWN = "WITHDRAWN";

    public WarrantRecord {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("WarrantRecord requires an id");
        }
        place = place == null ? "" : place;
        items = items == null ? List.of() : List.copyOf(items);
        probableCause = probableCause == null ? "" : probableCause;
        witnesses = witnesses == null ? List.of() : List.copyOf(witnesses);
        status = status == null || status.isBlank() ? STATUS_ACTIVE : status;
    }

    /** True when this warrant can still authorize an action at the given time. */
    public boolean inForceAt(final long now) {
        return STATUS_ACTIVE.equals(status) && (expiresAt <= 0L || now < expiresAt);
    }

    /** Serializes this warrant into a fresh tag. */
    public CompoundTag write() {
        final CompoundTag tag = new CompoundTag();
        tag.putString("id", id);
        NbtSupport.putUuid(tag, "subjectUuid", subjectUuid);
        tag.putString("place", place);
        NbtSupport.putStringList(tag, "items", items);
        tag.putString("probableCause", probableCause);
        NbtSupport.putUuid(tag, "issuedBy", issuedBy);
        tag.putLong("issuedAt", issuedAt);
        tag.putLong("expiresAt", expiresAt);
        NbtSupport.putStringList(tag, "witnesses", witnesses);
        tag.putString("status", status);
        return tag;
    }

    /** Reads a warrant. A tag without an id cannot be keyed, so the caller supplies the key. */
    public static WarrantRecord read(final CompoundTag tag, final String fallbackId) {
        final String stored = NbtSupport.getString(tag, "id", "");
        final String id = stored.isBlank() ? fallbackId : stored;
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("WarrantRecord tag has no id and no fallback");
        }
        return new WarrantRecord(
                id,
                NbtSupport.getUuid(tag, "subjectUuid"),
                NbtSupport.getString(tag, "place", ""),
                NbtSupport.getStringList(tag, "items"),
                NbtSupport.getString(tag, "probableCause", ""),
                NbtSupport.getUuid(tag, "issuedBy"),
                NbtSupport.getLong(tag, "issuedAt", 0L),
                NbtSupport.getLong(tag, "expiresAt", 0L),
                NbtSupport.getStringList(tag, "witnesses"),
                NbtSupport.getString(tag, "status", STATUS_ACTIVE));
    }
}
