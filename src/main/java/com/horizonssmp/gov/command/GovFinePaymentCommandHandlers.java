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

public final class GovFinePaymentCommandHandlers {
    private GovFinePaymentCommandHandlers() {}

    record IssueArgs(String article, String restitution, String victim, String exile, String reason) {
    }

    static int finePay(final CommandContext<CommandSourceStack> context) {
        final CommandSourceStack source = context.getSource();
        final GovSavedData data = GovSavedData.get(source.getServer());
        final String raw = StringArgumentType.getString(context, "fine");
        final ServerPlayer player = source.getPlayer();
        final FineService.Outcome precheck = FineService.payPrecheck(
                data, player == null ? null : player.getUUID(), raw);
        if (precheck != null) {
            source.sendFailure(Component.literal(precheck.message()));
            return 0;
        }
        if (player == null) {
            source.sendFailure(Component.literal("/gov fine pay can only be used by a player."));
            return 0;
        }
        return GovCommand.dispatch(context, "/gov fine pay", OpCode.FINE_PAY,
                Map.of("target", player.getName().getString(),
                        FineKeys.KEY_FINE_ID, Sanitizer.id(raw)));
    }

    static int fineWaive(final CommandContext<CommandSourceStack> context) {
        final CommandSourceStack source = context.getSource();
        final GovSavedData data = GovSavedData.get(source.getServer());
        final String raw = StringArgumentType.getString(context, "fine");
        final String reason = StringArgumentType.getString(context, "reason");
        final FineService.Outcome precheck = FineService.waivePrecheck(data, raw, reason);
        if (precheck != null) {
            source.sendFailure(Component.literal(precheck.message()));
            return 0;
        }
        final FineRecord fine = FineService.find(data, raw);
        if (fine == null) {
            source.sendFailure(Component.literal(FineService.noSuchFineMessage(raw)));
            return 0;
        }
        if (source.getPlayer() == null) {
            source.sendFailure(Component.literal("/gov fine waive can only be used by a player."));
            return 0;
        }
        return GovCommand.dispatch(context, "/gov fine waive", OpCode.FINE_WAIVE,
                Map.of("target", fine.targetUuid().toString(),
                        FineKeys.KEY_FINE_ID, fine.id(),
                        FineKeys.KEY_REASON, reason));
    }

    static IssueArgs parseIssueArgs(final String rest) {
        final String text = rest == null ? "" : rest.strip();
        String article = FineService.DEFAULT_ARTICLE;
        String restitution = "0";
        String victim = "";
        String exile = "";
        if (text.isEmpty()) {
            return new IssueArgs(article, restitution, victim, exile, "");
        }
        final List<String> tokens = new ArrayList<>(List.of(text.split("\\s+")));
        int index = 0;
        if (index < tokens.size() && tokens.get(index).matches("[0-9]+\\.[0-9]+")) {
            article = tokens.get(index++);
        }
        if (index < tokens.size() && GovFinePaymentCommandHandlers.isDigits(tokens.get(index))) {
            restitution = tokens.get(index++);
        }
        while (index < tokens.size()) {
            final String token = tokens.get(index);
            if (token.regionMatches(true, 0, "victim=", 0, 7)) {
                victim = token.substring(7);
                index++;
            } else if (token.regionMatches(true, 0, "exile=", 0, 6)) {
                exile = token.substring(6);
                index++;
            } else {
                break;
            }
        }
        final String reason = String.join(" ", tokens.subList(index, tokens.size())).strip();
        return new IssueArgs(article, restitution, victim, exile, reason);
    }

    static boolean isDigits(final String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }
        for (int i = 0; i < token.length(); i++) {
            if (token.charAt(i) < '0' || token.charAt(i) > '9') {
                return false;
            }
        }
        return true;
    }
}
