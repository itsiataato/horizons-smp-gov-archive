package com.horizonssmp.gov.config;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class GovConfigVehicle {

    /** The default plate pattern, also used when the configured pattern cannot be compiled. */
    public static final String DEFAULT_PLATE_REGEX = "^[A-Z]{3}-?[0-9]{3,4}$";

    public static final ModConfigSpec.ConfigValue<Integer> IMPOUND_RELEASE_FEE_GOLD;
    public static final ModConfigSpec.ConfigValue<Boolean> REQUIRE_LICENSE_TO_REGISTER;
    public static final ModConfigSpec.ConfigValue<String> PLATE_REGEX;

    static {
        IMPOUND_RELEASE_FEE_GOLD = GovConfigCore.BUILDER.comment(
                        "Vehicle registration and impound.",
                        "",
                        "Gold ingots charged to release an impounded vehicle. The value in force when",
                        "a vehicle is impounded is copied onto that impound, so changing this never",
                        "rewrites a fee someone already paid.")
                .defineInRange("gov.vehicle.impoundReleaseFeeGold", 8, 0, 4096);
        REQUIRE_LICENSE_TO_REGISTER = GovConfigCore.BUILDER.comment(
                        "Whether a vehicle may be registered or transferred to an owner without a",
                        "valid licence for its class. Article 6.09 bans unlicensed driving; this is",
                        "the registry policy that supports it. Turn off for off-road/project vehicles.")
                .define("gov.vehicle.requireLicenseToRegister", true);
        PLATE_REGEX = GovConfigCore.BUILDER.comment(
                        "Regular expression a plate must match. Corrected back to the default if it",
                        "does not compile.")
                .define("gov.vehicle.plateRegex", DEFAULT_PLATE_REGEX, GovConfigVehicle::isCompilableRegex);
    }

    private GovConfigVehicle() {
    }

    public static int impoundReleaseFeeGold() {
        return GovConfigCore.read(IMPOUND_RELEASE_FEE_GOLD);
    }

    public static boolean requireLicenseToRegister() {
        return GovConfigCore.read(REQUIRE_LICENSE_TO_REGISTER);
    }

    public static String plateRegex() {
        return GovConfigCore.read(PLATE_REGEX);
    }

    public static Pattern platePattern() {
        final String text = plateRegex();
        try {
            return Pattern.compile(text);
        } catch (final PatternSyntaxException e) {
            return Pattern.compile(DEFAULT_PLATE_REGEX);
        }
    }

    private static boolean isCompilableRegex(final Object candidate) {
        if (!(candidate instanceof String text) || text.isBlank()) {
            return false;
        }
        try {
            Pattern.compile(text);
            return true;
        } catch (final PatternSyntaxException e) {
            return false;
        }
    }
}
