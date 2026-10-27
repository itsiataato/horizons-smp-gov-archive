package com.horizonssmp.gov.server;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.horizonssmp.gov.common.GovCapabilities;
import com.horizonssmp.gov.common.GovCapability;
import com.horizonssmp.gov.common.records.JobRecord;
import com.horizonssmp.gov.common.records.PlayerRecord;

final class JobClassification {

    private JobClassification() {
    }

    static String typeOf(final JobRecord job) {
        if (job == null) {
            return JobService.TYPE_CUSTOM;
        }
        if (job.system()) {
            return JobService.TYPE_BUILTIN;
        }
        return job.template() ? JobService.TYPE_TEMPLATE : JobService.TYPE_CUSTOM;
    }

    static List<PlayerRecord> holders(final GovSavedData data, final String jobId) {
        final List<PlayerRecord> result = new ArrayList<>();
        if (data == null || jobId == null || jobId.isEmpty()) {
            return result;
        }
        for (final PlayerRecord player : data.players().values()) {
            if (GovCapabilities.holds(player, jobId)) {
                result.add(player);
            }
        }
        return result;
    }

    static int holderCount(final GovSavedData data, final String jobId) {
        return holders(data, jobId).size();
    }

    static String noSuchJobMessage(final String jobId) {
        return "There is no job '" + Sanitizer.singleLine(jobId, Sanitizer.MAX_ID) + "'.";
    }

    static Optional<GovCapability> parseCapability(final String raw) {
        return GovCapability.byId(raw == null ? "" : raw.strip());
    }
}