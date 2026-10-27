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
 * Helper for {@link BusinessEmploymentService}: BusinessEmploymentIds.
 */
public final class BusinessEmploymentIds {

    private BusinessEmploymentIds() {
    }

    /**
     * The active standing business role a player holds, or {@code null}.
     *
     * <p>Used by registration and ownership transfer to enforce one business position per player.
     * The scan is across every business and accepts only an ACTIVE non one-time contract. A legacy
     * staff-list entry grants no role and is deliberately not counted; it is a display label, not
     * an authority.
     */
    public static BusinessRecord standingRole(final GovSavedData data, final UUID playerUuid) {
        if (data == null || playerUuid == null) {
            return null;
        }
        for (final BusinessRecord business : data.businesses().values()) {
            if (business.hasActiveBusinessRole(playerUuid)) {
                return business;
            }
        }
        return null;
    }

    /** True when the player may take a new standing business role. */

    /** True when the player may take a new standing business role. */
    public static boolean canTakeStandingRole(final GovSavedData data, final UUID playerUuid) {
        return BusinessEmploymentIds.standingRole(data, playerUuid) == null;
    }

    /**
     * Adds the canonical CEO root and contract to a record that lacks them.
     *
     * <p>New registration goes through {@link BusinessService#register}, which calls
     * {@code withCeoContract} directly. This method is the tolerant repair for a business written
     * before the employment model: the owner is still the owner and needs a seat to delegate from.
     * It refuses to add a contract that would exceed the hard cap; the root position may still be
     * added because it is the tree's anchor.
     */

    /**
     * Adds the canonical CEO root and contract to a record that lacks them.
     *
     * <p>New registration goes through {@link BusinessService#register}, which calls
     * {@code withCeoContract} directly. This method is the tolerant repair for a business written
     * before the employment model: the owner is still the owner and needs a seat to delegate from.
     * It refuses to add a contract that would exceed the hard cap; the root position may still be
     * added because it is the tree's anchor.
     */
    static BusinessRecord ensureCeoShape(
            final GovSavedData data, final BusinessRecord business, final long now) {
        if (business == null) {
            return null;
        }
        BusinessRecord next = business;
        final String ownerName = BusinessService.ownerName(data, business.ownerUuid());
        if (next.ceoPosition() == null && next.positions().size() < BusinessKeys.MAX_POSITIONS) {
            final List<BusinessPositionRecord> positions = new ArrayList<>();
            positions.add(BusinessPositionRecord.ceo(now, business.ownerUuid()));
            positions.addAll(next.positions());
            next = next.withPositions(positions);
        }
        // A contract must name a real seat. If a damaged 64-position record has no root, the cap
        // stops us adding one, and adding a CEO contract anyway would create an orphan.
        if (next.ceoPosition() != null
                && !next.hasActiveBusinessRole(next.ownerUuid())
                && next.contracts().size() < BusinessKeys.MAX_CONTRACTS) {
            next = next.withCeoContract(ownerName, now);
        }
        return next;
    }

    /** Persists the CEO repair, if it changed anything. */

    /** Persists the CEO repair, if it changed anything. */
    static BusinessRecord prepared(
            final GovSavedData data, final BusinessRecord business, final long now) {
        final BusinessRecord next = BusinessEmploymentIds.ensureCeoShape(data, business, now);
        if (next != null && !next.equals(business)) {
            data.putBusiness(next);
        }
        return next;
    }

    // -------------------------------------------------------------- position tree

    /** True when the position's parent chain reaches the given ancestor, strict. */

    static String newPositionId(final BusinessRecord business) {
        for (int attempt = 0; attempt < 8; attempt++) {
            final String id = POSITION_ID_PREFIX
                    + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            if (business.position(id) == null) {
                return id;
            }
        }
        String fallback = POSITION_ID_PREFIX + Long.toHexString(System.nanoTime());
        while (business.position(fallback) != null) {
            fallback += "x";
        }
        return fallback;
    }

    static String newContractId(final BusinessRecord business) {
        for (int attempt = 0; attempt < 8; attempt++) {
            final String id = CONTRACT_ID_PREFIX
                    + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
            if (business.contract(id) == null) {
                return id;
            }
        }
        String fallback = CONTRACT_ID_PREFIX + Long.toHexString(System.nanoTime());
        while (business.contract(fallback) != null) {
            fallback += "x";
        }
        return fallback;
    }

    static String newComplaintId(final BusinessRecord business) {
        for (int attempt = 0; attempt < 8; attempt++) {
            final String id = "cmp-"
                    + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            for (final BusinessComplaintRecord complaint : business.complaints()) {
                if (id.equals(complaint.id())) {
                    return newComplaintId(business);
                }
            }
            return id;
        }
        String fallback = "cmp-" + Long.toHexString(System.nanoTime());
        while (true) {
            boolean taken = false;
            for (final BusinessComplaintRecord complaint : business.complaints()) {
                if (fallback.equals(complaint.id())) {
                    taken = true;
                    break;
                }
            }
            if (!taken) {
                return fallback;
            }
            fallback += "x";
        }
    }

    /** Parses a non-negative wage, with an empty value as the fallback. */

    /** Parses a non-negative wage, with an empty value as the fallback. */
    public static Long parseWage(final String raw, final long fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            final long value = Long.parseLong(raw.trim());
            return value < 0L || value > BusinessKeys.MAX_PAYABLE_WAGE ? null : value;
        } catch (final NumberFormatException e) {
            return null;
        }
    }

    /** Parses a non-negative display order, with an empty value as the fallback. */

    /** Parses a non-negative display order, with an empty value as the fallback. */
    public static Integer parseOrder(final String raw, final int fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            final int value = Integer.parseInt(raw.trim());
            return value < 0 ? null : value;
        } catch (final NumberFormatException e) {
            return null;
        }
    }

    /** Strict permission CSV parsing: an unknown id refuses the whole request. */

    /** Strict permission CSV parsing: an unknown id refuses the whole request. */
    public static Set<BusinessPermission> parsePermissionsStrict(final String raw) {
        final Set<BusinessPermission> result = EnumSet.noneOf(BusinessPermission.class);
        if (raw == null || raw.isBlank()) {
            return result;
        }
        for (final String part : raw.split(",")) {
            if (part.isBlank()) {
                continue;
            }
            final BusinessPermission permission = BusinessPermission.byId(part).orElse(null);
            if (permission == null) {
                return null;
            }
            result.add(permission);
        }
        return result;
    }

    /** Parses the one-time flag strictly; blank is false, anything else unreadable is null. */

    /** Parses the one-time flag strictly; blank is false, anything else unreadable is null. */
    public static Boolean parseOneTime(final String raw) {
        if (raw == null || raw.isBlank()) {
            return Boolean.FALSE;
        }
        final String value = raw.trim();
        if ("true".equalsIgnoreCase(value)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(value)) {
            return Boolean.FALSE;
        }
        return null;
    }

    static String sanitizeReason(final String raw, final String fallback) {
        final String reason = Sanitizer.text(raw);
        return reason.isBlank() ? fallback : reason;
    }

    /** True when the player has any active contract in any business; used by tests. */

}
