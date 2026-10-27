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

public final class GovBusinessCommandTree {
    private GovBusinessCommandTree() {}

    public static void register(final LiteralArgumentBuilder<CommandSourceStack> root) {
                root.then(Commands.literal("business")
                        .executes(context -> {
                            context.getSource().sendSuccess(
                                    () -> Component.literal(
                                            "Usage: /gov business list [player|all] | info <name>"
                                                    + " | register <name> <type> [owner]"
                                                    + " | close <name> | suspend <name> <reason...>"
                                                    + " | employee add <name> <player> [role]"
                                                    + " | employee remove <name> <player>"
                                                    + " | link <name> <jobId> | unlink <name>"
                                                    + " | payroll info <name>"
                                                    + " | payroll set <name> <ratePerDay> [note...]"
                                                    + " | payroll record <name> <amount> [note...]"
                                                    + "  (quote a name that contains spaces)"),
                                    false);
                            return 1;
                        })
                        .then(Commands.literal("list")
                                .executes(context -> GovBusinessCommandHandlers.businessList(context, "all"))
                                .then(Commands.argument("scope", StringArgumentType.word()).suggests(GovSuggestions.SCOPES)
                                        .executes(context -> GovBusinessCommandHandlers.businessList(context,
                                                StringArgumentType.getString(context, "scope")))))
                        .then(Commands.literal("info")
                                .then(Commands.argument("name", StringArgumentType.string()).suggests(GovSuggestions.BUSINESSES)
                                        .executes(GovBusinessCommandHandlers::businessInfo)))
                        .then(Commands.literal("register")
                                .then(Commands.argument("name", StringArgumentType.string()).suggests(GovSuggestions.BUSINESSES)
                                        .then(Commands.argument("type", StringArgumentType.word()).suggests(GovSuggestions.BUSINESS_TYPES)
                                                .executes(context -> GovBusinessCommandHandlers.businessRegister(context, ""))
                                                .then(Commands.argument("owner",
                                                                StringArgumentType.word()).suggests(GovSuggestions.PLAYERS)
                                                        .executes(context -> GovBusinessCommandHandlers.businessRegister(context,
                                                                StringArgumentType.getString(
                                                                        context, "owner")))))))
                        .then(Commands.literal("close")
                                .then(Commands.argument("name", StringArgumentType.string()).suggests(GovSuggestions.BUSINESSES)
                                        .executes(GovBusinessManageCommandHandlers::businessClose)))
                        .then(Commands.literal("suspend")
                                .then(Commands.argument("name", StringArgumentType.string()).suggests(GovSuggestions.BUSINESSES)
                                        .then(Commands.argument("reason",
                                                        StringArgumentType.greedyString())
                                                .executes(GovBusinessManageCommandHandlers::businessSuspend))))
                        .then(Commands.literal("employee")
                                .executes(context -> {
                                    context.getSource().sendSuccess(
                                            () -> Component.literal(
                                                    "Usage: /gov business employee add <name> <player> [role]"
                                                            + " | remove <name> <player>"),
                                            false);
                                    return 1;
                                })
                                .then(Commands.literal("add")
                                        .then(Commands.argument("name",
                                                        StringArgumentType.string()).suggests(GovSuggestions.BUSINESSES)
                                                .then(Commands.argument("player",
                                                                StringArgumentType.word()).suggests(GovSuggestions.PLAYERS)
                                                        .executes(context -> GovBusinessManageCommandHandlers.businessEmployeeAdd(
                                                                context, "worker"))
                                                        .then(Commands.argument("role",
                                                                        StringArgumentType.word())
                                                                .executes(context ->
                                                                        GovBusinessManageCommandHandlers.businessEmployeeAdd(
                                                                                context,
                                                                                StringArgumentType
                                                                                        .getString(
                                                                                                context,
                                                                                                "role")))))))
                                .then(Commands.literal("remove")
                                        .then(Commands.argument("name",
                                                        StringArgumentType.string()).suggests(GovSuggestions.BUSINESSES)
                                                .then(Commands.argument("player",
                                                                StringArgumentType.word()).suggests(GovSuggestions.PLAYERS)
                                                        .executes(GovBusinessManageCommandHandlers::businessEmployeeRemove)))))
                        .then(Commands.literal("link")
                                .then(Commands.argument("name", StringArgumentType.string()).suggests(GovSuggestions.BUSINESSES)
                                        .then(Commands.argument("job", StringArgumentType.word()).suggests(GovSuggestions.JOBS)
                                                .executes(GovBusinessManageCommandHandlers::businessLink))))
                        .then(Commands.literal("unlink")
                                .then(Commands.argument("name", StringArgumentType.string()).suggests(GovSuggestions.BUSINESSES)
                                        .executes(GovBusinessManageCommandHandlers::businessUnlink)))
                        .then(Commands.literal("payroll")
                                .executes(context -> {
                                    context.getSource().sendSuccess(
                                            () -> Component.literal(
                                                    "Usage: /gov business payroll info <name>"
                                                            + " | set <name> <ratePerDay> [note...]"
                                                            + " | record <name> <amount> [note...]"),
                                            false);
                                    return 1;
                                })
                                .then(Commands.literal("info")
                                        .then(Commands.argument("name", StringArgumentType.string()).suggests(GovSuggestions.BUSINESSES)
                                                .executes(GovBusinessManageCommandHandlers::businessPayrollInfo)))
                                .then(Commands.literal("set")
                                        .then(Commands.argument("name", StringArgumentType.string()).suggests(GovSuggestions.BUSINESSES)
                                                .then(Commands.argument("ratePerDay",
                                                                StringArgumentType.word())
                                                        .executes(context -> GovBusinessManageCommandHandlers.businessPayrollSet(
                                                                context, ""))
                                                        .then(Commands.argument("note",
                                                                        StringArgumentType
                                                                                .greedyString())
                                                                .executes(context ->
                                                                        GovBusinessManageCommandHandlers.businessPayrollSet(
                                                                                context,
                                                                                StringArgumentType
                                                                                        .getString(
                                                                                                context,
                                                                                                "note")))))))
                                .then(Commands.literal("record")
                                        .then(Commands.argument("name", StringArgumentType.string()).suggests(GovSuggestions.BUSINESSES)
                                                .then(Commands.argument("amount",
                                                                StringArgumentType.word())
                                                        .executes(context -> GovBusinessManageCommandHandlers.businessPayrollRecord(
                                                                context, ""))
                                                        .then(Commands.argument("note",
                                                                        StringArgumentType
                                                                                .greedyString())
                                                                .executes(context ->
                                                                        GovBusinessManageCommandHandlers.businessPayrollRecord(
                                                                                context,
                                                                                StringArgumentType
                                                                                        .getString(
                                                                                                context,
                                                                                                "note")))))))));
    }
}
