package com.horizonssmp.gov.common.records;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import com.horizonssmp.gov.common.enums.EmploymentStatus;

/**
 * Query helpers for {@link BusinessRecord}. Behavior-preserving delegates; the public API stays on
 * {@link BusinessRecord}.
 */
public final class BusinessRecordQueries {

    private BusinessRecordQueries() {
    }

    public static boolean active(final BusinessRecord r) {
        return BusinessRecord.STATUS_ACTIVE.equals(r.status());
    }

    public static boolean suspended(final BusinessRecord r) {
        return BusinessRecord.STATUS_SUSPENDED.equals(r.status());
    }

    public static boolean closed(final BusinessRecord r) {
        return BusinessRecord.STATUS_CLOSED.equals(r.status());
    }

    public static BusinessEmployee employee(final BusinessRecord r, final UUID uuid) {
        if (uuid == null) {
            return null;
        }
        for (final BusinessEmployee entry : r.employees()) {
            if (uuid.equals(entry.uuid())) {
                return entry;
            }
        }
        return null;
    }

    public static boolean employs(final BusinessRecord r, final UUID uuid) {
        return employee(r, uuid) != null;
    }

    public static BusinessPositionRecord position(final BusinessRecord r, final String positionId) {
        if (positionId == null || positionId.isBlank()) {
            return null;
        }
        for (final BusinessPositionRecord position : r.positions()) {
            if (positionId.equals(position.id())) {
                return position;
            }
        }
        return null;
    }

    public static BusinessPositionRecord ceoPosition(final BusinessRecord r) {
        return position(r, BusinessPositionRecord.CEO_POSITION_ID);
    }

    public static EmploymentContractRecord activeContractFor(final BusinessRecord r, final UUID playerUuid) {
        if (playerUuid == null) {
            return null;
        }
        for (final EmploymentContractRecord contract : r.contracts()) {
            if (contract.status() == EmploymentStatus.ACTIVE
                    && playerUuid.equals(contract.employeeUuid())) {
                return contract;
            }
        }
        return null;
    }

    public static EmploymentContractRecord standingContractFor(final BusinessRecord r, final UUID playerUuid) {
        if (playerUuid == null) {
            return null;
        }
        for (final EmploymentContractRecord contract : r.contracts()) {
            if (contract.status() == EmploymentStatus.ACTIVE
                    && !contract.oneTime()
                    && playerUuid.equals(contract.employeeUuid())) {
                return contract;
            }
        }
        return null;
    }

    public static boolean hasActiveBusinessRole(final BusinessRecord r, final UUID playerUuid) {
        return standingContractFor(r, playerUuid) != null;
    }

    public static boolean pendingClose(final BusinessRecord r) {
        return r.closeRequestedBy() != null && !closed(r);
    }

    public static EmploymentContractRecord contract(final BusinessRecord r, final String contractId) {
        if (contractId == null || contractId.isBlank()) {
            return null;
        }
        for (final EmploymentContractRecord entry : r.contracts()) {
            if (contractId.equals(entry.id())) {
                return entry;
            }
        }
        return null;
    }
}