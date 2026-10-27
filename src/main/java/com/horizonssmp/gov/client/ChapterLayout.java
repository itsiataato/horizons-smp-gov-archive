package com.horizonssmp.gov.client;

import java.util.List;
import java.util.Map;

record ChapterLayout(List<DocLine> lines, int[] pageStarts, Map<String, Integer> articlePages) {

    int pageCount() {
        return this.pageStarts.length;
    }

    /** The first line after {@code page}; the renderer draws exactly this half-open range. */
    int pageEnd(final int page) {
        return page + 1 < this.pageStarts.length
                ? this.pageStarts[page + 1] : this.lines.size();
    }

    int pageFor(final String articleId) {
        final Integer found = this.articlePages.get(articleId);
        return found == null ? 0 : found;
    }
}
