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

public final class GovAuditAdminCommandHandlers {
    private GovAuditAdminCommandHandlers() {}

    static int adminExport(final CommandContext<CommandSourceStack> context) {
        final CommandSourceStack source = context.getSource();
        final String raw = StringArgumentType.getString(context, "section");
        final GovExportSection section = GovExportSection.byId(raw).orElse(null);
        if (section == null) {
            source.sendFailure(Component.literal(
                    "Unknown export section '" + Sanitizer.singleLine(raw, Sanitizer.MAX_ID)
                            + "'. Sections: " + GovExportSection.ids() + "."));
            return 0;
        }
        final GovSavedData data = GovSavedData.get(source.getServer());
        final Set<GovCapability> capabilities = GovAuditCommandHandlers.readCapabilities(source, data);
        if (!capabilities.contains(GovCapability.SEASON_ADMIN)) {
            source.sendFailure(Component.literal(
                    "You need " + GovCapability.SEASON_ADMIN.id() + " for that action."));
            return 0;
        }
        final ServerPlayer player = source.getPlayer();
        if (player != null) {
            return GovAuditAdminCommandHandlers.replyExport(context, GovActionDispatcher.dispatch(
                    player, OpCode.ADMIN_EXPORT, Map.of(AuditKeys.KEY_SECTION, section.id())));
        }
        final GovExportService.Result result = GovExportService.exportSection(
                data, source.getServer().getServerDirectory(), section,
                System.currentTimeMillis());
        return GovAuditAdminCommandHandlers.replyConsoleExport(context, result, section, "ADMIN_EXPORT");
    }

    static int seasonStatus(final CommandContext<CommandSourceStack> context) {
        final CommandSourceStack source = context.getSource();
        final GovSavedData data = GovSavedData.get(source.getServer());
        final Set<GovCapability> capabilities = GovAuditCommandHandlers.readCapabilities(source, data);
        if (!capabilities.contains(GovCapability.SEASON_ADMIN)) {
            source.sendFailure(Component.literal(
                    "You need " + GovCapability.SEASON_ADMIN.id() + " for that action."));
            return 0;
        }
        final Map<String, String> fields = SeasonResetService.statusFields(
                data, GovAuditCommandHandlers.consoleOrPlayer(source), SeasonResetService.Policy.fromConfig(),
                System.currentTimeMillis());
        GovCommand.send(context, "Season reset plan (businesses "
                + ("true".equals(fields.get(AuditKeys.SEASON_CLEAR_BUSINESSES)) ? "CLEARED" : "KEPT")
                + "):");
        GovCommand.send(context, "  Clear: " + GovAuditCommandHandlers.value(fields, AuditKeys.SEASON_SECTIONS));
        GovCommand.send(context, "  Counts: " + GovAuditCommandHandlers.value(fields, AuditKeys.SEASON_COUNTS_TEXT));
        GovCommand.send(context, "  Keep: " + GovAuditCommandHandlers.value(fields, AuditKeys.SEASON_KEEP));
        GovCommand.send(context, "  Token: " + ("true".equals(fields.get(AuditKeys.SEASON_PENDING))
                ? "pending until " + GovAuditCommandHandlers.when(GovAuditCommandHandlers.parseLongField(fields, AuditKeys.SEASON_EXPIRES_AT))
                : "none") + "  (lifetime " + GovAuditCommandHandlers.value(fields, AuditKeys.SEASON_TOKEN_SECONDS) + "s)");
        GovCommand.send(context, "  Directories: " + GovAuditCommandHandlers.value(fields, AuditKeys.SEASON_EXPORT_DIR)
                + " and " + GovAuditCommandHandlers.value(fields, AuditKeys.SEASON_BACKUP_DIR));
        return 1;
    }

    static int seasonBegin(final CommandContext<CommandSourceStack> context) {
        final CommandSourceStack source = context.getSource();
        final GovSavedData data = GovSavedData.get(source.getServer());
        final Set<GovCapability> capabilities = GovAuditCommandHandlers.readCapabilities(source, data);
        if (!capabilities.contains(GovCapability.SEASON_ADMIN)) {
            source.sendFailure(Component.literal(
                    "You need " + GovCapability.SEASON_ADMIN.id() + " for that action."));
            return 0;
        }
        final ServerPlayer player = source.getPlayer();
        if (player != null) {
            return GovAuditAdminCommandHandlers.replySeason(context, GovActionDispatcher.dispatch(
                    player, OpCode.SEASON_BEGIN, Map.of()), true);
        }
        final SeasonResetService.Outcome outcome = SeasonResetService.begin(
                data, SeasonResetService.Actor.console(),
                SeasonResetService.Policy.fromConfig(), System.currentTimeMillis());
        if (!outcome.ok()) {
            source.sendFailure(Component.literal(outcome.message()));
            return 0;
        }
        GovCommand.send(context, "Season reset begun. Nothing has been cleared yet.");
        GovCommand.send(context, "  Token: " + outcome.token());
        GovCommand.send(context, "  Expires: " + GovAuditCommandHandlers.when(outcome.expiresAt()));
        GovCommand.send(context, "  Sections: " + String.join(",", outcome.plan().sections()));
        GovCommand.send(context, "  Counts: " + SeasonResetService.countsText(outcome.plan().counts()));
        GovCommand.send(context, "  Commit with: /gov admin season reset commit " + outcome.token());
        return 1;
    }

    static int seasonCommit(final CommandContext<CommandSourceStack> context) {
        final CommandSourceStack source = context.getSource();
        final GovSavedData data = GovSavedData.get(source.getServer());
        final Set<GovCapability> capabilities = GovAuditCommandHandlers.readCapabilities(source, data);
        if (!capabilities.contains(GovCapability.SEASON_ADMIN)) {
            source.sendFailure(Component.literal(
                    "You need " + GovCapability.SEASON_ADMIN.id() + " for that action."));
            return 0;
        }
        final String token = StringArgumentType.getString(context, "token");
        final ServerPlayer player = source.getPlayer();
        if (player != null) {
            return GovAuditAdminCommandHandlers.replySeason(context, GovActionDispatcher.dispatch(
                    player, OpCode.SEASON_COMMIT, Map.of(AuditKeys.KEY_TOKEN, token)), false);
        }
        final SeasonResetService.Outcome outcome = SeasonResetService.commit(
                data, source.getServer().getServerDirectory(),
                SeasonResetService.Actor.console(), token, System.currentTimeMillis());
        if (!outcome.ok()) {
            source.sendFailure(Component.literal(outcome.message()));
            return 0;
        }
        GovAuditAdminCommandHandlers.printCommit(source, context, outcome);
        return 1;
    }

    static int replyExport(
            final CommandContext<CommandSourceStack> context, final ActionResult result) {
        if (!result.ok()) {
            context.getSource().sendFailure(Component.literal(result.errorText()));
            return 0;
        }
        final Map<String, String> fields = GovFields.decode(result.delta());
        GovCommand.send(context, "Export written: " + GovAuditCommandHandlers.value(fields, AuditKeys.EXPORT_PATH));
        GovCommand.send(context, "  Section: " + GovAuditCommandHandlers.value(fields, AuditKeys.EXPORT_SECTION)
                + "  Bytes: " + GovAuditCommandHandlers.value(fields, AuditKeys.EXPORT_BYTES));
        GovCommand.send(context, "  sha256: " + GovAuditCommandHandlers.value(fields, AuditKeys.EXPORT_SHA256));
        return 1;
    }

    static int replyConsoleExport(
            final CommandContext<CommandSourceStack> context,
            final GovExportService.Result result,
            final GovExportSection section,
            final String action) {
        final GovSavedData data = GovSavedData.get(context.getSource().getServer());
        if (!result.ok()) {
            AuditService.appendOutcome(data, null, SeasonResetService.CONSOLE_NAME, "", action,
                    AuditOutcome.REFUSED, null, "", "section=" + section.id(), result.message());
            context.getSource().sendFailure(Component.literal(result.message()));
            return 0;
        }
        AuditService.appendOutcome(data, null, SeasonResetService.CONSOLE_NAME, "", action,
                AuditOutcome.ACCEPTED, null, "",
                "section=" + section.id() + "; path=" + result.relativePath()
                        + "; bytes=" + result.bytes() + "; sha256=" + result.sha256(),
                "export written by console");
        GovCommand.send(context, "Export written: " + result.relativePath());
        GovCommand.send(context, "  Section: " + section.id() + "  Bytes: " + result.bytes());
        GovCommand.send(context, "  sha256: " + result.sha256());
        return 1;
    }

    static int replySeason(
            final CommandContext<CommandSourceStack> context,
            final ActionResult result, final boolean begin) {
        if (!result.ok()) {
            context.getSource().sendFailure(Component.literal(result.errorText()));
            return 0;
        }
        final Map<String, String> fields = GovFields.decode(result.delta());
        if (begin) {
            GovCommand.send(context, "Season reset begun. Nothing has been cleared yet.");
            GovCommand.send(context, "  Token: " + GovAuditCommandHandlers.value(fields, AuditKeys.SEASON_TOKEN));
            GovCommand.send(context, "  Expires: " + GovAuditCommandHandlers.when(GovAuditCommandHandlers.parseLongField(fields, AuditKeys.SEASON_EXPIRES_AT)));
            GovCommand.send(context, "  Sections: " + GovAuditCommandHandlers.value(fields, AuditKeys.SEASON_SECTIONS));
            GovCommand.send(context, "  Counts: " + GovAuditCommandHandlers.value(fields, AuditKeys.SEASON_COUNTS_TEXT));
            GovCommand.send(context, "  Commit with: /gov admin season reset commit "
                    + GovAuditCommandHandlers.value(fields, AuditKeys.SEASON_TOKEN));
            return 1;
        }
        GovCommand.send(context, "Season reset committed.");
        GovCommand.send(context, "  Cleared: " + GovAuditCommandHandlers.value(fields, AuditKeys.SEASON_COUNTS_TEXT));
        GovCommand.send(context, "  Players reset to citizen: "
                + GovAuditCommandHandlers.value(fields, AuditKeys.SEASON_PLAYERS_RESET));
        GovCommand.send(context, "  Backup: " + GovAuditCommandHandlers.value(fields, AuditKeys.SEASON_BACKUP_PATH)
                + "  Bytes: " + GovAuditCommandHandlers.value(fields, AuditKeys.SEASON_BACKUP_BYTES));
        GovCommand.send(context, "  sha256: " + GovAuditCommandHandlers.value(fields, AuditKeys.SEASON_BACKUP_SHA256));
        return 1;
    }

    static void printCommit(
            final CommandSourceStack source,
            final CommandContext<CommandSourceStack> context,
            final SeasonResetService.Outcome outcome) {
        GovCommand.send(context, "Season reset committed.");
        GovCommand.send(context, "  Cleared: " + SeasonResetService.countsText(outcome.plan().counts()));
        GovCommand.send(context, "  Players reset to citizen: "
                + outcome.plan().counts().getOrDefault("player-jobs", 0));
        if (outcome.backup() != null) {
            GovCommand.send(context, "  Backup: " + outcome.backup().relativePath()
                    + "  Bytes: " + outcome.backup().bytes());
            GovCommand.send(context, "  sha256: " + outcome.backup().sha256());
        }
    }
}
