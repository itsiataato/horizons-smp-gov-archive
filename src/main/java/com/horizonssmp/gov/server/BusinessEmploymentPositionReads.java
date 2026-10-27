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
 * Helper for {@link BusinessEmploymentService}: BusinessEmploymentPositionReads.
 */
public final class BusinessEmploymentPositionReads {

    private BusinessEmploymentPositionReads() {
    }

    /** The page of positions in tree order. */
    public static PositionPage positionPage(
            final GovSavedData data,
            final BusinessRecord rawBusiness,
            final Actor actor,
            final Set<GovCapability> capabilities,
            final long now,
            final int rawPage) {
        if (rawBusiness == null) {
            return new PositionPage(List.of(), 0, 0, 1);
        }
        final BusinessRecord business = data == null ? rawBusiness
                : BusinessEmploymentIds.ensureCeoShape(data, rawBusiness, now);
        if (!BusinessEmploymentAccess.canView(business, actor, capabilities)) {
            return new PositionPage(List.of(), 0, 0, 1);
        }
        final Access access = BusinessEmploymentAccess.access(business, actor);
        final List<BusinessPositionRecord> ordered = BusinessEmploymentTree.treeOrder(business);
        final List<PositionRow> all = new ArrayList<>(ordered.size());
        for (final BusinessPositionRecord position : ordered) {
            final boolean editable = !BusinessEmploymentAccess.isCeoPosition(position)
                    && BusinessEmploymentAccess.canManagePositions(access) && BusinessEmploymentAccess.belowActor(business, access, position.id());
            all.add(new PositionRow(position, Math.max(0, BusinessEmploymentAccess.depthOf(business, position.id())), editable));
        }
        final int total = all.size();
        final int pages = Math.max(1, (total + BusinessKeys.EMPLOYMENT_PAGE_SIZE - 1)
                / BusinessKeys.EMPLOYMENT_PAGE_SIZE);
        final int page = Math.max(0, Math.min(rawPage, pages - 1));
        final List<PositionRow> rows = new ArrayList<>(BusinessKeys.EMPLOYMENT_PAGE_SIZE);
        final int start = page * BusinessKeys.EMPLOYMENT_PAGE_SIZE;
        for (int i = start; i < Math.min(total, start + BusinessKeys.EMPLOYMENT_PAGE_SIZE); i++) {
            rows.add(all.get(i));
        }
        return new PositionPage(rows, total, page, pages);
    }

    /** The {@code BIZ_POSITION_LIST} answer. */

    /** The {@code BIZ_POSITION_LIST} answer. */
    public static Map<String, String> positionFields(
            final GovSavedData data,
            final BusinessRecord business,
            final Actor actor,
            final Set<GovCapability> capabilities,
            final long now,
            final int rawPage) {
        final Map<String, String> fields = new LinkedHashMap<>();
        final BusinessRecord shown = business == null ? null
                : (data == null ? business : data.business(business.id()));
        fields.put(BusinessKeys.KEY_BUSINESS, shown == null ? "" : shown.id());
        fields.put(BusinessKeys.NAME, shown == null ? "" : shown.name());
        final PositionPage result = BusinessEmploymentPositionReads.positionPage(
                data, shown, actor, capabilities, now, rawPage);
        fields.put(BusinessKeys.POSITION_COUNT, Integer.toString(result.total()));
        fields.put(BusinessKeys.PAGE, Integer.toString(result.page()));
        fields.put(BusinessKeys.PAGES, Integer.toString(result.pages()));
        for (int i = 0; i < result.rows().size(); i++) {
            final PositionRow row = result.rows().get(i);
            final BusinessPositionRecord position = row.position();
            final String prefix = BusinessKeys.POSITION_PREFIX + i;
            fields.put(prefix + BusinessKeys.POSITION_ROW_ID, position.id());
            fields.put(prefix + BusinessKeys.POSITION_ROW_TITLE, position.title());
            fields.put(prefix + BusinessKeys.POSITION_ROW_PARENT,
                    position.parentId() == null ? "" : position.parentId());
            fields.put(prefix + BusinessKeys.POSITION_ROW_WAGE,
                    Long.toString(position.wagePerDay()));
            fields.put(prefix + BusinessKeys.POSITION_ROW_ORDER,
                    Integer.toString(position.order()));
            fields.put(prefix + BusinessKeys.POSITION_ROW_PERMISSIONS,
                    BusinessPermission.format(position.permissions()));
            fields.put(prefix + BusinessKeys.POSITION_ROW_SYSTEM,
                    Boolean.toString(position.system()));
            fields.put(prefix + BusinessKeys.POSITION_ROW_DEPTH,
                    Integer.toString(row.depth()));
            fields.put(prefix + BusinessKeys.POSITION_ROW_CAN_EDIT,
                    Boolean.toString(row.canEdit()));
        }
        return fields;
    }

    /** The {@code BIZ_OFFER_LIST} answer. */

    /** The position title, or the position id when the seat no longer resolves. */
    public static String positionTitle(
            final BusinessRecord business, final EmploymentContractRecord contract) {
        final BusinessPositionRecord position = business == null || contract == null
                ? null : business.position(contract.positionId());
        return position == null ? (contract == null ? "" : contract.positionId()) : position.title();
    }

    /** True when the contract is the owner's fixed CEO contract. */

    /** A compact map of the actor's position permissions, for an export or a startup assertion. */
    public static Map<String, String> positionPermissionMap(
            final BusinessRecord business, final EmploymentContractRecord contract) {
        final Map<String, String> result = new LinkedHashMap<>();
        final BusinessPositionRecord position = business == null || contract == null
                ? null : business.position(contract.positionId());
        if (position == null) {
            return result;
        }
        for (final BusinessPermission permission : position.permissions()) {
            result.put(permission.id(), "true");
        }
        return result;
    }

}
