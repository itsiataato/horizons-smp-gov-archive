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

public final class GovAreaCommandHandlers {
    private GovAreaCommandHandlers() {}

    static int areaPermitList(final CommandContext<CommandSourceStack> context) {
        final CommandSourceStack source = context.getSource();
        final GovSavedData data = GovSavedData.get(source.getServer());
        final WeaponService.AreaPage page = WeaponService.areaPage(
                data, 0, System.currentTimeMillis());
        GovCommand.send(context, "Explosion-area permits: " + page.total() + " total");
        if (page.rows().isEmpty()) {
            GovCommand.send(context, "  (none)");
            return 1;
        }
        if (page.total() > page.rows().size()) {
            GovCommand.send(context, "  showing " + page.rows().size() + " of " + page.total()
                    + "; the Weapons tab pages through the rest.");
        }
        for (final AreaPermitRecord permit : page.rows()) {
            GovCommand.send(context, "  " + permit.id() + "  " + permit.dimension()
                    + "  " + permit.centerText() + "  radius " + permit.radius()
                    + "  " + permit.stateAt(System.currentTimeMillis())
                    + "  until " + GovAuditCommandHandlers.when(permit.validUntil()));
            GovCommand.send(context, "      " + Sanitizer.singleLine(permit.reason(), Sanitizer.MAX_TEXT)
                    + "  by " + WeaponService.ownerName(data, permit.issuedBy()));
        }
        return 1;
    }

    static int areaPermitCreate(final CommandContext<CommandSourceStack> context) {
        final CommandSourceStack source = context.getSource();
        final String dimension = net.minecraft.commands.arguments.ResourceLocationArgument
                .getId(context, "dim").toString();
        final String x = StringArgumentType.getString(context, "x");
        final String y = StringArgumentType.getString(context, "y");
        final String z = StringArgumentType.getString(context, "z");
        final String radius = StringArgumentType.getString(context, "radius");
        final String minutes = StringArgumentType.getString(context, "minutes");
        final String reason = StringArgumentType.getString(context, "reason");
        final WeaponService.Outcome precheck = WeaponService.areaCreatePrecheck(
                dimension, x, y, z, radius, minutes, reason);
        if (precheck != null) {
            source.sendFailure(Component.literal(precheck.message()));
            return 0;
        }
        if (source.getPlayer() == null) {
            source.sendFailure(Component.literal(
                    "/gov area permit create can only be used by a player."));
            return 0;
        }
        return GovCommand.dispatch(context, "/gov area permit create", OpCode.AREA_PERMIT_CREATE,
                Map.of(WeaponKeys.KEY_DIMENSION, dimension,
                        WeaponKeys.KEY_X, x,
                        WeaponKeys.KEY_Y, y,
                        WeaponKeys.KEY_Z, z,
                        WeaponKeys.KEY_RADIUS, radius,
                        WeaponKeys.KEY_VALID_MINUTES, minutes,
                        WeaponKeys.KEY_REASON, reason));
    }

    static int areaPermitRevoke(final CommandContext<CommandSourceStack> context) {
        final CommandSourceStack source = context.getSource();
        final GovSavedData data = GovSavedData.get(source.getServer());
        final String id = Sanitizer.id(StringArgumentType.getString(context, "id"));
        final AreaPermitRecord permit = data.areaPermit(id);
        if (permit == null) {
            source.sendFailure(Component.literal(WeaponService.noSuchAreaMessage(id)));
            return 0;
        }
        final String reason = StringArgumentType.getString(context, "reason");
        if (Sanitizer.text(reason).isBlank()) {
            source.sendFailure(Component.literal("A revocation needs a recorded reason."));
            return 0;
        }
        if (source.getPlayer() == null) {
            source.sendFailure(Component.literal(
                    "/gov area permit revoke can only be used by a player."));
            return 0;
        }
        return GovCommand.dispatch(context, "/gov area permit revoke", OpCode.AREA_PERMIT_REVOKE,
                Map.of(WeaponKeys.KEY_AREA_ID, permit.id(), WeaponKeys.KEY_REASON, reason));
    }
}
