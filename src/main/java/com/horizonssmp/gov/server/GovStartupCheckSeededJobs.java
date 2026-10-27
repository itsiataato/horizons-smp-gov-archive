package com.horizonssmp.gov.server;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import com.horizonssmp.gov.common.GovCapability;
import com.horizonssmp.gov.common.GovCapabilities;
import com.horizonssmp.gov.common.JobKeys;
import com.horizonssmp.gov.common.records.JobRecord;

final class GovStartupCheckSeededJobs {

    private GovStartupCheckSeededJobs() {
    }

    static int run(final GovSavedData data, final List<String> failures) {
        int checks = 0;

        checks += GovStartupCheckJobSupport.expectJob(data, GovCapabilities.CITIZEN_JOB_ID, true, failures);
        checks += GovStartupCheckJobSupport.expectJob(data, GovCapabilities.POLICE_JOB_ID, true, failures);
        checks += GovStartupCheckJobSupport.expectJob(data, GovCapabilities.GOVEXECUTIVE_JOB_ID, true, failures);
        checks += GovStartupCheckJobSupport.expectJob(data, GovCapabilities.GOVWORKER_JOB_ID, false, failures);
        checks += GovStartupCheckJobSupport.expectJob(data, GovCapabilities.JUDGE_JOB_ID, false, failures);
        checks += GovStartupCheckJobSupport.expectJob(data, GovCapabilities.BUILDER_JOB_ID, false, failures);

        // The roster labels the Jobs tab shows: three protected built-ins and three templates.
        checks += GovStartupCheckJobSupport.expectJobType(data, GovCapabilities.CITIZEN_JOB_ID, JobKeys.TYPE_BUILTIN, failures);
        checks += GovStartupCheckJobSupport.expectJobType(data, GovCapabilities.POLICE_JOB_ID, JobKeys.TYPE_BUILTIN, failures);
        checks += GovStartupCheckJobSupport.expectJobType(data, GovCapabilities.GOVEXECUTIVE_JOB_ID, JobKeys.TYPE_BUILTIN, failures);
        checks += GovStartupCheckJobSupport.expectJobType(data, GovCapabilities.GOVWORKER_JOB_ID, JobKeys.TYPE_TEMPLATE, failures);
        checks += GovStartupCheckJobSupport.expectJobType(data, GovCapabilities.JUDGE_JOB_ID, JobKeys.TYPE_TEMPLATE, failures);
        checks += GovStartupCheckJobSupport.expectJobType(data, GovCapabilities.BUILDER_JOB_ID, JobKeys.TYPE_TEMPLATE, failures);

        checks++;
        final JobRecord citizen = data.job(GovCapabilities.CITIZEN_JOB_ID);
        if (citizen != null
                && !citizen.capabilities().equals(
                        EnumSet.of(GovCapability.PLAYER_LOOKUP_SELF, GovCapability.LAW_VIEW))) {
            failures.add("citizen capabilities are not the matrix set");
        }

        checks++;
        final JobRecord executive = data.job(GovCapabilities.GOVEXECUTIVE_JOB_ID);
        if (executive != null) {
            // Baseline capabilities are granted to every player at call time, so a world seeded
            // before a baseline capability was added (job.view arrived in M2) simply lacks it in
            // the stored record. The check asserts the authority the matrix promises: every
            // non-baseline capability.
            final Set<GovCapability> expected = EnumSet.allOf(GovCapability.class);
            expected.removeAll(GovCapabilities.BASELINE);
            if (!executive.capabilities().containsAll(expected)) {
                failures.add("govexecutive does not hold every non-baseline capability");
            }
        }

        checks++;
        final JobRecord worker = data.job(GovCapabilities.GOVWORKER_JOB_ID);
        if (worker != null && !worker.capabilities().contains(GovCapability.FINE_WAIVE)) {
            failures.add("govworker seed is missing fine.waive, so the reception override cannot work");
        }

        checks++;
        final JobRecord police = data.job(GovCapabilities.POLICE_JOB_ID);
        if (police != null
                && (!police.capabilities().contains(GovCapability.JAIL_COMMIT)
                        || !police.capabilities().contains(GovCapability.DUTY_TOGGLE))) {
            failures.add("police seed is missing jail.commit or duty.toggle");
        }

        checks++;
        final JobRecord judge = data.job(GovCapabilities.JUDGE_JOB_ID);
        if (judge != null
                && (!judge.capabilities().contains(GovCapability.FINE_WAIVE)
                        || !judge.capabilities().contains(GovCapability.AUDIT_VIEW))) {
            failures.add("judge seed is missing fine.waive or audit.view");
        }

        checks++;
        final JobRecord builder = data.job(GovCapabilities.BUILDER_JOB_ID);
        if (builder != null
                && !builder.capabilities().equals(
                        EnumSet.of(GovCapability.PLAYER_LOOKUP_SELF, GovCapability.LAW_VIEW))) {
            failures.add("builder capabilities are not the matrix set");
        }
        return checks;
    }

}
