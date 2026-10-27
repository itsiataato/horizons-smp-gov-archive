package com.horizonssmp.gov.server;

import java.util.List;

final class GovStartupCheckPeople {

    private GovStartupCheckPeople() {
    }

    static int run(final List<String> failures) {
        final GovStartupCheckPeopleContext ctx = new GovStartupCheckPeopleContext(failures);
        GovStartupCheckPeoplePart1.run(ctx);
        GovStartupCheckPeoplePart2.run(ctx);
        GovStartupCheckPeoplePart3.run(ctx);
        return ctx.checks;
    }
}
