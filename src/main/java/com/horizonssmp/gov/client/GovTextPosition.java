package com.horizonssmp.gov.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Positioning helpers for {@link GovText}: centred and right-aligned drawing.
 */
public final class GovTextPosition {

    private GovTextPosition() {
    }

    /** Draws text centred on a horizontal point. */
    public static void centered(
            final GuiGraphics graphics, final Font font, final String text, final int centerX,
            final int y, final int color) {
        if (graphics == null || font == null || text == null) {
            return;
        }
        final String safe = GovText.sanitize(text);
        if (safe.isEmpty()) {
            return;
        }
        graphics.drawString(font, safe, centerX - font.width(safe) / 2, y, color, false);
    }

    /** Draws text centred inside a width, clipping it when it does not fit. */
    public static void centered(
            final GuiGraphics graphics, final Font font, final String text, final int x, final int y,
            final int width, final int color) {
        if (graphics == null || font == null || text == null || text.isEmpty() || width <= 0) {
            return;
        }
        final String visible = GovTextClip.clip(font, text, width);
        graphics.drawString(font, visible, x + (width - font.width(visible)) / 2, y, color, false);
    }

    /** Draws a component centred on a horizontal point. */
    public static void centered(
            final GuiGraphics graphics, final Font font, final Component text, final int centerX,
            final int y, final int color) {
        if (graphics == null || font == null || text == null) {
            return;
        }
        graphics.drawString(font, text, centerX - font.width(text) / 2, y, color, false);
    }

    /** Draws text ending at a right edge. */
    public static void right(
            final GuiGraphics graphics, final Font font, final String text, final int rightX,
            final int y, final int color) {
        if (graphics == null || font == null || text == null) {
            return;
        }
        final String safe = GovText.sanitize(text);
        if (safe.isEmpty()) {
            return;
        }
        graphics.drawString(font, safe, rightX - font.width(safe), y, color, false);
    }

    /** Draws a component ending at a right edge. */
    public static void right(
            final GuiGraphics graphics, final Font font, final Component text, final int rightX,
            final int y, final int color) {
        if (graphics == null || font == null || text == null) {
            return;
        }
        graphics.drawString(font, text, rightX - font.width(text), y, color, false);
    }
}