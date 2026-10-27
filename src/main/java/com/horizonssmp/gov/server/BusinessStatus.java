package com.horizonssmp.gov.server;

import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.GovCapability;
import com.horizonssmp.gov.common.records.BusinessRecord;

/**
 * Helper for {@link BusinessService}: suspension, closure and deletion.
 */
public final class BusinessStatus {

    private BusinessStatus() {
    }

    public static BusinessService.Outcome suspend(
            final GovSavedData data,
            final BusinessService.Actor actor,
            final String rawBusiness,
            final String rawReason,
            final long now) {
        if (data == null || actor == null) {
            return BusinessService.Outcome.refused(ErrorCode.INVALID_STATE, "No business to suspend.");
        }
        if (!BusinessLookups.canManage(actor)) {
            return BusinessService.Outcome.refused(
                    ErrorCode.FORBIDDEN,
                    "You need " + GovCapability.BUSINESS_MANAGE.id() + " to suspend a business.");
        }
        final String reason = Sanitizer.text(rawReason);
        if (reason.isBlank()) {
            return BusinessService.Outcome.refused(
                    ErrorCode.BAD_PAYLOAD,
                    "A suspension needs a recorded reason of at most " + Sanitizer.MAX_TEXT
                            + " characters.");
        }
        final BusinessRecord business = BusinessLookups.find(data, rawBusiness);
        if (business == null) {
            return BusinessService.Outcome.refused(ErrorCode.BAD_BUSINESS, BusinessLookups.noSuchBusinessMessage(rawBusiness));
        }
        if (business.closed()) {
            return BusinessService.Outcome.refused(
                    ErrorCode.INVALID_STATE,
                    "Business '" + business.name() + "' is closed; it cannot be suspended.");
        }
        if (business.suspended()) {
            return BusinessService.Outcome.refused(
                    ErrorCode.INVALID_STATE,
                    "Business '" + business.name() + "' is already suspended.");
        }
        data.putBusiness(business.withStatus(
                BusinessRecord.STATUS_SUSPENDED, reason, actor.uuid(), now));
        return BusinessService.Outcome.ok("suspended business '" + business.name() + "': " + reason, business.id());
    }

    public static BusinessService.Outcome close(
            final GovSavedData data, final BusinessService.Actor actor, final String rawBusiness, final long now) {
        if (data == null || actor == null) {
            return BusinessService.Outcome.refused(ErrorCode.INVALID_STATE, "No business to close.");
        }
        if (!BusinessLookups.canManage(actor)) {
            return BusinessService.Outcome.refused(
                    ErrorCode.FORBIDDEN,
                    "You need " + GovCapability.BUSINESS_MANAGE.id() + " to close a business.");
        }
        final BusinessRecord business = BusinessLookups.find(data, rawBusiness);
        if (business == null) {
            return BusinessService.Outcome.refused(ErrorCode.BAD_BUSINESS, BusinessLookups.noSuchBusinessMessage(rawBusiness));
        }
        final BusinessEmploymentService.Outcome closed =
                BusinessEmploymentService.closeDirect(
                        data, new BusinessEmploymentService.Actor(
                                actor.uuid(), actor.name(), actor.capabilities()),
                        business.id(), "", now);
        if (!closed.ok()) {
            return BusinessService.Outcome.refused(closed.code(), closed.message());
        }
        return BusinessService.Outcome.ok(closed.message(), business.id());
    }

    public static BusinessService.Outcome delete(
            final GovSavedData data, final BusinessService.Actor actor, final String rawBusiness, final long now) {
        if (data == null || actor == null) {
            return BusinessService.Outcome.refused(ErrorCode.INVALID_STATE, "No business to delete.");
        }
        if (!BusinessLookups.canManage(actor)) {
            return BusinessService.Outcome.refused(
                    ErrorCode.FORBIDDEN,
                    "You need " + GovCapability.BUSINESS_MANAGE.id() + " to delete a business.");
        }
        final BusinessRecord business = BusinessLookups.find(data, rawBusiness);
        if (business == null) {
            return BusinessService.Outcome.refused(ErrorCode.BAD_BUSINESS, BusinessLookups.noSuchBusinessMessage(rawBusiness));
        }
        data.removeBusiness(business.id());
        return BusinessService.Outcome.ok("deleted business '" + business.name() + "'.", business.id());
    }
}