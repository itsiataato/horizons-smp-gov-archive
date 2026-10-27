package com.horizonssmp.gov.server;

import java.util.List;
import java.util.UUID;
import com.horizonssmp.gov.common.records.BusinessComplaintRecord;
import com.horizonssmp.gov.common.records.BusinessPositionRecord;
import com.horizonssmp.gov.common.records.EmploymentContractRecord;

final class GovStartupCheckBusinessEmploymentModelContext {
    final List<String> failures;
    int checks;
    final long now = 1_910_000_000_000L;
    final UUID owner = new UUID(0L, 9500L);
    final UUID worker = new UUID(0L, 9501L);
    final UUID stranger = new UUID(0L, 9502L);

    BusinessPositionRecord ceo;
    BusinessPositionRecord manager;
    EmploymentContractRecord contract;
    BusinessComplaintRecord complaint;

    GovStartupCheckBusinessEmploymentModelContext(final List<String> failures) {
        this.failures = failures;
    }
}
