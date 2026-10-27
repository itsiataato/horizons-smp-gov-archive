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
 * Helper for {@link BusinessEmploymentService}: BusinessEmploymentRenameTransfer.
 */
public final class BusinessEmploymentRenameTransfer {

    private BusinessEmploymentRenameTransfer() {
    }

    /** Renames a business for its owner or a government {@code business.manage} holder. */
    public static Outcome rename(
            final GovSavedData data,
            final Actor actor,
            final String rawBusiness,
            final String rawName,
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
                    "Only the owner or a government business.manage holder may rename this business.");
        }
        if (business.closed()) {
            return Outcome.refused(
                    ErrorCode.INVALID_STATE, "Business '" + business.name() + "' is closed.");
        }
        final String name = Sanitizer.singleLine(rawName, Sanitizer.MAX_NAME);
        if (name.isBlank()) {
            return Outcome.refused(ErrorCode.BAD_PAYLOAD, "A business needs a name.");
        }
        if (name.equalsIgnoreCase(business.name())) {
            return Outcome.refused(
                    ErrorCode.INVALID_STATE, "Business is already named '" + business.name() + "'.");
        }
        final BusinessRecord clash = BusinessService.byName(data, name);
        if (clash != null && !clash.id().equals(business.id())) {
            return Outcome.refused(ErrorCode.NAME_TAKEN, "The name '" + name + "' is taken.");
        }
        data.putBusiness(business.withName(name));
        HistoryService.appendAll(data,
                List.of(business.ownerUuid(), actor.uuid()),
                HistoryKind.BUSINESS_RENAMED,
                "'" + business.name() + "' was renamed to '" + name + "'.",
                actor.uuid(), actor.name(), now, business.id());
        return Outcome.ok("renamed business '" + business.name() + "' to '" + name + "'.");
    }

    // ---------------------------------------------------------------- transfer

    /**
     * Transfers ownership: the old owner's CEO contract ends, the new owner receives the one CEO
     * contract, and the position tree is preserved.
     *
     * <p>The new owner must not already hold a standing role in another business. A standing
     * contract they hold in this business is ended first, because a transfer must never leave two
     * CEOs or two standing positions for one person. One-time contracts are work and are left
     * alone; they do not occupy the slot.
     */

    /**
     * Transfers ownership: the old owner's CEO contract ends, the new owner receives the one CEO
     * contract, and the position tree is preserved.
     *
     * <p>The new owner must not already hold a standing role in another business. A standing
     * contract they hold in this business is ended first, because a transfer must never leave two
     * CEOs or two standing positions for one person. One-time contracts are work and are left
     * alone; they do not occupy the slot.
     */
    public static Outcome transferOwnership(
            final GovSavedData data,
            final Actor actor,
            final BusinessRecord found,
            final UUID newOwnerUuid,
            final String newOwnerName,
            final long now) {
        if (data == null || actor == null || found == null || newOwnerUuid == null) {
            return Outcome.refused(ErrorCode.INVALID_STATE, "No owner to transfer the business to.");
        }
        if (!actor.can(GovCapability.BUSINESS_MANAGE)) {
            return Outcome.refused(
                    ErrorCode.FORBIDDEN,
                    "You need " + GovCapability.BUSINESS_MANAGE.id()
                            + " to transfer a business.");
        }
        // WHY: business.manage is a registrar's hand; only the executive office may move a CEO
        // root and its whole position tree without the old owner.
        if (!actor.can(GovCapability.SEASON_ADMIN)) {
            return Outcome.refused(
                    ErrorCode.FORBIDDEN,
                    "You need " + GovCapability.SEASON_ADMIN.id() + " to transfer a business.");
        }
        final BusinessRecord business = BusinessEmploymentIds.prepared(data, found, now);
        if (business.closed()) {
            return Outcome.refused(
                    ErrorCode.INVALID_STATE,
                    "Business '" + business.name() + "' is closed and cannot change owner.");
        }
        if (newOwnerUuid.equals(business.ownerUuid())) {
            return Outcome.refused(
                    ErrorCode.INVALID_STATE,
                    business.name() + " is already owned by "
                            + BusinessService.ownerName(data, newOwnerUuid) + ".");
        }
        for (final BusinessRecord other : data.businesses().values()) {
            if (other.id().equals(business.id())) {
                continue;
            }
            if (other.hasActiveBusinessRole(newOwnerUuid)) {
                return Outcome.refused(
                        ErrorCode.INVALID_STATE,
                        BusinessService.ownerName(data, newOwnerUuid)
                                + " already holds a standing business position at '"
                                + other.name() + "'. A player may hold only one.");
            }
        }
        final UUID oldOwner = business.ownerUuid();
        final List<EmploymentContractRecord> contracts = new ArrayList<>();
        boolean endedOldCeo = false;
        boolean endedNewRole = false;
        for (final EmploymentContractRecord contract : business.contracts()) {
            final boolean open = contract.status() == EmploymentStatus.ACTIVE;
            final boolean ceo = BusinessPositionRecord.CEO_POSITION_ID.equals(
                    contract.positionId());
            final boolean old = oldOwner != null && oldOwner.equals(contract.employeeUuid());
            final boolean incoming = newOwnerUuid.equals(contract.employeeUuid());
            if (open && (ceo || old || (incoming && !contract.oneTime()))) {
                contracts.add(contract.withStatus(
                        EmploymentStatus.TERMINATED, now, "business ownership transferred"));
                endedOldCeo |= old && ceo;
                endedNewRole |= incoming && !ceo;
            } else {
                contracts.add(contract);
            }
        }
        if (contracts.size() >= BusinessKeys.MAX_CONTRACTS) {
            return Outcome.refused(
                    ErrorCode.INVALID_STATE,
                    "Business '" + business.name() + "' is at the contract cap; ownership cannot"
                            + " add the new CEO contract.");
        }
        final String newName = newOwnerName == null || newOwnerName.isBlank()
                ? BusinessService.ownerName(data, newOwnerUuid) : Sanitizer.name(newOwnerName);
        data.playerOrCreate(newOwnerUuid, newName, now);
        BusinessRecord updated = business.withContracts(contracts)
                .withOwner(newOwnerUuid)
                .withCeoContract(newName, now);
        data.putBusiness(updated);
        final List<UUID> recipients = new ArrayList<>();
        recipients.add(oldOwner);
        recipients.add(newOwnerUuid);
        recipients.add(actor.uuid());
        HistoryService.appendAll(data, recipients, HistoryKind.BUSINESS_TRANSFERRED,
                "Business '" + business.name() + "' was transferred from "
                        + BusinessService.ownerName(data, oldOwner) + " to " + newName + ".",
                actor.uuid(), actor.name(), now, business.id());
        if (endedOldCeo) {
            HistoryService.append(data, oldOwner, HistoryKind.CONTRACT_TRANSFERRED,
                    "CEO contract at '" + business.name() + "' ended on ownership transfer.",
                    actor.uuid(), actor.name(), now, business.id());
        }
        HistoryService.append(data, newOwnerUuid, HistoryKind.CONTRACT_TRANSFERRED,
                "Became CEO of '" + business.name() + "' on ownership transfer.",
                actor.uuid(), actor.name(), now, business.id());
        if (endedNewRole) {
            HistoryService.append(data, newOwnerUuid, HistoryKind.CONTRACT_TRANSFERRED,
                    "Previous standing contract at '" + business.name()
                            + "' ended when its owner became CEO.",
                    actor.uuid(), actor.name(), now, business.id());
        }
        return Outcome.ok(
                "transferred business '" + business.name() + "' to " + newName + ".");
    }

    // -------------------------------------------------------------------- reads

    /** The page of positions in tree order. */

}
