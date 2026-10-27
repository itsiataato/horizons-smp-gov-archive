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

public final class GovAuditCommandHandlers {
    private GovAuditCommandHandlers() {}

    static long parseLongField(final Map<String, String> fields, final String key) {
        return GovFields.longField(fields, key);
    }

    static int auditList(
            final CommandContext<CommandSourceStack> context,
            final String type, final String actor, final String action, final String target) {
        final CommandSourceStack source = context.getSource();
        final GovSavedData data = GovSavedData.get(source.getServer());
        final Set<GovCapability> capabilities = readCapabilities(source, data);
        if (!capabilities.contains(GovCapability.AUDIT_VIEW)) {
            source.sendFailure(Component.literal(
                    "You need " + GovCapability.AUDIT_VIEW.id() + " for that action."));
            return 0;
        }
        final AuditService.Filter filter = AuditService.filter(
                "actor".equals(type) ? actor : "",
                "action".equals(type) ? action : "",
                "target".equals(type) ? target : "",
                "", AuditKeys.DEFAULT_WINDOW, System.currentTimeMillis());
        final Map<String, String> fields =
                AuditService.listFields(data, filter, 0, capabilities);
        GovCommand.send(context, "Audit log: " + value(fields, AuditKeys.COUNT) + " retained"
                + " (oldest " + when(parseLongField(fields, AuditKeys.OLDEST_AT))
                + ", newest " + when(parseLongField(fields, AuditKeys.NEWEST_AT)) + "), "
                + value(fields, AuditKeys.MATCHING) + " matching, page "
                + (GovJailCommandHandlers.parseIntField(fields, AuditKeys.PAGE) + 1) + " of "
                + value(fields, AuditKeys.PAGES) + " (last 7 days)");
        final int rows = GovJailCommandHandlers.parseIntField(fields, AuditKeys.MATCHING);
        if (rows == 0) {
            GovCommand.send(context, "  No audit entries match. Only accepted changes and safety-critical");
            GovCommand.send(context, "  refusals are recorded; ordinary refusals are DEBUG log lines.");
            return 1;
        }
        for (int i = 0; i < AuditKeys.PAGE_SIZE; i++) {
            final String prefix = AuditKeys.ROW_PREFIX + i;
            final String seq = fields.get(prefix + AuditKeys.ROW_SEQ);
            if (seq == null || seq.isBlank()) {
                continue;
            }
            final String targetName = fields.getOrDefault(prefix + AuditKeys.ROW_TARGET, "");
            GovCommand.send(context, "  #" + seq + "  " + when(parseLongField(fields, prefix + AuditKeys.ROW_AT))
                    + "  " + value(fields, prefix + AuditKeys.ROW_ACTOR)
                    + "  " + value(fields, prefix + AuditKeys.ROW_ACTION)
                    + (targetName.isBlank() ? "" : " -> " + targetName)
                    + "  [" + value(fields, prefix + AuditKeys.ROW_OUTCOME) + "]");
        }
        if (GovJailCommandHandlers.parseIntField(fields, AuditKeys.PAGES) > 1) {
            GovCommand.send(context, "  The Audit tab pages through the rest; /gov audit info <seq> shows one line in full.");
        }
        return 1;
    }

    static int auditInfo(final CommandContext<CommandSourceStack> context) {
        final CommandSourceStack source = context.getSource();
        final GovSavedData data = GovSavedData.get(source.getServer());
        final Set<GovCapability> capabilities = readCapabilities(source, data);
        if (!capabilities.contains(GovCapability.AUDIT_VIEW)) {
            source.sendFailure(Component.literal(
                    "You need " + GovCapability.AUDIT_VIEW.id() + " for that action."));
            return 0;
        }
        final String raw = StringArgumentType.getString(context, "seq");
        final long seq = parseLongValue(raw, -1L);
        final AuditEntry entry = AuditService.bySeq(data, seq);
        if (entry == null) {
            source.sendFailure(Component.literal(AuditService.noSuchEntryMessage(raw)));
            return 0;
        }
        GovCommand.send(context, "Audit entry #" + entry.seq());
        GovCommand.send(context, "  At: " + when(entry.at()));
        GovCommand.send(context, "  Outcome: " + entry.outcome().name());
        GovCommand.send(context, "  Actor: " + dash(entry.actorName())
                + (entry.actorUuid() == null ? "" : "  (" + entry.actorUuid() + ")")
                + (entry.actorJobId().isBlank() ? "" : "  job=" + entry.actorJobId()));
        GovCommand.send(context, "  Action: " + entry.action());
        GovCommand.send(context, "  Target: " + dash(entry.targetName())
                + (entry.targetUuid() == null ? "" : "  (" + entry.targetUuid() + ")"));
        GovCommand.send(context, "  Payload: " + dash(entry.payload()));
        GovCommand.send(context, "  Reason: " + dash(entry.reason()));
        return 1;
    }

    static int auditExport(final CommandContext<CommandSourceStack> context) {
        final CommandSourceStack source = context.getSource();
        final GovSavedData data = GovSavedData.get(source.getServer());
        final Set<GovCapability> capabilities = readCapabilities(source, data);
        if (!capabilities.contains(GovCapability.AUDIT_VIEW)) {
            source.sendFailure(Component.literal(
                    "You need " + GovCapability.AUDIT_VIEW.id() + " for that action."));
            return 0;
        }
        final ServerPlayer player = source.getPlayer();
        if (player != null) {
            return GovAuditAdminCommandHandlers.replyExport(context, GovActionDispatcher.dispatch(
                    player, OpCode.AUDIT_EXPORT, Map.of()));
        }
        final GovExportService.Result result = GovExportService.exportSection(
                data, source.getServer().getServerDirectory(),
                GovExportSection.AUDIT, System.currentTimeMillis());
        return GovAuditAdminCommandHandlers.replyConsoleExport(context, result, GovExportSection.AUDIT, "AUDIT_EXPORT");
    }

    static SeasonResetService.Actor consoleOrPlayer(final CommandSourceStack source) {
        final ServerPlayer player = source.getPlayer();
        return player == null
                ? SeasonResetService.Actor.console()
                : new SeasonResetService.Actor(player.getUUID(), player.getName().getString());
    }

    static long parseLongValue(final String raw, final long fallback) {
        return GovFields.parseLong(raw, fallback);
    }

    static String dash(final String value) {
        return GovFields.dash(value);
    }

    static Set<GovCapability> readCapabilities(
            final CommandSourceStack source, final GovSavedData data) {
        final ServerPlayer player = source.getPlayer();
        if (player == null) {
            // Console and command blocks hold no job record, so there is nothing to derive
            // capabilities from. Level 2 matches the jail console mutations' gate: an operator
            // console keeps full read access, anything below sees nothing rather than everything.
            return source.hasPermission(2)
                    ? EnumSet.allOf(GovCapability.class)
                    : EnumSet.noneOf(GovCapability.class);
        }
        final PlayerRecord record = data.playerOrCreate(
                player.getUUID(), player.getName().getString(), System.currentTimeMillis());
        if (GovCapabilities.isOperator(player)) {
            return java.util.EnumSet.allOf(GovCapability.class);
        }
        return GovCapabilities.effective(record, data::job);
    }

    static String value(final Map<String, String> fields, final String key) {
        return GovFields.value(fields, key);
    }

    static String jobText(final Map<String, String> fields) {
        final String job = value(fields, PersonKeys.JOB);
        final String id = value(fields, PersonKeys.JOB_ID);
        return "-".equals(job) ? "-" : (job + " (" + id + ")");
    }

    static String yesNo(final boolean value) {
        return value ? "yes" : "no";
    }

    static String when(final long epochMillis) {
        return GovFields.when(epochMillis);
    }
}
