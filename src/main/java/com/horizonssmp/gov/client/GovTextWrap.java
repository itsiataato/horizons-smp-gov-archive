package com.horizonssmp.gov.client;

import java.util.List;
import java.util.Map;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * Wrapping helpers for {@link GovText}: cached wrapped lines and drawing.
 */
public final class GovTextWrap {

    private static final Map<String, WrapEntry> WRAP_CACHE = GovText.lru();
    private static final Map<Component, WrapEntry> COMPONENT_WRAP_CACHE = GovText.lru();

    private GovTextWrap() {
    }

    /** Wrapped lines for a string, cached until the width changes. */
    public static List<FormattedCharSequence> wrapCached(
            final Font font, final String text, final int width) {
        if (font == null || text == null || text.isEmpty() || width <= 0) {
            return List.of();
        }
        WrapEntry entry = WRAP_CACHE.get(text);
        if (entry != null && entry.width() == width) {
            return entry.lines();
        }
        final String safe = GovText.sanitize(text);
        if (safe.isEmpty()) {
            return List.of();
        }
        entry = new WrapEntry(width, List.copyOf(font.split(Component.literal(safe), width)));
        WRAP_CACHE.put(text, entry);
        return entry.lines();
    }

    /** Wrapped lines for a component, cached until the width changes. */
    public static List<FormattedCharSequence> wrapCached(
            final Font font, final Component text, final int width) {
        if (font == null || text == null || width <= 0) {
            return List.of();
        }
        WrapEntry entry = COMPONENT_WRAP_CACHE.get(text);
        if (entry != null && entry.width() == width) {
            return entry.lines();
        }
        entry = new WrapEntry(width, List.copyOf(font.split(text, width)));
        COMPONENT_WRAP_CACHE.put(text, entry);
        return entry.lines();
    }

    /** Draws wrapped text and returns the number of lines drawn. */
    public static int drawWrapped(
            final GuiGraphics graphics, final Font font, final String text, final int x, final int y,
            final int width, final int lineHeight, final int maxLines, final int color) {
        if (graphics == null || font == null || text == null || width <= 0
                || lineHeight <= 0 || maxLines <= 0) {
            return 0;
        }
        final List<FormattedCharSequence> lines = wrapCached(font, text, width);
        final int count = Math.min(maxLines, lines.size());
        for (int index = 0; index < count; index++) {
            graphics.drawString(font, lines.get(index), x, y + index * lineHeight, color, false);
        }
        return count;
    }

    /** Draws wrapped component text and returns the number of lines drawn. */
    public static int drawWrapped(
            final GuiGraphics graphics, final Font font, final Component text, final int x, final int y,
            final int width, final int lineHeight, final int maxLines, final int color) {
        if (graphics == null || font == null || text == null || width <= 0
                || lineHeight <= 0 || maxLines <= 0) {
            return 0;
        }
        final List<FormattedCharSequence> lines = wrapCached(font, text, width);
        final int count = Math.min(maxLines, lines.size());
        for (int index = 0; index < count; index++) {
            graphics.drawString(font, lines.get(index), x, y + index * lineHeight, color, false);
        }
        return count;
    }

    private record WrapEntry(int width, List<FormattedCharSequence> lines) {
    }
}