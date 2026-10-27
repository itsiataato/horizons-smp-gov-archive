package com.horizonssmp.gov.server;

import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.records.BusinessRecord;

/**
 * Helper for {@link BusinessService}: linking a business to a job.
 */
public final class BusinessLinks {

    private BusinessLinks() {
    }

    public static BusinessService.Outcome link(
            final GovSavedData data,
            final BusinessService.Actor actor,
            final String rawBusiness,
            final String rawJobId) {
        if (data == null || actor == null) {
            return BusinessService.Outcome.refused(ErrorCode.INVALID_STATE, "No business to link.");
        }
        final BusinessRecord business = BusinessLookups.find(data, rawBusiness);
        if (business == null) {
            return BusinessService.Outcome.refused(ErrorCode.BAD_BUSINESS, BusinessLookups.noSuchBusinessMessage(rawBusiness));
        }
        if (!BusinessLookups.isOwner(business, actor)) {
            return BusinessService.Outcome.refused(
                    ErrorCode.FORBIDDEN,
                    "Only the owner of this business may link it.");
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
            data.putBusiness(business.withLinkedJob(""));
            return BusinessService.Outcome.ok("unlinked the job from '" + business.name() + "'.", business.id());
        }
        if (data.job(jobId) == null) {
            return BusinessService.Outcome.refused(ErrorCode.BAD_JOB, JobService.noSuchJobMessage(jobId));
        }
        if (jobId.equals(business.linkedJobId())) {
            return BusinessService.Outcome.refused(
                    ErrorCode.INVALID_STATE,
                    "Business '" + business.name() + "' is already linked to job '" + jobId + "'.");
        }
        data.putBusiness(business.withLinkedJob(jobId));
        return BusinessService.Outcome.ok(
                "linked job '" + jobId + "' to '" + business.name()
                        + "' (registry note only; it grants no capability).",
                business.id());
    }
}