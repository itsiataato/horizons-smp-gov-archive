package com.horizonssmp.gov.server;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.horizonssmp.gov.common.GovCapabilities;
import com.horizonssmp.gov.common.GovCapability;
import com.horizonssmp.gov.common.NoteKeys;
import com.horizonssmp.gov.common.records.NoteRecord;
import com.horizonssmp.gov.common.records.PlayerRecord;

/**
 * Helper for {@link PersonService} note listing logic.
 */
public final class PersonNotes {

    private PersonNotes() {
    }

    public static List<NoteRecord> notesOf(final GovSavedData data, final UUID targetUuid) {
        if (data == null || targetUuid == null) {
            return List.of();
        }
        final List<NoteRecord> notes = new ArrayList<>(data.notesFor(targetUuid));
        notes.sort(newestFirst());
        return notes;
    }

    public static PersonService.NotePage notesPage(
            final GovSavedData data, final UUID targetUuid, final int rawPage) {
        final List<NoteRecord> all = new ArrayList<>();
        if (data != null) {
            if (targetUuid == null) {
                all.addAll(data.globalNotes());
            } else {
                all.addAll(data.notesFor(targetUuid));
            }
        }
        all.sort(newestFirst());

        final int total = all.size();
        final int pages = Math.max(1, (total + NoteKeys.PAGE_SIZE - 1) / NoteKeys.PAGE_SIZE);
        final int page = Math.max(0, Math.min(rawPage, pages - 1));
        final List<PersonService.NoteRow> rows = new ArrayList<>(NoteKeys.PAGE_SIZE);
        final int start = page * NoteKeys.PAGE_SIZE;
        for (int i = start; i < Math.min(total, start + NoteKeys.PAGE_SIZE); i++) {
            final NoteRecord note = all.get(i);
            rows.add(new PersonService.NoteRow(note, targetName(data, note.targetUuid())));
        }
        return new PersonService.NotePage(rows, total, page, pages);
    }

    public static Map<String, String> notesPageFields(
            final GovSavedData data, final UUID targetUuid, final String targetName,
            final Set<GovCapability> viewerCapabilities, final int page) {
        final PersonService.NotePage result = notesPage(data, targetUuid, page);
        final Map<String, String> fields = new LinkedHashMap<>();
        fields.put(NoteKeys.CAN_MANAGE,
                Boolean.toString(GovCapabilities.has(viewerCapabilities, GovCapability.NOTE_WRITE)));
        fields.put(NoteKeys.TOTAL, Integer.toString(result.total()));
        fields.put(NoteKeys.PAGE, Integer.toString(result.page()));
        fields.put(NoteKeys.PAGES, Integer.toString(result.pages()));
        for (int i = 0; i < result.rows().size(); i++) {
            final PersonService.NoteRow row = result.rows().get(i);
            writeNoteRow(fields, i, row.note(),
                    targetName == null || targetName.isBlank() ? row.targetName() : targetName);
        }
        return fields;
    }

    public static void writeNoteRow(
            final Map<String, String> fields, final int index,
            final NoteRecord note, final String targetName) {
        final String prefix = NoteKeys.ROW_PREFIX + index;
        fields.put(prefix + NoteKeys.ROW_ID, note.id());
        fields.put(prefix + NoteKeys.ROW_TARGET, Sanitizer.name(targetName));
        fields.put(prefix + NoteKeys.ROW_TARGET_UUID,
                note.targetUuid() == null ? "" : note.targetUuid().toString());
        fields.put(prefix + NoteKeys.ROW_AUTHOR, note.authorName());
        fields.put(prefix + NoteKeys.ROW_AUTHOR_UUID,
                note.authorUuid() == null ? "" : note.authorUuid().toString());
        fields.put(prefix + NoteKeys.ROW_TEXT, note.text());
        fields.put(prefix + NoteKeys.ROW_CREATED, Long.toString(note.createdAt()));
        fields.put(prefix + NoteKeys.ROW_EDITED, Long.toString(note.editedAt()));
    }

    private static String targetName(final GovSavedData data, final UUID targetUuid) {
        final PlayerRecord record = data == null || targetUuid == null ? null : data.player(targetUuid);
        if (record != null && !record.lastName().isBlank()) {
            return record.lastName();
        }
        return targetUuid == null ? "" : targetUuid.toString();
    }

    private static Comparator<NoteRecord> newestFirst() {
        return Comparator
                .comparingLong(NoteRecord::createdAt).reversed()
                .thenComparing(NoteRecord::id);
    }
}