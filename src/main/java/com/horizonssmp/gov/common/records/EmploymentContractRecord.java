package com.horizonssmp.gov.common.records;

import java.util.UUID;

import com.horizonssmp.gov.common.BusinessKeys;
import com.horizonssmp.gov.common.enums.EmploymentStatus;

import net.minecraft.nbt.CompoundTag;

/**
 * One employment contract between a player and a business.
 *
 * <p>A contract is the only thing that gives a player a business position. The {@code positionId}
 * names a {@link BusinessPositionRecord} on the business; the contract is the person-to-seat link,
 * not a copy of the seat's permissions. Editing the position therefore changes what every holder of
 * it may do, exactly as editing a government job changes its holders.
 *
 * <p><b>One business slot.</b> A player may hold one {@link EmploymentStatus#ACTIVE} contract that
 * is not {@link #oneTime()}. A one-time task contract is also ACTIVE while it runs, but it does not
 * occupy that slot: it is a job of work, not a standing office. The service, not this record,
 * enforces the slot; the record exposes the facts so the rule has one shape everywhere.
 *
 * <p><b>Wages are per real 24 hours.</b> {@code wagePerDay} is a stated rate in gold ingots, never
 * a balance. The mod holds no gold and settles no payment; {@code paidTotal} is the sum of physical
 * payments an authorized person recorded, which is why it can be used by the fine service as the
 * restitution figure on a complaint upheld against an employer. There is no account and no debt
 * field here.
 *
 * <p>Terminal contracts are retained forever. A fired, resigned, declined, expired or completed
 * contract is evidence: the complaint, payroll and history records that reference it must still
 * resolve after the person has left.
 *
 * <p>Reads are tolerant: an unknown status reads as {@link EmploymentStatus#TERMINATED} (never
 * ACTIVE), a missing employee or employer drops the row, and out-of-range numbers drop it rather
 * than clamping a wage into a different deal from the one that was agreed.
 *
 * @param id the stable contract id
 * @param positionId the position this contract fills
 * @param employeeUuid the employee's UUID
 * @param employeeName the employee's display name at the time of the offer
 * @param employerUuid the business owner's UUID, the party who may fire and is the complaint target
 * @param wagePerDay the wage in gold ingots per real 24 hours
 * @param oneTime true for a single task; a one-time contract never occupies the standing slot
 * @param status the contract lifecycle status
 * @param offeredAt epoch milliseconds the offer was sent
 * @param startedAt epoch milliseconds the offer was accepted, {@code 0} while pending
 * @param endedAt epoch milliseconds the contract ended, {@code 0} while open
 * @param lastPaidAt epoch milliseconds of the last recorded payroll payment, {@code 0} when none
 * @param paidTotal the total recorded physical wages in gold ingots, {@code 0} when none
 * @param endReason the stated reason for the ending, or empty
 */
public record EmploymentContractRecord(
        String id,
        String positionId,
        UUID employeeUuid,
        String employeeName,
        UUID employerUuid,
        long wagePerDay,
        boolean oneTime,
        EmploymentStatus status,
        long offeredAt,
        long startedAt,
        long endedAt,
        long lastPaidAt,
        long paidTotal,
        String endReason) {

    public EmploymentContractRecord {
        id = id == null ? "" : id.trim();
        if (id.isEmpty()) {
            throw new IllegalArgumentException("EmploymentContractRecord requires an id");
        }
        positionId = positionId == null ? "" : positionId.trim();
        if (positionId.isEmpty()) {
            throw new IllegalArgumentException("EmploymentContractRecord requires a positionId");
        }
        if (employeeUuid == null) {
            throw new IllegalArgumentException("EmploymentContractRecord requires an employeeUuid");
        }
        if (employerUuid == null) {
            throw new IllegalArgumentException("EmploymentContractRecord requires an employerUuid");
        }
        employeeName = employeeName == null ? "" : employeeName.trim();
        if (wagePerDay < 0L || wagePerDay > BusinessKeys.MAX_WAGE_PER_DAY) {
            throw new IllegalArgumentException(
                    "EmploymentContractRecord wage must be 0.." + BusinessKeys.MAX_WAGE_PER_DAY);
        }
        if (status == null) {
            throw new IllegalArgumentException("EmploymentContractRecord requires a status");
        }
        // A corrupt negative stamp is not a date; zero means "not recorded", which every reader
        // already understands. Clamping is safe here because no logic compares these to a deadline.
        offeredAt = Math.max(0L, offeredAt);
        startedAt = Math.max(0L, startedAt);
        endedAt = Math.max(0L, endedAt);
        lastPaidAt = Math.max(0L, lastPaidAt);
        paidTotal = Math.max(0L, paidTotal);
        endReason = endReason == null ? "" : endReason.trim();
    }

    /** True when the contract is in force and authorizes its position. */
    public boolean active() {
        return status == EmploymentStatus.ACTIVE;
    }

    /** True when the contract has ended and can never become active again. */
    public boolean terminated() {
        return status.terminal();
    }

    /**
     * Returns a copy with a new status.
     *
     * <p>Accepting a contract stamps {@code startedAt} if it was not already stamped. A terminal
     * status stamps {@code endedAt} when the caller passes a positive time. Reactivating a
     * terminated contract is not a legal transition, but this record does not enforce the state
     * machine; it only keeps the timestamps truthful so a service that does enforce it has clean
     * data. An {@code at} of zero means "keep the stored stamp", which is what a status-only call
     * needs.
     */
    public EmploymentContractRecord withStatus(
            final EmploymentStatus newStatus, final long at, final String reason) {
        if (newStatus == null) {
            throw new IllegalArgumentException("EmploymentContractRecord status is required");
        }
        final long stamp = Math.max(0L, at);
        final long newStarted = newStatus == EmploymentStatus.ACTIVE && startedAt <= 0L
                ? (stamp > 0L ? stamp : offeredAt)
                : startedAt;
        // An ACTIVE contract has not ended, so a stale end stamp from an earlier state must not
        // linger; a terminal status keeps the first ending it recorded unless a new time is given.
        final long newEnded = newStatus == EmploymentStatus.ACTIVE
                ? 0L
                : (newStatus.terminal() && stamp > 0L ? stamp : endedAt);
        final String newReason = newStatus == EmploymentStatus.ACTIVE
                ? ""
                : (newStatus.terminal() && reason != null ? reason.trim() : endReason);
        return new EmploymentContractRecord(
                id, positionId, employeeUuid, employeeName, employerUuid, wagePerDay, oneTime,
                newStatus, offeredAt, newStarted, newEnded, lastPaidAt, paidTotal, newReason);
    }

    /** Returns a copy with a new status and no new timestamp. */
    public EmploymentContractRecord withStatus(final EmploymentStatus newStatus) {
        return withStatus(newStatus, 0L, "");
    }

    /**
     * Returns a copy moved to another position.
     *
     * <p>The service checks that the target position exists and is below the actor before calling
     * this. A promotion is a position move; the contract id, employee and payroll history do not
     * change, so a promotion is never a second employment relationship.
     */
    public EmploymentContractRecord withPosition(final String newPositionId) {
        return new EmploymentContractRecord(
                id, newPositionId, employeeUuid, employeeName, employerUuid, wagePerDay, oneTime,
                status, offeredAt, startedAt, endedAt, lastPaidAt, paidTotal, endReason);
    }

    /**
     * Returns a copy with one recorded physical payment added to the total.
     *
     * <p>{@code paidTotal} accumulates because it is the figure a complaint uses as restitution.
     * A non-positive amount still stamps the payment time when one was supplied; the stored total
     * never decreases. Saturation at {@link Long#MAX_VALUE} is a theoretical guard against a
     * hand-edited file, not a real wage.
     */
    public EmploymentContractRecord withPayroll(final long at, final long amount) {
        final long added = Math.max(0L, amount);
        final long total = paidTotal > Long.MAX_VALUE - added
                ? Long.MAX_VALUE
                : paidTotal + added;
        return new EmploymentContractRecord(
                id, positionId, employeeUuid, employeeName, employerUuid, wagePerDay, oneTime,
                status, offeredAt, startedAt, endedAt,
                at > 0L ? at : lastPaidAt, total, endReason);
    }

    /** Serializes this contract into a fresh tag. */
    public CompoundTag write() {
        final CompoundTag tag = new CompoundTag();
        tag.putString("id", id);
        tag.putString("positionId", positionId);
        NbtSupport.putUuid(tag, "employeeUuid", employeeUuid);
        tag.putString("employeeName", employeeName);
        NbtSupport.putUuid(tag, "employerUuid", employerUuid);
        tag.putLong("wagePerDay", wagePerDay);
        tag.putBoolean("oneTime", oneTime);
        tag.putString("status", status.name());
        tag.putLong("offeredAt", offeredAt);
        tag.putLong("startedAt", startedAt);
        tag.putLong("endedAt", endedAt);
        tag.putLong("lastPaidAt", lastPaidAt);
        tag.putLong("paidTotal", paidTotal);
        tag.putString("endReason", endReason);
        return tag;
    }

    /**
     * Reads one contract, returning {@code null} when it has no usable identity or a bad wage.
     *
     * <p>Dropping the row is safer than inventing an employer or a wage: an orphan contract would
     * be an employment relationship with a party the file never named.
     */
    public static EmploymentContractRecord read(final CompoundTag tag) {
        if (tag == null || tag.isEmpty()) {
            return null;
        }
        final String id = NbtSupport.getString(tag, "id", "").trim();
        final String positionId = NbtSupport.getString(tag, "positionId", "").trim();
        final UUID employeeUuid = NbtSupport.getUuid(tag, "employeeUuid");
        final UUID employerUuid = NbtSupport.getUuid(tag, "employerUuid");
        final long wage = NbtSupport.getLong(tag, "wagePerDay", 0L);
        if (id.isEmpty() || positionId.isEmpty() || employeeUuid == null || employerUuid == null
                || wage < 0L || wage > BusinessKeys.MAX_WAGE_PER_DAY) {
            return null;
        }
        try {
            return new EmploymentContractRecord(
                    id,
                    positionId,
                    employeeUuid,
                    NbtSupport.getString(tag, "employeeName", ""),
                    employerUuid,
                    wage,
                    NbtSupport.getBoolean(tag, "oneTime", false),
                    EmploymentStatus.parse(NbtSupport.getString(tag, "status", "")),
                    NbtSupport.getLong(tag, "offeredAt", 0L),
                    NbtSupport.getLong(tag, "startedAt", 0L),
                    NbtSupport.getLong(tag, "endedAt", 0L),
                    NbtSupport.getLong(tag, "lastPaidAt", 0L),
                    NbtSupport.getLong(tag, "paidTotal", 0L),
                    NbtSupport.getString(tag, "endReason", ""));
        } catch (final IllegalArgumentException e) {
            return null;
        }
    }
}
