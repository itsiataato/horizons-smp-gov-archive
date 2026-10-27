package com.horizonssmp.gov.config;

import java.util.List;
import java.util.regex.Pattern;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The Gov server config, registered as {@code ModConfig.Type.SERVER}.
 *
 * <p>Facade over the section classes in this package. The fields and getters keep their original
 * public names so callers do not change; registration and implementation live in the helpers.
 */
public final class GovConfig {

    /** Current config schema version. */
    public static final int CURRENT_CONFIG_VERSION = 1;

    /** The built spec, registered against the mod container in {@code Gov}'s constructor. */
    public static final ModConfigSpec SPEC;

    // ---------------------------------------------------------------- gov.fine
    public static final ModConfigSpec.ConfigValue<String> CURRENCY_MODE = GovConfigFine.CURRENCY_MODE;
    public static final ModConfigSpec.ConfigValue<String> CUSTOM_ITEM = GovConfigFine.CUSTOM_ITEM;
    public static final ModConfigSpec.ConfigValue<Boolean> ALLOW_RESTITUTION = GovConfigFine.ALLOW_RESTITUTION;
    public static final ModConfigSpec.ConfigValue<Boolean> FINE_JOIN_REMINDER = GovConfigFine.FINE_JOIN_REMINDER;

    // ---------------------------------------------------------------- gov.jobs
    public static final ModConfigSpec.ConfigValue<Boolean> GOVWORKER_CAN_ASSIGN = GovConfigJobs.GOVWORKER_CAN_ASSIGN;
    public static final ModConfigSpec.ConfigValue<Boolean> GOVWORKER_CAN_WAIVE_FINES = GovConfigJobs.GOVWORKER_CAN_WAIVE_FINES;
    public static final ModConfigSpec.ConfigValue<Boolean> OPS_GRANT_ALL_CAPABILITIES = GovConfigJobs.OPS_GRANT_ALL_CAPABILITIES;
    public static final ModConfigSpec.ConfigValue<Integer> BOUNTY_MIN = GovConfigBounty.BOUNTY_MIN;
    public static final ModConfigSpec.ConfigValue<Integer> BOUNTY_MAX = GovConfigBounty.BOUNTY_MAX;
    public static final ModConfigSpec.ConfigValue<Integer> BOUNTY_PER_TARGET_CAP = GovConfigBounty.BOUNTY_PER_TARGET_CAP;
    public static final ModConfigSpec.ConfigValue<Integer> BOUNTY_PER_POSTER_CAP = GovConfigBounty.BOUNTY_PER_POSTER_CAP;
    public static final ModConfigSpec.ConfigValue<Integer> BOUNTY_EXPIRY_DAYS = GovConfigBounty.BOUNTY_EXPIRY_DAYS;
    public static final ModConfigSpec.ConfigValue<Integer> BOUNTY_KILLER_COOLDOWN_SECONDS = GovConfigBounty.BOUNTY_KILLER_COOLDOWN_SECONDS;
    public static final ModConfigSpec.ConfigValue<Boolean> BOUNTY_SAME_IP_BLOCK = GovConfigBounty.BOUNTY_SAME_IP_BLOCK;
    public static final ModConfigSpec.ConfigValue<Integer> CONFIG_VERSION = GovConfigJobs.CONFIG_VERSION;

    // ------------------------------------------------------------- gov.vehicle
    public static final ModConfigSpec.ConfigValue<Integer> IMPOUND_RELEASE_FEE_GOLD = GovConfigVehicle.IMPOUND_RELEASE_FEE_GOLD;
    public static final ModConfigSpec.ConfigValue<Boolean> REQUIRE_LICENSE_TO_REGISTER = GovConfigVehicle.REQUIRE_LICENSE_TO_REGISTER;
    public static final ModConfigSpec.ConfigValue<String> PLATE_REGEX = GovConfigVehicle.PLATE_REGEX;
    public static final String DEFAULT_PLATE_REGEX = GovConfigVehicle.DEFAULT_PLATE_REGEX;

    // ---------------------------------------------------------------- gov.jail
    public static final ModConfigSpec.ConfigValue<Integer> MAX_DETENTION_MINUTES_NO_WARRANT = GovConfigJail.MAX_DETENTION_MINUTES_NO_WARRANT;
    public static final ModConfigSpec.ConfigValue<Integer> MAX_DETENTION_MINUTES_WARRANT = GovConfigJail.MAX_DETENTION_MINUTES_WARRANT;
    public static final ModConfigSpec.ConfigValue<Boolean> NATIVE_CUFFS = GovConfigJail.NATIVE_CUFFS;
    public static final ModConfigSpec.ConfigValue<Boolean> NATIVE_FALLBACK = GovConfigJail.NATIVE_FALLBACK;
    public static final ModConfigSpec.ConfigValue<String> PLUGIN_COMMAND = GovConfigJail.PLUGIN_COMMAND;
    public static final ModConfigSpec.ConfigValue<Boolean> CELL_CONFIGURED = GovConfigJail.CELL_CONFIGURED;
    public static final ModConfigSpec.ConfigValue<String> CELL_DIMENSION = GovConfigJail.CELL_DIMENSION;
    public static final ModConfigSpec.ConfigValue<Integer> CELL_X = GovConfigJail.CELL_X;
    public static final ModConfigSpec.ConfigValue<Integer> CELL_Y = GovConfigJail.CELL_Y;
    public static final ModConfigSpec.ConfigValue<Integer> CELL_Z = GovConfigJail.CELL_Z;
    public static final ModConfigSpec.ConfigValue<Double> CELL_LEASH_RADIUS = GovConfigJail.CELL_LEASH_RADIUS;
    public static final ModConfigSpec.ConfigValue<String> CELL_REGION_TAG = GovConfigJail.CELL_REGION_TAG;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> CELLS = GovConfigJail.CELLS;

    // ----------------------------------------------------------- gov.reception
    public static final ModConfigSpec.ConfigValue<String> PAYMENT_POINT = GovConfigReception.PAYMENT_POINT;
    public static final ModConfigSpec.ConfigValue<Boolean> REQUIRE_RECEPTION = GovConfigReception.REQUIRE_RECEPTION;
    public static final ModConfigSpec.ConfigValue<Boolean> RECEPTION_CONFIGURED = GovConfigReception.RECEPTION_CONFIGURED;
    public static final ModConfigSpec.ConfigValue<String> RECEPTION_DIMENSION = GovConfigReception.RECEPTION_DIMENSION;
    public static final ModConfigSpec.ConfigValue<Integer> RECEPTION_X = GovConfigReception.RECEPTION_X;
    public static final ModConfigSpec.ConfigValue<Integer> RECEPTION_Y = GovConfigReception.RECEPTION_Y;
    public static final ModConfigSpec.ConfigValue<Integer> RECEPTION_Z = GovConfigReception.RECEPTION_Z;
    public static final ModConfigSpec.ConfigValue<Double> RECEPTION_RADIUS = GovConfigReception.RECEPTION_RADIUS;

    // ---------------------------------------------------------------- gov.scan
    public static final ModConfigSpec.ConfigValue<Integer> MAX_DISTANCE_BLOCKS = GovConfigScan.MAX_DISTANCE_BLOCKS;

    // -------------------------------------------------------------- gov.weapon
    public static final ModConfigSpec.ConfigValue<String> SEASON_END = GovConfigWeapon.SEASON_END;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> TOWN_CORES = GovConfigWeapon.TOWN_CORES;
    public static final ModConfigSpec.ConfigValue<Integer> ENFORCEMENT_MESSAGE_COOLDOWN_MS = GovConfigWeapon.ENFORCEMENT_MESSAGE_COOLDOWN_MS;
    public static final ModConfigSpec.ConfigValue<Integer> ENFORCEMENT_AUDIT_COOLDOWN_MS = GovConfigWeapon.ENFORCEMENT_AUDIT_COOLDOWN_MS;

    // ------------------------------------------------------------ gov.security
    public static final ModConfigSpec.ConfigValue<Integer> RATE_LIMIT_ACTIONS_PER_10S = GovConfigSecurity.RATE_LIMIT_ACTIONS_PER_10S;
    public static final ModConfigSpec.ConfigValue<Integer> MAX_REQUESTS_PER_TICK = GovConfigSecurity.MAX_REQUESTS_PER_TICK;

    // --------------------------------------------------------------- gov.season
    public static final ModConfigSpec.ConfigValue<Boolean> SEASON_CLEAR_BUSINESSES = GovConfigSeason.SEASON_CLEAR_BUSINESSES;
    public static final ModConfigSpec.ConfigValue<Integer> SEASON_RESET_TOKEN_SECONDS = GovConfigSeason.SEASON_RESET_TOKEN_SECONDS;

    // ---------------------------------------------------------------- gov.bank
    public static final ModConfigSpec.ConfigValue<Boolean> BANK_ENABLED = GovConfigBank.BANK_ENABLED;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> BANK_REGIONS = GovConfigBank.BANK_REGIONS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> TOWNHALL_REGIONS = GovConfigBank.TOWNHALL_REGIONS;
    public static final ModConfigSpec.ConfigValue<Integer> BANK_MAX_REGIONS = GovConfigBank.BANK_MAX_REGIONS;
    public static final ModConfigSpec.ConfigValue<String> CONTRACT_REVIEWER = GovConfigBank.CONTRACT_REVIEWER;

    // --------------------------------------------------------------- gov.store
    public static final ModConfigSpec.ConfigValue<Boolean> STORE_ENABLED = GovConfigStore.STORE_ENABLED;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> STORE_CATALOG = GovConfigStore.STORE_CATALOG;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> STORE_MARKET_FLOOR = GovConfigStore.STORE_MARKET_FLOOR;

    // ------------------------------------------------------------ gov.contract
    public static final ModConfigSpec.ConfigValue<Boolean> CONTRACT_ENABLED = GovConfigContract.CONTRACT_ENABLED;
    public static final ModConfigSpec.ConfigValue<Integer> CONTRACT_ACCEPT_DAYS = GovConfigContract.CONTRACT_ACCEPT_DAYS;
    public static final ModConfigSpec.ConfigValue<Integer> CONTRACT_MAX_CONCURRENT = GovConfigContract.CONTRACT_MAX_CONCURRENT;
    public static final ModConfigSpec.ConfigValue<Integer> CONTRACT_MAX_TOTAL = GovConfigContract.CONTRACT_MAX_TOTAL;

    // ------------------------------------------------------------- gov.payroll
    public static final ModConfigSpec.ConfigValue<Integer> PAYROLL_PER_PAYOUT = GovConfigPayroll.PAYROLL_PER_PAYOUT;
    public static final ModConfigSpec.ConfigValue<Integer> PAYROLL_RUN_CAP = GovConfigPayroll.PAYROLL_RUN_CAP;
    public static final ModConfigSpec.ConfigValue<Integer> PAYROLL_ARREARS_CAP = GovConfigPayroll.PAYROLL_ARREARS_CAP;
    public static final ModConfigSpec.ConfigValue<Integer> PAYROLL_GOV_WAGE = GovConfigPayroll.PAYROLL_GOV_WAGE;
    public static final ModConfigSpec.ConfigValue<Integer> PAYROLL_POLICE_WAGE = GovConfigPayroll.PAYROLL_POLICE_WAGE;
    public static final ModConfigSpec.ConfigValue<Integer> PAYROLL_DEFAULT_JOB_WAGE = GovConfigPayroll.PAYROLL_DEFAULT_JOB_WAGE;

    static {
        GovConfigCore.SPEC = GovConfigCore.BUILDER.build();
        SPEC = GovConfigCore.SPEC;
    }

    private GovConfig() {
    }

    // ------------------------------------------------------------------ getters

    public static String currencyMode() { return GovConfigFine.currencyMode(); }
    public static String customItem() { return GovConfigFine.customItem(); }
    public static boolean allowRestitution() { return GovConfigFine.allowRestitution(); }
    public static boolean fineJoinReminder() { return GovConfigFine.fineJoinReminder(); }

    public static boolean govworkerCanAssign() { return GovConfigJobs.govworkerCanAssign(); }
    public static boolean govworkerCanWaiveFines() { return GovConfigJobs.govworkerCanWaiveFines(); }
    public static boolean opsGrantAllCapabilities() { return GovConfigJobs.opsGrantAllCapabilities(); }

    public static int impoundReleaseFeeGold() { return GovConfigVehicle.impoundReleaseFeeGold(); }
    public static boolean requireLicenseToRegister() { return GovConfigVehicle.requireLicenseToRegister(); }
    public static String plateRegex() { return GovConfigVehicle.plateRegex(); }
    public static Pattern platePattern() { return GovConfigVehicle.platePattern(); }

    public static int maxDetentionMinutesNoWarrant() { return GovConfigJail.maxDetentionMinutesNoWarrant(); }
    public static int maxDetentionMinutesWithWarrant() { return GovConfigJail.maxDetentionMinutesWithWarrant(); }
    public static boolean nativeCuffs() { return GovConfigJail.nativeCuffs(); }
    public static boolean nativeFallback() { return GovConfigJail.nativeFallback(); }
    public static String jailPluginCommand() { return GovConfigJail.jailPluginCommand(); }
    public static boolean jailCellConfigured() { return GovConfigJail.jailCellConfigured(); }
    public static java.util.List<com.horizonssmp.gov.common.records.LocationRecord> jailCells() { return GovConfigJail.jailCells(); }
    public static String jailCellDimension() { return GovConfigJail.jailCellDimension(); }
    public static int jailCellX() { return GovConfigJail.jailCellX(); }
    public static int jailCellY() { return GovConfigJail.jailCellY(); }
    public static int jailCellZ() { return GovConfigJail.jailCellZ(); }
    public static double jailCellLeashRadius() { return GovConfigJail.jailCellLeashRadius(); }
    public static String cellRegionTag() { return GovConfigJail.cellRegionTag(); }

    public static String paymentPoint() { return GovConfigReception.paymentPoint(); }
    public static boolean requireReception() { return GovConfigReception.requireReception(); }
    public static boolean receptionConfigured() { return GovConfigReception.receptionConfigured(); }
    public static String receptionDimension() { return GovConfigReception.receptionDimension(); }
    public static int receptionX() { return GovConfigReception.receptionX(); }
    public static int receptionY() { return GovConfigReception.receptionY(); }
    public static int receptionZ() { return GovConfigReception.receptionZ(); }
    public static double receptionRadius() { return GovConfigReception.receptionRadius(); }

    public static int maxDistanceBlocks() { return GovConfigScan.maxDistanceBlocks(); }

    public static long seasonEndMillis() { return GovConfigWeapon.seasonEndMillis(); }
    public static java.util.List<com.horizonssmp.gov.common.TownCore> townCoreRegions() { return GovConfigWeapon.townCoreRegions(); }
    public static boolean townCoresConfigured() { return GovConfigWeapon.townCoresConfigured(); }
    public static int enforcementMessageCooldownMs() { return GovConfigWeapon.enforcementMessageCooldownMs(); }
    public static int enforcementAuditCooldownMs() { return GovConfigWeapon.enforcementAuditCooldownMs(); }

    public static int rateLimitActionsPer10s() { return GovConfigSecurity.rateLimitActionsPer10s(); }
    public static int maxRequestsPerTick() { return GovConfigSecurity.maxRequestsPerTick(); }

    public static boolean seasonClearBusinesses() { return GovConfigSeason.seasonClearBusinesses(); }
    public static int seasonResetTokenSeconds() { return GovConfigSeason.seasonResetTokenSeconds(); }

    public static boolean bankEnabled() { return GovConfigBank.bankEnabled(); }
    public static java.util.List<String> bankRegions() { return GovConfigBank.bankRegions(); }
    public static java.util.List<String> townhallRegions() { return GovConfigBank.townhallRegions(); }
    public static int bankMaxRegions() { return GovConfigBank.bankMaxRegions(); }
    public static String contractReviewer() { return GovConfigBank.contractReviewer(); }

    public static boolean storeEnabled() { return GovConfigStore.storeEnabled(); }
    public static java.util.List<String> storeCatalog() { return GovConfigStore.storeCatalog(); }
    public static java.util.List<String> storeMarketFloor() { return GovConfigStore.storeMarketFloor(); }

    public static boolean contractEnabled() { return GovConfigContract.contractEnabled(); }
    public static int contractAcceptDays() { return GovConfigContract.contractAcceptDays(); }
    public static int contractMaxConcurrent() { return GovConfigContract.contractMaxConcurrent(); }
    public static int contractMaxTotal() { return GovConfigContract.contractMaxTotal(); }

    public static int payrollPerPayout() { return GovConfigPayroll.payrollPerPayout(); }
    public static int payrollRunCap() { return GovConfigPayroll.payrollRunCap(); }
    public static int payrollArrearsCap() { return GovConfigPayroll.payrollArrearsCap(); }
    public static int payrollGovWage() { return GovConfigPayroll.payrollGovWage(); }
    public static int payrollPoliceWage() { return GovConfigPayroll.payrollPoliceWage(); }
    public static int payrollDefaultJobWage() { return GovConfigPayroll.payrollDefaultJobWage(); }
}
