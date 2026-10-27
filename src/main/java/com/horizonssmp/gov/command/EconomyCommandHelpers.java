package com.horizonssmp.gov.command;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.horizonssmp.gov.common.ActionResult;
import com.horizonssmp.gov.common.EconomyKeys;
import com.horizonssmp.gov.common.GovFields;
import com.horizonssmp.gov.common.OpCode;
import com.horizonssmp.gov.server.GovSavedData;
import com.horizonssmp.gov.server.GovTargets;
import com.horizonssmp.gov.server.PersonService;
import com.horizonssmp.gov.server.economy.AccountService;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Shared helpers for the economy command surface. Package-private utilities only.
 */
final class EconomyCommandHelpers {

    private EconomyCommandHelpers() {
    }

    static Map<String, String> read(
            final CommandContext<CommandSourceStack> context,
            final String command,
            final OpCode op,
            final Map<String, String> payload) {
        final ServerPlayer player = requirePlayer(context, command);
        if (player == null) {
            return null;
        }
        final ActionResult result = GovCommand.dispatchExternal(player, op, payload);
        if (!result.ok()) {
            context.getSource().sendFailure(Component.literal(result.errorText()));
            return null;
        }
        return GovFields.decode(result.delta());
    }

    static ServerPlayer requirePlayer(
            final CommandContext<CommandSourceStack> context, final String command) {
        final ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendFailure(
                    Component.literal(command + " can only be used by a player."));
        }
        return player;
    }

    static GovTargets.Resolved resolveTarget(
            final CommandContext<CommandSourceStack> context, final String raw) {
        final CommandSourceStack source = context.getSource();
        final GovSavedData data = GovSavedData.get(source.getServer());
        final GovTargets.Resolved target = GovTargets.resolve(source.getServer(), data, raw);
        if (target == null) {
            source.sendFailure(Component.literal(PersonService.noSuchPersonMessage(raw)));
        }
        return target;
    }

    static String accountRef(final CommandSourceStack source, final String raw) {
        final String value = raw == null ? "" : raw.strip();
        if (value.isEmpty()) {
            return "me";
        }
        if (!value.regionMatches(true, 0, AccountService.PLAYER_PREFIX, 0,
                AccountService.PLAYER_PREFIX.length())) {
            return value;
        }
        final String name = value.substring(AccountService.PLAYER_PREFIX.length()).strip();
        if (name.isEmpty()) {
            return null;
        }
        final Optional<UUID> parsed = GovTargets.parseUuid(name);
        if (parsed.isPresent()) {
            return AccountService.PLAYER_PREFIX + parsed.get();
        }
        final GovSavedData data = GovSavedData.get(source.getServer());
        final GovTargets.Resolved target = GovTargets.resolve(source.getServer(), data, name);
        return target == null ? null : AccountService.PLAYER_PREFIX + target.uuid();
    }

    static String nonce() {
        return UUID.randomUUID().toString();
    }

    static String arg(final CommandContext<CommandSourceStack> context, final String name) {
        return StringArgumentType.getString(context, name);
    }

    static int usage(final CommandContext<CommandSourceStack> context, final String text) {
        send(context, text);
        return 1;
    }

    static void send(final CommandContext<CommandSourceStack> context, final String line) {
        context.getSource().sendSuccess(() -> Component.literal(line), false);
    }

    static String value(final Map<String, String> fields, final String key) {
        return GovFields.value(fields, key);
    }

    static String pageText(
            final Map<String, String> fields, final String pageKey, final String pagesKey) {
        return (whole(fields.get(pageKey)) + 1) + " / " + Math.max(1, whole(fields.get(pagesKey)));
    }

    static int whole(final String raw) {
        try {
            return Integer.parseInt(raw == null ? "" : raw.strip());
        } catch (final NumberFormatException invalid) {
            return 0;
        }
    }

    static String when(final String raw) {
        try {
            final long at = Long.parseLong(raw == null ? "" : raw.strip());
            return at <= 0L ? "-" : Instant.ofEpochMilli(at).toString();
        } catch (final NumberFormatException invalid) {
            return "-";
        }
    }

    static String shortId(final String raw) {
        if (raw == null || raw.isBlank()) {
            return "-";
        }
        return raw.length() <= 8 ? raw : raw.substring(0, 8);
    }

    static String yesNo(final String raw) {
        return "true".equalsIgnoreCase(raw == null ? "" : raw.strip()) ? "yes" : "no";
    }
}
