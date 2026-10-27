package com.horizonssmp.gov.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class GovConfigContract {

    public static final ModConfigSpec.ConfigValue<Boolean> CONTRACT_ENABLED;
    public static final ModConfigSpec.ConfigValue<Integer> CONTRACT_ACCEPT_DAYS;
    public static final ModConfigSpec.ConfigValue<Integer> CONTRACT_MAX_CONCURRENT;
    public static final ModConfigSpec.ConfigValue<Integer> CONTRACT_MAX_TOTAL;

    static {
        CONTRACT_ENABLED = GovConfigCore.BUILDER.comment(
                        "Whether building contracts are open. Default false (the E5 rollout flag).",
                        "Value and source: false, from the economy specification rollout table.",
                        "Derivation: a contract moves gold into escrow before any work exists, so it",
                        "opens only after the bank and the reviewer rule are configured.",
                        "Failure both ways: opening it without a reviewer blocks every self-posted",
                        "approval; leaving it closed returns ECONOMY_DISABLED for every contract op.",
                        "Tolerance: gov.bank.enabled must also be true; the escrow record survives a",
                        "later disable so a rollback strands no gold.")
                .define("gov.contract.enabled", false);
        CONTRACT_ACCEPT_DAYS = GovConfigCore.BUILDER.comment(
                        "Days an accepted contract stays valid before it expires.",
                        "Value and source: 7, from economy specification section 8.3.",
                        "Derivation: the acceptance deadline is the week the specification gives a",
                        "posted contract, applied from the accept moment.",
                        "Failure both ways: a longer term lets escrow sit idle; a shorter one expires",
                        "work a builder has already started.",
                        "Tolerance: the deadline is copied onto the record at accept time, so a later",
                        "config change never re-dates an existing contract; the loader clamps to",
                        "1..30.")
                .defineInRange("gov.contract.acceptDays", 7, 1, 30);
        CONTRACT_MAX_CONCURRENT = GovConfigCore.BUILDER.comment(
                        "Server-wide non-terminal contract cap. Default 5.",
                        "Value and source: 5, from economy specification section 8.2.",
                        "Derivation: escrow is a liability outside every balance, so the number of",
                        "open claims needs a bound.",
                        "Failure both ways: too low blocks a busy public works programme; too high",
                        "lets one poster tie up the whole Government balance in escrow.",
                        "Tolerance: the cap counts POSTED, ACCEPTED and SUBMITTED only; a sixth post",
                        "is refused with CONTRACT_LIMIT, never truncated.")
                .defineInRange("gov.contract.maxConcurrent", 5, 1, 64);
        CONTRACT_MAX_TOTAL = GovConfigCore.BUILDER.comment(
                        "Materials plus labour ceiling for one contract, in gold ingots.",
                        "Default 198.",
                        "Value and source: 198, from section 8.2: materials and labour are each",
                        "1..99, so 198 is the largest legal total.",
                        "Derivation: the ceiling is the arithmetic maximum of the two line items,",
                        "not an independent policy.",
                        "Failure both ways: a lower ceiling refuses a legal maximum contract; a",
                        "higher one lets escrow exceed the sum the law describes.",
                        "Tolerance: the loader clamps to 2..198; the service still refuses a line",
                        "item outside 1..99 and a total above the effective value.")
                .defineInRange("gov.contract.maxTotal", 198, 2, 198);
    }

    private GovConfigContract() {
    }

    public static boolean contractEnabled() {
        return GovConfigCore.read(CONTRACT_ENABLED);
    }

    public static int contractAcceptDays() {
        return GovConfigCore.read(CONTRACT_ACCEPT_DAYS);
    }

    public static int contractMaxConcurrent() {
        return GovConfigCore.read(CONTRACT_MAX_CONCURRENT);
    }

    public static int contractMaxTotal() {
        return GovConfigCore.read(CONTRACT_MAX_TOTAL);
    }
}
