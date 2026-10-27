package com.horizonssmp.gov.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class GovConfigJobs {

    public static final ModConfigSpec.ConfigValue<Boolean> GOVWORKER_CAN_ASSIGN;
    public static final ModConfigSpec.ConfigValue<Boolean> GOVWORKER_CAN_WAIVE_FINES;
    public static final ModConfigSpec.ConfigValue<Boolean> OPS_GRANT_ALL_CAPABILITIES;
    public static final ModConfigSpec.ConfigValue<Integer> CONFIG_VERSION;

    static {
        GOVWORKER_CAN_ASSIGN = GovConfigCore.BUILDER.comment(
                        "What the built-in jobs are allowed to do.",
                        "",
                        "Whether the built-in govworker job may assign jobs.")
                .define("gov.jobs.govworkerCanAssign", true);
        GOVWORKER_CAN_WAIVE_FINES = GovConfigCore.BUILDER.comment(
                        "Reception override: whether a govworker at the Government Building reception",
                        "may clear a fine. Takes effect on reload without re-seeding jobs.")
                .define("gov.jobs.govworkerCanWaiveFines", true);

        OPS_GRANT_ALL_CAPABILITIES = GovConfigCore.BUILDER.comment(
                        "Whether vanilla server operators (ops.json) receive every Government",
                        "capability. Toggleable; when false, operators are treated like any other player.")
                .define("gov.ops.grantAllCapabilities", false);

        CONFIG_VERSION = GovConfigCore.BUILDER.comment(
                        "Config schema version. Older files are migrated automatically on load by",
                        "bumping this value and writing any missing keys with their defaults.")
                .defineInRange("gov.configVersion", GovConfig.CURRENT_CONFIG_VERSION, 0, 1000000);
    }

    private GovConfigJobs() {
    }

    public static boolean govworkerCanAssign() {
        return GovConfigCore.read(GOVWORKER_CAN_ASSIGN);
    }

    public static boolean govworkerCanWaiveFines() {
        return GovConfigCore.read(GOVWORKER_CAN_WAIVE_FINES);
    }

    public static boolean opsGrantAllCapabilities() {
        return GovConfigCore.read(OPS_GRANT_ALL_CAPABILITIES);
    }
}
