package com.horizonssmp.gov.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * The shared institutional widgets.
 *
 * <p>Every widget paints from {@link GovTheme} only: paper fills, ink borders, monochrome text.
 * There are no gradients, rounded corners or shadows. A widget accepts a {@link GovRect} and
 * returns without drawing when the rectangle is empty, so a cramped layout degrades to "nothing
 * shown" instead of a negative-size fill.
 */
public final class GovWidgets {

    private GovWidgets() {
    }

    public static void button(
            final GuiGraphics graphics, final Font font, final GovRect rect, final String label,
            final boolean enabled, final boolean hovered, final boolean primary) {
        GovWidgetButtons.button(graphics, font, rect, label, enabled, hovered, primary);
    }

    public static void button(
            final GuiGraphics graphics, final Font font, final GovRect rect, final Component label,
            final boolean enabled, final boolean hovered, final boolean primary) {
        GovWidgetButtons.button(graphics, font, rect, label, enabled, hovered, primary);
    }

    public static void fieldFrame(
            final GuiGraphics graphics, final GovRect rect, final boolean focused) {
        GovWidgetFrames.fieldFrame(graphics, rect, focused);
    }

    public static EditBox field(
            final Font font, final int x, final int y, final int width, final int height,
            final Component hint) {
        return GovWidgetFrames.field(font, x, y, width, height, hint);
    }

    public static void tableHeader(
            final GuiGraphics graphics, final Font font, final GovRect rect,
            final String[] columns) {
        GovWidgetPanels.tableHeader(graphics, font, rect, columns);
    }

    public static void rule(
            final GuiGraphics graphics, final int x, final int y, final int width,
            final int color) {
        GovWidgetRules.rule(graphics, x, y, width, color);
    }

    public static void rule(final GuiGraphics graphics, final GovRect rect) {
        GovWidgetRules.rule(graphics, rect);
    }

    public static void rule(final GuiGraphics graphics, final GovRect rect, final int color) {
        GovWidgetRules.rule(graphics, rect, color);
    }

    public static void doubleRule(final GuiGraphics graphics, final GovRect rect) {
        GovWidgetRules.doubleRule(graphics, rect);
    }

    public static void doubleRule(
            final GuiGraphics graphics, final int x, final int y, final int width) {
        GovWidgetRules.doubleRule(graphics, x, y, width);
    }

    public static void stamp(
            final GuiGraphics graphics, final Font font, final GovRect rect, final String text,
            final boolean strong) {
        GovWidgetStamps.stamp(graphics, font, rect, text, strong);
    }

    public static void stamp(
            final GuiGraphics graphics, final Font font, final GovRect rect, final Component text,
            final boolean strong) {
        GovWidgetStamps.stamp(graphics, font, rect, text, strong);
    }

    public static void modalBackdrop(final GuiGraphics graphics, final GovRect content) {
        GovWidgetFrames.modalBackdrop(graphics, content);
    }

    public static void modalFrame(
            final GuiGraphics graphics, final Font font, final GovRect rect, final String title) {
        GovWidgetFrames.modalFrame(graphics, font, rect, title);
    }

    public static void modalFrame(
            final GuiGraphics graphics, final Font font, final GovRect rect,
            final Component title) {
        GovWidgetFrames.modalFrame(graphics, font, rect, title);
    }

    public static void emptyState(
            final GuiGraphics graphics, final Font font, final GovRect rect, final String title,
            final String detail) {
        GovWidgetPanels.emptyState(graphics, font, rect, title, detail);
    }

    public static void scrollbar(
            final GuiGraphics graphics, final GovRect rect, final int scroll,
            final int maxScroll) {
        GovWidgetPanels.scrollbar(graphics, rect, scroll, maxScroll);
    }

    static void scrollbar(
            final GuiGraphics graphics, final int x, final int y, final int width,
            final int height, final int scroll, final int maxScroll) {
        GovWidgetPanels.scrollbar(graphics, x, y, width, height, scroll, maxScroll);
    }

    public static void tooltip(
            final GuiGraphics graphics, final Font font, final int x, final int y,
            final String title, final String detail) {
        GovWidgetPanels.tooltip(graphics, font, x, y, title, detail);
    }

    public static void tooltip(
            final GuiGraphics graphics, final Font font, final int x, final int y,
            final String title, final String detail, final int screenW, final int screenH) {
        GovWidgetPanels.tooltip(graphics, font, x, y, title, detail, screenW, screenH);
    }

    public static void iconButton(
            final GuiGraphics graphics, final GovRect rect, final GovIcons.GovIcon icon,
            final boolean enabled, final boolean hovered) {
        GovWidgetButtons.iconButton(graphics, rect, icon, enabled, hovered);
    }

    static boolean hasArea(final GovRect rect) {
        return rect != null && rect.w() > 0 && rect.h() > 0;
    }
}
