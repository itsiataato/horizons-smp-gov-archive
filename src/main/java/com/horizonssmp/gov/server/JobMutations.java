package com.horizonssmp.gov.server;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.GovCapability;
import com.horizonssmp.gov.common.records.JobRecord;

final class JobMutations {

    private JobMutations() {
    }

    static JobService.Outcome create(
            final GovSavedData data, final JobService.Actor actor, final String name, final long now) {
        if (data == null || actor == null) {
            return JobService.Outcome.refused(ErrorCode.INVALID_STATE, "No actor to create a job.");
        }
        final String cleanName = Sanitizer.singleLine(name, Sanitizer.MAX_NAME);
        final String id = Sanitizer.id(cleanName);
        if (id.isBlank()) {
            return JobService.Outcome.refused(
                    ErrorCode.BAD_PAYLOAD,
                    "A job needs a name with at least one letter or digit.");
        }
        if (data.job(id) != null) {
            return JobService.Outcome.refused(
                    ErrorCode.INVALID_STATE, "A job with the id '" + id + "' already exists.");
        }
        data.putJob(new JobRecord(
                id,
                cleanName,
                "",
                "",
                "",
                false,
                EnumSet.noneOf(GovCapability.class),
                now,
                actor.uuid()));
        return JobService.Outcome.ok("created job '" + id + "'");
    }

    static JobService.Outcome deletePrecheck(final GovSavedData data, final String jobId) {
        if (data == null || jobId == null || jobId.isEmpty()) {
            return JobService.Outcome.refused(ErrorCode.BAD_JOB, JobService.noSuchJobMessage(jobId == null ? "" : jobId));
        }
        final JobRecord job = data.job(jobId);
        if (job == null) {
            return JobService.Outcome.refused(ErrorCode.BAD_JOB, JobService.noSuchJobMessage(jobId));
        }
        if (job.system()) {
            return JobService.Outcome.refused(
                    ErrorCode.INVALID_STATE,
                    "The built-in job '" + job.id() + "' cannot be deleted.");
        }
        final int members = JobClassification.holderCount(data, job.id());
        if (members > 0) {
            return JobService.Outcome.refused(
                    ErrorCode.INVALID_STATE,
                    "Job '" + job.id() + "' still has " + members
                            + (members == 1 ? " member" : " members")
                            + "; unassign them first.");
        }
        return null;
    }

    static JobService.Outcome delete(final GovSavedData data, final String jobId) {
        final JobService.Outcome precheck = deletePrecheck(data, jobId);
        if (precheck != null) {
            return precheck;
        }
        if (!data.removeJob(jobId)) {
            return JobService.Outcome.refused(
                    ErrorCode.INVALID_STATE, "Job '" + jobId + "' could not be deleted.");
        }
        return JobService.Outcome.ok("deleted job '" + jobId + "'");
    }

    static JobService.Outcome rename(
            final GovSavedData data, final String jobId, final String displayName) {
        if (data == null || jobId == null || jobId.isEmpty()) {
            return JobService.Outcome.refused(ErrorCode.BAD_JOB, JobService.noSuchJobMessage(jobId == null ? "" : jobId));
        }
        final JobRecord job = data.job(jobId);
        if (job == null) {
            return JobService.Outcome.refused(ErrorCode.BAD_JOB, JobService.noSuchJobMessage(jobId));
        }
        if (job.system()) {
            return JobService.Outcome.refused(
                    ErrorCode.INVALID_STATE,
                    "The built-in job '" + job.id() + "' cannot be renamed.");
        }
        final String cleanName = Sanitizer.singleLine(displayName, Sanitizer.MAX_NAME);
        if (cleanName.isBlank()) {
            return JobService.Outcome.refused(ErrorCode.BAD_PAYLOAD, "A job needs a name.");
        }
        if (cleanName.equals(job.displayName())) {
            return JobService.Outcome.refused(
                    ErrorCode.INVALID_STATE,
                    "Job '" + job.id() + "' is already named '" + cleanName + "'.");
        }
        data.putJob(new JobRecord(
                job.id(),
                cleanName,
                job.color(),
                job.department(),
                job.rankLabel(),
                job.system(),
                job.capabilities(),
                job.createdAt(),
                job.createdBy()));
        return JobService.Outcome.ok("renamed job '" + job.id() + "' to '" + cleanName + "'");
    }

    static JobService.Outcome editCapability(
            final GovSavedData data, final JobService.Actor actor, final String jobId, final String capabilityId,
            final boolean add) {
        if (data == null || actor == null) {
            return JobService.Outcome.refused(ErrorCode.INVALID_STATE, "No actor to edit a job.");
        }
        if (jobId == null || jobId.isEmpty()) {
            return JobService.Outcome.refused(ErrorCode.BAD_JOB, JobService.noSuchJobMessage(""));
        }
        final JobRecord job = data.job(jobId);
        if (job == null) {
            return JobService.Outcome.refused(ErrorCode.BAD_JOB, JobService.noSuchJobMessage(jobId));
        }
        final Optional<GovCapability> parsed = JobService.parseCapability(capabilityId);
        if (parsed.isEmpty()) {
            return JobService.Outcome.refused(
                    ErrorCode.BAD_PAYLOAD,
                    "There is no capability '" + Sanitizer.singleLine(capabilityId, Sanitizer.MAX_ID)
                            + "' in this build.");
        }
        final GovCapability capability = parsed.get();
        if (add && !actor.capabilities().contains(capability)) {
            return JobService.Outcome.refused(
                    ErrorCode.FORBIDDEN,
                    "You cannot grant a capability you do not hold.");
        }
        if (job.capabilities().contains(capability) == add) {
            return JobService.Outcome.refused(
                    ErrorCode.INVALID_STATE,
                    "Job '" + job.id() + "' " + (add ? "already has" : "does not have")
                            + " the capability '" + capability.id() + "'.");
        }

        final Set<GovCapability> edited = EnumSet.copyOf(job.capabilities());
        if (add) {
            edited.add(capability);
        } else {
            edited.remove(capability);
        }
        final JobRecord editedJob = new JobRecord(
                job.id(),
                job.displayName(),
                job.color(),
                job.department(),
                job.rankLabel(),
                job.system(),
                edited,
                job.createdAt(),
                job.createdBy());

        if (!add && capability == GovCapability.JOB_MANAGE && JobExecutiveGuard.stripsLastExecutive(data, editedJob)) {
            return JobExecutiveGuard.lastExecutiveRefusal();
        }
        data.putJob(editedJob);
        return JobService.Outcome.ok(
                (add ? "added" : "removed") + " capability '" + capability.id()
                        + "' " + (add ? "to" : "from") + " job '" + job.id() + "'");
    }
}