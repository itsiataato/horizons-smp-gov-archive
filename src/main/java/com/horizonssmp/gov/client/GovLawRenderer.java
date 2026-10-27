package com.horizonssmp.gov.client;

import java.util.List;

import net.minecraft.client.gui.GuiGraphics;

final class GovLawRenderer {

    private GovLawRenderer() {
    }

    static void render(final GovLawPanel p, final GuiGraphics graphics, final int mouseX,
            final int mouseY) {
        if (!p.active || p.font == null) {
            return;
        }
        if (p.subtab == 1) {
            renderSearch(p, graphics, mouseX, mouseY);
        } else {
            renderChapter(p, graphics, mouseX, mouseY);
        }
    }

    static void renderChapter(final GovLawPanel p, final GuiGraphics graphics, final int mouseX,
            final int mouseY) {
        if (p.tocRight < p.rectRight) {
            graphics.fill(p.tocRight, p.rectTop, p.tocRight + 1, p.rectBottom,
                    GovTheme.BORDER_SOFT);
        }
        drawToc(p, graphics, mouseX, mouseY);
        drawChapterDocument(p, graphics);
        drawChapterFooter(p, graphics, mouseX, mouseY);
    }

    static void drawToc(final GovLawPanel p, final GuiGraphics graphics, final int mouseX,
            final int mouseY) {
        for (int index = 0; index < p.tocHits.length; index++) {
            final GovRect row = p.tocHits[index];
            if (row == null || row.w() <= 0 || row.h() <= 0) {
                continue;
            }
            final boolean selected = index == p.selectedChapter;
            final boolean hovered = row.contains(mouseX, mouseY);
            if (selected) {
                graphics.fill(row.x(), row.y(), row.right(), row.bottom(), GovTheme.SELECTED);
                graphics.fill(row.x(), row.y(), row.x() + 3, row.bottom(), GovTheme.ACCENT);
            } else if (hovered) {
                graphics.fill(row.x(), row.y(), row.right(), row.bottom(), GovTheme.HOVER);
            }
            if (p.tocLabels.length > index && row.h() >= 8) {
                GovText.clipped(graphics, p.font, p.tocLabels[index], row.x() + 7,
                        row.y() + (row.h() - 9) / 2, Math.max(0, row.w() - 10),
                        selected ? GovTheme.TEXT : GovTheme.MUTED);
            }
        }
    }

    static void drawChapterDocument(final GovLawPanel p, final GuiGraphics graphics) {
        final ChapterLayout cache = p.chapterCache;
        if (cache == null || cache.pageCount() <= 0 || p.docTextW <= 0) {
            return;
        }
        final int page = GovMetrics.clamp(p.pageIndex, 0, cache.pageCount() - 1);
        final int start = cache.pageStarts()[page];
        final int end = cache.pageEnd(page);
        final boolean scissored = p.docRight - p.docLeft > 2
                && p.docBottom - p.docTop > 2;
        if (scissored) {
            graphics.enableScissor(p.docLeft + 1, p.docTop, p.docRight - 1, p.docBottom);
        }
        try {
            int y = p.docTop;
            final List<DocLine> lines = cache.lines();
            for (int index = start; index < end; index++) {
                final DocLine line = lines.get(index);
                drawDocLine(p, graphics, line, y);
                y += line.height();
            }
        } finally {
            if (scissored) {
                graphics.disableScissor();
            }
        }
    }

    static void drawDocLine(final GovLawPanel p, final GuiGraphics graphics, final DocLine line,
            final int y) {
        switch (line.kind()) {
            case TEXT -> {
                if (line.text() != null) {
                    graphics.drawString(p.font, line.text(), p.docTextX, y, line.color(), false);
                }
            }
            case MONO -> {
                if (line.id() != null) {
                    if (line.id().length() * GovTheme.MONO_CELL <= p.docTextW) {
                        GovTheme.monoId(graphics, p.font, line.id(), p.docTextX, y, line.color());
                    } else {
                        GovText.clipped(graphics, p.font, line.id(), p.docTextX, y, p.docTextW,
                                line.color());
                    }
                }
            }
            case RULE -> {
                graphics.fill(p.docTextX, y, p.docTextX + p.docTextW, y + 1,
                        GovTheme.BORDER_SOFT);
            }
            case DOUBLE_RULE -> GovWidgets.doubleRule(graphics, p.docTextX, y, p.docTextW);
            case SPACER -> {
                // Height only.
            }
        }
    }

    static void drawChapterFooter(final GovLawPanel p, final GuiGraphics graphics, final int mouseX,
            final int mouseY) {
        GovWidgets.rule(graphics, p.docLeft, p.docFooterY - 1,
                Math.max(0, p.docRight - p.docLeft), GovTheme.BORDER_SOFT);
        final int pageCount = p.chapterCache == null ? 0 : p.chapterCache.pageCount();
        final boolean previousEnabled = p.pageIndex > 0;
        final boolean nextEnabled = pageCount > 0 && p.pageIndex < pageCount - 1;
        GovWidgets.button(graphics, p.font, p.chapterPrevRect, p.previousText,
                previousEnabled, previousEnabled && p.chapterPrevRect.contains(mouseX, mouseY),
                false);
        GovWidgets.button(graphics, p.font, p.chapterNextRect, p.nextText, nextEnabled,
                nextEnabled && p.chapterNextRect.contains(mouseX, mouseY), false);

        final int zoneX = p.chapterPrevRect.right() + 4;
        final int zoneWidth = Math.max(0, p.chapterNextRect.x() - 4 - zoneX);
        if (zoneWidth > 0 && !p.pageLabel.isEmpty()) {
            GovText.centered(graphics, p.font, p.pageLabel, zoneX, p.docFooterY + 3, zoneWidth,
                    GovTheme.MUTED);
        }
        if (!p.chapterOfLabel.isEmpty()) {
            GovText.clipped(graphics, p.font, p.chapterOfLabel, p.rectLeft + 2,
                    p.docFooterY + 3, Math.max(0, p.tocRight - p.rectLeft - 4), GovTheme.MUTED);
        }
    }

    static void renderSearch(final GovLawPanel p, final GuiGraphics graphics, final int mouseX,
            final int mouseY) {
        GovWidgets.fieldFrame(graphics, p.searchFrameRect,
                p.searchBox != null && p.searchBox.isFocused());

        if (!p.query.isEmpty()) {
            if (p.searchCache == null || p.searchCache.rows().isEmpty()) {
                GovText.centered(graphics, p.font, p.searchEmptyText, p.rectLeft + 4,
                        p.resultsTop + 8, Math.max(0, p.rectRight - p.rectLeft - 8),
                        GovTheme.MUTED);
            } else {
                drawSearchRows(p, graphics, mouseX, mouseY);
            }
        }
        drawSearchFooter(p, graphics, mouseX, mouseY);
    }

    static void drawSearchRows(final GovLawPanel p, final GuiGraphics graphics, final int mouseX,
            final int mouseY) {
        final SearchLayout cache = p.searchCache;
        if (cache == null || cache.pageCount() <= 0) {
            return;
        }
        final int start = cache.pageStarts()[p.resultPage];
        final int end = Math.min(cache.rows().size(), start + cache.perPage());
        final boolean scissored = p.resultsBottom > p.resultsTop
                && p.rectRight - p.rectLeft > 2;
        if (scissored) {
            graphics.enableScissor(p.rectLeft + 1, p.resultsTop, p.rectRight - 1,
                    p.resultsBottom);
        }
        try {
            final List<SearchRow> rows = cache.rows();
            final int idWidth = Math.min(44, Math.max(0, p.rectRight - p.rectLeft - 8));
            final int titleX = p.rectLeft + 4 + idWidth + 4;
            final int titleWidth = Math.max(0, p.rectRight - titleX - 4);
            for (int index = start; index < end; index++) {
                final int slot = index - start;
                final int y = p.resultsTop + slot * GovLawPanel.RESULT_ROW_H;
                final GovRect hit = slot < p.resultHits.length ? p.resultHits[slot]
                        : GovLawPanel.EMPTY;
                if (hit.contains(mouseX, mouseY)) {
                    graphics.fill(hit.x(), hit.y(), hit.right(), hit.bottom(), GovTheme.HOVER);
                }
                final SearchRow row = rows.get(index);
                if (row.articleId().length() * GovTheme.MONO_CELL <= idWidth) {
                    GovTheme.monoId(graphics, p.font, row.articleId(), p.rectLeft + 4, y + 3,
                            GovTheme.ACCENT);
                } else {
                    GovText.clipped(graphics, p.font, row.articleId(), p.rectLeft + 4, y + 3,
                            idWidth, GovTheme.ACCENT);
                }
                GovText.clipped(graphics, p.font, row.title(), titleX, y + 2, titleWidth,
                        GovTheme.TEXT);
                GovText.clipped(graphics, p.font, row.meta(), p.rectLeft + 4, y + 13,
                        Math.max(0, p.rectRight - p.rectLeft - 8), GovTheme.MUTED);
                graphics.fill(p.rectLeft + 4, y + GovLawPanel.RESULT_ROW_H - 1,
                        p.rectRight - 4, y + GovLawPanel.RESULT_ROW_H, GovTheme.BORDER_SOFT);
            }
        } finally {
            if (scissored) {
                graphics.disableScissor();
            }
        }
    }

    static void drawSearchFooter(final GovLawPanel p, final GuiGraphics graphics, final int mouseX,
            final int mouseY) {
        final int pageCount = p.searchCache == null ? 0 : p.searchCache.pageCount();
        if (pageCount <= 0) {
            return;
        }
        GovWidgets.rule(graphics, p.rectLeft, p.searchFooterY - 1,
                Math.max(0, p.rectRight - p.rectLeft), GovTheme.BORDER_SOFT);
        final boolean previousEnabled = p.resultPage > 0;
        final boolean nextEnabled = p.resultPage < pageCount - 1;
        GovWidgets.button(graphics, p.font, p.searchPrevRect, p.previousText, previousEnabled,
                previousEnabled && p.searchPrevRect.contains(mouseX, mouseY), false);
        GovWidgets.button(graphics, p.font, p.searchNextRect, p.nextText, nextEnabled,
                nextEnabled && p.searchNextRect.contains(mouseX, mouseY), false);
        final int zoneX = p.searchPrevRect.right() + 4;
        final int zoneWidth = Math.max(0, p.searchNextRect.x() - 4 - zoneX);
        if (zoneWidth > 0 && !p.resultsPageLabel.isEmpty()) {
            GovText.centered(graphics, p.font, p.resultsPageLabel, zoneX, p.searchFooterY + 3,
                    zoneWidth, GovTheme.MUTED);
        }
    }
}
