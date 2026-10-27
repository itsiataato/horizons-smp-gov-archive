package com.horizonssmp.gov.client;

import com.horizonssmp.gov.common.NoteKeys;
import com.horizonssmp.gov.common.OpCode;
import com.horizonssmp.gov.network.GovNetwork;

import java.util.Map;

final class GovPeopleInput {
    static boolean mouseClicked(final GovPeoplePanel p, final double mouseX, final double mouseY,
            final int button) {
        if (!p.active || button != 0) {
            return false;
        }
        if (p.mode == GovPeoplePanel.SUBTAB_SEARCH && p.searchBox != null
                && p.searchFieldRect.contains(mouseX, mouseY)) {
            p.host.setFocused(p.searchBox);
            return true;
        }
        if (p.mode == GovPeoplePanel.SUBTAB_NOTES && p.noteBox != null && p.noteBoxVisible
                && p.noteFieldRect.contains(mouseX, mouseY)) {
            p.deleteArmedNoteId = "";
            p.host.setFocused(p.noteBox);
            return true;
        }
        for (final GovPeoplePanelModel.Hit hit : p.hits) {
            if (hit.contains(mouseX, mouseY)) {
                handle(p, hit);
                return true;
            }
        }
        if (p.searchBox != null && p.host.getFocused() == p.searchBox
                && !p.searchFieldRect.contains(mouseX, mouseY)) {
            p.host.setFocused(null);
        }
        if (p.noteBox != null && p.host.getFocused() == p.noteBox
                && !p.noteFieldRect.contains(mouseX, mouseY)) {
            p.host.setFocused(null);
        }
        p.deleteArmedNoteId = "";
        return false;
    }

    static void handle(final GovPeoplePanel p, final GovPeoplePanelModel.Hit hit) {
        if (hit.action() != GovPeoplePanelModel.Action.NOTE_DELETE) {
            p.deleteArmedNoteId = "";
        }
        switch (hit.action()) {
            case SEARCH -> GovPeopleRequests.doSearch(p);
            case REFRESH -> refreshCurrent(p);
            case OPEN_SEARCH -> GovPeopleLifecycle.setMode(p, GovPeoplePanel.SUBTAB_SEARCH, true);
            case ROW -> {
                if (hit.index() >= 0 && hit.index() < p.personViews.size()) {
                    final GovPeoplePanelModel.PersonView view = p.personViews.get(hit.index());
                    GovPeopleSelection.selectPerson(p, view.uuid(), view.name());
                }
            }
            case NOTE_ADD_SAVE -> saveNote(p);
            case NOTE_CANCEL -> cancelEdit(p);
            case NOTE_EDIT -> startEdit(p, hit.value());
            case NOTE_DELETE -> armOrDelete(p, hit.value());
            case NOTE_ROW -> {
                p.selectedNoteIndex = hit.index();
                p.deleteArmedNoteId = "";
                GovPeopleHits.rebuildHits(p);
            }
            case NOTE_PREV -> GovPeopleRequests.requestNotes(p, p.selectedUuid, p.notesPage - 1);
            case NOTE_NEXT -> GovPeopleRequests.requestNotes(p, p.selectedUuid, p.notesPage + 1);
            default -> {
            }
        }
    }

    static void refreshCurrent(final GovPeoplePanel p) {
        if (GovPeopleUtil.mutationPending(p)) {
            return;
        }
        if (p.mode == GovPeoplePanel.SUBTAB_SEARCH) {
            GovPeopleRequests.doSearch(p);
        } else if (!p.selectedUuid.isEmpty()) {
            GovPeopleRequests.requestDetail(p, p.selectedUuid);
            if (p.canViewNotes) {
                GovPeopleRequests.requestNotes(p, p.selectedUuid, p.notesPage);
            }
        }
    }

    static void armOrDelete(final GovPeoplePanel p, final String noteId) {
        if (noteId == null || noteId.isEmpty() || p.selectedUuid.isEmpty()) {
            return;
        }
        final long now = System.currentTimeMillis();
        if (noteId.equals(p.deleteArmedNoteId) && p.deleteArmedAt > 0
                && now - p.deleteArmedAt <= GovPeoplePanel.ARM_MS) {
            if (now - p.deleteArmedAt < GovPeoplePanel.ARM_FLOOR_MS) {
                return;
            }
            p.deleteArmedNoteId = "";
            GovPeopleRequests.mutate(p, OpCode.NOTE_DELETE, Map.of("target", p.selectedUuid,
                    NoteKeys.KEY_NOTE_ID, noteId));
            return;
        }
        p.deleteArmedNoteId = noteId;
        p.deleteArmedAt = now;
        GovPeopleHits.rebuildHits(p);
    }

    static void startEdit(final GovPeoplePanel p, final String noteId) {
        final GovPeoplePanelModel.NoteRow note = GovPeopleUtil.findNote(p, noteId);
        if (note == null) {
            return;
        }
        p.editingNoteId = noteId;
        p.selectedNoteIndex = p.notes.indexOf(note);
        p.deleteArmedNoteId = "";
        if (p.noteBox != null) {
            p.noteBox.setValue(note.text());
            p.host.setFocused(p.noteBox);
        }
        GovPeopleGeometry.computeComposerGeometry(p);
        GovPeopleHits.rebuildHits(p);
    }

    static void cancelEdit(final GovPeoplePanel p) {
        p.editingNoteId = "";
        if (p.noteBox != null) {
            p.noteBox.setValue("");
            if (p.host.getFocused() == p.noteBox) {
                p.host.setFocused(null);
            }
        }
        GovPeopleGeometry.computeComposerGeometry(p);
        GovPeopleHits.rebuildHits(p);
    }

    static void saveNote(final GovPeoplePanel p) {
        if (p.noteBox == null || p.selectedUuid.isEmpty()
                || !p.canManageNotes || !p.canPostNotes) {
            return;
        }
        final String text = p.noteBox.getValue().strip();
        if (text.isEmpty()) {
            return;
        }
        if (p.editingNoteId.isEmpty()) {
            GovPeopleRequests.mutate(p, OpCode.NOTE_ADD, Map.of("target", p.selectedUuid,
                    NoteKeys.KEY_TEXT, text));
        } else {
            GovPeopleRequests.mutate(p, OpCode.NOTE_EDIT, Map.of("target", p.selectedUuid,
                    NoteKeys.KEY_NOTE_ID, p.editingNoteId,
                    NoteKeys.KEY_TEXT, text));
        }
    }

    static boolean mouseScrolled(final GovPeoplePanel p, final double mouseX, final double mouseY,
            final double scrollY) {
        if (!p.active) {
            return false;
        }
        final int step = scrollY < 0 ? 1 : -1;
        if (p.mode == GovPeoplePanel.SUBTAB_SEARCH && p.listRect.contains(mouseX, mouseY)) {
            p.searchScroll = GovMetrics.clamp(
                    p.searchScroll + step * p.searchRowH, 0, p.searchScrollMax);
            GovPeopleHits.rebuildHits(p);
            return true;
        }
        if (p.mode == GovPeoplePanel.SUBTAB_PERSON && p.personFormRect.contains(mouseX, mouseY)
                && p.personScrollMax > 0) {
            p.personScroll = GovMetrics.clamp(
                    p.personScroll + step * p.personPitch, 0, p.personScrollMax);
            return true;
        }
        if (p.mode == GovPeoplePanel.SUBTAB_NOTES && p.notesListRect.contains(mouseX, mouseY)) {
            p.notesScroll = GovMetrics.clamp(
                    p.notesScroll + step * p.noteRowHeight, 0, p.notesScrollMax);
            GovPeopleNotesCache.rebuildNoteRects(p);
            GovPeopleHits.rebuildHits(p);
            return true;
        }
        return false;
    }

    static boolean keyPressed(final GovPeoplePanel p, final int keyCode, final int scanCode,
            final int modifiers) {
        if (!p.active) {
            return false;
        }
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            if (blurVisibleFocus(p)) {
                return true;
            }
            if (!p.deleteArmedNoteId.isEmpty()) {
                p.deleteArmedNoteId = "";
                GovPeopleHits.rebuildHits(p);
                return true;
            }
            if (p.mode == GovPeoplePanel.SUBTAB_NOTES && (!p.editingNoteId.isEmpty()
                    || (p.noteBox != null && p.noteBox.isFocused()
                            && !p.noteBox.getValue().isBlank()))) {
                cancelEdit(p);
                return true;
            }
            return false;
        }
        if (GovKeyRepeat.isEnter(keyCode)) {
            if (p.enterRepeat.accepts(System.currentTimeMillis())) {
                submitEnter(p);
            }
            return true;
        }
        if (p.mode == GovPeoplePanel.SUBTAB_SEARCH
                && !(p.host.getFocused() instanceof net.minecraft.client.gui.components.EditBox)
                && (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_UP || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN)) {
            return moveSearchSelection(p, keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN ? 1 : -1);
        }
        if (p.mode == GovPeoplePanel.SUBTAB_NOTES
                && !(p.host.getFocused() instanceof net.minecraft.client.gui.components.EditBox)
                && (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_UP || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN)) {
            return moveNoteSelection(p, keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN ? 1 : -1);
        }
        return false;
    }

    static boolean blurVisibleFocus(final GovPeoplePanel p) {
        if (p.searchBox != null && p.searchBoxVisible
                && p.host.getFocused() == p.searchBox) {
            p.host.setFocused(null);
            return true;
        }
        if (p.noteBox != null && p.noteBoxVisible
                && p.host.getFocused() == p.noteBox) {
            p.host.setFocused(null);
            return true;
        }
        return false;
    }

    static void submitEnter(final GovPeoplePanel p) {
        if (p.mode == GovPeoplePanel.SUBTAB_SEARCH) {
            GovPeopleRequests.doSearch(p);
            return;
        }
        if (p.mode == GovPeoplePanel.SUBTAB_NOTES && p.noteBox != null
                && (p.host.getFocused() == p.noteBox
                        || !p.noteBox.getValue().isBlank())) {
            saveNote(p);
        }
    }

    static boolean moveSearchSelection(final GovPeoplePanel p, final int delta) {
        if (p.personViews.isEmpty()) {
            return false;
        }
        int index = -1;
        for (int i = 0; i < p.personViews.size(); i++) {
            if (p.personViews.get(i).uuid().equals(p.selectedUuid)) {
                index = i;
                break;
            }
        }
        final int next = GovMetrics.clamp(index + delta, 0, p.personViews.size() - 1);
        final GovPeoplePanelModel.PersonView view = p.personViews.get(next);
        GovPeopleSelection.selectPerson(p, view.uuid(), view.name());
        return true;
    }

    static boolean moveNoteSelection(final GovPeoplePanel p, final int delta) {
        if (p.noteViews.isEmpty()) {
            return false;
        }
        final int next = GovMetrics.clamp(p.selectedNoteIndex + delta, 0,
                p.noteViews.size() - 1);
        p.selectedNoteIndex = next;
        p.deleteArmedNoteId = "";
        final int top = next * p.noteRowHeight;
        if (top < p.notesScroll) {
            p.notesScroll = top;
        } else if (top + p.noteRowHeight > p.notesScroll + p.notesListRect.h()) {
            p.notesScroll = top + p.noteRowHeight - p.notesListRect.h();
        }
        p.notesScroll = GovMetrics.clamp(p.notesScroll, 0, p.notesScrollMax);
        p.notesScroll = (p.notesScroll / p.noteRowHeight) * p.noteRowHeight;
        GovPeopleNotesCache.rebuildNoteRects(p);
        GovPeopleHits.rebuildHits(p);
        return true;
    }
}
