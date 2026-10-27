package com.horizonssmp.gov.server;

import java.util.List;

final class GovStartupCheckFines {

    private GovStartupCheckFines() {
    }

    static int run(final List<String> failures) {
        final GovStartupCheckFinesContext ctx = new GovStartupCheckFinesContext(failures);
        try {
            GovStartupCheckFinesPart1.run(ctx);
            GovStartupCheckFinesPart2.run(ctx);
            GovStartupCheckFinesPart3.run(ctx);
        } catch (final RuntimeException e) {
            ctx.checks++;
            failures.add("fine rule check threw: " + e);
        }
        return ctx.checks;
    }
}
