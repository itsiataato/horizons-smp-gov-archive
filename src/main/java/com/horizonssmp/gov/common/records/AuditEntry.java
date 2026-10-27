package com.horizonssmp.gov.common.records;

import java.util.UUID;

import com.horizonssmp.gov.common.enums.AuditOutcome;

import net.minecraft.nbt.CompoundTag;

/**
 * One line of the audit log.
 *
 * <p>The log is append-only and ordered by {@code seq}, a monotonically increasing counter owned by
 * {@link com.horizonssmp.gov.server.GovSavedData}. Ordering by counter rather than by timestamp
 * matters: two actions in the same millisecond, or a clock correction, must not reorder the record
 * of who did what.
 *
 * <p>Actor and target names are copied in at write time and never refreshed. An audit line must
 * still read correctly after a rename, a job change or a deletion, which is exactly when it is
 * being read.
 *
 * @param seq the monotonic sequence number, unique within the data file
 * @param at epoch milliseconds the action was recorded
 * @param actorUuid the player who acted, or {@code null} for a system action
 * @param actorName the actor's name at the time
 * @param actorJobId the actor's job id at the time, or empty
 * @param action the action verb: an {@code OpCode} name, or a service verb such as {@code
 *     SEED_JOBS}
 * @param outcome whether the action was accepted or refused
 * @param targetUuid the target's UUID, or {@code null} when the action had no person target
 * @param targetName the target's name at the time, or empty
 * @param payload a sanitized, truncated summary of the request, or empty
 * @param reason the stated reason, or empty
 */
public record AuditEntry(
        long seq,
        long at,
        UUID actorUuid,
        String actorName,
        String actorJobId,
        String action,
        AuditOutcome outcome,
        UUID targetUuid,
        String targetName,
        String payload,
        String reason) {

    public AuditEntry {
        if (action == null || action.isBlank()) {
            throw new IllegalArgumentException("AuditEntry requires an action");
        }
        outcome = outcome == null ? AuditOutcome.ACCEPTED : outcome;
        actorName = actorName == null ? "" : actorName;
        actorJobId = actorJobId == null ? "" : actorJobId;
        targetName = targetName == null ? "" : targetName;
        payload = payload == null ? "" : payload;
        reason = reason == null ? "" : reason;
    }

    /** Serializes this line into a fresh tag. */
    public CompoundTag write() {
        final CompoundTag tag = new CompoundTag();
        tag.putLong("seq", seq);
        tag.putLong("at", at);
        NbtSupport.putUuid(tag, "actorUuid", actorUuid);
        tag.putString("actorName", actorName);
        tag.putString("actorJobId", actorJobId);
        tag.putString("action", action);
        tag.putString("outcome", outcome.name());
        NbtSupport.putUuid(tag, "targetUuid", targetUuid);
        tag.putString("targetName", targetName);
        tag.putString("payload", payload);
        tag.putString("reason", reason);
        return tag;
    }

    /** Reads a line. A line without a sequence number is unusable, so the caller supplies one. */
    public static AuditEntry read(final CompoundTag tag, final long fallbackSeq) {
        return new AuditEntry(
                NbtSupport.getLong(tag, "seq", fallbackSeq),
                NbtSupport.getLong(tag, "at", 0L),
                NbtSupport.getUuid(tag, "actorUuid"),
                NbtSupport.getString(tag, "actorName", ""),
                NbtSupport.getString(tag, "actorJobId", ""),
                NbtSupport.getString(tag, "action", "UNKNOWN"),
                // A line written before M9 has no outcome and described an accepted change.
                AuditOutcome.parse(NbtSupport.getString(tag, "outcome", "")),
                NbtSupport.getUuid(tag, "targetUuid"),
                NbtSupport.getString(tag, "targetName", ""),
                NbtSupport.getString(tag, "payload", ""),
                NbtSupport.getString(tag, "reason", ""));
    }
}
