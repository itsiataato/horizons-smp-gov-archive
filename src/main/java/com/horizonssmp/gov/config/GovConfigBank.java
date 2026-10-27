package com.horizonssmp.gov.config;

import java.util.List;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class GovConfigBank {

    public static final ModConfigSpec.ConfigValue<Boolean> BANK_ENABLED;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> BANK_REGIONS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> TOWNHALL_REGIONS;
    public static final ModConfigSpec.ConfigValue<Integer> BANK_MAX_REGIONS;
    public static final ModConfigSpec.ConfigValue<String> CONTRACT_REVIEWER;

    static {
        BANK_ENABLED = GovConfigCore.BUILDER.comment(
                        "The economy.",
                        "",
                        "Whether the custodial bank is enabled. Default false (the E0 rollout flag).",
                        "Value and source: false, from the economy specification rollout table.",
                        "Derivation: a ledger that moves gold is opt-in, so a server that never turns",
                        "it on sees no behaviour change.",
                        "Failure both ways: enabling it before regions exist makes every deposit fail",
                        "REGION_REQUIRED; leaving it off while players expect a bank makes every",
                        "mutation return ECONOMY_DISABLED.",
                        "Tolerance: balance and history reads stay available while off, so a rollback",
                        "hides no ledger; the bank holds claims denominated in gold ingots and mints",
                        "no currency.")
                .define("gov.bank.enabled", false);
        BANK_REGIONS = GovConfigCore.BUILDER.comment(
                        "Regions that accept a deposit, a teller withdrawal, a transfer and a",
                        "contract post. One entry per region:",
                        "  dimension:minX,minY,minZ:maxX,maxY,maxZ",
                        "for example minecraft:overworld:-40,60,-40:40,90,40",
                        "",
                        "Value and source: empty, because section 5.1 defines no bank location and",
                        "the mod must not invent one.",
                        "Derivation: a region is the physical counter the law requires; a deposit",
                        "outside every bank or town hall is refused.",
                        "Failure both ways: a missing region makes every counter action fail",
                        "REGION_REQUIRED; an over-wide region turns the whole map into a bank.",
                        "Tolerance: a blank entry is dropped by the loader; a malformed non-blank",
                        "entry is kept so GovStartupCheck fails startup (section 5.2) instead of",
                        "silently turning the bank into nowhere. Bounds are inclusive; min/max are",
                        "normalised and the dimension is lower-cased by the parser.")
                .defineListAllowEmpty("gov.bank.bankRegions", List.of(), () -> "",
                        candidate -> candidate instanceof String text && !text.isBlank());
        TOWNHALL_REGIONS = GovConfigCore.BUILDER.comment(
                        "Regions that accept a deposit only, with the same syntax and the same",
                        "startup rule as gov.bank.bankRegions.",
                        "Value and source: empty, from section 5.1; a town hall is a deposit point,",
                        "not a counter.",
                        "Derivation: the law lets a citizen bank at a town hall while teller",
                        "withdrawals, transfers and contract posting stay at a bank.",
                        "Failure both ways: omitting a town hall forces travel to a bank; listing a",
                        "bank here grants only the weaker deposit authority at that spot.",
                        "Tolerance: overlap with bankRegions is allowed and a bank match wins the",
                        "feedback string; malformed non-blank entries fail startup.")
                .defineListAllowEmpty("gov.bank.townhallRegions", List.of(), () -> "",
                        candidate -> candidate instanceof String text && !text.isBlank());
        BANK_MAX_REGIONS = GovConfigCore.BUILDER.comment(
                        "Largest number of entries allowed in either region list. Default 16.",
                        "Value and source: 16, from economy specification section 5.1.",
                        "Derivation: a region check is a linear scan on every economy action, so the",
                        "list needs a bound an operator can reason about.",
                        "Failure both ways: too low refuses a legitimate town with many halls; too",
                        "high makes a duplicate typo expensive to notice.",
                        "Tolerance: the loader clamps to 1..64; a list longer than the effective value",
                        "fails startup (section 5.2), and byte-identical duplicate entries are a",
                        "startup failure too.")
                .defineInRange("gov.bank.maxRegions", 16, 1, 64);
        CONTRACT_REVIEWER = GovConfigCore.BUILDER.comment(
                        "The sole-government contract approver. Default Saf.",
                        "Value and source: Saf, from economy specification section 8.4.",
                        "Derivation: when the poster is the only holder of bank.manage, a second",
                        "named human must approve, or the poster could pay themselves.",
                        "Failure both ways: blank while the bank and contracts are enabled fails",
                        "startup (section 5.2); an unknown name makes approval return NO_LEGAL_BASIS",
                        "until that player is online.",
                        "Tolerance: the name is resolved server-side and the UUID is written to the",
                        "audit line; an approval is never rewritten by a later rename.")
                .define("gov.bank.contractReviewer", "Saf");
    }

    private GovConfigBank() {
    }

    public static boolean bankEnabled() {
        return GovConfigCore.read(BANK_ENABLED);
    }

    public static List<String> bankRegions() {
        return GovConfigCore.strings(BANK_REGIONS);
    }

    public static List<String> townhallRegions() {
        return GovConfigCore.strings(TOWNHALL_REGIONS);
    }

    public static int bankMaxRegions() {
        return GovConfigCore.read(BANK_MAX_REGIONS);
    }

    public static String contractReviewer() {
        return GovConfigCore.read(CONTRACT_REVIEWER);
    }
}
