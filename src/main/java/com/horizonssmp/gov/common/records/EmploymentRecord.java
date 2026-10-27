package com.horizonssmp.gov.common.records;

import java.util.UUID;

import net.minecraft.nbt.CompoundTag;

/**
 * The current employment block of a player's card: which job, at what rank, since when.
 *
 * <p>This duplicates {@code primaryJobId} and {@code rank} on purpose. The flat fields are the
 * current values, cheap to read and to index; this block is the denormalized copy that stays
 * readable if the job definition is later edited or deleted, which is what the audit trail and the
 * dashboard need.
 *
 * @param jobId the job definition id, or empty when the player has never been employed
 * @param rank the rank label at the time of assignment, or empty
 * @param since epoch milliseconds the job was assigned, {@code 0} when never
 * @param assignedBy the official who assigned it, or {@code null} if unknown
 */
public record EmploymentRecord(String jobId, String rank, long since, UUID assignedBy) {

    /** No employment recorded. */
    public static final EmploymentRecord NONE = new EmploymentRecord("", "", 0L, null);

    public EmploymentRecord {
        jobId = jobId == null ? "" : jobId;
        rank = rank == null ? "" : rank;
    }

    /** True when a job has been assigned. */
    public boolean employed() {
        return !jobId.isEmpty();
    }

    /** Serializes this block into a fresh tag. */
    public CompoundTag write() {
        final CompoundTag tag = new CompoundTag();
        tag.putString("jobId", jobId);
        tag.putString("rank", rank);
        tag.putLong("since", since);
        NbtSupport.putUuid(tag, "assignedBy", assignedBy);
        return tag;
    }

    /** Reads the block, returning {@link #NONE} when the tag is missing or empty. */
    public static EmploymentRecord read(final CompoundTag tag) {
        if (tag == null || tag.isEmpty()) {
            return NONE;
        }
        return new EmploymentRecord(
                NbtSupport.getString(tag, "jobId", ""),
                NbtSupport.getString(tag, "rank", ""),
                NbtSupport.getLong(tag, "since", 0L),
                NbtSupport.getUuid(tag, "assignedBy"));
    }
}
