package com.horizonssmp.gov.server;

import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.NoteKeys;

final class GovStartupCheckPeoplePart2 {

    private GovStartupCheckPeoplePart2() {
    }

    static void run(final GovStartupCheckPeopleContext ctx) {
        try {
            // Add: author, stamps, sanitization, empty/oversize refusal, capability refusal.
            ctx.checks++;
            if (!PersonService.addNote(ctx.scratch, ctx.writer, ctx.alice, "StartupCheck Alice",
                    "First observation.", ctx.now).ok()) {
                ctx.failures.add("adding a note failed");
            }
            ctx.checks++;
            ctx.aliceNotes = ctx.scratch.notesFor(ctx.alice);
            if (ctx.aliceNotes.size() != 1) {
                ctx.failures.add("the added note is not on the person's file");
            }
            ctx.first = ctx.aliceNotes.isEmpty() ? null : ctx.aliceNotes.get(0);
            ctx.checks++;
            if (ctx.first == null
                    || !ctx.alice.equals(ctx.first.targetUuid())
                    || !ctx.writer.uuid().equals(ctx.first.authorUuid())
                    || !"StartupCheck Writer".equals(ctx.first.authorName())
                    || !"First observation.".equals(ctx.first.text())
                    || ctx.first.createdAt() != ctx.now
                    || ctx.first.editedAt() != 0L
                    || ctx.first.edited()) {
                ctx.failures.add("the added note does not carry its author, target and stamps");
            }
            ctx.checks++;
            if (PersonService.addNote(ctx.scratch, ctx.writer, ctx.alice, "StartupCheck Alice", "   ", ctx.now)
                    .code() != ErrorCode.BAD_PAYLOAD) {
                ctx.failures.add("an empty note was accepted");
            }
            ctx.checks++;
            if (PersonService.addNote(ctx.scratch, ctx.writer, ctx.alice, "StartupCheck Alice",
                    "x".repeat(NoteKeys.MAX_NOTE_CHARS + 1), ctx.now).code() != ErrorCode.BAD_PAYLOAD) {
                ctx.failures.add("an over-length note was accepted");
            }
            ctx.checks++;
            if (!PersonService.addNote(ctx.scratch, ctx.writer, ctx.alice, "StartupCheck Alice",
                    "y".repeat(NoteKeys.MAX_NOTE_CHARS), ctx.now).ok()) {
                ctx.failures.add("a note at exactly the length limit was refused");
            }
            ctx.checks++;
            if (PersonService.addNote(ctx.scratch, ctx.reader, ctx.alice, "StartupCheck Alice", "nope", ctx.now)
                    .code() != ErrorCode.FORBIDDEN) {
                ctx.failures.add("a note write without note.write was accepted");
            }
            ctx.checks++;
            if (!PersonService.addNote(ctx.scratch, ctx.writer, ctx.bob, "StartupCheck Bob",
                    "\u00a7csecret\u00a7r\nsecond line", ctx.now).ok()) {
                ctx.failures.add("adding a note with formatting codes failed");
            }
            ctx.bobNotes = ctx.scratch.notesFor(ctx.bob);
            ctx.checks++;
            if (ctx.bobNotes.isEmpty() || !"secret\nsecond line".equals(ctx.bobNotes.get(0).text())) {
                ctx.failures.add("note sanitization did not strip the formatting codes");
            }

            // Edit: text replaced, author and creation time kept, editedAt stamped.
            ctx.checks++;
            if (!PersonService.editNote(ctx.scratch, ctx.writer, ctx.alice,
                    ctx.first == null ? "" : ctx.first.id(), "Corrected observation.", ctx.now + 5).ok()) {
                ctx.failures.add("editing a note failed");
            }
            ctx.edited = ctx.scratch.note(ctx.first == null ? "" : ctx.first.id());
            ctx.checks++;
            if (ctx.edited == null
                    || !"Corrected observation.".equals(ctx.edited.text())
                    || !ctx.writer.uuid().equals(ctx.edited.authorUuid())
                    || !"StartupCheck Writer".equals(ctx.edited.authorName())
                    || ctx.edited.createdAt() != ctx.now
                    || ctx.edited.editedAt() != ctx.now + 5
                    || !ctx.edited.edited()) {
                ctx.failures.add("the ctx.edited note lost its original author or stamps");
            }
            ctx.checks++;
            if (PersonService.editNote(ctx.scratch, ctx.writer, ctx.alice,
                    ctx.first == null ? "" : ctx.first.id(), "Corrected observation.", ctx.now + 6)
                    .code() != ErrorCode.INVALID_STATE) {
                ctx.failures.add("an edit that changed nothing was accepted");
            }
            ctx.checks++;
            if (PersonService.editNote(ctx.scratch, ctx.writer, ctx.alice,
                    "n-00000000", "text", ctx.now).code() != ErrorCode.BAD_NOTE) {
                ctx.failures.add("editing an unknown note did not report BAD_NOTE");
            }
            ctx.checks++;
            if (PersonService.editNote(ctx.scratch, ctx.writer, ctx.alice,
                    ctx.bobNotes.isEmpty() ? "" : ctx.bobNotes.get(0).id(), "text", ctx.now).code()
                    != ErrorCode.BAD_NOTE) {
                ctx.failures.add("editing another person's note did not report BAD_NOTE");
            }
            ctx.checks++;
            if (PersonService.editNote(ctx.scratch, ctx.reader, ctx.alice,
                    ctx.first == null ? "" : ctx.first.id(), "text", ctx.now).code() != ErrorCode.FORBIDDEN) {
                ctx.failures.add("a note edit without note.write was accepted");
            }

            // The reusable pre-ctx.checks the command layer prints for console and player alike.
            ctx.checks++;
            if (PersonService.notePrecheck(ctx.scratch, ctx.alice, ctx.first == null ? "" : ctx.first.id()) != null) {
                ctx.failures.add("the note pre-check refused a note that exists");
            }
            ctx.checks++;
            if (PersonService.notePrecheck(ctx.scratch, ctx.alice, "n-00000000").code() != ErrorCode.BAD_NOTE) {
                ctx.failures.add("the note pre-check accepted an unknown note id");
            }
            ctx.checks++;
            if (PersonService.textPrecheck("").code() != ErrorCode.BAD_PAYLOAD
                    || PersonService.textPrecheck("x".repeat(NoteKeys.MAX_NOTE_CHARS + 1)).code()
                            != ErrorCode.BAD_PAYLOAD
                    || PersonService.textPrecheck("fine") != null) {
                ctx.failures.add("the note text pre-check is wrong");
            }
            ctx.checks++;
            if (!"padded".equals(PersonService.noteText("  padded  "))) {
                ctx.failures.add("note text is not trimmed");
            }

            // Delete: gone, and a second delete is BAD_NOTE.
            ctx.checks++;
            if (!PersonService.deleteNote(ctx.scratch, ctx.writer, ctx.alice,
                    ctx.first == null ? "" : ctx.first.id()).ok()) {
                ctx.failures.add("deleting a note failed");
            }
            ctx.checks++;
            if (ctx.scratch.note(ctx.first == null ? "" : ctx.first.id()) != null) {
                ctx.failures.add("the deleted note is still stored");
            }
            ctx.checks++;
            if (PersonService.deleteNote(ctx.scratch, ctx.writer, ctx.alice,
                    ctx.first == null ? "" : ctx.first.id()).code() != ErrorCode.BAD_NOTE) {
                ctx.failures.add("deleting the same note twice did not report BAD_NOTE");
            }
            ctx.checks++;
            if (PersonService.deleteNote(ctx.scratch, ctx.reader, ctx.alice,
                    ctx.bobNotes.isEmpty() ? "" : ctx.bobNotes.get(0).id()).code() != ErrorCode.FORBIDDEN) {
                ctx.failures.add("a note delete without note.write was accepted");
            }

        } catch (final RuntimeException e) {
            ctx.checks++;
            ctx.failures.add("person and note rule check threw: " + e);
        }
    }
}
