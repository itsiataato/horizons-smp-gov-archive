package com.horizonssmp.gov.client;

import com.horizonssmp.gov.client.GovNotesTypes.*;
import static com.horizonssmp.gov.client.GovNotesTypes.*;

import org.lwjgl.glfw.GLFW;

class GovNotesInput extends GovNotesRender3 {
    GovNotesInput(final GovDashboardScreen host) {
        super(host);
    }

    protected boolean mouseClicked(final double mouseX, final double mouseY, final int button) {
        if (!this.active || button != 0) {
            return false;
        }
        expireMutationIfStale();
        if (!this.wide && this.pickerOpen) {
            if (this.scopeToggleRect.contains(mouseX, mouseY)) {
                this.pickerOpen = false;
                rebuildScopeGeometry();
                syncWidgets();
                return true;
            }
            for (final ScopeHit hit : this.pickerHits) {
                if (hit.rect().contains(mouseX, mouseY)) {
                    selectScope(hit.scope());
                    return true;
                }
            }
            if (this.scopeRetryRect.contains(mouseX, mouseY)
                    && this.scopesState == Load.ERROR) {
                requestScopes();
                return true;
            }
            if (this.mainRect.contains(mouseX, mouseY)) {
                return true;
            }
            this.pickerOpen = false;
            rebuildScopeGeometry();
            syncWidgets();
            return true;
        }
        if (!this.wide && (this.mode == 1 || this.mode == 2)
                && this.scopeToggleRect.contains(mouseX, mouseY)) {
            if ((this.scopesState == Load.READY && !currentScopes().isEmpty())
                    || this.scopesState == Load.ERROR) {
                this.pickerOpen = !this.pickerOpen;
                rebuildScopeGeometry();
                syncWidgets();
            }
            return true;
        }
        if (this.mode == 2 && this.bizSwitchRect.w() > 0) {
            if (this.boardRect.contains(mouseX, mouseY)) {
                switchBusinessPersonal(false);
                return true;
            }
            if (this.personalRect.contains(mouseX, mouseY)) {
                switchBusinessPersonal(true);
                return true;
            }
        }
        if (this.refreshRect.contains(mouseX, mouseY)) {
            if (refreshEnabled(currentView())) {
                refreshCurrent();
            }
            return true;
        }
        if (this.prevRect.contains(mouseX, mouseY)) {
            final View view = currentView();
            if (prevEnabled(view)) {
                requestList(view, view.page - 1, view.target);
            }
            return true;
        }
        if (this.nextRect.contains(mouseX, mouseY)) {
            final View view = currentView();
            if (nextEnabled(view)) {
                requestList(view, view.page + 1, view.target);
            }
            return true;
        }
        if (this.postRect.contains(mouseX, mouseY)) {
            if (postEnabled(currentView())) {
                saveComposer();
            }
            return true;
        }
        if (this.cancelRect.contains(mouseX, mouseY)) {
            cancelEdit();
            return true;
        }
        if (this.wide && this.scopeRetryRect.contains(mouseX, mouseY)
                && this.scopesState == Load.ERROR) {
            requestScopes();
            return true;
        }
        if (this.retryRect.contains(mouseX, mouseY)
                && (this.scopesState == Load.ERROR
                        || (currentView() != null && currentView().state == Load.ERROR))) {
            retryCurrent();
            return true;
        }
        for (final ScopeHit hit : this.scopeHits) {
            if (hit.rect().contains(mouseX, mouseY)) {
                selectScope(hit.scope());
                return true;
            }
        }
        final View view = currentView();
        if (view != null) {
            for (final NoteHit hit : view.hits) {
                if (hit.rect().contains(mouseX, mouseY)) {
                    if (hit.kind() == HitKind.DELETE) {
                        handleDelete(hit.note());
                    } else if (hit.kind() == HitKind.EDIT) {
                        handleEdit(hit.note());
                    } else {
                        handleRow(hit.note());
                    }
                    return true;
                }
            }
        }
        if ((targetFieldVisible() && this.targetFieldRect.contains(mouseX, mouseY))
                || (textFieldVisible() && this.textFieldRect.contains(mouseX, mouseY))) {
            return false;
        }
        clearFocus();
        return this.bounds.contains(mouseX, mouseY);
    }

    protected boolean mouseScrolled(final double mouseX, final double mouseY, final double scrollY) {
        if (!this.active) {
            return false;
        }
        if (!this.wide && this.pickerOpen) {
            return this.mainRect.contains(mouseX, mouseY);
        }
        final View view = currentView();
        if (view == null || view.state != Load.READY) {
            return false;
        }
        if (!this.listRect.contains(mouseX, mouseY)) {
            return false;
        }
        if (view.scrollMax > 0) {
            final int step = Math.max(18, view.rowPitch);
            view.scroll = GovMetrics.clamp(view.scroll - (int) (scrollY * step),
                    0, view.scrollMax);
            rebuildHits(view);
        }
        return true;
    }

    protected void submitEnter() {
        final View view = currentView();
        if (postEnabled(view)) {
            saveComposer();
        }
    }

    protected boolean keyPressed(final int keyCode, final int scanCode, final int modifiers) {
        if (!this.active) {
            return false;
        }
        expireMutationIfStale();
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (blurVisibleField()) {
                return true;
            }
            if (this.pickerOpen) {
                this.pickerOpen = false;
                rebuildScopeGeometry();
                syncWidgets();
                return true;
            }
            if (!this.editingNoteId.isEmpty()) {
                cancelEdit();
                return true;
            }
            if (!this.deleteArmedNoteId.isEmpty()) {
                disarm();
                return true;
            }
            return false;
        }
        final boolean fieldFocused = this.host.getFocused() == this.targetBox
                || this.host.getFocused() == this.textBox;
        if (GovKeyRepeat.isEnter(keyCode)) {
            if (this.enterRepeat.accepts(System.currentTimeMillis())) {
                submitEnter();
            }
            return true;
        }
        if (fieldFocused) {
            return false;
        }
        final View view = currentView();
        if (view == null || view.state != Load.READY) {
            return false;
        }
        if (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN) {
            if (view.scrollMax > 0) {
                final int step = keyCode == GLFW.GLFW_KEY_UP ? -view.rowPitch : view.rowPitch;
                view.scroll = GovMetrics.clamp(view.scroll + step, 0, view.scrollMax);
                rebuildHits(view);
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_HOME) {
            view.scroll = 0;
            rebuildHits(view);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_END) {
            view.scroll = view.scrollMax;
            rebuildHits(view);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_PAGE_UP) {
            if (prevEnabled(view)) {
                requestList(view, view.page - 1, view.target);
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_PAGE_DOWN) {
            if (nextEnabled(view)) {
                requestList(view, view.page + 1, view.target);
            }
            return true;
        }
        return false;
    }

    protected boolean charTyped(final char codePoint, final int modifiers) {
        return false;
    }
}
