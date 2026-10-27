package com.horizonssmp.gov.client;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * Text drawing for the institutional interface.
 *
 * <p>Every user string is clipped to a width and every wrapped block is cached. The caches are
 * bounded and access-ordered: they evict the coldest 512 entries. A cache entry remembers the
 * width it was built for, so a resize invalidates it without needing a separate clear. There are
 * no composite key objects, which means a cache hit allocates nothing in the render path.
 *
 * <p>Raw strings are sanitized before they reach the font. A section sign in a player name or note
 * is not harmless: {@link Component#literal} still passes the character to
 * {@code Language#getVisualOrder}, which parses legacy formatting codes. Stripping the sign and a
 * recognised code keeps the measured width identical because a parsed code is zero-width.
 *
 * <p>All text is drawn with {@code shadow = false}. A drop shadow belongs to a game HUD, not to a
 * printed government record.
 *
 * <p>Implementation is split across {@link GovTextClip}, {@link GovTextWrap},
 * {@link GovTextPosition} and {@link GovTextCompose}; this class keeps the public API and the
 * sanitizing entry point.
 */
public final class GovText {

    private static final int CACHE_LIMIT = 512;
    /**
     * The legacy formatting prefix that {@code StringDecomposer} still interprets inside
     * literals.
     */
    private static final char SECTION = '\u00a7';

    /** Strings that contained formatting codes, mapped to their visible text. */
    private static final Map<String, String> SANITIZE_CACHE = lru();

    private GovText() {
    }

    static <K, V> Map<K, V> lru() {
        // Access order plus a hard cap: the oldest unused string is the first to go. The client is
        // single-threaded, so this deliberately does not pay for synchronization.
        return new LinkedHashMap<>(64, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(final Map.Entry<K, V> eldest) {
                return size() > CACHE_LIMIT;
            }
        };
    }

    // ----------------------------------------------------------------- sanitizing

    /**
     * Removes legacy formatting codes from a raw string.
     *
     * <p>A section sign and the code character that follows it are removed together when the code
     * is one vanilla recognises; a lone or unknown section sign is removed by itself. The result is
     * cached only when the input actually contained a sign, so ordinary UI labels cost one map
     * miss rather than filling the cache with identity entries.
     */
    public static String sanitize(final String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        final String cached = SANITIZE_CACHE.get(text);
        if (cached != null) {
            return cached;
        }
        final int marker = text.indexOf(SECTION);
        if (marker < 0) {
            return text;
        }
        final StringBuilder visible = new StringBuilder(text.length());
        for (int index = 0; index < text.length(); index++) {
            final char character = text.charAt(index);
            if (character == SECTION) {
                if (index + 1 < text.length()
                        && ChatFormatting.getByCode(text.charAt(index + 1)) != null) {
                    index++;
                }
                continue;
            }
            visible.append(character);
        }
        final String result = visible.toString();
        SANITIZE_CACHE.put(text, result);
        return result;
    }

    // ------------------------------------------------------------------ clipping

    /** Draws a string clipped to a width, with a trailing ellipsis when it does not fit. */
    public static void clipped(
            final GuiGraphics graphics, final Font font, final String text, final int x, final int y,
            final int width, final int color) {
        GovTextClip.clipped(graphics, font, text, x, y, width, color);
    }

    /** Draws a component clipped to a width, preserving its styles and adding an ellipsis. */
    public static void clipped(
            final GuiGraphics graphics, final Font font, final Component text, final int x, final int y,
            final int width, final int color) {
        GovTextClip.clipped(graphics, font, text, x, y, width, color);
    }

    /** Returns the text that fits in a width, ending in an ellipsis when it had to be cut. */
    public static String clip(final Font font, final String text, final int width) {
        return GovTextClip.clip(font, text, width);
    }

    // -------------------------------------------------------------- positioning

    /** Draws text centred on a horizontal point. */
    public static void centered(
            final GuiGraphics graphics, final Font font, final String text, final int centerX,
            final int y, final int color) {
        GovTextPosition.centered(graphics, font, text, centerX, y, color);
    }

    /** Draws text centred inside a width, clipping it when it does not fit. */
    public static void centered(
            final GuiGraphics graphics, final Font font, final String text, final int x, final int y,
            final int width, final int color) {
        GovTextPosition.centered(graphics, font, text, x, y, width, color);
    }

    /** Draws a component centred on a horizontal point. */
    public static void centered(
            final GuiGraphics graphics, final Font font, final Component text, final int centerX,
            final int y, final int color) {
        GovTextPosition.centered(graphics, font, text, centerX, y, color);
    }

    /** Draws text ending at a right edge. */
    public static void right(
            final GuiGraphics graphics, final Font font, final String text, final int rightX,
            final int y, final int color) {
        GovTextPosition.right(graphics, font, text, rightX, y, color);
    }

    /** Draws a component ending at a right edge. */
    public static void right(
            final GuiGraphics graphics, final Font font, final Component text, final int rightX,
            final int y, final int color) {
        GovTextPosition.right(graphics, font, text, rightX, y, color);
    }

    // ------------------------------------------------------------------ wrapping

    /** Wrapped lines for a string, cached until the width changes. */
    public static List<FormattedCharSequence> wrapCached(
            final Font font, final String text, final int width) {
        return GovTextWrap.wrapCached(font, text, width);
    }

    /** Wrapped lines for a component, cached until the width changes. */
    public static List<FormattedCharSequence> wrapCached(
            final Font font, final Component text, final int width) {
        return GovTextWrap.wrapCached(font, text, width);
    }

    /** Draws wrapped text and returns the number of lines drawn. */
    public static int drawWrapped(
            final GuiGraphics graphics, final Font font, final String text, final int x, final int y,
            final int width, final int lineHeight, final int maxLines, final int color) {
        return GovTextWrap.drawWrapped(graphics, font, text, x, y, width, lineHeight, maxLines, color);
    }

    /** Draws wrapped component text and returns the number of lines drawn. */
    public static int drawWrapped(
            final GuiGraphics graphics, final Font font, final Component text, final int x, final int y,
            final int width, final int lineHeight, final int maxLines, final int color) {
        return GovTextWrap.drawWrapped(graphics, font, text, x, y, width, lineHeight, maxLines, color);
    }

    // --------------------------------------------------------------- compositions

    /** Draws a muted label and its ink value on one line. */
    public static void labelValue(
            final GuiGraphics graphics, final Font font, final String label, final String value,
            final int labelX, final int valueX, final int y, final int valueWidth) {
        GovTextCompose.labelValue(graphics, font, label, value, labelX, valueX, y, valueWidth);
    }

    /** Draws an uppercase bold section title with a rule beneath it. */
    public static void sectionTitle(
            final GuiGraphics graphics, final Font font, final String text, final GovRect rect) {
        GovTextCompose.sectionTitle(graphics, font, text, rect);
    }

    /** Draws an uppercase bold section title from a component's plain text. */
    public static void sectionTitle(
            final GuiGraphics graphics, final Font font, final Component text, final GovRect rect) {
        GovTextCompose.sectionTitle(graphics, font, text, rect);
    }

    /** A bold component built once per string. */
    public static Component boldCached(final String text) {
        return GovTextCompose.boldCached(text);
    }

    /** An uppercase bold component built once per string. */
    public static Component upperBoldCached(final String text) {
        return GovTextCompose.upperBoldCached(text);
    }
}