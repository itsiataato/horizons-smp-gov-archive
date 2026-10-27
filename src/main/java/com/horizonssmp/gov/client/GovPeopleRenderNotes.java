package com.horizonssmp.gov.client;

import net.minecraft.client.gui.GuiGraphics;

final class GovPeopleRenderNotes {
    static void drawNotes(final GovPeoplePanel p, final GuiGraphics graphics, final int mouseX,
            final int mouseY) {
        if (p.notesHeaderRect.w() > 0) {
            GovText.clipped(graphics, p.font, p.notesTitleComponent,
                    p.notesHeaderRect.x(), p.notesHeaderRect.y(), p.notesTitleWidth,
                    GovTheme.TEXT);
            GovText.right(graphics, p.font, p.notesCountText,
                    p.notesHeaderRect.right(), p.notesHeaderRect.y(), GovTheme.MUTED);
        }
        GovPeopleRender.drawListFrame(graphics, p.notesListRect);
        if (p.selectedUuid.isEmpty()) {
            GovWidgets.emptyState(graphics, p.font, p.notesListInnerRect,
                    p.noCitizenText, p.noSelectionText);
        } else if (p.detailState == GovPeoplePanelModel.LoadState.LOADING
                || p.detailState == GovPeoplePanelModel.LoadState.NONE) {
            GovWidgets.emptyState(graphics, p.font, p.notesListInnerRect,
                    p.loadingText, null);
        } else if (p.detailState == GovPeoplePanelModel.LoadState.ERROR) {
            GovWidgets.emptyState(graphics, p.font, p.notesListInnerRect,
                    p.detailErrorText, p.detailError);
        } else if (!p.canViewNotes) {
            GovWidgets.emptyState(graphics, p.font, p.notesListInnerRect,
                    p.notesHiddenText, null);
        } else if (p.notesState == GovPeoplePanelModel.LoadState.LOADING) {
            GovWidgets.emptyState(graphics, p.font, p.notesListInnerRect,
                    p.loadingText, null);
        } else if (p.notesState == GovPeoplePanelModel.LoadState.ERROR) {
            GovWidgets.emptyState(graphics, p.font, p.notesListInnerRect,
                    p.notesErrorText, null);
        } else if (p.noteViews.isEmpty()) {
            GovWidgets.emptyState(graphics, p.font, p.notesListInnerRect,
                    p.noNotesText, null);
        } else {
            drawNoteRows(p, graphics, mouseX, mouseY);
        }
        drawComposer(p, graphics, mouseX, mouseY);
        drawNotesBar(p, graphics, mouseX, mouseY);
    }

    static void drawNoteRows(final GovPeoplePanel p, final GuiGraphics graphics, final int mouseX,
            final int mouseY) {
        if (p.notesListInnerRect.w() <= 0 || p.notesListInnerRect.h() <= 0) {
            return;
        }
        graphics.enableScissor(p.notesListInnerRect.x(), p.notesListInnerRect.y(),
                p.notesListInnerRect.right(), p.notesListInnerRect.bottom());
        final int textW = Math.max(1, p.noteTextW);
        int y = p.notesListRect.y() + 1 - p.notesScroll;
        for (int index = 0; index < p.noteViews.size(); index++) {
            final GovPeoplePanelModel.NoteView note = p.noteViews.get(index);
            if (y + p.noteRowHeight >= p.notesListInnerRect.y()
                    && y >= p.notesListInnerRect.y()
                    && y + p.noteRowHeight <= p.notesListInnerRect.bottom()) {
                final boolean selected = index == p.selectedNoteIndex;
                final boolean hovered = GovTheme.inside(mouseX, mouseY,
                        p.notesListRect.x() + 2, y,
                        p.notesListRect.w() - 6, p.noteRowHeight - 1);
                if (selected) {
                    graphics.fill(p.notesListRect.x() + 2, y, p.notesListRect.right() - 2,
                            y + p.noteRowHeight - 1, GovTheme.SELECTED);
                    graphics.fill(p.notesListRect.x() + 2, y, p.notesListRect.x() + 5,
                            y + p.noteRowHeight - 1, GovTheme.ACCENT);
                } else if (hovered) {
                    graphics.fill(p.notesListRect.x() + 2, y, p.notesListRect.right() - 2,
                            y + p.noteRowHeight - 1, GovTheme.HOVER);
                }
                GovText.clipped(graphics, p.font, note.meta(),
                        p.notesListRect.x() + 8, y + 1, textW, GovTheme.FAINT);
                for (int line = 0; line < p.noteMaxLines
                        && line < note.textLines().size(); line++) {
                    graphics.drawString(p.font, note.textLines().get(line),
                            p.notesListRect.x() + 8, y + 11 + line * p.font.lineHeight,
                            GovTheme.BODY, false);
                }
                drawNoteButtons(p, graphics, note, index, mouseX, mouseY);
            }
            y += p.noteRowHeight;
        }
        graphics.disableScissor();
        if (p.notesScrollMax > 0) {
            GovTheme.scrollbar(graphics, p.notesListRect.right() - 4,
                    p.notesListRect.y() + 2, Math.max(1, p.notesListRect.h() - 4),
                    p.notesScroll, p.notesScrollMax);
        }
    }

    static void drawNoteButtons(final GovPeoplePanel p, final GuiGraphics graphics,
            final GovPeoplePanelModel.NoteView note, final int index, final int mouseX,
            final int mouseY) {
        if (!p.canManageNotes || !p.canPostNotes) {
            return;
        }
        if (index < 0 || index >= p.noteEditRects.size()
                || index >= p.noteDeleteRects.size()
                || !GovPeopleHits.noteButtonsVisible(p, index)) {
            return;
        }
        final boolean armed = note.id().equals(p.deleteArmedNoteId);
        GovPeopleRender.drawButton(p, graphics, p.noteEditRects.get(index), p.noteEditText,
                !GovPeopleUtil.mutationPending(p), mouseX, mouseY, false);
        GovPeopleRender.drawButton(p, graphics, p.noteDeleteRects.get(index),
                armed ? p.noteConfirmDeleteText : p.noteDeleteText,
                !GovPeopleUtil.mutationPending(p), mouseX, mouseY, false);
    }

    static void drawComposer(final GovPeoplePanel p, final GuiGraphics graphics, final int mouseX,
            final int mouseY) {
        if (!p.canManageNotes || !p.canPostNotes
                || p.detailState != GovPeoplePanelModel.LoadState.READY
                || p.selectedUuid.isEmpty()) {
            return;
        }
        GovWidgets.fieldFrame(graphics, p.noteFieldRect,
                p.noteBox != null && p.noteBox.isFocused());
        final boolean editing = !p.editingNoteId.isEmpty();
        GovPeopleRender.drawButton(p, graphics, p.noteActionRect,
                editing ? p.noteSaveText : p.noteAddText, noteSaveEnabled(p),
                mouseX, mouseY, true);
        if (editing) {
            GovPeopleRender.drawButton(p, graphics, p.noteCancelRect, p.noteCancelText, true,
                    mouseX, mouseY, false);
        }
    }

    static void drawNotesBar(final GovPeoplePanel p, final GuiGraphics graphics, final int mouseX,
            final int mouseY) {
        GovWidgets.rule(graphics, p.barRect.x(), p.barRect.y() - 1, p.barRect.w(),
                GovTheme.BORDER_SOFT);
        GovPeopleRender.drawStatus(p, graphics, p.notesStatusRect);
        final boolean prevEnabled = p.canViewNotes && !p.selectedUuid.isEmpty()
                && p.notesPage > 0 && !GovPeopleUtil.mutationPending(p);
        final boolean nextEnabled = p.canViewNotes && !p.selectedUuid.isEmpty()
                && p.notesPage + 1 < p.notesPages && !GovPeopleUtil.mutationPending(p);
        GovPeopleRender.drawButton(p, graphics, p.notePrevRect, p.prevText, prevEnabled, mouseX, mouseY, false);
        GovPeopleRender.drawButton(p, graphics, p.noteNextRect, p.nextText, nextEnabled, mouseX, mouseY, false);
        GovText.clipped(graphics, p.font, p.notesPageText, p.notesPageRect.x() + 2,
                p.notesPageRect.y() + 4, Math.max(0, p.notesPageRect.w() - 4),
                GovTheme.MUTED);
        GovPeopleRender.drawButton(p, graphics, p.notesRefreshRect, p.refreshText,
                p.canViewNotes && !p.selectedUuid.isEmpty() && !GovPeopleUtil.mutationPending(p),
                mouseX, mouseY, false);
    }

    static boolean noteSaveEnabled(final GovPeoplePanel p) {
        return !GovPeopleUtil.mutationPending(p) && p.noteBox != null
                && !p.noteBox.getValue().isBlank();
    }
}
