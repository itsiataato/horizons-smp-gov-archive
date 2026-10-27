package com.horizonssmp.gov.server;

import java.util.List;

final class GovStartupCheckBusinesses {

    private GovStartupCheckBusinesses() {
    }

    static int run(final List<String> failures) {
        final GovStartupCheckBusinessesContext ctx = new GovStartupCheckBusinessesContext(failures);
        try {
            GovStartupCheckBusinessesPart1.run(ctx);
            GovStartupCheckBusinessesPart2.run(ctx);
            GovStartupCheckBusinessesPart3.run(ctx);
            GovStartupCheckBusinessesPart4.run(ctx);
        } catch (final RuntimeException e) {
            ctx.checks++;
            failures.add("business registry rule check threw: " + e);
        }
        return ctx.checks;
    }
}
