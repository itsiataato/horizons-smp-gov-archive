package com.horizonssmp.gov;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

/**
 * The bundled Season 10 Law, loaded once from the mod jar.
 *
 * <p>The JSON is generated from the website's {@code law.ts} by
 * {@code tools/generate-law-json.mjs}. The mod never parses website TypeScript at runtime and
 * never downloads law data: the classpath resource inside the jar is the only source.
 *
 * <p>The returned object graph is immutable. A missing or malformed resource throws an
 * {@link IllegalStateException} naming the resource, so a broken build fails at startup with an
 * actionable message instead of silently giving players an empty book.
 */
public final class LawData {

    /** Classpath location of the generated law data. */
    public static final String RESOURCE = "/data/gov/law/season10.json";

    private static volatile LawData instance;

    private final String version;
    private final String lastUpdated;
    private final List<Chapter> chapters;
    private final int articleCount;

    private LawData(final String version, final String lastUpdated, final List<Chapter> chapters) {
        this.version = version;
        this.lastUpdated = lastUpdated;
        this.chapters = List.copyOf(chapters);
        int count = 0;
        for (final Chapter chapter : this.chapters) {
            count += chapter.articles().size();
        }
        this.articleCount = count;
    }

    /**
     * Returns the single immutable law instance, loading and validating it on first use.
     *
     * @return the loaded law
     * @throws IllegalStateException if the bundled resource is missing or malformed
     */
    public static LawData get() {
        LawData result = instance;
        if (result == null) {
            synchronized (LawData.class) {
                result = instance;
                if (result == null) {
                    result = load();
                    instance = result;
                }
            }
        }
        return result;
    }

    public String version() {
        return version;
    }

    public String lastUpdated() {
        return lastUpdated;
    }

    public List<Chapter> chapters() {
        return chapters;
    }

    public int articleCount() {
        return articleCount;
    }

    public int chapterCount() {
        return chapters.size();
    }

    private static LawData load() {
        final InputStream stream = LawData.class.getResourceAsStream(RESOURCE);
        if (stream == null) {
            throw new IllegalStateException(
                    "Missing bundled law data " + RESOURCE
                            + ". Run tools/generate-law-json.mjs and rebuild the mod.");
        }

        try (stream; Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            final JsonElement rootElement = JsonParser.parseReader(reader);
            if (!rootElement.isJsonObject()) {
                throw new IllegalStateException("Law data " + RESOURCE + " must be a JSON object");
            }
            final JsonObject root = rootElement.getAsJsonObject();
            final String version = requiredString(root, "version", "root");
            final String lastUpdated = requiredString(root, "lastUpdated", "root");

            final JsonElement chaptersElement = root.get("chapters");
            if (chaptersElement == null || !chaptersElement.isJsonArray()) {
                throw new IllegalStateException("Law data " + RESOURCE + " has no chapters array");
            }
            final JsonArray chapterArray = chaptersElement.getAsJsonArray();
            if (chapterArray.isEmpty()) {
                throw new IllegalStateException("Law data " + RESOURCE + " contains no chapters");
            }

            final List<Chapter> chapters = new ArrayList<>(chapterArray.size());
            final Set<String> chapterIds = new HashSet<>();
            final Set<String> articleIds = new HashSet<>();
            for (int chapterIndex = 0; chapterIndex < chapterArray.size(); chapterIndex++) {
                final JsonElement chapterElement = chapterArray.get(chapterIndex);
                if (!chapterElement.isJsonObject()) {
                    throw new IllegalStateException(
                            "Law data " + RESOURCE + " chapter " + (chapterIndex + 1) + " is not an object");
                }
                final Chapter chapter = parseChapter(
                        chapterElement.getAsJsonObject(), chapterIndex, chapterIds, articleIds);
                chapters.add(chapter);
            }

            final LawData data = new LawData(version, lastUpdated, chapters);
            Gov.LOGGER.info(
                    "[gov] Loaded law {} from {}: {} chapters, {} articles",
                    data.version(), RESOURCE, data.chapterCount(), data.articleCount());
            return data;
        } catch (final IOException | JsonParseException e) {
            throw new IllegalStateException(
                    "Failed to read bundled law data " + RESOURCE + ": " + e.getMessage(), e);
        }
    }

    private static Chapter parseChapter(
            final JsonObject object,
            final int chapterIndex,
            final Set<String> chapterIds,
            final Set<String> articleIds) {
        final String where = "chapter " + (chapterIndex + 1);
        final String id = requiredString(object, "id", where);
        if (!chapterIds.add(id)) {
            throw new IllegalStateException("Duplicate chapter id " + id + " in " + RESOURCE);
        }
        final String numeral = requiredString(object, "numeral", where);
        final String title = requiredString(object, "title", where);
        final String summary = requiredString(object, "summary", where);
        final String category = requiredString(object, "category", where);

        final JsonElement articlesElement = object.get("articles");
        if (articlesElement == null || !articlesElement.isJsonArray()) {
            throw new IllegalStateException(where + " in " + RESOURCE + " has no articles array");
        }
        final JsonArray articleArray = articlesElement.getAsJsonArray();
        if (articleArray.isEmpty()) {
            throw new IllegalStateException(where + " in " + RESOURCE + " contains no articles");
        }

        final List<Article> articles = new ArrayList<>(articleArray.size());
        for (int articleIndex = 0; articleIndex < articleArray.size(); articleIndex++) {
            final JsonElement articleElement = articleArray.get(articleIndex);
            final String articleWhere = where + ", article " + (articleIndex + 1);
            if (!articleElement.isJsonObject()) {
                throw new IllegalStateException(articleWhere + " in " + RESOURCE + " is not an object");
            }
            final JsonObject articleObject = articleElement.getAsJsonObject();
            final String articleId = requiredString(articleObject, "id", articleWhere);
            if (!articleIds.add(articleId)) {
                throw new IllegalStateException("Duplicate article id " + articleId + " in " + RESOURCE);
            }
            final String articleTitle = requiredString(articleObject, "title", articleWhere);
            final String body = requiredString(articleObject, "body", articleWhere);
            final String penalty = optionalString(articleObject, "penalty", articleWhere);
            articles.add(new Article(articleId, articleTitle, body, penalty));
        }

        return new Chapter(id, numeral, title, summary, category, articles);
    }

    private static String requiredString(
            final JsonObject object, final String field, final String where) {
        final JsonElement element = object.get(field);
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isString()) {
            throw new IllegalStateException(
                    "Law data " + RESOURCE + " " + where + " is missing string field '" + field + "'");
        }
        final String value = element.getAsString();
        if (value.trim().isEmpty()) {
            throw new IllegalStateException(
                    "Law data " + RESOURCE + " " + where + " has an empty field '" + field + "'");
        }
        return value;
    }

    private static String optionalString(
            final JsonObject object, final String field, final String where) {
        final JsonElement element = object.get(field);
        if (element == null || element.isJsonNull()) {
            return "";
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new IllegalStateException(
                    "Law data " + RESOURCE + " " + where + " field '" + field + "' must be a string");
        }
        return element.getAsString();
    }

    /**
     * One article of the law. {@code penalty} is empty when the source article has no penalty.
     */
    public record Article(String id, String title, String body, String penalty) {

        public Article {
            if (penalty == null) {
                penalty = "";
            }
        }

        public boolean hasPenalty() {
            return !penalty.isEmpty();
        }
    }

    /**
     * One chapter of the law, with its articles in source order.
     */
    public record Chapter(
            String id,
            String numeral,
            String title,
            String summary,
            String category,
            List<Article> articles) {

        public Chapter {
            articles = List.copyOf(articles);
        }
    }
}
