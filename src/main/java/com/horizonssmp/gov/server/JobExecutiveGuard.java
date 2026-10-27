package com.horizonssmp.gov.server;

import java.util.Set;
import java.util.UUID;

import com.horizonssmp.gov.common.GovCapabilities;
import com.horizonssmp.gov.common.GovCapability;
import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.records.JobRecord;
import com.horizonssmp.gov.common.records.PlayerRecord;

final class JobExecutiveGuard {

    private JobExecutiveGuard() {
    }

    static boolean removesLastExecutive(
            final GovSavedData data, final UUID target, final String newJobId) {
        final int before = countExecutiveHolders(data, null, null, null);
        if (before == 0) {
            return false;
        }
        return countExecutiveHolders(data, target, newJobId, null) == 0;
    }

    static boolean stripsLastExecutive(
            final GovSavedData data, final JobRecord editedJob) {
        final int before = countExecutiveHolders(data, null, null, null);
        if (before == 0) {
            return false;
        }
        return countExecutiveHolders(data, null, null, editedJob) == 0;
    }

    static int countExecutiveHolders(
            final GovSavedData data, final UUID overriddenUuid, final String overriddenJobId,
            final JobRecord overriddenJob) {
        int count = 0;
        for (final PlayerRecord player : data.players().values()) {
            final Set<GovCapability> capabilities;
            if (overriddenJob != null) {
                capabilities = GovCapabilities.effective(
                        player, data::job, overriddenJob.id(), overriddenJob);
            } else if (player.uuid().equals(overriddenUuid)) {
                capabilities = GovCapabilities.effective(
                        player.withJob(overriddenJobId == null ? "" : overriddenJobId,
                                "", 0L, null, true),
                        data::job);
            } else {
                capabilities = GovCapabilities.effective(player, data::job);
            }
            if (capabilities.contains(GovCapability.JOB_MANAGE)) {
                count++;
            }
        }
        return count;
    }

    static JobService.Outcome lastExecutiveRefusal() {
        return JobService.Outcome.refused(
                ErrorCode.LAST_EXEC,
                "That would leave the government without anyone holding job.manage.");
    }
}