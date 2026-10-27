package com.horizonssmp.gov.command;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.horizonssmp.gov.Gov;
import com.horizonssmp.gov.common.ActionResult;
import com.horizonssmp.gov.common.AuditKeys;
import com.horizonssmp.gov.common.BusinessKeys;
import com.horizonssmp.gov.common.FineKeys;
import com.horizonssmp.gov.common.GovCapabilities;
import com.horizonssmp.gov.common.GovCapability;
import com.horizonssmp.gov.common.GovExportSection;
import com.horizonssmp.gov.common.GovFields;
import com.horizonssmp.gov.common.JailKeys;
import com.horizonssmp.gov.common.records.LocationRecord;
import com.horizonssmp.gov.common.LicenseKeys;
import com.horizonssmp.gov.common.NoteKeys;
import com.horizonssmp.gov.common.OpCode;
import com.horizonssmp.gov.common.PersonKeys;
import com.horizonssmp.gov.common.VehicleKeys;
import com.horizonssmp.gov.common.WeaponKeys;
import com.horizonssmp.gov.common.enums.LicenseClass;
import com.horizonssmp.gov.common.enums.LicenseStatus;
import com.horizonssmp.gov.common.enums.PermitClass;
import com.horizonssmp.gov.common.enums.ScanResult;
import com.horizonssmp.gov.common.enums.WeaponCategory;
import com.horizonssmp.gov.common.enums.AuditOutcome;
import com.horizonssmp.gov.common.records.AreaPermitRecord;
import com.horizonssmp.gov.common.records.AuditEntry;
import com.horizonssmp.gov.common.records.BusinessRecord;
import com.horizonssmp.gov.common.records.FineRecord;
import com.horizonssmp.gov.common.records.JobRecord;
import com.horizonssmp.gov.common.records.LicenseRecord;
import com.horizonssmp.gov.common.records.NoteRecord;
import com.horizonssmp.gov.common.records.PermitRecord;
import com.horizonssmp.gov.common.records.PlayerRecord;
import com.horizonssmp.gov.common.records.BountyRecord;
import com.horizonssmp.gov.common.records.VehicleRecord;
import com.horizonssmp.gov.common.records.WeaponRecord;
import com.horizonssmp.gov.event.LawJoinHandler;
import com.horizonssmp.gov.law.LawIndex;
import com.horizonssmp.gov.network.GovNetwork;
import com.horizonssmp.gov.network.GovOpenScreenPayload;
import com.horizonssmp.gov.server.BusinessService;
import com.horizonssmp.gov.server.AuditService;
import com.horizonssmp.gov.server.DetentionService;
import com.horizonssmp.gov.server.FineService;
import com.horizonssmp.gov.server.GovActionDispatcher;
import com.horizonssmp.gov.server.GovExportService;
import com.horizonssmp.gov.server.GovHandlerSupport;
import com.horizonssmp.gov.server.GovSavedData;
import com.horizonssmp.gov.server.BountyService;
import com.horizonssmp.gov.server.GovTargets;
import com.horizonssmp.gov.server.JobService;
import com.horizonssmp.gov.server.PersonService;
import com.horizonssmp.gov.server.SeasonResetService;
import com.horizonssmp.gov.server.SelfCardService;
import com.horizonssmp.gov.server.VehicleService;
import com.horizonssmp.gov.server.WeaponClassifier;
import com.horizonssmp.gov.server.WeaponService;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class GovCommand {

    private GovCommand() {
    }

    public static void register(final RegisterCommandsEvent event) {
        final LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("gov");
        root.executes(context -> {
                    context.getSource().sendSuccess(
                            () -> Component.literal(
                                    "Usage: /gov law | /gov dashboard | /gov me | /gov person <name>"
                                            + " | /gov job <list|info|assign|unassign|create|delete|caps>"
                                            + " | /gov note <list|add|edit|delete>"
                                            + " | /gov fine <list|info|issue|pay|waive>"
                                            + " | /gov vehicle <list|info|register|scan|impound|release>"
                                            + " | /gov license <issue|revoke|points>"
                                            + " | /gov weapon <list|info|register|transfer|revoke|loss|classify|permit>"
                                            + " | /gov area permit <list|create|revoke>"
                                            + " | /gov business <list|info|register|close|suspend|employee|link|unlink|payroll>"
                                            + " | /gov jail <list|info|detain|release|extend|cell|new|delete>"
                                            + " | /gov cuff <player> | /gov uncuff <player>"
                                            + " | /gov audit <list|info|export>"
                                            + " | /gov admin <export|season>"
                                            + " | /gov bank <balance|history|transfer|withdraw>"
                                            + " | /gov payroll run <scope>"
                                           + " | /gov contract | /gov store | /gov bounty"),
                            false);
                    return 1;
                });
        root.then(Commands.literal("law").executes(GovCommand::giveLawAndOpen));
        root.then(Commands.literal("dashboard").executes(GovCommand::openDashboard));
        root.then(Commands.literal("me").executes(GovCommand::selfCard));
        GovBusinessCommandTree.register(root);
        GovJobCommandHandlers.register(root);
        GovBountyCommandHandlers.register(root);
        GovPersonCommandHandlers.register(root);
        GovNoteCommandHandlers.register(root);
        GovFineCommandTree.register(root);
        GovVehicleCommandTree.register(root);
        GovLicenseCommandTree.register(root);
        GovJailCommandTree.register(root);
        GovAuditCommandTree.register(root);
        EconomyCommands.register(event, root);
        event.getDispatcher().register(root);
        event.getDispatcher().register(Commands.literal("law").executes(GovCommand::giveLawAndOpen));
    }

    static int giveLawAndOpen(final CommandContext<CommandSourceStack> context) {
        final ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendFailure(
                    Component.literal("/gov law can only be used by a player."));
            return 0;
        }

        try {
            LawJoinHandler.giveBook(player);
            GovNetwork.openScreen(player, GovOpenScreenPayload.law());
            return 1;
        } catch (final RuntimeException e) {
            Gov.LOGGER.error(
                    "[gov] failed to give a /gov law book to {}",
                    player.getName().getString(), e);
            context.getSource().sendFailure(
                    Component.literal("Could not create the law book. See the server log."));
            return 0;
        }
    }

    static int openDashboard(final CommandContext<CommandSourceStack> context) {
        final ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendFailure(
                    Component.literal("/gov dashboard can only be used by a player."));
            return 0;
        }
        GovNetwork.openScreen(player, GovOpenScreenPayload.dashboard());
        return 1;
    }

    static int selfCard(final CommandContext<CommandSourceStack> context) {
        final ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendFailure(
                    Component.literal("/gov me can only be used by a player."));
            return 0;
        }
        final GovSavedData data = GovSavedData.get(player.getServer());
        final Map<String, String> fields =
                SelfCardService.fields(player, data, System.currentTimeMillis());
        final List<String> lines = SelfCardService.chatLines(fields);
        for (int i = 0; i < lines.size(); i++) {
            final Component line = Component.literal(lines.get(i))
                    .withStyle(i == 0
                            ? net.minecraft.ChatFormatting.WHITE
                            : net.minecraft.ChatFormatting.GRAY);
            player.sendSystemMessage(line);
        }
        return 1;
    }

    static int dispatch(
            final CommandContext<CommandSourceStack> context,
            final String command,
            final OpCode op,
            final Map<String, String> payload) {
        final ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendFailure(
                    Component.literal(command + " can only be used by a player."));
            return 0;
        }
        return reply(context, GovActionDispatcher.dispatch(player, op, payload));
    }

    static ActionResult dispatchExternal(
            final ServerPlayer player, final OpCode op, final Map<String, String> payload) {
        return GovActionDispatcher.dispatch(player, op, payload);
    }

    static int reply(
            final CommandContext<CommandSourceStack> context, final ActionResult result) {
        if (result.ok()) {
            send(context, result.delta().isBlank() ? "Done." : result.delta());
            return 1;
        }
        context.getSource().sendFailure(Component.literal(result.errorText()));
        return 0;
    }

    static void send(final CommandContext<CommandSourceStack> context, final String line) {
        context.getSource().sendSuccess(() -> Component.literal(line), false);
    }

    static JobRecord jobById(final GovSavedData data, final String raw) {
        return GovHandlerSupport.jobById(data, raw);
    }
}
