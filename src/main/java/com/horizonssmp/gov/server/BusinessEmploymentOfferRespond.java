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
 * Helper for {@link BusinessEmploymentService}: BusinessEmploymentOfferRespond.
 */
public final class BusinessEmploymentOfferRespond {

    private BusinessEmploymentOfferRespond() {
    }

    /** Accepts or declines the actor's own pending offer. */
    public static Outcome respondOffer(
            final GovSavedData data,
            final Actor actor,
            final String rawBusiness,
            final String rawOffer,
            final boolean accept,
            final long now) {
        final ContractRef ref = BusinessEmploymentContractReads.findContract(data, rawBusiness, rawOffer);
        if (ref == null) {
            return Outcome.refused(ErrorCode.BAD_PAYLOAD, "There is no such offer.");
        }
        final EmploymentContractRecord contract = ref.contract();
        if (!actor.uuid().equals(contract.employeeUuid())) {
            return Outcome.refused(ErrorCode.FORBIDDEN, "That offer is not addressed to you.");
        }
        if (contract.status() != EmploymentStatus.OFFERED) {
            return Outcome.refused(
                    ErrorCode.INVALID_STATE, "Offer " + contract.id() + " is no longer pending.");
        }
        final BusinessRecord business = ref.business();
        if (!accept) {
            final List<EmploymentContractRecord> contracts = BusinessEmploymentTree.replaceContract(
                    business, contract.withStatus(EmploymentStatus.DECLINED, now, "declined"));
            data.putBusiness(business.withContracts(contracts));
            HistoryService.appendAll(data,
                    List.of(actor.uuid(), business.ownerUuid()),
                    HistoryKind.CONTRACT_DECLINED,
                    "Declined the offer of '" + BusinessEmploymentPositionReads.positionTitle(business, contract)
                            + "' at '" + business.name() + "'.",
                    actor.uuid(), actor.name(), now, contract.id());
            return Outcome.ok("declined the offer from '" + business.name() + "'.");
        }
        if (!business.active()) {
            return Outcome.refused(
                    ErrorCode.INVALID_STATE,
                    "Business '" + business.name() + "' is not active; the offer cannot be accepted.");
        }
        if (business.position(contract.positionId()) == null) {
            return Outcome.refused(
                    ErrorCode.INVALID_STATE, "The offered position no longer exists.");
        }
        if (!contract.oneTime()) {
            final BusinessRecord other = BusinessEmploymentIds.standingRole(data, actor.uuid());
            if (other != null) {
                return Outcome.refused(
                        ErrorCode.INVALID_STATE,
                        "You already hold a standing business position at '" + other.name()
                                + "'. A player may hold only one.");
            }
        }
        final EmploymentContractRecord active = contract.withStatus(
                EmploymentStatus.ACTIVE, now, "");
        final List<EmploymentContractRecord> contracts = BusinessEmploymentTree.replaceContract(business, active);
        data.putBusiness(business.withContracts(contracts));
        HistoryService.appendAll(data,
                List.of(actor.uuid(), business.ownerUuid()),
                HistoryKind.CONTRACT_ACCEPTED,
                "Accepted position '" + BusinessEmploymentPositionReads.positionTitle(business, active) + "' at '"
                        + business.name() + "' for " + active.wagePerDay()
                        + " gold ingots per real 24 hours.",
                actor.uuid(), actor.name(), now, contract.id());
        return Outcome.ok("accepted the offer from '" + business.name() + "'.");
    }

    // ---------------------------------------------------------------- contracts

    /**
     * Ends a contract: resign, complete, withdraw an offer, or fire.
     *
     * <p>The employee may always end their own contract; a one-time contract completed by the
     * employee becomes COMPLETED, a standing contract becomes RESIGNED. An employer-side actor
     * needs the FIRE permission and a position strictly above the employee. The owner's CEO
     * contract is never ended here: ownership transfer and closure are its only exits.
     */

}
