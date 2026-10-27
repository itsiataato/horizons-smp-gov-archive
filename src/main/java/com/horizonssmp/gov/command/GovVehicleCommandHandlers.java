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

public final class GovVehicleCommandHandlers {
    private GovVehicleCommandHandlers() {}

    static int vehicleList(final CommandContext<CommandSourceStack> context, final String scope) {
        final CommandSourceStack source = context.getSource();
        final GovSavedData data = GovSavedData.get(source.getServer());
        final Set<GovCapability> capabilities = GovAuditCommandHandlers.readCapabilities(source, data);
        final ServerPlayer self = source.getPlayer();
        final boolean viewAll = VehicleService.canViewAll(capabilities);

        UUID filter = null;
        if (!"all".equalsIgnoreCase(scope)) {
            final GovTargets.Resolved target = GovTargets.resolve(source.getServer(), data, scope);
            if (target == null) {
                source.sendFailure(Component.literal(PersonService.noSuchPersonMessage(scope)));
                return 0;
            }
            if (!viewAll && (self == null || !target.uuid().equals(self.getUUID()))) {
                source.sendFailure(Component.literal(
                        "You need " + GovCapability.VEHICLE_REGISTER.id()
                                + " to list another person's vehicles."));
                return 0;
            }
            filter = target.uuid();
        } else if (!viewAll) {
            if (self == null) {
                source.sendFailure(Component.literal(
                        "You need " + GovCapability.VEHICLE_REGISTER.id() + " to list all vehicles."));
                return 0;
            }
            filter = self.getUUID();
        }

        final VehicleService.Page page = VehicleService.page(
                data, self == null ? null : self.getUUID(), capabilities,
                VehicleKeys.FILTER_ALL, filter, 0, System.currentTimeMillis());
        final String heading = filter == null ? "all vehicles" : "vehicles for " + scope;
        GovCommand.send(context, "Vehicle registry (" + heading + "): " + page.total() + " total");
        if (page.rows().isEmpty()) {
            GovCommand.send(context, "  (none)");
            return 1;
        }
        if (page.total() > page.rows().size()) {
            GovCommand.send(context, "  showing " + page.rows().size() + " of " + page.total()
                    + "; the Vehicles tab pages through the rest.");
        }
        for (final VehicleService.VehicleRow row : page.rows()) {
            final VehicleRecord vehicle = row.vehicle();
            final StringBuilder line = new StringBuilder("  ").append(vehicle.plate())
                    .append("  ").append(vehicle.vehClass().toUpperCase(Locale.ROOT))
                    .append("  ").append(row.ownerName())
                    .append("  ").append(vehicle.status().name())
                    .append("  licence ").append(row.license() == null
                            ? LicenseKeys.STATE_NONE : row.licenseState().name())
                    .append(" (").append(row.points()).append(" pts)");
            if (vehicle.impound().active()) {
                line.append("  IMPOUNDED");
                if (vehicle.impound().flaggedForDisposal()
                        || vehicle.status() == com.horizonssmp.gov.common.enums.VehicleStatus.FLAGGED_FOR_DISPOSAL) {
                    line.append(" FLAGGED-FOR-DISPOSAL");
                }
            }
            GovCommand.send(context, line.toString());
            if (vehicle.impound().active() && !vehicle.impound().reasonText().isBlank()) {
                GovCommand.send(context, "      " + Sanitizer.singleLine(
                        vehicle.impound().reasonText(), Sanitizer.MAX_TEXT));
            }
        }
        return 1;
    }

    static int vehicleInfo(final CommandContext<CommandSourceStack> context) {
        final CommandSourceStack source = context.getSource();
        final GovSavedData data = GovSavedData.get(source.getServer());
        final Set<GovCapability> capabilities = GovAuditCommandHandlers.readCapabilities(source, data);
        final String raw = StringArgumentType.getString(context, "plate");
        final VehicleRecord vehicle = VehicleService.find(data, raw);
        if (vehicle == null) {
            source.sendFailure(Component.literal(VehicleService.noSuchVehicleMessage(raw)));
            return 0;
        }
        final ServerPlayer self = source.getPlayer();
        if (self != null && !VehicleService.canViewAll(capabilities)
                && (vehicle.ownerUuid() == null || !vehicle.ownerUuid().equals(self.getUUID()))) {
            source.sendFailure(Component.literal(
                    "You can only read the registry entry for your own vehicles."));
            return 0;
        }
        final Map<String, String> fields = VehicleService.detailFields(
                data, vehicle, capabilities, System.currentTimeMillis());
        GovCommand.send(context, "Vehicle " + GovAuditCommandHandlers.value(fields, VehicleKeys.PLATE)
                + "  (" + GovAuditCommandHandlers.value(fields, VehicleKeys.ID) + ")");
        GovCommand.send(context, "  Class: " + GovAuditCommandHandlers.value(fields, VehicleKeys.CLASS)
                + "  Status: " + GovAuditCommandHandlers.value(fields, VehicleKeys.STATUS));
        GovCommand.send(context, "  Owner: " + GovAuditCommandHandlers.value(fields, VehicleKeys.OWNER));
        GovCommand.send(context, "  Registered: " + GovAuditCommandHandlers.when(GovAuditCommandHandlers.parseLongField(fields, VehicleKeys.REGISTERED_AT))
                + " by " + GovAuditCommandHandlers.value(fields, VehicleKeys.REGISTERED_BY));
        GovCommand.send(context, "  Licence: " + GovAuditCommandHandlers.value(fields, VehicleKeys.LICENSE_STATE)
                + "  Points: " + GovAuditCommandHandlers.value(fields, VehicleKeys.LICENSE_POINTS));
        final long suspendedUntil = GovAuditCommandHandlers.parseLongField(fields, VehicleKeys.LICENSE_SUSPENDED_UNTIL);
        if (suspendedUntil > System.currentTimeMillis()) {
            GovCommand.send(context, "  Suspended until: " + GovAuditCommandHandlers.when(suspendedUntil));
        }
        if ("true".equals(fields.get(VehicleKeys.IMPOUND_ACTIVE))) {
            GovCommand.send(context, "  Impound: " + GovAuditCommandHandlers.value(fields, VehicleKeys.IMPOUND_GROUND)
                    + "  at " + GovAuditCommandHandlers.when(GovAuditCommandHandlers.parseLongField(fields, VehicleKeys.IMPOUNDED_AT))
                    + " by " + GovAuditCommandHandlers.value(fields, VehicleKeys.IMPOUNDED_BY));
            GovCommand.send(context, "  Reason: " + GovAuditCommandHandlers.value(fields, VehicleKeys.IMPOUND_REASON));
            if (!GovAuditCommandHandlers.value(fields, VehicleKeys.IMPOUND_LOCATION).equals("-")) {
                GovCommand.send(context, "  Location: " + GovAuditCommandHandlers.value(fields, VehicleKeys.IMPOUND_LOCATION));
            }
            GovCommand.send(context, "  Release fee: " + GovAuditCommandHandlers.value(fields, VehicleKeys.FEE)
                    + " gold ingots at the " + com.horizonssmp.gov.config.GovConfig.paymentPoint());
        } else if (GovAuditCommandHandlers.parseLongField(fields, VehicleKeys.RELEASED_AT) > 0L) {
            GovCommand.send(context, "  Released: " + GovAuditCommandHandlers.when(GovAuditCommandHandlers.parseLongField(fields, VehicleKeys.RELEASED_AT))
                    + " by " + GovAuditCommandHandlers.value(fields, VehicleKeys.RELEASED_BY)
                    + " (fee " + GovAuditCommandHandlers.value(fields, VehicleKeys.FEE) + " gold ingots)");
        } else {
            GovCommand.send(context, "  Impound: never");
        }
        if ("true".equals(fields.get(VehicleKeys.DISPOSAL))) {
            GovCommand.send(context, "  FLAGGED FOR DISPOSAL after 30 in-game days; staff action with notice only.");
        }
        return 1;
    }
}
