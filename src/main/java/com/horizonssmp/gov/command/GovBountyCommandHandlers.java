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
import com.horizonssmp.gov.server.GovSavedData;
import com.horizonssmp.gov.server.BountyService;
import com.horizonssmp.gov.server.GovTargets;
import com.horizonssmp.gov.server.JobService;
import com.horizonssmp.gov.server.PersonService;
import com.horizonssmp.gov.server.Sanitizer;
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

public final class GovBountyCommandHandlers {
    private GovBountyCommandHandlers() {}

    static int bountyPlace(final CommandContext<CommandSourceStack> context) {
        final CommandSourceStack source = context.getSource();
        final ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Bounty placement requires a player."));
            return 0;
        }
        final String targetName = StringArgumentType.getString(context, "player");
        final int amount = IntegerArgumentType.getInteger(context, "amount");
        final GovSavedData data = GovSavedData.get(player.getServer());
        final GovTargets.Resolved target = GovTargets.resolve(player.getServer(), data, targetName);
        if (target == null) {
            source.sendFailure(Component.literal("No player named '" + targetName + "'."));
            return 0;
        }
        final ServerPlayer targetPlayer = player.getServer().getPlayerList().getPlayer(target.uuid());
        final String result = BountyService.place(player, targetPlayer, target.uuid(), target.name(), amount);
        source.sendSuccess(() -> Component.literal(result), false);
        return 1;
    }

    static int bountyList(final CommandContext<CommandSourceStack> context, final String targetName) {
        final CommandSourceStack source = context.getSource();
        if (source.getServer() == null) {
            source.sendFailure(Component.literal("No server.")); 
            return 0;
        }
        final GovSavedData data = GovSavedData.get(source.getServer());
        UUID filter = null;
        if (targetName != null && !targetName.isBlank()) {
            final GovTargets.Resolved target = GovTargets.resolve(source.getServer(), data, targetName);
            if (target == null) {
                source.sendFailure(Component.literal("No player named '" + targetName + "'."));
                return 0;
            }
            filter = target.uuid();
        }
        final List<BountyRecord> bounties = BountyService.listActive(data, filter);
        if (bounties.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No active bounties."), false);
            return 1;
        }
        final StringBuilder builder = new StringBuilder("Active bounties:");
        for (final BountyRecord bounty : bounties) {
            builder.append('\n')
                    .append(bounty.targetName()).append(" - ").append(bounty.amount())
                    .append(" gold (poster: ").append(bounty.posterName()).append(')');
        }
        source.sendSuccess(() -> Component.literal(builder.toString()), false);
        return 1;
    }

    static int bountyCancel(final CommandContext<CommandSourceStack> context) {
        final CommandSourceStack source = context.getSource();
        final ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Bounty cancellation requires a player."));
            return 0;
        }
        final String bountyId = StringArgumentType.getString(context, "bounty");
        final String result = BountyService.cancel(player, bountyId);
        source.sendSuccess(() -> Component.literal(result), false);
        return 1;
    }
    public static void register(final LiteralArgumentBuilder<CommandSourceStack> root) {
                root.then(Commands.literal("bounty")
                        .executes(context -> {
                            context.getSource().sendSuccess(
                                    () -> Component.literal("Usage: /gov bounty place <player> <amount> | list [player] | cancel <bounty>"), false);
                            return 1;
                        })
                        .then(Commands.literal("place")
                                .then(Commands.argument("player", StringArgumentType.word()).suggests(GovSuggestions.PLAYERS)
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                                .executes(GovBountyCommandHandlers::bountyPlace))))
                        .then(Commands.literal("list")
                                .executes(context -> bountyList(context, null))
                                .then(Commands.argument("player", StringArgumentType.word()).suggests(GovSuggestions.PLAYERS)
                                        .executes(context -> bountyList(context,
                                                StringArgumentType.getString(context, "player")))))
                        .then(Commands.literal("cancel")
                                .then(Commands.argument("bounty", StringArgumentType.word()).suggests(GovSuggestions.BOUNTIES)
                                        .executes(GovBountyCommandHandlers::bountyCancel))));
    }
}
