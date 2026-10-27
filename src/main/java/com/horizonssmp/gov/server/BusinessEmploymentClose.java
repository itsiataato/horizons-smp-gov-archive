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
 * Helper for {@link BusinessEmploymentService}: BusinessEmploymentClose.
 */
public final class BusinessEmploymentClose {

    private BusinessEmploymentClose() {
    }

    /** Requests a soft close. The status does not change until a government official decides. */
    public static Outcome requestClose(
            final GovSavedData data,
            final Actor actor,
            final String rawBusiness,
            final String rawReason,
            final long now) {
        final BusinessRecord found = BusinessService.find(data, rawBusiness);
        if (found == null) {
            return Outcome.refused(
                    ErrorCode.BAD_BUSINESS, BusinessService.noSuchBusinessMessage(rawBusiness));
        }
        final BusinessRecord business = BusinessEmploymentIds.prepared(data, found, now);
        final Access access = BusinessEmploymentAccess.access(business, actor);
        if (access == null || (!access.owner() && !access.government())) {
            return Outcome.refused(
                    ErrorCode.FORBIDDEN,
                    "Only the owner or a government business.manage holder may request a close.");
        }
        if (business.closed()) {
            return Outcome.refused(
                    ErrorCode.INVALID_STATE, "Business '" + business.name() + "' is already closed.");
        }
        if (business.pendingClose()) {
            return Outcome.refused(
                    ErrorCode.INVALID_STATE,
                    "A close request for '" + business.name() + "' is already pending.");
        }
        final String reason = Sanitizer.text(rawReason);
        data.putBusiness(business.withCloseRequest(actor.uuid(), now));
        HistoryService.appendAll(data,
                List.of(business.ownerUuid(), actor.uuid()),
                HistoryKind.BUSINESS_CLOSE_REQUESTED,
                "Soft close requested for '" + business.name() + "'"
                        + (reason.isBlank() ? "." : ": " + reason),
                actor.uuid(), actor.name(), now, business.id());
        return Outcome.ok(
                "requested a soft close of '" + business.name() + "'; it keeps trading until the"
                        + " government decides.");
    }

    /** Approves a pending close: status CLOSED, every record retained, open contracts ended. */

    /** Approves a pending close: status CLOSED, every record retained, open contracts ended. */
    public static Outcome approveClose(
            final GovSavedData data,
            final Actor actor,
            final String rawBusiness,
            final String rawReason,
            final long now) {
        if (actor == null || !actor.can(GovCapability.BUSINESS_CLOSE_APPROVE)) {
            return Outcome.refused(
                    ErrorCode.FORBIDDEN,
                    "You need " + GovCapability.BUSINESS_CLOSE_APPROVE.id()
                            + " to approve a business closure.");
        }
        final BusinessRecord found = BusinessService.find(data, rawBusiness);
        if (found == null) {
            return Outcome.refused(
                    ErrorCode.BAD_BUSINESS, BusinessService.noSuchBusinessMessage(rawBusiness));
        }
        final BusinessRecord business = BusinessEmploymentIds.prepared(data, found, now);
        if (business.closed()) {
            return Outcome.refused(
                    ErrorCode.INVALID_STATE, "Business '" + business.name() + "' is already closed.");
        }
        if (!business.pendingClose()) {
            return Outcome.refused(
                    ErrorCode.INVALID_STATE,
                    "Business '" + business.name() + "' has no pending close request.");
        }
        final String reason = Sanitizer.text(rawReason);
        final List<EmploymentContractRecord> contracts = new ArrayList<>();
        final List<UUID> affected = new ArrayList<>();
        for (final EmploymentContractRecord contract : business.contracts()) {
            if (contract.status() == EmploymentStatus.ACTIVE
                    || contract.status() == EmploymentStatus.OFFERED) {
                contracts.add(contract.withStatus(
                        contract.status() == EmploymentStatus.OFFERED
                                ? EmploymentStatus.DECLINED : EmploymentStatus.TERMINATED,
                        now, "business closed"));
                affected.add(contract.employeeUuid());
            } else {
                contracts.add(contract);
            }
        }
        // Keep closeRequestedBy/At on the closed record as evidence of who asked; pendingClose()
        // is false once the status is CLOSED, so nothing reads it as still waiting.
        BusinessRecord closed = business.withContracts(contracts)
                .withStatus(BusinessRecord.STATUS_CLOSED, "", actor.uuid(), now);
        data.putBusiness(closed);
        final List<UUID> recipients = new ArrayList<>(affected);
        recipients.add(business.ownerUuid());
        if (business.closeRequestedBy() != null) {
            recipients.add(business.closeRequestedBy());
        }
        recipients.add(actor.uuid());
        HistoryService.appendAll(data, recipients, HistoryKind.BUSINESS_CLOSED,
                "Business '" + business.name() + "' was closed"
                        + (reason.isBlank() ? "." : ": " + reason),
                actor.uuid(), actor.name(), now, business.id());
        for (final UUID employee : new LinkedHashSet<>(affected)) {
            HistoryService.append(data, employee, HistoryKind.CONTRACT_FIRED,
                    "Contract at '" + business.name() + "' ended because the business closed.",
                    actor.uuid(), actor.name(), now, business.id());
        }
        return Outcome.ok(
                "approved the close of '" + business.name() + "'. Every record is retained.");
    }

    /** Rejects a pending close, leaving the business trading. */

    /** Rejects a pending close, leaving the business trading. */
    public static Outcome rejectClose(
            final GovSavedData data,
            final Actor actor,
            final String rawBusiness,
            final String rawReason,
            final long now) {
        if (actor == null || !actor.can(GovCapability.BUSINESS_CLOSE_APPROVE)) {
            return Outcome.refused(
                    ErrorCode.FORBIDDEN,
                    "You need " + GovCapability.BUSINESS_CLOSE_APPROVE.id()
                            + " to reject a business closure.");
        }
        final BusinessRecord found = BusinessService.find(data, rawBusiness);
        if (found == null) {
            return Outcome.refused(
                    ErrorCode.BAD_BUSINESS, BusinessService.noSuchBusinessMessage(rawBusiness));
        }
        final BusinessRecord business = BusinessEmploymentIds.prepared(data, found, now);
        if (!business.pendingClose()) {
            return Outcome.refused(
                    ErrorCode.INVALID_STATE,
                    "Business '" + business.name() + "' has no pending close request.");
        }
        final String reason = Sanitizer.text(rawReason);
        data.putBusiness(business.withCloseRequest(null, 0L));
        final List<UUID> recipients = new ArrayList<>();
        recipients.add(business.ownerUuid());
        if (business.closeRequestedBy() != null) {
            recipients.add(business.closeRequestedBy());
        }
        recipients.add(actor.uuid());
        HistoryService.appendAll(data, recipients, HistoryKind.BUSINESS_CLOSE_REJECTED,
                "Close request for '" + business.name() + "' was rejected"
                        + (reason.isBlank() ? "." : ": " + reason),
                actor.uuid(), actor.name(), now, business.id());
        return Outcome.ok("rejected the close of '" + business.name() + "'; it continues trading.");
    }

    /**
     * Closes directly through the old registrar action.
     *
     * <p>The old {@code BIZ_REVOKE} opcode remains in the wire vocabulary, but this build gives it
     * the same executive authority as an approval: a government worker with only
     * {@code business.manage} must not be able to bypass the close decision. Contracts are ended
     * and history is written, exactly as {@link #approveClose} does; no record is deleted.
     */

    /**
     * Closes directly through the old registrar action.
     *
     * <p>The old {@code BIZ_REVOKE} opcode remains in the wire vocabulary, but this build gives it
     * the same executive authority as an approval: a government worker with only
     * {@code business.manage} must not be able to bypass the close decision. Contracts are ended
     * and history is written, exactly as {@link #approveClose} does; no record is deleted.
     */
    public static Outcome closeDirect(
            final GovSavedData data,
            final Actor actor,
            final String rawBusiness,
            final String rawReason,
            final long now) {
        if (actor == null || !actor.can(GovCapability.BUSINESS_CLOSE_APPROVE)) {
            return Outcome.refused(
                    ErrorCode.FORBIDDEN,
                    "You need " + GovCapability.BUSINESS_CLOSE_APPROVE.id()
                            + " to close a business registration.");
        }
        final BusinessRecord found = BusinessService.find(data, rawBusiness);
        if (found == null) {
            return Outcome.refused(
                    ErrorCode.BAD_BUSINESS, BusinessService.noSuchBusinessMessage(rawBusiness));
        }
        final BusinessRecord business = BusinessEmploymentIds.prepared(data, found, now);
        if (business.closed()) {
            return Outcome.refused(
                    ErrorCode.INVALID_STATE, "Business '" + business.name() + "' is already closed.");
        }
        final String reason = Sanitizer.text(rawReason);
        final List<EmploymentContractRecord> contracts = new ArrayList<>();
        final List<UUID> affected = new ArrayList<>();
        for (final EmploymentContractRecord contract : business.contracts()) {
            if (contract.status() == EmploymentStatus.ACTIVE
                    || contract.status() == EmploymentStatus.OFFERED) {
                contracts.add(contract.withStatus(
                        contract.status() == EmploymentStatus.OFFERED
                                ? EmploymentStatus.DECLINED : EmploymentStatus.TERMINATED,
                        now, "business closed"));
                affected.add(contract.employeeUuid());
            } else {
                contracts.add(contract);
            }
        }
        final BusinessRecord closed = business.withContracts(contracts)
                .withStatus(BusinessRecord.STATUS_CLOSED, "", actor.uuid(), now);
        data.putBusiness(closed);
        final List<UUID> recipients = new ArrayList<>(affected);
        recipients.add(business.ownerUuid());
        if (business.closeRequestedBy() != null) {
            recipients.add(business.closeRequestedBy());
        }
        recipients.add(actor.uuid());
        HistoryService.appendAll(data, recipients, HistoryKind.BUSINESS_CLOSED,
                "Business '" + business.name() + "' was closed"
                        + (reason.isBlank() ? "." : ": " + reason),
                actor.uuid(), actor.name(), now, business.id());
        return Outcome.ok("closed business '" + business.name() + "'. Every record is retained.");
    }

    /** Renames a business for its owner or a government {@code business.manage} holder. */

}
