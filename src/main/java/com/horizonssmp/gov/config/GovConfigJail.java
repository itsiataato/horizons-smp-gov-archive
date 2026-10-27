package com.horizonssmp.gov.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.horizonssmp.gov.common.JailKeys;
import com.horizonssmp.gov.common.records.LocationRecord;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class GovConfigJail {

    public static final ModConfigSpec.ConfigValue<Integer> MAX_DETENTION_MINUTES_NO_WARRANT;
    public static final ModConfigSpec.ConfigValue<Integer> MAX_DETENTION_MINUTES_WARRANT;
    public static final ModConfigSpec.ConfigValue<Boolean> NATIVE_CUFFS;
    public static final ModConfigSpec.ConfigValue<Boolean> NATIVE_FALLBACK;
    public static final ModConfigSpec.ConfigValue<String> PLUGIN_COMMAND;
    public static final ModConfigSpec.ConfigValue<Boolean> CELL_CONFIGURED;
    public static final ModConfigSpec.ConfigValue<String> CELL_DIMENSION;
    public static final ModConfigSpec.ConfigValue<Integer> CELL_X;
    public static final ModConfigSpec.ConfigValue<Integer> CELL_Y;
    public static final ModConfigSpec.ConfigValue<Integer> CELL_Z;
    public static final ModConfigSpec.ConfigValue<Double> CELL_LEASH_RADIUS;
    public static final ModConfigSpec.ConfigValue<String> CELL_REGION_TAG;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> CELLS;

    static {
        MAX_DETENTION_MINUTES_NO_WARRANT = GovConfigCore.BUILDER.comment(
                        "Detention.",
                        "",
                        "Longest detention allowed without a warrant, in minutes. Article 9.12 caps",
                        "this at ten real minutes; the service enforces ten even if this file says",
                        "more. The loader clamps an out-of-range file value to this range at load.")
                .defineInRange("gov.jail.maxDetentionMinutesNoWarrant", 10, 1, 10);
        MAX_DETENTION_MINUTES_WARRANT = GovConfigCore.BUILDER.comment(
                        "Longest detention a warrant may authorize, in minutes.",
                        "",
                        "Value and source: 140 minutes, the warrant lifetime under Article 9.03",
                        "read with the Article 1.11 definition of an in-game day (7 x 20).",
                        "Derivation: the warrant's own life is the natural ceiling for a term",
                        "ordered under it. Failure both ways: a larger ceiling outlives the paper;",
                        "a tiny one makes a warrant no stronger than the ten-minute cap.",
                        "Tolerance: a longer request is refused, never clamped, and the value is",
                        "copied onto each record so a later config change cannot rewrite a term.")
                .defineInRange("gov.jail.maxDetentionMinutesWithWarrant",
                        JailKeys.DEFAULT_MAX_WARRANT_MINUTES, 1, JailKeys.HARD_MAX_WARRANT_MINUTES);
        NATIVE_CUFFS = GovConfigCore.BUILDER.comment(
                        "Whether native cuffs block walking, item use, attacks and block breaking.",
                        "Cuffs never grant search powers and never remove an item.")
                .define("gov.jail.nativeCuffs", true);
        NATIVE_FALLBACK = GovConfigCore.BUILDER.comment(
                        "Whether a configured native cell is used when no plugin jail command is set.",
                        "When false, or when no cell is configured, the record still gates release",
                        "and expiry; nothing physical happens.")
                .define("gov.jail.nativeFallback", true);
        PLUGIN_COMMAND = GovConfigCore.BUILDER.comment(
                        "Plugin jail command hook. Blank means no plugin jail.",
                        "Placeholders: {action}, {player}, {uuid}, {minutes}, {reason}, {id}.",
                        "When set, this wins over the native fallback. A command whose root is not",
                        "registered is treated as absent; the record still gates release and expiry.")
                .define("gov.jail.pluginCommand", "");
        CELL_CONFIGURED = GovConfigCore.BUILDER.comment(
                        "The native cell. Default false, deliberately: a default coordinate is not a",
                        "jail. Until an administrator enables this and sets the position, the native",
                        "fallback is inert.")
                .define("gov.jail.cellConfigured", false);
        CELL_DIMENSION = GovConfigCore.BUILDER.comment(
                        "The native cell's dimension id, for example minecraft:overworld.")
                .define("gov.jail.cellDimension", "minecraft:overworld");
        CELL_X = GovConfigCore.BUILDER.comment("The native cell's block X coordinate.")
                .defineInRange("gov.jail.cellX", 0, -30_000_000, 30_000_000);
        CELL_Y = GovConfigCore.BUILDER.comment("The native cell's block Y coordinate.")
                .defineInRange("gov.jail.cellY", 64, -2048, 2048);
        CELL_Z = GovConfigCore.BUILDER.comment("The native cell's block Z coordinate.")
                .defineInRange("gov.jail.cellZ", 0, -30_000_000, 30_000_000);
        CELL_LEASH_RADIUS = GovConfigCore.BUILDER.comment(
                        "Native cell leash radius in blocks. A detained player outside it is",
                        "teleported back on the coarse server tick.")
                .defineInRange("gov.jail.cellLeashRadius", 12.0d, 1.0d, 128.0d);
        CELL_REGION_TAG = GovConfigCore.BUILDER.comment(
                        "Region tag identifying a legal cell.")
                .define("gov.jail.cellRegionTag", "gov_jail_cell");
        CELLS = GovConfigCore.BUILDER.comment(
                        "Multiple native cells. Each entry is dimension:x,y,z, for example "
                                + "minecraft:overworld:100,64,200. When this list is non-empty it "
                                + "overrides the single gov.jail.cell* settings.")
                .defineListAllowEmpty("gov.jail.cells", List.of(), () -> "",
                        candidate -> candidate instanceof String text && !text.isBlank());
    }

    private GovConfigJail() {
    }

    public static int maxDetentionMinutesNoWarrant() {
        return Math.min(JailKeys.HARD_MAX_NO_WARRANT_MINUTES, GovConfigCore.read(MAX_DETENTION_MINUTES_NO_WARRANT));
    }

    public static int maxDetentionMinutesWithWarrant() {
        return Math.min(JailKeys.HARD_MAX_WARRANT_MINUTES, GovConfigCore.read(MAX_DETENTION_MINUTES_WARRANT));
    }

    public static boolean nativeCuffs() {
        return GovConfigCore.read(NATIVE_CUFFS);
    }

    public static boolean nativeFallback() {
        return GovConfigCore.read(NATIVE_FALLBACK);
    }

    public static String jailPluginCommand() {
        return GovConfigCore.read(PLUGIN_COMMAND);
    }

    public static boolean jailCellConfigured() {
        return !jailCells().isEmpty();
    }

    public static List<LocationRecord> jailCells() {
        final List<LocationRecord> result = new ArrayList<>();
        for (final Object raw : GovConfigCore.read(CELLS)) {
            final LocationRecord parsed = parseJailCell(String.valueOf(raw));
            if (parsed != null) {
                result.add(parsed);
            }
        }
        // The single-cell fields are legacy convenience only. They must never
        // override an explicit, possibly empty, cells list: an empty list means
        // "no native cells", not "use the default coordinate".
        // (No fallback here.)
        return List.copyOf(result);
    }

    public static String jailCellDimension() {
        return GovConfigCore.read(CELL_DIMENSION);
    }

    public static int jailCellX() {
        return GovConfigCore.read(CELL_X);
    }

    public static int jailCellY() {
        return GovConfigCore.read(CELL_Y);
    }

    public static int jailCellZ() {
        return GovConfigCore.read(CELL_Z);
    }

    public static double jailCellLeashRadius() {
        return GovConfigCore.read(CELL_LEASH_RADIUS);
    }

    public static String cellRegionTag() {
        return GovConfigCore.read(CELL_REGION_TAG);
    }

    private static LocationRecord parseJailCell(final String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        final String[] fields = raw.trim().split(":", 3);
        if (fields.length != 3) {
            return null;
        }
        final String[] coords = fields[2].trim().split(",");
        if (coords.length != 3) {
            return null;
        }
        try {
            return new LocationRecord(
                    (fields[0] + ":" + fields[1]).toLowerCase(Locale.ROOT),
                    Integer.parseInt(coords[0].trim()),
                    Integer.parseInt(coords[1].trim()),
                    Integer.parseInt(coords[2].trim()));
        } catch (final NumberFormatException e) {
            return null;
        }
    }
}
