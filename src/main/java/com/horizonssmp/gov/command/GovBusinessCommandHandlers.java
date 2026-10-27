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

public final class GovBusinessCommandHandlers {
    private GovBusinessCommandHandlers() {}

    static int businessList(
            final CommandContext<CommandSourceStack> context, final String scope) {
        final CommandSourceStack source = context.getSource();
        final GovSavedData data = GovSavedData.get(source.getServer());
        final Set<GovCapability> capabilities = GovAuditCommandHandlers.readCapabilities(source, data);
        final ServerPlayer self = source.getPlayer();
        final boolean viewAll = BusinessService.canViewAll(capabilities);

        UUID filter = null;
        if (!"all".equalsIgnoreCase(scope)) {
            final GovTargets.Resolved target =
                    GovTargets.resolve(source.getServer(), data, scope);
            if (target == null) {
                source.sendFailure(Component.literal(PersonService.noSuchPersonMessage(scope)));
                return 0;
            }
            if (!viewAll && (self == null || !target.uuid().equals(self.getUUID()))) {
                source.sendFailure(Component.literal(
                        "You need " + GovCapability.BUSINESS_MANAGE.id()
                                + " to list another person's businesses."));
                return 0;
            }
            filter = target.uuid();
        } else if (!viewAll) {
            if (self == null) {
                source.sendFailure(Component.literal(
                        "You need " + GovCapability.BUSINESS_MANAGE.id()
                                + " to list all businesses."));
                return 0;
            }
            filter = self.getUUID();
        }

        final BusinessService.Page page = BusinessService.page(
                data, self == null ? null : self.getUUID(), capabilities,
                BusinessKeys.FILTER_ALL, filter, 0);
        final String heading = filter == null ? "all businesses" : "businesses in scope";
        GovCommand.send(context, "Business registry (" + heading + "): " + page.total() + " total");
        if (page.rows().isEmpty()) {
            GovCommand.send(context, "  (none)");
            return 1;
        }
        if (page.total() > page.rows().size()) {
            GovCommand.send(context, "  showing " + page.rows().size() + " of " + page.total()
                    + "; the Businesses tab pages through the rest.");
        }
        for (final BusinessService.BusinessRow row : page.rows()) {
            final BusinessRecord business = row.business();
            final StringBuilder line = new StringBuilder("  ").append(business.name())
                    .append(" (").append(business.id()).append(")")
                    .append("  ").append(business.status())
                    .append("  ").append(row.ownerName())
                    .append("  ").append(business.type())
                    .append("  staff ").append(business.employees().size());
            if (!business.linkedJobId().isBlank()) {
                line.append("  job ").append(business.linkedJobId())
                        .append(" (").append(row.jobName()).append(")");
            }
            if (!business.suspendReason().isBlank()) {
                line.append("  suspended: ").append(business.suspendReason());
            }
            GovCommand.send(context, line.toString());
        }
        return 1;
    }

    static int businessInfo(final CommandContext<CommandSourceStack> context) {
        final CommandSourceStack source = context.getSource();
        final GovSavedData data = GovSavedData.get(source.getServer());
        final Set<GovCapability> capabilities = GovAuditCommandHandlers.readCapabilities(source, data);
        final ServerPlayer self = source.getPlayer();
        final String raw = StringArgumentType.getString(context, "name");
        final BusinessRecord business = BusinessService.find(data, raw);
        if (business == null) {
            source.sendFailure(Component.literal(BusinessService.noSuchBusinessMessage(raw)));
            return 0;
        }
        if (self != null && !BusinessService.canViewAll(capabilities)
                && !BusinessService.visibleTo(data, self.getUUID(), business.id())) {
            source.sendFailure(Component.literal(
                    "You can only read the registry entry for your own business or workplace."));
            return 0;
        }

        final Set<UUID> online = new java.util.HashSet<>();
        for (final ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
            online.add(player.getUUID());
        }
        final Map<String, String> fields = BusinessService.detailFields(
                data, business, capabilities, self == null ? null : self.getUUID(), online);
        GovCommand.send(context, "Business: " + GovAuditCommandHandlers.value(fields, BusinessKeys.NAME)
                + " (" + GovAuditCommandHandlers.value(fields, BusinessKeys.ID) + ")");
        GovCommand.send(context, "  Status: " + GovAuditCommandHandlers.value(fields, BusinessKeys.STATUS)
                + "  Type: " + GovAuditCommandHandlers.value(fields, BusinessKeys.TYPE));
        GovCommand.send(context, "  Owner: " + GovAuditCommandHandlers.value(fields, BusinessKeys.OWNER)
                + "  registered " + GovAuditCommandHandlers.when(GovAuditCommandHandlers.parseLongField(fields, BusinessKeys.CREATED_AT))
                + " by " + GovAuditCommandHandlers.value(fields, BusinessKeys.CREATED_BY));
        final int staff = (int) GovAuditCommandHandlers.parseLongField(fields, BusinessKeys.EMPLOYEE_COUNT);
        GovCommand.send(context, "  Staff (" + staff + "):");
        for (int i = 0; i < staff && i < BusinessKeys.MAX_LISTED_EMPLOYEES; i++) {
            final String prefix = BusinessKeys.EMPLOYEE_PREFIX + i;
            GovCommand.send(context, "    " + GovAuditCommandHandlers.value(fields, prefix + BusinessKeys.EMPLOYEE_NAME)
                    + "  " + GovAuditCommandHandlers.value(fields, prefix + BusinessKeys.EMPLOYEE_ROLE)
                    + "  since " + GovAuditCommandHandlers.when(GovAuditCommandHandlers.parseLongField(
                            fields, prefix + BusinessKeys.EMPLOYEE_SINCE))
                    + ("true".equals(fields.get(prefix + BusinessKeys.EMPLOYEE_ONLINE))
                            ? "  (online)" : "  (offline)"));
        }
        if (staff > BusinessKeys.MAX_LISTED_EMPLOYEES) {
            GovCommand.send(context, "    showing " + BusinessKeys.MAX_LISTED_EMPLOYEES + " of " + staff);
        }
        final String jobId = GovAuditCommandHandlers.value(fields, BusinessKeys.JOB);
        GovCommand.send(context, "  Linked job: " + ("-".equals(jobId)
                ? "(none; a link grants no capability)"
                : jobId + " (" + GovAuditCommandHandlers.value(fields, BusinessKeys.JOB_NAME) + ")"));
        GovCommand.send(context, "  Payroll: " + GovAuditCommandHandlers.value(fields, BusinessKeys.PAYROLL_RATE)
                + " gold ingots per in-game day  schedule "
                + GovAuditCommandHandlers.value(fields, BusinessKeys.PAYROLL_SCHEDULE));
        GovCommand.send(context, "    recorded payments: " + GovAuditCommandHandlers.value(fields, BusinessKeys.PAYROLL_COUNT)
                + "  last " + GovAuditCommandHandlers.value(fields, BusinessKeys.PAYROLL_LAST_AMOUNT) + " gold at "
                + GovAuditCommandHandlers.when(GovAuditCommandHandlers.parseLongField(fields, BusinessKeys.PAYROLL_LAST_AT))
                + " by " + GovAuditCommandHandlers.value(fields, BusinessKeys.PAYROLL_LAST_BY));
        if (!business.suspendReason().isBlank()) {
            GovCommand.send(context, "  Suspended: " + business.suspendReason()
                    + "  (" + GovAuditCommandHandlers.when(business.suspendedAt()) + ")");
        }
        if (!business.notes().isBlank()) {
            GovCommand.send(context, "  Notes: " + Sanitizer.singleLine(business.notes(), Sanitizer.MAX_TEXT));
        }
        return 1;
    }

    static int businessRegister(
            final CommandContext<CommandSourceStack> context, final String owner) {
        final CommandSourceStack source = context.getSource();
        final GovSavedData data = GovSavedData.get(source.getServer());
        final String name = StringArgumentType.getString(context, "name");
        final String type = StringArgumentType.getString(context, "type");
        final BusinessService.Outcome precheck =
                BusinessService.registerPrecheck(data, name, type);
        if (precheck != null) {
            source.sendFailure(Component.literal(precheck.message()));
            return 0;
        }
        if (source.getPlayer() == null) {
            source.sendFailure(Component.literal(
                    "/gov business register can only be used by a player."));
            return 0;
        }
        if (owner != null && !owner.isBlank()) {
            final GovTargets.Resolved target =
                    GovTargets.resolve(source.getServer(), data, owner);
            if (target == null) {
                source.sendFailure(Component.literal(PersonService.noSuchPersonMessage(owner)));
                return 0;
            }
        }
        return GovCommand.dispatch(context, "/gov business register", OpCode.BIZ_REGISTER,
                owner == null || owner.isBlank()
                        ? Map.of(BusinessKeys.KEY_NAME, name, BusinessKeys.KEY_TYPE, type)
                        : Map.of(BusinessKeys.KEY_NAME, name, BusinessKeys.KEY_TYPE, type,
                                GovTargets.KEY_TARGET, owner));
    }
}
