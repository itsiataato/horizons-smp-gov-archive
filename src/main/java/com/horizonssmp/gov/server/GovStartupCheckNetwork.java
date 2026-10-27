package com.horizonssmp.gov.server;

import java.util.List;

final class GovStartupCheckNetwork {

    private GovStartupCheckNetwork() {
    }

    static int run(final List<String> failures) {
        final GovStartupCheckNetworkContext ctx = new GovStartupCheckNetworkContext(failures);
        GovStartupCheckNetworkPart1.run(ctx);
        GovStartupCheckNetworkPart2.run(ctx);
        GovStartupCheckNetworkPart3.run(ctx);
        return ctx.checks;
    }
}
