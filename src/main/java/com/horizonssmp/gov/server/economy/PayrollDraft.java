package com.horizonssmp.gov.server.economy;

import java.util.List;

/**
 * A validated roster and the two skip counters reported to the caller.
 */
record PayrollDraft(List<PayrollPayment> payments, int skippedSelf, int skippedTax) {
}