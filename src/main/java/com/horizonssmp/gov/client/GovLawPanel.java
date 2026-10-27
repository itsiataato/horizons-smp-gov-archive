package com.horizonssmp.gov.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.horizonssmp.gov.LawData;
import com.horizonssmp.gov.common.GovFields;
import com.horizonssmp.gov.common.OpCode;
import com.horizonssmp.gov.network.GovActionResultPayload;
import com.horizonssmp.gov.network.GovNetwork;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

/**
 * The page-based law reader.
 *
 * <p>The panel is a document, not a scroll view: the ten-chapter table of contents and the chapter
 * body are both paginated, and the wheel is never consumed. A chapter layout is cached by
 * {@code (chapter, contentWidth, contentHeight)} and a search layout by
 * {@code (query, contentWidth, contentHeight)}; a page break can fall between articles but never
 * inside a wrapped line. The host screen owns the shell and adds the one real widget, the search
 * field, which this panel positions and shows only in the Search sub-category.
 */
final class GovLawPanel {

    static final GovRect EMPTY = new GovRect(0, 0, 0, 0);
    static final int RESULT_ROW_H = 22;
    static final int PAGE_BTN_H = 14;
    static final int TOC_FOOTER_H = 14;
    static final int SEARCH_H = 16;

    /**
     * The id, title and first body line: 11 + 11 + 10 pixels. Keeping all three together means a
     * page never opens on a bare id or on a heading whose first line has slipped to the next page.
     */
    static final int ARTICLE_KEEP = 32;

    final LawData law = LawData.get();
    final Screen host;
    String latestArticleReqId = "";
    final Map<String, String> articleFields = new LinkedHashMap<>();
    final List<SearchEntry> searchIndex = new ArrayList<>();
    final String[] tocLabels;
    final String[] chapterHeadings;
    final Component searchHint;
    final String previousText;
    final String nextText;
    final String searchEmptyText;

    Font font;
    EditBox searchBox;
    boolean active;
    int subtab;
    int selectedChapter;
    int pageIndex;
    int resultPage;
    String searchText = "";
    String query = "";

    int rectLeft;
    int rectTop;
    int rectRight;
    int rectBottom;
    GovRect panelBounds = EMPTY;
    int tocRight;
    int tocTop;
    int tocBottom;
    int tocPitch = 8;
    int docLeft;
    int docRight;
    int docTop;
    int docBottom;
    int docTextX;
    int docTextW;
    int docFooterY;
    int searchX;
    int searchY;
    int searchW;
    int searchH;
    int resultsTop;
    int resultsBottom;
    int searchFooterY;
    GovRect searchFrameRect = EMPTY;

    GovRect[] tocHits = new GovRect[0];
    GovRect chapterPrevRect = EMPTY;
    GovRect chapterNextRect = EMPTY;
    GovRect searchPrevRect = EMPTY;
    GovRect searchNextRect = EMPTY;

    ChapterLayout chapterCache;
    int chapterCacheChapter = -1;
    int chapterCacheW = -1;
    int chapterCacheH = -1;
    String chapterOfLabel = "";
    String pageLabel = "";

    SearchLayout searchCache;
    String searchCacheQuery = "";
    int searchCacheW = -1;
    int searchCacheH = -1;
    String resultsPageLabel = "";
    GovRect[] resultHits = new GovRect[0];
    int[] resultHitRows = new int[0];

    GovLawPanel(final Screen host) {
        this.host = host;
        final int chapters = this.law.chapters().size();
        this.tocLabels = new String[chapters];
        this.chapterHeadings = new String[chapters];
        for (int index = 0; index < chapters; index++) {
            final LawData.Chapter chapter = this.law.chapters().get(index);
            this.tocLabels[index] = chapter.numeral() + "  " + chapter.title();
            this.chapterHeadings[index] = tr("gui.gov.law.chapter", chapter.numeral());
            final String meta = this.chapterHeadings[index] + "  /  "
                    + chapter.category().toUpperCase(Locale.ROOT);
            for (final LawData.Article article : chapter.articles()) {
                final String haystack = (chapter.title() + " " + chapter.category() + " "
                        + chapter.numeral() + " "
                        + article.id() + " " + article.title() + " " + article.body() + " "
                        + article.penalty()).toLowerCase(Locale.ROOT);
                this.searchIndex.add(new SearchEntry(index, article, meta, haystack));
            }
        }
        this.searchHint = Component.translatable("gui.gov.law.search_hint");
        this.previousText = tr("gui.gov.law.prev");
        this.nextText = tr("gui.gov.law.next");
        this.searchEmptyText = tr("gui.gov.law.search_empty");
        GovLawChapters.updateChapterLabel(this);
    }

    void requestArticle(final String id) {
        final String reqId = UUID.randomUUID().toString();
        this.latestArticleReqId = reqId;
        GovNetwork.sendRequest(OpCode.LAW_ARTICLE, Map.of("article", id == null ? "" : id), reqId);
    }

    void onActionResult(final GovActionResultPayload payload) {
        if (payload == null || payload.reqId().isEmpty()
                || !payload.reqId().equals(this.latestArticleReqId)) {
            return;
        }
        if (payload.ok()) {
            this.articleFields.clear();
            this.articleFields.putAll(GovFields.decode(payload.delta()));
        } else {
            this.articleFields.clear();
            this.articleFields.put("error", payload.errorText());
        }
    }

    static String tr(final String key, final Object... args) {
        return Component.translatable(key, args).getString();
    }

    /** The law shown by this panel. */
    LawData law() {
        return this.law;
    }

    // ------------------------------------------------------------- public state

    void setActive(final boolean active) {
        this.active = active;
        GovLawActions.syncSearchVisibility(this);
        if (!active) {
            GovLawActions.clearSearchFocus(this);
            return;
        }
        if (this.subtab == 1) {
            GovLawSearches.ensureSearchResults(this);
        } else {
            GovLawChapters.ensureChapterLayout(this);
        }
    }

    void setSubtab(final int index) {
        this.subtab = index == 1 ? 1 : 0;
        GovLawActions.syncSearchVisibility(this);
        if (this.subtab == 1) {
            GovLawSearches.ensureSearchResults(this);
        } else {
            GovLawChapters.ensureChapterLayout(this);
            GovLawActions.clearSearchFocus(this);
        }
        this.notifySubtabChanged();
    }

    /** Keeps the host shell's top strip in step when a result returns the reader to Chapters. */
    private void notifySubtabChanged() {
        if (this.host instanceof GovDashboardScreen screen) {
            screen.onLawSubtabChanged(this.subtab);
        }
    }

    // ------------------------------------------------------------------ widgets

    EditBox createSearchBox(final Font font) {
        this.font = font;
        if (this.searchBox == null) {
            this.searchBox = GovWidgets.field(font, 0, 0, 10, 9, this.searchHint);
            this.searchBox.setMaxLength(80);
            this.searchBox.setValue(this.searchText);
            this.searchBox.setResponder(value -> GovLawSearches.onSearchChanged(this, value));
        }
        this.searchBox.setVisible(this.active && this.subtab == 1
                && this.searchW > 0 && this.searchH > 0);
        return this.searchBox;
    }

    /** The required widget factory; it returns the same cached field on every call. */
    List<EditBox> createWidgets(final Font font) {
        return List.of(createSearchBox(font));
    }

    // ------------------------------------------------------------------ layout

    void layout(final int left, final int top, final int right, final int bottom,
            final Font font) {
        GovLawLayout.layout(this, left, top, right, bottom, font);
    }

    // ------------------------------------------------------------------ render

    void render(final GuiGraphics graphics, final int mouseX, final int mouseY) {
        GovLawRenderer.render(this, graphics, mouseX, mouseY);
    }

    // ------------------------------------------------------------------- input

    boolean mouseClicked(final double mouseX, final double mouseY, final int button) {
        return GovLawInput.mouseClicked(this, mouseX, mouseY, button);
    }

    boolean keyPressed(final int keyCode, final int scanCode, final int modifiers) {
        return GovLawInput.keyPressed(this, keyCode, scanCode, modifiers);
    }

}

