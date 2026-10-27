package com.horizonssmp.gov.client;

import java.util.Map;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;

/**
 * Clipping helpers for {@link GovText}: strings and components cut to a width with a trailing
 * ellipsis, cached by text and invalidated when the width changes.
 */
public final class GovTextClip {

    private static final String ELLIPSIS = "...";
    private static final Map<String, ClipEntry> CLIP_CACHE = GovText.lru();
    private static final Map<Component, ComponentClipEntry> COMPONENT_CLIP_CACHE = GovText.lru();

    private GovTextClip() {
    }

    /** Draws a string clipped to a width, with a trailing ellipsis when it does not fit. */
    public static void clipped(
            final GuiGraphics graphics, final Font font, final String text, final int x, final int y,
            final int width, final int color) {
        if (graphics == null || font == null || text == null || text.isEmpty() || width <= 0) {
            return;
        }
        graphics.drawString(font, clip(font, text, width), x, y, color, false);
    }

    /** Draws a component clipped to a width, preserving its styles and adding an ellipsis. */
    public static void clipped(
            final GuiGraphics graphics, final Font font, final Component text, final int x, final int y,
            final int width, final int color) {
        if (graphics == null || font == null || text == null || width <= 0) {
            return;
        }
        ComponentClipEntry entry = COMPONENT_CLIP_CACHE.get(text);
        if (entry == null || entry.width() != width) {
            entry = buildComponentClip(font, text, width);
            COMPONENT_CLIP_CACHE.put(text, entry);
        }
        if (!entry.truncated()) {
            graphics.drawString(font, text, x, y, color, false);
            return;
        }
        final FormattedCharSequence sequence = entry.sequence();
        if (sequence != null) {
            graphics.drawString(font, sequence, x, y, color, false);
        }
        final int remaining = width - entry.sequenceWidth();
        if (remaining > 0) {
            clipped(graphics, font, ELLIPSIS, x + entry.sequenceWidth(), y, remaining, color);
        }
    }

    /**
     * Returns the text that fits in a width, ending in an ellipsis when it had to be cut.
     */
    public static String clip(final Font font, final String text, final int width) {
        if (font == null || text == null || text.isEmpty() || width <= 0) {
            return "";
        }
        ClipEntry entry = CLIP_CACHE.get(text);
        if (entry != null && entry.width() == width) {
            return entry.text();
        }
        final String safe = GovText.sanitize(text);
        final String result;
        if (font.width(safe) <= width) {
            result = safe;
        } else {
            final int ellipsisWidth = font.width(ELLIPSIS);
            if (width <= ellipsisWidth) {
                result = font.plainSubstrByWidth(ELLIPSIS, width);
            } else {
                result = font.plainSubstrByWidth(safe, width - ellipsisWidth) + ELLIPSIS;
            }
        }
        CLIP_CACHE.put(text, new ClipEntry(width, result));
        return result;
    }

    private static ComponentClipEntry buildComponentClip(
            final Font font, final Component text, final int width) {
        if (font.width(text) <= width) {
            return new ComponentClipEntry(width, false, width, null);
        }
        final int ellipsisWidth = font.width(ELLIPSIS);
        if (width <= ellipsisWidth) {
            return new ComponentClipEntry(width, true, 0, null);
        }
        final FormattedText visible = font.substrByWidth(text, width - ellipsisWidth);
        final FormattedCharSequence sequence = Language.getInstance().getVisualOrder(visible);
        return new ComponentClipEntry(width, true, font.width(sequence), sequence);
    }

    private record ClipEntry(int width, String text) {
    }

    private record ComponentClipEntry(
            int width, boolean truncated, int sequenceWidth, FormattedCharSequence sequence) {
    }
}