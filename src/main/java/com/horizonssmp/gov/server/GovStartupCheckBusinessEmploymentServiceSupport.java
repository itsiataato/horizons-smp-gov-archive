package com.horizonssmp.gov.server;

import java.util.UUID;
import com.horizonssmp.gov.common.OpCode;
import com.horizonssmp.gov.common.enums.EmploymentStatus;
import com.horizonssmp.gov.common.records.BusinessPositionRecord;
import com.horizonssmp.gov.common.records.BusinessRecord;
import com.horizonssmp.gov.common.records.EmploymentContractRecord;

final class GovStartupCheckBusinessEmploymentServiceSupport {

    private GovStartupCheckBusinessEmploymentServiceSupport() {
    }

    static boolean isEmploymentRead(final OpCode op) {
        return switch (op) {
            case BIZ_POSITION_LIST, BIZ_OFFER_LIST, BIZ_CONTRACT_LIST,
                    BIZ_COMPLAINT_LIST, HISTORY_LIST -> true;
            default -> false;
        };
    }

    static BusinessPositionRecord findPosition(
            final BusinessRecord business, final String title) {
        if (business == null) {
            return null;
        }
        for (final BusinessPositionRecord position : business.positions()) {
            if (position.title().equals(title)) {
                return position;
            }
        }
        return null;
    }

    static EmploymentContractRecord findOffer(
            final BusinessRecord business, final UUID employee) {
        if (business == null) {
            return null;
        }
        for (final EmploymentContractRecord contract : business.contracts()) {
            if (contract.status() == EmploymentStatus.OFFERED
                    && employee.equals(contract.employeeUuid())) {
                return contract;
            }
        }
        return null;
    }

    static EmploymentContractRecord activeStanding(
            final BusinessRecord business, final UUID employee) {
        if (business == null) {
            return null;
        }
        for (final EmploymentContractRecord contract : business.contracts()) {
            if (contract.status() == EmploymentStatus.ACTIVE
                    && !contract.oneTime()
                    && employee.equals(contract.employeeUuid())) {
                return contract;
            }
        }
        return null;
    }
}
