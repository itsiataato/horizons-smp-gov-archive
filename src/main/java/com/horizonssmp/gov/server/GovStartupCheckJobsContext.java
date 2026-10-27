package com.horizonssmp.gov.server;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import com.horizonssmp.gov.common.GovCapability;

final class GovStartupCheckJobsContext {
    final List<String> failures;
    int checks;
    final long now = System.currentTimeMillis();
    final GovSavedData scratch = new GovSavedData();
    final JobService.Actor manager = new JobService.Actor(
            new UUID(0L, 1001L), "Manager", EnumSet.allOf(GovCapability.class));
    final UUID alice = new UUID(0L, 2001L);
    final UUID bob = new UUID(0L, 2002L);
    final UUID carol = new UUID(0L, 2003L);
    final UUID dave = new UUID(0L, 2004L);

    GovStartupCheckJobsContext(final List<String> failures) {
        this.failures = failures;
        this.scratch.seedIfNeeded(now, false);
    }
}
