package com.horizonssmp.gov.server;

import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.GovCapability;
import com.horizonssmp.gov.common.records.BusinessRecord;
import com.horizonssmp.gov.config.GovConfig;

/**
 * Helper for {@link BusinessService}: payroll notes and recorded payments.
 */
public final class BusinessPayroll {

    private BusinessPayroll() {
    }

    public static BusinessService.Outcome setPayroll(
            final GovSavedData data,
            final BusinessService.Actor actor,
            final String rawBusiness,
            final String rawRate,
            final String rawSchedule) {
        if (data == null || actor == null) {
            return BusinessService.Outcome.refused(ErrorCode.INVALID_STATE, "No business to set payroll on.");
        }
        if (GovConfig.bankEnabled()) {
            return BusinessService.Outcome.refused(
                    ErrorCode.INVALID_STATE,
                    "Banking is enabled; use /gov payroll for business payroll.");
        }
        final BusinessRecord business = BusinessLookups.find(data, rawBusiness);
        if (business == null) {
            return BusinessService.Outcome.refused(ErrorCode.BAD_BUSINESS, BusinessLookups.noSuchBusinessMessage(rawBusiness));
        }
        if (!BusinessLookups.isOwner(business, actor)) {
            return BusinessService.Outcome.refused(
                    ErrorCode.FORBIDDEN,
                    "Only the owner of this business may set its payroll.");
        }
        if (business.closed()) {
            return BusinessService.Outcome.refused(
                    ErrorCode.INVALID_STATE,
                    "Business '" + business.name() + "' is closed; its payroll is frozen.");
        }
        final long rate = parseAmount(rawRate);
        if (rate < 0L || rate > BusinessService.MAX_PAYROLL_GOLD) {
            return BusinessService.Outcome.refused(
                    ErrorCode.BAD_PAYLOAD,
                    "Payroll rate must be a whole number of gold ingots from 0 to "
                            + BusinessService.MAX_PAYROLL_GOLD + " per in-game day.");
        }
        final String schedule = Sanitizer.text(rawSchedule);
        data.putBusiness(business.withPayroll(
                business.payroll().withRate(rate, schedule)));
        return BusinessService.Outcome.ok(
                "set the payroll of '" + business.name() + "' to " + rate
                        + " gold ingots per in-game day"
                        + (schedule.isBlank() ? "." : " (" + schedule + ")."),
                business.id());
    }

    public static BusinessService.Outcome recordPayment(
            final GovSavedData data,
            final BusinessService.Actor actor,
            final String rawBusiness,
            final String rawAmount,
            final String rawNote,
            final long now) {
        if (data == null || actor == null) {
            return BusinessService.Outcome.refused(ErrorCode.INVALID_STATE, "No business to record a payment on.");
        }
        if (GovConfig.bankEnabled()) {
            return BusinessService.Outcome.refused(
                    ErrorCode.INVALID_STATE,
                    "Banking is enabled; use /gov payroll for business payroll.");
        }
        if (!BusinessLookups.canManage(actor)) {
            return BusinessService.Outcome.refused(
                    ErrorCode.FORBIDDEN,
                    "You need " + GovCapability.BUSINESS_MANAGE.id()
                            + " to record a payroll payment.");
        }
        final BusinessRecord business = BusinessLookups.find(data, rawBusiness);
        if (business == null) {
            return BusinessService.Outcome.refused(ErrorCode.BAD_BUSINESS, BusinessLookups.noSuchBusinessMessage(rawBusiness));
        }
        if (business.closed()) {
            return BusinessService.Outcome.refused(
                    ErrorCode.INVALID_STATE,
                    "Business '" + business.name() + "' is closed; its payroll is frozen.");
        }
        final long amount = parseAmount(rawAmount);
        if (amount <= 0L || amount > BusinessService.MAX_PAYROLL_GOLD) {
            return BusinessService.Outcome.refused(
                    ErrorCode.BAD_PAYLOAD,
                    "A recorded payment must be a whole number of gold ingots from 1 to "
                            + BusinessService.MAX_PAYROLL_GOLD + ".");
        }
        final String note = Sanitizer.text(rawNote);
        data.putBusiness(business.withPayroll(
                business.payroll().withPayment(amount, actor.uuid(), now, note)));
        return BusinessService.Outcome.ok(
                "recorded a physical payment of " + amount + " gold ingots for '"
                        + business.name() + "'"
                        + (note.isBlank() ? "." : " (" + note + ")."),
                business.id());
    }

    public static long parseAmount(final String raw) {
        try {
            return Long.parseLong(raw == null ? "" : raw.trim());
        } catch (final NumberFormatException e) {
            return -1L;
        }
    }
}