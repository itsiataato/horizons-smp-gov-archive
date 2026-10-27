package com.horizonssmp.gov.server;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.horizonssmp.gov.common.BusinessKeys;
import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.GovCapability;
import com.horizonssmp.gov.common.enums.BusinessPermission;
import com.horizonssmp.gov.common.enums.EmploymentStatus;
import com.horizonssmp.gov.common.enums.HistoryKind;
import com.horizonssmp.gov.common.records.BusinessComplaintRecord;
import com.horizonssmp.gov.common.records.BusinessPositionRecord;
import com.horizonssmp.gov.common.records.BusinessRecord;
import com.horizonssmp.gov.common.records.EmploymentContractRecord;
import com.horizonssmp.gov.common.records.PlayerRecord;
import com.horizonssmp.gov.config.GovConfig;

import com.horizonssmp.gov.server.BusinessEmploymentService.*;
import static com.horizonssmp.gov.server.BusinessEmploymentService.REAL_DAY_MS;
import static com.horizonssmp.gov.server.BusinessEmploymentService.MAX_PAYROLL_DAYS_PER_RUN;
import static com.horizonssmp.gov.server.BusinessEmploymentService.COMPLAINT_THRESHOLD;
import static com.horizonssmp.gov.server.BusinessEmploymentService.COMPLAINT_FINE_GOLD;
import static com.horizonssmp.gov.server.BusinessEmploymentService.POSITION_ID_PREFIX;
import static com.horizonssmp.gov.server.BusinessEmploymentService.CONTRACT_ID_PREFIX;
import com.horizonssmp.gov.server.BusinessEmploymentAccess.Access;

/**
 * Helper for {@link BusinessEmploymentService}: BusinessEmploymentPayroll.
 */
public final class BusinessEmploymentPayroll {

    private BusinessEmploymentPayroll() {
    }

    /**
     * Records one payroll run against active contracts.
     *
     * <p>Each contract is paid for every completed real 24-hour day since {@code lastPaidAt} (or
     * {@code startedAt} for its first run), at most {@link #MAX_PAYROLL_DAYS_PER_RUN} days, at its
     * own frozen {@code wagePerDay}. The contract's {@code lastPaidAt} advances by exactly the days
     * paid, so an absent owner can catch up over several runs without a single runaway payment.
     *
     * <p>The gold is physical: this method records a payment that the owner handed over in
     * roleplay. It never credits a balance, and the only totals it stores are the contract's
     * {@code paidTotal} and the display payroll block.
     */
    public static Outcome runPayroll(
            final GovSavedData data,
            final Actor actor,
            final String rawBusiness,
            final long now) {
        // WHY: the legacy physical-payment note must not mutate the contract counters that the
        // economy payroll arrears and complaint restitution trust.
        if (GovConfig.bankEnabled()) {
            return Outcome.refused(
                    ErrorCode.INVALID_STATE,
                    "Banking is enabled; use /gov payroll for business payroll.");
        }
        final BusinessRecord found = BusinessService.find(data, rawBusiness);
        if (found == null) {
            return Outcome.refused(
                    ErrorCode.BAD_BUSINESS, BusinessService.noSuchBusinessMessage(rawBusiness));
        }
        final BusinessRecord business = BusinessEmploymentIds.prepared(data, found, now);
        final Access access = BusinessEmploymentAccess.access(business, actor);
        final boolean payrollAuthority = access != null && (access.owner() || access.government()
                || (access.position() != null && access.position().has(BusinessPermission.PAYROLL)));
        if (!payrollAuthority) {
            return Outcome.refused(
                    ErrorCode.FORBIDDEN,
                    "You need the PAYROLL permission to record payroll.");
        }
        if (business.closed()) {
            return Outcome.refused(
                    ErrorCode.INVALID_STATE,
                    "Business '" + business.name() + "' is closed; its payroll is frozen.");
        }
        final List<EmploymentContractRecord> contracts = new ArrayList<>(business.contracts());
        long total = 0L;
        int paidContracts = 0;
        int paidDays = 0;
        final List<String> paidMessages = new ArrayList<>();
        for (int i = 0; i < contracts.size(); i++) {
            final EmploymentContractRecord contract = contracts.get(i);
            if (contract.status() != EmploymentStatus.ACTIVE) {
                continue;
            }
            if (!access.owner() && !access.government()
                    && (access.position() == null
                            || !BusinessEmploymentAccess.isStrictDescendant(
                                    business, contract.positionId(), access.position().id()))) {
                // A manager may pay only people strictly below them; the owner and the government
                // manager pay the whole roster.
                continue;
            }
            final long base = contract.lastPaidAt() > 0L
                    ? contract.lastPaidAt() : contract.startedAt();
            if (base <= 0L || now <= base) {
                continue;
            }
            final long elapsed = now - base;
            final long days = Math.min(MAX_PAYROLL_DAYS_PER_RUN, elapsed / REAL_DAY_MS);
            if (days <= 0L) {
                continue;
            }
            final long amount = contract.wagePerDay() * days;
            if (amount <= 0L) {
                continue;
            }
            final long paidThrough = base + days * REAL_DAY_MS;
            final EmploymentContractRecord paid = contract.withPayroll(paidThrough, amount);
            contracts.set(i, paid);
            total += amount;
            paidContracts++;
            paidDays += (int) days;
            HistoryService.append(
                    data, contract.employeeUuid(), HistoryKind.PAYROLL_RECORDED,
                    "Payroll: " + amount + " gold ingots for " + days + " day(s) from '"
                            + business.name() + "'.",
                    actor.uuid(), actor.name(), now, contract.id());
            paidMessages.add(BusinessService.ownerName(data, contract.employeeUuid())
                    + " " + amount);
        }
        if (paidContracts == 0) {
            return Outcome.refused(
                    ErrorCode.INVALID_STATE,
                    "No active contract under your authority has a completed real day to pay.");
        }
        BusinessRecord updated = business.withContracts(contracts);
        updated = updated.withPayroll(updated.payroll().withPayment(
                total, actor.uuid(), now,
                "payroll run: " + paidContracts + " contract(s), " + paidDays + " day(s)"));
        data.putBusiness(updated);
        HistoryService.appendAll(data,
                List.of(business.ownerUuid(), actor.uuid()),
                HistoryKind.PAYROLL_RECORDED,
                "Payroll run in '" + business.name() + "': " + total + " gold ingots to "
                        + paidContracts + " contract(s).",
                actor.uuid(), actor.name(), now, business.id());
        return Outcome.ok(
                "recorded payroll in '" + business.name() + "': " + total + " gold ingots to "
                        + paidContracts + " contract(s) over " + paidDays + " day(s) ("
                        + String.join(", ", paidMessages) + ").");
    }

    // --------------------------------------------------------------- complaints

    /**
     * Files one CEO complaint against an active employee.
     *
     * <p>Only the owner may file. The 24-hour cooldown is per owner, business and employee, so a
     * third complaint necessarily represents conduct on three separate days. Every complaint is
     * upheld when filed: this build has no separate adjudication action, and the product rule makes
     * the third one the automatic threshold. On the third, the contract is terminated and the fine
     * service creates a 64-gold AGGRAVATED fine whose restitution equals the wages actually paid
     * on that contract, credited to the CEO.
     */

}
