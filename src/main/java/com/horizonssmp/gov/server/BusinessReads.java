package com.horizonssmp.gov.server;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.horizonssmp.gov.common.BusinessKeys;
import com.horizonssmp.gov.common.GovCapability;
import com.horizonssmp.gov.common.records.BusinessEmployee;
import com.horizonssmp.gov.common.records.PayrollInfo;
import com.horizonssmp.gov.common.records.BusinessRecord;

/**
 * Helper for {@link BusinessService}: read models and field maps.
 */
public final class BusinessReads {

    private BusinessReads() {
    }

    public static String canonicalFilter(final String raw) {
        if (raw == null) {
            return BusinessKeys.FILTER_ALL;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case BusinessKeys.FILTER_ACTIVE -> BusinessKeys.FILTER_ACTIVE;
            case BusinessKeys.FILTER_SUSPENDED -> BusinessKeys.FILTER_SUSPENDED;
            case BusinessKeys.FILTER_CLOSED -> BusinessKeys.FILTER_CLOSED;
            default -> BusinessKeys.FILTER_ALL;
        };
    }

    public static BusinessService.Page page(
            final GovSavedData data,
            final UUID viewerUuid,
            final Set<GovCapability> capabilities,
            final String rawFilter,
            final UUID ownerFilter,
            final int rawPage) {
        final String filter = canonicalFilter(rawFilter);
        final boolean viewAll = BusinessLookups.canViewAll(capabilities);
        final List<BusinessService.BusinessRow> all = new ArrayList<>();
        if (data != null) {
            for (final BusinessRecord business : data.businesses().values()) {
                if (viewAll) {
                    if (ownerFilter != null && !ownerFilter.equals(business.ownerUuid())) {
                        continue;
                    }
                } else if (viewerUuid == null
                        || (!viewerUuid.equals(business.ownerUuid())
                                && !business.employs(viewerUuid)
                                && business.activeContractFor(viewerUuid) == null)) {
                    continue;
                }
                if (!matchesFilter(business, filter)) {
                    continue;
                }
                all.add(new BusinessService.BusinessRow(
                        business, BusinessLookups.ownerName(data, business.ownerUuid()),
                        BusinessLookups.jobName(data, business.linkedJobId())));
            }
        }
        all.sort(Comparator
                .comparing((BusinessService.BusinessRow row) -> row.business().name().toLowerCase(Locale.ROOT))
                .thenComparing(row -> row.business().id()));
        final int total = all.size();
        final int pages = Math.max(1, (total + BusinessKeys.PAGE_SIZE - 1) / BusinessKeys.PAGE_SIZE);
        final int page = Math.max(0, Math.min(rawPage, pages - 1));
        final List<BusinessService.BusinessRow> rows = new ArrayList<>(BusinessKeys.PAGE_SIZE);
        final int start = page * BusinessKeys.PAGE_SIZE;
        for (int i = start; i < Math.min(total, start + BusinessKeys.PAGE_SIZE); i++) {
            rows.add(all.get(i));
        }
        return new BusinessService.Page(rows, total, page, pages);
    }

    private static boolean matchesFilter(final BusinessRecord business, final String filter) {
        return switch (filter) {
            case BusinessKeys.FILTER_ACTIVE -> business.active();
            case BusinessKeys.FILTER_SUSPENDED -> business.suspended();
            case BusinessKeys.FILTER_CLOSED -> business.closed();
            default -> true;
        };
    }

    public static Map<String, String> listFields(
            final GovSavedData data,
            final UUID viewerUuid,
            final String viewerName,
            final Set<GovCapability> capabilities,
            final String rawFilter,
            final UUID ownerFilter,
            final int page,
            final List<String> onlineNames) {
        final BusinessService.Page result = page(data, viewerUuid, capabilities, rawFilter, ownerFilter, page);
        final Map<String, String> fields = new LinkedHashMap<>();
        fields.put(BusinessKeys.CAN_REGISTER,
                Boolean.toString(capabilities != null
                        && capabilities.contains(GovCapability.BUSINESS_REGISTER)));
        fields.put(BusinessKeys.CAN_MANAGE,
                Boolean.toString(capabilities != null
                        && capabilities.contains(GovCapability.BUSINESS_MANAGE)));
        fields.put(BusinessKeys.CAN_PAYROLL,
                Boolean.toString(capabilities != null
                        && capabilities.contains(GovCapability.PAYROLL_RUN)));
        fields.put(BusinessKeys.VIEWER_UUID, viewerUuid == null ? "" : viewerUuid.toString());
        fields.put(BusinessKeys.VIEWER_NAME, Sanitizer.name(viewerName));
        fields.put(BusinessKeys.PAYROLL_CAP_GOLD, Long.toString(BusinessService.MAX_PAYROLL_GOLD));
        fields.put(BusinessKeys.TOTAL, Integer.toString(result.total()));
        fields.put(BusinessKeys.PAGE, Integer.toString(result.page()));
        fields.put(BusinessKeys.PAGES, Integer.toString(result.pages()));
        fields.put(BusinessKeys.FILTER, canonicalFilter(rawFilter));

        for (int i = 0; i < result.rows().size(); i++) {
            final BusinessService.BusinessRow row = result.rows().get(i);
            final BusinessRecord business = row.business();
            final String prefix = BusinessKeys.ROW_PREFIX + i;
            fields.put(prefix + BusinessKeys.ROW_ID, business.id());
            fields.put(prefix + BusinessKeys.ROW_NAME, business.name());
            fields.put(prefix + BusinessKeys.ROW_OWNER, row.ownerName());
            fields.put(prefix + BusinessKeys.ROW_OWNER_UUID,
                    business.ownerUuid() == null ? "" : business.ownerUuid().toString());
            fields.put(prefix + BusinessKeys.ROW_TYPE, business.type());
            fields.put(prefix + BusinessKeys.ROW_STATUS, business.status());
            fields.put(prefix + BusinessKeys.ROW_EMPLOYEES,
                    Integer.toString(business.employees().size()));
            fields.put(prefix + BusinessKeys.ROW_JOB, business.linkedJobId());
            fields.put(prefix + BusinessKeys.ROW_JOB_NAME, row.jobName());
        }

        final List<String> names = onlineNames == null ? List.of() : onlineNames;
        fields.put(BusinessKeys.ONLINE_COUNT, Integer.toString(names.size()));
        for (int i = 0; i < names.size() && i < BusinessKeys.MAX_LISTED_ONLINE; i++) {
            fields.put(BusinessKeys.ONLINE_PREFIX + i, Sanitizer.name(names.get(i)));
        }
        return fields;
    }

    public static Map<String, String> detailFields(
            final GovSavedData data,
            final BusinessRecord business,
            final Set<GovCapability> capabilities,
            final UUID viewerUuid,
            final Set<UUID> onlineUuids) {
        if (business == null) {
            return Map.of();
        }
        final BusinessRecord shown = data == null || data.business(business.id()) == null
                ? business : data.business(business.id());
        final Map<String, String> fields = new LinkedHashMap<>();
        fields.put(BusinessKeys.ID, shown.id());
        fields.put(BusinessKeys.NAME, shown.name());
        fields.put(BusinessKeys.OWNER, BusinessLookups.ownerName(data, shown.ownerUuid()));
        fields.put(BusinessKeys.OWNER_UUID,
                shown.ownerUuid() == null ? "" : shown.ownerUuid().toString());
        fields.put(BusinessKeys.TYPE, shown.type());
        fields.put(BusinessKeys.STATUS, shown.status());
        fields.put(BusinessKeys.CREATED_AT, Long.toString(shown.createdAt()));
        fields.put(BusinessKeys.CREATED_BY, BusinessLookups.ownerName(data, shown.createdBy()));
        fields.put(BusinessKeys.JOB, shown.linkedJobId());
        fields.put(BusinessKeys.JOB_NAME, BusinessLookups.jobName(data, shown.linkedJobId()));
        fields.put(BusinessKeys.SUSPEND_REASON, shown.suspendReason());
        fields.put(BusinessKeys.SUSPENDED_AT, Long.toString(shown.suspendedAt()));
        fields.put(BusinessKeys.SUSPENDED_BY, BusinessLookups.ownerName(data, shown.suspendedBy()));
        fields.put(BusinessKeys.NOTES, shown.notes());
        fields.put(BusinessKeys.POSITION_COUNT, Integer.toString(shown.positions().size()));
        fields.put(BusinessKeys.CONTRACT_COUNT, Integer.toString(shown.contracts().size()));
        fields.put(BusinessKeys.CLOSE_PENDING, Boolean.toString(shown.pendingClose()));
        fields.put(BusinessKeys.CLOSE_REQUESTED_BY,
                shown.closeRequestedBy() == null ? "" : BusinessLookups.ownerName(data, shown.closeRequestedBy()));
        fields.put(BusinessKeys.CLOSE_REQUESTED_BY_UUID,
                shown.closeRequestedBy() == null ? "" : shown.closeRequestedBy().toString());
        fields.put(BusinessKeys.CLOSE_REQUESTED_AT, Long.toString(shown.closeRequestedAt()));
        fields.put(BusinessKeys.CLOSE_REASON, "");
        fields.put(BusinessKeys.CLOSE_DECISION, "");

        final boolean manage = capabilities != null
                && capabilities.contains(GovCapability.BUSINESS_MANAGE);
        final boolean owner = viewerUuid != null && viewerUuid.equals(shown.ownerUuid());
        fields.put(BusinessKeys.CAN_MANAGE_THIS, Boolean.toString(manage));
        fields.put(BusinessKeys.CAN_EDIT_THIS, Boolean.toString(owner));
        fields.put(BusinessKeys.CAN_RECORD_PAYROLL_THIS, Boolean.toString(manage));
        fields.put(BusinessKeys.VIEWER_UUID, viewerUuid == null ? "" : viewerUuid.toString());

        final List<BusinessEmployee> employees = shown.employees();
        fields.put(BusinessKeys.EMPLOYEE_COUNT, Integer.toString(employees.size()));
        final Set<UUID> online = onlineUuids == null ? Set.of() : onlineUuids;
        for (int i = 0; i < employees.size() && i < BusinessKeys.MAX_LISTED_EMPLOYEES; i++) {
            final BusinessEmployee entry = employees.get(i);
            final String prefix = BusinessKeys.EMPLOYEE_PREFIX + i;
            fields.put(prefix + BusinessKeys.EMPLOYEE_NAME, BusinessLookups.ownerName(data, entry.uuid()));
            fields.put(prefix + BusinessKeys.EMPLOYEE_UUID, entry.uuid().toString());
            fields.put(prefix + BusinessKeys.EMPLOYEE_ROLE, entry.role());
            fields.put(prefix + BusinessKeys.EMPLOYEE_SINCE, Long.toString(entry.since()));
            fields.put(prefix + BusinessKeys.EMPLOYEE_ONLINE,
                    Boolean.toString(online.contains(entry.uuid())));
        }

        fields.putAll(payrollFields(data, shown.payroll()));
        return fields;
    }

    public static Map<String, String> payrollInfoFields(
            final GovSavedData data,
            final BusinessRecord business,
            final Set<GovCapability> capabilities) {
        if (business == null) {
            return Map.of();
        }
        final BusinessRecord shown = data == null || data.business(business.id()) == null
                ? business : data.business(business.id());
        final Map<String, String> fields = new LinkedHashMap<>();
        fields.put(BusinessKeys.ID, shown.id());
        fields.put(BusinessKeys.NAME, shown.name());
        fields.put(BusinessKeys.STATUS, shown.status());
        fields.put(BusinessKeys.PAYROLL_CAP_GOLD, Long.toString(BusinessService.MAX_PAYROLL_GOLD));
        fields.put(BusinessKeys.CAN_MANAGE_THIS,
                Boolean.toString(capabilities != null
                        && capabilities.contains(GovCapability.BUSINESS_MANAGE)));
        fields.putAll(payrollFields(data, shown.payroll()));
        return fields;
    }

    private static Map<String, String> payrollFields(
            final GovSavedData data, final PayrollInfo payroll) {
        final PayrollInfo value = payroll == null ? PayrollInfo.NONE : payroll;
        final Map<String, String> fields = new LinkedHashMap<>();
        fields.put(BusinessKeys.PAYROLL_RATE, Long.toString(value.ratePerDayGold()));
        fields.put(BusinessKeys.PAYROLL_SCHEDULE, value.scheduleNote());
        fields.put(BusinessKeys.PAYROLL_LAST_AT, Long.toString(value.lastPaidAt()));
        fields.put(BusinessKeys.PAYROLL_LAST_BY,
                BusinessLookups.ownerName(data, value.lastPaidBy()));
        fields.put(BusinessKeys.PAYROLL_LAST_AMOUNT, Long.toString(value.lastPaidAmountGold()));
        fields.put(BusinessKeys.PAYROLL_LAST_NOTE, value.lastPaymentNote());
        fields.put(BusinessKeys.PAYROLL_COUNT, Integer.toString(value.paymentsRecorded()));
        return fields;
    }
}