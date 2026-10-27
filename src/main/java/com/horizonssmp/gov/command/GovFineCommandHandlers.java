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

public final class GovFineCommandHandlers {
    private GovFineCommandHandlers() {}


    static int fineList(final CommandContext<CommandSourceStack> context, final String scope) {
        final CommandSourceStack source = context.getSource();
        final GovSavedData data = GovSavedData.get(source.getServer());
        final Set<GovCapability> capabilities = GovAuditCommandHandlers.readCapabilities(source, data);
        final ServerPlayer self = source.getPlayer();
        final boolean viewAll = FineService.canViewAll(capabilities);

        UUID filter = null;
        if (!"all".equalsIgnoreCase(scope)) {
            final GovTargets.Resolved target = GovTargets.resolve(source.getServer(), data, scope);
            if (target == null) {
                source.sendFailure(Component.literal(PersonService.noSuchPersonMessage(scope)));
                return 0;
            }
            if (!viewAll && (self == null || !target.uuid().equals(self.getUUID()))) {
                source.sendFailure(Component.literal(
                        "You need " + GovCapability.FINE_VIEW_ALL.id()
                                + " to list another person's fines."));
                return 0;
            }
            filter = target.uuid();
        } else if (!viewAll) {
            if (self == null) {
                source.sendFailure(Component.literal(
                        "You need " + GovCapability.FINE_VIEW_ALL.id() + " to list all fines."));
                return 0;
            }
            filter = self.getUUID();
        }

        final FineService.Page page = FineService.page(
                data,
                self == null ? null : self.getUUID(),
                capabilities,
                FineKeys.FILTER_ALL,
                filter,
                0);
        final String heading = filter == null ? "all fines" : "fines for " + scope;
        GovCommand.send(context, "Government fines (" + heading + "): " + page.total() + " total");
        if (page.rows().isEmpty()) {
            GovCommand.send(context, "  (none)");
            return 1;
        }
        for (final FineService.FineRow row : page.rows()) {
            final FineRecord fine = row.fine();
            final StringBuilder line = new StringBuilder("  [").append(fine.id()).append("] ")
                    .append(row.targetName()).append("  ").append(fine.band().name()).append(' ')
                    .append(fine.amount().count()).append("g");
            if (fine.restitution().count() > 0L) {
                line.append(" + ").append(fine.restitution().count()).append("g restitution to ")
                        .append(fine.victim());
            }
            line.append("  ").append(fine.status().name())
                    .append("  article ").append(fine.lawArticle());
            GovCommand.send(context, line.toString());
            GovCommand.send(context, "      " + Sanitizer.singleLine(fine.reason(), Sanitizer.MAX_TEXT));
        }
        if (page.pages() > 1) {
            GovCommand.send(context, "  Page 1 of " + page.pages()
                    + "; the Fines tab pages through the rest.");
        }
        return 1;
    }

    static int fineInfo(final CommandContext<CommandSourceStack> context) {
        final CommandSourceStack source = context.getSource();
        final GovSavedData data = GovSavedData.get(source.getServer());
        final Set<GovCapability> capabilities = GovAuditCommandHandlers.readCapabilities(source, data);
        final ServerPlayer self = source.getPlayer();
        final String raw = StringArgumentType.getString(context, "fine");
        final FineRecord fine = FineService.visibleFine(
                data, self == null ? null : self.getUUID(), capabilities, raw);
        if (fine == null) {
            source.sendFailure(Component.literal(FineService.noSuchFineMessage(raw)));
            return 0;
        }

        GovCommand.send(context, "Fine " + fine.id() + "  " + fine.status().name());
        GovCommand.send(context, "  Target: " + FineService.targetName(data, fine.targetUuid())
                + " (" + fine.targetUuid() + ")");
        GovCommand.send(context, "  Band: " + fine.band().name() + "  Amount: "
                + fine.amount().count() + " gold ingots");
        if (fine.restitution().count() > 0L) {
            GovCommand.send(context, "  Restitution: " + fine.restitution().count()
                    + " gold ingots to " + fine.victim());
        }
        GovCommand.send(context, "  Total due at reception: " + fine.totalDue().count() + " gold ingots");
        GovCommand.send(context, "  Article: " + fine.lawArticle()
                + LawIndex.get().article(fine.lawArticle())
                        .map(article -> " - " + article.title())
                        .orElse(""));
        GovCommand.send(context, "  Issued: " + GovAuditCommandHandlers.when(fine.createdAt()));
        GovCommand.send(context, "  Reason: " + Sanitizer.singleLine(fine.reason(), Sanitizer.MAX_TEXT));
        if (fine.status() == com.horizonssmp.gov.common.enums.FineStatus.PAID) {
            GovCommand.send(context, "  Receipt: " + fine.paidAtReceptionRef() + "  paid " + GovAuditCommandHandlers.when(fine.paidAt())
                    + "  by " + (fine.selfPaid() ? "self" : fine.collectedBy()));
        }
        if (fine.status() == com.horizonssmp.gov.common.enums.FineStatus.WAIVED) {
            GovCommand.send(context, "  Waived by: " + FineService.targetName(data, fine.waivedBy())
                    + "  reason: " + Sanitizer.singleLine(fine.waivedReason(), Sanitizer.MAX_TEXT));
        }
        if (fine.exileOrdered()) {
            GovCommand.send(context, "  Exile order: " + fine.exileDays()
                    + " in-game days (staff enforcement; not automated in this build)");
        }
        return 1;
    }

    static int fineIssue(final CommandContext<CommandSourceStack> context, final String rest) {
        final CommandSourceStack source = context.getSource();
        final GovSavedData data = GovSavedData.get(source.getServer());
        final String player = StringArgumentType.getString(context, "player");
        final String band = StringArgumentType.getString(context, "band");
        final GovFinePaymentCommandHandlers.IssueArgs args = GovFinePaymentCommandHandlers.parseIssueArgs(rest);

        final FineService.Outcome precheck = FineService.issuePrecheck(
                band, args.article(), args.restitution(), args.victim(), args.exile(), args.reason());
        if (precheck != null) {
            source.sendFailure(Component.literal(precheck.message()));
            return 0;
        }
        final ServerPlayer actor = source.getPlayer();
        if (actor == null) {
            source.sendFailure(Component.literal(
                    "/gov fine issue can only be used by a player."));
            return 0;
        }
        final GovTargets.Resolved target = GovTargets.resolve(source.getServer(), data, player);
        if (target == null) {
            source.sendFailure(Component.literal(PersonService.noSuchPersonMessage(player)));
            return 0;
        }
        return GovCommand.dispatch(context, "/gov fine issue", OpCode.FINE_ISSUE,
                Map.of(
                        "target", player,
                        FineKeys.KEY_BAND, band,
                        FineKeys.KEY_ARTICLE, args.article(),
                        FineKeys.KEY_RESTITUTION, args.restitution(),
                        FineKeys.KEY_VICTIM, args.victim(),
                        FineKeys.KEY_EXILE_DAYS, args.exile(),
                        FineKeys.KEY_REASON, args.reason()));
    }
}
