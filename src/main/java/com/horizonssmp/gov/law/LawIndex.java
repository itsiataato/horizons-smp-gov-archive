package com.horizonssmp.gov.law;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.horizonssmp.gov.LawData;

/**
 * A lookup index over the bundled law.
 *
 * <p>The law itself is {@link LawData}: one immutable object graph parsed once from the jar. This
 * class adds nothing to that data and never re-reads the resource; it only builds an id-keyed view
 * so a future fine can be validated against a real article without walking ten chapters on every
 * request.
 *
 * <p>It exists because a fine records the article it rests on, and that article id comes from a
 * client. Validating it needs a fast, exact "does this article exist" answer, and the answer must
 * come from the same law the book and the reader show. Indexing {@link LawData} rather than copying
 * it is what guarantees that: there is exactly one copy of the law in the process.
 *
 * <p>Article ids are compared case-insensitively and with surrounding whitespace ignored, because
 * an id arrives from a text field or a payload. Nothing else is normalized: {@code 9.1} and
 * {@code 9.10} are different articles and a near miss must fail, not match.
 */
public final class LawIndex {

    private static volatile LawIndex instance;

    private final LawData law;
    private final Map<String, LawData.Article> articlesById;
    private final Map<String, LawData.Chapter> chapterByArticleId;
    private final List<LawData.Article> articles;

    private LawIndex(final LawData law) {
        this.law = law;
        final Map<String, LawData.Article> byId = new HashMap<>();
        final Map<String, LawData.Chapter> byChapter = new HashMap<>();
        final List<LawData.Article> flat = new ArrayList<>();
        for (final LawData.Chapter chapter : law.chapters()) {
            for (final LawData.Article article : chapter.articles()) {
                byId.putIfAbsent(normalize(article.id()), article);
                byChapter.putIfAbsent(normalize(article.id()), chapter);
                flat.add(article);
            }
        }
        this.articlesById = Map.copyOf(byId);
        this.chapterByArticleId = Map.copyOf(byChapter);
        this.articles = List.copyOf(flat);
    }

    /**
     * The index over the bundled law, built on first use.
     *
     * @throws IllegalStateException if the bundled law resource is missing or malformed
     */
    public static LawIndex get() {
        LawIndex result = instance;
        if (result == null) {
            synchronized (LawIndex.class) {
                result = instance;
                if (result == null) {
                    result = new LawIndex(LawData.get());
                    instance = result;
                }
            }
        }
        return result;
    }

    /**
     * Looks up one article.
     *
     * @param id the article id as it appears in the law, for example {@code 9.11}
     * @return the article, or empty when there is no such article
     */
    public Optional<LawData.Article> article(final String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(articlesById.get(normalize(id)));
    }

    /**
     * Whether an article id exists in the bundled law.
     *
     * <p>This is the check a fine-issuing service must make before storing an article reference.
     */
    public boolean exists(final String id) {
        return article(id).isPresent();
    }

    /** The chapter an article belongs to, or empty when the article does not exist. */
    public Optional<LawData.Chapter> chapterOf(final String articleId) {
        if (articleId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(chapterByArticleId.get(normalize(articleId)));
    }

    /** Every article, in chapter and source order. */
    public List<LawData.Article> articles() {
        return articles;
    }

    /** Every article id, in chapter and source order. */
    public List<String> articleIds() {
        final List<String> ids = new ArrayList<>(articles.size());
        for (final LawData.Article article : articles) {
            ids.add(article.id());
        }
        return List.copyOf(ids);
    }

    /** The number of indexed articles. */
    public int articleCount() {
        return articles.size();
    }

    /** The number of chapters in the underlying law. */
    public int chapterCount() {
        return law.chapterCount();
    }

    /** The underlying law. The index holds no data of its own. */
    public LawData law() {
        return law;
    }

    private static String normalize(final String id) {
        return id.strip().toLowerCase(Locale.ROOT);
    }
}
