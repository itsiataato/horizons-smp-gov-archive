package com.horizonssmp.gov.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class GovConfigScan {

    public static final ModConfigSpec.ConfigValue<Integer> MAX_DISTANCE_BLOCKS;

    static {
        MAX_DISTANCE_BLOCKS = GovConfigCore.BUILDER.comment(
                        "Plate scanning.",
                        "",
                        "Maximum distance in blocks at which a plate may be scanned.")
                .defineInRange("gov.scan.maxDistanceBlocks", 6, 1, 64);
    }

    private GovConfigScan() {
    }

    public static int maxDistanceBlocks() {
        return GovConfigCore.read(MAX_DISTANCE_BLOCKS);
    }
}
