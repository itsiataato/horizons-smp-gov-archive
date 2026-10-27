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

public final class GovWeaponCommandHandlers {
    private GovWeaponCommandHandlers() {}

    static int weaponList(final CommandContext<CommandSourceStack> context, final String scope) {
        final CommandSourceStack source = context.getSource();
        final GovSavedData data = GovSavedData.get(source.getServer());
        final Set<GovCapability> capabilities = GovAuditCommandHandlers.readCapabilities(source, data);
        final ServerPlayer self = source.getPlayer();
        final boolean viewAll = WeaponService.canViewAll(capabilities);

        UUID filter = null;
        if (!"all".equalsIgnoreCase(scope)) {
            final GovTargets.Resolved target = GovTargets.resolve(source.getServer(), data, scope);
            if (target == null) {
                source.sendFailure(Component.literal(PersonService.noSuchPersonMessage(scope)));
                return 0;
            }
            if (!viewAll && (self == null || !target.uuid().equals(self.getUUID()))) {
                source.sendFailure(Component.literal(
                        "You need " + GovCapability.WEAPON_VIEW.id()
                                + " to list another person's weapons."));
                return 0;
            }
            filter = target.uuid();
        } else if (!viewAll) {
            if (self == null) {
                source.sendFailure(Component.literal(
                        "You need " + GovCapability.WEAPON_VIEW.id()
                                + " to list all weapons."));
                return 0;
            }
            filter = self.getUUID();
        }

        final WeaponService.WeaponPage page = WeaponService.page(
                data, self == null ? null : self.getUUID(), capabilities,
                WeaponKeys.FILTER_ALL, filter, 0);
        final String heading = filter == null ? "all weapons" : "weapons for " + scope;
        GovCommand.send(context, "Weapon registry (" + heading + "): " + page.total() + " total");
        if (page.rows().isEmpty()) {
            GovCommand.send(context, "  (none)");
            return 1;
        }
        if (page.total() > page.rows().size()) {
            GovCommand.send(context, "  showing " + page.rows().size() + " of " + page.total()
                    + "; the Weapons tab pages through the rest.");
        }
        for (final WeaponService.WeaponRow row : page.rows()) {
            final WeaponRecord weapon = row.weapon();
            final StringBuilder line = new StringBuilder("  ").append(weapon.serial())
                    .append("  Class ").append(weapon.category().name())
                    .append("  ").append(row.ownerName())
                    .append("  ").append(weapon.status());
            if (!weapon.permitId().isBlank()) {
                line.append("  permit ").append(weapon.permitId());
            }
            if (weapon.militaryGrade()) {
                line.append("  MILITARY-GRADE");
            }
            GovCommand.send(context, line.toString());
            GovCommand.send(context, "      " + Sanitizer.singleLine(weapon.type(), Sanitizer.MAX_TEXT));
        }
        return 1;
    }

    static int weaponInfo(final CommandContext<CommandSourceStack> context) {
        final CommandSourceStack source = context.getSource();
        final GovSavedData data = GovSavedData.get(source.getServer());
        final Set<GovCapability> capabilities = GovAuditCommandHandlers.readCapabilities(source, data);
        final String raw = StringArgumentType.getString(context, "serial");
        final WeaponRecord weapon = WeaponService.find(data, raw);
        if (weapon == null) {
            source.sendFailure(Component.literal(WeaponService.noSuchWeaponMessage(raw)));
            return 0;
        }
        final ServerPlayer self = source.getPlayer();
        if (self != null && !WeaponService.canViewAll(capabilities)
                && (weapon.ownerUuid() == null || !weapon.ownerUuid().equals(self.getUUID()))) {
            source.sendFailure(Component.literal(
                    "You can only read the registry entry for your own weapons."));
            return 0;
        }
        final Map<String, String> fields = WeaponService.detailFields(data, weapon);
        GovCommand.send(context, "Weapon " + GovAuditCommandHandlers.value(fields, WeaponKeys.SERIAL)
                + "  (" + GovAuditCommandHandlers.value(fields, WeaponKeys.ID) + ")");
        GovCommand.send(context, "  Class: " + GovAuditCommandHandlers.value(fields, WeaponKeys.CLASS)
                + (GovAuditCommandHandlers.value(fields, WeaponKeys.MILITARY).equals("true") ? "  MILITARY-GRADE" : ""));
        GovCommand.send(context, "  Type: " + GovAuditCommandHandlers.value(fields, WeaponKeys.TYPE));
        GovCommand.send(context, "  Owner: " + GovAuditCommandHandlers.value(fields, WeaponKeys.OWNER));
        GovCommand.send(context, "  Status: " + GovAuditCommandHandlers.value(fields, WeaponKeys.STATUS)
                + "  Permit: " + GovAuditCommandHandlers.value(fields, WeaponKeys.PERMIT));
        GovCommand.send(context, "  Registered: " + GovAuditCommandHandlers.when(GovAuditCommandHandlers.parseLongField(fields, WeaponKeys.REGISTERED_AT))
                + " by " + GovAuditCommandHandlers.value(fields, WeaponKeys.REGISTERED_BY));
        if (!GovAuditCommandHandlers.value(fields, WeaponKeys.DEALER).equals("-")) {
            GovCommand.send(context, "  Dealer: " + GovAuditCommandHandlers.value(fields, WeaponKeys.DEALER)
                    + "  sale recorded: " + GovAuditCommandHandlers.when(GovAuditCommandHandlers.parseLongField(fields, WeaponKeys.SOLD_AT)));
        }
        final int transfers = (int) GovAuditCommandHandlers.parseLongField(fields, WeaponKeys.TRANSFER_COUNT);
        GovCommand.send(context, "  Transfers: " + transfers);
        for (int i = 0; i < transfers && i < WeaponKeys.MAX_LISTED_TRANSFERS; i++) {
            final String prefix = WeaponKeys.TRANSFER_PREFIX + i;
            GovCommand.send(context, "    " + GovAuditCommandHandlers.value(fields, prefix + WeaponKeys.TRANSFER_FROM)
                    + " -> " + GovAuditCommandHandlers.value(fields, prefix + WeaponKeys.TRANSFER_TO)
                    + "  " + GovAuditCommandHandlers.when(GovAuditCommandHandlers.parseLongField(fields, prefix + WeaponKeys.TRANSFER_REQUESTED_AT))
                    + (GovAuditCommandHandlers.value(fields, prefix + WeaponKeys.TRANSFER_IN_WINDOW).equals("true")
                            ? "  in window" : "  LATE"));
        }
        return 1;
    }

    static int weaponRegister(final CommandContext<CommandSourceStack> context) {
        final CommandSourceStack source = context.getSource();
        final GovSavedData data = GovSavedData.get(source.getServer());
        final String player = StringArgumentType.getString(context, "player");
        final String weaponClass = StringArgumentType.getString(context, "class");
        final String serial = StringArgumentType.getString(context, "serial");
        final String type = StringArgumentType.getString(context, "type");
        final WeaponService.Outcome precheck =
                WeaponService.registerPrecheck(data, weaponClass, serial, type);
        if (precheck != null) {
            source.sendFailure(Component.literal(precheck.message()));
            return 0;
        }
        final ServerPlayer actor = source.getPlayer();
        if (actor == null) {
            source.sendFailure(Component.literal(
                    "/gov weapon register can only be used by a player."));
            return 0;
        }
        final GovTargets.Resolved target = GovTargets.resolve(source.getServer(), data, player);
        if (target == null) {
            source.sendFailure(Component.literal(PersonService.noSuchPersonMessage(player)));
            return 0;
        }
        return GovCommand.dispatch(context, "/gov weapon register", OpCode.WPN_REGISTER,
                Map.of("target", player,
                        WeaponKeys.KEY_CLASS, weaponClass,
                        WeaponKeys.KEY_SERIAL, serial,
                        WeaponKeys.KEY_TYPE, type));
    }

    static int weaponTransfer(final CommandContext<CommandSourceStack> context) {
        final CommandSourceStack source = context.getSource();
        final GovSavedData data = GovSavedData.get(source.getServer());
        final String serial = StringArgumentType.getString(context, "serial");
        final String player = StringArgumentType.getString(context, "player");
        final WeaponService.Outcome precheck = WeaponService.transferPrecheck(data, serial);
        if (precheck != null) {
            source.sendFailure(Component.literal(precheck.message()));
            return 0;
        }
        if (source.getPlayer() == null) {
            source.sendFailure(Component.literal(
                    "/gov weapon transfer can only be used by a player."));
            return 0;
        }
        final GovTargets.Resolved target = GovTargets.resolve(source.getServer(), data, player);
        if (target == null) {
            source.sendFailure(Component.literal(PersonService.noSuchPersonMessage(player)));
            return 0;
        }
        return GovCommand.dispatch(context, "/gov weapon transfer", OpCode.WPN_TRANSFER,
                Map.of(WeaponKeys.KEY_SERIAL, serial, "target", player));
    }

    static int weaponRevoke(final CommandContext<CommandSourceStack> context) {
        final CommandSourceStack source = context.getSource();
        final GovSavedData data = GovSavedData.get(source.getServer());
        final String serial = StringArgumentType.getString(context, "serial");
        final String reason = StringArgumentType.getString(context, "reason");
        final WeaponRecord weapon = WeaponService.find(data, serial);
        if (weapon == null) {
            source.sendFailure(Component.literal(WeaponService.noSuchWeaponMessage(serial)));
            return 0;
        }
        if (Sanitizer.text(reason).isBlank()) {
            source.sendFailure(Component.literal("A revocation needs a recorded reason."));
            return 0;
        }
        if (source.getPlayer() == null) {
            source.sendFailure(Component.literal(
                    "/gov weapon revoke can only be used by a player."));
            return 0;
        }
        return GovCommand.dispatch(context, "/gov weapon revoke", OpCode.WPN_REVOKE,
                Map.of(WeaponKeys.KEY_SERIAL, weapon.serial(), WeaponKeys.KEY_REASON, reason));
    }

    static int weaponLoss(final CommandContext<CommandSourceStack> context) {
        final CommandSourceStack source = context.getSource();
        final GovSavedData data = GovSavedData.get(source.getServer());
        final String serial = StringArgumentType.getString(context, "serial");
        final String reason = StringArgumentType.getString(context, "reason");
        final WeaponRecord weapon = WeaponService.find(data, serial);
        if (weapon == null) {
            source.sendFailure(Component.literal(WeaponService.noSuchWeaponMessage(serial)));
            return 0;
        }
        if (Sanitizer.text(reason).isBlank()) {
            source.sendFailure(Component.literal("A loss report needs a recorded reason."));
            return 0;
        }
        if (source.getPlayer() == null) {
            source.sendFailure(Component.literal(
                    "/gov weapon loss can only be used by a player."));
            return 0;
        }
        return GovCommand.dispatch(context, "/gov weapon loss", OpCode.WPN_REPORT_LOSS,
                Map.of(WeaponKeys.KEY_SERIAL, weapon.serial(), WeaponKeys.KEY_REASON, reason));
    }
}
