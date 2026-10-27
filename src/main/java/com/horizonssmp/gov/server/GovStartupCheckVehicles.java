package com.horizonssmp.gov.server;

import java.util.List;

final class GovStartupCheckVehicles {

    private GovStartupCheckVehicles() {
    }

    static int run(final List<String> failures) {
        final GovStartupCheckVehiclesContext ctx = new GovStartupCheckVehiclesContext(failures);
        try {
            GovStartupCheckVehiclesPart1.run(ctx);
            GovStartupCheckVehiclesPart2.run(ctx);
            GovStartupCheckVehiclesPart3.run(ctx);
            GovStartupCheckVehiclesPart4.run(ctx);
        } catch (final RuntimeException e) {
            ctx.checks++;
            failures.add("vehicle rule check threw: " + e);
        }
        return ctx.checks;
    }
}
