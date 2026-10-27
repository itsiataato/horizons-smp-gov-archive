package com.horizonssmp.gov.client;

import java.util.Locale;
import java.util.Map;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Composition helpers for {@link GovText}: label/value lines, section titles and cached styled
 * components.
 */
public final class GovTextCompose {

    private static final Map<String, Component> BOLD_CACHE = GovText.lru();
    private static final Map<String, Component> UPPER_BOLD_CACHE = GovText.lru();

    private GovTextCompose() {
    }

    /** Draws a muted label and its ink value on one line. */
    public static void labelValue(
            final GuiGraphics graphics, final Font font, final String label, final String value,
            final int labelX, final int valueX, final int y, final int valueWidth) {
        if (graphics == null || font == null || valueWidth <= 0) {
            return;
        }
        final int labelWidth = valueX - labelX - GovMetrics.GAP_XS;
        if (label != null && labelWidth > 0) {
            GovTextClip.clipped(graphics, font, label, labelX, y, labelWidth, GovTheme.MUTED);
        }
        if (value != null) {
            GovTextClip.clipped(graphics, font, value, valueX, y, valueWidth, GovTheme.TEXT);
        }
    }

    /** Draws an uppercase bold section title with a rule beneath it. */
    public static void sectionTitle(
            final GuiGraphics graphics, final Font font, final String text, final GovRect rect) {
        if (graphics == null || font == null || text == null || text.isEmpty()
                || rect == null || rect.w() <= 0 || rect.h() <= 0) {
            return;
        }
        final Component title = upperBoldCached(text);
        GovTextClip.clipped(graphics, font, title, rect.x(), rect.y(), rect.w(), GovTheme.TEXT);
        if (rect.h() > GovMetrics.LINE_TITLE) {
            final int ruleY = rect.y() + GovMetrics.LINE_TITLE;
            graphics.fill(rect.x(), ruleY, rect.right(), ruleY + 1, GovTheme.BORDER);
        }
    }

    /** Draws an uppercase bold section title from a component's plain text. */
    public static void sectionTitle(
            final GuiGraphics graphics, final Font font, final Component text, final GovRect rect) {
        sectionTitle(graphics, font, text == null ? null : text.getString(), rect);
    }

    /** A bold component built once per string. */
    public static Component boldCached(final String text) {
        if (text == null) {
            return Component.empty();
        }
        final String safe = GovText.sanitize(text);
        Component component = BOLD_CACHE.get(safe);
        if (component == null) {
            component = Component.literal(safe).withStyle(ChatFormatting.BOLD);
            BOLD_CACHE.put(safe, component);
        }
        return component;
    }

    /** An uppercase bold component built once per string. */
    public static Component upperBoldCached(final String text) {
        if (text == null) {
            return Component.empty();
        }
        final String upper = GovText.sanitize(text).toUpperCase(Locale.ROOT);
        Component component = UPPER_BOLD_CACHE.get(upper);
        if (component == null) {
            component = Component.literal(upper).withStyle(ChatFormatting.BOLD);
            UPPER_BOLD_CACHE.put(upper, component);
        }
        return component;
    }
}