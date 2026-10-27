package com.horizonssmp.gov.server;

import java.util.List;
import com.horizonssmp.gov.common.records.JobRecord;

final class GovStartupCheckJobSupport {

    private GovStartupCheckJobSupport() {
    }

    static int expectJob(
            final GovSavedData data, final String id, final boolean system, final List<String> failures) {
        final JobRecord job = data.job(id);
        if (job == null) {
            failures.add("seeded job is missing: " + id);
            return 1;
        }
        if (job.system() != system) {
            failures.add("seeded job " + id + " should have system=" + system);
        }
        if (job.capabilities().isEmpty()) {
            failures.add("seeded job " + id + " has no capabilities");
        }
        return 1;
    }

    /** One seeded job must classify as the expected roster type. */
    static int expectJobType(
            final GovSavedData data, final String id, final String expected, final List<String> failures) {
        final JobRecord job = data.job(id);
        if (job == null) {
            failures.add("job missing for the type check: " + id);
            return 1;
        }
        final String actual = JobService.typeOf(job);
        if (!expected.equals(actual)) {
            failures.add("job " + id + " should be " + expected + " but is " + actual);
        }
        return 1;
    }

    /**
     * The M2 job rules, exercised against a scratch data set seeded with the real matrix.
     *
     * <p>A scratch set, not the live one: the check creates, assigns, replaces and deletes jobs to
     * prove the rules, and it must not leave a synthetic player or job in {@code gov.dat}. The
     * scratch set is seeded through the same {@link GovSavedData#seedIfNeeded} the world uses, so
     * the rules run against exactly the definitions a fresh server has.
     *
     * <p>The dispatcher needs a {@code ServerPlayer} and cannot run headlessly, so this exercises
     * the service layer the handlers call, with a synthetic actor; handler registration and the
     * capability mapping are asserted separately in {@link #checkNetwork}.
     */

}
