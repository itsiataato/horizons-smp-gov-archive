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
 * Helper for {@link BusinessEmploymentService}: BusinessEmploymentOfferReads.
 */
public final class BusinessEmploymentOfferReads {

    private BusinessEmploymentOfferReads() {
    }

    /** Lists pending offers for a business (owner/manager) or for the viewer as a target. */
    public static OfferPage offerPage(
            final GovSavedData data,
            final Actor actor,
            final String rawBusiness,
            final UUID employeeFilter,
            final int rawPage) {
        final List<OfferRow> all = new ArrayList<>();
        if (data != null && actor != null && actor.uuid() != null) {
            if (rawBusiness == null || rawBusiness.isBlank()) {
                for (final BusinessRecord business : data.businesses().values()) {
                    for (final EmploymentContractRecord contract : business.contracts()) {
                        if (contract.status() == EmploymentStatus.OFFERED
                                && actor.uuid().equals(contract.employeeUuid())
                                && (employeeFilter == null
                                        || employeeFilter.equals(contract.employeeUuid()))) {
                            all.add(new OfferRow(contract, BusinessEmploymentPositionReads.positionTitle(business, contract)));
                        }
                    }
                }
            } else {
                final BusinessRecord business = BusinessService.find(data, rawBusiness);
                if (business != null) {
                    final Access access = BusinessEmploymentAccess.access(business, actor);
                    final boolean allRows = access != null && (access.owner() || access.government()
                            || (access.position() != null
                                    && access.position().has(BusinessPermission.HIRE)));
                    for (final EmploymentContractRecord contract : business.contracts()) {
                        if (contract.status() != EmploymentStatus.OFFERED) {
                            continue;
                        }
                        if (allRows) {
                            if (employeeFilter != null
                                    && !employeeFilter.equals(contract.employeeUuid())) {
                                continue;
                            }
                        } else if (!actor.uuid().equals(contract.employeeUuid())) {
                            continue;
                        }
                        all.add(new OfferRow(contract, BusinessEmploymentPositionReads.positionTitle(business, contract)));
                    }
                }
            }
        }
        all.sort(Comparator
                .comparingLong((OfferRow row) -> row.contract().offeredAt()).reversed()
                .thenComparing(row -> row.contract().id()));
        final int total = all.size();
        final int pages = Math.max(1, (total + BusinessKeys.EMPLOYMENT_PAGE_SIZE - 1)
                / BusinessKeys.EMPLOYMENT_PAGE_SIZE);
        final int page = Math.max(0, Math.min(rawPage, pages - 1));
        final List<OfferRow> rows = new ArrayList<>(BusinessKeys.EMPLOYMENT_PAGE_SIZE);
        final int start = page * BusinessKeys.EMPLOYMENT_PAGE_SIZE;
        for (int i = start; i < Math.min(total, start + BusinessKeys.EMPLOYMENT_PAGE_SIZE); i++) {
            rows.add(all.get(i));
        }
        return new OfferPage(rows, total, page, pages);
    }

    /** Accepts or declines the actor's own pending offer. */

    /** The {@code BIZ_OFFER_LIST} answer. */
    public static Map<String, String> offerFields(
            final GovSavedData data,
            final Actor actor,
            final String rawBusiness,
            final UUID employeeFilter,
            final int rawPage) {
        final OfferPage result = BusinessEmploymentOfferReads.offerPage(data, actor, rawBusiness, employeeFilter, rawPage);
        final Map<String, String> fields = new LinkedHashMap<>();
        final BusinessRecord business = rawBusiness == null || rawBusiness.isBlank()
                ? null : BusinessService.find(data, rawBusiness);
        fields.put(BusinessKeys.KEY_BUSINESS, business == null ? "" : business.id());
        fields.put(BusinessKeys.NAME, business == null ? "" : business.name());
        fields.put(BusinessKeys.OFFER_COUNT, Integer.toString(result.total()));
        fields.put(BusinessKeys.PAGE, Integer.toString(result.page()));
        fields.put(BusinessKeys.PAGES, Integer.toString(result.pages()));
        for (int i = 0; i < result.rows().size(); i++) {
            final OfferRow row = result.rows().get(i);
            final EmploymentContractRecord contract = row.contract();
            final String prefix = BusinessKeys.OFFER_PREFIX + i;
            fields.put(prefix + BusinessKeys.OFFER_ROW_ID, contract.id());
            fields.put(prefix + BusinessKeys.OFFER_ROW_POSITION, contract.positionId());
            fields.put(prefix + BusinessKeys.OFFER_ROW_EMPLOYEE, contract.employeeName());
            fields.put(prefix + BusinessKeys.OFFER_ROW_EMPLOYEE_UUID,
                    contract.employeeUuid().toString());
            fields.put(prefix + BusinessKeys.OFFER_ROW_WAGE,
                    Long.toString(contract.wagePerDay()));
            fields.put(prefix + BusinessKeys.OFFER_ROW_ONE_TIME,
                    Boolean.toString(contract.oneTime()));
            fields.put(prefix + BusinessKeys.OFFER_ROW_STATUS, contract.status().name());
            fields.put(prefix + BusinessKeys.OFFER_ROW_OFFERED_AT,
                    Long.toString(contract.offeredAt()));
        }
        return fields;
    }

    /** The {@code BIZ_CONTRACT_LIST} answer. */

}
