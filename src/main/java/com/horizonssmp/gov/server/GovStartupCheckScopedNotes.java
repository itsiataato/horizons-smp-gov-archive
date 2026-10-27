package com.horizonssmp.gov.server;

import java.util.List;

final class GovStartupCheckScopedNotes {

    private GovStartupCheckScopedNotes() {
    }

    static int run(final List<String> failures) {
        final GovStartupCheckScopedNotesContext ctx = new GovStartupCheckScopedNotesContext(failures);
        try {
            GovStartupCheckScopedNotesPart1.run(ctx);
            GovStartupCheckScopedNotesPart2.run(ctx);
            GovStartupCheckScopedNotesPart3.run(ctx);
            GovStartupCheckScopedNotesPart4.run(ctx);
            GovStartupCheckScopedNotesPart5.run(ctx);
            GovStartupCheckScopedNotesPart6.run(ctx);
        } catch (final RuntimeException e) {
            ctx.checks++;
            failures.add("scoped note check threw: " + e);
        }
        return ctx.checks;
    }
}
