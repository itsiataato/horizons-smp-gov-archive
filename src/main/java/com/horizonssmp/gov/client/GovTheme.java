package com.horizonssmp.gov.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * The Gov palette and the low-level chrome primitives.
 *
 * <p>The visual system is an institutional paper record: an ink header, a neutral paper body,
 * hairline rules and no chromatic colour anywhere. Every palette value below has equal red,
 * green and blue bytes. Status is carried by words, weight, rules and position; green, amber and
 * red are deliberately absent. The three status tokens are monochrome shades so existing panels
 * can keep their names without reintroducing a hue.
 *
 * <p>Contrast: {@link #FAINT} (#5C5C5C) is 5.97:1 on {@link #PANEL} (#F2F2F2) and 4.51:1 on the
 * darkest small-text background, {@link #SELECTED} (#D4D4D4). It therefore clears the 4.5:1
 * requirement for tertiary text on every paper surface. {@link #MUTED} and {@link #BODY} are
 * darker still.
 *
 * <p>The helpers at the bottom are kept source-compatible for the ten existing panels. New code
 * should prefer {@link GovWidgets} and {@link GovText}, which handle empty rectangles and clipping.
 */
public final class GovTheme {

    // ------------------------------------------------------------------- paper

    /** The screen backdrop behind the panel; outside the paper. */
    public static final int BACKGROUND = 0xFF080808;
    /** The world dimming behind the panel. Same neutral as {@link #BACKGROUND}. */
    public static final int OVERLAY = 0xE6080808;

    /** The main paper surface. */
    public static final int PANEL = 0xFFF2F2F2;
    /** The rail surface, one step darker than the paper. */
    public static final int SIDEBAR = 0xFFE6E6E6;
    /** Form fields and recessed paper. */
    public static final int FIELD = 0xFFFCFCFC;
    /** Selected paper band and table header. */
    public static final int PANEL_ALT = 0xFFE9E9E9;

    /** Strong ink rules: panel outline, focused field. */
    public static final int BORDER = 0xFF1B1B1B;
    /** Hairline dividers. */
    public static final int BORDER_SOFT = 0xFFC6C6C6;

    // -------------------------------------------------------------------- ink

    /** Headings and primary values. */
    public static final int TEXT = 0xFF111111;
    /** Body text. */
    public static final int BODY = 0xFF222222;
    /** Secondary labels. */
    public static final int MUTED = 0xFF4C4C4C;
    /** Tertiary text and disabled labels; at least 4.5:1 on every paper surface. */
    public static final int FAINT = 0xFF5C5C5C;

    /** Focus and selected rules. Pure ink, deliberately not a hue. */
    public static final int ACCENT = 0xFF000000;

    // ---------------------------------------------------------------- controls

    /** A selected row. */
    public static final int SELECTED = 0xFFD4D4D4;
    /** A hovered row. */
    public static final int HOVER = 0xFFE4E4E4;
    /** A disabled control fill. */
    public static final int DISABLED = 0xFFDEDEDE;
    /** The list scrollbar thumb only. */
    public static final int SCROLLBAR = 0xFF8C8C8C;
    /** Text selection band in a field: a twenty-percent ink wash over {@link #FIELD}. */
    public static final int SELECTION_TINT = 0x33000000;

    // ----------------------------------------------------------------- header

    /** The ink header band. */
    public static final int HEADER = 0xFF121212;
    /** The header headline. */
    public static final int HEADER_TEXT = 0xFFF7F7F7;
    /** Header secondary text. */
    public static final int HEADER_MUTED = 0xFFBDBDBD;
    /** The official stamp border and text. */
    public static final int STAMP = 0xFF3A3A3A;

    // ----------------------------------------------------------------- status

    /** Monochrome "clear". */
    public static final int POSITIVE = 0xFF303030;
    /** Monochrome "attention". */
    public static final int WARN = 0xFF555555;
    /** Monochrome "restriction". */
    public static final int DANGER = 0xFF000000;

    /** The fixed advance used to give ids a monospaced look in the normal font. */
    public static final int MONO_CELL = 7;

    private GovTheme() {
    }

    // ---------------------------------------------------------------- primitives

    /** Draws a one-pixel border inside the given rectangle. */
    public static void border(
            final GuiGraphics graphics, final int x, final int y, final int width, final int height,
            final int color) {
        if (width <= 0 || height <= 0) {
            return;
        }
        if (width < 2 || height < 2) {
            graphics.fill(x, y, x + width, y + height, color);
            return;
        }
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y, x + 1, y + height, color);
        graphics.fill(x + width - 1, y, x + width, y + height, color);
    }

    /** Draws a one-pixel border inside a rectangle. */
    public static void border(final GuiGraphics graphics, final GovRect rect, final int color) {
        if (rect == null) {
            return;
        }
        border(graphics, rect.x(), rect.y(), rect.w(), rect.h(), color);
    }

    /**
     * Draws a vertical scrollbar when there is something to scroll.
     *
     * <p>Kept as a source-compatible facade over {@link GovWidgets#scrollbar(GuiGraphics, GovRect,
     * int, int)} because the existing panels call this signature. New code should pass a
     * {@link GovRect}.
     */
    public static void scrollbar(
            final GuiGraphics graphics, final int x, final int y, final int height,
            final int scroll, final int maxScroll) {
        GovWidgets.scrollbar(graphics, x, y, 2, height, scroll, maxScroll);
    }

    /** Draws a scrollbar inside a rectangle. */
    public static void scrollbar(
            final GuiGraphics graphics, final GovRect rect, final int scroll,
            final int maxScroll) {
        if (rect == null) {
            return;
        }
        scrollbar(graphics, rect.x(), rect.y(), rect.h(), scroll, maxScroll);
    }

    /** Draws a short id with a fixed advance so digits and dots line up. */
    public static void monoId(
            final GuiGraphics graphics, final Font font, final String id, final int x, final int y,
            final int color) {
        final String safe = GovText.sanitize(id);
        for (int i = 0; i < safe.length(); i++) {
            final String character = String.valueOf(safe.charAt(i));
            final int characterWidth = font.width(character);
            graphics.drawString(font, character,
                    x + i * MONO_CELL + (MONO_CELL - characterWidth) / 2, y, color, false);
        }
    }

    /** True when a point is inside a rectangle. */
    public static boolean inside(
            final double mouseX, final double mouseY, final int x, final int y,
            final int width, final int height) {
        return width > 0 && height > 0
                && mouseX >= x && mouseX < x + width
                && mouseY >= y && mouseY < y + height;
    }

    /** True when a point is inside a rectangle. */
    public static boolean inside(
            final double mouseX, final double mouseY, final GovRect rect) {
        return rect != null && rect.contains(mouseX, mouseY);
    }
}
