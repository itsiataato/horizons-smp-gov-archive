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

public final class GovPersonCommandHandlers {
    private GovPersonCommandHandlers() {}

    static int personLookup(final CommandContext<CommandSourceStack> context) {
        final CommandSourceStack source = context.getSource();
        final GovSavedData data = GovSavedData.get(source.getServer());
        final Set<GovCapability> capabilities = GovAuditCommandHandlers.readCapabilities(source, data);
        if (!capabilities.contains(GovCapability.PLAYER_LOOKUP)) {
            source.sendFailure(Component.literal(
                    "You need " + GovCapabilities.requiredFor(OpCode.LOOKUP_PERSON).id()
                            + " for that action."));
            return 0;
        }
        final String raw = StringArgumentType.getString(context, "player");
        final GovTargets.Resolved target = GovTargets.resolve(source.getServer(), data, raw);
        if (target == null) {
            source.sendFailure(Component.literal(PersonService.noSuchPersonMessage(raw)));
            return 0;
        }
        final Map<String, String> fields = PersonService.detail(
                data, target.uuid(), target.name(), target.online(), capabilities);

        GovCommand.send(context, "Government record: " + GovAuditCommandHandlers.value(fields, PersonKeys.NAME));
        GovCommand.send(context, "  UUID: " + GovAuditCommandHandlers.value(fields, PersonKeys.UUID));
        GovCommand.send(context, "  Online: " + GovAuditCommandHandlers.yesNo("true".equals(fields.get(PersonKeys.ONLINE))));
        GovCommand.send(context, "  Job: " + GovAuditCommandHandlers.jobText(fields));
        GovCommand.send(context, "  Rank: " + GovAuditCommandHandlers.value(fields, PersonKeys.RANK));
        GovCommand.send(context, "  Notes: " + GovAuditCommandHandlers.value(fields, PersonKeys.NOTES));
        GovCommand.send(context, "  Unpaid fines: " + GovAuditCommandHandlers.value(fields, PersonKeys.FINES));
        GovCommand.send(context, "  Licenses: " + GovAuditCommandHandlers.value(fields, PersonKeys.LICENSE));
        if (!"true".equals(fields.get(PersonKeys.CAN_VIEW_NOTES))) {
            GovCommand.send(context, "  (notes are hidden; you need "
                    + GovCapability.NOTE_VIEW_ALL.id() + ")");
        }
        return 1;
    }
    public static void register(final LiteralArgumentBuilder<CommandSourceStack> root) {
                root.then(Commands.literal("person")
                        .executes(context -> {
                            context.getSource().sendSuccess(
                                    () -> Component.literal("Usage: /gov person <name>"), false);
                            return 1;
                        })
                        .then(Commands.argument("player", StringArgumentType.word()).suggests(GovSuggestions.PLAYERS)
                                .executes(GovPersonCommandHandlers::personLookup)));
    }
}
