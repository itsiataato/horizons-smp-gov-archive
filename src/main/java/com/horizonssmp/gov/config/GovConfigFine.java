package com.horizonssmp.gov.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.horizonssmp.gov.common.records.GoldAmount;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class GovConfigFine {

    public static final ModConfigSpec.ConfigValue<String> CURRENCY_MODE;
    public static final ModConfigSpec.ConfigValue<String> CUSTOM_ITEM;
    public static final ModConfigSpec.ConfigValue<Boolean> ALLOW_RESTITUTION;
    public static final ModConfigSpec.ConfigValue<Boolean> FINE_JOIN_REMINDER;

    static {
        CURRENCY_MODE = GovConfigCore.BUILDER.comment(
                        "Fines. The currency of this server is physical gold ingots. The custodial",
                        "bank of economy specification section 2 keeps ledger claims denominated in",
                        "ingots; it mints no currency and is off by default (gov.bank.enabled).",
                        "",
                        "The unit fines are denominated in. Only GOLD_INGOT is accepted; any other",
                        "value in the file is corrected back to it at load.")
                .defineInList("gov.fine.currencyMode", GoldAmount.GOLD_INGOT,
                        allowedValues(GoldAmount.GOLD_INGOT));
        CUSTOM_ITEM = GovConfigCore.BUILDER.comment(
                        "Item id used if a custom currency mode is ever added. Unused in this build.")
                .define("gov.fine.customItem", "minecraft:gold_ingot");
        ALLOW_RESTITUTION = GovConfigCore.BUILDER.comment(
                        "Whether a fine may also order restitution to a victim.")
                .define("gov.fine.allowRestitution", true);
        FINE_JOIN_REMINDER = GovConfigCore.BUILDER.comment(
                        "Whether a joining player with unpaid fines gets a short chat reminder.")
                .define("gov.fine.joinReminder", true);
    }

    private GovConfigFine() {
    }

    public static String currencyMode() {
        return GovConfigCore.read(CURRENCY_MODE);
    }

    public static String customItem() {
        return GovConfigCore.read(CUSTOM_ITEM);
    }

    public static boolean allowRestitution() {
        return GovConfigCore.read(ALLOW_RESTITUTION);
    }

    public static boolean fineJoinReminder() {
        return GovConfigCore.read(FINE_JOIN_REMINDER);
    }

    private static List<String> allowedValues(final String... values) {
        return new ArrayList<>(Arrays.asList(values));
    }
}
