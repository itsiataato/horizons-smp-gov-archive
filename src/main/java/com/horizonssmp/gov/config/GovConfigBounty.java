package com.horizonssmp.gov.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class GovConfigBounty {

    public static final ModConfigSpec.ConfigValue<Integer> BOUNTY_MIN;
    public static final ModConfigSpec.ConfigValue<Integer> BOUNTY_MAX;
    public static final ModConfigSpec.ConfigValue<Integer> BOUNTY_PER_TARGET_CAP;
    public static final ModConfigSpec.ConfigValue<Integer> BOUNTY_PER_POSTER_CAP;
    public static final ModConfigSpec.ConfigValue<Integer> BOUNTY_EXPIRY_DAYS;
    public static final ModConfigSpec.ConfigValue<Integer> BOUNTY_KILLER_COOLDOWN_SECONDS;
    public static final ModConfigSpec.ConfigValue<Boolean> BOUNTY_SAME_IP_BLOCK;

    static {
        BOUNTY_MIN = GovConfigCore.BUILDER.comment("Minimum gold ingots for a bounty.")
                .defineInRange("gov.bounty.min", 10, 1, 4096);
        BOUNTY_MAX = GovConfigCore.BUILDER.comment("Maximum gold ingots for a single bounty.")
                .defineInRange("gov.bounty.max", 64, 1, 4096);
        BOUNTY_PER_TARGET_CAP = GovConfigCore.BUILDER.comment("Maximum active bounties on one target.")
                .defineInRange("gov.bounty.perTargetCap", 3, 1, 100);
        BOUNTY_PER_POSTER_CAP = GovConfigCore.BUILDER.comment("Maximum active bounties one poster may have.")
                .defineInRange("gov.bounty.perPosterCap", 5, 1, 100);
        BOUNTY_EXPIRY_DAYS = GovConfigCore.BUILDER.comment("Days before an unclaimed bounty expires.")
                .defineInRange("gov.bounty.expiryDays", 7, 1, 365);
        BOUNTY_KILLER_COOLDOWN_SECONDS = GovConfigCore.BUILDER.comment("Seconds before a killer may claim another bounty on the same target.")
                .defineInRange("gov.bounty.killerCooldownSeconds", 300, 0, 86400);
        BOUNTY_SAME_IP_BLOCK = GovConfigCore.BUILDER.comment("Block a bounty claim when killer and target share an IP.")
                .define("gov.bounty.sameIpBlock", true);
    }

    private GovConfigBounty() {
    }
}
