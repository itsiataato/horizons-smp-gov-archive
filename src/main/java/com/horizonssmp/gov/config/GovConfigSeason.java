package com.horizonssmp.gov.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class GovConfigSeason {

    public static final ModConfigSpec.ConfigValue<Boolean> SEASON_CLEAR_BUSINESSES;
    public static final ModConfigSpec.ConfigValue<Integer> SEASON_RESET_TOKEN_SECONDS;

    static {
        SEASON_CLEAR_BUSINESSES = GovConfigCore.BUILDER.comment(
                        "Season reset.",
                        "",
                        "Whether a season reset also clears business records. Default false: a",
                        "reset clears licences, vehicles, weapons, permits, area permits,",
                        "detentions, fines and notes, and resets player jobs to citizen. Job",
                        "definitions, capabilities, config, warrants, plate scans and the audit",
                        "log are always kept. The begin answer reports the policy in force.")
                .define("gov.season.clearBusinesses", false);
        SEASON_RESET_TOKEN_SECONDS = GovConfigCore.BUILDER.comment(
                        "How long a season-reset confirmation token stays valid, in seconds.",
                        "The token is single-use, tied to the actor and the loaded data, and",
                        "in memory only. Default 300 (five real minutes).")
                .defineInRange("gov.season.resetTokenSeconds", 300, 30, 3600);
    }

    private GovConfigSeason() {
    }

    public static boolean seasonClearBusinesses() {
        return GovConfigCore.read(SEASON_CLEAR_BUSINESSES);
    }

    public static int seasonResetTokenSeconds() {
        return GovConfigCore.read(SEASON_RESET_TOKEN_SECONDS);
    }
}
