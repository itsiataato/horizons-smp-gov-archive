package com.horizonssmp.gov.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class GovConfigSecurity {

    public static final ModConfigSpec.ConfigValue<Integer> RATE_LIMIT_ACTIONS_PER_10S;
    public static final ModConfigSpec.ConfigValue<Integer> MAX_REQUESTS_PER_TICK;

    static {
        RATE_LIMIT_ACTIONS_PER_10S = GovConfigCore.BUILDER.comment(
                        "Rate limiting and abuse protection.",
                        "",
                        "Actions allowed per player per ten seconds, across every action type.",
                        "Every action has a cost: reads and ordinary mutations 1, exports 3,",
                        "season-reset steps 5. The bucket is per player, never global.")
                .defineInRange("gov.security.rateLimitActionsPer10s", 20, 1, 1000);
        MAX_REQUESTS_PER_TICK = GovConfigCore.BUILDER.comment(
                        "Global per-tick request backstop across all players and actions.",
                        "This is a final safety cap, not a shared quota: each player still has",
                        "their own bucket. Default 64.")
                .defineInRange("gov.security.maxRequestsPerTick", 64, 4, 4096);
    }

    private GovConfigSecurity() {
    }

    public static int rateLimitActionsPer10s() {
        return GovConfigCore.read(RATE_LIMIT_ACTIONS_PER_10S);
    }

    public static int maxRequestsPerTick() {
        return GovConfigCore.read(MAX_REQUESTS_PER_TICK);
    }
}
