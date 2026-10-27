package com.horizonssmp.gov.server;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.GovCapabilities;
import com.horizonssmp.gov.common.GovCapability;
import com.horizonssmp.gov.common.NoteKeys;
import com.horizonssmp.gov.common.PersonKeys;
import com.horizonssmp.gov.common.records.NoteRecord;

/**
 * The person rules: search, the person card, and the notes on a person's file.
 *
 * <p>Implementation details live in {@link PersonSearch}, {@link PersonDetail}, {@link PersonNotes}
 * and {@link PersonNoteMutations}; this class keeps the public API and records.
 */
public final class PersonService {

    /** Most notes one person may hold, the same value the wire keys document. */
    public static final int MAX_NOTES_PER_PERSON = NoteKeys.MAX_NOTES_PER_PERSON;

    public record Actor(UUID uuid, String name, Set<GovCapability> capabilities) {
        public Actor {
            name = name == null ? "" : name;
            capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
        }
    }

    public record PersonRef(UUID uuid, String name) {
    }

    public record Hit(
            UUID uuid, String name, boolean online,
            String jobId, String jobName, int noteCount, int fineCount) {
    }

    public record Search(List<Hit> hits, int total) {
        public Search {
            hits = hits == null ? List.of() : List.copyOf(hits);
        }
    }

    public record NoteRow(NoteRecord note, String targetName) {
    }

    public record NotePage(List<NoteRow> rows, int total, int page, int pages) {
        public NotePage {
            rows = rows == null ? List.of() : List.copyOf(rows);
        }
    }

    public record Outcome(boolean ok, ErrorCode code, String message) {
        public Outcome {
            message = message == null ? "" : message;
        }

        public static Outcome ok(final String message) {
            return new Outcome(true, null, message);
        }

        public static Outcome refused(final ErrorCode code, final String message) {
            return new Outcome(false, code, message);
        }
    }

    private PersonService() {
    }

    public static Search search(
            final GovSavedData data, final Collection<PersonRef> online, final String rawQuery) {
        return PersonSearch.search(data, online, rawQuery);
    }

    public static Map<String, String> detail(
            final GovSavedData data, final UUID uuid, final String name, final boolean online,
            final Set<GovCapability> viewerCapabilities) {
        return detail(data, uuid, name, online, viewerCapabilities, System.currentTimeMillis());
    }

    public static Map<String, String> detail(
            final GovSavedData data, final UUID uuid, final String name, final boolean online,
            final Set<GovCapability> viewerCapabilities, final long now) {
        return PersonDetail.detail(data, uuid, name, online, viewerCapabilities, now);
    }

    public static List<NoteRecord> notesOf(final GovSavedData data, final UUID targetUuid) {
        return PersonNotes.notesOf(data, targetUuid);
    }

    public static NotePage notesPage(
            final GovSavedData data, final UUID targetUuid, final int rawPage) {
        return PersonNotes.notesPage(data, targetUuid, rawPage);
    }

    public static Map<String, String> notesPageFields(
            final GovSavedData data, final UUID targetUuid, final String targetName,
            final Set<GovCapability> viewerCapabilities, final int page) {
        return PersonNotes.notesPageFields(data, targetUuid, targetName, viewerCapabilities, page);
    }

    public static void writeNoteRow(
            final Map<String, String> fields, final int index,
            final NoteRecord note, final String targetName) {
        PersonNotes.writeNoteRow(fields, index, note, targetName);
    }

    public static Outcome addNote(
            final GovSavedData data, final Actor actor, final UUID targetUuid,
            final String targetName, final String rawText, final long now) {
        return PersonNoteMutations.addNote(data, actor, targetUuid, targetName, rawText, now);
    }

    public static Outcome editNote(
            final GovSavedData data, final Actor actor, final UUID targetUuid,
            final String noteId, final String rawText, final long now) {
        return PersonNoteMutations.editNote(data, actor, targetUuid, noteId, rawText, now);
    }

    public static Outcome deleteNote(
            final GovSavedData data, final Actor actor, final UUID targetUuid, final String noteId) {
        return PersonNoteMutations.deleteNote(data, actor, targetUuid, noteId);
    }

    public static Outcome notePrecheck(
            final GovSavedData data, final UUID targetUuid, final String rawNoteId) {
        return PersonNoteMutations.notePrecheck(data, targetUuid, rawNoteId);
    }

    public static Outcome textPrecheck(final String rawText) {
        return PersonNoteMutations.textPrecheck(rawText);
    }

    public static String noteText(final String rawText) {
        return PersonNoteMutations.noteText(rawText);
    }

    public static String textLimitMessage() {
        return PersonNoteMutations.textLimitMessage();
    }

    public static String noSuchNoteMessage(final String rawNoteId) {
        return PersonNoteMutations.noSuchNoteMessage(rawNoteId);
    }

    public static String noSuchPersonMessage(final String rawName) {
        return PersonNoteMutations.noSuchPersonMessage(rawName);
    }

    static String newNoteId(final GovSavedData data) {
        return PersonNoteMutations.newNoteId(data);
    }
}