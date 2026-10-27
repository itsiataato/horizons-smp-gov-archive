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

public final class GovWeaponPermitCommandHandlers {
    private GovWeaponPermitCommandHandlers() {}

    static int weaponClassify(
            final CommandContext<CommandSourceStack> context, final String itemId) {
        final CommandSourceStack source = context.getSource();
        final ServerPlayer player = source.getPlayer();
        final WeaponClassifier.Classification classification;
        if (itemId == null || itemId.isBlank()) {
            if (player == null) {
                source.sendFailure(Component.literal(
                        "Give an item id, for example /gov weapon classify minecraft:tnt."));
                return 0;
            }
            classification = WeaponClassifier.get().classify(player.getMainHandItem());
        } else {
            classification = WeaponService.classify(itemId, WeaponClassifier.get());
        }
        final Map<String, String> fields = WeaponService.classificationFields(classification);
        GovCommand.send(context, "Classification of " + GovAuditCommandHandlers.value(fields, WeaponKeys.ITEM_ID) + ": "
                + GovAuditCommandHandlers.value(fields, WeaponKeys.ITEM_CLASS)
                + "  (" + GovAuditCommandHandlers.value(fields, WeaponKeys.ITEM_CLASS_LABEL) + ")");
        GovCommand.send(context, "  Source: " + GovAuditCommandHandlers.value(fields, WeaponKeys.ITEM_SOURCE)
                + "  Permit: " + GovAuditCommandHandlers.value(fields, WeaponKeys.ITEM_PERMIT_CLASS)
                + "  Military: " + GovAuditCommandHandlers.value(fields, WeaponKeys.ITEM_MILITARY)
                + "  Explosive: " + GovAuditCommandHandlers.value(fields, WeaponKeys.ITEM_EXPLOSIVE)
                + "  Nuclear: " + GovAuditCommandHandlers.value(fields, WeaponKeys.ITEM_NUCLEAR));
        GovCommand.send(context, "  " + GovAuditCommandHandlers.value(fields, WeaponKeys.ITEM_EXPLANATION));
        return 1;
    }

    static int weaponPermitList(final CommandContext<CommandSourceStack> context) {
        final CommandSourceStack source = context.getSource();
        final GovSavedData data = GovSavedData.get(source.getServer());
        final Set<GovCapability> capabilities = GovAuditCommandHandlers.readCapabilities(source, data);
        final String player = StringArgumentType.getString(context, "player");
        final GovTargets.Resolved target = GovTargets.resolve(source.getServer(), data, player);
        if (target == null) {
            source.sendFailure(Component.literal(PersonService.noSuchPersonMessage(player)));
            return 0;
        }
        final ServerPlayer self = source.getPlayer();
        if (self != null && !WeaponService.canViewAll(capabilities)
                && !target.uuid().equals(self.getUUID())) {
            source.sendFailure(Component.literal(
                    "You can only read your own permits."));
            return 0;
        }
        final WeaponService.PermitPage page = WeaponService.permitsPage(
                data, target.uuid(), 0, System.currentTimeMillis());
        GovCommand.send(context, "Permits for " + target.name() + ": " + page.total());
        if (page.rows().isEmpty()) {
            GovCommand.send(context, "  (none)");
            return 1;
        }
        for (final PermitRecord permit : page.rows()) {
            GovCommand.send(context, "  " + permit.permitClass().label()
                    + "  " + permit.stateAt(System.currentTimeMillis()).name()
                    + "  issued " + GovAuditCommandHandlers.when(permit.issuedAt())
                    + (permit.expiresAt() > 0L ? "  expires " + GovAuditCommandHandlers.when(permit.expiresAt()) : "  no expiry")
                    + (permit.permitClass().militaryOnly() ? "  MILITARY" : ""));
            final String reason = !permit.revokeReason().isBlank() ? permit.revokeReason()
                    : !permit.suspendReason().isBlank() ? permit.suspendReason() : permit.note();
            if (!reason.isBlank()) {
                GovCommand.send(context, "      " + Sanitizer.singleLine(reason, Sanitizer.MAX_TEXT));
            }
        }
        return 1;
    }

    static int weaponPermitIssue(
            final CommandContext<CommandSourceStack> context, final String reason) {
        final CommandSourceStack source = context.getSource();
        final String player = StringArgumentType.getString(context, "player");
        final String permitClass = StringArgumentType.getString(context, "class");
        final WeaponService.Outcome precheck = WeaponService.permitIssuePrecheck(permitClass);
        if (precheck != null) {
            source.sendFailure(Component.literal(precheck.message()));
            return 0;
        }
        final ServerPlayer actor = source.getPlayer();
        if (actor == null) {
            source.sendFailure(Component.literal(
                    "/gov weapon permit issue can only be used by a player."));
            return 0;
        }
        final GovSavedData data = GovSavedData.get(source.getServer());
        final GovTargets.Resolved target = GovTargets.resolve(source.getServer(), data, player);
        if (target == null) {
            source.sendFailure(Component.literal(PersonService.noSuchPersonMessage(player)));
            return 0;
        }
        return GovCommand.dispatch(context, "/gov weapon permit issue", OpCode.WPN_PERMIT_ISSUE,
                Map.of("target", player,
                        WeaponKeys.KEY_PERMIT_CLASS, permitClass,
                        WeaponKeys.KEY_REASON, reason));
    }

    static int weaponPermitRevoke(final CommandContext<CommandSourceStack> context) {
        final CommandSourceStack source = context.getSource();
        final String player = StringArgumentType.getString(context, "player");
        final String permitClass = StringArgumentType.getString(context, "class");
        final String reason = StringArgumentType.getString(context, "reason");
        final PermitClass parsed = PermitClass.parse(permitClass);
        if (parsed == null) {
            source.sendFailure(Component.literal(
                    "Unknown permit class. Use one of: " + PermitClass.ids() + "."));
            return 0;
        }
        if (Sanitizer.text(reason).isBlank()) {
            source.sendFailure(Component.literal("A revocation needs a recorded reason."));
            return 0;
        }
        final ServerPlayer actor = source.getPlayer();
        if (actor == null) {
            source.sendFailure(Component.literal(
                    "/gov weapon permit revoke can only be used by a player."));
            return 0;
        }
        final GovSavedData data = GovSavedData.get(source.getServer());
        final GovTargets.Resolved target = GovTargets.resolve(source.getServer(), data, player);
        if (target == null) {
            source.sendFailure(Component.literal(PersonService.noSuchPersonMessage(player)));
            return 0;
        }
        final WeaponService.Outcome precheck =
                WeaponService.permitPrecheck(data, target.uuid(), parsed.id());
        if (precheck != null) {
            source.sendFailure(Component.literal(precheck.message()));
            return 0;
        }
        return GovCommand.dispatch(context, "/gov weapon permit revoke", OpCode.WPN_PERMIT_REVOKE,
                Map.of("target", player,
                        WeaponKeys.KEY_PERMIT_CLASS, parsed.id(),
                        WeaponKeys.KEY_REASON, reason));
    }

    static int weaponPermitSuspend(final CommandContext<CommandSourceStack> context) {
        final CommandSourceStack source = context.getSource();
        final String player = StringArgumentType.getString(context, "player");
        final String permitClass = StringArgumentType.getString(context, "class");
        final String minutes = StringArgumentType.getString(context, "minutes");
        final String reason = StringArgumentType.getString(context, "reason");
        final PermitClass parsed = PermitClass.parse(permitClass);
        if (parsed == null) {
            source.sendFailure(Component.literal(
                    "Unknown permit class. Use one of: " + PermitClass.ids() + "."));
            return 0;
        }
        final long parsedMinutes;
        try {
            parsedMinutes = Long.parseLong(minutes.trim());
        } catch (final NumberFormatException e) {
            source.sendFailure(Component.literal("Minutes must be a whole number."));
            return 0;
        }
        if (parsedMinutes <= 0L || parsedMinutes > WeaponService.MAX_SUSPENSION_MINUTES) {
            source.sendFailure(Component.literal(
                    "The suspension must be 1-" + WeaponService.MAX_SUSPENSION_MINUTES + " minutes."));
            return 0;
        }
        if (Sanitizer.text(reason).isBlank()) {
            source.sendFailure(Component.literal("A suspension needs a recorded reason."));
            return 0;
        }
        final ServerPlayer actor = source.getPlayer();
        if (actor == null) {
            source.sendFailure(Component.literal(
                    "/gov weapon permit suspend can only be used by a player."));
            return 0;
        }
        final GovSavedData data = GovSavedData.get(source.getServer());
        final GovTargets.Resolved target = GovTargets.resolve(source.getServer(), data, player);
        if (target == null) {
            source.sendFailure(Component.literal(PersonService.noSuchPersonMessage(player)));
            return 0;
        }
        final WeaponService.Outcome precheck =
                WeaponService.permitPrecheck(data, target.uuid(), parsed.id());
        if (precheck != null) {
            source.sendFailure(Component.literal(precheck.message()));
            return 0;
        }
        return GovCommand.dispatch(context, "/gov weapon permit suspend", OpCode.WPN_PERMIT_SUSPEND,
                Map.of("target", player,
                        WeaponKeys.KEY_PERMIT_CLASS, parsed.id(),
                        WeaponKeys.KEY_MINUTES, Long.toString(parsedMinutes),
                        WeaponKeys.KEY_REASON, reason));
    }
}
