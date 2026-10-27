package com.horizonssmp.gov.common.records;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.horizonssmp.gov.common.BusinessKeys;

import net.minecraft.nbt.CompoundTag;

/**
 * A registered business. See original documentation; behavior preserved by delegating to helpers.
 */
public record BusinessRecord(
        String id,
        String name,
        UUID ownerUuid,
        String type,
        String status,
        long createdAt,
        UUID createdBy,
        String linkedJobId,
        List<BusinessEmployee> employees,
        PayrollInfo payroll,
        String notes,
        String suspendReason,
        long suspendedAt,
        UUID suspendedBy,
        List<BusinessPositionRecord> positions,
        List<EmploymentContractRecord> contracts,
        List<BusinessComplaintRecord> complaints,
        UUID closeRequestedBy,
        long closeRequestedAt) {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_SUSPENDED = "SUSPENDED";
    public static final String STATUS_CLOSED = "CLOSED";
    public static final String LEGACY_STATUS_REVOKED = "REVOKED";
    public static final int MAX_POSITIONS = BusinessKeys.MAX_POSITIONS;
    public static final int MAX_CONTRACTS = BusinessKeys.MAX_CONTRACTS;
    public static final int MAX_COMPLAINTS = BusinessKeys.MAX_COMPLAINTS;

    public BusinessRecord {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("BusinessRecord requires an id");
        }
        name = name == null ? "" : name;
        type = type == null ? "" : type;
        status = normalizeStatus(status);
        linkedJobId = linkedJobId == null ? "" : linkedJobId;
        employees = employees == null ? List.of() : List.copyOf(employees);
        payroll = payroll == null ? PayrollInfo.NONE : payroll;
        notes = notes == null ? "" : notes;
        suspendReason = suspendReason == null ? "" : suspendReason;
        positions = BusinessRecordLists.bounded(positions, MAX_POSITIONS);
        contracts = BusinessRecordLists.bounded(contracts, MAX_CONTRACTS);
        complaints = BusinessRecordLists.bounded(complaints, MAX_COMPLAINTS);
        closeRequestedAt = closeRequestedBy == null ? 0L : Math.max(0L, closeRequestedAt);
    }

    public BusinessRecord(
            final String id,
            final String name,
            final UUID ownerUuid,
            final String type,
            final String status,
            final long createdAt,
            final UUID createdBy,
            final String linkedJobId,
            final List<BusinessEmployee> employees,
            final PayrollInfo payroll,
            final String notes,
            final String suspendReason,
            final long suspendedAt,
            final UUID suspendedBy) {
        this(id, name, ownerUuid, type, status, createdAt, createdBy, linkedJobId, employees,
                payroll, notes, suspendReason, suspendedAt, suspendedBy,
                List.of(), List.of(), List.of(), null, 0L);
    }

    public static String normalizeStatus(final String raw) {
        if (raw == null || raw.isBlank()) {
            return STATUS_ACTIVE;
        }
        final String needle = raw.trim().toUpperCase(Locale.ROOT);
        if (LEGACY_STATUS_REVOKED.equals(needle)) {
            return STATUS_CLOSED;
        }
        return needle;
    }

    public boolean active() {
        return BusinessRecordQueries.active(this);
    }

    public boolean suspended() {
        return BusinessRecordQueries.suspended(this);
    }

    public boolean closed() {
        return BusinessRecordQueries.closed(this);
    }

    public BusinessEmployee employee(final UUID uuid) {
        return BusinessRecordQueries.employee(this, uuid);
    }

    public boolean employs(final UUID uuid) {
        return BusinessRecordQueries.employs(this, uuid);
    }

    public BusinessPositionRecord position(final String positionId) {
        return BusinessRecordQueries.position(this, positionId);
    }

    public BusinessPositionRecord ceoPosition() {
        return BusinessRecordQueries.ceoPosition(this);
    }

    public EmploymentContractRecord activeContractFor(final UUID playerUuid) {
        return BusinessRecordQueries.activeContractFor(this, playerUuid);
    }

    public EmploymentContractRecord standingContractFor(final UUID playerUuid) {
        return BusinessRecordQueries.standingContractFor(this, playerUuid);
    }

    public boolean hasActiveBusinessRole(final UUID playerUuid) {
        return BusinessRecordQueries.hasActiveBusinessRole(this, playerUuid);
    }

    public boolean pendingClose() {
        return BusinessRecordQueries.pendingClose(this);
    }

    public BusinessRecord withStatus(
            final String newStatus, final String reason, final UUID by, final long at) {
        return BusinessRecordMutations.withStatus(this, newStatus, reason, by, at);
    }

    public BusinessRecord withOwner(final UUID newOwner) {
        return BusinessRecordMutations.withOwner(this, newOwner);
    }

    public BusinessRecord withEmployees(final List<BusinessEmployee> newEmployees) {
        return BusinessRecordMutations.withEmployees(this, newEmployees);
    }

    public BusinessRecord withLinkedJob(final String jobId) {
        return BusinessRecordMutations.withLinkedJob(this, jobId);
    }

    public BusinessRecord withName(final String newName) {
        return BusinessRecordMutations.withName(this, newName);
    }

    public BusinessRecord withPayroll(final PayrollInfo newPayroll) {
        return BusinessRecordMutations.withPayroll(this, newPayroll);
    }

    public BusinessRecord withNotes(final String newNotes) {
        return BusinessRecordMutations.withNotes(this, newNotes);
    }

    public BusinessRecord withPositions(final List<BusinessPositionRecord> newPositions) {
        return BusinessRecordMutations.withPositions(this, newPositions);
    }

    public BusinessRecord withContracts(final List<EmploymentContractRecord> newContracts) {
        return BusinessRecordMutations.withContracts(this, newContracts);
    }

    public BusinessRecord withComplaints(final List<BusinessComplaintRecord> newComplaints) {
        return BusinessRecordMutations.withComplaints(this, newComplaints);
    }

    public BusinessRecord withCloseRequest(final UUID by, final long at) {
        return BusinessRecordMutations.withCloseRequest(this, by, at);
    }

    public BusinessRecord withCeoContract(final String ownerName, final long now) {
        return BusinessRecordMutations.withCeoContract(this, ownerName, now);
    }

    public EmploymentContractRecord contract(final String contractId) {
        return BusinessRecordQueries.contract(this, contractId);
    }

    public CompoundTag write() {
        return BusinessRecordNbt.write(this);
    }

    public static BusinessRecord read(final CompoundTag tag, final String fallbackId) {
        return BusinessRecordNbt.read(tag, fallbackId);
    }
}