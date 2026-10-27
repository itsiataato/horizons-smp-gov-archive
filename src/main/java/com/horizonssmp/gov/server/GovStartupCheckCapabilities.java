package com.horizonssmp.gov.server;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import com.horizonssmp.gov.common.GovCapability;

final class GovStartupCheckCapabilities {

    private GovStartupCheckCapabilities() {
    }

    static int run(final List<String> failures) {
        int checks = 0;
        final Set<String> seen = new HashSet<>();
        for (final GovCapability capability : GovCapability.values()) {
            checks++;
            if (GovCapability.byId(capability.id()).orElse(null) != capability) {
                failures.add("capability id does not round-trip: " + capability.id());
            }
            checks++;
            if (!seen.add(capability.id())) {
                failures.add("duplicate capability id: " + capability.id());
            }
            checks++;
            if (!capability.id().equals(capability.id().toLowerCase(Locale.ROOT))
                    || capability.id().isBlank()) {
                failures.add("capability id is not a lowercase token: " + capability.id());
            }
        }
        return checks;
    }

    /** Every action must name exactly one required capability. */

}
