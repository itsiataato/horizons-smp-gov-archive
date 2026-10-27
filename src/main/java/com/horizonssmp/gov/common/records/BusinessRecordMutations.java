package com.horizonssmp.gov.common.records;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.horizonssmp.gov.common.enums.EmploymentStatus;

/**
 * Mutation helpers for {@link BusinessRecord}. Behavior-preserving delegates.
 */
public final class BusinessRecordMutations {

    private BusinessRecordMutations() {
    }

    public static BusinessRecord withStatus(
            final BusinessRecord r, final String newStatus, final String reason, final UUID by, final long at) {
        final String normalized = BusinessRecord.normalizeStatus(newStatus);
        final boolean suspending = BusinessRecord.STATUS_SUSPENDED.equals(normalized);
        return new BusinessRecord(
                r.id(), r.name(), r.ownerUuid(), r.type(), normalized, r.createdAt(), r.createdBy(),
                r.linkedJobId(), r.employees(), r.payroll(), r.notes(),
                suspending && reason != null && !reason.isBlank() ? reason : r.suspendReason(),
                suspending && at > 0L ? at : r.suspendedAt(),
                suspending && by != null ? by : r.suspendedBy(),
                r.positions(), r.contracts(), r.complaints(), r.closeRequestedBy(), r.closeRequestedAt());
    }

    public static BusinessRecord withOwner(final BusinessRecord r, final UUID newOwner) {
        return new BusinessRecord(
                r.id(), r.name(), newOwner, r.type(), r.status(), r.createdAt(), r.createdBy(),
                r.linkedJobId(), r.employees(), r.payroll(), r.notes(), r.suspendReason(), r.suspendedAt(), r.suspendedBy(),
                r.positions(), r.contracts(), r.complaints(), r.closeRequestedBy(), r.closeRequestedAt());
    }

    public static BusinessRecord withEmployees(final BusinessRecord r, final List<BusinessEmployee> newEmployees) {
        return new BusinessRecord(
                r.id(), r.name(), r.ownerUuid(), r.type(), r.status(), r.createdAt(), r.createdBy(),
                r.linkedJobId(), newEmployees, r.payroll(), r.notes(), r.suspendReason(), r.suspendedAt(), r.suspendedBy(),
                r.positions(), r.contracts(), r.complaints(), r.closeRequestedBy(), r.closeRequestedAt());
    }

    public static BusinessRecord withLinkedJob(final BusinessRecord r, final String jobId) {
        return new BusinessRecord(
                r.id(), r.name(), r.ownerUuid(), r.type(), r.status(), r.createdAt(), r.createdBy(),
                jobId == null ? "" : jobId, r.employees(), r.payroll(), r.notes(),
                r.suspendReason(), r.suspendedAt(), r.suspendedBy(),
                r.positions(), r.contracts(), r.complaints(), r.closeRequestedBy(), r.closeRequestedAt());
    }

    public static BusinessRecord withName(final BusinessRecord r, final String newName) {
        return new BusinessRecord(
                r.id(), newName, r.ownerUuid(), r.type(), r.status(), r.createdAt(), r.createdBy(),
                r.linkedJobId(), r.employees(), r.payroll(), r.notes(), r.suspendReason(), r.suspendedAt(), r.suspendedBy(),
                r.positions(), r.contracts(), r.complaints(), r.closeRequestedBy(), r.closeRequestedAt());
    }

    public static BusinessRecord withPayroll(final BusinessRecord r, final PayrollInfo newPayroll) {
        return new BusinessRecord(
                r.id(), r.name(), r.ownerUuid(), r.type(), r.status(), r.createdAt(), r.createdBy(),
                r.linkedJobId(), r.employees(), newPayroll, r.notes(), r.suspendReason(), r.suspendedAt(), r.suspendedBy(),
                r.positions(), r.contracts(), r.complaints(), r.closeRequestedBy(), r.closeRequestedAt());
    }

    public static BusinessRecord withNotes(final BusinessRecord r, final String newNotes) {
        return new BusinessRecord(
                r.id(), r.name(), r.ownerUuid(), r.type(), r.status(), r.createdAt(), r.createdBy(),
                r.linkedJobId(), r.employees(), r.payroll(), newNotes, r.suspendReason(), r.suspendedAt(), r.suspendedBy(),
                r.positions(), r.contracts(), r.complaints(), r.closeRequestedBy(), r.closeRequestedAt());
    }

    public static BusinessRecord withPositions(final BusinessRecord r, final List<BusinessPositionRecord> newPositions) {
        return new BusinessRecord(
                r.id(), r.name(), r.ownerUuid(), r.type(), r.status(), r.createdAt(), r.createdBy(),
                r.linkedJobId(), r.employees(), r.payroll(), r.notes(), r.suspendReason(), r.suspendedAt(), r.suspendedBy(),
                newPositions, r.contracts(), r.complaints(), r.closeRequestedBy(), r.closeRequestedAt());
    }

    public static BusinessRecord withContracts(final BusinessRecord r, final List<EmploymentContractRecord> newContracts) {
        return new BusinessRecord(
                r.id(), r.name(), r.ownerUuid(), r.type(), r.status(), r.createdAt(), r.createdBy(),
                r.linkedJobId(), r.employees(), r.payroll(), r.notes(), r.suspendReason(), r.suspendedAt(), r.suspendedBy(),
                r.positions(), newContracts, r.complaints(), r.closeRequestedBy(), r.closeRequestedAt());
    }

    public static BusinessRecord withComplaints(final BusinessRecord r, final List<BusinessComplaintRecord> newComplaints) {
        return new BusinessRecord(
                r.id(), r.name(), r.ownerUuid(), r.type(), r.status(), r.createdAt(), r.createdBy(),
                r.linkedJobId(), r.employees(), r.payroll(), r.notes(), r.suspendReason(), r.suspendedAt(), r.suspendedBy(),
                r.positions(), r.contracts(), newComplaints, r.closeRequestedBy(), r.closeRequestedAt());
    }

    public static BusinessRecord withCloseRequest(final BusinessRecord r, final UUID by, final long at) {
        return new BusinessRecord(
                r.id(), r.name(), r.ownerUuid(), r.type(), r.status(), r.createdAt(), r.createdBy(),
                r.linkedJobId(), r.employees(), r.payroll(), r.notes(), r.suspendReason(), r.suspendedAt(), r.suspendedBy(),
                r.positions(), r.contracts(), r.complaints(), by, by == null ? 0L : at);
    }

    public static BusinessRecord withCeoContract(final BusinessRecord r, final String ownerName, final long now) {
        if (r.ownerUuid() == null) {
            return r;
        }
        BusinessRecord result = r;
        if (BusinessRecordQueries.ceoPosition(result) == null) {
            final List<BusinessPositionRecord> withRoot = new ArrayList<>();
            withRoot.add(BusinessPositionRecord.ceo(now, r.ownerUuid()));
            withRoot.addAll(result.positions());
            result = result.withPositions(withRoot);
        }
        if (result.hasActiveBusinessRole(r.ownerUuid())) {
            return result;
        }
        final String baseId = "c-" + r.id() + "-ceo";
        String contractId = baseId;
        int suffix = 2;
        while (result.contract(contractId) != null) {
            contractId = baseId + "-" + suffix++;
        }
        final String name = ownerName == null || ownerName.isBlank()
                ? r.ownerUuid().toString() : ownerName.trim();
        final EmploymentContractRecord contract = new EmploymentContractRecord(
                contractId,
                BusinessPositionRecord.CEO_POSITION_ID,
                r.ownerUuid(),
                name,
                r.ownerUuid(),
                0L,
                false,
                EmploymentStatus.ACTIVE,
                now,
                now,
                0L,
                0L,
                0L,
                "");
        final List<EmploymentContractRecord> withFounder = new ArrayList<>();
        withFounder.add(contract);
        withFounder.addAll(result.contracts());
        return result.withContracts(withFounder);
    }
}