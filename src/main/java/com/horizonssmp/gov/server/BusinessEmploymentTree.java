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
 * Helper for {@link BusinessEmploymentService}: BusinessEmploymentTree.
 */
public final class BusinessEmploymentTree {

    private BusinessEmploymentTree() {
    }

    static void replacePosition(
            final List<BusinessPositionRecord> positions, final BusinessPositionRecord edited) {
        for (int i = 0; i < positions.size(); i++) {
            if (positions.get(i).id().equals(edited.id())) {
                positions.set(i, edited);
                return;
            }
        }
    }

    static List<EmploymentContractRecord> replaceContract(
            final BusinessRecord business, final EmploymentContractRecord edited) {
        final List<EmploymentContractRecord> contracts = new ArrayList<>(business.contracts());
        for (int i = 0; i < contracts.size(); i++) {
            if (contracts.get(i).id().equals(edited.id())) {
                contracts.set(i, edited);
                return contracts;
            }
        }
        contracts.add(edited);
        return contracts;
    }

    /** Tree order: CEO first, then children by display order and title; orphans last. */

    /** Tree order: CEO first, then children by display order and title; orphans last. */
    static List<BusinessPositionRecord> treeOrder(final BusinessRecord business) {
        final List<BusinessPositionRecord> result = new ArrayList<>();
        final Map<String, List<BusinessPositionRecord>> children = new LinkedHashMap<>();
        final Set<String> seen = new LinkedHashSet<>();
        for (final BusinessPositionRecord position : business.positions()) {
            final String parent = position.parentId() == null ? "" : position.parentId();
            children.computeIfAbsent(parent, key -> new ArrayList<>()).add(position);
        }
        for (final List<BusinessPositionRecord> list : children.values()) {
            list.sort(Comparator
                    .comparingInt(BusinessPositionRecord::order)
                    .thenComparing(position -> position.title().toLowerCase(Locale.ROOT))
                    .thenComparing(BusinessPositionRecord::id));
        }
        final BusinessPositionRecord root = business.ceoPosition();
        if (root != null) {
            result.add(root);
            seen.add(root.id());
        }
        BusinessEmploymentTree.walk(BusinessPositionRecord.CEO_POSITION_ID, children, seen, result);
        for (final BusinessPositionRecord position : business.positions()) {
            if (!seen.contains(position.id())) {
                result.add(position);
            }
        }
        return result;
    }

    static void walk(
            final String id,
            final Map<String, List<BusinessPositionRecord>> children,
            final Set<String> seen,
            final List<BusinessPositionRecord> out) {
        final List<BusinessPositionRecord> direct = children.get(id);
        if (direct == null) {
            return;
        }
        for (final BusinessPositionRecord position : direct) {
            if (position.id().equals(id) || !seen.add(position.id())) {
                continue;
            }
            out.add(position);
            walk(position.id(), children, seen, out);
        }
    }

}
