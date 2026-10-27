package com.horizonssmp.gov.server;

import java.util.Set;
import java.util.UUID;

import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.GovCapabilities;
import com.horizonssmp.gov.common.GovCapability;
import com.horizonssmp.gov.common.records.JobRecord;
import com.horizonssmp.gov.common.records.PlayerRecord;

final class JobAssignment {

    private JobAssignment() {
    }

    static JobService.Outcome assign(
            final GovSavedData data, final JobService.Actor actor, final UUID targetUuid,
            final String targetName, final String jobId, final long now) {
        if (data == null || targetUuid == null || actor == null) {
            return JobService.Outcome.refused(ErrorCode.INVALID_STATE, "No player to assign to.");
        }
        final String name = Sanitizer.name(targetName);
        final String wanted = jobId == null ? "" : jobId.trim();
        final PlayerRecord record = data.playerOrCreate(targetUuid, name, now);

        if (wanted.isEmpty()) {
            if (!record.employed() && !record.employment().employed()) {
                if (!record.jobDecided()) {
                    data.putPlayer(record.withJob("", "", 0L, null, true));
                    return JobService.Outcome.ok("recorded " + name + " as having no job");
                }
                return JobService.Outcome.refused(ErrorCode.INVALID_STATE, name + " has no job to clear.");
            }
            if (JobExecutiveGuard.removesLastExecutive(data, targetUuid, "")) {
                return JobExecutiveGuard.lastExecutiveRefusal();
            }
            final String previous = record.primaryJobId().isEmpty()
                    ? record.employment().jobId() : record.primaryJobId();
            data.putPlayer(record.withJob("", "", 0L, null, true));
            HistoryService.append(data, targetUuid, com.horizonssmp.gov.common.enums.HistoryKind.GOV_JOB_REMOVED,
                    "Government job '" + previous + "' was cleared by "
                            + Sanitizer.name(actor.name()) + ".",
                    actor.uuid(), actor.name(), now, previous);
            return JobService.Outcome.ok("cleared the job of " + name);
        }

        final JobRecord job = data.job(wanted);
        if (job == null) {
            return JobService.Outcome.refused(ErrorCode.BAD_JOB, JobService.noSuchJobMessage(wanted));
        }
        if (grantsNewAuthority(data, record, job, actor.capabilities())) {
            if (targetUuid.equals(actor.uuid())) {
                return JobService.Outcome.refused(
                        ErrorCode.SELF_PROMOTION,
                        "You cannot assign yourself a job that grants authority you do not hold.");
            }
            return JobService.Outcome.refused(
                    ErrorCode.FORBIDDEN,
                    "You cannot grant a job with authority you do not hold.");
        }
        if (record.primaryJobId().equals(job.id()) && record.employment().jobId().equals(job.id())) {
            return JobService.Outcome.ok(name + " already holds the job '" + job.id() + "'");
        }
        if (JobExecutiveGuard.removesLastExecutive(data, targetUuid, job.id())) {
            return JobExecutiveGuard.lastExecutiveRefusal();
        }
        data.putPlayer(record.withJob(job.id(), job.rankLabel(), now, actor.uuid(), true));
        HistoryService.append(data, targetUuid, com.horizonssmp.gov.common.enums.HistoryKind.GOV_JOB_ASSIGNED,
                "Government job '" + job.displayName() + " (" + job.id() + ")' was assigned by "
                        + Sanitizer.name(actor.name()) + ".",
                actor.uuid(), actor.name(), now, job.id());
        return JobService.Outcome.ok("assigned job '" + job.id() + "' to " + name);
    }

    private static boolean grantsNewAuthority(
            final GovSavedData data, final PlayerRecord record, final JobRecord job,
            final Set<GovCapability> actorCapabilities) {
        final PlayerRecord after = record.withJob(job.id(), job.rankLabel(), 0L, null, true);
        for (final GovCapability capability : GovCapabilities.effective(after, data::job)) {
            if (!actorCapabilities.contains(capability)) {
                return true;
            }
        }
        return false;
    }
}