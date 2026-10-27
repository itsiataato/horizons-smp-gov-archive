package com.horizonssmp.gov.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class GovConfigPayroll {

    public static final ModConfigSpec.ConfigValue<Integer> PAYROLL_PER_PAYOUT;
    public static final ModConfigSpec.ConfigValue<Integer> PAYROLL_RUN_CAP;
    public static final ModConfigSpec.ConfigValue<Integer> PAYROLL_ARREARS_CAP;
    public static final ModConfigSpec.ConfigValue<Integer> PAYROLL_GOV_WAGE;
    public static final ModConfigSpec.ConfigValue<Integer> PAYROLL_POLICE_WAGE;
    public static final ModConfigSpec.ConfigValue<Integer> PAYROLL_DEFAULT_JOB_WAGE;

    static {
        PAYROLL_PER_PAYOUT = GovConfigCore.BUILDER.comment(
                        "Payroll.",
                        "",
                        "Hard per-employee ceiling for one payroll run, in gold ingots. Default 64.",
                        "Value and source: 64, from economy specification section 9.2 and",
                        "BusinessKeys.MAX_PAYROLL_GOLD.",
                        "Derivation: one run pays at most one stack per employee; arrears carry the",
                        "rest to later runs.",
                        "Failure both ways: a higher value would let one run drain a treasury; a lower",
                        "one lengthens every arrears tail.",
                        "Tolerance: the loader clamps to 1..64, the specification's hard cap; a paid",
                        "amount is copied onto the ledger entry, so a later edit never rewrites it.")
                .defineInRange("gov.payroll.perPayout", 64, 1, 64);
        PAYROLL_RUN_CAP = GovConfigCore.BUILDER.comment(
                        "Gross ceiling for one payroll run, in gold ingots. Default 1024.",
                        "Value and source: 1,024, from economy specification section 9.2.",
                        "Derivation: 16 employees at the 64-ingot per-payout cap.",
                        "Failure both ways: too low refuses a legitimate full roster; too high lets",
                        "one run commit an unbounded total.",
                        "Tolerance: a run over the cap is refused, not truncated; the loader clamps to",
                        "1..1024, the specification's cap.")
                .defineInRange("gov.payroll.runCap", 1024, 1, 1024);
        PAYROLL_ARREARS_CAP = GovConfigCore.BUILDER.comment(
                        "Ceiling on the unpaid remainder carried by one position or contract.",
                        "Default 640.",
                        "Value and source: 640, from economy specification section 9.2: ten runs at",
                        "the 64-ingot per-payout cap.",
                        "Derivation: arrears are paid at 64 per run, so ten runs is the longest tail",
                        "the specification allows before an edit is refused.",
                        "Failure both ways: a larger cap promises gold the schedule cannot deliver; a",
                        "smaller one refuses a lawful wage edit.",
                        "Tolerance: arrears above the effective cap are refused at contract/position",
                        "edit time, never silently dropped; the loader clamps to 0..640.")
                .defineInRange("gov.payroll.arrearsCap", 640, 0, 640);
        PAYROLL_GOV_WAGE = GovConfigCore.BUILDER.comment(
                        "Government payroll wage.",
                        "",
                        "Daily wage for one Government employee in the gov payroll scope.",
                        "Default 16.",
                        "Value and source: 16, the default chosen for this build; section 9.2",
                        "fixes the 64-ingot per-payout ceiling, not the base wage.",
                        "Derivation: one quarter of the ceiling, a round rate that leaves room",
                        "for a later wage rise without changing the hard cap.",
                        "Failure both ways: too low makes a Government post worthless; too high",
                        "drains the treasury in one run.",
                        "Tolerance: the loader clamps to 0..64; 0 disables the wage; a paid amount",
                        "is copied onto the ledger line, so a later edit never rewrites it.")
                .defineInRange("gov.payroll.govWage", 16, 0, 64);
        PAYROLL_POLICE_WAGE = GovConfigCore.BUILDER.comment(
                        "Police payroll wage.",
                        "",
                        "Daily wage for one Police employee in the police payroll scope.",
                        "Default 16.",
                        "Value and source: 16, the default chosen for this build, at parity with",
                        "the Government rate; neither public payroll is taxed.",
                        "Derivation: parity keeps the Government subsidy to Police predictable,",
                        "because the Police account has no other funding source.",
                        "Failure both ways: a lower rate creates a two-tier public service; a",
                        "higher one makes Police payroll outrun its only funding source.",
                        "Tolerance: the loader clamps to 0..64; 0 disables the wage; a paid amount",
                        "is copied onto the ledger line, so a later edit never rewrites it.")
                .defineInRange("gov.payroll.policeWage", 16, 0, 64);
        PAYROLL_DEFAULT_JOB_WAGE = GovConfigCore.BUILDER.comment(
                        "Fallback payroll wage.",
                        "",
                        "Daily wage for a payroll job that has no rate of its own.",
                        "Default 8.",
                        "Value and source: 8, the default chosen for this build; section 9.2",
                        "defines the 64-ingot ceiling but names no job rate.",
                        "Derivation: half the public-service rate, so a fallback can never pay",
                        "more than the two departments it sits beside.",
                        "Failure both ways: too low makes generic work pointless; too high lets a",
                        "missing mapping quietly outpay a real department.",
                        "Tolerance: the loader clamps to 0..64; 0 disables fallback pay; only a",
                        "job without a Government or Police rate reaches this value.")
                .defineInRange("gov.payroll.defaultJobWage", 8, 0, 64);
    }

    private GovConfigPayroll() {
    }

    public static int payrollPerPayout() {
        return GovConfigCore.read(PAYROLL_PER_PAYOUT);
    }

    public static int payrollRunCap() {
        return GovConfigCore.read(PAYROLL_RUN_CAP);
    }

    public static int payrollArrearsCap() {
        return GovConfigCore.read(PAYROLL_ARREARS_CAP);
    }

    public static int payrollGovWage() {
        return GovConfigCore.read(PAYROLL_GOV_WAGE);
    }

    public static int payrollPoliceWage() {
        return GovConfigCore.read(PAYROLL_POLICE_WAGE);
    }

    public static int payrollDefaultJobWage() {
        return GovConfigCore.read(PAYROLL_DEFAULT_JOB_WAGE);
    }
}
