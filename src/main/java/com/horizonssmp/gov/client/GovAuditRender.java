package com.horizonssmp.gov.client;

import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.FormattedCharSequence;

final class GovAuditRender {
    static void render(final GovAuditPanel p, final GuiGraphics graphics, final int mouseX, final int mouseY) {
        if (!p.active || p.font == null) {
            return;
        }
        if (p.mode == GovAuditPanel.Mode.AUDIT) {
            drawAudit(p, graphics, mouseX, mouseY);
        } else {
            GovAuditRenderAdmin.drawAdmin(p, graphics, mouseX, mouseY);
        }
    }

    static void drawAudit(final GovAuditPanel p, final GuiGraphics graphics, final int mouseX, final int mouseY) {
        drawFilters(p, graphics, mouseX, mouseY);
        drawList(p, graphics, mouseX, mouseY);
        drawDetail(p, graphics, mouseX, mouseY);
        drawBottom(p, graphics, mouseX, mouseY);
    }

    static void drawFilters(final GovAuditPanel p, final GuiGraphics graphics, final int mouseX, final int mouseY) {
        GovText.clipped(graphics, p.font, p.actorLabelText, p.actorRect.x(),
                p.filterLabelRow.y(), p.actorRect.w(), GovTheme.MUTED);
        GovText.clipped(graphics, p.font, p.actionLabelText, p.actionRect.x(),
                p.filterLabelRow.y(), p.actionRect.w(), GovTheme.MUTED);
        GovText.clipped(graphics, p.font, p.targetLabelText, p.targetRect.x(),
                p.filterLabelRow.y(), p.targetRect.w(), GovTheme.MUTED);
        GovWidgets.fieldFrame(graphics, p.actorRect,
                p.actorBox != null && p.actorBox.isFocused());
        GovWidgets.fieldFrame(graphics, p.actionRect,
                p.actionBox != null && p.actionBox.isFocused());
        GovWidgets.fieldFrame(graphics, p.targetRect,
                p.targetBox != null && p.targetBox.isFocused());
        drawButton(p, graphics, p.applyRect, p.applyText, true, mouseX, mouseY, true);
        drawButton(p, graphics, p.outcomeRect, p.outcomeLabelText, true, mouseX, mouseY,
                false);
        drawButton(p, graphics, p.windowRect, p.windowLabelText, true, mouseX, mouseY,
                false);
        if (p.canExport) {
            drawButton(p, graphics, p.exportAuditRect,
                    p.exportArm == GovAuditPanel.ExportArm.AUDIT ? p.confirmExportText : p.exportText,
                    !GovAuditCaches.mutationPending(p), mouseX, mouseY, false);
        } else {
            GovText.clipped(graphics, p.font, p.exportLockedText,
                    p.exportAuditRect.x(), p.exportAuditRect.y() + 4,
                    p.exportAuditRect.w(), GovTheme.FAINT);
        }
    }

    static void drawList(final GovAuditPanel p, final GuiGraphics graphics, final int mouseX, final int mouseY) {
        if (p.listRect.w() <= 0 || p.listRect.h() <= 0) {
            return;
        }
        if (p.listState == GovAuditPanel.LoadState.ERROR && !p.listError.isEmpty()) {
            GovText.clipped(graphics, p.font, p.listError, p.countRect.x(),
                    p.countRect.y(), p.countRect.w(), GovTheme.DANGER);
        } else if (!p.status.isEmpty()) {
            GovText.clipped(graphics, p.font, p.status, p.countRect.x(),
                    p.countRect.y(), p.countRect.w(),
                    p.statusIsError ? GovTheme.DANGER : GovTheme.POSITIVE);
        } else if (!p.headerText.isEmpty()) {
            GovText.clipped(graphics, p.font, p.headerText, p.countRect.x(),
                    p.countRect.y(), p.countRect.w(), GovTheme.FAINT);
        }
        graphics.fill(p.listRect.x(), p.listRect.y(), p.listRect.right(),
                p.listRect.bottom(), GovTheme.FIELD);
        GovTheme.border(graphics, p.listRect, GovTheme.BORDER);
        if (p.listState == GovAuditPanel.LoadState.LOADING) {
            GovWidgets.emptyState(graphics, p.font, p.listInnerRect, p.loadingText,
                    null);
            return;
        }
        if (p.listState == GovAuditPanel.LoadState.ERROR) {
            GovWidgets.emptyState(graphics, p.font, p.listInnerRect, p.errorText,
                    p.listError);
            if (p.listRetryRect.w() > 0) {
                drawButton(p, graphics, p.listRetryRect, p.retryText, true, mouseX, mouseY,
                        false);
            }
            return;
        }
        if (p.rowViews.isEmpty()) {
            GovWidgets.emptyState(graphics, p.font, p.listInnerRect, p.emptyText,
                    null);
            return;
        }
        drawRows(p, graphics, mouseX, mouseY);
    }

    static void drawRows(final GovAuditPanel p, final GuiGraphics graphics, final int mouseX, final int mouseY) {
        if (p.listInnerRect.w() <= 0 || p.listInnerRect.h() <= 0) {
            return;
        }
        graphics.enableScissor(p.listInnerRect.x(), p.listInnerRect.y(),
                p.listInnerRect.right(), p.listInnerRect.bottom());
        final int textW = Math.max(1, p.listRect.w() - p.resultBadgeW - 20);
        int y = p.listRect.y() + 1 - p.listScroll;
        for (final GovAuditPanel.RowView view : p.rowViews) {
            if (y + GovMetrics.ROW_H >= p.listInnerRect.y()
                    && y >= p.listInnerRect.y()
                    && y + GovMetrics.ROW_H <= p.listInnerRect.bottom()) {
                final boolean selected = view.seq() == p.selectedSeq;
                final boolean hovered = GovTheme.inside(mouseX, mouseY,
                        p.listRect.x() + 2, y, p.listRect.w() - 6,
                        GovMetrics.ROW_H - 2);
                if (selected) {
                    graphics.fill(p.listRect.x() + 2, y, p.listRect.right() - 2,
                            y + GovMetrics.ROW_H - 2, GovTheme.SELECTED);
                    graphics.fill(p.listRect.x() + 2, y, p.listRect.x() + 5,
                            y + GovMetrics.ROW_H - 2, GovTheme.ACCENT);
                } else if (hovered) {
                    graphics.fill(p.listRect.x() + 2, y, p.listRect.right() - 2,
                            y + GovMetrics.ROW_H - 2, GovTheme.HOVER);
                }
                GovText.clipped(graphics, p.font, view.line1(), p.listRect.x() + 8,
                        y, textW, selected ? GovTheme.TEXT : GovTheme.BODY);
                GovText.clipped(graphics, p.font, view.line2(), p.listRect.x() + 8,
                        y + 9, textW, GovTheme.FAINT);
                final int badgeX = p.listRect.right() - 6 - p.resultBadgeW;
                graphics.fill(badgeX, y + 3, badgeX + p.resultBadgeW, y + 13,
                        GovTheme.SELECTED);
                GovTheme.border(graphics, badgeX, y + 3, p.resultBadgeW, 10,
                        view.denied() ? GovTheme.DANGER : GovTheme.POSITIVE);
                GovText.centered(graphics, p.font, view.result(), badgeX, y + 4,
                        p.resultBadgeW,
                        view.denied() ? GovTheme.DANGER : GovTheme.POSITIVE);
            }
            y += GovMetrics.ROW_H;
        }
        graphics.disableScissor();
        if (p.listScrollMax > 0) {
            GovTheme.scrollbar(graphics, p.listRect.right() - 4,
                    p.listRect.y() + 2, Math.max(1, p.listRect.h() - 4),
                    p.listScroll, p.listScrollMax);
        }
    }

    static void drawDetail(final GovAuditPanel p, final GuiGraphics graphics, final int mouseX, final int mouseY) {
        if (p.shortDetail) {
            if (p.detailSummaryRect.w() <= 0) {
                return;
            }
            final String text;
            if (p.selectedSeq <= 0L) {
                text = p.noSelectionText;
            } else if (p.detailState == GovAuditPanel.LoadState.LOADING) {
                text = p.loadingText;
            } else if (p.detailState == GovAuditPanel.LoadState.ERROR) {
                text = p.detailSummaryText;
            } else {
                text = p.detailSummaryText;
            }
            GovText.clipped(graphics, p.font, text, p.detailSummaryRect.x(),
                    p.detailSummaryRect.y(), p.detailSummaryRect.w(), GovTheme.MUTED);
            return;
        }
        if (p.detailRect.w() <= 0 || p.detailRect.h() <= 0) {
            return;
        }
        graphics.fill(p.detailRect.x(), p.detailRect.y(), p.detailRect.right(),
                p.detailRect.bottom(), GovTheme.FIELD);
        GovTheme.border(graphics, p.detailRect, GovTheme.BORDER);
        if (p.selectedSeq <= 0L || p.detailState == GovAuditPanel.LoadState.NONE) {
            GovWidgets.emptyState(graphics, p.font, p.detailInnerRect,
                    p.noSelectionText, null);
            return;
        }
        if (p.detailState == GovAuditPanel.LoadState.LOADING) {
            GovWidgets.emptyState(graphics, p.font, p.detailInnerRect,
                    p.loadingText, null);
            return;
        }
        if (p.detailState == GovAuditPanel.LoadState.ERROR) {
            GovText.clipped(graphics, p.font, p.detailErrorText,
                    p.detailRect.x() + 4, p.detailRect.y() + 3,
                    Math.max(0, p.detailRect.w() - 8), GovTheme.DANGER);
            if (!p.detailError.isEmpty()) {
                GovText.clipped(graphics, p.font, p.detailError,
                        p.detailRect.x() + 4, p.detailRect.y() + 13,
                        Math.max(0, p.detailRect.w() - 8), GovTheme.MUTED);
            }
            if (p.detailRetryRect.w() > 0) {
                drawButton(p, graphics, p.detailRetryRect, p.retryText, true, mouseX, mouseY,
                        false);
            }
            return;
        }
        if (p.detailHeadText.isEmpty()) {
            return;
        }
        GovText.clipped(graphics, p.font, p.detailHeadComponent,
                p.detailRect.x() + 4, p.detailRect.y() + 3,
                Math.max(0, p.detailRect.w() - 8), GovTheme.TEXT);
        GovWidgets.rule(graphics, p.detailRect.x() + 4, p.detailRect.y() + 12,
                Math.max(0, p.detailRect.w() - 8), GovTheme.BORDER_SOFT);
        if (p.detailInnerRect.w() <= 0 || p.detailInnerRect.h() <= 0) {
            return;
        }
        graphics.enableScissor(p.detailInnerRect.x(), p.detailInnerRect.y(),
                p.detailInnerRect.right(), p.detailInnerRect.bottom());
        int y = p.detailInnerRect.y() - p.detailScroll;
        for (final GovAuditPanel.DetailLine line : p.detailLines) {
            final int lineH = line.height(p.font);
            if (y + lineH >= p.detailInnerRect.y()
                    && y <= p.detailInnerRect.bottom()) {
                GovText.clipped(graphics, p.font, line.label(), p.detailInnerRect.x(), y,
                        p.detailLabelW, GovTheme.MUTED);
                final int valueX = p.detailInnerRect.x() + p.detailLabelW
                        + GovMetrics.GAP_XS;
                if (line.wrapped().isEmpty()) {
                    GovText.clipped(graphics, p.font, line.value(), valueX, y,
                            p.detailValueW, line.color());
                } else {
                    for (int index = 0; index < line.wrapped().size(); index++) {
                        final int lineY = y + index * p.font.lineHeight;
                        if (lineY + p.font.lineHeight <= p.detailInnerRect.bottom()) {
                            graphics.drawString(p.font, line.wrapped().get(index), valueX,
                                    lineY, line.color(), false);
                        }
                    }
                }
            }
            y += lineH;
        }
        graphics.disableScissor();
        if (p.detailScrollMax > 0) {
            GovTheme.scrollbar(graphics, p.detailRect.right() - 4,
                    p.detailInnerRect.y(), Math.max(1, p.detailInnerRect.h()),
                    p.detailScroll, p.detailScrollMax);
        }
    }

    static void drawBottom(final GovAuditPanel p, final GuiGraphics graphics, final int mouseX, final int mouseY) {
        drawButton(p, graphics, p.prevRect, p.prevText, p.page > 0, mouseX, mouseY, false);
        drawButton(p, graphics, p.nextRect, p.nextText, p.page + 1 < p.pages,
                mouseX, mouseY, false);
        GovText.clipped(graphics, p.font, p.pageText, p.pageRect.x() + 2,
                p.pageRect.y() + 4, Math.max(0, p.pageRect.w() - 4), GovTheme.MUTED);
        drawButton(p, graphics, p.refreshRect, refreshLabel(p), true, mouseX, mouseY, false);
    }

    static String refreshLabel(final GovAuditPanel p) {
        return p.listState == GovAuditPanel.LoadState.ERROR
                || (p.detailState == GovAuditPanel.LoadState.ERROR && p.selectedSeq > 0L)
                ? p.retryText : p.refreshText;
    }

    static void refreshOrRetry(final GovAuditPanel p) {
        if (p.listState == GovAuditPanel.LoadState.ERROR) {
            GovAuditRequests.requestList(p);
        } else if (p.detailState == GovAuditPanel.LoadState.ERROR && p.selectedSeq > 0L) {
            GovAuditRequests.requestDetail(p, p.selectedSeq);
        } else {
            GovAuditRequests.requestList(p);
        }
    }

    static void drawButton(final GovAuditPanel p, final GuiGraphics graphics, final GovRect rect, final String label,
            final boolean enabled, final int mouseX, final int mouseY, final boolean primary) {
        if (rect.w() <= 0 || rect.h() <= 0) {
            return;
        }
        GovWidgets.button(graphics, p.font, rect, label, enabled,
                enabled && rect.contains(mouseX, mouseY), primary);
    }
}
