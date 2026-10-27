package com.horizonssmp.gov.client;

import net.minecraft.client.gui.screens.Screen;

final class GovLawActions {

    private GovLawActions() {
    }

    static void selectChapter(final GovLawPanel p, final int index) {
        if (p.law().chapters().isEmpty()) {
            return;
        }
        p.selectedChapter = GovMetrics.clamp(index, 0, p.law().chapters().size() - 1);
        p.pageIndex = 0;
        p.chapterCache = null;
        GovLawChapters.ensureChapterLayout(p);
        GovLawChapters.updateChapterLabel(p);
        GovLawChapters.updatePageLabel(p);
        clearSearchFocus(p);
    }

    static void switchChapter(final GovLawPanel p, final int delta) {
        if (p.law().chapters().isEmpty()) {
            return;
        }
        final int next = GovMetrics.clamp(p.selectedChapter + delta, 0,
                p.law().chapters().size() - 1);
        if (next != p.selectedChapter) {
            selectChapter(p, next);
        }
    }

    static void selectSearchResult(final GovLawPanel p, final SearchRow row) {
        if (row == null) {
            return;
        }
        p.selectedChapter = GovMetrics.clamp(row.chapterIndex(), 0,
                Math.max(0, p.law().chapters().size() - 1));
        p.pageIndex = 0;
        p.chapterCache = null;
        p.setSubtab(0);
        GovLawChapters.ensureChapterLayout(p);
        p.pageIndex = p.chapterCache == null ? 0
                : p.chapterCache.pageFor(row.articleId());
        GovLawChapters.updatePageLabel(p);
    }

    static void goToPage(final GovLawPanel p, final int delta) {
        if (p.chapterCache == null) {
            return;
        }
        goToPageIndex(p, p.pageIndex + delta);
    }

    static void goToPageIndex(final GovLawPanel p, final int index) {
        if (p.chapterCache == null || p.chapterCache.pageCount() <= 0) {
            return;
        }
        p.pageIndex = GovMetrics.clamp(index, 0, p.chapterCache.pageCount() - 1);
        GovLawChapters.updatePageLabel(p);
    }

    static void goResultPage(final GovLawPanel p, final int delta) {
        if (p.searchCache == null) {
            return;
        }
        goToResultPage(p, p.resultPage + delta);
    }

    static void goToResultPage(final GovLawPanel p, final int index) {
        if (p.searchCache == null || p.searchCache.pageCount() <= 0) {
            return;
        }
        p.resultPage = GovMetrics.clamp(index, 0, p.searchCache.pageCount() - 1);
        GovLawSearches.rebuildResultHits(p);
        GovLawSearches.updateResultsPageLabel(p);
    }

    static void focusSearch(final GovLawPanel p) {
        if (p.searchBox != null && p.active && p.subtab == 1 && p.searchBox.visible) {
            p.host.setFocused(p.searchBox);
        }
    }

    static void clearSearchFocus(final GovLawPanel p) {
        if (p.searchBox != null && p.host.getFocused() == p.searchBox) {
            p.host.setFocused(null);
        }
    }

    static void syncSearchVisibility(final GovLawPanel p) {
        if (p.searchBox == null) {
            return;
        }
        final boolean visible = p.active && p.subtab == 1
                && p.searchW > 0 && p.searchH > 0;
        p.searchBox.setVisible(visible);
        if (!visible) {
            clearSearchFocus(p);
        }
    }
}
