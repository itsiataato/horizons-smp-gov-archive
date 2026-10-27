package com.horizonssmp.gov.server.economy;

import com.horizonssmp.gov.common.records.EmploymentContractRecord;

/**
 * One employee payout built before any store is touched.
 */
record PayrollPayment(
        String accountId,
        long gross,
        long tax,
        EmploymentContractRecord contract) {

    long net() {
        return gross - tax;
    }
}