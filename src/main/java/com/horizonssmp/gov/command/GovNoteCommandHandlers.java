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

public final class GovNoteCommandHandlers {
    private GovNoteCommandHandlers() {}

    static int noteList(final CommandContext<CommandSourceStack> context) {
        final CommandSourceStack source = context.getSource();
        final GovSavedData data = GovSavedData.get(source.getServer());
        final Set<GovCapability> capabilities = GovAuditCommandHandlers.readCapabilities(source, data);
        if (!capabilities.contains(GovCapability.NOTE_VIEW_ALL)) {
            source.sendFailure(Component.literal(
                    "You need " + GovCapabilities.requiredFor(OpCode.NOTE_LIST).id()
                            + " for that action."));
            return 0;
        }
        final String raw = StringArgumentType.getString(context, "player");
        final GovTargets.Resolved target = GovTargets.resolve(source.getServer(), data, raw);
        if (target == null) {
            source.sendFailure(Component.literal(PersonService.noSuchPersonMessage(raw)));
            return 0;
        }
        final PersonService.NotePage page = PersonService.notesPage(data, target.uuid(), 0);
        GovCommand.send(context, "Notes on " + target.name() + " (" + page.total() + "):");
        if (page.rows().isEmpty()) {
            GovCommand.send(context, "  (none)");
            return 1;
        }
        for (final PersonService.NoteRow row : page.rows()) {
            final NoteRecord note = row.note();
            GovCommand.send(context, "  [" + note.id() + "] " + note.authorName() + "  " + GovAuditCommandHandlers.when(note.createdAt())
                    + (note.edited() ? "  (edited " + GovAuditCommandHandlers.when(note.editedAt()) + ")" : ""));
            GovCommand.send(context, "      " + Sanitizer.singleLine(note.text(), Sanitizer.MAX_TEXT));
        }
        if (page.pages() > 1) {
            GovCommand.send(context, "  Page 1 of " + page.pages()
                    + "; the Notes tab pages through the rest.");
        }
        return 1;
    }

    static int noteAdd(final CommandContext<CommandSourceStack> context) {
        final CommandSourceStack source = context.getSource();
        final GovSavedData data = GovSavedData.get(source.getServer());
        final String text = StringArgumentType.getString(context, "text");
        final PersonService.Outcome textCheck = PersonService.textPrecheck(text);
        if (textCheck != null) {
            source.sendFailure(Component.literal(textCheck.message()));
            return 0;
        }
        final String player = StringArgumentType.getString(context, "player");
        final GovTargets.Resolved target = GovTargets.resolve(source.getServer(), data, player);
        if (target == null) {
            source.sendFailure(Component.literal(PersonService.noSuchPersonMessage(player)));
            return 0;
        }
        return GovCommand.dispatch(context, "/gov note add", OpCode.NOTE_ADD,
                Map.of("target", player, "text", text));
    }

    static int noteEdit(final CommandContext<CommandSourceStack> context) {
        final CommandSourceStack source = context.getSource();
        final GovSavedData data = GovSavedData.get(source.getServer());
        final String player = StringArgumentType.getString(context, "player");
        final String noteId = Sanitizer.id(StringArgumentType.getString(context, "note"));
        final String text = StringArgumentType.getString(context, "text");
        final GovTargets.Resolved target = GovTargets.resolve(source.getServer(), data, player);
        if (target == null) {
            source.sendFailure(Component.literal(PersonService.noSuchPersonMessage(player)));
            return 0;
        }
        final PersonService.Outcome noteCheck = PersonService.notePrecheck(data, target.uuid(), noteId);
        if (noteCheck != null) {
            source.sendFailure(Component.literal(noteCheck.message()));
            return 0;
        }
        final PersonService.Outcome textCheck = PersonService.textPrecheck(text);
        if (textCheck != null) {
            source.sendFailure(Component.literal(textCheck.message()));
            return 0;
        }
        return GovCommand.dispatch(context, "/gov note edit", OpCode.NOTE_EDIT,
                Map.of("target", player, "noteId", noteId, "text", text));
    }

    static int noteDelete(final CommandContext<CommandSourceStack> context) {
        final CommandSourceStack source = context.getSource();
        final GovSavedData data = GovSavedData.get(source.getServer());
        final String player = StringArgumentType.getString(context, "player");
        final String noteId = Sanitizer.id(StringArgumentType.getString(context, "note"));
        final GovTargets.Resolved target = GovTargets.resolve(source.getServer(), data, player);
        if (target == null) {
            source.sendFailure(Component.literal(PersonService.noSuchPersonMessage(player)));
            return 0;
        }
        final PersonService.Outcome noteCheck = PersonService.notePrecheck(data, target.uuid(), noteId);
        if (noteCheck != null) {
            source.sendFailure(Component.literal(noteCheck.message()));
            return 0;
        }
        return GovCommand.dispatch(context, "/gov note delete", OpCode.NOTE_DELETE,
                Map.of("target", player, "noteId", noteId));
    }
    public static void register(final LiteralArgumentBuilder<CommandSourceStack> root) {
                root.then(Commands.literal("note")
                        .executes(context -> {
                            context.getSource().sendSuccess(
                                    () -> Component.literal(
                                            "Usage: /gov note list <player> | add <player> <text>"
                                                    + " | edit <player> <note> <text> | delete <player> <note>"),
                                    false);
                            return 1;
                        })
                        .then(Commands.literal("list")
                                .then(Commands.argument("player", StringArgumentType.word()).suggests(GovSuggestions.PLAYERS)
                                        .executes(GovNoteCommandHandlers::noteList)))
                        .then(Commands.literal("add")
                                .then(Commands.argument("player", StringArgumentType.word()).suggests(GovSuggestions.PLAYERS)
                                        .then(Commands.argument("text", StringArgumentType.greedyString())
                                                .executes(GovNoteCommandHandlers::noteAdd))))
                        .then(Commands.literal("edit")
                                .then(Commands.argument("player", StringArgumentType.word()).suggests(GovSuggestions.PLAYERS)
                                        .then(Commands.argument("note", StringArgumentType.word())
                                                .then(Commands.argument("text", StringArgumentType.greedyString())
                                                        .executes(GovNoteCommandHandlers::noteEdit)))))
                        .then(Commands.literal("delete")
                                .then(Commands.argument("player", StringArgumentType.word()).suggests(GovSuggestions.PLAYERS)
                                        .then(Commands.argument("note", StringArgumentType.word())
                                                .executes(GovNoteCommandHandlers::noteDelete)))));
    }
}
