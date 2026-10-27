package com.horizonssmp.gov.config;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import com.horizonssmp.gov.common.TownCore;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class GovConfigWeapon {

    public static final ModConfigSpec.ConfigValue<String> SEASON_END;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> TOWN_CORES;
    public static final ModConfigSpec.ConfigValue<Integer> ENFORCEMENT_MESSAGE_COOLDOWN_MS;
    public static final ModConfigSpec.ConfigValue<Integer> ENFORCEMENT_AUDIT_COOLDOWN_MS;

    static {
        SEASON_END = GovConfigCore.BUILDER.comment(
                        "Weapons, permits and explosion areas.",
                        "",
                        "The season end as an ISO-8601 instant (2026-12-31T23:59:59Z) or date",
                        "(2026-12-31). A weapon, blasting or mining permit issued while this is set",
                        "expires at that moment; empty means permits are issued open-ended. A",
                        "malformed value is treated as unset.")
                .define("gov.weapon.seasonEnd", "");
        TOWN_CORES = GovConfigCore.BUILDER.comment(
                        "Town-core regions for Article 5.14, one entry per region:",
                        "  dimension:minX,minY,minZ:maxX,maxY,maxZ",
                        "for example minecraft:overworld:-200,0,-200:200,320,200",
                        "",
                        "Default empty: the mod does not know where a town is, and the town rule is",
                        "inert until a region is configured. In a configured core only a Class L",
                        "permit holder carrying a Class L handgun, or an on-duty Class G officer,",
                        "may carry a weapon. Malformed entries are ignored.")
                .defineListAllowEmpty("gov.weapon.townCores", List.of(), () -> "",
                        candidate -> candidate instanceof String text && !text.isBlank());
        ENFORCEMENT_MESSAGE_COOLDOWN_MS = GovConfigCore.BUILDER.comment(
                        "How long one player's repeated law-denial chat messages are suppressed,",
                        "in milliseconds. The first denial is always shown.")
                .defineInRange("gov.weapon.enforcementMessageCooldownMs", 1500, 0, 60_000);
        ENFORCEMENT_AUDIT_COOLDOWN_MS = GovConfigCore.BUILDER.comment(
                        "How often one player's law denials may write an audit line, in",
                        "milliseconds. A denial loop cannot fill the audit log.")
                .defineInRange("gov.weapon.enforcementAuditCooldownMs", 10_000, 0, 600_000);
    }

    private GovConfigWeapon() {
    }

    public static long seasonEndMillis() {
        final String raw = GovConfigCore.read(SEASON_END);
        if (raw == null || raw.isBlank()) {
            return 0L;
        }
        final String text = raw.trim();
        try {
            return Instant.parse(text).toEpochMilli();
        } catch (final DateTimeParseException ignored) {
            // A plain date is a common, useful form; read it as the end of that UTC day.
        }
        try {
            return LocalDate.parse(text).atTime(23, 59, 59).toInstant(ZoneOffset.UTC).toEpochMilli();
        } catch (final DateTimeParseException ignored) {
            return 0L;
        }
    }

    public static List<TownCore> townCoreRegions() {
        final List<String> raw = new ArrayList<>();
        for (final String entry : GovConfigCore.read(TOWN_CORES)) {
            if (entry != null) {
                raw.add(entry);
            }
        }
        return TownCore.parseAll(raw);
    }

    public static boolean townCoresConfigured() {
        return !townCoreRegions().isEmpty();
    }

    public static int enforcementMessageCooldownMs() {
        return GovConfigCore.read(ENFORCEMENT_MESSAGE_COOLDOWN_MS);
    }

    public static int enforcementAuditCooldownMs() {
        return GovConfigCore.read(ENFORCEMENT_AUDIT_COOLDOWN_MS);
    }
}
