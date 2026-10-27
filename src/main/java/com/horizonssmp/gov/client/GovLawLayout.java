package com.horizonssmp.gov.client;

import net.minecraft.client.gui.Font;

final class GovLawLayout {

    private GovLawLayout() {
    }

    static void layout(final GovLawPanel p, final int left, final int top, final int right,
            final int bottom, final Font font) {
        p.font = font;
        p.rectLeft = left;
        p.rectTop = top;
        p.rectRight = Math.max(left, right);
        p.rectBottom = Math.max(top, bottom);
        final int width = p.rectRight - p.rectLeft;
        final int height = p.rectBottom - p.rectTop;
        p.panelBounds = new GovRect(left, top, width, height);

        int tocWidth = GovMetrics.clamp(width * 34 / 100, 76, 150);
        if (width < 200) {
            tocWidth = Math.min(tocWidth, width / 2);
        }
        tocWidth = Math.min(tocWidth, width);
        p.tocRight = p.rectLeft + tocWidth;
        p.docLeft = p.tocRight + (p.tocRight < p.rectRight ? 1 : 0);
        p.docRight = p.rectRight;
        p.docTop = p.rectTop + 2;
        p.docFooterY = Math.max(p.rectTop, p.rectBottom - GovLawPanel.TOC_FOOTER_H);
        p.docBottom = Math.max(p.docTop, p.docFooterY - 2);
        p.docTextX = p.docLeft + 6;
        p.docTextW = Math.max(0, p.docRight - p.docTextX - 6);

        p.tocTop = p.rectTop + 1;
        p.tocBottom = Math.max(p.tocTop, p.docFooterY - 1);
        final int chapterCount = p.law().chapters().size();
        final int tocArea = Math.max(0, p.tocBottom - p.tocTop);
        p.tocPitch = chapterCount > 0
                ? GovMetrics.clamp(tocArea / chapterCount, 8, 13) : 8;
        if (p.tocPitch <= 0) {
            p.tocPitch = 1;
        }
        p.tocHits = new GovRect[chapterCount];
        for (int index = 0; index < chapterCount; index++) {
            final int y = p.tocTop + index * p.tocPitch;
            final int rowHeight = Math.min(p.tocPitch, Math.max(0, p.tocBottom - y));
            p.tocHits[index] = new GovRect(p.rectLeft, y, tocWidth, rowHeight)
                    .clamp(p.panelBounds);
        }

        p.searchX = p.rectLeft + 2;
        p.searchY = p.rectTop + 2;
        p.searchW = Math.max(0, width - 4);
        p.searchH = Math.min(GovLawPanel.SEARCH_H, Math.max(0, height));
        p.searchFrameRect = new GovRect(p.searchX, p.searchY, p.searchW, p.searchH)
                .clamp(p.panelBounds);
        p.resultsTop = p.searchY + p.searchH + 4;
        p.searchFooterY = p.docFooterY;
        p.resultsBottom = Math.max(p.resultsTop, p.searchFooterY - 2);

        final int docWidth = Math.max(0, p.docRight - p.docLeft);
        int chapterButtonW = GovMetrics.clamp(docWidth / 5, 34, 56);
        chapterButtonW = Math.min(chapterButtonW, Math.max(0, docWidth - 4));
        final int buttonH = Math.min(GovLawPanel.PAGE_BTN_H,
                Math.max(0, p.rectBottom - p.docFooterY));
        p.chapterPrevRect = new GovRect(p.docLeft + 2, p.docFooterY, chapterButtonW, buttonH)
                .clamp(p.panelBounds);
        p.chapterNextRect = new GovRect(p.docRight - 2 - chapterButtonW, p.docFooterY,
                chapterButtonW, buttonH).clamp(p.panelBounds);

        int searchButtonW = GovMetrics.clamp(width / 5, 34, 56);
        searchButtonW = Math.min(searchButtonW, Math.max(0, width - 4));
        p.searchPrevRect = new GovRect(p.rectLeft + 2, p.searchFooterY, searchButtonW, buttonH)
                .clamp(p.panelBounds);
        p.searchNextRect = new GovRect(p.rectRight - 2 - searchButtonW, p.searchFooterY,
                searchButtonW, buttonH).clamp(p.panelBounds);

        if (p.searchBox != null) {
            p.searchBox.setX(p.searchX + 3);
            p.searchBox.setY(p.searchY + 4);
            p.searchBox.setWidth(Math.max(0, p.searchW - 6));
        }

        p.chapterCache = null;
        p.searchCache = null;
        GovLawChapters.ensureChapterLayout(p);
        GovLawChapters.updateChapterLabel(p);
        if (p.subtab == 1) {
            GovLawSearches.ensureSearchResults(p);
        }
    }
}
