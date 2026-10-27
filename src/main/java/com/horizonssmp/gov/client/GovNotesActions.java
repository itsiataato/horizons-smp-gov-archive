package com.horizonssmp.gov.client;

import com.horizonssmp.gov.client.GovNotesTypes.*;
import static com.horizonssmp.gov.client.GovNotesTypes.*;

import java.util.LinkedHashMap;
import java.util.Map;

import com.horizonssmp.gov.common.NoteKeys;
import com.horizonssmp.gov.common.OpCode;

class GovNotesActions extends GovNotesSelection {
    GovNotesActions(final GovDashboardScreen host) {
        super(host);
    }

    protected void refreshCurrent() {
        expireMutationIfStale();
        final View view = currentView();
        if (view == null) {
            if (this.scopesState == Load.ERROR) {
                requestScopes();
            }
            return;
        }
        if (this.mode == 2 && view.personal) {
            final String target = this.targetBox == null ? "" : this.targetBox.getValue().strip();
            final BusinessState state = this.bizStates.get(this.selectedBizId);
            if (state != null) {
                state.target = target;
            }
            if (target.isEmpty()) {
                this.targetDirty = false;
                view.state = Load.NONE;
                view.listPending = false;
                view.stale = false;
                view.latestListReqId = "";
                view.target = "";
                view.notes = NO_NOTES;
                view.display = NO_DISPLAY;
                view.hits = NO_HITS;
                view.total = 0;
                view.page = 0;
                view.pages = 1;
                updateViewText(view);
                rebuildViewGeometry(view);
                syncWidgets();
                return;
            }
            this.targetDirty = false;
            view.target = target;
            requestList(view, 0, target);
            return;
        }
        if (this.mode == 1) {
            final String target = this.targetBox == null ? "" : this.targetBox.getValue().strip();
            final int page = target.equals(view.target) ? view.page : 0;
            view.target = target;
            requestList(view, page, target);
            return;
        }
        requestList(view, view.page, "");
    }

    protected void retryCurrent() {
        expireMutationIfStale();
        if (this.scopesState == Load.ERROR) {
            requestScopes();
            return;
        }
        final View view = currentView();
        if (view != null && view.state == Load.ERROR) {
            requestList(view, view.page, view.target);
        }
    }

    protected void handleDelete(final NoteRow note) {
        final long now = System.currentTimeMillis();
        if (note.id().equals(this.deleteArmedNoteId)) {
            final long elapsed = now - this.deleteArmedAt;
            if (elapsed < ARM_FLOOR_MS) {
                return;
            }
            if (elapsed <= ARM_EXPIRY_MS) {
                disarm();
                sendDelete(note);
                return;
            }
        }
        this.deleteArmedNoteId = note.id();
        this.deleteArmedAt = now;
    }

    protected void sendDelete(final NoteRow note) {
        final View view = currentView();
        if (view == null) {
            return;
        }
        final Map<String, String> payload = basePayload(view);
        if (view.mode == 0) {
            final String target = !note.targetUuid().isBlank()
                    ? note.targetUuid() : note.targetName();
            if (target.isBlank()) {
                return;
            }
            payload.put("target", target);
            payload.put(NoteKeys.KEY_NOTE_ID, note.id());
            mutate(OpCode.NOTE_DELETE, payload, view, target, view.page);
            return;
        }
        final String target = !note.targetName().isBlank()
                ? note.targetName() : note.targetUuid();
        if (!target.isBlank()) {
            payload.put("target", target);
        }
        payload.put(NoteKeys.KEY_NOTE_ID, note.id());
        mutate(OpCode.NOTE_SCOPED_DELETE, payload, view, target, view.page);
    }

    protected void handleEdit(final NoteRow note) {
        final View view = currentView();
        if (view == null) {
            return;
        }
        this.editingNoteId = note.id();
        this.editingView = view;
        this.editingTarget = view.mode == 0
                ? (!note.targetUuid().isBlank() ? note.targetUuid() : note.targetName())
                : (!note.targetName().isBlank() ? note.targetName() : note.targetUuid());
        this.editingTargetLabel = !note.targetName().isBlank() ? note.targetName()
                : !note.targetUuid().isBlank() ? note.targetUuid() : this.teamText;
        this.deleteArmedNoteId = "";
        if (this.targetBox != null) {
            final String target = !note.targetName().isBlank()
                    ? note.targetName() : note.targetUuid();
            this.targetBox.setValue(target);
            this.hasTarget = !target.isBlank();
            this.targetDirty = false;
        }
        if (this.textBox != null) {
            this.textBox.setValue(note.text());
            this.hasText = !note.text().isBlank();
        }
        layoutComposer();
        layoutFields();
        syncWidgets();
    }

    protected void handleRow(final NoteRow note) {
        disarm();
        if (!note.targetUuid().isBlank()) {
            this.host.openPerson(note.targetUuid(), note.targetName());
        }
    }

    protected void finishMutation(final View view) {
        this.editingNoteId = "";
        this.editingView = null;
        this.editingTarget = "";
        this.editingTargetLabel = "";
        if (this.textBox != null) {
            this.textBox.setValue("");
        }
        this.hasText = false;
        final String keep = view.mode == 1 || (view.mode == 2 && view.personal)
                ? view.target : "";
        if (this.targetBox != null) {
            this.targetBox.setValue(keep);
            this.hasTarget = !keep.isBlank();
        }
        this.targetDirty = false;
        layoutComposer();
        layoutFields();
        syncWidgets();
    }

    protected void saveComposer() {
        final View view = currentView();
        if (view == null || view.state != Load.READY || !view.canPost || this.textBox == null) {
            return;
        }
        final String text = this.textBox.getValue().strip();
        if (text.isEmpty()) {
            return;
        }
        final boolean editing = !this.editingNoteId.isEmpty();
        final Map<String, String> payload = basePayload(view);
        if (editing) {
            if (this.editingView != view) {
                return;
            }
            if (view.mode == 0) {
                if (this.editingTarget.isBlank()) {
                    return;
                }
                payload.put("target", this.editingTarget);
                payload.put(NoteKeys.KEY_NOTE_ID, this.editingNoteId);
                payload.put(NoteKeys.KEY_TEXT, text);
                mutate(OpCode.NOTE_EDIT, payload, view, this.editingTarget, view.page);
                return;
            }
            if (!this.editingTarget.isBlank()) {
                payload.put("target", this.editingTarget);
            }
            payload.put(NoteKeys.KEY_NOTE_ID, this.editingNoteId);
            payload.put(NoteKeys.KEY_TEXT, text);
            mutate(OpCode.NOTE_SCOPED_EDIT, payload, view, this.editingTarget, view.page);
            return;
        }

        String target = this.targetBox == null ? "" : this.targetBox.getValue().strip();
        if (view.mode == 2 && !view.personal) {
            target = "";
        }
        if (requiresTarget() && target.isEmpty()) {
            return;
        }
        if (view.mode == 2 && view.personal && this.targetDirty) {
            return;
        }
        if (view.mode == 0) {
            payload.put("target", target);
            payload.put(NoteKeys.KEY_TEXT, text);
            mutate(OpCode.NOTE_ADD, payload, view, target, view.page);
            return;
        }
        if (!target.isBlank()) {
            payload.put("target", target);
        }
        payload.put(NoteKeys.KEY_TEXT, text);
        mutate(OpCode.NOTE_SCOPED_ADD, payload, view, target, view.page);
    }
}
