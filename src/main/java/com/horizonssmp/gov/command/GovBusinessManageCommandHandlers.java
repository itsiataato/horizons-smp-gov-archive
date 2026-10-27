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

public final class GovBusinessManageCommandHandlers {
    private GovBusinessManageCommandHandlers() {}

    static int businessClose(final CommandContext<CommandSourceStack> context) {
        final CommandSourceStack source = context.getSource();
        final GovSavedData data = GovSavedData.get(source.getServer());
        final String name = StringArgumentType.getString(context, "name");
        final BusinessService.Outcome precheck = BusinessService.closePrecheck(data, name);
        if (precheck != null) {
            source.sendFailure(Component.literal(precheck.message()));
            return 0;
        }
        if (source.getPlayer() == null) {
            source.sendFailure(Component.literal(
                    "/gov business close can only be used by a player."));
            return 0;
        }
        return GovCommand.dispatch(context, "/gov business close", OpCode.BIZ_REVOKE,
                Map.of(BusinessKeys.KEY_BUSINESS, name));
    }

    static int businessSuspend(final CommandContext<CommandSourceStack> context) {
        final CommandSourceStack source = context.getSource();
        final GovSavedData data = GovSavedData.get(source.getServer());
        final String name = StringArgumentType.getString(context, "name");
        final String reason = StringArgumentType.getString(context, "reason");
        final BusinessService.Outcome precheck =
                BusinessService.suspendPrecheck(data, name, reason);
        if (precheck != null) {
            source.sendFailure(Component.literal(precheck.message()));
            return 0;
        }
        if (source.getPlayer() == null) {
            source.sendFailure(Component.literal(
                    "/gov business suspend can only be used by a player."));
            return 0;
        }
        return GovCommand.dispatch(context, "/gov business suspend", OpCode.BIZ_SUSPEND,
                Map.of(BusinessKeys.KEY_BUSINESS, name, BusinessKeys.KEY_REASON, reason));
    }

    static int businessEmployeeAdd(
            final CommandContext<CommandSourceStack> context, final String role) {
        final CommandSourceStack source = context.getSource();
        final GovSavedData data = GovSavedData.get(source.getServer());
        final String name = StringArgumentType.getString(context, "name");
        final String player = StringArgumentType.getString(context, "player");
        final BusinessService.Outcome precheck =
                BusinessService.employeeAddPrecheck(data, name, role);
        if (precheck != null) {
            source.sendFailure(Component.literal(precheck.message()));
            return 0;
        }
        if (source.getPlayer() == null) {
            source.sendFailure(Component.literal(
                    "/gov business employee add can only be used by a player."));
            return 0;
        }
        final GovTargets.Resolved target = GovTargets.resolve(source.getServer(), data, player);
        if (target == null) {
            source.sendFailure(Component.literal(PersonService.noSuchPersonMessage(player)));
            return 0;
        }
        return GovCommand.dispatch(context, "/gov business employee add", OpCode.BIZ_EMPLOYEE_ADD,
                Map.of(BusinessKeys.KEY_BUSINESS, name,
                        GovTargets.KEY_TARGET, player,
                        BusinessKeys.KEY_ROLE, role));
    }

    static int businessEmployeeRemove(final CommandContext<CommandSourceStack> context) {
        final CommandSourceStack source = context.getSource();
        final GovSavedData data = GovSavedData.get(source.getServer());
        final String name = StringArgumentType.getString(context, "name");
        final String player = StringArgumentType.getString(context, "player");
        final BusinessService.Outcome precheck =
                BusinessService.employeeRemovePrecheck(data, name);
        if (precheck != null) {
            source.sendFailure(Component.literal(precheck.message()));
            return 0;
        }
        if (source.getPlayer() == null) {
            source.sendFailure(Component.literal(
                    "/gov business employee remove can only be used by a player."));
            return 0;
        }
        final GovTargets.Resolved target = GovTargets.resolve(source.getServer(), data, player);
        if (target == null) {
            source.sendFailure(Component.literal(PersonService.noSuchPersonMessage(player)));
            return 0;
        }
        return GovCommand.dispatch(context, "/gov business employee remove", OpCode.BIZ_EMPLOYEE_REMOVE,
                Map.of(BusinessKeys.KEY_BUSINESS, name, GovTargets.KEY_TARGET, player));
    }

    static int businessLink(final CommandContext<CommandSourceStack> context) {
        final CommandSourceStack source = context.getSource();
        final GovSavedData data = GovSavedData.get(source.getServer());
        final String name = StringArgumentType.getString(context, "name");
        final String job = StringArgumentType.getString(context, "job");
        final BusinessService.Outcome precheck =
                BusinessService.linkPrecheck(data, name, job);
        if (precheck != null) {
            source.sendFailure(Component.literal(precheck.message()));
            return 0;
        }
        if (source.getPlayer() == null) {
            source.sendFailure(Component.literal(
                    "/gov business link can only be used by a player."));
            return 0;
        }
        return GovCommand.dispatch(context, "/gov business link", OpCode.BIZ_LINK,
                Map.of(BusinessKeys.KEY_BUSINESS, name, BusinessKeys.KEY_JOB, job));
    }

    static int businessUnlink(final CommandContext<CommandSourceStack> context) {
        final CommandSourceStack source = context.getSource();
        final GovSavedData data = GovSavedData.get(source.getServer());
        final String name = StringArgumentType.getString(context, "name");
        final BusinessService.Outcome precheck = BusinessService.linkPrecheck(data, name, "");
        if (precheck != null) {
            source.sendFailure(Component.literal(precheck.message()));
            return 0;
        }
        if (source.getPlayer() == null) {
            source.sendFailure(Component.literal(
                    "/gov business unlink can only be used by a player."));
            return 0;
        }
        return GovCommand.dispatch(context, "/gov business unlink", OpCode.BIZ_LINK,
                Map.of(BusinessKeys.KEY_BUSINESS, name, BusinessKeys.KEY_JOB, ""));
    }

    static int businessPayrollInfo(final CommandContext<CommandSourceStack> context) {
        final CommandSourceStack source = context.getSource();
        final GovSavedData data = GovSavedData.get(source.getServer());
        final Set<GovCapability> capabilities = GovAuditCommandHandlers.readCapabilities(source, data);
        if (!capabilities.contains(GovCapability.PAYROLL_RUN)) {
            source.sendFailure(Component.literal(
                    "You need " + GovCapabilities.requiredFor(OpCode.PAYROLL_INFO).id()
                            + " for that action."));
            return 0;
        }
        final String name = StringArgumentType.getString(context, "name");
        final BusinessRecord business = BusinessService.find(data, name);
        if (business == null) {
            source.sendFailure(Component.literal(BusinessService.noSuchBusinessMessage(name)));
            return 0;
        }
        final Map<String, String> fields =
                BusinessService.payrollInfoFields(data, business, capabilities);
        GovCommand.send(context, "Payroll: " + GovAuditCommandHandlers.value(fields, BusinessKeys.NAME)
                + " (" + GovAuditCommandHandlers.value(fields, BusinessKeys.ID) + ")  "
                + GovAuditCommandHandlers.value(fields, BusinessKeys.STATUS));
        GovCommand.send(context, "  Rate: " + GovAuditCommandHandlers.value(fields, BusinessKeys.PAYROLL_RATE)
                + " gold ingots per in-game day  schedule "
                + GovAuditCommandHandlers.value(fields, BusinessKeys.PAYROLL_SCHEDULE));
        GovCommand.send(context, "  Recorded payments: " + GovAuditCommandHandlers.value(fields, BusinessKeys.PAYROLL_COUNT)
                + "  last " + GovAuditCommandHandlers.value(fields, BusinessKeys.PAYROLL_LAST_AMOUNT)
                + " gold at " + GovAuditCommandHandlers.when(GovAuditCommandHandlers.parseLongField(fields, BusinessKeys.PAYROLL_LAST_AT))
                + " by " + GovAuditCommandHandlers.value(fields, BusinessKeys.PAYROLL_LAST_BY));
        if (!GovAuditCommandHandlers.value(fields, BusinessKeys.PAYROLL_LAST_NOTE).equals("-")) {
            GovCommand.send(context, "  Last note: " + GovAuditCommandHandlers.value(fields, BusinessKeys.PAYROLL_LAST_NOTE));
        }
        GovCommand.send(context, "  Display-only: no gold moves through this mod; the payment is physical.");
        return 1;
    }

    static int businessPayrollSet(
            final CommandContext<CommandSourceStack> context, final String note) {
        final CommandSourceStack source = context.getSource();
        final GovSavedData data = GovSavedData.get(source.getServer());
        final String name = StringArgumentType.getString(context, "name");
        final String rate = StringArgumentType.getString(context, "ratePerDay");
        final BusinessService.Outcome precheck =
                BusinessService.payrollSetPrecheck(data, name, rate);
        if (precheck != null) {
            source.sendFailure(Component.literal(precheck.message()));
            return 0;
        }
        if (source.getPlayer() == null) {
            source.sendFailure(Component.literal(
                    "/gov business payroll set can only be used by a player."));
            return 0;
        }
        return GovCommand.dispatch(context, "/gov business payroll set", OpCode.BIZ_PAYROLL_SET,
                Map.of(BusinessKeys.KEY_BUSINESS, name,
                        BusinessKeys.KEY_RATE, rate,
                        BusinessKeys.KEY_SCHEDULE, note));
    }

    static int businessPayrollRecord(
            final CommandContext<CommandSourceStack> context, final String note) {
        final CommandSourceStack source = context.getSource();
        final GovSavedData data = GovSavedData.get(source.getServer());
        final String name = StringArgumentType.getString(context, "name");
        final String amount = StringArgumentType.getString(context, "amount");
        final BusinessService.Outcome precheck =
                BusinessService.payrollRecordPrecheck(data, name, amount);
        if (precheck != null) {
            source.sendFailure(Component.literal(precheck.message()));
            return 0;
        }
        if (source.getPlayer() == null) {
            source.sendFailure(Component.literal(
                    "/gov business payroll record can only be used by a player."));
            return 0;
        }
        return GovCommand.dispatch(context, "/gov business payroll record", OpCode.BIZ_PAYROLL_RECORD,
                Map.of(BusinessKeys.KEY_BUSINESS, name,
                        BusinessKeys.KEY_AMOUNT, amount,
                        BusinessKeys.KEY_NOTE, note));
    }
}
