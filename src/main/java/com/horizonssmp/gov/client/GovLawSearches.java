package com.horizonssmp.gov.client;

import java.util.ArrayList;
import java.util.List;

final class GovLawSearches {

    private GovLawSearches() {
    }

    static void ensureSearchResults(final GovLawPanel p) {
        final int width = Math.max(0, p.rectRight - p.rectLeft - 8);
        final int height = Math.max(0, p.resultsBottom - p.resultsTop);
        if (p.font == null || width <= 0 || height <= 0) {
            p.searchCache = null;
            p.resultHits = new GovRect[0];
            p.resultHitRows = new int[0];
            p.resultsPageLabel = "";
            return;
        }
        if (p.searchCache == null || !p.searchCacheQuery.equals(p.query)
                || p.searchCacheW != width || p.searchCacheH != height) {
            p.searchCache = buildSearch(p, p.query, width, height);
            p.searchCacheQuery = p.query;
            p.searchCacheW = width;
            p.searchCacheH = height;
        }
        p.resultPage = GovMetrics.clamp(p.resultPage, 0,
                Math.max(0, p.searchCache.pageCount() - 1));
        rebuildResultHits(p);
        updateResultsPageLabel(p);
    }

    static SearchLayout buildSearch(final GovLawPanel p, final String needle, final int width,
            final int height) {
        final List<SearchRow> rows = new ArrayList<>();
        if (!needle.isEmpty()) {
            for (final SearchEntry entry : p.searchIndex) {
                if (!entry.haystack().contains(needle)) {
                    continue;
                }
                rows.add(new SearchRow(entry.chapterIndex(), entry.article().id(),
                        GovText.boldCached(entry.article().title()), entry.meta()));
            }
        }
        if (rows.isEmpty()) {
            return new SearchLayout(List.of(), new int[0], 2);
        }
        int perPage = GovMetrics.clamp(height / GovLawPanel.RESULT_ROW_H, 2, 4);
        perPage = Math.min(perPage, rows.size());
        final int pages = (rows.size() + perPage - 1) / perPage;
        final int[] starts = new int[pages];
        for (int index = 0; index < pages; index++) {
            starts[index] = index * perPage;
        }
        p.resultPage = GovMetrics.clamp(p.resultPage, 0, Math.max(0, pages - 1));
        return new SearchLayout(List.copyOf(rows), starts, perPage);
    }

    static void rebuildResultHits(final GovLawPanel p) {
        if (p.searchCache == null || p.searchCache.pageCount() <= 0) {
            p.resultHits = new GovRect[0];
            p.resultHitRows = new int[0];
            return;
        }
        final int start = p.searchCache.pageStarts()[p.resultPage];
        final int end = Math.min(p.searchCache.rows().size(),
                start + p.searchCache.perPage());
        final GovRect[] hits = new GovRect[Math.max(0, end - start)];
        final int[] rowIndices = new int[hits.length];
        final int innerLeft = p.rectLeft + 2;
        final int innerWidth = Math.max(0, p.rectRight - p.rectLeft - 4);
        for (int index = 0; index < hits.length; index++) {
            final int y = p.resultsTop + index * GovLawPanel.RESULT_ROW_H;
            hits[index] = new GovRect(innerLeft, y, innerWidth, GovLawPanel.RESULT_ROW_H)
                    .clamp(p.panelBounds);
            rowIndices[index] = start + index;
        }
        p.resultHits = hits;
        p.resultHitRows = rowIndices;
    }

    static void updateResultsPageLabel(final GovLawPanel p) {
        if (p.searchCache == null || p.searchCache.pageCount() <= 0) {
            p.resultsPageLabel = "";
            return;
        }
        p.resultsPageLabel = GovLawPanel.tr("gui.gov.law.results_page", p.resultPage + 1,
                p.searchCache.pageCount());
    }

    static void onSearchChanged(final GovLawPanel p, final String value) {
        p.searchText = value == null ? "" : value;
        final String nextQuery = p.searchText.trim().toLowerCase(java.util.Locale.ROOT);
        if (!nextQuery.equals(p.query)) {
            p.resultPage = 0;
        }
        p.query = nextQuery;
        p.searchCache = null;
        ensureSearchResults(p);
    }
}
