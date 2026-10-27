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
 * Helper for {@link BusinessEmploymentService}: BusinessEmploymentPositionCreate.
 */
public final class BusinessEmploymentPositionCreate {

    private BusinessEmploymentPositionCreate() {
    }

    /**
     * Creates a position beside the existing tree.
     *
     * <p>An empty parent means "under me": the owner and a government manager get the CEO root,
     * a business-side manager gets their own position. A named parent must be strictly below a
     * business-side manager; the owner and government may name any non-CEO position. The new
     * position can never be the root and never reuses the CEO id.
     */
    public static Outcome createPosition(
            final GovSavedData data,
            final Actor actor,
            final String rawBusiness,
            final String rawTitle,
            final String rawParent,
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
        if (!BusinessEmploymentAccess.canManagePositions(access)) {
            return Outcome.refused(
                    ErrorCode.FORBIDDEN,
                    "You need the owner's seat or position permission positions.manage to create a"
                            + " position.");
        }
        if (business.closed()) {
            return Outcome.refused(
                    ErrorCode.INVALID_STATE,
                    "Business '" + business.name() + "' is closed; its tree is frozen.");
        }
        if (business.positions().size() >= BusinessKeys.MAX_POSITIONS) {
            return Outcome.refused(
                    ErrorCode.INVALID_STATE,
                    "Business '" + business.name() + "' already has the maximum of "
                            + BusinessKeys.MAX_POSITIONS + " positions.");
        }
        final String title = Sanitizer.singleLine(rawTitle, BusinessPositionRecord.MAX_TITLE_LENGTH);
        if (title.isBlank()) {
            return Outcome.refused(ErrorCode.BAD_PAYLOAD, "A position needs a title.");
        }
        String parentId = rawParent == null ? "" : rawParent.trim();
        if (parentId.isEmpty()) {
            parentId = access.owner() || access.government()
                    ? BusinessPositionRecord.CEO_POSITION_ID
                    : (access.position() == null
                            ? BusinessPositionRecord.CEO_POSITION_ID : access.position().id());
        }
        final BusinessPositionRecord parent = business.position(parentId);
        if (parent == null) {
            return Outcome.refused(ErrorCode.BAD_PAYLOAD, "There is no parent position '" + parentId + "'.");
        }
        if (!BusinessEmploymentAccess.canParentBelow(business, access, parentId)) {
            return Outcome.refused(
                    ErrorCode.FORBIDDEN,
                    "You may create a position only strictly below your own position.");
        }
        final Long wage = BusinessEmploymentIds.parseWage(rawWage, 0L);
        if (wage == null) {
            return Outcome.refused(
                    ErrorCode.BAD_PAYLOAD,
                    "Wage must be a whole number of gold ingots from 0 to "
                            + BusinessKeys.MAX_PAYABLE_WAGE + " per real 24 hours.");
        }
        final Set<BusinessPermission> permissions = BusinessEmploymentIds.parsePermissionsStrict(rawPermissions);
        if (permissions == null) {
            return Outcome.refused(
                    ErrorCode.BAD_PAYLOAD,
                    "Unknown business permission. Use one of: " + BusinessPermission.ids() + ".");
        }
        final Integer order = BusinessEmploymentIds.parseOrder(rawOrder, 0);
        if (order == null) {
            return Outcome.refused(ErrorCode.BAD_PAYLOAD, "Position order must be zero or greater.");
        }
        final BusinessPositionRecord position = new BusinessPositionRecord(
                BusinessEmploymentIds.newPositionId(business), title, parentId, permissions, wage, order, false, now,
                actor.uuid());
        final List<BusinessPositionRecord> positions = new ArrayList<>(business.positions());
        positions.add(position);
        data.putBusiness(business.withPositions(positions));
        HistoryService.appendAll(data,
                List.of(business.ownerUuid(), actor.uuid()),
                HistoryKind.POSITION_CREATED,
                "Position '" + title + "' was created in '" + business.name() + "'.",
                actor.uuid(), actor.name(), now, position.id());
        return Outcome.ok(
                "created position '" + title + "' (" + position.id() + ") in '"
                        + business.name() + "'.");
    }

    /** Edits one non-CEO position. A null field means "leave unchanged". */

}
