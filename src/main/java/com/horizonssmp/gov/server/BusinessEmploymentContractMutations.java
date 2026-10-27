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
 * Helper for {@link BusinessEmploymentService}: BusinessEmploymentContractMutations.
 */
public final class BusinessEmploymentContractMutations {

    private BusinessEmploymentContractMutations() {
    }

    /**
     * Ends a contract: resign, complete, withdraw an offer, or fire.
     *
     * <p>The employee may always end their own contract; a one-time contract completed by the
     * employee becomes COMPLETED, a standing contract becomes RESIGNED. An employer-side actor
     * needs the FIRE permission and a position strictly above the employee. The owner's CEO
     * contract is never ended here: ownership transfer and closure are its only exits.
     */
    public static Outcome endContract(
            final GovSavedData data,
            final Actor actor,
            final String rawBusiness,
            final String rawContract,
            final String decision,
            final String rawReason,
            final long now) {
        final ContractRef ref = BusinessEmploymentContractReads.findContract(data, rawBusiness, rawContract);
        if (ref == null) {
            return Outcome.refused(ErrorCode.BAD_PAYLOAD, "There is no such contract.");
        }
        final BusinessRecord business = ref.business();
        final EmploymentContractRecord contract = ref.contract();
        final boolean self = actor.uuid() != null && actor.uuid().equals(contract.employeeUuid());
        final Access access = BusinessEmploymentAccess.access(business, actor);
        final boolean employer = BusinessEmploymentAccess.canManageEmployee(
                business, access, contract, BusinessPermission.FIRE);
        if (!self && !employer) {
            return Outcome.refused(
                    ErrorCode.FORBIDDEN,
                    "You may end only your own contract or one strictly below your own position.");
        }
        if (BusinessEmploymentAccess.isCeoContract(business, contract)) {
            return Outcome.refused(
                    ErrorCode.INVALID_STATE,
                    "The CEO contract ends only by ownership transfer or business closure.");
        }
        final String mode = decision == null ? "" : decision.trim().toLowerCase(Locale.ROOT);
        final EmploymentStatus next;
        final HistoryKind kind;
        final String reason;
        if (contract.status() == EmploymentStatus.OFFERED) {
            next = EmploymentStatus.DECLINED;
            kind = HistoryKind.CONTRACT_DECLINED;
            reason = BusinessEmploymentIds.sanitizeReason(rawReason, self ? "declined" : "withdrawn");
        } else if (contract.status() != EmploymentStatus.ACTIVE) {
            return Outcome.refused(
                    ErrorCode.INVALID_STATE, "Contract " + contract.id() + " has already ended.");
        } else if ("complete".equals(mode)) {
            if (!contract.oneTime()) {
                return Outcome.refused(
                        ErrorCode.INVALID_STATE, "Only a one-time contract can be completed.");
            }
            next = EmploymentStatus.COMPLETED;
            kind = HistoryKind.CONTRACT_COMPLETED;
            reason = BusinessEmploymentIds.sanitizeReason(rawReason, "completed");
        } else if (self && contract.oneTime()) {
            next = EmploymentStatus.COMPLETED;
            kind = HistoryKind.CONTRACT_COMPLETED;
            reason = BusinessEmploymentIds.sanitizeReason(rawReason, "completed");
        } else if (self) {
            next = EmploymentStatus.RESIGNED;
            kind = HistoryKind.CONTRACT_RESIGNED;
            reason = BusinessEmploymentIds.sanitizeReason(rawReason, "resigned");
        } else {
            next = EmploymentStatus.TERMINATED;
            kind = HistoryKind.CONTRACT_FIRED;
            reason = BusinessEmploymentIds.sanitizeReason(rawReason, "terminated by the employer");
        }
        final EmploymentContractRecord ended = contract.withStatus(next, now, reason);
        data.putBusiness(business.withContracts(BusinessEmploymentTree.replaceContract(business, ended)));
        final String text = switch (next) {
            case RESIGNED -> "Resigned from '" + BusinessEmploymentPositionReads.positionTitle(business, ended) + "' at '"
                    + business.name() + "'.";
            case COMPLETED -> "Completed the one-time task '" + BusinessEmploymentPositionReads.positionTitle(business, ended)
                    + "' at '" + business.name() + "'.";
            case DECLINED -> "Offer at '" + business.name() + "' was "
                    + (self ? "declined." : "withdrawn.");
            default -> "Contract for '" + BusinessEmploymentPositionReads.positionTitle(business, ended) + "' at '"
                    + business.name() + "' was terminated: " + reason;
        };
        HistoryService.appendAll(data,
                List.of(contract.employeeUuid(), business.ownerUuid(), actor.uuid()),
                kind, text, actor.uuid(), actor.name(), now, contract.id());
        return Outcome.ok(text);
    }

    /** Moves an ACTIVE contract to another position. */

    /** Moves an ACTIVE contract to another position. */
    public static Outcome promoteContract(
            final GovSavedData data,
            final Actor actor,
            final String rawBusiness,
            final String rawContract,
            final String rawPosition,
            final long now) {
        final ContractRef ref = BusinessEmploymentContractReads.findContract(data, rawBusiness, rawContract);
        if (ref == null) {
            return Outcome.refused(ErrorCode.BAD_PAYLOAD, "There is no such contract.");
        }
        final BusinessRecord business = ref.business();
        final EmploymentContractRecord contract = ref.contract();
        if (contract.status() != EmploymentStatus.ACTIVE) {
            return Outcome.refused(
                    ErrorCode.INVALID_STATE, "Only an active contract can be moved.");
        }
        if (BusinessEmploymentAccess.isCeoContract(business, contract)) {
            return Outcome.refused(
                    ErrorCode.INVALID_STATE, "The CEO position is fixed and cannot be changed by a move.");
        }
        final BusinessPositionRecord target = business.position(rawPosition);
        if (target == null) {
            return Outcome.refused(ErrorCode.BAD_PAYLOAD, "There is no such position.");
        }
        if (BusinessEmploymentAccess.isCeoPosition(target)) {
            return Outcome.refused(
                    ErrorCode.INVALID_STATE, "A contract cannot be moved into the CEO position.");
        }
        if (target.id().equals(contract.positionId())) {
            return Outcome.refused(
                    ErrorCode.INVALID_STATE, "The contract already fills that position.");
        }
        final Access access = BusinessEmploymentAccess.access(business, actor);
        if (!BusinessEmploymentAccess.canManageEmployee(business, access, contract, BusinessPermission.PROMOTE)
                || !BusinessEmploymentAccess.belowActor(business, access, target.id())) {
            return Outcome.refused(
                    ErrorCode.FORBIDDEN,
                    "You need PROMOTE and a position strictly above both the employee and the target.");
        }
        final EmploymentContractRecord moved = contract.withPosition(target.id());
        data.putBusiness(business.withContracts(BusinessEmploymentTree.replaceContract(business, moved)));
        HistoryService.appendAll(data,
                List.of(contract.employeeUuid(), business.ownerUuid(), actor.uuid()),
                HistoryKind.CONTRACT_PROMOTED,
                "Contract moved from '" + BusinessEmploymentPositionReads.positionTitle(business, contract) + "' to '"
                        + target.title() + "' at '" + business.name() + "'.",
                actor.uuid(), actor.name(), now, contract.id());
        return Outcome.ok(
                "moved " + BusinessService.ownerName(data, contract.employeeUuid()) + " to '"
                        + target.title() + "' at '" + business.name() + "'.");
    }

    /** One page of contracts for a business, or the viewer's own across every business. */

}
