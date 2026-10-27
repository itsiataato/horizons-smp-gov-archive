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
 * Helper for {@link BusinessEmploymentService}: BusinessEmploymentPositionDelete.
 */
public final class BusinessEmploymentPositionDelete {

    private BusinessEmploymentPositionDelete() {
    }

    /** Deletes a non-CEO position that no contract or child position names. */
    public static Outcome deletePosition(
            final GovSavedData data,
            final Actor actor,
            final String rawBusiness,
            final String rawPosition,
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
                    ErrorCode.INVALID_STATE, "The CEO position is the fixed root and cannot be deleted.");
        }
        if (!BusinessEmploymentAccess.canManagePositions(access) || !BusinessEmploymentAccess.belowActor(business, access, existing.id())) {
            return Outcome.refused(
                    ErrorCode.FORBIDDEN,
                    "You may delete only a position strictly below your own.");
        }
        for (final BusinessPositionRecord position : business.positions()) {
            if (existing.id().equals(position.parentId())) {
                return Outcome.refused(
                        ErrorCode.INVALID_STATE,
                        "Position '" + existing.title() + "' still has child position '"
                                + position.title() + "'.");
            }
        }
        for (final EmploymentContractRecord contract : business.contracts()) {
            if (existing.id().equals(contract.positionId())) {
                return Outcome.refused(
                        ErrorCode.INVALID_STATE,
                        "Position '" + existing.title() + "' is named by contract "
                                + contract.id() + " and is kept as record.");
            }
        }
        final List<BusinessPositionRecord> positions = new ArrayList<>(business.positions());
        positions.removeIf(position -> existing.id().equals(position.id()));
        data.putBusiness(business.withPositions(positions));
        HistoryService.appendAll(data,
                List.of(business.ownerUuid(), actor.uuid()),
                HistoryKind.POSITION_DELETED,
                "Position '" + existing.title() + "' was deleted from '" + business.name() + "'.",
                actor.uuid(), actor.name(), now, existing.id());
        return Outcome.ok("deleted position '" + existing.title() + "' from '" + business.name() + "'.");
    }

    /** Moves a non-CEO position under a new parent, refusing a cycle. */

}
