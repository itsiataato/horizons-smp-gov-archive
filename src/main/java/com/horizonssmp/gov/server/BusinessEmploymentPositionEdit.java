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
 * Helper for {@link BusinessEmploymentService}: BusinessEmploymentPositionEdit.
 */
public final class BusinessEmploymentPositionEdit {

    private BusinessEmploymentPositionEdit() {
    }

    /** Edits one non-CEO position. A null field means "leave unchanged". */
    public static Outcome editPosition(
            final GovSavedData data,
            final Actor actor,
            final String rawBusiness,
            final String rawPosition,
            final String rawTitle,
            final String rawWage,
            final String rawPermissions,
            final String rawOrder,
            final long now) {
        final BusinessRecord found = BusinessService.find(data, rawBusiness);
        if (found == null) {
            return Outcome.refused(
                    ErrorCode.BAD_BUSINESS, BusinessService.noSuchBusinessMessage(rawBusiness));
        }
        final BusinessRecord business = BusinessEmploymentIds.prepared(data, found, now);
        final Access access = BusinessEmploymentAccess.access(business, actor);
        final BusinessPositionRecord existing = business.position(rawPosition);
        if (existing == null) {
            return Outcome.refused(ErrorCode.BAD_PAYLOAD, "There is no such position.");
        }
        if (BusinessEmploymentAccess.isCeoPosition(existing)) {
            return Outcome.refused(
                    ErrorCode.INVALID_STATE, "The CEO position is the fixed root and cannot be edited.");
        }
        if (!BusinessEmploymentAccess.canManagePositions(access) || !BusinessEmploymentAccess.belowActor(business, access, existing.id())) {
            return Outcome.refused(
                    ErrorCode.FORBIDDEN,
                    "You may edit only a position strictly below your own.");
        }
        String title = existing.title();
        if (rawTitle != null) {
            title = Sanitizer.singleLine(rawTitle, BusinessPositionRecord.MAX_TITLE_LENGTH);
            if (title.isBlank()) {
                return Outcome.refused(ErrorCode.BAD_PAYLOAD, "A position needs a title.");
            }
        }
        long wage = existing.wagePerDay();
        if (rawWage != null) {
            final Long parsed = BusinessEmploymentIds.parseWage(rawWage, 0L);
            if (parsed == null) {
                return Outcome.refused(
                        ErrorCode.BAD_PAYLOAD,
                        "Wage must be a whole number of gold ingots from 0 to "
                                + BusinessKeys.MAX_PAYABLE_WAGE + " per real 24 hours.");
            }
            wage = parsed;
        }
        Set<BusinessPermission> permissions = existing.permissions();
        if (rawPermissions != null) {
            final Set<BusinessPermission> parsed = BusinessEmploymentIds.parsePermissionsStrict(rawPermissions);
            if (parsed == null) {
                return Outcome.refused(
                        ErrorCode.BAD_PAYLOAD,
                        "Unknown business permission. Use one of: " + BusinessPermission.ids() + ".");
            }
            permissions = parsed;
        }
        int order = existing.order();
        if (rawOrder != null) {
            final Integer parsed = BusinessEmploymentIds.parseOrder(rawOrder, existing.order());
            if (parsed == null) {
                return Outcome.refused(ErrorCode.BAD_PAYLOAD, "Position order must be zero or greater.");
            }
            order = parsed;
        }
        final BusinessPositionRecord edited = new BusinessPositionRecord(
                existing.id(), title, existing.parentId(), permissions, wage, order, false,
                existing.createdAt(), existing.createdBy());
        if (edited.equals(existing)) {
            return Outcome.refused(ErrorCode.INVALID_STATE, "That position is already in that state.");
        }
        final List<BusinessPositionRecord> positions = new ArrayList<>(business.positions());
        BusinessEmploymentTree.replacePosition(positions, edited);
        data.putBusiness(business.withPositions(positions));
        HistoryService.appendAll(data,
                List.of(business.ownerUuid(), actor.uuid()),
                HistoryKind.POSITION_EDITED,
                "Position '" + title + "' was edited in '" + business.name() + "'.",
                actor.uuid(), actor.name(), now, edited.id());
        return Outcome.ok("edited position '" + title + "' in '" + business.name() + "'.");
    }

    /** Deletes a non-CEO position that no contract or child position names. */

}
