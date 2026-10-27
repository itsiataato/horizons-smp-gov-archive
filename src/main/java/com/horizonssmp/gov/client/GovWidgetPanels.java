package com.horizonssmp.gov.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public final class GovWidgetPanels {

    private GovWidgetPanels() {
    }

    /**
     * Clips text until its bold form fits {@code innerW}. Bold renders wider than the
     * plain width the clip measures, so callers that draw the title bold must use this
     * instead of clipping directly, or the title bleeds past its box.
     */
    public static String fitBold(final Font font, final String text, final int innerW) {
        if (font == null || text == null || text.isEmpty() || innerW <= 0) {
            return "";
        }
        int fit = innerW;
        String visible = GovText.clip(font, text, fit);
        int boldW = font.width(GovText.boldCached(visible));
        while (boldW > innerW && fit > 0) {
            fit = GovFit.shrinkStep(fit, boldW, innerW);
            visible = GovText.clip(font, text, fit);
            boldW = font.width(GovText.boldCached(visible));
        }
        return visible;
    }

    public static void tableHeader(
            final GuiGraphics graphics, final Font font, final GovRect rect,
            final String[] columns) {
        if (graphics == null || !GovWidgets.hasArea(rect)) {
            return;
        }
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), GovTheme.PANEL_ALT);
        if (rect.h() > 0) {
            graphics.fill(rect.x(), rect.bottom() - 1, rect.right(), rect.bottom(),
                    GovTheme.BORDER);
        }
        if (font == null || columns == null || columns.length == 0
                || rect.h() < font.lineHeight) {
            return;
        }
        final int innerX = rect.x() + GovMetrics.GAP_S;
        final int innerW = rect.w() - GovMetrics.GAP_S * 2;
        if (innerW <= 0) {
            return;
        }
        final int base = innerW / columns.length;
        final int remainder = innerW % columns.length;
        final int textY = rect.y() + Math.max(0, (rect.h() - font.lineHeight) / 2);
        int columnX = innerX;
        for (int index = 0; index < columns.length; index++) {
            final int columnW = base + (index == columns.length - 1 ? remainder : 0);
            if (columnW > 0 && columns[index] != null) {
                GovText.clipped(graphics, font, GovText.boldCached(columns[index]), columnX,
                        textY, columnW, GovTheme.MUTED);
            }
            columnX += columnW;
        }
    }

    public static void emptyState(
            final GuiGraphics graphics, final Font font, final GovRect rect, final String title,
            final String detail) {
        if (graphics == null || font == null || !GovWidgets.hasArea(rect)) {
            return;
        }
        final int innerW = rect.w() - GovMetrics.GAP_S * 2;
        if (innerW <= 0) {
            return;
        }
        final String titleText = GovText.sanitize(title);
        final String detailText = GovText.sanitize(detail);
        final boolean hasTitle = !titleText.isEmpty();
        final boolean hasDetail = !detailText.isEmpty();
        final int contentH = (hasTitle ? font.lineHeight : 0)
                + (hasDetail ? (hasTitle ? GovMetrics.GAP_XS : 0) + font.lineHeight : 0);
        int textY = rect.y() + Math.max(0, (rect.h() - contentH) / 2);
        if (hasTitle && textY + font.lineHeight <= rect.bottom()) {
            final String visible = fitBold(font, titleText, innerW);
            graphics.drawString(font, GovText.boldCached(visible),
                    rect.x() + (rect.w() - font.width(GovText.boldCached(visible))) / 2, textY,
                    GovTheme.BODY, false);
            textY += font.lineHeight + (hasDetail ? GovMetrics.GAP_XS : 0);
        }
        if (hasDetail && textY + font.lineHeight <= rect.bottom()) {
            final String visible = GovText.clip(font, detailText, innerW);
            graphics.drawString(font, visible,
                    rect.x() + (rect.w() - font.width(visible)) / 2, textY,
                    GovTheme.FAINT, false);
        }
    }

    public static void scrollbar(
            final GuiGraphics graphics, final GovRect rect, final int scroll,
            final int maxScroll) {
        if (rect == null) {
            return;
        }
        scrollbar(graphics, rect.x(), rect.y(), rect.w(), rect.h(), scroll, maxScroll);
    }

    public static void scrollbar(
            final GuiGraphics graphics, final int x, final int y, final int width,
            final int height, final int scroll, final int maxScroll) {
        if (graphics == null || width <= 0 || height <= 0 || maxScroll <= 0) {
            return;
        }
        graphics.fill(x, y, x + 1, y + height, GovTheme.BORDER_SOFT);
        int thumbH = Math.max(GovMetrics.SCROLLBAR_MIN_THUMB,
                (int) ((long) height * height / (height + (long) maxScroll)));
        thumbH = Math.min(thumbH, height);
        final int clampedScroll = Math.max(0, Math.min(scroll, maxScroll));
        final int thumbY = y + (int) ((long) (height - thumbH) * clampedScroll / maxScroll);
        graphics.fill(x, thumbY, x + Math.min(2, width), thumbY + thumbH,
                GovTheme.SCROLLBAR);
    }

    public static void tooltip(
            final GuiGraphics graphics, final Font font, final int x, final int y,
            final String title, final String detail) {
        tooltip(graphics, font, x, y, title, detail, 0, 0);
    }

    public static void tooltip(
            final GuiGraphics graphics, final Font font, final int x, final int y,
            final String title, final String detail, final int screenW, final int screenH) {
        if (graphics == null || font == null) {
            return;
        }
        final String titleText = GovText.sanitize(title);
        final String detailText = GovText.sanitize(detail);
        final boolean hasTitle = !titleText.isEmpty();
        final boolean hasDetail = !detailText.isEmpty();
        final int pad = GovMetrics.GAP_XS;
        final int titleH = hasTitle ? font.lineHeight : 0;
        final int detailH = hasDetail
                ? (hasTitle ? GovMetrics.GAP_XS : 0) + font.lineHeight : 0;
        int boxW = Math.max(hasTitle ? font.width(titleText) : 0,
                hasDetail ? font.width(detailText) : 0) + pad * 2;
        int boxH = pad * 2 + titleH + detailH;
        if (screenW > 0) {
            boxW = Math.min(boxW, screenW);
        }
        if (screenH > 0) {
            boxH = Math.min(boxH, screenH);
        }
        final int boxX = screenW > 0
                ? GovMetrics.clamp(x, 0, Math.max(0, screenW - boxW)) : x;
        final int boxY = screenH > 0
                ? GovMetrics.clamp(y, 0, Math.max(0, screenH - boxH)) : y;
        final int innerW = boxW - pad * 2;
        if (innerW <= 0) {
            return;
        }
        graphics.fill(boxX, boxY, boxX + boxW, boxY + boxH, GovTheme.PANEL);
        GovTheme.border(graphics, boxX, boxY, boxW, boxH, GovTheme.BORDER);
        int textY = boxY + pad;
        if (hasTitle && textY + font.lineHeight <= boxY + boxH) {
            GovText.clipped(graphics, font, titleText, boxX + pad, textY, innerW, GovTheme.TEXT);
            textY += font.lineHeight + (hasDetail ? GovMetrics.GAP_XS : 0);
        }
        if (hasDetail && textY + font.lineHeight <= boxY + boxH) {
            GovText.clipped(graphics, font, detailText, boxX + pad, textY, innerW, GovTheme.MUTED);
        }
    }
}
