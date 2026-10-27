package com.horizonssmp.gov.server;

import java.util.List;

final class GovStartupCheckBusinessEmploymentService {

    private GovStartupCheckBusinessEmploymentService() {
    }

    static int run(final List<String> failures) {
        final GovStartupCheckBusinessEmploymentServiceContext ctx =
                new GovStartupCheckBusinessEmploymentServiceContext(failures);
        try {
            GovStartupCheckBusinessEmploymentServicePart1.run(ctx);
            GovStartupCheckBusinessEmploymentServicePart2.run(ctx);
            GovStartupCheckBusinessEmploymentServicePart3.run(ctx);
            GovStartupCheckBusinessEmploymentServicePart4.run(ctx);
        } catch (final RuntimeException e) {
            ctx.checks++;
            failures.add("business employment service check threw: " + e);
        }
        return ctx.checks;
    }
}
