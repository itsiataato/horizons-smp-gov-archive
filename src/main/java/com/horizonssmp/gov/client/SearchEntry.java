package com.horizonssmp.gov.client;

import com.horizonssmp.gov.LawData;

record SearchEntry(int chapterIndex, LawData.Article article, String meta, String haystack) {
}
