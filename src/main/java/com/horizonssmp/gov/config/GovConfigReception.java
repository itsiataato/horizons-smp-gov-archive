package com.horizonssmp.gov.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class GovConfigReception {

    public static final ModConfigSpec.ConfigValue<String> PAYMENT_POINT;
    public static final ModConfigSpec.ConfigValue<Boolean> REQUIRE_RECEPTION;
    public static final ModConfigSpec.ConfigValue<Boolean> RECEPTION_CONFIGURED;
    public static final ModConfigSpec.ConfigValue<String> RECEPTION_DIMENSION;
    public static final ModConfigSpec.ConfigValue<Integer> RECEPTION_X;
    public static final ModConfigSpec.ConfigValue<Integer> RECEPTION_Y;
    public static final ModConfigSpec.ConfigValue<Integer> RECEPTION_Z;
    public static final ModConfigSpec.ConfigValue<Double> RECEPTION_RADIUS;

    static {
        PAYMENT_POINT = GovConfigCore.BUILDER.comment(
                        "Where fines and fees are paid.",
                        "",
                        "The single payment point. Recorded with every reception payment.")
                .define("gov.reception.paymentPoint", "GOVERNMENT_BUILDING_RECEPTION");
        REQUIRE_RECEPTION = GovConfigCore.BUILDER.comment(
                        "Whether a payment must be recorded as made at the reception.")
                .define("gov.reception.requireReception", true);
        RECEPTION_CONFIGURED = GovConfigCore.BUILDER.comment(
                        "Where the Government Building reception is.",
                        "",
                        "Whether the coordinates below are a real reception. Default false: until",
                        "an administrator enables this and sets the location, no self-payment can",
                        "be accepted, because a fine is paid in person and in gold.")
                .define("gov.reception.configured", false);
        RECEPTION_DIMENSION = GovConfigCore.BUILDER.comment(
                        "The dimension id of the reception, for example minecraft:overworld.")
                .define("gov.reception.dimension", "minecraft:overworld");
        RECEPTION_X = GovConfigCore.BUILDER.comment("The reception's block X coordinate.")
                .defineInRange("gov.reception.x", 0, -30_000_000, 30_000_000);
        RECEPTION_Y = GovConfigCore.BUILDER.comment("The reception's block Y coordinate.")
                .defineInRange("gov.reception.y", 64, -2048, 2048);
        RECEPTION_Z = GovConfigCore.BUILDER.comment("The reception's block Z coordinate.")
                .defineInRange("gov.reception.z", 0, -30_000_000, 30_000_000);
        RECEPTION_RADIUS = GovConfigCore.BUILDER.comment(
                        "The radius in blocks within which a payment counts as made at the",
                        "reception.")
                .defineInRange("gov.reception.radius", 8.0d, 1.0d, 128.0d);
    }

    private GovConfigReception() {
    }

    public static String paymentPoint() {
        return GovConfigCore.read(PAYMENT_POINT);
    }

    public static boolean requireReception() {
        return GovConfigCore.read(REQUIRE_RECEPTION);
    }

    public static boolean receptionConfigured() {
        return GovConfigCore.read(RECEPTION_CONFIGURED);
    }

    public static String receptionDimension() {
        return GovConfigCore.read(RECEPTION_DIMENSION);
    }

    public static int receptionX() {
        return GovConfigCore.read(RECEPTION_X);
    }

    public static int receptionY() {
        return GovConfigCore.read(RECEPTION_Y);
    }

    public static int receptionZ() {
        return GovConfigCore.read(RECEPTION_Z);
    }

    public static double receptionRadius() {
        return GovConfigCore.read(RECEPTION_RADIUS);
    }
}
