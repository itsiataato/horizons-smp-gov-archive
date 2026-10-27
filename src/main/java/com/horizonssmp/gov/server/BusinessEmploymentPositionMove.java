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
 * Helper for {@link BusinessEmploymentService}: BusinessEmploymentPositionMove.
 */
public final class BusinessEmploymentPositionMove {

    private BusinessEmploymentPositionMove() {
    }

    /** Moves a non-CEO position under a new parent, refusing a cycle. */
    public static Outcome movePosition(
            final GovSavedData data,
            final Actor actor,
            final String rawBusiness,
            final String rawPosition,
            final String rawParent,
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
                    ErrorCode.INVALID_STATE, "The CEO position is the fixed root and cannot be moved.");
        }
        if (!BusinessEmploymentAccess.canManagePositions(access) || !BusinessEmploymentAccess.belowActor(business, access, existing.id())) {
            return Outcome.refused(
                    ErrorCode.FORBIDDEN,
                    "You may move only a position strictly below your own.");
        }
        final String parentId = rawParent == null ? "" : rawParent.trim();
        if (parentId.isEmpty()) {
            return Outcome.refused(ErrorCode.BAD_PAYLOAD, "A position needs a parent; only the CEO is a root.");
        }
        final BusinessPositionRecord parent = business.position(parentId);
        if (parent == null) {
            return Outcome.refused(ErrorCode.BAD_PAYLOAD, "There is no parent position '" + parentId + "'.");
        }
        if (parentId.equals(existing.id())) {
            return Outcome.refused(ErrorCode.INVALID_STATE, "A position cannot parent itself.");
        }
        if (BusinessEmploymentAccess.isStrictDescendant(business, parentId, existing.id())) {
            return Outcome.refused(
                    ErrorCode.INVALID_STATE, "That move would make the tree a cycle.");
        }
        if (!BusinessEmploymentAccess.canParentBelow(business, access, parentId)) {
            return Outcome.refused(
                    ErrorCode.FORBIDDEN,
                    "You may move a position only below your own position.");
        }
        if (parentId.equals(existing.parentId())) {
            return Outcome.refused(ErrorCode.INVALID_STATE, "That position is already under that parent.");
        }
        final BusinessPositionRecord moved = new BusinessPositionRecord(
                existing.id(), existing.title(), parentId, existing.permissions(),
                existing.wagePerDay(), existing.order(), false, existing.createdAt(),
                existing.createdBy());
        final List<BusinessPositionRecord> positions = new ArrayList<>(business.positions());
        BusinessEmploymentTree.replacePosition(positions, moved);
        data.putBusiness(business.withPositions(positions));
        HistoryService.appendAll(data,
                List.of(business.ownerUuid(), actor.uuid()),
                HistoryKind.POSITION_MOVED,
                "Position '" + existing.title() + "' was moved under '" + parent.title()
                        + "' in '" + business.name() + "'.",
                actor.uuid(), actor.name(), now, moved.id());
        return Outcome.ok("moved position '" + existing.title() + "' under '" + parent.title() + "'.");
    }

    // ------------------------------------------------------------------- offers

    /**
     * Sends a written offer.
     *
     * <p>The target must not be the owner, must not already hold a standing business role unless
     * the offer is one-time, and must not already have a pending offer in this business. Hiring is
     * business-side authority: the owner or a position holding HIRE. A government
     * {@code business.manage} holder who is not the owner cannot send an offer, because that would
     * replace the owner's consent with the state's.
     */

}
