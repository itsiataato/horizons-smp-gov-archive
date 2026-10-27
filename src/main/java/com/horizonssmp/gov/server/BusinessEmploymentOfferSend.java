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
 * Helper for {@link BusinessEmploymentService}: BusinessEmploymentOfferSend.
 */
public final class BusinessEmploymentOfferSend {

    private BusinessEmploymentOfferSend() {
    }

    /**
     * Sends a written offer.
     *
     * <p>The target must not be the owner, must not already hold a standing business role unless
     * the offer is one-time, and must not already have a pending offer in this business. Hiring is
     * business-side authority: the owner or a position holding HIRE. A government
     * {@code business.manage} holder who is not the owner cannot send an offer, because that would
     * replace the owner's consent with the state's.
     */
    public static Outcome sendOffer(
            final GovSavedData data,
            final Actor actor,
            final String rawBusiness,
            final UUID employeeUuid,
            final String employeeName,
            final String rawPosition,
            final String rawWage,
            final String rawOneTime,
            final long now) {
        final BusinessRecord found = BusinessService.find(data, rawBusiness);
        if (found == null) {
            return Outcome.refused(
                    ErrorCode.BAD_BUSINESS, BusinessService.noSuchBusinessMessage(rawBusiness));
        }
        final BusinessRecord business = BusinessEmploymentIds.prepared(data, found, now);
        final Access access = BusinessEmploymentAccess.access(business, actor);
        if (access == null) {
            return Outcome.refused(
                    ErrorCode.FORBIDDEN, "Only the business owner or its staff may hire.");
        }
        if (access.government() && !access.owner()
                && !(access.position() != null && access.position().has(BusinessPermission.HIRE))) {
            return Outcome.refused(
                    ErrorCode.FORBIDDEN,
                    "Government business.manage may inspect and manage the registry, but hiring"
                            + " needs the owner's consent through a HIRE position.");
        }
        if (!access.owner() && (access.position() == null
                || !access.position().has(BusinessPermission.HIRE))) {
            return Outcome.refused(
                    ErrorCode.FORBIDDEN,
                    "You need the HIRE permission to send an offer.");
        }
        if (!business.active()) {
            return Outcome.refused(
                    ErrorCode.INVALID_STATE,
                    "Business '" + business.name() + "' is not active; it cannot hire.");
        }
        if (employeeUuid == null) {
            return Outcome.refused(ErrorCode.BAD_TARGET, "No person to offer a position to.");
        }
        if (employeeUuid.equals(business.ownerUuid())) {
            return Outcome.refused(
                    ErrorCode.INVALID_STATE, "The owner already holds the CEO position.");
        }
        if (employeeUuid.equals(actor.uuid())) {
            return Outcome.refused(ErrorCode.INVALID_STATE, "You cannot send an offer to yourself.");
        }
        final BusinessPositionRecord position = business.position(rawPosition);
        if (position == null) {
            return Outcome.refused(ErrorCode.BAD_PAYLOAD, "There is no such position.");
        }
        if (BusinessEmploymentAccess.isCeoPosition(position)) {
            return Outcome.refused(
                    ErrorCode.INVALID_STATE, "The CEO position belongs to the owner and cannot be offered.");
        }
        if (!access.owner() && !BusinessEmploymentAccess.belowActor(business, access, position.id())) {
            return Outcome.refused(
                    ErrorCode.FORBIDDEN,
                    "You may offer only a position strictly below your own.");
        }
        final Boolean parsedOneTime = BusinessEmploymentIds.parseOneTime(rawOneTime);
        if (parsedOneTime == null) {
            return Outcome.refused(
                    ErrorCode.BAD_PAYLOAD, "The one-time flag must be 'true' or 'false'.");
        }
        final boolean oneTime = parsedOneTime;
        if (!oneTime) {
            final BusinessRecord other = BusinessEmploymentIds.standingRole(data, employeeUuid);
            if (other != null) {
                return Outcome.refused(
                        ErrorCode.INVALID_STATE,
                        BusinessService.ownerName(data, employeeUuid)
                                + " already holds a standing business position at '"
                                + other.name() + "'. A player may hold only one.");
            }
        }
        for (final EmploymentContractRecord contract : business.contracts()) {
            if (contract.status() == EmploymentStatus.OFFERED
                    && employeeUuid.equals(contract.employeeUuid())) {
                return Outcome.refused(
                        ErrorCode.INVALID_STATE,
                        "There is already a pending offer for "
                                + BusinessService.ownerName(data, employeeUuid)
                                + " in this business.");
            }
        }
        if (business.contracts().size() >= BusinessKeys.MAX_CONTRACTS) {
            return Outcome.refused(
                    ErrorCode.INVALID_STATE,
                    "Business '" + business.name() + "' already has the maximum of "
                            + BusinessKeys.MAX_CONTRACTS + " contracts.");
        }
        final Long wage = BusinessEmploymentIds.parseWage(rawWage, position.wagePerDay());
        if (wage == null) {
            return Outcome.refused(
                    ErrorCode.BAD_PAYLOAD,
                    "Wage must be a whole number of gold ingots from 0 to "
                            + BusinessKeys.MAX_PAYABLE_WAGE + " per real 24 hours.");
        }
        final String employee = employeeName == null || employeeName.isBlank()
                ? BusinessService.ownerName(data, employeeUuid) : Sanitizer.name(employeeName);
        data.playerOrCreate(employeeUuid, employee, now);
        final EmploymentContractRecord offer = new EmploymentContractRecord(
                BusinessEmploymentIds.newContractId(business), position.id(), employeeUuid, employee,
                business.ownerUuid(), wage, oneTime, EmploymentStatus.OFFERED,
                now, 0L, 0L, 0L, 0L, "");
        final List<EmploymentContractRecord> contracts = new ArrayList<>(business.contracts());
        contracts.add(offer);
        data.putBusiness(business.withContracts(contracts));
        HistoryService.appendAll(data,
                List.of(employeeUuid, business.ownerUuid(), actor.uuid()),
                HistoryKind.CONTRACT_OFFERED,
                "Offer of position '" + position.title() + "' at '" + business.name()
                        + "' for " + wage + " gold ingots per real 24 hours.",
                actor.uuid(), actor.name(), now, offer.id());
        return Outcome.ok(
                "offered position '" + position.title() + "' at '" + business.name() + "' to "
                        + employee + " for " + wage + " gold ingots per real 24 hours.");
    }

    /** Lists pending offers for a business (owner/manager) or for the viewer as a target. */

}
