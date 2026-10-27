package com.horizonssmp.gov.client;

import net.minecraft.network.chat.Component;

import com.horizonssmp.gov.client.GovNotesTypes.Load;
import com.horizonssmp.gov.client.GovNotesTypes.NoteRow;
import com.horizonssmp.gov.common.NoteKeys;
import com.horizonssmp.gov.common.OpCode;

/**
 * TASK-037 button matrix for the notes tab: list/scopes/refresh/retry/save and the
 * armed delete run through the real panel methods headless (mode 0 = all-notes view).
 * Row-open needs a live host. Runs via {@code main()}.
 */
final class ButtonMatrixNotesTest {
    private ButtonMatrixNotesTest() {
    }

    private static GovThemedEditBox box(final String value) {
        final GovThemedEditBox b = new GovThemedEditBox(null, 0, 0, 60, 9, Component.empty());
        b.setValue(value);
        return b;
    }

    private static GovNotesPanel fresh() {
        final GovNotesPanel p = new GovNotesPanel(null);
        p.active = true;
        p.mode = 0;
        p.textBox = box("hello");
        p.targetBox = box("Alex");
        p.allView.state = Load.READY;
        p.allView.canPost = true;
        // Keep the composer boxes visible so mutate-only paths (SAVE/DELETE) sync
        // without touching the null host; list/scopes paths still seam on
        // Minecraft.getInstance() inside rebuildViewGeometry (fireSeam proves the shot).
        p.targetFieldRect = new GovRect(0, 0, 100, 20);
        p.textFieldRect = new GovRect(0, 0, 100, 20);
        p.pending.clear();
        p.mutationPending = false;
        return p;
    }

    public static void main(final String[] args) {
        ButtonMatrix.install();
        try {
            final GovNotesPanel pRefresh = fresh();
            ButtonMatrix.fireSeam("NOTES", "REFRESH", () -> {
                pRefresh.pending.clear();
                pRefresh.mutationPending = false;
                pRefresh.refreshCurrent();
            }, OpCode.NOTE_LIST);

            final GovNotesPanel pRetry = fresh();
            pRetry.allView.state = Load.ERROR;
            ButtonMatrix.fireSeam("NOTES", "RETRY", () -> {
                pRetry.pending.clear();
                pRetry.mutationPending = false;
                pRetry.retryCurrent();
            }, OpCode.NOTE_LIST);

            final GovNotesPanel pScopes = fresh();
            ButtonMatrix.fireSeam("NOTES", "SCOPES", () -> {
                pScopes.pending.clear();
                pScopes.scopesPending = false;
                pScopes.requestScopes();
            }, OpCode.NOTE_SCOPES);

            final GovNotesPanel pScopesRetry = fresh();
            pScopesRetry.scopesState = Load.ERROR;
            ButtonMatrix.fireSeam("NOTES", "SCOPES_RETRY", () -> {
                pScopesRetry.pending.clear();
                pScopesRetry.scopesPending = false;
                pScopesRetry.mutationPending = false;
                pScopesRetry.retryCurrent();
            }, OpCode.NOTE_SCOPES);

            final GovNotesPanel pSave = fresh();
            ButtonMatrix.fire("NOTES", "SAVE", () -> {
                pSave.pending.clear();
                pSave.mutationPending = false;
                pSave.saveComposer();
            }, OpCode.NOTE_ADD, "target", NoteKeys.KEY_TEXT);

            // Delete arms on first click, fires NOTE_DELETE on confirm inside the window.
            final GovNotesPanel pDel = fresh();
            final NoteRow note = new NoteRow("n-1", "uuid-1", "Alex", "me", "text", 0L, 0L);
            ButtonMatrix.fireNone("NOTES", "DELETE_ARM", () -> pDel.handleDelete(note),
                    () -> ButtonMatrix.check("n-1".equals(pDel.deleteArmedNoteId),
                            "delete did not arm"));
            pDel.deleteArmedAt = System.currentTimeMillis() - 1000L;
            ButtonMatrix.fire("NOTES", "DELETE_FIRE", () -> {
                pDel.pending.clear();
                pDel.mutationPending = false;
                pDel.handleDelete(note);
            }, OpCode.NOTE_DELETE, "target", NoteKeys.KEY_NOTE_ID);

            // Row-open hands off to the live screen (host.openPerson): headed-only seam.
            final GovNotesPanel pRow = fresh();
            ButtonMatrix.fireBlock("NOTES", "ROW_OPEN", () -> pRow.handleRow(note),
                    "person handoff needs a live host");

            final GovNotesPanel pEdit = fresh();
            ButtonMatrix.fireNoneSeam("NOTES", "EDIT_STAGE", () -> pEdit.handleEdit(note),
                    () -> ButtonMatrix.check("n-1".equals(pEdit.editingNoteId),
                            "edit did not stage"),
                    "composer sync needs a live host");
        } finally {
            ButtonMatrix.uninstall();
        }
        ButtonMatrix.summary("NOTES");
    }
}
