package com.horizonssmp.gov.client;

import java.util.List;

record SearchLayout(List<SearchRow> rows, int[] pageStarts, int perPage) {

    int pageCount() {
        return this.pageStarts.length;
    }
}
