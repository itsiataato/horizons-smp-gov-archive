package com.horizonssmp.gov.client;

import com.horizonssmp.gov.client.GovFinesTypes.*;
import static com.horizonssmp.gov.client.GovFinesTypes.*;

import com.horizonssmp.gov.common.FineKeys;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

final class GovFinesRenderDetails {

    static void drawTableHeader(final GovFinesPanel p, final GuiGraphics graphics) {
        if (p.tableHeader.w() <= 0 || p.tableHeader.h() <= 0) {
            return;
        }
        graphics.fill(p.tableHeader.x(), p.tableHeader.y(), p.tableHeader.right(),
                p.tableHeader.bottom(), GovTheme.PANEL_ALT);
        graphics.fill(p.tableHeader.x(), p.tableHeader.bottom() - 1,
                p.tableHeader.right(), p.tableHeader.bottom(), GovTheme.BORDER);
        final int textY = p.tableHeader.y()
                + Math.max(0, (p.tableHeader.h() - ROW_TEXT) / 2);
        for (int i = 0; i < 5; i++) {
            if (p.columnW[i] > 0) {
                GovText.clipped(graphics, p.font, p.columnHeaders[i], p.columnX[i],
                        textY, p.columnW[i], GovTheme.MUTED);
            }
        }
    }

    static void drawDetail(final GovFinesPanel p, final GuiGraphics graphics, final int mouseX, final int mouseY) {
        if (p.detailRect.w() <= 0 || p.detailRect.h() <= 0) {
            return;
        }
        graphics.fill(p.detailRect.x(), p.detailRect.y(), p.detailRect.right(),
                p.detailRect.bottom(), GovTheme.FIELD);
        GovTheme.border(graphics, p.detailRect, GovTheme.BORDER);

        if (p.selectedId.isEmpty() || p.detailState == LoadState.NONE) {
            GovFinesRender.drawText(p, graphics, GovFinesLabels.label(p, "gui.gov.fines.no_selection"), p.detailRect.x() + 6,
                    p.detailRect.y() + 6, p.detailRect.w() - 12, GovTheme.MUTED);
            return;
        }
        if (p.detailState == LoadState.LOADING) {
            GovFinesRender.drawText(p, graphics, GovFinesLabels.label(p, "gui.gov.fines.loading"), p.detailRect.x() + 6,
                    p.detailRect.y() + 6, p.detailRect.w() - 12, GovTheme.MUTED);
            return;
        }
        if (p.detailState == LoadState.ERROR) {
            GovFinesRender.drawText(p, graphics, GovFinesLabels.label(p, "gui.gov.fines.detail_error"), p.detailRect.x() + 6,
                    p.detailRect.y() + 6, p.detailRect.w() - 12, GovTheme.DANGER);
            GovFinesRender.drawText(p, graphics, p.detailErrorView, p.detailRect.x() + 6,
                    p.detailRect.y() + 18, p.detailRect.w() - 12, GovTheme.MUTED);
            return;
        }

        if (p.detailTitleBold != null) {
            GovText.clipped(graphics, p.font, p.detailTitleBold,
                    p.detailRect.x() + 6, p.detailRect.y() + 4,
                    p.detailTitleW, GovTheme.TEXT);
        }
        if (p.detailStateView != null && !p.detailStateView.isEmpty()) {
            graphics.drawString(p.font, p.detailStateView, p.detailStateX,
                    p.detailRect.y() + 4, GovTheme.TEXT, false);
        }
        graphics.fill(p.detailRect.x() + 4, p.detailRect.y() + 15,
                p.detailRect.right() - 4, p.detailRect.y() + 16, GovTheme.BORDER_SOFT);

        final int bodyTop = p.detailRect.y() + 18;
        final int bodyBottom = p.detailRect.bottom() - 2;
        if (bodyBottom <= bodyTop) {
            return;
        }
        final int labelW = GovMetrics.span(p.detailRect.w(), p.detailRect.w() / 3, 40, 80);
        final int valueX = p.detailRect.x() + 6 + labelW + 3;
        graphics.enableScissor(p.detailRect.x() + 1, bodyTop,
                p.detailRect.right() - 1, bodyBottom);
        try {
            int y = bodyTop - p.detailScroll;
            for (int i = 0; i < p.detailLines.size(); i++) {
                final DetailLine line = p.detailLines.get(i);
                if (y + DETAIL_LINE > bodyTop && y < bodyBottom) {
                    if (line.wrapped() != null) {
                        graphics.drawString(p.font, line.wrapped(),
                                p.detailRect.x() + 8, y, line.color(), false);
                    } else {
                        if (line.label() != null && !line.label().isEmpty()) {
                            graphics.drawString(p.font, line.label(),
                                    p.detailRect.x() + 6, y, GovTheme.MUTED, false);
                        }
                        if (line.value() != null && !line.value().isEmpty()) {
                            graphics.drawString(p.font, line.value(), valueX, y,
                                    line.color(), false);
                        }
                    }
                }
                y += DETAIL_LINE;
            }
        } finally {
            graphics.disableScissor();
        }

        if (p.detailScrollMax > 0) {
            final GovRect track = GovFinesGeometry.rect(p, p.detailRect.right() - 4, bodyTop, 2,
                    Math.max(0, bodyBottom - bodyTop));
            GovWidgets.scrollbar(graphics, track, p.detailScroll, p.detailScrollMax);
        }
    }

    static void drawFieldRows(final GovFinesPanel p, final GuiGraphics graphics) {
        for (int i = 0; i < p.fieldRows.size(); i++) {
            final FieldRow row = p.fieldRows.get(i);
            if (row.tab() != p.subtab || row.box() == null || !row.box().visible) {
                continue;
            }
            graphics.drawString(p.font, row.label(), row.labelRect().x(),
                    row.labelRect().y() + (row.labelRect().h() - ROW_TEXT) / 2,
                    GovTheme.MUTED, false);
            GovWidgets.fieldFrame(graphics, row.fieldRect(), row.box().isFocused());
        }
    }


}
