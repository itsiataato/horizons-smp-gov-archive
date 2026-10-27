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
 * Helper for {@link BusinessEmploymentService}: BusinessEmploymentContractReads.
 */
public final class BusinessEmploymentContractReads {

    private BusinessEmploymentContractReads() {
    }

    /** One page of contracts for a business, or the viewer's own across every business. */
    public static ContractPage contractPage(
            final GovSavedData data,
            final Actor actor,
            final String rawBusiness,
            final int rawPage) {
        final List<ContractRow> all = new ArrayList<>();
        if (data != null && actor != null && actor.uuid() != null) {
            if (rawBusiness == null || rawBusiness.isBlank()) {
                for (final BusinessRecord business : data.businesses().values()) {
                    for (final EmploymentContractRecord contract : business.contracts()) {
                        if (actor.uuid().equals(contract.employeeUuid())) {
                            all.add(new ContractRow(contract, BusinessEmploymentPositionReads.positionTitle(business, contract)));
                        }
                    }
                }
            } else {
                final BusinessRecord business = BusinessService.find(data, rawBusiness);
                if (business != null) {
                    final Access access = BusinessEmploymentAccess.access(business, actor);
                    final boolean allRows = BusinessEmploymentAccess.seesAllContracts(access);
                    for (final EmploymentContractRecord contract : business.contracts()) {
                        if (allRows || actor.uuid().equals(contract.employeeUuid())) {
                            all.add(new ContractRow(contract, BusinessEmploymentPositionReads.positionTitle(business, contract)));
                        }
                    }
                }
            }
        }
        all.sort(Comparator
                .comparing((ContractRow row) -> row.contract().status() == EmploymentStatus.ACTIVE
                        ? 0 : 1)
                .thenComparing(Comparator.comparingLong(
                        (ContractRow row) -> row.contract().offeredAt()).reversed())
                .thenComparing(row -> row.contract().id()));
        final int total = all.size();
        final int pages = Math.max(1, (total + BusinessKeys.EMPLOYMENT_PAGE_SIZE - 1)
                / BusinessKeys.EMPLOYMENT_PAGE_SIZE);
        final int page = Math.max(0, Math.min(rawPage, pages - 1));
        final List<ContractRow> rows = new ArrayList<>(BusinessKeys.EMPLOYMENT_PAGE_SIZE);
        final int start = page * BusinessKeys.EMPLOYMENT_PAGE_SIZE;
        for (int i = start; i < Math.min(total, start + BusinessKeys.EMPLOYMENT_PAGE_SIZE); i++) {
            rows.add(all.get(i));
        }
        return new ContractPage(rows, total, page, pages);
    }

    // ------------------------------------------------------------------ payroll

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

    /** The {@code BIZ_CONTRACT_LIST} answer. */
    public static Map<String, String> contractFields(
            final GovSavedData data,
            final Actor actor,
            final String rawBusiness,
            final int rawPage) {
        final ContractPage result = BusinessEmploymentContractReads.contractPage(data, actor, rawBusiness, rawPage);
        final Map<String, String> fields = new LinkedHashMap<>();
        final BusinessRecord business = rawBusiness == null || rawBusiness.isBlank()
                ? null : BusinessService.find(data, rawBusiness);
        fields.put(BusinessKeys.KEY_BUSINESS, business == null ? "" : business.id());
        fields.put(BusinessKeys.NAME, business == null ? "" : business.name());
        fields.put(BusinessKeys.CONTRACT_COUNT, Integer.toString(result.total()));
        fields.put(BusinessKeys.PAGE, Integer.toString(result.page()));
        fields.put(BusinessKeys.PAGES, Integer.toString(result.pages()));
        for (int i = 0; i < result.rows().size(); i++) {
            final ContractRow row = result.rows().get(i);
            final EmploymentContractRecord contract = row.contract();
            final String prefix = BusinessKeys.CONTRACT_PREFIX + i;
            fields.put(prefix + BusinessKeys.CONTRACT_ROW_ID, contract.id());
            fields.put(prefix + BusinessKeys.CONTRACT_ROW_POSITION, contract.positionId());
            fields.put(prefix + BusinessKeys.CONTRACT_ROW_EMPLOYEE, contract.employeeName());
            fields.put(prefix + BusinessKeys.CONTRACT_ROW_EMPLOYEE_UUID,
                    contract.employeeUuid().toString());
            fields.put(prefix + BusinessKeys.CONTRACT_ROW_WAGE,
                    Long.toString(contract.wagePerDay()));
            fields.put(prefix + BusinessKeys.CONTRACT_ROW_ONE_TIME,
                    Boolean.toString(contract.oneTime()));
            fields.put(prefix + BusinessKeys.CONTRACT_ROW_STATUS, contract.status().name());
            fields.put(prefix + BusinessKeys.CONTRACT_ROW_STARTED_AT,
                    Long.toString(contract.startedAt()));
            fields.put(prefix + BusinessKeys.CONTRACT_ROW_PAID_TOTAL,
                    Long.toString(contract.paidTotal()));
        }
        return fields;
    }

    /** The {@code BIZ_COMPLAINT_LIST} answer. */

    /** The contract a payload names, restricted to one business when the payload supplied one. */
    public static ContractRef findContract(
            final GovSavedData data, final String rawBusiness, final String rawContract) {
        if (data == null || rawContract == null || rawContract.isBlank()) {
            return null;
        }
        final String id = rawContract.trim();
        if (rawBusiness != null && !rawBusiness.isBlank()) {
            final BusinessRecord business = BusinessService.find(data, rawBusiness);
            final EmploymentContractRecord contract = business == null ? null : business.contract(id);
            return contract == null ? null : new ContractRef(business, contract);
        }
        for (final BusinessRecord business : data.businesses().values()) {
            final EmploymentContractRecord contract = business.contract(id);
            if (contract != null) {
                return new ContractRef(business, contract);
            }
        }
        return null;
    }

    /** The position title, or the position id when the seat no longer resolves. */

    /** True when the player has any active contract in any business; used by tests. */
    public static boolean hasAnyActiveContract(
            final GovSavedData data, final UUID playerUuid) {
        if (data == null || playerUuid == null) {
            return false;
        }
        for (final BusinessRecord business : data.businesses().values()) {
            if (business.activeContractFor(playerUuid) != null) {
                return true;
            }
        }
        return false;
    }

    /** A compact map of the actor's position permissions, for an export or a startup assertion. */

}
