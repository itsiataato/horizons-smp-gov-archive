package com.horizonssmp.gov.command;
import java.util.List;
import java.util.Map;
import com.horizonssmp.gov.common.JailKeys;
import com.horizonssmp.gov.common.OpCode;
import com.horizonssmp.gov.common.records.LocationRecord;
import com.horizonssmp.gov.server.DetentionService;
import com.horizonssmp.gov.server.GovSavedData;
import com.horizonssmp.gov.server.GovTargets;
import com.horizonssmp.gov.server.Sanitizer;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class GovJailMutationCommandHandlers {
    private GovJailMutationCommandHandlers() {}

    record DetainArgs(String warrantRef, String minutes, String reason) {
    }

    static int jailDetain(final CommandContext<CommandSourceStack> context,
            final String rest) {
        final String player = StringArgumentType.getString(context, "player");
        final String basis = StringArgumentType.getString(context, "basis");
        final DetainArgs args = GovJailMutationCommandHandlers.parseDetainArgs(basis, rest);
        final Map<String, String> payload = new java.util.LinkedHashMap<>();
        payload.put(GovTargets.KEY_TARGET, player);
        payload.put(JailKeys.KEY_BASIS, basis);
        payload.put(JailKeys.KEY_REASON, args.reason());
        payload.put(JailKeys.KEY_WARRANT_REF, args.warrantRef());
        payload.put(JailKeys.KEY_MINUTES, args.minutes());
        // Typing "consent" is itself the officer's explicit confirmation; the dashboard sends the
        // same key from its checkbox.
        payload.put(JailKeys.KEY_CONSENT_CONFIRMED,
                "consent".equalsIgnoreCase(basis.trim()) ? "true" : "false");
        return GovCommand.dispatch(context, "/gov jail detain", OpCode.JAIL_COMMIT, payload);
    }

    static int jailRelease(final CommandContext<CommandSourceStack> context,
            final String reason) {
        final CommandSourceStack source = context.getSource();
        final GovSavedData data = GovSavedData.get(source.getServer());
        final String raw = StringArgumentType.getString(context, "player");
        final GovJailCommandHandlers.RecordTarget target = GovJailCommandHandlers.recordTarget(source, data, raw);
        if (target == null) {
            source.sendFailure(Component.literal(
                    "There is no active detention for '" + Sanitizer.singleLine(raw, 32) + "'."));
            return 0;
        }
        final DetentionService.Outcome precheck =
                DetentionService.releasePrecheck(data, target.uuid(), target.name());
        if (!precheck.ok()) {
            source.sendFailure(Component.literal(precheck.message()));
            return 0;
        }
        return GovCommand.dispatch(context, "/gov jail release", OpCode.JAIL_RELEASE,
                Map.of(GovTargets.KEY_TARGET, target.name(), JailKeys.KEY_REASON, reason));
    }

    static int jailExtend(final CommandContext<CommandSourceStack> context) {
        return GovCommand.dispatch(context, "/gov jail extend", OpCode.JAIL_EXTEND,
                Map.of(
                        GovTargets.KEY_TARGET, StringArgumentType.getString(context, "player"),
                        JailKeys.KEY_MINUTES, StringArgumentType.getString(context, "minutes"),
                        JailKeys.KEY_REASON, StringArgumentType.getString(context, "reason")));
    }

    static int jailCell(final CommandContext<CommandSourceStack> context, final int cell) {
        return GovCommand.dispatch(context, "/gov jail cell", OpCode.JAIL_CELL,
                Map.of(
                        GovTargets.KEY_TARGET, StringArgumentType.getString(context, "player"),
                        JailKeys.KEY_CELL, Integer.toString(cell)));
    }

    static int jailNew(final CommandContext<CommandSourceStack> context) {
        final String name = StringArgumentType.getString(context, "name");
        final String where = StringArgumentType.getString(context, "where");
        final ServerPlayer player = context.getSource().getPlayer();
        if (player != null) {
            final String dimension = player.level().dimension().location().toString();
            final int x;
            final int y;
            final int z;
            if ("here".equalsIgnoreCase(where)) {
                x = (int) Math.floor(player.getX());
                y = (int) Math.floor(player.getY());
                z = (int) Math.floor(player.getZ());
            } else {
                final int[] coords = cellCoords(context.getSource(), where);
                if (coords == null) {
                    return 0;
                }
                x = coords[0];
                y = coords[1];
                z = coords[2];
            }
            return GovCommand.dispatch(context, "/gov jail new", OpCode.JAIL_CELL_CREATE,
                    Map.of(
                            JailKeys.KEY_CELL_NAME, name,
                            JailKeys.KEY_CELL_X, Integer.toString(x),
                            JailKeys.KEY_CELL_Y, Integer.toString(y),
                            JailKeys.KEY_CELL_Z, Integer.toString(z),
                            JailKeys.KEY_CELL_DIMENSION, dimension));
        }
        if (!context.getSource().hasPermission(2)) {
            context.getSource().sendFailure(Component.literal("You do not have permission to use /gov jail new."));
            return 0;
        }
        if ("here".equalsIgnoreCase(where)) {
            context.getSource().sendFailure(Component.literal("Use /gov jail new <name> <x,y,z> from console."));
            return 0;
        }
        final int[] coords = cellCoords(context.getSource(), where);
        if (coords == null) {
            return 0;
        }
        final LocationRecord loc = new LocationRecord("minecraft:overworld",
                coords[0], coords[1], coords[2]);
        final GovSavedData data = GovSavedData.get(context.getSource().getServer());
        if (data.addRuntimeCell(name, loc)) {
            context.getSource().sendSuccess(() -> Component.literal(
                    "Registered jail cell '" + Sanitizer.name(name) + "' at " + loc.dimension() + ":"
                            + loc.x() + "," + loc.y() + "," + loc.z() + "."), false);
            return 1;
        }
        context.getSource().sendFailure(Component.literal(
                "A jail cell named '" + Sanitizer.name(name) + "' already exists."));
        return 0;
    }

    static int jailDelete(final CommandContext<CommandSourceStack> context) {
        final String name = StringArgumentType.getString(context, "name");
        if (context.getSource().getPlayer() != null) {
            return GovCommand.dispatch(context, "/gov jail delete", OpCode.JAIL_CELL_DELETE,
                    Map.of(JailKeys.KEY_CELL_NAME, name));
        }
        if (!context.getSource().hasPermission(2)) {
            context.getSource().sendFailure(Component.literal("You do not have permission to use /gov jail delete."));
            return 0;
        }
        final GovSavedData data = GovSavedData.get(context.getSource().getServer());
        if (data.removeRuntimeCell(name)) {
            context.getSource().sendSuccess(() -> Component.literal(
                    "Deleted jail cell '" + Sanitizer.name(name) + "'."), false);
            return 1;
        }
        context.getSource().sendFailure(Component.literal(
                "No jail cell named '" + Sanitizer.name(name) + "' exists."));
        return 0;
    }

    static int cuff(final CommandContext<CommandSourceStack> context) {
        return GovCommand.dispatch(context, "/gov cuff", OpCode.CUFF,
                Map.of(GovTargets.KEY_TARGET,
                        StringArgumentType.getString(context, "player")));
    }

    static int uncuff(final CommandContext<CommandSourceStack> context) {
        return GovCommand.dispatch(context, "/gov uncuff", OpCode.UNCUFF,
                Map.of(GovTargets.KEY_TARGET,
                        StringArgumentType.getString(context, "player")));
    }

    static DetainArgs parseDetainArgs(final String basis, final String rest) {
        final String text = rest == null ? "" : rest.strip();
        if (basis != null && "warrant".equalsIgnoreCase(basis.trim())) {
            final List<String> tokens = tokens(text);
            final String warrantRef = tokens.isEmpty() ? "" : tokens.get(0);
            final String minutes = tokens.size() < 2 ? "" : tokens.get(1);
            final String reason = tokens.size() < 3
                    ? "" : String.join(" ", tokens.subList(2, tokens.size())).strip();
            return new DetainArgs(warrantRef, minutes, reason);
        }
        final List<String> tokens = tokens(text);
        if (!tokens.isEmpty() && GovFinePaymentCommandHandlers.isDigits(tokens.get(0))) {
            return new DetainArgs("", tokens.get(0),
                    String.join(" ", tokens.subList(1, tokens.size())).strip());
        }
        return new DetainArgs("", "", text);
    }

    /**
     * Whitespace tokens of already-stripped detain text; empty text yields no tokens.
     */
    private static List<String> tokens(final String text) {
        return text.isEmpty() ? List.of() : List.of(text.split("\\s+"));
    }

    /**
     * Parses {@code x,y,z} cell coordinates, reporting failures to the source. Returns the three
     * coordinates, or {@code null} after sending the matching failure (usage text for a wrong
     * part count, invalid-coordinates text for unreadable numbers). Both jailNew branches share
     * these exact messages.
     */
    private static int[] cellCoords(final CommandSourceStack source, final String where) {
        final String[] parts = where == null ? new String[0] : where.split(",");
        if (parts.length != 3) {
            source.sendFailure(Component.literal("Use /gov jail new <name> <here|x,y,z>."));
            return null;
        }
        try {
            return new int[]{
                    Integer.parseInt(parts[0].trim()),
                    Integer.parseInt(parts[1].trim()),
                    Integer.parseInt(parts[2].trim())};
        } catch (final NumberFormatException e) {
            source.sendFailure(Component.literal("Invalid coordinates: " + where));
            return null;
        }
    }

    static String remaining(final long millis) {
        return DetentionService.remainingText(millis);
    }
}
