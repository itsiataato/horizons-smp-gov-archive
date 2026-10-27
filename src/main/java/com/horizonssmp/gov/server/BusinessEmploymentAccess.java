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

/**
 * Helper for {@link BusinessEmploymentService}: BusinessEmploymentAccess.
 */
public final class BusinessEmploymentAccess {

    private BusinessEmploymentAccess() {
    }

    /** The actor's relationship to one business, or {@code null} when they have none. */
    public static record Access(
            boolean owner,
            boolean government,
            boolean employee,
            EmploymentContractRecord contract,
            BusinessPositionRecord position) {

        boolean businessSide() {
            return owner || position != null;
        }
    }


    /** True when the position's parent chain reaches the given ancestor, strict. */
    public static boolean isStrictDescendant(
            final BusinessRecord business, final String positionId, final String ancestorId) {
        if (business == null || positionId == null || ancestorId == null
                || positionId.equals(ancestorId)) {
            return false;
        }
        final Set<String> seen = new HashSet<>();
        String current = positionId;
        while (current != null && seen.add(current)) {
            final BusinessPositionRecord position = business.position(current);
            if (position == null) {
                return false;
            }
            final String parent = position.parentId();
            if (parent == null) {
                return false;
            }
            if (ancestorId.equals(parent)) {
                return true;
            }
            current = parent;
        }
        return false;
    }

    /** The depth from the root, or {@code -1} for a damaged/cyclic chain. */

    /** The depth from the root, or {@code -1} for a damaged/cyclic chain. */
    public static int depthOf(final BusinessRecord business, final String positionId) {
        if (business == null || positionId == null) {
            return -1;
        }
        final Set<String> seen = new HashSet<>();
        int depth = 0;
        String current = positionId;
        while (current != null) {
            if (!seen.add(current)) {
                return -1;
            }
            final BusinessPositionRecord position = business.position(current);
            if (position == null) {
                return -1;
            }
            if (position.isCeo()) {
                return position.id().equals(BusinessPositionRecord.CEO_POSITION_ID)
                        ? depth : -1;
            }
            current = position.parentId();
            depth++;
        }
        return -1;
    }

    /** True when the position is the owner's fixed root. */

    /** True when the position is the owner's fixed root. */
    public static boolean isCeoPosition(final BusinessPositionRecord position) {
        return position != null && (position.isCeo()
                || BusinessPositionRecord.CEO_POSITION_ID.equals(position.id()));
    }

    /** The actor's relationship to a business, or {@code null}. */

    /** The actor's relationship to a business, or {@code null}. */
    static Access access(
            final BusinessRecord business, final Actor actor) {
        if (business == null || actor == null || actor.uuid() == null) {
            return null;
        }
        final boolean owner = actor.uuid().equals(business.ownerUuid());
        final boolean government = actor.can(GovCapability.BUSINESS_MANAGE);
        // Prefer the standing contract: a one-time task never grants a position, and it may have
        // been written before the standing office in the stored list.
        final EmploymentContractRecord standing = business.standingContractFor(actor.uuid());
        final EmploymentContractRecord contract = standing != null
                ? standing : business.activeContractFor(actor.uuid());
        final boolean employee = contract != null;
        final BusinessPositionRecord position = contract == null || contract.oneTime()
                ? null : business.position(contract.positionId());
        if (!owner && !government && !employee) {
            return null;
        }
        return new Access(owner, government, employee, contract, position);
    }

    /** True when the viewer may see the business's employment records. */

    /** True when the viewer may see the business's employment records. */
    public static boolean canView(
            final BusinessRecord business, final Actor actor, final Set<GovCapability> capabilities) {
        if (business == null || actor == null || actor.uuid() == null) {
            return false;
        }
        if (actor.uuid().equals(business.ownerUuid())
                || business.hasActiveBusinessRole(actor.uuid())
                || business.activeContractFor(actor.uuid()) != null
                || business.employs(actor.uuid())) {
            return true;
        }
        if (actor.can(GovCapability.BUSINESS_MANAGE)) {
            return true;
        }
        return BusinessService.canViewAll(
                capabilities == null ? actor.capabilities() : capabilities);
    }

    /** True when the actor may create, edit, move or delete positions. */

    /** True when the actor may create, edit, move or delete positions. */
    static boolean canManagePositions(final Access access) {
        return access != null && (access.owner() || access.government()
                || (access.position() != null
                        && access.position().has(BusinessPermission.POSITIONS_MANAGE)));
    }

    /** True when the target position is strictly below the actor's position. */

    /** True when the target position is strictly below the actor's position. */
    static boolean belowActor(
            final BusinessRecord business, final Access access, final String targetPositionId) {
        if (access == null || targetPositionId == null) {
            return false;
        }
        if (access.owner() || access.government()) {
            return !BusinessPositionRecord.CEO_POSITION_ID.equals(targetPositionId);
        }
        return access.position() != null
                && BusinessEmploymentAccess.isStrictDescendant(business, targetPositionId, access.position().id());
    }

    /**
     * True when the actor may hang a position under this parent.
     *
     * <p>The owner and a government manager may use any existing parent, including the CEO root:
     * the root is a parent even though it is not a target an actor may edit. A business-side
     * manager may use their own position or a position strictly below it.
     */

    /**
     * True when the actor may hang a position under this parent.
     *
     * <p>The owner and a government manager may use any existing parent, including the CEO root:
     * the root is a parent even though it is not a target an actor may edit. A business-side
     * manager may use their own position or a position strictly below it.
     */
    static boolean canParentBelow(
            final BusinessRecord business, final Access access, final String parentId) {
        if (access == null || parentId == null || business.position(parentId) == null) {
            return false;
        }
        if (access.owner() || access.government()) {
            return true;
        }
        return access.position() != null
                && (parentId.equals(access.position().id())
                        || BusinessEmploymentAccess.isStrictDescendant(business, parentId, access.position().id()));
    }

    /** True when an actor may fire/promote/demote a contract in this position. */

    /** True when an actor may fire/promote/demote a contract in this position. */
    static boolean canManageEmployee(
            final BusinessRecord business, final Access access,
            final EmploymentContractRecord contract, final BusinessPermission permission) {
        if (access == null || contract == null) {
            return false;
        }
        if (access.owner() || access.government()) {
            return !BusinessPositionRecord.CEO_POSITION_ID.equals(contract.positionId());
        }
        return access.position() != null
                && access.position().has(permission)
                && BusinessEmploymentAccess.isStrictDescendant(business, contract.positionId(), access.position().id());
    }

    /** True when the actor may see every contract in the business, not only their own. */

    /** True when the actor may see every contract in the business, not only their own. */
    static boolean seesAllContracts(final Access access) {
        if (access == null) {
            return false;
        }
        if (access.owner() || access.government()) {
            return true;
        }
        final BusinessPositionRecord position = access.position();
        return position != null && (position.has(BusinessPermission.HIRE)
                || position.has(BusinessPermission.FIRE)
                || position.has(BusinessPermission.PROMOTE)
                || position.has(BusinessPermission.DEMOTE)
                || position.has(BusinessPermission.POSITIONS_MANAGE)
                || position.has(BusinessPermission.PAYROLL));
    }

    /**
     * Creates a position beside the existing tree.
     *
     * <p>An empty parent means "under me": the owner and a government manager get the CEO root,
     * a business-side manager gets their own position. A named parent must be strictly below a
     * business-side manager; the owner and government may name any non-CEO position. The new
     * position can never be the root and never reuses the CEO id.
     */

    /** True when the contract is the owner's fixed CEO contract. */
    static boolean isCeoContract(
            final BusinessRecord business, final EmploymentContractRecord contract) {
        return contract != null
                && BusinessPositionRecord.CEO_POSITION_ID.equals(contract.positionId())
                && business != null && contract.employeeUuid().equals(business.ownerUuid());
    }

}
