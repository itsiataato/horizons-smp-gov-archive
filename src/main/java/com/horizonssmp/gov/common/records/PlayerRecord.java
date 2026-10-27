package com.horizonssmp.gov.common.records;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import com.horizonssmp.gov.common.GovCapabilities;
import com.horizonssmp.gov.common.GovCapability;
import com.horizonssmp.gov.common.enums.DutyState;

import net.minecraft.nbt.CompoundTag;

/**
 * One person's government file: identity, employment, duty state, suspect flag and detention.
 *
 * <p>The file is keyed by UUID, which is the only stable identity a Minecraft account has. The last
 * known name is stored for display and for audit readability, never as a lookup key: the dispatcher
 * resolves a payload name to a UUID on the server and then works with the UUID.
 *
 * <p>{@code capabilities} holds personal grants only. Job capabilities live on the job definition,
 * and the effective set is the union computed by
 * {@link com.horizonssmp.gov.common.GovCapabilities#effective}. Storing them separately is what
 * makes a job edit take effect immediately for everyone who holds it, without rewriting every
 * player file.
 *
 * @param uuid the player's UUID, never {@code null}
 * @param lastName the last known display name, or empty
 * @param firstSeen epoch milliseconds of the first join, {@code 0} when unknown
 * @param lastSeen epoch milliseconds of the most recent join, {@code 0} when unknown
 * @param primaryJobId the job definition id, or empty when unemployed
 * @param jobAssignedAt epoch milliseconds of the current job assignment, {@code 0} when never
 * @param jobAssignedBy the official who made the assignment, or {@code null} if unknown
 * @param rank the rank label copied from the job at assignment time, or empty
 * @param capabilities personal capability grants, on top of the job's set
 * @param suspect the suspect flag, never {@code null}
 * @param duty the duty state, {@link DutyState#N_A} when the job has no duty concept
 * @param employment the denormalized employment block, never {@code null}
 * @param jail the detention block, never {@code null}
 * @param jobDecided true once a job decision has been recorded for this player: the first-join
 *     default, an assignment, or an unassignment. It exists so that an explicit unassignment
 *     stays unassigned instead of being undone by the citizen default on the next login, and so a
 *     file written before the field existed (it reads as {@code false}) still gets the default
 *     applied once.
 */
public record PlayerRecord(
        UUID uuid,
        String lastName,
        long firstSeen,
        long lastSeen,
        String primaryJobId,
        long jobAssignedAt,
        UUID jobAssignedBy,
        String rank,
        Set<GovCapability> capabilities,
        SuspectRecord suspect,
        DutyState duty,
        EmploymentRecord employment,
        JailRecord jail,
        boolean jobDecided) {

    public PlayerRecord {
        if (uuid == null) {
            throw new IllegalArgumentException("PlayerRecord requires a uuid");
        }
        lastName = lastName == null ? "" : lastName;
        primaryJobId = primaryJobId == null ? "" : primaryJobId;
        rank = rank == null ? "" : rank;
        capabilities = capabilities == null
                ? EnumSet.noneOf(GovCapability.class)
                // A caller may pass Set.of(); the shared copy handles empty collections safely.
                : GovCapabilities.copyOf(capabilities);
        suspect = suspect == null ? SuspectRecord.NONE : suspect;
        duty = duty == null ? DutyState.N_A : duty;
        employment = employment == null ? EmploymentRecord.NONE : employment;
        jail = jail == null ? JailRecord.NONE : jail;
    }

    /**
     * A fresh file for a player who has never been seen: no job, no grants, no flags.
     *
     * @param uuid the player's UUID
     * @param lastName the player's current name
     * @param now epoch milliseconds to record as first and last seen
     */
    public static PlayerRecord blank(final UUID uuid, final String lastName, final long now) {
        return new PlayerRecord(
                uuid,
                lastName,
                now,
                now,
                "",
                0L,
                null,
                "",
                EnumSet.noneOf(GovCapability.class),
                SuspectRecord.NONE,
                DutyState.N_A,
                EmploymentRecord.NONE,
                JailRecord.NONE,
                false);
    }

    /** True when the player currently holds a job. */
    public boolean employed() {
        return !primaryJobId.isEmpty();
    }

    /** True when the player is currently detained. */
    public boolean jailed() {
        return jail.active();
    }

    /** True when the suspect flag currently applies. */
    public boolean isSuspect() {
        return suspect.active();
    }

    /** Returns a copy with the last-seen stamp and name refreshed. */
    public PlayerRecord seenAt(final long now, final String name) {
        return new PlayerRecord(
                uuid,
                name == null || name.isEmpty() ? lastName : name,
                firstSeen,
                now,
                primaryJobId,
                jobAssignedAt,
                jobAssignedBy,
                rank,
                capabilities,
                suspect,
                duty,
                employment,
                jail,
                jobDecided);
    }

    /**
     * Returns a copy with a different duty state.
     *
     * <p>Duty is a property of the official, not of the job definition, so changing it is a copy of
     * this file rather than an edit to the job. Used by the duty action and by the startup check's
     * "off duty may not accept a fine payment" assertion.
     */
    public PlayerRecord withDuty(final DutyState duty) {
        return new PlayerRecord(
                uuid,
                lastName,
                firstSeen,
                lastSeen,
                primaryJobId,
                jobAssignedAt,
                jobAssignedBy,
                rank,
                capabilities,
                suspect,
                duty,
                employment,
                jail,
                jobDecided);
    }

    /**
     * Returns a copy holding exactly one job, with the denormalized employment block in step.
     *
     * <p>An empty job id clears the job: the primary id, the rank, the assignment stamp and the
     * employment block all become empty. Either way {@code jobDecided} is set, so an explicit
     * decision (including an unassignment) is never overwritten by the first-join default.
     *
     * @param jobId the job id, or empty to clear the job
     * @param rank the rank label to record, or empty
     * @param assignedAt epoch milliseconds of the assignment, or {@code 0} when cleared
     * @param assignedBy the official who made the decision, or {@code null} for the server default
     * @param decided the value for the job-decided marker, normally {@code true}
     */
    public PlayerRecord withJob(
            final String jobId, final String rank, final long assignedAt,
            final UUID assignedBy, final boolean decided) {
        final String id = jobId == null ? "" : jobId;
        return new PlayerRecord(
                uuid,
                lastName,
                firstSeen,
                lastSeen,
                id,
                id.isEmpty() ? 0L : assignedAt,
                id.isEmpty() ? null : assignedBy,
                id.isEmpty() ? "" : rank,
                capabilities,
                suspect,
                duty,
                id.isEmpty() ? EmploymentRecord.NONE
                        : new EmploymentRecord(id, rank, assignedAt, assignedBy),
                jail,
                decided);
    }

    /**
     * Returns a copy with only the job-decided marker changed.
     *
     * <p>WHY: an older file can hold its job only in the denormalized employment block, with an
     * empty {@code primaryJobId}. A copy that took {@code primaryJobId} through {@link #withJob}
     * would map that empty id to {@link EmploymentRecord#NONE}, collapse the block and destroy the
     * job and every capability derived from it. This copy carries every field through untouched.
     */
    public PlayerRecord withJobDecided(final boolean decided) {
        return new PlayerRecord(
                uuid,
                lastName,
                firstSeen,
                lastSeen,
                primaryJobId,
                jobAssignedAt,
                jobAssignedBy,
                rank,
                capabilities,
                suspect,
                duty,
                employment,
                jail,
                decided);
    }

    /** Returns a copy with a different detention summary. */
    public PlayerRecord withJail(final JailRecord jail) {
        return new PlayerRecord(
                uuid,
                lastName,
                firstSeen,
                lastSeen,
                primaryJobId,
                jobAssignedAt,
                jobAssignedBy,
                rank,
                capabilities,
                suspect,
                duty,
                employment,
                jail == null ? JailRecord.NONE : jail,
                jobDecided);
    }

    /** Serializes this file into a fresh tag. */
    public CompoundTag write() {
        final CompoundTag tag = new CompoundTag();
        tag.putUUID("uuid", uuid);
        tag.putString("lastName", lastName);
        tag.putLong("firstSeen", firstSeen);
        tag.putLong("lastSeen", lastSeen);
        tag.putString("primaryJobId", primaryJobId);
        tag.putLong("jobAssignedAt", jobAssignedAt);
        NbtSupport.putUuid(tag, "jobAssignedBy", jobAssignedBy);
        tag.putString("rank", rank);
        NbtSupport.putCapabilities(tag, "capabilities", capabilities);
        tag.put("suspect", suspect.write());
        tag.putString("duty", duty.name());
        tag.put("employment", employment.write());
        tag.put("jail", jail.write());
        tag.putBoolean("jobDecided", jobDecided);
        return tag;
    }

    /** Reads a file. A tag without a UUID cannot be keyed, so the caller supplies the key. */
    public static PlayerRecord read(final CompoundTag tag, final UUID fallbackUuid) {
        final UUID stored = NbtSupport.getUuid(tag, "uuid");
        final UUID uuid = stored != null ? stored : fallbackUuid;
        if (uuid == null) {
            throw new IllegalArgumentException("PlayerRecord tag has no uuid and no fallback");
        }
        return new PlayerRecord(
                uuid,
                NbtSupport.getString(tag, "lastName", ""),
                NbtSupport.getLong(tag, "firstSeen", 0L),
                NbtSupport.getLong(tag, "lastSeen", 0L),
                NbtSupport.getString(tag, "primaryJobId", ""),
                NbtSupport.getLong(tag, "jobAssignedAt", 0L),
                NbtSupport.getUuid(tag, "jobAssignedBy"),
                NbtSupport.getString(tag, "rank", ""),
                NbtSupport.getCapabilities(tag, "capabilities"),
                SuspectRecord.read(NbtSupport.getCompoundOr(tag, "suspect")),
                DutyState.parse(NbtSupport.getString(tag, "duty", "")),
                EmploymentRecord.read(NbtSupport.getCompoundOr(tag, "employment")),
                JailRecord.read(NbtSupport.getCompoundOr(tag, "jail")),
                // A file written before M2 has no such key; false makes the first-join default
                // apply once, which is exactly the intended rollout behaviour for old worlds.
                NbtSupport.getBoolean(tag, "jobDecided", false));
    }
}
