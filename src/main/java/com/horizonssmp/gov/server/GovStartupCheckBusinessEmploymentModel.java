package com.horizonssmp.gov.server;

import java.util.List;

final class GovStartupCheckBusinessEmploymentModel {

    private GovStartupCheckBusinessEmploymentModel() {
    }

    static int run(final List<String> failures) {
        final GovStartupCheckBusinessEmploymentModelContext ctx =
                new GovStartupCheckBusinessEmploymentModelContext(failures);
        try {
            GovStartupCheckBusinessEmploymentModelPart1.run(ctx);
            GovStartupCheckBusinessEmploymentModelPart2.run(ctx);
        } catch (final RuntimeException e) {
            ctx.checks++;
            failures.add("business employment model check threw: " + e);
        }
        return ctx.checks;
    }
}
