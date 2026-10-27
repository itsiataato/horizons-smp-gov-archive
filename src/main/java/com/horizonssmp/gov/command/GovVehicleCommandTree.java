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

public final class GovVehicleCommandTree {
    private GovVehicleCommandTree() {}

    public static void register(final LiteralArgumentBuilder<CommandSourceStack> root) {
                root.then(Commands.literal("vehicle")
                        .executes(context -> {
                            context.getSource().sendSuccess(
                                    () -> Component.literal(
                                            "Usage: /gov vehicle list [player|all] | info <plate>"
                                                    + " | register <plate> <class> [player]"
                                                    + " | scan <plate> | impound <plate> <reason...>"
                                                    + " | release <plate>"),
                                    false);
                            return 1;
                        })
                        .then(Commands.literal("list")
                                .executes(context -> GovVehicleCommandHandlers.vehicleList(context, "all"))
                                .then(Commands.argument("scope", StringArgumentType.word()).suggests(GovSuggestions.SCOPES)
                                        .executes(context -> GovVehicleCommandHandlers.vehicleList(context,
                                                StringArgumentType.getString(context, "scope")))))
                        .then(Commands.literal("info")
                                .then(Commands.argument("plate", StringArgumentType.word()).suggests(GovSuggestions.PLATES)
                                        .executes(GovVehicleCommandHandlers::vehicleInfo)))
                        .then(Commands.literal("register")
                                .then(Commands.argument("plate", StringArgumentType.word()).suggests(GovSuggestions.PLATES)
                                        .then(Commands.argument("class", StringArgumentType.word()).suggests(GovSuggestions.LICENSE_CLASSES)
                                                .executes(context -> GovVehicleMutationCommandHandlers.vehicleRegister(context, ""))
                                                .then(Commands.argument("player", StringArgumentType.word()).suggests(GovSuggestions.PLAYERS)
                                                        .executes(context -> GovVehicleMutationCommandHandlers.vehicleRegister(context,
                                                                StringArgumentType.getString(
                                                                        context, "player")))))))
                        .then(Commands.literal("scan")
                                .then(Commands.argument("plate", StringArgumentType.word()).suggests(GovSuggestions.PLATES)
                                        .executes(GovVehicleMutationCommandHandlers::vehicleScan)))
                        .then(Commands.literal("impound")
                                .then(Commands.argument("plate", StringArgumentType.word()).suggests(GovSuggestions.PLATES)
                                        .then(Commands.argument("reason", StringArgumentType.greedyString())
                                                .executes(GovVehicleMutationCommandHandlers::vehicleImpound))))
                        .then(Commands.literal("release")
                                .then(Commands.argument("plate", StringArgumentType.word()).suggests(GovSuggestions.PLATES)
                                        .executes(GovVehicleMutationCommandHandlers::vehicleRelease))));
    }
}
