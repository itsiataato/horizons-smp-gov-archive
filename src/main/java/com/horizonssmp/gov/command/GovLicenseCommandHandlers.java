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

public final class GovLicenseCommandHandlers {
    private GovLicenseCommandHandlers() {}

    static int licenseIssue(final CommandContext<CommandSourceStack> context) {
        final CommandSourceStack source = context.getSource();
        final String player = StringArgumentType.getString(context, "player");
        final String rawClass = StringArgumentType.getString(context, "class");
        final LicenseClass licenseClass = LicenseClass.parse(rawClass);
        if (licenseClass == null) {
            source.sendFailure(Component.literal(
                    "Unknown licence class. Use one of: " + LicenseClass.ids() + "."));
            return 0;
        }
        final ServerPlayer actor = source.getPlayer();
        if (actor == null) {
            source.sendFailure(Component.literal(
                    "/gov license issue can only be used by a player."));
            return 0;
        }
        final GovSavedData data = GovSavedData.get(source.getServer());
        final GovTargets.Resolved target = GovTargets.resolve(source.getServer(), data, player);
        if (target == null) {
            source.sendFailure(Component.literal(PersonService.noSuchPersonMessage(player)));
            return 0;
        }
        return GovCommand.dispatch(context, "/gov license issue", OpCode.LICENSE_ISSUE,
                Map.of("target", player, LicenseKeys.KEY_CLASS, licenseClass.id()));
    }

    static int licenseRevoke(final CommandContext<CommandSourceStack> context) {
        final CommandSourceStack source = context.getSource();
        final String player = StringArgumentType.getString(context, "player");
        final String rawClass = StringArgumentType.getString(context, "class");
        final String reason = StringArgumentType.getString(context, "reason");
        final LicenseClass licenseClass = LicenseClass.parse(rawClass);
        if (licenseClass == null) {
            source.sendFailure(Component.literal(
                    "Unknown licence class. Use one of: " + LicenseClass.ids() + "."));
            return 0;
        }
        if (Sanitizer.text(reason).isBlank()) {
            source.sendFailure(Component.literal("A revocation needs a recorded reason."));
            return 0;
        }
        final ServerPlayer actor = source.getPlayer();
        if (actor == null) {
            source.sendFailure(Component.literal(
                    "/gov license revoke can only be used by a player."));
            return 0;
        }
        final GovSavedData data = GovSavedData.get(source.getServer());
        final GovTargets.Resolved target = GovTargets.resolve(source.getServer(), data, player);
        if (target == null) {
            source.sendFailure(Component.literal(PersonService.noSuchPersonMessage(player)));
            return 0;
        }
        final VehicleService.Outcome precheck =
                VehicleService.licensePrecheck(data, target.uuid(), licenseClass.id());
        if (precheck != null) {
            source.sendFailure(Component.literal(precheck.message()));
            return 0;
        }
        return GovCommand.dispatch(context, "/gov license revoke", OpCode.LICENSE_REVOKE,
                Map.of("target", player,
                        LicenseKeys.KEY_CLASS, licenseClass.id(),
                        LicenseKeys.KEY_REASON, reason));
    }

    static int licensePoints(final CommandContext<CommandSourceStack> context) {
        final CommandSourceStack source = context.getSource();
        final String player = StringArgumentType.getString(context, "player");
        final String rawClass = StringArgumentType.getString(context, "class");
        final String rawPoints = StringArgumentType.getString(context, "points");
        final String reason = StringArgumentType.getString(context, "reason");
        final LicenseClass licenseClass = LicenseClass.parse(rawClass);
        if (licenseClass == null) {
            source.sendFailure(Component.literal(
                    "Unknown licence class. Use one of: " + LicenseClass.ids() + "."));
            return 0;
        }
        final int points = VehicleService.parsePoints(rawPoints);
        if (!LicenseKeys.allowedPoints(points)) {
            source.sendFailure(Component.literal(
                    "Points must be 2 (minor), 4 (reckless) or 6 (impaired)."));
            return 0;
        }
        if (Sanitizer.text(reason).isBlank()) {
            source.sendFailure(Component.literal("Assessing points needs a recorded reason."));
            return 0;
        }
        final ServerPlayer actor = source.getPlayer();
        if (actor == null) {
            source.sendFailure(Component.literal(
                    "/gov license points can only be used by a player."));
            return 0;
        }
        final GovSavedData data = GovSavedData.get(source.getServer());
        final GovTargets.Resolved target = GovTargets.resolve(source.getServer(), data, player);
        if (target == null) {
            source.sendFailure(Component.literal(PersonService.noSuchPersonMessage(player)));
            return 0;
        }
        final VehicleService.Outcome precheck =
                VehicleService.licensePrecheck(data, target.uuid(), licenseClass.id());
        if (precheck != null) {
            source.sendFailure(Component.literal(precheck.message()));
            return 0;
        }
        return GovCommand.dispatch(context, "/gov license points", OpCode.LICENSE_POINTS,
                Map.of("target", player,
                        LicenseKeys.KEY_CLASS, licenseClass.id(),
                        LicenseKeys.KEY_POINTS, Integer.toString(points),
                        LicenseKeys.KEY_REASON, reason));
    }
}
