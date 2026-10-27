package com.horizonssmp.gov.server;

import com.horizonssmp.gov.Gov;
import com.horizonssmp.gov.common.GovCapabilities;

public final class GovSavedDataSeeds {
    private GovSavedDataSeeds() {}

    public static int seedIfNeeded(final GovSavedData data, final long now, final boolean announce) {
        final GovPersonnelStore p = data.personnel;
        final int economySeeded = data.accountStore.seedEconomyIfNeeded(now);
        // TASK-033 rollout: worlds seeded before the builder job existed have
        // templatesSeeded=true but no builder row. Seeding the builder first makes the
        // call idempotent for both fresh and existing worlds: seedJob is a no-op when
        // the id already exists.
        final int builderSeeded = p.seedJob(GovCapabilities.BUILDER_JOB_ID, "Builder", "#A67C3D",
                "Works", "Builder", false, now);
        if (data.templatesSeeded) {
            if (builderSeeded > 0) {
                data.setDirty();
                if (announce) {
                    Gov.LOGGER.info("[gov] Seeded builder job definition for an existing world");
                }
            }
            if (economySeeded > 0 && announce) {
                Gov.LOGGER.info("[gov] Seeded {} economy singleton account(s)", economySeeded);
            }
            return builderSeeded;
        }
        int written = builderSeeded;
        written += p.seedJob(GovCapabilities.CITIZEN_JOB_ID, "Citizen", "#9AA0A6", "Public", "Citizen", true, now);
        written += p.seedJob(GovCapabilities.POLICE_JOB_ID, "Police", "#4A6E9B", "Police", "Officer", true, now);
        written += p.seedJob(GovCapabilities.GOVEXECUTIVE_JOB_ID, "Government Executive", "#8C7A3F",
                "Government", "Executive", true, now);
        written += p.seedJob(GovCapabilities.GOVWORKER_JOB_ID, "Government Worker", "#6E7B85",
                "Government", "Receptionist", false, now);
        written += p.seedJob(GovCapabilities.JUDGE_JOB_ID, "Judge", "#7A6E8C", "Judiciary", "Judge", false, now);

        data.templatesSeeded = true;
        data.setDirty();
        if (announce) {
            Gov.LOGGER.info("[gov] Seeded {} job definitions ({} system, {} templates)",
                    p.jobs.size(), p.jobs.size() - data.templateCount(), data.templateCount());
            if (economySeeded > 0) {
                Gov.LOGGER.info("[gov] Seeded {} economy singleton account(s)", economySeeded);
            }
        }
        return written;
    }
}