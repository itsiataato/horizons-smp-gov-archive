package com.horizonssmp.gov.command;
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

public final class GovJailCommandHandlers {
    private GovJailCommandHandlers() {}


    record RecordTarget(UUID uuid, String name) {
    }

    static RecordTarget recordTarget(
            final CommandSourceStack source, final GovSavedData data, final String raw) {
        final GovTargets.Resolved resolved = GovTargets.resolveLocal(
                source.getServer(), data, raw);
        return resolved == null ? null : new RecordTarget(resolved.uuid(), resolved.name());
    }

    static int jailList(final CommandContext<CommandSourceStack> context,
            final String rawScope) {
        final CommandSourceStack source = context.getSource();
        final GovSavedData data = GovSavedData.get(source.getServer());
        final Set<GovCapability> capabilities = GovAuditCommandHandlers.readCapabilities(source, data);
        final ServerPlayer viewer = source.getPlayer();
        final UUID viewerUuid = viewer == null ? null : viewer.getUUID();
        final String viewerName = viewer == null ? "console" : viewer.getName().getString();

        UUID targetFilter = null;
        final String scope = rawScope == null ? "all" : rawScope.trim();
        if (!scope.isEmpty() && !JailKeys.FILTER_ALL.equalsIgnoreCase(scope)) {
            if (!DetentionService.canViewAll(capabilities)) {
                source.sendFailure(Component.literal(
                        "You can only read your own detention record."));
                return 0;
            }
            final RecordTarget target = recordTarget(source, data, scope);
            if (target == null) {
                source.sendFailure(Component.literal("No player named '" + scope + "'."));
                return 0;
            }
            targetFilter = target.uuid();
        }

        final List<String> names =
                com.horizonssmp.gov.server.GovHandlerSupport.onlineNames(source.getServer());

        final Map<String, String> fields = DetentionService.listFields(
                data, viewerUuid, viewerName, capabilities, JailKeys.FILTER_ACTIVE, targetFilter,
                0, names, DetentionService.Policy.fromConfig(), System.currentTimeMillis());
        final int total = parseIntField(fields, JailKeys.TOTAL);
        final int active = parseIntField(fields, JailKeys.ACTIVE_TOTAL);
        final int pages = Math.max(1, parseIntField(fields, JailKeys.PAGES));
        GovCommand.send(context, "Detentions: " + total + " in this view, " + active + " active.");
        final int listed = Math.min(JailKeys.PAGE_SIZE, Math.max(0, total));
        for (int i = 0; i < listed; i++) {
            final String prefix = JailKeys.ROW_PREFIX + i;
            final String id = fields.get(prefix + JailKeys.ROW_ID);
            if (id == null || id.isBlank()) {
                continue;
            }
            GovCommand.send(context, "  " + id + "  " + fields.getOrDefault(prefix + JailKeys.ROW_TARGET, "")
                    + "  " + fields.getOrDefault(prefix + JailKeys.ROW_BASIS, "")
                    + "  " + fields.getOrDefault(prefix + JailKeys.ROW_STATUS, "")
                    + "  " + fields.getOrDefault(prefix + JailKeys.ROW_OFFICER, "")
                    + "  " + GovJailMutationCommandHandlers.remaining(GovAuditCommandHandlers.parseLongField(fields,
                            prefix + JailKeys.ROW_REMAINING_MS))
                    + ("true".equals(fields.get(prefix + JailKeys.ROW_CUFFED)) ? "  cuffed" : ""));
        }
        if (total == 0) {
            GovCommand.send(context, "  No active detentions.");
        } else if (total > listed) {
            GovCommand.send(context, "  showing " + listed + " of " + total + "; page 1 of " + pages + ".");
        }
        return 1;
    }

    static int jailInfo(final CommandContext<CommandSourceStack> context) {
        final CommandSourceStack source = context.getSource();
        final GovSavedData data = GovSavedData.get(source.getServer());
        final Set<GovCapability> capabilities = GovAuditCommandHandlers.readCapabilities(source, data);
        final String raw = StringArgumentType.getString(context, "player");
        final RecordTarget target = recordTarget(source, data, raw);
        if (target == null) {
            source.sendFailure(Component.literal(
                    "There is no detention on record for '" + Sanitizer.singleLine(raw, 32) + "'."));
            return 0;
        }
        if (!DetentionService.canViewAll(capabilities)
                && !target.uuid().equals(source.getPlayer() == null
                        ? null : source.getPlayer().getUUID())) {
            source.sendFailure(Component.literal(
                    "You can only read your own detention record."));
            return 0;
        }
        final var active = DetentionService.activeRecord(data, target.uuid());
        final var record = active != null
                ? active : DetentionService.latestRecord(data, target.uuid());
        if (record == null) {
            source.sendFailure(Component.literal(
                    "There is no detention on record for " + target.name() + "."));
            return 0;
        }
        final Map<String, String> fields = DetentionService.detailFields(
                data, record, capabilities,
                source.getPlayer() == null ? null : source.getPlayer().getUUID(),
                DetentionService.Policy.fromConfig(), System.currentTimeMillis());
        GovCommand.send(context, "Detention " + GovAuditCommandHandlers.value(fields, JailKeys.ID)
                + "  target " + GovAuditCommandHandlers.value(fields, JailKeys.TARGET)
                + "  status " + GovAuditCommandHandlers.value(fields, JailKeys.STATUS));
        GovCommand.send(context, "  Basis: " + GovAuditCommandHandlers.value(fields, JailKeys.BASIS)
                + (GovAuditCommandHandlers.value(fields, JailKeys.WARRANT_REF).equals("-") ? ""
                        : "  warrant " + GovAuditCommandHandlers.value(fields, JailKeys.WARRANT_REF)));
        GovCommand.send(context, "  Officer: " + GovAuditCommandHandlers.value(fields, JailKeys.OFFICER)
                + "  Started: " + GovAuditCommandHandlers.when(GovAuditCommandHandlers.parseLongField(fields, JailKeys.STARTED_AT)));
        GovCommand.send(context, "  Term: " + GovAuditCommandHandlers.value(fields, JailKeys.MAX_MINUTES) + " minutes  expires "
                + GovAuditCommandHandlers.when(GovAuditCommandHandlers.parseLongField(fields, JailKeys.EXPIRES_AT))
                + "  remaining " + GovJailMutationCommandHandlers.remaining(GovAuditCommandHandlers.parseLongField(fields, JailKeys.REMAINING_MS)));
        GovCommand.send(context, "  Reason: " + GovAuditCommandHandlers.value(fields, JailKeys.REASON));
        GovCommand.send(context, "  Cuffed: " + GovAuditCommandHandlers.yesNo("true".equals(fields.get(JailKeys.CUFFED)))
                + "  Cell: " + GovAuditCommandHandlers.value(fields, JailKeys.CELL_LOCATION)
                + "  Adapter: " + GovAuditCommandHandlers.value(fields, JailKeys.RECORD_ADAPTER));
        if (GovAuditCommandHandlers.parseLongField(fields, JailKeys.RELEASED_AT) > 0L) {
            GovCommand.send(context, "  Released: " + GovAuditCommandHandlers.when(GovAuditCommandHandlers.parseLongField(fields, JailKeys.RELEASED_AT))
                    + " by " + GovAuditCommandHandlers.value(fields, JailKeys.RELEASED_BY)
                    + "  reason " + GovAuditCommandHandlers.value(fields, JailKeys.RELEASE_REASON));
        }
        if (parseIntField(fields, JailKeys.EXTENSION_COUNT) > 0) {
            GovCommand.send(context, "  Extensions: " + GovAuditCommandHandlers.value(fields, JailKeys.EXTENSION_COUNT)
                    + "  last by " + GovAuditCommandHandlers.value(fields, JailKeys.EXTENDED_BY)
                    + "  reason " + GovAuditCommandHandlers.value(fields, JailKeys.EXTENSION_REASON));
        }
        return 1;
    }

    static int parseIntField(final Map<String, String> fields, final String key) {
        return GovFields.intField(fields, key, 0);
    }
}
