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

public final class GovVehicleMutationCommandHandlers {
    private GovVehicleMutationCommandHandlers() {}

    static int vehicleRegister(
            final CommandContext<CommandSourceStack> context, final String requestedOwner) {
        final CommandSourceStack source = context.getSource();
        final GovSavedData data = GovSavedData.get(source.getServer());
        final String plate = StringArgumentType.getString(context, "plate");
        final String vehicleClass = StringArgumentType.getString(context, "class");
        final VehicleService.Outcome precheck =
                VehicleService.registerPrecheck(data, plate, vehicleClass);
        if (precheck != null) {
            source.sendFailure(Component.literal(precheck.message()));
            return 0;
        }
        final ServerPlayer actor = source.getPlayer();
        if (actor == null) {
            source.sendFailure(Component.literal(
                    "/gov vehicle register can only be used by a player."));
            return 0;
        }
        final String target = requestedOwner == null || requestedOwner.isBlank()
                ? actor.getName().getString() : requestedOwner;
        return GovCommand.dispatch(context, "/gov vehicle register", OpCode.VEH_REGISTER,
                Map.of("target", target,
                        VehicleKeys.KEY_PLATE, plate,
                        VehicleKeys.KEY_CLASS, vehicleClass));
    }

    static int vehicleScan(final CommandContext<CommandSourceStack> context) {
        final CommandSourceStack source = context.getSource();
        final String plate = StringArgumentType.getString(context, "plate");
        final GovSavedData data = GovSavedData.get(source.getServer());
        final ServerPlayer actor = source.getPlayer();
        final VehicleService.Scan scan;
        if (actor == null) {
            // The console has no actor and no audit line to write; a console scan is a pure read.
            scan = VehicleService.scan(
                    data,
                    new VehicleService.Actor(null, "CONSOLE",
                            EnumSet.allOf(GovCapability.class)),
                    plate,
                    VehicleService.REGISTRY_ONLY,
                    System.currentTimeMillis(),
                    false);
        } else {
            final ActionResult result = GovActionDispatcher.dispatch(
                    actor, OpCode.PLATE_SCAN, Map.of(VehicleKeys.KEY_PLATE, plate));
            if (!result.ok()) {
                source.sendFailure(Component.literal(result.errorText()));
                return 0;
            }
            GovVehicleMutationCommandHandlers.printScan(context, com.horizonssmp.gov.common.GovFields.decode(result.delta()));
            return 1;
        }
        if (!scan.ok()) {
            source.sendFailure(Component.literal(scan.message()));
            return 0;
        }
        GovVehicleMutationCommandHandlers.printScan(context, VehicleService.scanFields(scan.view()));
        return 1;
    }

    static int vehicleImpound(final CommandContext<CommandSourceStack> context) {
        final CommandSourceStack source = context.getSource();
        final GovSavedData data = GovSavedData.get(source.getServer());
        final String plate = StringArgumentType.getString(context, "plate");
        final String reason = StringArgumentType.getString(context, "reason");
        final VehicleService.Outcome precheck =
                VehicleService.impoundPrecheck(data, plate, reason);
        if (precheck != null) {
            source.sendFailure(Component.literal(precheck.message()));
            return 0;
        }
        if (source.getPlayer() == null) {
            source.sendFailure(Component.literal(
                    "/gov vehicle impound can only be used by a player."));
            return 0;
        }
        return GovCommand.dispatch(context, "/gov vehicle impound", OpCode.VEH_IMPOUND,
                Map.of(VehicleKeys.KEY_PLATE, plate, VehicleKeys.KEY_REASON, reason));
    }

    static int vehicleRelease(final CommandContext<CommandSourceStack> context) {
        final CommandSourceStack source = context.getSource();
        final GovSavedData data = GovSavedData.get(source.getServer());
        final String plate = StringArgumentType.getString(context, "plate");
        final VehicleService.Outcome precheck = VehicleService.releasePrecheck(data, plate);
        if (precheck != null) {
            source.sendFailure(Component.literal(precheck.message()));
            return 0;
        }
        if (source.getPlayer() == null) {
            source.sendFailure(Component.literal(
                    "/gov vehicle release can only be used by a player."));
            return 0;
        }
        return GovCommand.dispatch(context, "/gov vehicle release", OpCode.VEH_RELEASE,
                Map.of(VehicleKeys.KEY_PLATE, plate));
    }

    static void printScan(
            final CommandContext<CommandSourceStack> context, final Map<String, String> fields) {
        final String plate = GovAuditCommandHandlers.value(fields, VehicleKeys.SCAN_PLATE);
        final String result = GovAuditCommandHandlers.value(fields, VehicleKeys.SCAN_RESULT);
        GovCommand.send(context, "Plate scan " + plate + ": " + result);
        if (ScanResult.UNREGISTERED.name().equals(result)) {
            GovCommand.send(context, "  No registry entry for that plate.");
            GovCommand.send(context, "  Source: " + GovAuditCommandHandlers.value(fields, VehicleKeys.SCAN_SOURCE));
            return;
        }
        GovCommand.send(context, "  Owner: " + GovAuditCommandHandlers.value(fields, VehicleKeys.SCAN_OWNER)
                + "  Class: " + GovAuditCommandHandlers.value(fields, VehicleKeys.SCAN_CLASS)
                + "  Status: " + GovAuditCommandHandlers.value(fields, VehicleKeys.SCAN_STATUS));
        GovCommand.send(context, "  Licence: " + GovAuditCommandHandlers.value(fields, VehicleKeys.SCAN_LICENSE_STATE)
                + "  Points: " + GovAuditCommandHandlers.value(fields, VehicleKeys.SCAN_LICENSE_POINTS));
        if ("true".equals(fields.get(VehicleKeys.SCAN_IMPOUND))) {
            GovCommand.send(context, "  Impounded: " + GovAuditCommandHandlers.value(fields, VehicleKeys.SCAN_IMPOUND_REASON));
        }
        GovCommand.send(context, "  Source: " + GovAuditCommandHandlers.value(fields, VehicleKeys.SCAN_SOURCE)
                + "  Scan: " + GovAuditCommandHandlers.value(fields, VehicleKeys.SCAN_ID));
    }
}
