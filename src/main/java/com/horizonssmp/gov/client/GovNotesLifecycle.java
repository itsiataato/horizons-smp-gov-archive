package com.horizonssmp.gov.client;

import com.horizonssmp.gov.client.GovNotesTypes.*;
import static com.horizonssmp.gov.client.GovNotesTypes.*;

import com.horizonssmp.gov.common.OpCode;
import com.horizonssmp.gov.network.GovActionResultPayload;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

class GovNotesLifecycle extends GovNotesInput {
    GovNotesLifecycle(final GovDashboardScreen host) {
        super(host);
    }

    protected void setSubtab(final int index) {
        final int next = GovMetrics.clamp(index, 0, 2);
        if (next == this.mode) {
            if (this.active) {
                ensureMode();
            }
            return;
        }
        this.mode = next;
        this.pickerOpen = false;
        this.status = "";
        this.statusIsError = false;
        disarm();
        cancelEdit();
        layoutModeBands();
        layoutComposer();
        rebuildScopeGeometry();
        rebuildViewGeometry(currentView());
        if (this.active) {
            ensureMode();
        }
        syncWidgets();
    }

    protected void setActive(final boolean active) {
        this.active = active;
        expireMutationIfStale();
        if (!active) {
            this.pickerOpen = false;
            this.enterRepeat.reset();
            disarm();
            clearFocus();
            syncWidgets();
            return;
        }
        syncWidgets();
        ensureMode();
    }

    protected void reset() {
        this.pending.clear();
        this.latestScopesReqId = "";
        this.scopesPending = false;
        this.scopesSentAt = 0L;
        this.mutationPending = false;
        this.mutationSentAt = 0L;
        this.status = "";
        this.statusIsError = false;
        this.pickerOpen = false;
        this.active = false;
        this.enterRepeat.reset();
        disarm();
        this.editingNoteId = "";
        this.editingView = null;
        this.editingTarget = "";
        this.editingTargetLabel = "";
        this.scopesState = Load.NONE;
        this.scopesError = "";
        this.scopesErrorLines = NO_LINES;
        this.jobScopes = NO_SCOPES;
        this.businessScopes = NO_SCOPES;
        this.selectedJobId = "";
        this.selectedJobLabel = "";
        this.selectedBizId = "";
        this.selectedBizLabel = "";
        this.jobViews.clear();
        this.bizStates.clear();
        this.allView.clearData();
        if (this.targetBox != null) {
            this.targetBox.setValue("");
            this.targetBox.setVisible(false);
        }
        if (this.textBox != null) {
            this.textBox.setValue("");
            this.textBox.setVisible(false);
        }
        this.hasText = false;
        this.hasTarget = false;
        this.targetDirty = false;
        clearFocus();
        rebuildScopeGeometry();
        rebuildViewGeometry(null);
    }

    protected void render(final GuiGraphics graphics, final int mouseX, final int mouseY) {
        render(graphics, mouseX, mouseY, 0.0F);
    }

    protected void render(final GuiGraphics graphics, final int mouseX, final int mouseY,
            final float partialTick) {
        if (!this.active || this.bounds.w() <= 0 || this.bounds.h() <= 0) {
            return;
        }
        expireMutationIfStale();
        drawHeader(graphics);
        if (this.wide) {
            drawScopeColumn(graphics, mouseX, mouseY);
        } else {
            drawScopeBar(graphics, mouseX, mouseY);
        }
        if (this.pickerOpen && !this.wide) {
            drawScopePicker(graphics, mouseX, mouseY);
        } else {
            drawMain(graphics, mouseX, mouseY);
        }
        drawFooter(graphics, mouseX, mouseY);
    }

    protected void layout(final int left, final int top, final int right, final int bottom,
            final Font font) {
        super.layout(left, top, right, bottom, font);
        rebuildScopeGeometry();
        rebuildViewGeometry(currentView());
        syncWidgets();
    }

    protected void onActionResult(final GovActionResultPayload payload) {
        final Pending request = this.pending.remove(payload.reqId());
        if (request == null) {
            return;
        }
        switch (request.kind()) {
            case SCOPES -> {
                if (request.op() != OpCode.NOTE_SCOPES
                        || !payload.reqId().equals(this.latestScopesReqId)) {
                    return;
                }
                this.scopesPending = false;
                if (payload.ok()) {
                    applyScopes(payload.delta());
                } else {
                    this.scopesState = Load.ERROR;
                    this.scopesError = payload.errorText().isBlank()
                            ? fallbackError("gui.gov.notes.error")
                            : payload.errorText();
                    rebuildScopeGeometry();
                    rebuildViewGeometry(currentView());
                    syncWidgets();
                }
            }
            case LIST -> {
                final View view = request.view();
                if (view == null || !payload.reqId().equals(view.latestListReqId)
                        || (request.op() != OpCode.NOTE_LIST
                                && request.op() != OpCode.NOTE_SCOPED_LIST)) {
                    return;
                }
                if ((view.mode == 0 && request.op() != OpCode.NOTE_LIST)
                        || (view.mode != 0 && request.op() != OpCode.NOTE_SCOPED_LIST)) {
                    return;
                }
                view.listPending = false;
                if (payload.ok()) {
                    applyList(view, request, payload.delta());
                } else {
                    view.state = Load.ERROR;
                    view.error = payload.errorText().isBlank()
                            ? fallbackError("gui.gov.notes.error")
                            : payload.errorText();
                    view.notes = NO_NOTES;
                    view.display = NO_DISPLAY;
                    view.hits = NO_HITS;
                    view.total = 0;
                    view.pages = 1;
                    updateViewText(view);
                    rebuildViewGeometry(view);
                    if (view == currentView()) {
                        syncWidgets();
                    }
                }
            }
            case MUTATE -> {
                this.mutationPending = false;
                if (request.op() != OpCode.NOTE_ADD && request.op() != OpCode.NOTE_EDIT
                        && request.op() != OpCode.NOTE_DELETE
                        && request.op() != OpCode.NOTE_SCOPED_ADD
                        && request.op() != OpCode.NOTE_SCOPED_EDIT
                        && request.op() != OpCode.NOTE_SCOPED_DELETE) {
                    return;
                }
                final View view = request.view();
                if (payload.ok()) {
                    this.status = payload.delta().isBlank() ? this.savedText : payload.delta();
                    this.statusIsError = false;
                    disarm();
                    if (view != null) {
                        view.stale = true;
                        if (view.mode == 1 && request.op() == OpCode.NOTE_SCOPED_ADD) {
                            view.target = request.target() == null ? "" : request.target();
                        }
                        if (view == currentView()) {
                            finishMutation(view);
                            if (this.active) {
                                final int page = request.op() == OpCode.NOTE_ADD
                                        || request.op() == OpCode.NOTE_SCOPED_ADD ? 0 : view.page;
                                requestList(view, page, view.target);
                            }
                        }
                    }
                } else {
                    this.status = payload.errorText().isBlank()
                            ? fallbackError("gui.gov.notes.error")
                            : payload.errorText();
                    this.statusIsError = true;
                    if (request.op() == OpCode.NOTE_DELETE
                            || request.op() == OpCode.NOTE_SCOPED_DELETE) {
                        disarm();
                    }
                    syncWidgets();
                }
            }
        }
    }
}
