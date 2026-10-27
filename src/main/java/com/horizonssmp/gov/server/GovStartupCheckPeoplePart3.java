package com.horizonssmp.gov.server;

import java.util.EnumSet;
import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.GovCapability;
import com.horizonssmp.gov.common.NoteKeys;
import com.horizonssmp.gov.common.records.NoteRecord;
import net.minecraft.nbt.CompoundTag;

final class GovStartupCheckPeoplePart3 {

    private GovStartupCheckPeoplePart3() {
    }

    static void run(final GovStartupCheckPeopleContext ctx) {
        try {
            // The per-person cap: the 51st note on one file is refused, and the file stays at 50.
            ctx.checks++;
            for (int i = 0; i < NoteKeys.MAX_NOTES_PER_PERSON; i++) {
                ctx.filled = PersonService.addNote(
                        ctx.scratch, ctx.writer, ctx.dave, "StartupCheck Dave", "cap filler " + i, ctx.now + i);
                if (!ctx.filled.ok()) {
                    ctx.failures.add("filling a person's file to the note cap failed at " + i);
                    break;
                }
            }
            if (ctx.scratch.notesFor(ctx.dave).size() != NoteKeys.MAX_NOTES_PER_PERSON) {
                ctx.failures.add("a person's file did not reach the note cap");
            }
            ctx.checks++;
            if (PersonService.addNote(ctx.scratch, ctx.writer, ctx.dave, "StartupCheck Dave", "one too many", ctx.now)
                    .code() != ErrorCode.INVALID_STATE) {
                ctx.failures.add("a note beyond the per-person cap was accepted");
            }
            ctx.checks++;
            if (ctx.scratch.notesFor(ctx.dave).size() != NoteKeys.MAX_NOTES_PER_PERSON) {
                ctx.failures.add("the refused note was written anyway");
            }

            // Pagination: bounds, clamping, and the true total for both scopes.
            ctx.checks++;
            ctx.davePage = PersonService.notesPage(ctx.scratch, ctx.dave, 0);
            if (ctx.davePage.total() != NoteKeys.MAX_NOTES_PER_PERSON
                    || ctx.davePage.rows().size() != NoteKeys.PAGE_SIZE
                    || ctx.davePage.page() != 0
                    || ctx.davePage.pages()
                            != (NoteKeys.MAX_NOTES_PER_PERSON + NoteKeys.PAGE_SIZE - 1)
                                    / NoteKeys.PAGE_SIZE) {
                ctx.failures.add("a person's ctx.first note page is wrong");
            }
            ctx.checks++;
            ctx.lastPage = PersonService.notesPage(ctx.scratch, ctx.dave, 9999);
            if (ctx.lastPage.page() != ctx.lastPage.pages() - 1) {
                ctx.failures.add("an out-of-range note page was not clamped to the last page");
            }
            ctx.checks++;
            if (PersonService.notesPage(ctx.scratch, ctx.dave, -5).page() != 0) {
                ctx.failures.add("a negative note page was not clamped to the ctx.first page");
            }
            ctx.checks++;
            ctx.globalPage = PersonService.notesPage(ctx.scratch, null, 0);
            if (ctx.globalPage.total() < NoteKeys.MAX_NOTES_PER_PERSON) {
                ctx.failures.add("the global note page does not see every person's notes");
            }
            ctx.pageFields = PersonService.notesPageFields(
                    ctx.scratch, ctx.dave, "StartupCheck Dave", EnumSet.of(GovCapability.NOTE_WRITE), 0);
            ctx.checks++;
            if (!"true".equals(ctx.pageFields.get(NoteKeys.CAN_MANAGE))
                    || !"0".equals(ctx.pageFields.get(NoteKeys.PAGE))
                    || ctx.pageFields.get(NoteKeys.ROW_PREFIX + "0" + NoteKeys.ROW_ID) == null
                    || ctx.pageFields.get(NoteKeys.ROW_PREFIX + "0" + NoteKeys.ROW_TARGET_UUID) == null) {
                ctx.failures.add("the note list field block is missing its bounds or ctx.first row");
            }
            ctx.checks++;
            if (PersonService.notesPageFields(
                    ctx.scratch, ctx.dave, "StartupCheck Dave", EnumSet.noneOf(GovCapability.class), 0)
                    .get(NoteKeys.CAN_MANAGE).equals("true")) {
                ctx.failures.add("the note list exposed note.write to a viewer without it");
            }

            // The record survives its own encoding, including the edit stamp, and an older tag
            // still loads with defaults. The ctx.legacy id names this check so a warning in the boot
            // log cannot be mistaken for a damaged file.
            ctx.checks++;
            ctx.roundTrip = new NoteRecord(
                    "n-roundtrip", ctx.alice, ctx.writer.uuid(), "StartupCheck Writer",
                    "line one\nline two = escaped \\", ctx.now, ctx.now + 1);
            if (!ctx.roundTrip.equals(NoteRecord.read(ctx.roundTrip.write(), null))) {
                ctx.failures.add("note record did not round-trip through its own encoding");
            }
            ctx.checks++;
            ctx.legacyNote = new CompoundTag();
            ctx.legacyNote.putString("id", "startup-check-ctx.legacy-note");
            ctx.legacyNote.putString("text", "from an older build");
            ctx.legacyNote.putLong("createdAt", ctx.now);
            ctx.legacyNote.putString("unknownField", "ignored");
            ctx.legacy = NoteRecord.read(ctx.legacyNote, null);
            if (!ctx.legacy.authorName().isEmpty() || ctx.legacy.editedAt() != 0L
                    || !"from an older build".equals(ctx.legacy.text())) {
                ctx.failures.add("an older note tag did not load with tolerant defaults");
            }

            ctx.checks++;
            if (!PersonService.noSuchPersonMessage("StartupCheck Ghost")
                    .contains("StartupCheck Ghost")) {
                ctx.failures.add("the unknown-person message does not name the input");
            }
            ctx.checks++;
            if (!PersonService.noSuchNoteMessage("n-00000000").contains("n-00000000")) {
                ctx.failures.add("the unknown-note message does not name the input");
            }
        } catch (final RuntimeException e) {
            ctx.checks++;
            ctx.failures.add("person and note rule check threw: " + e);
        }
    }
}
