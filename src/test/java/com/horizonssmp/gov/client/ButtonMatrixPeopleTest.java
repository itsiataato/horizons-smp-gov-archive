package com.horizonssmp.gov.client;

import net.minecraft.network.chat.Component;

import com.horizonssmp.gov.client.GovPeoplePanelModel.Action;
import com.horizonssmp.gov.client.GovPeoplePanelModel.Hit;
import com.horizonssmp.gov.client.GovPeoplePanelModel.LoadState;
import com.horizonssmp.gov.client.GovPeoplePanelModel.NoteRow;
import com.horizonssmp.gov.client.GovPeoplePanelModel.PersonView;
import com.horizonssmp.gov.common.NoteKeys;
import com.horizonssmp.gov.common.OpCode;
import com.horizonssmp.gov.common.PersonKeys;

/**
 * TASK-037 button matrix for the people tab: every {@link Action} through the real
 * {@code GovPeopleInput.handle} dispatch. Runs via {@code main()}.
 */
final class ButtonMatrixPeopleTest {
    private ButtonMatrixPeopleTest() {
    }

    private static GovThemedEditBox box(final String value) {
        final GovThemedEditBox b = new GovThemedEditBox(null, 0, 0, 60, 9, Component.empty());
        b.setValue(value);
        return b;
    }

    private static GovPeoplePanel fresh() {
        final GovPeoplePanel p = new GovPeoplePanel(null);
        p.active = true;
        p.mode = GovPeoplePanel.SUBTAB_SEARCH;
        p.searchBox = box("alex");
        p.noteBox = box("hello");
        p.selectedUuid = "uuid-1";
        p.selectedName = "Alex";
        p.canViewNotes = true;
        p.canManageNotes = true;
        p.canPostNotes = true;
        p.personViews.add(new PersonView("l1", "l2", "l3", "uuid-1", "Alex"));
        p.notes.add(new NoteRow("n-1", "Alex", "text", 0L, 0L));
        p.pending.clear();
        return p;
    }

    private static void hit(final GovPeoplePanel p, final Action action, final int index,
            final String value) {
        p.pending.clear();
        GovPeopleInput.handle(p, new Hit(new GovRect(0, 0, 1, 1), action, index, value));
    }

    public static void main(final String[] args) {
        ButtonMatrix.install();
        try {
            final GovPeoplePanel pSearch = fresh();
            ButtonMatrix.fire("PEOPLE", "SEARCH", () -> hit(pSearch, Action.SEARCH, 0, ""),
                    OpCode.PERSON_SEARCH, PersonKeys.KEY_QUERY);

            final GovPeoplePanel pRefresh = fresh();
            ButtonMatrix.fire("PEOPLE", "REFRESH_SEARCH", () -> hit(pRefresh, Action.REFRESH, 0, ""),
                    OpCode.PERSON_SEARCH, PersonKeys.KEY_QUERY);

            final GovPeoplePanel pRefreshP = fresh();
            pRefreshP.mode = GovPeoplePanel.SUBTAB_PERSON;
            ButtonMatrix.fire("PEOPLE", "REFRESH_PERSON", () -> hit(pRefreshP, Action.REFRESH, 0, ""),
                    OpCode.LOOKUP_PERSON, "target");

            final GovPeoplePanel pOpen = fresh();
            pOpen.mode = GovPeoplePanel.SUBTAB_PERSON;
            ButtonMatrix.fireNone("PEOPLE", "OPEN_SEARCH",
                    () -> hit(pOpen, Action.OPEN_SEARCH, 0, ""),
                    () -> ButtonMatrix.check(pOpen.mode == GovPeoplePanel.SUBTAB_SEARCH,
                            "mode is " + pOpen.mode));

            final GovPeoplePanel pRow = fresh();
            // Row select syncs widgets; null boxes skip the live-host focus check.
            pRow.searchBox = null;
            pRow.noteBox = null;
            ButtonMatrix.fire("PEOPLE", "ROW", () -> hit(pRow, Action.ROW, 0, ""),
                    OpCode.LOOKUP_PERSON, "target");

            final GovPeoplePanel pAdd = fresh();
            ButtonMatrix.fire("PEOPLE", "NOTE_ADD_SAVE",
                    () -> hit(pAdd, Action.NOTE_ADD_SAVE, 0, ""),
                    OpCode.NOTE_ADD, "target", NoteKeys.KEY_TEXT);

            final GovPeoplePanel pEditSave = fresh();
            pEditSave.editingNoteId = "n-1";
            ButtonMatrix.fire("PEOPLE", "NOTE_EDIT_SAVE",
                    () -> hit(pEditSave, Action.NOTE_ADD_SAVE, 0, ""),
                    OpCode.NOTE_EDIT, "target", NoteKeys.KEY_NOTE_ID, NoteKeys.KEY_TEXT);

            // startEdit/cancelEdit need a live host for field focus: headed-only seam.
            final GovPeoplePanel pStartEdit = fresh();
            ButtonMatrix.fireBlock("PEOPLE", "NOTE_EDIT",
                    () -> hit(pStartEdit, Action.NOTE_EDIT, 0, "n-1"),
                    "composer focus needs a live host");

            final GovPeoplePanel pCancel = fresh();
            ButtonMatrix.fireBlock("PEOPLE", "NOTE_CANCEL",
                    () -> hit(pCancel, Action.NOTE_CANCEL, 0, ""),
                    "composer unfocus needs a live host");

            // Delete arms on first click, fires NOTE_DELETE on confirm inside the window.
            final GovPeoplePanel pDel = fresh();
            ButtonMatrix.fireNone("PEOPLE", "NOTE_DELETE_ARM",
                    () -> hit(pDel, Action.NOTE_DELETE, 0, "n-1"),
                    () -> ButtonMatrix.check("n-1".equals(pDel.deleteArmedNoteId),
                            "delete did not arm"));
            pDel.deleteArmedAt = System.currentTimeMillis() - 1000L;
            ButtonMatrix.fire("PEOPLE", "NOTE_DELETE_FIRE",
                    () -> hit(pDel, Action.NOTE_DELETE, 0, "n-1"),
                    OpCode.NOTE_DELETE, "target", NoteKeys.KEY_NOTE_ID);

            final GovPeoplePanel pNoteRow = fresh();
            ButtonMatrix.fireNone("PEOPLE", "NOTE_ROW",
                    () -> hit(pNoteRow, Action.NOTE_ROW, 0, ""),
                    () -> ButtonMatrix.check(pNoteRow.selectedNoteIndex == 0,
                            "note row not selected"));

            final GovPeoplePanel pPrev = fresh();
            ButtonMatrix.fire("PEOPLE", "NOTE_PREV",
                    () -> hit(pPrev, Action.NOTE_PREV, 0, ""),
                    OpCode.NOTE_LIST, "target");

            final GovPeoplePanel pNextf = fresh();
            ButtonMatrix.fire("PEOPLE", "NOTE_NEXT",
                    () -> hit(pNextf, Action.NOTE_NEXT, 0, ""),
                    OpCode.NOTE_LIST, "target");

            final GovPeoplePanel pState = fresh();
            ButtonMatrix.check(LoadState.IDLE != null, "load states present");
            ButtonMatrix.check(pState.searchState != null, "search state present");
        } finally {
            ButtonMatrix.uninstall();
        }
        ButtonMatrix.summary("PEOPLE");
    }
}
