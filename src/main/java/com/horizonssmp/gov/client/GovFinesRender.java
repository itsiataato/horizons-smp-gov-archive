package com.horizonssmp.gov.client;

import com.horizonssmp.gov.client.GovFinesTypes.*;
import static com.horizonssmp.gov.client.GovFinesTypes.*;

import com.horizonssmp.gov.common.FineKeys;

import net.minecraft.client.gui.GuiGraphics;

final class GovFinesRender {

    // -------------------------------------------------------------------- render

    /** Draws the active mode. Every string below comes from a cache built off the render path. */
    static void render(final GovFinesPanel p, final GuiGraphics graphics, final int mouseX, final int mouseY) {
        if (p.area.w() <= 0 || p.area.h() <= 0) {
            return;
        }
        if (p.showTitle && p.titleRect.w() > 0) {
            GovText.sectionTitle(graphics, p.font, p.titleView, p.titleRect);
        }
        switch (p.subtab) {
            case TAB_REGISTER -> drawRegister(p, graphics, mouseX, mouseY);
            case TAB_ISSUE -> drawIssue(p, graphics, mouseX, mouseY);
            case TAB_COLLECT -> drawCollect(p, graphics, mouseX, mouseY);
            case TAB_WAIVE -> drawWaive(p, graphics, mouseX, mouseY);
            default -> {
                // No other mode exists.
            }
        }
    }

    static void drawRegister(final GovFinesPanel p, final GuiGraphics graphics, final int mouseX, final int mouseY) {
        drawList(p, graphics, mouseX, mouseY);
        if (p.detailRect.w() > 0) {
            GovFinesRenderDetails.drawDetail(p, graphics, mouseX, mouseY);
        } else {
            drawSummary(p, graphics);
        }
        drawBottomBar(p, graphics);
        drawButtons(p, graphics, mouseX, mouseY);
    }

    static void drawList(final GovFinesPanel p, final GuiGraphics graphics, final int mouseX, final int mouseY) {
        if (p.listRect.w() <= 0 || p.listRect.h() <= 0) {
            return;
        }
        graphics.fill(p.listRect.x(), p.listRect.y(), p.listRect.right(),
                p.listRect.bottom(), GovTheme.FIELD);
        GovTheme.border(graphics, p.listRect, GovTheme.BORDER);
        GovFinesRenderDetails.drawTableHeader(p, graphics);

        if (p.listState == LoadState.LOADING) {
            drawText(p, graphics, GovFinesLabels.label(p, "gui.gov.fines.loading"), p.listRect.x() + 6,
                    p.listRect.y() + 6, p.listRect.w() - 12, GovTheme.MUTED);
            return;
        }
        if (p.listState == LoadState.ERROR) {
            drawText(p, graphics, GovFinesLabels.label(p, "gui.gov.fines.error"), p.listRect.x() + 6,
                    p.listRect.y() + 6, p.listRect.w() - 12, GovTheme.DANGER);
            drawText(p, graphics, p.listErrorView, p.listRect.x() + 6,
                    p.listRect.y() + 18, p.listRect.w() - 12, GovTheme.MUTED);
            return;
        }
        if (p.rows.isEmpty()) {
            final String empty = p.subtab == TAB_COLLECT || p.subtab == TAB_WAIVE
                    ? GovFinesLabels.label(p, "gui.gov.fines.no_unpaid")
                    : GovFinesLabels.label(p, "gui.gov.fines.empty." + p.registerFilter);
            drawText(p, graphics, empty, p.listRect.x() + 6, p.listRect.y() + 6,
                    p.listRect.w() - 12, GovTheme.MUTED);
            if (!p.receptionConfigured && p.subtab == TAB_REGISTER) {
                drawText(p, graphics, GovFinesLabels.label(p, "gui.gov.fines.reception_missing"),
                        p.listRect.x() + 6, p.listRect.y() + 18,
                        p.listRect.w() - 12, GovTheme.WARN);
            }
            return;
        }

        graphics.enableScissor(p.listRect.x() + 1, p.listRect.y() + 1,
                p.listRect.right() - 1, p.listRect.bottom() - 1);
        try {
            int y = p.listRect.y() + 1 - p.listScroll;
            for (int i = 0; i < p.rowViews.size(); i++) {
                final FineRowView view = p.rowViews.get(i);
                if (GovFit.fitsWithin(y, p.rowH, p.listRect.y() + 1,
                        p.listRect.bottom() - 1)) {
                    final boolean selected = view.id().equals(p.selectedId);
                    final boolean hovered = p.listRect.contains(mouseX, mouseY)
                            && mouseX >= p.listRect.x() + 1
                            && mouseX < p.listRect.right() - 1
                            && mouseY >= y && mouseY < y + p.rowH;
                    if (selected) {
                        graphics.fill(p.listRect.x() + 1, y, p.listRect.right() - 1,
                                y + p.rowH, GovTheme.SELECTED);
                        graphics.fill(p.listRect.x() + 1, y, p.listRect.x() + 3,
                                y + p.rowH, GovTheme.ACCENT);
                    } else if (hovered) {
                        graphics.fill(p.listRect.x() + 1, y, p.listRect.right() - 1,
                                y + p.rowH, GovTheme.HOVER);
                    }
                    final int textY = y + (p.rowH - ROW_TEXT) / 2;
                    final int color = selected ? GovTheme.TEXT : GovTheme.BODY;
                    graphics.drawString(p.font, view.citizen(), p.columnX[0], textY,
                            color, false);
                    graphics.drawString(p.font, view.article(), p.columnX[1], textY,
                            color, false);
                    graphics.drawString(p.font, view.amount(), p.columnX[2], textY,
                            color, false);
                    graphics.drawString(p.font, view.stateBold(), p.columnX[3], textY,
                            GovTheme.TEXT, false);
                    graphics.drawString(p.font, view.age(), p.columnX[4], textY,
                            GovTheme.MUTED, false);
                    if (i + 1 < p.rowViews.size()) {
                        graphics.fill(p.listRect.x() + 4, y + p.rowH - 1,
                                p.listRect.right() - 4, y + p.rowH, GovTheme.BORDER_SOFT);
                    }
                }
                y += p.rowH;
            }
        } finally {
            graphics.disableScissor();
        }

        if (p.listScrollMax > 0) {
            final GovRect track = GovFinesGeometry.rect(p, p.listRect.right() - 4, p.listRect.y() + 2,
                    2, Math.max(0, p.listRect.h() - 4));
            GovWidgets.scrollbar(graphics, track, p.listScroll, p.listScrollMax);
        }
    }

    static void drawSummary(final GovFinesPanel p, final GuiGraphics graphics) {
        if (p.summaryRect.w() <= 0) {
            return;
        }
        final String text = p.status.isEmpty() ? p.summaryView : p.statusView;
        final int color = p.status.isEmpty() ? GovTheme.MUTED
                : p.statusIsError ? GovTheme.DANGER : GovTheme.POSITIVE;
        drawText(p, graphics, text, p.summaryRect.x(), p.summaryRect.y(),
                p.summaryRect.w(), color);
    }

    static void drawBottomBar(final GovFinesPanel p, final GuiGraphics graphics) {
        if (p.bottomBar.w() <= 0) {
            return;
        }
        graphics.fill(p.bottomBar.x(), p.bottomBar.y(), p.bottomBar.right(),
                p.bottomBar.y() + 1, GovTheme.BORDER_SOFT);
        final boolean showStatus = !p.status.isEmpty()
                && p.subtab == TAB_REGISTER
                && p.detailRect.w() > 0 && p.pageTextRect.w() > 60;
        final String barText = showStatus ? p.statusView : p.pageView;
        final int barColor = showStatus
                ? (p.statusIsError ? GovTheme.DANGER : GovTheme.POSITIVE)
                : GovTheme.MUTED;
        GovText.clipped(graphics, p.font, barText, p.pageTextRect.x(),
                p.pageTextRect.y() + (p.pageTextRect.h() - ROW_TEXT) / 2,
                p.pageTextRect.w(), barColor);
    }

    static void drawIssue(final GovFinesPanel p, final GuiGraphics graphics, final int mouseX, final int mouseY) {
        GovFinesRenderDetails.drawFieldRows(p, graphics);
        drawText(p, graphics, GovFinesLabels.label(p, "gui.gov.fines.field.citizen"), p.citizenLabelRect.x(),
                p.citizenLabelRect.y() + (p.citizenLabelRect.h() - ROW_TEXT) / 2,
                p.citizenLabelRect.w(), GovTheme.MUTED);
        graphics.drawString(p.font, p.issueCitizenView, p.citizenValueRect.x(),
                p.citizenValueRect.y() + (p.citizenValueRect.h() - ROW_TEXT) / 2,
                p.issueTarget.isBlank() ? GovTheme.WARN : GovTheme.TEXT, false);
        drawText(p, graphics, GovFinesLabels.label(p, "gui.gov.fines.field.band"), p.bandLabelRect.x(),
                p.bandLabelRect.y() + (p.bandLabelRect.h() - ROW_TEXT) / 2,
                p.bandLabelRect.w(), GovTheme.MUTED);
        if (p.status.isEmpty()) {
            drawText(p, graphics, p.issueAmountView, p.issueAmountRect.x(),
                    p.issueAmountRect.y() + (p.issueAmountRect.h() - ROW_TEXT) / 2,
                    p.issueAmountRect.w(), GovTheme.MUTED);
        } else {
            graphics.drawString(p.font, p.statusView, p.issueAmountRect.x(),
                    p.issueAmountRect.y() + (p.issueAmountRect.h() - ROW_TEXT) / 2,
                    p.statusIsError ? GovTheme.DANGER : GovTheme.POSITIVE, false);
        }
        if (p.issueHintRect.w() > 0 && p.issueHintRect.h() > 0
                && p.issueHintRect.y() + ROW_TEXT <= p.issueActionRect.y() - 2) {
            graphics.drawString(p.font, p.issueHintView, p.issueHintRect.x(),
                    p.issueHintRect.y(), GovTheme.FAINT, false);
        }
        drawButtons(p, graphics, mouseX, mouseY);
    }

    static void drawCollect(final GovFinesPanel p, final GuiGraphics graphics, final int mouseX, final int mouseY) {
        if (p.detailRect.w() > 0) {
            drawList(p, graphics, mouseX, mouseY);
            drawBottomBar(p, graphics);
        }
        graphics.drawString(p.font, GovFinesLabels.label(p, "gui.gov.fines.amount_due"),
                p.collectSummaryRect.x(), p.collectSummaryRect.y(), GovTheme.MUTED, false);
        graphics.drawString(p.font, p.collectTargetView, p.collectTargetRect.x(),
                p.collectTargetRect.y() + (p.collectTargetRect.h() - ROW_TEXT) / 2,
                p.selectedId.isEmpty() ? GovTheme.WARN : GovTheme.TEXT, false);
        graphics.drawString(p.font, p.collectAmountView, p.collectAmountRect.x(),
                p.collectAmountRect.y(), GovTheme.TEXT, false);
        if (!p.status.isEmpty()) {
            graphics.drawString(p.font, p.statusView, p.collectStatusRect.x(),
                    p.collectStatusRect.y(),
                    p.statusIsError ? GovTheme.DANGER : GovTheme.POSITIVE, false);
        } else {
            graphics.drawString(p.font, p.collectRuleView, p.collectStatusRect.x(),
                    p.collectStatusRect.y(), GovTheme.FAINT, false);
        }
        drawButtons(p, graphics, mouseX, mouseY);
    }

    static void drawWaive(final GovFinesPanel p, final GuiGraphics graphics, final int mouseX, final int mouseY) {
        if (p.detailRect.w() > 0) {
            drawList(p, graphics, mouseX, mouseY);
            drawBottomBar(p, graphics);
        }
        graphics.drawString(p.font, p.waiveTargetView, p.waiveSummaryRect.x(),
                p.waiveSummaryRect.y(), p.selectedId.isEmpty()
                        ? GovTheme.WARN : GovTheme.TEXT, false);
        GovFinesRenderDetails.drawFieldRows(p, graphics);
        graphics.drawString(p.font, p.waiveRuleView, p.waiveRuleRect.x(),
                p.waiveRuleRect.y(), GovTheme.FAINT, false);
        if (!p.status.isEmpty()) {
            graphics.drawString(p.font, p.statusView, p.waiveStatusRect.x(),
                    p.waiveStatusRect.y() + (p.waiveStatusRect.h() - ROW_TEXT) / 2,
                    p.statusIsError ? GovTheme.DANGER : GovTheme.POSITIVE, false);
        }
        drawButtons(p, graphics, mouseX, mouseY);
    }

    static void drawButtons(final GovFinesPanel p, final GuiGraphics graphics, final int mouseX, final int mouseY) {
        final long now = System.currentTimeMillis();
        for (int i = 0; i < p.buttons.size(); i++) {
            final Button button = p.buttons.get(i);
            final boolean armed = button.confirm() != null
                    && GovFinesSupport.isArmedVisual(p, button.click(), now);
            final String text = armed ? button.confirm() : button.label();
            final boolean hovered = button.enabled() && button.rect().contains(mouseX, mouseY);
            GovWidgets.button(graphics, p.font, button.rect(), text, button.enabled(),
                    hovered, button.primary() || armed);
        }
    }

    static void drawText(final GovFinesPanel p, final GuiGraphics graphics, final String text, final int x, final int y,
            final int width, final int color) {
        if (text == null || text.isEmpty() || width <= 0) {
            return;
        }
        GovText.clipped(graphics, p.font, text, x, y, width, color);
    }


}
