package com.horizonssmp.gov.common.records;

import java.util.UUID;

import net.minecraft.nbt.CompoundTag;

/**
 * One complaint the CEO filed against an active employee.
 *
 * <p>Three upheld complaints against the same employee are the contractual threshold for dismissal
 * with a fine. The complaint is stored on the business rather than only in the audit log because the
 * count must survive a restart and must be visible to the parties; {@code fineId} ties the upheld
 * complaint to the exact fine that the existing fine/restitution path created. A complaint with an
 * empty {@code fineId} is either still pending or was not upheld.
 *
 * <p>The reason is retained verbatim (after null normalization) as the employee's side of the
 * record. It is not truncated here: a dismissed employee reading the complaint must see the
 * accusation that was actually made. The service that accepts the input applies its own display
 * sanitizer before storage, exactly as it does for fine reasons.
 *
 * <p>Reads are tolerant: a row without an id, an employee or a filer is dropped. Both UUIDs are
 * required because a complaint with no accused or no accuser cannot support dismissal and would be
 * a record of nothing.
 *
 * @param id the stable complaint id
 * @param employeeUuid the employee complained about
 * @param filedByUuid the CEO or owner who filed it
 * @param reason the stated reason, or empty
 * @param createdAt epoch milliseconds the complaint was filed
 * @param upheld true once the complaint was upheld and the fine was issued
 * @param fineId the fine created by the upheld complaint, or empty
 */
public record BusinessComplaintRecord(
        String id,
        UUID employeeUuid,
        UUID filedByUuid,
        String reason,
        long createdAt,
        boolean upheld,
        String fineId) {

    public BusinessComplaintRecord {
        id = id == null ? "" : id.trim();
        if (id.isEmpty()) {
            throw new IllegalArgumentException("BusinessComplaintRecord requires an id");
        }
        if (employeeUuid == null) {
            throw new IllegalArgumentException("BusinessComplaintRecord requires an employeeUuid");
        }
        if (filedByUuid == null) {
            throw new IllegalArgumentException("BusinessComplaintRecord requires a filedByUuid");
        }
        reason = reason == null ? "" : reason.trim();
        createdAt = Math.max(0L, createdAt);
        fineId = fineId == null ? "" : fineId.trim();
    }

    /**
     * Returns a copy marked upheld with the fine that was issued.
     *
     * <p>A blank fine id is accepted because a future adjudication flow may uphold the complaint
     * before the fine id is known; the caller overwrites it when the fine exists. The count toward
     * the three-complaint threshold is by upheld flag, never by the presence of a fine id.
     */
    public BusinessComplaintRecord withUpheld(final String upheldFineId) {
        return new BusinessComplaintRecord(
                id, employeeUuid, filedByUuid, reason, createdAt, true,
                upheldFineId == null ? "" : upheldFineId);
    }

    /** Serializes this complaint into a fresh tag. */
    public CompoundTag write() {
        final CompoundTag tag = new CompoundTag();
        tag.putString("id", id);
        NbtSupport.putUuid(tag, "employeeUuid", employeeUuid);
        NbtSupport.putUuid(tag, "filedByUuid", filedByUuid);
        tag.putString("reason", reason);
        tag.putLong("createdAt", createdAt);
        tag.putBoolean("upheld", upheld);
        tag.putString("fineId", fineId);
        return tag;
    }

    /** Reads one complaint, returning {@code null} when it has no usable identity or parties. */
    public static BusinessComplaintRecord read(final CompoundTag tag) {
        if (tag == null || tag.isEmpty()) {
            return null;
        }
        final String id = NbtSupport.getString(tag, "id", "").trim();
        final UUID employee = NbtSupport.getUuid(tag, "employeeUuid");
        final UUID filer = NbtSupport.getUuid(tag, "filedByUuid");
        if (id.isEmpty() || employee == null || filer == null) {
            return null;
        }
        try {
            return new BusinessComplaintRecord(
                    id,
                    employee,
                    filer,
                    NbtSupport.getString(tag, "reason", ""),
                    NbtSupport.getLong(tag, "createdAt", 0L),
                    NbtSupport.getBoolean(tag, "upheld", false),
                    NbtSupport.getString(tag, "fineId", ""));
        } catch (final IllegalArgumentException e) {
            return null;
        }
    }
}
