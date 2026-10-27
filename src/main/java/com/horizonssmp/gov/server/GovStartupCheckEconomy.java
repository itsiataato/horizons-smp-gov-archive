package com.horizonssmp.gov.server;

import java.util.List;
import net.minecraft.server.MinecraftServer;

final class GovStartupCheckEconomy {

    private GovStartupCheckEconomy() {
    }

    static int run(final GovSavedData data, final MinecraftServer server, final List<String> failures) {
        int checks = 0;
        checks += GovStartupCheckEconomyPart1.run(data, failures);
        checks += GovStartupCheckEconomyPart2.run(data, server, failures);
        return checks;
    }
}
