package com.horizonssmp.gov.server;

import java.util.Set;
import java.util.UUID;

import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.GovCapabilities;
import com.horizonssmp.gov.common.GovCapability;
import com.horizonssmp.gov.common.NoteKeys;
import com.horizonssmp.gov.common.records.NoteRecord;
import com.horizonssmp.gov.common.records.PlayerRecord;

/**
 * Helper for {@link PersonService} note mutation logic.
 */
public final class PersonNoteMutations {

    private PersonNoteMutations() {
    }

    public static PersonService.Outcome addNote(
            final GovSavedData data, final PersonService.Actor actor, final UUID targetUuid,
            final String targetName, final String rawText, final long now) {
        final PersonService.Outcome refusal = writeChecks(data, actor, targetUuid);
        if (refusal != null) {
            return refusal;
        }
        final String text = noteText(rawText);
        if (text == null) {
            return PersonService.Outcome.refused(ErrorCode.BAD_PAYLOAD, textLimitMessage());
        }
        final String name = Sanitizer.name(targetName);
        final int existing = data.notesFor(targetUuid).size();
        if (existing >= NoteKeys.MAX_NOTES_PER_PERSON) {
            return PersonService.Outcome.refused(
                    ErrorCode.INVALID_STATE,
                    name + " already has " + NoteKeys.MAX_NOTES_PER_PERSON
                            + " notes; delete one before adding another.");
        }
        final PlayerRecord record = data.playerOrCreate(targetUuid, name, now);
        final String id = newNoteId(data);
        data.putNote(new NoteRecord(
                id, targetUuid, actor.uuid(), Sanitizer.name(actor.name()), text, now, 0L));
        return PersonService.Outcome.ok("added note " + id + " on " + record.lastName());
    }

    public static PersonService.Outcome editNote(
            final GovSavedData data, final PersonService.Actor actor, final UUID targetUuid,
            final String noteId, final String rawText, final long now) {
        final PersonService.Outcome refusal = writeChecks(data, actor, targetUuid);
        if (refusal != null) {
            return refusal;
        }
        final String id = Sanitizer.id(noteId);
        final NoteRecord note = id.isEmpty() ? null : data.note(id);
        if (note == null || !targetUuid.equals(note.targetUuid())) {
            return PersonService.Outcome.refused(ErrorCode.BAD_NOTE, noSuchNoteMessage(id));
        }
        final String text = noteText(rawText);
        if (text == null) {
            return PersonService.Outcome.refused(ErrorCode.BAD_PAYLOAD, textLimitMessage());
        }
        if (text.equals(note.text())) {
            return PersonService.Outcome.refused(
                    ErrorCode.INVALID_STATE, "Note " + note.id() + " already has that text.");
        }
        data.putNote(note.withText(text, now));
        return PersonService.Outcome.ok("edited note " + note.id());
    }

    public static PersonService.Outcome deleteNote(
            final GovSavedData data, final PersonService.Actor actor, final UUID targetUuid, final String noteId) {
        final PersonService.Outcome refusal = writeChecks(data, actor, targetUuid);
        if (refusal != null) {
            return refusal;
        }
        final String id = Sanitizer.id(noteId);
        final NoteRecord note = id.isEmpty() ? null : data.note(id);
        if (note == null || !targetUuid.equals(note.targetUuid())) {
            return PersonService.Outcome.refused(ErrorCode.BAD_NOTE, noSuchNoteMessage(id));
        }
        if (!data.removeNote(note.id())) {
            return PersonService.Outcome.refused(
                    ErrorCode.INVALID_STATE, "Note " + note.id() + " could not be deleted.");
        }
        return PersonService.Outcome.ok("deleted note " + note.id());
    }

    private static PersonService.Outcome writeChecks(
            final GovSavedData data, final PersonService.Actor actor, final UUID targetUuid) {
        if (data == null || actor == null || targetUuid == null) {
            return PersonService.Outcome.refused(ErrorCode.INVALID_STATE, "No person to note.");
        }
        if (!actor.capabilities().contains(GovCapability.NOTE_WRITE)) {
            return PersonService.Outcome.refused(
                    ErrorCode.FORBIDDEN,
                    "You need " + GovCapability.NOTE_WRITE.id() + " for that action.");
        }
        return null;
    }

    public static PersonService.Outcome notePrecheck(
            final GovSavedData data, final UUID targetUuid, final String rawNoteId) {
        final String id = Sanitizer.id(rawNoteId);
        final NoteRecord note = data == null || id.isEmpty() ? null : data.note(id);
        if (note == null || (targetUuid != null && !targetUuid.equals(note.targetUuid()))) {
            return PersonService.Outcome.refused(ErrorCode.BAD_NOTE, noSuchNoteMessage(id));
        }
        return null;
    }

    public static PersonService.Outcome textPrecheck(final String rawText) {
        if (noteText(rawText) == null) {
            return PersonService.Outcome.refused(ErrorCode.BAD_PAYLOAD, textLimitMessage());
        }
        return null;
    }

    public static String noteText(final String rawText) {
        final String text = Sanitizer.multiLineUncapped(rawText);
        if (text.isEmpty() || text.length() > NoteKeys.MAX_NOTE_CHARS) {
            return null;
        }
        return text;
    }

    public static String textLimitMessage() {
        return "A note needs text of at most " + NoteKeys.MAX_NOTE_CHARS + " characters.";
    }

    public static String noSuchNoteMessage(final String rawNoteId) {
        return "There is no note '" + Sanitizer.singleLine(rawNoteId, Sanitizer.MAX_ID)
                + "' on that person.";
    }

    public static String noSuchPersonMessage(final String rawName) {
        return "There is no person '" + Sanitizer.name(rawName) + "'.";
    }

    static String newNoteId(final GovSavedData data) {
        for (int attempt = 0; attempt < 8; attempt++) {
            final String id = "n-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            if (data.note(id) == null) {
                return id;
            }
        }
        String fallback = "n-" + Long.toHexString(System.nanoTime());
        while (data.note(fallback) != null) {
            fallback = fallback + "x";
        }
        return fallback;
    }
}