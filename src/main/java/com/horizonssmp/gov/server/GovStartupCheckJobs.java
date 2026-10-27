package com.horizonssmp.gov.server;

import java.util.List;

final class GovStartupCheckJobs {

    private GovStartupCheckJobs() {
    }

    static int run(final List<String> failures) {
        final GovStartupCheckJobsContext ctx = new GovStartupCheckJobsContext(failures);
        try {
            GovStartupCheckJobsPart1.run(ctx);
            GovStartupCheckJobsPart2.run(ctx);
        } catch (final RuntimeException e) {
            ctx.checks++;
            failures.add("job rule check threw: " + e);
        }
        return ctx.checks;
    }
}
