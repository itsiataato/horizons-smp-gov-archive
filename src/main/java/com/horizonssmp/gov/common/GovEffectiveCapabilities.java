package com.horizonssmp.gov.common;

import static com.horizonssmp.gov.common.GovCapabilities.*;

import java.util.EnumSet;
import java.util.Set;
import java.util.function.Function;

import com.horizonssmp.gov.common.records.JobRecord;
import com.horizonssmp.gov.common.records.PlayerRecord;
import com.horizonssmp.gov.config.GovConfig;

/**
 * Helper: effective capability computation for a player.
 * Extracted from {@link GovCapabilities}; {@link GovCapabilities} delegates here.
 */
final class GovEffectiveCapabilities {

    private GovEffectiveCapabilities() {
    }

    public static Set<GovCapability> effective(
            final PlayerRecord player, final Function<String, JobRecord> jobs) {
        return effective(player, jobs, null, null);
    }

    public static Set<GovCapability> effective(
            final PlayerRecord player, final Function<String, JobRecord> jobs,
            final String overriddenJobId, final JobRecord override) {
        final Set<GovCapability> result = EnumSet.noneOf(GovCapability.class);
        result.addAll(BASELINE);
        if (player == null) {
            return result;
        }

        result.addAll(player.capabilities());

        // Exactly one job is authoritative. The primary field wins; the employment block is the
        // denormalized mirror of the same decision and is consulted only when the primary is empty,
        // which is the shape of an older file. A hand-edited file naming two jobs therefore grants
        // the authority of one, never both.
        final String jobId = jobIdOf(player);
        if (!jobId.isEmpty()) {
            final JobRecord job = jobId.equals(overriddenJobId) ? override : jobs.apply(jobId);
            if (job != null) {
                for (final GovCapability capability : job.capabilities()) {
                    if (grantedBy(job, capability)) {
                        result.add(capability);
                    }
                }
            }
        }
        return result;
    }

    public static String jobIdOf(final PlayerRecord player) {
        if (player == null) {
            return "";
        }
        return player.primaryJobId().isEmpty()
                ? player.employment().jobId() : player.primaryJobId();
    }

    public static boolean holds(final PlayerRecord player, final String jobId) {
        if (player == null || jobId == null || jobId.isEmpty()) {
            return false;
        }
        return jobId.equals(jobIdOf(player));
    }

    private static boolean grantedBy(final JobRecord job, final GovCapability capability) {
        if (GOVWORKER_JOB_ID.equals(job.id())) {
            if (capability == GovCapability.JOB_ASSIGN) {
                return GovConfig.govworkerCanAssign();
            }
            if (capability == GovCapability.FINE_WAIVE) {
                return GovConfig.govworkerCanWaiveFines();
            }
        }
        return true;
    }
}
