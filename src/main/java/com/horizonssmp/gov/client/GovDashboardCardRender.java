package com.horizonssmp.gov.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

final class GovDashboardCardRender {
    private GovDashboardCardRender() {}

     static void drawCard(final GovDashboardScreen s, final GuiGraphics graphics, final int mouseX, final int mouseY) {
        final GovRect card = s.cardRect;
        if (card.w() <= 0 || card.h() <= 0) {
            return;
        }
        final boolean compact = s.layout.breakpoint() == GovLayout.Breakpoint.COMPACT;
        final boolean restrictionsTab = s.subtabs[GovDashboardScreen.Tab.DASHBOARD.ordinal()] == 1;

        graphics.fill(card.x(), card.y(), card.right(), card.bottom(), GovTheme.FIELD);
        GovTheme.border(graphics, card, GovTheme.BORDER);

        final int titleY = card.y() + (compact ? 1 : 3);
        final int row2Y = card.y() + (compact ? 10 : 13);
        final int titleStart = card.x() + s.cardPad;
        final int formX = card.right() - s.cardPad - s.font().width(s.formText);
        final int titleMax = Math.max(0, formX - 4 - titleStart);
        int titleClip = titleMax;
        int restrictedX = -1;
        int restrictedWidth = 0;
        if (restrictionsTab && !s.restrictions.isEmpty()) {
            final int titleWidth = s.font().width(s.restrictionsTitleBold);
            final int candidateX = titleStart + titleWidth + 6;
            final int candidateWidth = titleMax - titleWidth - 6;
            if (candidateWidth >= s.font().width(s.restrictedText) + 2) {
                restrictedX = candidateX;
                restrictedWidth = candidateWidth;
                titleClip = Math.max(0, candidateX - 4 - titleStart);
            }
        }
        GovText.clipped(graphics, s.font(),
                restrictionsTab ? s.restrictionsTitleBold : s.recordTitleBold,
                titleStart, titleY, titleClip, GovTheme.TEXT);
        if (restrictedX >= 0) {
            GovText.clipped(graphics, s.font(), s.restrictedText, restrictedX, titleY,
                    restrictedWidth, GovTheme.FAINT);
        }
        GovText.clipped(graphics, s.font(), s.formText, formX, titleY,
                Math.max(0, card.right() - s.cardPad - formX), GovTheme.FAINT);

        if (!s.cardStatus.isEmpty()) {
            final Component stamp = statusStamp(s, s.cardStatus);
            if (stamp != null) {
                final int stampH = compact ? 10 : 12;
                final int stampW = Math.min(card.w() / 2, s.font().width(stamp) + 10);
                drawStamp(s, graphics, titleStart, row2Y, stampW, stampH, stamp);
            }
        }
        GovWidgets.button(graphics, s.font(), s.refreshRect, s.refreshText,
                s.cardState != GovDashboardScreen.CardState.LOADING,
                s.refreshRect.contains(mouseX, mouseY), false);
        graphics.fill(card.x() + 1, card.y() + s.cardHeaderH - 1,
                card.right() - 1, card.y() + s.cardHeaderH, GovTheme.BORDER_SOFT);

        switch (s.cardState) {
            case LOADING -> GovText.centered(graphics, s.font(), s.loadingText,
                    card.x() + s.cardPad, s.cardRowsTop + 6,
                    Math.max(0, card.w() - s.cardPad * 2), GovTheme.MUTED);
            case ERROR -> drawCardError(s, graphics, mouseX, mouseY);
            case READY -> {
                if (restrictionsTab) {
                    drawRestrictions(s, graphics);
                } else {
                    drawCardRows(s, graphics);
                }
            }
        }
    }
     static void drawCardRows(final GovDashboardScreen s, final GuiGraphics graphics) {
        final GovRect card = s.cardRect;
        final int labelX = card.x() + s.cardPad;
        final int labelColumn = GovMetrics.clamp(card.w() / 3, 54, 110);
        final int valueX = labelX + labelColumn;
        final int valueWidth = Math.max(0, card.right() - s.cardPad - valueX);
        final int labelWidth = Math.max(0, valueX - labelX - 4);
        final int stampHeight = Math.max(1, Math.min(11, s.cardRowPitch - 1));
        for (int index = 0; index < s.cardRows.size(); index++) {
            final GovDashboardScreen.CardRow row = s.cardRows.get(index);
            final int y = s.cardRowsTop + index * s.cardRowPitch;
            if (y + 9 > card.bottom()) {
                break;
            }
            GovText.clipped(graphics, s.font(), row.label(), labelX, y, labelWidth,
                    GovTheme.MUTED);
            if (row.stamp() != null && valueWidth > 0) {
                final int stampW = Math.min(valueWidth, s.font().width(row.stamp()) + 10);
                drawStamp(s, graphics, valueX, y, stampW, stampHeight, row.stamp());
                if (!row.value().isEmpty() && stampW + 4 < valueWidth) {
                    GovText.clipped(graphics, s.font(), row.value(), valueX + stampW + 4, y,
                            valueWidth - stampW - 4, GovTheme.BODY);
                }
            } else if (!row.value().isEmpty()) {
                GovText.clipped(graphics, s.font(), row.value(), valueX, y, valueWidth,
                        GovTheme.BODY);
            }
        }
    }
     static void drawRestrictions(final GovDashboardScreen s, final GuiGraphics graphics) {
        final GovRect card = s.cardRect;
        final int bodyTop = card.y() + s.cardHeaderH;
        final int bodyBottom = card.bottom() - 2;
        if (s.restrictions.isEmpty()) {
            final int titleY = bodyTop + 7;
            GovText.centered(graphics, s.font(), s.noRestrictions,
                    card.x() + s.cardPad, titleY,
                    Math.max(0, card.w() - s.cardPad * 2), GovTheme.BODY);
            int y = titleY + 14;
            for (final FormattedCharSequence line : s.receptionNoteLines) {
                if (y + 9 > bodyBottom) {
                    break;
                }
                graphics.drawString(s.font(), line, card.x() + s.cardPad, y,
                        GovTheme.FAINT, false);
                y += 9;
            }
            return;
        }
        final int count = s.restrictions.size();
        final int available = Math.max(0, bodyBottom - bodyTop - 2);
        final int pitch = Math.min(24, Math.max(1, available / Math.max(1, count)));
        final int valueX = card.x() + s.cardPad + GovMetrics.clamp(card.w() / 4, 46, 90);
        final int valueWidth = Math.max(0, card.right() - s.cardPad - valueX);
        for (int index = 0; index < count; index++) {
            final int y = bodyTop + 2 + index * pitch;
            if (y + 9 > bodyBottom) {
                break;
            }
            final GovDashboardScreen.RestrictionRow row = s.restrictions.get(index);
            final int stampW = Math.max(1, Math.min(valueX - card.x() - s.cardPad - 4,
                    s.font().width(row.stamp()) + 10));
            drawStamp(s, graphics, card.x() + s.cardPad, y, stampW,
                    Math.max(1, Math.min(11, pitch - 2)), row.stamp());
            GovText.clipped(graphics, s.font(), row.detail(), valueX, y, valueWidth,
                    GovTheme.BODY);
        }
    }
     static void drawCardError(final GovDashboardScreen s, final GuiGraphics graphics, final int mouseX, final int mouseY) {
        final GovRect card = s.cardRect;
        final int x = card.x() + s.cardPad;
        final int width = Math.max(0, card.w() - s.cardPad * 2);
        final int y = s.cardRowsTop + 2;
        GovText.clipped(graphics, s.font(), s.errorTitleBold, x, y, width, GovTheme.TEXT);
        int lineY = y + 11;
        final int limit = s.retryRect.y() - 2;
        for (final FormattedCharSequence line : s.cardErrorLines) {
            if (lineY + 9 > limit || lineY + 9 > card.bottom()) {
                break;
            }
            graphics.drawString(s.font(), line, x, lineY, GovTheme.MUTED, false);
            lineY += 9;
        }
        GovWidgets.button(graphics, s.font(), s.retryRect, s.retryText, true,
                s.retryRect.contains(mouseX, mouseY), false);
    }
     static void drawStamp(final GovDashboardScreen s, final GuiGraphics graphics, final int x, final int y, final int width,
            final int height, final Component text) {
        if (width <= 0 || height <= 0 || text == null) {
            return;
        }
        graphics.fill(x, y, x + width, y + height, GovTheme.PANEL);
        GovTheme.border(graphics, x, y, width, height, GovTheme.STAMP);
        if (height >= 9 && width > 6) {
            final int inner = width - 6;
            final int textWidth = s.font().width(text);
            if (textWidth <= inner) {
                graphics.drawString(s.font(), text, x + (width - textWidth) / 2,
                        y + (height - 9) / 2, GovTheme.STAMP, false);
            } else {
                GovText.clipped(graphics, s.font(), text, x + 3, y + (height - 9) / 2,
                        inner, GovTheme.STAMP);
            }
        }
    }
     static Component statusStamp(final GovDashboardScreen s, final String status) {
        if (s.clearText.equals(status)) {
            return s.clearStamp;
        }
        if (s.flaggedText.equals(status)) {
            return s.flaggedStamp;
        }
        if (s.heldText.equals(status)) {
            return s.heldStamp;
        }
        if (s.dueText.equals(status)) {
            return s.dueStamp;
        }
        return null;
    }
}
