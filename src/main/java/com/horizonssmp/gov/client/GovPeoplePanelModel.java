package com.horizonssmp.gov.client;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** Shared constants, enums and data records for the People panel. */
final class GovPeoplePanelModel {

    static final int SUBTAB_SEARCH = 0;
    static final int SUBTAB_PERSON = 1;
    static final int SUBTAB_NOTES = 2;

    static final long ARM_FLOOR_MS = 600L;
    static final long ARM_MS = 5000L;
    static final int MIN_LINE_PITCH = 9;
    static final int NOTE_ROW_MIN = 20;
    static final int NOTE_ROW_MAX = 32;
    static final DateTimeFormatter SHORT_TIME =
            DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneOffset.UTC);

    static final GovRect EMPTY = new GovRect(0, 0, 0, 0);

    enum RequestKind {
        SEARCH, DETAIL, NOTES, MUTATE
    }

    enum LoadState {
        NONE, IDLE, LOADING, READY, ERROR
    }

    enum Action {
        SEARCH, REFRESH, OPEN_SEARCH, ROW,
        NOTE_ADD_SAVE, NOTE_CANCEL, NOTE_EDIT, NOTE_DELETE, NOTE_ROW, NOTE_PREV, NOTE_NEXT
    }

    record PersonRow(
            String uuid, String name, boolean online, String job,
            String notesText, String rank, String standing) {
    }

    record PersonView(String line1, String line2, String line3, String uuid, String name) {
    }

    record NoteRow(String id, String author, String text, long createdAt, long editedAt) {
    }

    record NoteView(String id, String meta, List<FormattedCharSequence> textLines) {
    }

    record DetailLine(
            String label, String value, List<FormattedCharSequence> wrapped, int color) {
    }

    record Hit(GovRect rect, Action action, int index, String value) {

        boolean contains(final double mouseX, final double mouseY) {
            return rect.contains(mouseX, mouseY);
        }
    }

    static String tr(final String key, final Object... args) {
        return Component.translatable(key, args).getString();
    }

    static String upper(final String text) {
        return text == null ? "" : text.toUpperCase(Locale.ROOT);
    }
}
