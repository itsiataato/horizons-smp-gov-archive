package com.horizonssmp.gov.client;

import com.horizonssmp.gov.client.GovNotesTypes.*;
import static com.horizonssmp.gov.client.GovNotesTypes.*;

import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

class GovNotesRender3 extends GovNotesRender2 {
    GovNotesRender3(final GovDashboardScreen host) {
        super(host);
    }

    protected void drawError(final GuiGraphics graphics, final GovRect area, final String message,
            final boolean scopes, final int mouseX, final int mouseY) {
        if (area.w() <= 0 || area.h() <= 0) {
            return;
        }
        final int width = Math.max(0, area.w() - 8);
        final int titleY = area.y() + 4;
        final int titleW = Math.min(width, this.font.width(this.errorTitleBold));
        GovText.clipped(graphics, this.font, this.errorTitleBold,
                area.x() + 4 + Math.max(0, (width - titleW) / 2), titleY, width,
                GovTheme.TEXT);
        final View view = currentView();
        final List<FormattedCharSequence> lines = scopes
                ? this.scopesErrorLines
                : view == null ? NO_LINES : view.errorLines;
        int y = titleY + 12;
        for (final FormattedCharSequence line : lines) {
            if (y + 9 > this.retryRect.y() - 2) {
                break;
            }
            final int lineW = this.font.width(line);
            graphics.drawString(this.font, line,
                    area.x() + 4 + Math.max(0, (width - lineW) / 2), y,
                    GovTheme.MUTED, false);
            y += 9;
        }
        if (this.retryRect.w() > 0 && this.retryRect.h() > 0) {
            drawButton(graphics, this.retryRect, this.retryText, true,
                    this.retryRect.contains(mouseX, mouseY), false);
        }
    }

    protected void drawList(final GuiGraphics graphics, final View view, final int mouseX,
            final int mouseY) {
        if (this.listRect.w() <= 0 || this.listRect.h() <= 0 || view.rowPitch <= 0) {
            return;
        }
        GovWidgets.rule(graphics, this.listRect.x(), this.listRect.y(),
                this.listRect.w(), GovTheme.BORDER_SOFT);
        GovWidgets.rule(graphics, this.listRect.x(), this.listRect.bottom() - 1,
                this.listRect.w(), GovTheme.BORDER_SOFT);
        final boolean scissored = this.listRect.w() > 2 && this.listRect.h() > 2;
        if (scissored) {
            graphics.enableScissor(this.listRect.x() + 1, this.listRect.y() + 1,
                    this.listRect.right() - 1, this.listRect.bottom() - 1);
        }
        try {
            final int top = this.listRect.y() + 2 - view.scroll;
            for (int index = 0; index < view.display.size(); index++) {
                final int y = top + index * view.rowPitch;
                if (y + view.rowPitch <= this.listRect.y() + 2
                        || y >= this.listRect.bottom() - 2) {
                    continue;
                }
                drawNoteRow(graphics, view, view.display.get(index), y, mouseX, mouseY);
            }
        } finally {
            if (scissored) {
                graphics.disableScissor();
            }
        }
        if (view.scrollMax > 0) {
            GovTheme.scrollbar(graphics, this.listRect.right() - 3, this.listRect.y() + 2,
                    Math.max(0, this.listRect.h() - 4), view.scroll, view.scrollMax);
        }
    }

    protected void drawNoteRow(final GuiGraphics graphics, final View view,
            final DisplayRow row, final int y, final int mouseX, final int mouseY) {
        final int rowBottom = Math.min(y + view.rowPitch, this.listRect.bottom() - 2);
        if (rowBottom <= y) {
            return;
        }
        final boolean armed = row.note().id().equals(this.deleteArmedNoteId)
                && System.currentTimeMillis() - this.deleteArmedAt <= ARM_EXPIRY_MS;
        final boolean hovered = mouseX >= this.listRect.x() + 2
                && mouseX < this.listRect.right() - 2 && mouseY >= y && mouseY < rowBottom;
        if (armed) {
            graphics.fill(this.listRect.x() + 2, y, this.listRect.right() - 2, rowBottom,
                    GovTheme.SELECTED);
        } else if (hovered) {
            graphics.fill(this.listRect.x() + 2, y, this.listRect.right() - 2, rowBottom,
                    GovTheme.HOVER);
        }

        final int btnY = y + (view.rowPitch >= 20 ? 1 : 0);
        final int textStart = y + (view.rowPitch >= 20 ? 11 : 9);
        final int btnH = Math.min(ROW_BUTTON_H,
                Math.max(0, Math.min(rowBottom, textStart) - btnY));
        int metaRight = this.listRect.right() - 4 - view.barW;
        if (row.canDelete() && view.deleteW > 0 && btnH > 0) {
            final int x = metaRight - view.deleteW;
            if (x >= this.listRect.x()) {
                drawButton(graphics, x, btnY, view.deleteW, btnH,
                        armed ? this.confirmDeleteText : this.deleteText, true,
                        mouseX >= x && mouseX < x + view.deleteW
                                && mouseY >= btnY && mouseY < btnY + btnH, false);
                metaRight = x - 3;
            }
        }
        if (row.canEdit() && view.editW > 0 && btnH > 0) {
            final int x = metaRight - view.editW;
            if (x >= this.listRect.x()) {
                drawButton(graphics, x, btnY, view.editW, btnH, this.editText, true,
                        mouseX >= x && mouseX < x + view.editW
                                && mouseY >= btnY && mouseY < btnY + btnH, false);
                metaRight = x - 3;
            }
        }

        final int textX = view.textX;
        final int metaW = Math.max(0, metaRight - textX);
        GovText.clipped(graphics, this.font, row.meta(), textX,
                y + (view.rowPitch >= 20 ? 2 : 0), metaW, GovTheme.MUTED);
        int lineY = textStart;
        for (final FormattedCharSequence line : row.textLines()) {
            if (lineY + 9 > rowBottom) {
                break;
            }
            graphics.drawString(this.font, line, textX, lineY, GovTheme.BODY, false);
            lineY += 9;
        }
    }

    protected void drawComposer(final GuiGraphics graphics, final View view, final int mouseX,
            final int mouseY) {
        if (this.composerRect.w() <= 0 || this.composerRect.h() <= 0) {
            return;
        }
        GovWidgets.rule(graphics, this.composerRect.x(),
                Math.max(this.mainRect.y(), this.composerRect.y() - 1),
                this.composerRect.w(), GovTheme.BORDER_SOFT);
        final int textY = this.composerRect.y()
                + Math.max(0, (this.composerRect.h() - 9) / 2);
        final boolean targetVisible = targetFieldVisible();
        if (targetVisible && this.targetFieldRect.w() > 8) {
            GovWidgets.fieldFrame(graphics, this.targetFieldRect,
                    this.targetBox != null && this.host.getFocused() == this.targetBox);
        }
        if (view == null || view.state != Load.READY) {
            return;
        }
        if (!view.canPost) {
            final int readX = targetVisible && this.targetFieldRect.w() > 0
                    ? this.targetFieldRect.right() + 4 : this.composerRect.x() + 2;
            GovText.clipped(graphics, this.font, this.readOnlyText, readX, textY,
                    Math.max(0, this.composerRect.right() - readX - 2), GovTheme.FAINT);
            if (!this.editingNoteId.isEmpty() && this.cancelRect.w() > 0) {
                drawButton(graphics, this.cancelRect, this.cancelText, true,
                        this.cancelRect.contains(mouseX, mouseY), false);
            }
            return;
        }
        if (!this.editingNoteId.isEmpty()) {
            final int labelW = Math.max(0, this.editTargetW - 4);
            if (labelW > 0) {
                GovText.clipped(graphics, this.font,
                        this.editingTargetLabel.isBlank() ? this.teamText
                                : this.editingTargetLabel,
                        this.composerRect.x() + 2, textY, labelW, GovTheme.MUTED);
            }
        }
        if (textFieldVisible() && this.textFieldRect.w() > 8) {
            GovWidgets.fieldFrame(graphics, this.textFieldRect,
                    this.textBox != null && this.host.getFocused() == this.textBox);
        }
        final boolean editing = !this.editingNoteId.isEmpty();
        final boolean enabled = postEnabled(view);
        drawButton(graphics, this.postRect, editing ? this.saveText : this.postText,
                enabled, this.postRect.contains(mouseX, mouseY), true);
        if (editing && this.cancelRect.w() > 0) {
            drawButton(graphics, this.cancelRect, this.cancelText, true,
                    this.cancelRect.contains(mouseX, mouseY), false);
        }
    }

    protected void drawFooter(final GuiGraphics graphics, final int mouseX, final int mouseY) {
        if (this.footerRect.w() <= 0 || this.footerRect.h() <= 0) {
            return;
        }
        GovWidgets.rule(graphics, this.footerRect.x(),
                Math.max(this.bounds.y(), this.footerRect.y() - 1),
                this.footerRect.w(), GovTheme.BORDER_SOFT);
        if (!this.status.isEmpty() && this.statusW > 0) {
            GovText.clipped(graphics, this.font, this.status, this.statusX,
                    this.footerTextY, this.statusW,
                    this.statusIsError ? GovTheme.DANGER : GovTheme.MUTED);
        }
        final View view = currentView();
        if (view != null && !view.pageText.isEmpty() && this.pageW > 0) {
            GovText.centered(graphics, this.font, view.pageText, this.pageX,
                    this.footerTextY, this.pageW, GovTheme.MUTED);
        }
        drawButton(graphics, this.prevRect, this.previousText, prevEnabled(view),
                this.prevRect.contains(mouseX, mouseY), false);
        drawButton(graphics, this.nextRect, this.nextText, nextEnabled(view),
                this.nextRect.contains(mouseX, mouseY), false);
        drawButton(graphics, this.refreshRect, refreshLabel(view), refreshEnabled(view),
                this.refreshRect.contains(mouseX, mouseY), false);
    }

    protected void drawMain(final GuiGraphics graphics, final int mouseX, final int mouseY) {
        if (this.mainRect.w() <= 0 || this.mainRect.h() <= 0) {
            return;
        }
        if (this.mode == 2 && this.bizSwitchRect.w() > 0
                && !this.selectedBizId.isEmpty()) {
            drawBusinessSwitch(graphics, mouseX, mouseY);
        }
        if (this.mode == 1 || this.mode == 2) {
            if (this.scopesState == Load.LOADING) {
                drawLoading(graphics, this.listRect);
                return;
            }
            if (this.scopesState == Load.ERROR) {
                drawError(graphics, this.listRect, this.scopesError, true, mouseX, mouseY);
                return;
            }
            if (this.scopesState == Load.READY && currentScopes().isEmpty()) {
                drawEmpty(graphics, this.listRect,
                        this.mode == 1 ? this.noJobBold : this.noBizBold);
                return;
            }
        }
        final View view = currentView();
        if (view == null) {
            drawEmpty(graphics, this.listRect,
                    this.mode == 1 ? this.noJobBold : this.noBizBold);
            return;
        }
        switch (view.state) {
            case NONE -> {
                if (view.mode == 2 && view.personal && view.target.isBlank()) {
                    drawEmpty(graphics, this.listRect,
                            this.hasTarget ? this.pressRefreshBold : this.enterTargetBold);
                } else {
                    drawLoading(graphics, this.listRect);
                }
                drawComposer(graphics, view, mouseX, mouseY);
            }
            case LOADING -> {
                drawLoading(graphics, this.listRect);
                drawComposer(graphics, view, mouseX, mouseY);
            }
            case ERROR -> {
                drawError(graphics, this.listRect, view.error, false, mouseX, mouseY);
                drawComposer(graphics, view, mouseX, mouseY);
            }
            case READY -> {
                if (view.notes.isEmpty()) {
                    drawEmpty(graphics, this.listRect, this.noNotesBold);
                } else {
                    drawList(graphics, view, mouseX, mouseY);
                }
                drawComposer(graphics, view, mouseX, mouseY);
            }
        }
    }

    protected void drawBusinessSwitch(final GuiGraphics graphics, final int mouseX,
            final int mouseY) {
        final BusinessState state = this.bizStates.get(this.selectedBizId);
        if (state == null) {
            return;
        }
        drawButton(graphics, this.boardRect, this.boardText, true,
                this.boardRect.contains(mouseX, mouseY), !state.personalMode);
        drawButton(graphics, this.personalRect, this.personalText, true,
                this.personalRect.contains(mouseX, mouseY), state.personalMode);
        GovWidgets.rule(graphics, this.bizSwitchRect.x(),
                Math.min(this.mainRect.bottom() - 1, this.bizSwitchRect.bottom()),
                this.bizSwitchRect.w(), GovTheme.BORDER_SOFT);
    }
}
