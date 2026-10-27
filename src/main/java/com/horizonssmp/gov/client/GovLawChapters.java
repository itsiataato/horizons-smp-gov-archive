package com.horizonssmp.gov.client;

import com.horizonssmp.gov.LawData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

final class GovLawChapters {

    private GovLawChapters() {
    }

    static void ensureChapterLayout(final GovLawPanel p) {
        if (p.font == null || p.law().chapters().isEmpty()
                || p.docTextW <= 0 || p.docBottom <= p.docTop) {
            p.chapterCache = null;
            p.pageLabel = "";
            return;
        }
        p.selectedChapter = GovMetrics.clamp(p.selectedChapter, 0,
                p.law().chapters().size() - 1);
        final int pageHeight = Math.max(1, p.docBottom - p.docTop);
        if (p.chapterCache == null || p.chapterCacheChapter != p.selectedChapter
                || p.chapterCacheW != p.docTextW || p.chapterCacheH != pageHeight) {
            p.chapterCache = buildChapter(p, p.selectedChapter, p.docTextW, pageHeight);
            p.chapterCacheChapter = p.selectedChapter;
            p.chapterCacheW = p.docTextW;
            p.chapterCacheH = pageHeight;
        }
        p.pageIndex = GovMetrics.clamp(p.pageIndex, 0,
                Math.max(0, p.chapterCache.pageCount() - 1));
        updatePageLabel(p);
        updateChapterLabel(p);
    }

    static ChapterLayout buildChapter(final GovLawPanel p, final int chapterIndex,
            final int width, final int pageHeight) {
        final LawData.Chapter chapter = p.law().chapters().get(chapterIndex);
        final List<DocLine> lines = new ArrayList<>();
        final int safeWidth = Math.max(1, width);

        GovLawChapters.addWrapped(p, lines, Component.literal(chapter.category().toUpperCase(java.util.Locale.ROOT)),
                safeWidth, 10, GovTheme.FAINT);
        lines.add(DocLine.spacer(1));
        GovLawChapters.addWrapped(p, lines,
                Component.literal(p.chapterHeadings[chapterIndex])
                        .withStyle(ChatFormatting.BOLD),
                safeWidth, 11, GovTheme.TEXT);
        GovLawChapters.addWrapped(p, lines,
                Component.literal(chapter.title()).withStyle(ChatFormatting.BOLD),
                safeWidth, 11, GovTheme.TEXT);
        GovLawChapters.addWrapped(p, lines,
                Component.literal(chapter.summary()).withStyle(ChatFormatting.ITALIC),
                safeWidth, 10, GovTheme.MUTED);
        lines.add(DocLine.spacer(4));
        lines.add(DocLine.doubleRule());
        lines.add(DocLine.spacer(6));

        for (final LawData.Article article : chapter.articles()) {
            lines.add(DocLine.mono(article.id(), GovTheme.ACCENT, GovLawPanel.ARTICLE_KEEP));
            GovLawChapters.addWrapped(p, lines,
                    Component.literal(article.title()).withStyle(ChatFormatting.BOLD),
                    safeWidth, 11, GovTheme.TEXT);
            GovLawChapters.addWrapped(p, lines, Component.literal(article.body()),
                    safeWidth, 10, GovTheme.BODY);
            if (article.hasPenalty()) {
                lines.add(DocLine.spacer(2));
                final Component penalty = Component.literal("")
                        .append(Component.translatable("gui.gov.penalty"))
                        .append(Component.literal(": " + article.penalty()));
                GovLawChapters.addWrapped(p, lines, penalty, safeWidth, 10, GovTheme.MUTED);
            }
            lines.add(DocLine.spacer(6));
            lines.add(DocLine.rule());
            lines.add(DocLine.spacer(6));
        }
        return paginate(lines, pageHeight);
    }

    static void addWrapped(final GovLawPanel p, final List<DocLine> lines, final Component text,
            final int width, final int lineHeight, final int color) {
        if (text == null || width <= 0) {
            return;
        }
        for (final FormattedCharSequence sequence : GovText.wrapCached(p.font, text, width)) {
            lines.add(DocLine.text(sequence, lineHeight, color));
        }
    }

    // addWrapped is an instance method originally; expose as helper above.
    // But GovLawPanel.addWrapped private; we moved. Update callers: p.addWrapped -> GovLawChapters.addWrapped(p,...)
    // However original code calls this.addWrapped. In buildChapter we call p.addWrapped above? We used p.addWrapped; need change to GovLawChapters.addWrapped or keep delegator.
    // We'll make GovLawPanel have package-private delegator addWrapped to helper for compatibility.
    static ChapterLayout paginate(final List<DocLine> lines, final int pageHeight) {
        final List<Integer> starts = new ArrayList<>();
        starts.add(0);
        int used = 0;
        int start = 0;
        for (int index = 0; index < lines.size(); index++) {
            final DocLine line = lines.get(index);
            final int need = line.keep() > 0
                    ? Math.max(line.height(), line.keep()) : line.height();
            if (index > start && used + need > pageHeight) {
                starts.add(index);
                start = index;
                used = 0;
            }
            used += line.height();
        }
        final int[] pageStarts = new int[starts.size()];
        for (int index = 0; index < starts.size(); index++) {
            pageStarts[index] = starts.get(index);
        }
        assert breaksMatch(lines, pageStarts, pageHeight)
                : "law page boundary does not match the break rule";
        final Map<String, Integer> articlePages = new LinkedHashMap<>();
        int page = 0;
        for (int index = 0; index < lines.size(); index++) {
            while (page + 1 < pageStarts.length && pageStarts[page + 1] <= index) {
                page++;
            }
            final String id = lines.get(index).id();
            if (id != null) {
                articlePages.put(id, page);
            }
        }
        return new ChapterLayout(List.copyOf(lines), pageStarts, Map.copyOf(articlePages));
    }

    private static boolean breaksMatch(final List<DocLine> lines, final int[] pageStarts,
            final int pageHeight) {
        int start = 0;
        int used = 0;
        int next = 1;
        for (int index = 0; index < lines.size(); index++) {
            final DocLine line = lines.get(index);
            final int need = line.keep() > 0
                    ? Math.max(line.height(), line.keep()) : line.height();
            if (index > start && used + need > pageHeight) {
                if (next >= pageStarts.length || pageStarts[next] != index) {
                    return false;
                }
                next++;
                start = index;
                used = 0;
            }
            used += line.height();
        }
        return next == pageStarts.length;
    }

    static void updatePageLabel(final GovLawPanel p) {
        if (p.chapterCache == null || p.chapterCache.pageCount() <= 0) {
            p.pageLabel = "";
            return;
        }
        p.pageLabel = GovLawPanel.tr("gui.gov.law.page_of", p.pageIndex + 1,
                p.chapterCache.pageCount());
    }

    static void updateChapterLabel(final GovLawPanel p) {
        if (p.law().chapters().isEmpty()) {
            p.chapterOfLabel = "";
            return;
        }
        p.chapterOfLabel = GovLawPanel.tr("gui.gov.law.chapter_of",
                p.selectedChapter + 1, p.law().chapters().size());
    }
}
