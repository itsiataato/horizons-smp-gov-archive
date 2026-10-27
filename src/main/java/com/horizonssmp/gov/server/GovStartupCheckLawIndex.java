package com.horizonssmp.gov.server;

import java.util.List;
import com.horizonssmp.gov.law.LawIndex;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

final class GovStartupCheckLawIndex {

    private GovStartupCheckLawIndex() {
    }

    static int run(final List<String> failures) {
        int checks = 0;
        final LawIndex index = LawIndex.get();

        checks++;
        if (index.articleCount() != index.law().articleCount()) {
            failures.add("law index covers " + index.articleCount() + " of "
                    + index.law().articleCount() + " articles");
        }

        checks++;
        if (index.articleIds().isEmpty() || !index.exists(index.articleIds().get(0))) {
            failures.add("law index cannot resolve its own first article");
        }

        checks++;
        if (index.exists("999.999")) {
            failures.add("law index resolved an article that does not exist");
        }
        return checks;
    }

    /**
     * The network contract: the channel is really registered, the payload codecs round-trip, and
     * the two shipped read actions are reachable by a brand new player.
     *
     * <p>This runs on the server thread after the network registry was set up (which happens
     * during mod loading, before the server starts), so {@code NetworkRegistry.getCodec} answers
     * about the live registry, not about a copy. A channel that silently failed to register would
     * otherwise only be discovered by the first player who could not connect.
     */

}
