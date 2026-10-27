package com.horizonssmp.gov.config;

import java.util.List;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class GovConfigStore {

    public static final ModConfigSpec.ConfigValue<Boolean> STORE_ENABLED;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> STORE_CATALOG;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> STORE_MARKET_FLOOR;

    static {
        STORE_ENABLED = GovConfigCore.BUILDER.comment(
                        "Whether the government store is open. Default false (the E4 rollout flag).",
                        "Value and source: false, from the economy specification rollout table.",
                        "Derivation: the store spends real account gold, so it opens only after the",
                        "bank and the catalogue have been validated.",
                        "Failure both ways: opening it with an empty catalogue leaves a screen with",
                        "nothing to buy; leaving it closed while players expect it returns",
                        "ECONOMY_DISABLED.",
                        "Tolerance: gov.bank.enabled must also be true; a buy is refused when either",
                        "flag is off, and reads stay available.")
                .define("gov.store.enabled", false);
        STORE_CATALOG = GovConfigCore.BUILDER.comment(
                        "The store catalogue, one itemId,tier per entry, maximum 256 entries.",
                        "Tier is 1..4 and the price is tier gold ingots per stack.",
                        "",
                        "Value and source: empty, because section 7.2 forbids a built-in catalogue",
                        "or a fallback to the creative menu or the item registry.",
                        "Derivation: only a listed item is purchasable, so the store cannot become a",
                        "free-item exploit.",
                        "Failure both ways: an empty catalogue makes STORE_LIST empty; a forged",
                        "payload naming an unlisted item returns STORE_UNAVAILABLE.",
                        "Tolerance: blank entries are dropped by the loader; malformed non-blank",
                        "entries and a list over 256 fail startup, so a typo cannot silently remove",
                        "a product.")
                .defineListAllowEmpty("gov.store.catalog", List.of(), () -> "",
                        candidate -> candidate instanceof String text && !text.isBlank());
        STORE_MARKET_FLOOR = GovConfigCore.BUILDER.comment(
                        "The price floor per item, one itemId=price per entry.",
                        "Value and source: empty; section 7.2 makes the floor operator-maintained",
                        "because the mod has no market data feed.",
                        "Derivation: a store price must be at least the floor, the machine-checkable",
                        "form of price >= market.",
                        "Failure both ways: a missing floor makes startup fail rather than guess; a",
                        "floor above the tier price fails startup too.",
                        "Tolerance: a catalogue item whose floor is absent, a tier price below its",
                        "floor, and any gold-bearing item are startup failures (section 7.2).")
                .defineListAllowEmpty("gov.store.marketFloor", List.of(), () -> "",
                        candidate -> candidate instanceof String text && !text.isBlank());
    }

    private GovConfigStore() {
    }

    public static boolean storeEnabled() {
        return GovConfigCore.read(STORE_ENABLED);
    }

    public static List<String> storeCatalog() {
        return GovConfigCore.strings(STORE_CATALOG);
    }

    public static List<String> storeMarketFloor() {
        return GovConfigCore.strings(STORE_MARKET_FLOOR);
    }
}
