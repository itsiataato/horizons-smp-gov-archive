package com.horizonssmp.gov.server;

import com.horizonssmp.gov.common.enums.BusinessType;
import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.enums.BusinessRole;
import com.horizonssmp.gov.common.records.BusinessRecord;

/**
 * Helper for {@link BusinessService}: data-only prechecks.
 */
public final class BusinessPrechecks {

    private BusinessPrechecks() {
    }

    public static BusinessService.Outcome registerPrecheck(
            final GovSavedData data, final String rawName, final String rawType) {
        final String name = Sanitizer.singleLine(rawName, Sanitizer.MAX_NAME);
        if (name.isBlank()) {
            return BusinessService.Outcome.refused(
                    ErrorCode.BAD_PAYLOAD,
                    "A business needs a name with at least one letter or digit.");
        }
        if (BusinessType.parse(rawType) == null) {
            return BusinessService.Outcome.refused(
                    ErrorCode.BAD_PAYLOAD,
                    "Unknown business type. Use one of: " + BusinessType.ids() + ".");
        }
        if (BusinessLookups.byName(data, name) != null) {
            return BusinessService.Outcome.refused(ErrorCode.NAME_TAKEN, "The name '" + name + "' is taken.");
        }
        return null;
    }

    public static BusinessService.Outcome suspendPrecheck(
            final GovSavedData data, final String rawBusiness, final String rawReason) {
        final BusinessRecord business = BusinessLookups.find(data, rawBusiness);
        if (business == null) {
            return BusinessService.Outcome.refused(ErrorCode.BAD_BUSINESS, BusinessLookups.noSuchBusinessMessage(rawBusiness));
        }
        if (Sanitizer.text(rawReason).isBlank()) {
            return BusinessService.Outcome.refused(
                    ErrorCode.BAD_PAYLOAD,
                    "A suspension needs a recorded reason of at most " + Sanitizer.MAX_TEXT
                            + " characters.");
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
        return null;
    }

    public static BusinessService.Outcome closePrecheck(final GovSavedData data, final String rawBusiness) {
        final BusinessRecord business = BusinessLookups.find(data, rawBusiness);
        if (business == null) {
            return BusinessService.Outcome.refused(ErrorCode.BAD_BUSINESS, BusinessLookups.noSuchBusinessMessage(rawBusiness));
        }
        if (business.closed()) {
            return BusinessService.Outcome.refused(
                    ErrorCode.INVALID_STATE,
                    "Business '" + business.name() + "' is already closed.");
        }
        return null;
    }

    public static BusinessService.Outcome employeeAddPrecheck(
            final GovSavedData data, final String rawBusiness, final String rawRole) {
        final BusinessRecord business = BusinessLookups.find(data, rawBusiness);
        if (business == null) {
            return BusinessService.Outcome.refused(ErrorCode.BAD_BUSINESS, BusinessLookups.noSuchBusinessMessage(rawBusiness));
        }
        if (BusinessRole.parse(rawRole) == null) {
            return BusinessService.Outcome.refused(
                    ErrorCode.BAD_PAYLOAD,
                    "Unknown role. Use one of: " + BusinessRole.ids() + ".");
        }
        if (business.closed()) {
            return BusinessService.Outcome.refused(
                    ErrorCode.INVALID_STATE,
                    "Business '" + business.name() + "' is closed; it cannot take staff.");
        }
        if (business.employees().size() >= BusinessService.MAX_EMPLOYEES) {
            return BusinessService.Outcome.refused(
                    ErrorCode.INVALID_STATE,
                    "Business '" + business.name() + "' already has the maximum of "
                            + BusinessService.MAX_EMPLOYEES + " staff entries.");
        }
        return null;
    }

    public static BusinessService.Outcome employeeRemovePrecheck(
            final GovSavedData data, final String rawBusiness) {
        final BusinessRecord business = BusinessLookups.find(data, rawBusiness);
        if (business == null) {
            return BusinessService.Outcome.refused(ErrorCode.BAD_BUSINESS, BusinessLookups.noSuchBusinessMessage(rawBusiness));
        }
        return null;
    }

    public static BusinessService.Outcome linkPrecheck(
            final GovSavedData data, final String rawBusiness, final String rawJobId) {
        final BusinessRecord business = BusinessLookups.find(data, rawBusiness);
        if (business == null) {
            return BusinessService.Outcome.refused(ErrorCode.BAD_BUSINESS, BusinessLookups.noSuchBusinessMessage(rawBusiness));
        }
        if (business.closed()) {
            return BusinessService.Outcome.refused(
                    ErrorCode.INVALID_STATE,
                    "Business '" + business.name() + "' is closed; it cannot be linked.");
        }
        final String jobId = Sanitizer.id(rawJobId);
        if (jobId.isEmpty()) {
            if (business.linkedJobId().isEmpty()) {
                return BusinessService.Outcome.refused(
                        ErrorCode.INVALID_STATE,
                        "Business '" + business.name() + "' has no linked job to remove.");
            }
            return null;
        }
        if (data.job(jobId) == null) {
            return BusinessService.Outcome.refused(ErrorCode.BAD_JOB, JobService.noSuchJobMessage(jobId));
        }
        if (jobId.equals(business.linkedJobId())) {
            return BusinessService.Outcome.refused(
                    ErrorCode.INVALID_STATE,
                    "Business '" + business.name() + "' is already linked to job '" + jobId + "'.");
        }
        return null;
    }

    public static BusinessService.Outcome payrollSetPrecheck(
            final GovSavedData data, final String rawBusiness, final String rawRate) {
        final BusinessRecord business = BusinessLookups.find(data, rawBusiness);
        if (business == null) {
            return BusinessService.Outcome.refused(ErrorCode.BAD_BUSINESS, BusinessLookups.noSuchBusinessMessage(rawBusiness));
        }
        if (business.closed()) {
            return BusinessService.Outcome.refused(
                    ErrorCode.INVALID_STATE,
                    "Business '" + business.name() + "' is closed; its payroll is frozen.");
        }
        final long rate = BusinessPayroll.parseAmount(rawRate);
        if (rate < 0L || rate > BusinessService.MAX_PAYROLL_GOLD) {
            return BusinessService.Outcome.refused(
                    ErrorCode.BAD_PAYLOAD,
                    "Payroll rate must be a whole number of gold ingots from 0 to "
                            + BusinessService.MAX_PAYROLL_GOLD + " per in-game day.");
        }
        return null;
    }

    public static BusinessService.Outcome payrollRecordPrecheck(
            final GovSavedData data, final String rawBusiness, final String rawAmount) {
        final BusinessRecord business = BusinessLookups.find(data, rawBusiness);
        if (business == null) {
            return BusinessService.Outcome.refused(ErrorCode.BAD_BUSINESS, BusinessLookups.noSuchBusinessMessage(rawBusiness));
        }
        if (business.closed()) {
            return BusinessService.Outcome.refused(
                    ErrorCode.INVALID_STATE,
                    "Business '" + business.name() + "' is closed; its payroll is frozen.");
        }
        final long amount = BusinessPayroll.parseAmount(rawAmount);
        if (amount <= 0L || amount > BusinessService.MAX_PAYROLL_GOLD) {
            return BusinessService.Outcome.refused(
                    ErrorCode.BAD_PAYLOAD,
                    "A recorded payment must be a whole number of gold ingots from 1 to "
                            + BusinessService.MAX_PAYROLL_GOLD + ".");
        }
        return null;
    }
}