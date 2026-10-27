package com.horizonssmp.gov.client;

import com.horizonssmp.gov.client.GovNotesTypes.*;
import static com.horizonssmp.gov.client.GovNotesTypes.*;

class GovNotesSelection extends GovNotesScopes {
    GovNotesSelection(final GovDashboardScreen host) {
        super(host);
    }

    protected void selectJob(final ScopeRow scope) {
        if (scope == null) {
            return;
        }
        this.selectedJobId = scope.id();
        this.selectedJobLabel = scope.label();
        View view = this.jobViews.get(scope.id());
        if (view == null) {
            view = new View(1, "job", scope.id(), false);
            this.jobViews.put(scope.id(), view);
        }
        this.pickerOpen = false;
        this.status = "";
        this.statusIsError = false;
        disarm();
        cancelEdit();
        if (this.targetBox != null) {
            this.targetBox.setValue(view.target);
            this.hasTarget = !view.target.isBlank();
            this.targetDirty = false;
        }
        layoutComposer();
        rebuildScopeGeometry();
        rebuildViewGeometry(view);
        syncWidgets();
        if (this.active) {
            ensureView(view);
        }
    }

    protected void selectBusiness(final ScopeRow scope) {
        if (scope == null) {
            return;
        }
        this.selectedBizId = scope.id();
        this.selectedBizLabel = scope.label();
        BusinessState state = this.bizStates.get(scope.id());
        if (state == null) {
            state = new BusinessState(scope.id(), scope.label());
            this.bizStates.put(scope.id(), state);
        }
        this.pickerOpen = false;
        this.status = "";
        this.statusIsError = false;
        disarm();
        cancelEdit();
        if (this.targetBox != null) {
            final String target = state.personalMode ? state.target : "";
            this.targetBox.setValue(target);
            this.hasTarget = !target.isBlank();
            this.targetDirty = false;
        }
        layoutComposer();
        rebuildScopeGeometry();
        rebuildViewGeometry(state.current());
        syncWidgets();
        if (this.active) {
            ensureView(state.current());
        }
    }

    protected void switchBusinessPersonal(final boolean personal) {
        final BusinessState state = this.bizStates.get(this.selectedBizId);
        if (state == null || state.personalMode == personal) {
            return;
        }
        state.personalMode = personal;
        this.status = "";
        this.statusIsError = false;
        disarm();
        cancelEdit();
        if (this.targetBox != null) {
            final String target = personal ? state.target : "";
            this.targetBox.setValue(target);
            this.hasTarget = !target.isBlank();
            this.targetDirty = false;
        }
        layoutComposer();
        rebuildViewGeometry(state.current());
        syncWidgets();
        if (this.active) {
            ensureView(state.current());
        }
    }

    protected void selectScope(final ScopeRow scope) {
        if (this.mode == 1) {
            selectJob(scope);
        } else if (this.mode == 2) {
            selectBusiness(scope);
        }
    }

    protected void cancelEdit() {
        this.editingNoteId = "";
        this.editingView = null;
        this.editingTarget = "";
        this.editingTargetLabel = "";
        if (this.textBox != null) {
            this.textBox.setValue("");
        }
        final View view = currentView();
        final String keep = view != null && (view.mode == 1
                || (view.mode == 2 && view.personal)) ? view.target : "";
        if (this.targetBox != null) {
            this.targetBox.setValue(keep);
        }
        this.hasText = false;
        this.hasTarget = !keep.isBlank();
        this.targetDirty = false;
        clearFocus();
        layoutComposer();
        layoutFields();
        syncWidgets();
    }
}
