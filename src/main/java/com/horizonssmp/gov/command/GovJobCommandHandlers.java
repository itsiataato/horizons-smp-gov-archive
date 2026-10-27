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

public final class GovJobCommandHandlers {
    private GovJobCommandHandlers() {}

    static int jobList(final CommandContext<CommandSourceStack> context) {
        final GovSavedData data = GovSavedData.get(context.getSource().getServer());
        final List<JobRecord> jobs = new ArrayList<>(data.jobs().values());
        jobs.sort(Comparator.comparing(JobRecord::id));

        GovCommand.send(context, "Government jobs: " + jobs.size() + " total");
        for (final JobRecord job : jobs) {
            GovCommand.send(context, "  [" + JobService.typeOf(job) + "] " + job.id() + " - "
                    + job.displayName() + "  (" + job.capabilities().size() + " caps, "
                    + JobService.holderCount(data, job.id()) + " members)");
        }
        return 1;
    }

    static int jobInfo(final CommandContext<CommandSourceStack> context) {
        final GovSavedData data = GovSavedData.get(context.getSource().getServer());
        final String raw = StringArgumentType.getString(context, "job");
        final JobRecord job = GovCommand.jobById(data, raw);
        if (job == null) {
            context.getSource().sendFailure(
                    Component.literal(JobService.noSuchJobMessage(raw)));
            return 0;
        }

        GovCommand.send(context, "Job: " + job.displayName() + " (" + job.id() + ")");
        GovCommand.send(context, "  Type: " + JobService.typeOf(job));
        if (!job.rankLabel().isBlank()) {
            GovCommand.send(context, "  Rank: " + job.rankLabel());
        }
        if (!job.department().isBlank()) {
            GovCommand.send(context, "  Department: " + job.department());
        }

        final List<String> capabilities = GovCapabilities.ids(job.capabilities());
        GovCommand.send(context, "  Capabilities (" + capabilities.size() + "):");
        for (int i = 0; i < capabilities.size(); i += 8) {
            GovCommand.send(context, "    " + String.join(", ",
                    capabilities.subList(i, Math.min(capabilities.size(), i + 8))));
        }

        final List<PlayerRecord> members = JobService.holders(data, job.id());
        GovCommand.send(context, "  Members (" + members.size() + "):");
        for (final PlayerRecord member : members) {
            final boolean online = context.getSource().getServer().getPlayerList()
                    .getPlayer(member.uuid()) != null;
            GovCommand.send(context, "    " + member.lastName() + (online ? " (online)" : " (offline)"));
        }
        return 1;
    }

    static int jobCaps(final CommandContext<CommandSourceStack> context) {
        final GovSavedData data = GovSavedData.get(context.getSource().getServer());
        final String raw = StringArgumentType.getString(context, "job");
        final JobRecord job = GovCommand.jobById(data, raw);
        if (job == null) {
            context.getSource().sendFailure(Component.literal(JobService.noSuchJobMessage(raw)));
            return 0;
        }
        final List<String> capabilities = GovCapabilities.ids(job.capabilities());
        GovCommand.send(context, "Capabilities of '" + job.id() + "' (" + capabilities.size() + "):");
        if (capabilities.isEmpty()) {
            GovCommand.send(context, "  (none)");
            return 1;
        }
        for (int i = 0; i < capabilities.size(); i += 8) {
            GovCommand.send(context, "  " + String.join(", ",
                    capabilities.subList(i, Math.min(capabilities.size(), i + 8))));
        }
        return 1;
    }

    static int jobAssign(final CommandContext<CommandSourceStack> context) {
        final CommandSourceStack source = context.getSource();
        final String player = StringArgumentType.getString(context, "player");
        final String job = StringArgumentType.getString(context, "job");
        // The console is the server operator and is trusted for job management, even though
        // the normal dispatch path requires a player actor for its audit line.
        final ServerPlayer sourcePlayer = source.getPlayer();
        final boolean trustedOperator = sourcePlayer != null && GovCapabilities.isOperator(sourcePlayer);
        if (source.getPlayer() == null || trustedOperator) {
            final GovSavedData data = GovSavedData.get(source.getServer());
            final GovTargets.Resolved target = GovTargets.resolve(source.getServer(), data, player);
            if (target == null) {
                source.sendFailure(Component.literal("No player named '" + player + "'."));
                return 0;
            }
            final JobService.Actor consoleActor = new JobService.Actor(
                    new UUID(0L, 1L), "Console", EnumSet.allOf(GovCapability.class));
            final JobService.Outcome outcome = JobService.assign(
                    data, consoleActor, target.uuid(), target.name(), job,
                    System.currentTimeMillis());
            if (!outcome.ok()) {
                source.sendFailure(Component.literal(outcome.message()));
                return 0;
            }
            if (target.online()) {
                target.player().sendSystemMessage(Component.literal(
                        "Console assigned you the job "
                                + (data.job(job) == null ? job : data.job(job).displayName()
                                        + " (" + data.job(job).id() + ")")
                                + ".").withStyle(ChatFormatting.GRAY));
            }
            source.sendSuccess(() -> Component.literal(outcome.message()), false);
            return 1;
        }
        return GovCommand.dispatch(context, "/gov job assign", OpCode.JOB_ASSIGN,
                Map.of("target", player, "job", job));
    }

    static int jobUnassign(final CommandContext<CommandSourceStack> context) {
        final CommandSourceStack source = context.getSource();
        final String player = StringArgumentType.getString(context, "player");
        final ServerPlayer sourcePlayer2 = source.getPlayer();
        final boolean trustedOperator2 = sourcePlayer2 != null && GovCapabilities.isOperator(sourcePlayer2);
        if (source.getPlayer() == null || trustedOperator2) {
            final GovSavedData data = GovSavedData.get(source.getServer());
            final GovTargets.Resolved target = GovTargets.resolve(source.getServer(), data, player);
            if (target == null) {
                source.sendFailure(Component.literal("No player named '" + player + "'."));
                return 0;
            }
            final JobService.Actor consoleActor = new JobService.Actor(
                    new UUID(0L, 1L), "Console", EnumSet.allOf(GovCapability.class));
            final JobService.Outcome outcome = JobService.assign(
                    data, consoleActor, target.uuid(), target.name(), "",
                    System.currentTimeMillis());
            if (!outcome.ok()) {
                source.sendFailure(Component.literal(outcome.message()));
                return 0;
            }
            if (target.online()) {
                target.player().sendSystemMessage(Component.literal(
                        "Console cleared your government job.").withStyle(ChatFormatting.GRAY));
            }
            source.sendSuccess(() -> Component.literal(outcome.message()), false);
            return 1;
        }
        return GovCommand.dispatch(context, "/gov job unassign", OpCode.JOB_ASSIGN,
                Map.of("target", player, "job", ""));
    }

    static int jobCreate(final CommandContext<CommandSourceStack> context) {
        final String name = StringArgumentType.getString(context, "name");
        return GovCommand.dispatch(context, "/gov job create", OpCode.JOB_CREATE, Map.of("name", name));
    }

    static int jobDelete(final CommandContext<CommandSourceStack> context) {
        final GovSavedData data = GovSavedData.get(context.getSource().getServer());
        final String raw = StringArgumentType.getString(context, "job");
        final JobService.Outcome precheck = JobService.deletePrecheck(data, Sanitizer.id(raw));
        if (precheck != null) {
            context.getSource().sendFailure(Component.literal(precheck.message()));
            return 0;
        }
        return GovCommand.dispatch(context, "/gov job delete", OpCode.JOB_DELETE,
                Map.of("job", Sanitizer.id(raw)));
    }

    static int jobCapsEdit(final CommandContext<CommandSourceStack> context) {
        final String job = StringArgumentType.getString(context, "job");
        final String mode = StringArgumentType.getString(context, "mode").toLowerCase(Locale.ROOT);
        final String capability = StringArgumentType.getString(context, "capability");
        if (!"add".equals(mode) && !"remove".equals(mode)) {
            context.getSource().sendFailure(
                    Component.literal("Mode must be 'add' or 'remove', not '" + mode + "'."));
            return 0;
        }
        return GovCommand.dispatch(context, "/gov job caps", OpCode.JOB_EDIT,
                Map.of("job", job, "capability", capability, "mode", mode));
    }
    public static void register(final LiteralArgumentBuilder<CommandSourceStack> root) {
                root.then(Commands.literal("job")
                        .executes(context -> {
                            context.getSource().sendSuccess(
                                    () -> Component.literal(
                                            "Usage: /gov job list | info <job> | assign <player> <job> | unassign <player> | create <name> | delete <job> | caps <job> [add|remove <capability>]"),
                                    false);
                            return 1;
                        })
                        .then(Commands.literal("list").executes(GovJobCommandHandlers::jobList))
                        .then(Commands.literal("info")
                                .then(Commands.argument("job", StringArgumentType.word()).suggests(GovSuggestions.JOBS)
                                        .executes(GovJobCommandHandlers::jobInfo)))
                        .then(Commands.literal("assign")
                                .then(Commands.argument("player", StringArgumentType.word()).suggests(GovSuggestions.PLAYERS)
                                        .then(Commands.argument("job", StringArgumentType.word()).suggests(GovSuggestions.JOBS)
                                                .executes(GovJobCommandHandlers::jobAssign))))
                        .then(Commands.literal("unassign")
                                .then(Commands.argument("player", StringArgumentType.word()).suggests(GovSuggestions.PLAYERS)
                                        .executes(GovJobCommandHandlers::jobUnassign)))
                        .then(Commands.literal("create")
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(GovJobCommandHandlers::jobCreate)))
                        .then(Commands.literal("delete")
                                .then(Commands.argument("job", StringArgumentType.word()).suggests(GovSuggestions.JOBS)
                                        .executes(GovJobCommandHandlers::jobDelete)))
                        .then(Commands.literal("caps")
                                .then(Commands.argument("job", StringArgumentType.word()).suggests(GovSuggestions.JOBS)
                                        .executes(GovJobCommandHandlers::jobCaps)
                                        .then(Commands.argument("mode", StringArgumentType.word()).suggests(GovSuggestions.CAP_MODES)
                                                .then(Commands.argument("capability", StringArgumentType.word())
                                                        .executes(GovJobCommandHandlers::jobCapsEdit))))));
    }
}
