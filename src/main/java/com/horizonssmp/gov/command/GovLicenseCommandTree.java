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

public final class GovLicenseCommandTree {
    private GovLicenseCommandTree() {}

    public static void register(final LiteralArgumentBuilder<CommandSourceStack> root) {
                root.then(Commands.literal("license")
                        .executes(context -> {
                            context.getSource().sendSuccess(
                                    () -> Component.literal(
                                            "Usage: /gov license issue <player> <class>"
                                                    + " | revoke <player> <class> <reason...>"
                                                    + " | points <player> <class> <2|4|6> <reason...>"),
                                    false);
                            return 1;
                        })
                        .then(Commands.literal("issue")
                                .then(Commands.argument("player", StringArgumentType.word()).suggests(GovSuggestions.PLAYERS)
                                        .then(Commands.argument("class", StringArgumentType.word()).suggests(GovSuggestions.LICENSE_CLASSES)
                                                .executes(GovLicenseCommandHandlers::licenseIssue))))
                        .then(Commands.literal("revoke")
                                .then(Commands.argument("player", StringArgumentType.word()).suggests(GovSuggestions.PLAYERS)
                                        .then(Commands.argument("class", StringArgumentType.word()).suggests(GovSuggestions.LICENSE_CLASSES)
                                                .then(Commands.argument("reason", StringArgumentType.greedyString())
                                                        .executes(GovLicenseCommandHandlers::licenseRevoke)))))
                        .then(Commands.literal("points")
                                .then(Commands.argument("player", StringArgumentType.word()).suggests(GovSuggestions.PLAYERS)
                                        .then(Commands.argument("class", StringArgumentType.word()).suggests(GovSuggestions.LICENSE_CLASSES)
                                                .then(Commands.argument("points", StringArgumentType.word())
                                                        .then(Commands.argument("reason",
                                                                        StringArgumentType.greedyString())
                                                                .executes(GovLicenseCommandHandlers::licensePoints)))))))
                        .then(Commands.literal("weapon")
                                .executes(context -> {
                                    context.getSource().sendSuccess(
                                            () -> Component.literal(
                                                    "Usage: /gov weapon list [player|all] | info <serial>"
                                                            + " | register <player> <class> <serial> <type>"
                                                            + " | transfer <serial> <player>"
                                                            + " | revoke <serial> <reason...>"
                                                            + " | loss <serial> <reason...>"
                                                            + " | classify [itemId]"
                                                            + " | permit <list|issue|revoke|suspend>"),
                                            false);
                                    return 1;
                                })
                                .then(Commands.literal("list")
                                        .executes(context -> GovWeaponCommandHandlers.weaponList(context, "all"))
                                        .then(Commands.argument("scope", StringArgumentType.word()).suggests(GovSuggestions.SCOPES)
                                                .executes(context -> GovWeaponCommandHandlers.weaponList(context,
                                                        StringArgumentType.getString(context, "scope")))))
                                .then(Commands.literal("info")
                                        .then(Commands.argument("serial", StringArgumentType.string()).suggests(GovSuggestions.WEAPONS)
                                                .executes(GovWeaponCommandHandlers::weaponInfo)))
                                .then(Commands.literal("register")
                                        .then(Commands.argument("player", StringArgumentType.word()).suggests(GovSuggestions.PLAYERS)
                                                .then(Commands.argument("class", StringArgumentType.word()).suggests(GovSuggestions.LICENSE_CLASSES)
                                                        .then(Commands.argument("serial",
                                                                        StringArgumentType.string()).suggests(GovSuggestions.WEAPONS)
                                                                .then(Commands.argument("type",
                                                                                StringArgumentType.greedyString())
                                                                        .executes(
                                                                                GovWeaponCommandHandlers::weaponRegister))))))
                                .then(Commands.literal("transfer")
                                        .then(Commands.argument("serial", StringArgumentType.string()).suggests(GovSuggestions.WEAPONS)
                                                .then(Commands.argument("player", StringArgumentType.word()).suggests(GovSuggestions.PLAYERS)
                                                        .executes(GovWeaponCommandHandlers::weaponTransfer))))
                                .then(Commands.literal("revoke")
                                        .then(Commands.argument("serial", StringArgumentType.string()).suggests(GovSuggestions.WEAPONS)
                                                .then(Commands.argument("reason",
                                                                StringArgumentType.greedyString())
                                                        .executes(GovWeaponCommandHandlers::weaponRevoke))))
                                .then(Commands.literal("loss")
                                        .then(Commands.argument("serial", StringArgumentType.string()).suggests(GovSuggestions.WEAPONS)
                                                .then(Commands.argument("reason",
                                                                StringArgumentType.greedyString())
                                                        .executes(GovWeaponCommandHandlers::weaponLoss))))
                                .then(Commands.literal("classify")
                                        .executes(context -> GovWeaponPermitCommandHandlers.weaponClassify(context, ""))
                                        .then(Commands.argument("itemId", StringArgumentType.greedyString())
                                                .executes(context -> GovWeaponPermitCommandHandlers.weaponClassify(context,
                                                        StringArgumentType.getString(context, "itemId")))))
                                .then(Commands.literal("permit")
                                        .executes(context -> {
                                            context.getSource().sendSuccess(
                                                    () -> Component.literal(
                                                            "Usage: /gov weapon permit list <player>"
                                                                    + " | issue <player> <class> [reason...]"
                                                                    + " | revoke <player> <class> <reason...>"
                                                                    + " | suspend <player> <class> <minutes> <reason...>"),
                                                    false);
                                            return 1;
                                        })
                                        .then(Commands.literal("list")
                                                .then(Commands.argument("player",
                                                                StringArgumentType.word()).suggests(GovSuggestions.PLAYERS)
                                                        .executes(GovWeaponPermitCommandHandlers::weaponPermitList)))
                                        .then(Commands.literal("issue")
                                                .then(Commands.argument("player",
                                                                StringArgumentType.word()).suggests(GovSuggestions.PLAYERS)
                                                        .then(Commands.argument("class",
                                                                        StringArgumentType.word()).suggests(GovSuggestions.LICENSE_CLASSES)
                                                                .executes(context -> GovWeaponPermitCommandHandlers.weaponPermitIssue(context, ""))
                                                                .then(Commands.argument("reason",
                                                                                StringArgumentType.greedyString())
                                                                        .executes(context -> GovWeaponPermitCommandHandlers.weaponPermitIssue(
                                                                                context,
                                                                                StringArgumentType.getString(
                                                                                        context,
                                                                                        "reason")))))))
                                        .then(Commands.literal("revoke")
                                                .then(Commands.argument("player",
                                                                StringArgumentType.word()).suggests(GovSuggestions.PLAYERS)
                                                        .then(Commands.argument("class",
                                                                        StringArgumentType.word()).suggests(GovSuggestions.LICENSE_CLASSES)
                                                                .then(Commands.argument("reason",
                                                                                StringArgumentType.greedyString())
                                                                        .executes(
                                                                                GovWeaponPermitCommandHandlers::weaponPermitRevoke)))))
                                        .then(Commands.literal("suspend")
                                                .then(Commands.argument("player",
                                                                StringArgumentType.word()).suggests(GovSuggestions.PLAYERS)
                                                        .then(Commands.argument("class",
                                                                        StringArgumentType.word()).suggests(GovSuggestions.LICENSE_CLASSES)
                                                                .then(Commands.argument("minutes",
                                                                                StringArgumentType.word())
                                                                        .then(Commands.argument("reason",
                                                                                        StringArgumentType.greedyString())
                                                                                .executes(
                                                                                        GovWeaponPermitCommandHandlers::weaponPermitSuspend))))))))
                        .then(Commands.literal("area")
                                .executes(context -> {
                                    context.getSource().sendSuccess(
                                            () -> Component.literal(
                                                    "Usage: /gov area permit list"
                                                            + " | create <dim> <x> <y> <z> <radius> <minutes> <reason...>"
                                                            + " | revoke <id> <reason...>"),
                                            false);
                                    return 1;
                                })
                                .then(Commands.literal("permit")
                                        .executes(context -> {
                                            context.getSource().sendSuccess(
                                                    () -> Component.literal(
                                                            "Usage: /gov area permit list"
                                                                    + " | create <dim> <x> <y> <z> <radius> <minutes> <reason...>"
                                                                    + " | revoke <id> <reason...>"),
                                                    false);
                                            return 1;
                                        })
                                        .then(Commands.literal("list")
                                                .executes(GovAreaCommandHandlers::areaPermitList))
                                        .then(Commands.literal("create")
                                                .then(Commands.argument("dim",
                                                                net.minecraft.commands.arguments.ResourceLocationArgument.id())
                                                        .then(Commands.argument("x",
                                                                        StringArgumentType.word())
                                                                .then(Commands.argument("y",
                                                                                StringArgumentType.word())
                                                                        .then(Commands.argument("z",
                                                                                        StringArgumentType.word())
                                                                                .then(Commands.argument("radius",
                                                                                                StringArgumentType.word())
                                                                                        .then(Commands.argument("minutes",
                                                                                                        StringArgumentType.word())
                                                                                                .then(Commands.argument("reason",
                                                                                                                StringArgumentType.greedyString())
                                                                                                        .executes(
                                                                                                                GovAreaCommandHandlers::areaPermitCreate)))))))))
                                        .then(Commands.literal("revoke")
                                                .then(Commands.argument("id", StringArgumentType.string())
                                                        .then(Commands.argument("reason",
                                                                        StringArgumentType.greedyString())
                                                                .executes(GovAreaCommandHandlers::areaPermitRevoke))))));
    }
}
