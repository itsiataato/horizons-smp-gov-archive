package com.horizonssmp.gov.server;

import java.util.List;
import java.util.UUID;

import com.horizonssmp.gov.common.enums.BusinessType;
import com.horizonssmp.gov.common.enums.HistoryKind;
import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.GovCapability;
import com.horizonssmp.gov.common.records.BusinessRecord;
import com.horizonssmp.gov.common.records.PayrollInfo;

/**
 * Helper for {@link BusinessService}: registration and transfer.
 */
public final class BusinessRegistration {

    private BusinessRegistration() {
    }

    public static BusinessService.Outcome register(
            final GovSavedData data,
            final BusinessService.Actor actor,
            final String rawName,
            final String rawType,
            final UUID ownerUuid,
            final String enteredOwnerName,
            final long now) {
        if (data == null || actor == null) {
            return BusinessService.Outcome.refused(ErrorCode.INVALID_STATE, "No actor to register a business.");
        }
        if (!actor.capabilities().contains(GovCapability.BUSINESS_REGISTER)) {
            return BusinessService.Outcome.refused(
                    ErrorCode.FORBIDDEN,
                    "You need " + GovCapability.BUSINESS_REGISTER.id()
                            + " to register a business.");
        }
        final String name = Sanitizer.singleLine(rawName, Sanitizer.MAX_NAME);
        if (name.isBlank()) {
            return BusinessService.Outcome.refused(
                    ErrorCode.BAD_PAYLOAD,
                    "A business needs a name with at least one letter or digit.");
        }
        final BusinessType type = BusinessType.parse(rawType);
        if (type == null) {
            return BusinessService.Outcome.refused(
                    ErrorCode.BAD_PAYLOAD,
                    "Unknown business type. Use one of: " + BusinessType.ids() + ".");
        }
        if (BusinessLookups.byName(data, name) != null) {
            return BusinessService.Outcome.refused(ErrorCode.NAME_TAKEN, "The name '" + name + "' is taken.");
        }
        final UUID owner = ownerUuid == null ? actor.uuid() : ownerUuid;
        if (owner == null) {
            return BusinessService.Outcome.refused(ErrorCode.BAD_TARGET, "No owner to register the business to.");
        }
        final String resolvedOwner = enteredOwnerName == null || enteredOwnerName.isBlank()
                ? BusinessLookups.ownerName(data, owner)
                : Sanitizer.name(enteredOwnerName);
        final BusinessRecord standing = BusinessEmploymentService.standingRole(data, owner);
        if (standing != null) {
            return BusinessService.Outcome.refused(
                    ErrorCode.INVALID_STATE,
                    BusinessLookups.ownerName(data, owner) + " already holds a standing business position at '"
                            + standing.name() + "'. A player may hold only one.");
        }
        data.playerOrCreate(owner, resolvedOwner, now);

        final String id = newBusinessId(data);
        final BusinessRecord created = new BusinessRecord(
                id,
                name,
                owner,
                type.id(),
                BusinessRecord.STATUS_ACTIVE,
                now,
                actor.uuid(),
                "",
                List.of(),
                PayrollInfo.NONE,
                "",
                "",
                0L,
                null);
        final BusinessRecord business = created.withCeoContract(resolvedOwner, now);
        data.putBusiness(business);
        HistoryService.appendAll(data,
                List.of(owner, actor.uuid()),
                HistoryKind.BUSINESS_FOUNDED,
                "Business '" + name + "' (" + id + ") was registered to " + resolvedOwner + ".",
                actor.uuid(), actor.name(), now, id);
        return BusinessService.Outcome.ok(
                "registered business '" + name + "' (" + id + ") to " + BusinessLookups.ownerName(data, owner) + ".",
                id);
    }

    public static BusinessService.Outcome transfer(
            final GovSavedData data,
            final BusinessService.Actor actor,
            final String rawBusiness,
            final UUID newOwnerUuid,
            final String newOwnerName,
            final long now) {
        if (data == null || actor == null || newOwnerUuid == null) {
            return BusinessService.Outcome.refused(ErrorCode.INVALID_STATE, "No owner to transfer the business to.");
        }
        if (!BusinessLookups.canManage(actor)) {
            return BusinessService.Outcome.refused(
                    ErrorCode.FORBIDDEN,
                    "You need " + GovCapability.BUSINESS_MANAGE.id()
                            + " to transfer a business.");
        }
        final BusinessRecord business = BusinessLookups.find(data, rawBusiness);
        if (business == null) {
            return BusinessService.Outcome.refused(ErrorCode.BAD_BUSINESS, BusinessLookups.noSuchBusinessMessage(rawBusiness));
        }
        if (!actor.capabilities().contains(GovCapability.SEASON_ADMIN)) {
            return BusinessService.Outcome.refused(
                    ErrorCode.FORBIDDEN,
                    "You need " + GovCapability.SEASON_ADMIN.id() + " to transfer a business.");
        }
        if (business.closed()) {
            return BusinessService.Outcome.refused(
                    ErrorCode.INVALID_STATE,
                    "Business '" + business.name() + "' is closed and cannot change owner.");
        }
        if (newOwnerUuid.equals(business.ownerUuid())) {
            return BusinessService.Outcome.refused(
                    ErrorCode.INVALID_STATE,
                    business.name() + " is already owned by "
                            + BusinessLookups.ownerName(data, newOwnerUuid) + ".");
        }
        final BusinessEmploymentService.Outcome moved =
                BusinessEmploymentService.transferOwnership(
                        data, new BusinessEmploymentService.Actor(
                                actor.uuid(), actor.name(), actor.capabilities()),
                        business, newOwnerUuid, newOwnerName, now);
        if (!moved.ok()) {
            return BusinessService.Outcome.refused(moved.code(), moved.message());
        }
        return BusinessService.Outcome.ok(moved.message(), business.id());
    }

    private static String newBusinessId(final GovSavedData data) {
        for (int attempt = 0; attempt < 8; attempt++) {
            final String id = BusinessService.BUSINESS_ID_PREFIX
                    + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            if (data.business(id) == null) {
                return id;
            }
        }
        String fallback = BusinessService.BUSINESS_ID_PREFIX + Long.toHexString(System.nanoTime());
        while (data.business(fallback) != null) {
            fallback += "x";
        }
        return fallback;
    }
}