package com.horizonssmp.gov.client;

/**
 * The Gov metric tokens.
 *
 * <p>This is the only class that is allowed to name layout geometry. Every shell and panel reads
 * these values (or {@link GovLayout}) instead of inventing its own margins, row heights and gaps.
 * Keeping the numbers in one place is what lets the interface scale from 320x180 to 1080p without
 * a second layout vocabulary drifting into the panels.
 */
public final class GovMetrics {

    // ---------------------------------------------------------------- margins
    public static final int MARGIN_WIDE = 16;
    public static final int MARGIN_MEDIUM = 10;
    public static final int MARGIN_COMPACT = 6;

    // ------------------------------------------------------------ shell chrome
    public static final int HEADER_H = 36;
    public static final int HEADER_H_COMPACT = 30;
    public static final int FOOTER_H = 18;
    public static final int FOOTER_H_COMPACT = 16;

    // ------------------------------------------------------------ navigation
    public static final int TAB_H = 18;
    public static final int TAB_H_COMPACT = 16;
    public static final int RAIL_W_WIDE = 132;
    public static final int RAIL_W_MEDIUM = 64;
    public static final int RAIL_W_COMPACT = 58;

    // ----------------------------------------------------------------- rows
    public static final int ROW_H = 18;
    public static final int ROW_H_COMPACT = 16;
    public static final int FIELD_H = 16;
    public static final int BUTTON_H = 16;

    // --------------------------------------------------------------- spacing
    public static final int GAP = 12;
    public static final int GAP_S = 8;
    public static final int GAP_XS = 4;

    // ----------------------------------------------------------------- type
    public static final int LINE_BODY = 10;
    public static final int LINE_TITLE = 12;
    public static final int PAGE_PAD = 10;

    /** The smallest scrollbar thumb: a thumb shorter than this is not grabbable. */
    static final int SCROLLBAR_MIN_THUMB = 14;

    private GovMetrics() {
    }

    /**
     * Fits a design-sized modal inside a container.
     *
     * <p>The result is never larger than {@code container - 16} on either axis and is centred in
     * the container. Both maximums are clamped at zero, so a container smaller than the inset
     * yields an empty rectangle rather than a negative one. This deliberately uses
     * {@code min(design, available)} and never {@code max(minimum, available)}.
     */
    public static GovRect fit(final GovRect container, final int designW, final int designH) {
        if (container == null) {
            return new GovRect(0, 0, 0, 0);
        }
        final int availableW = Math.max(0, container.w() - 16);
        final int availableH = Math.max(0, container.h() - 16);
        final int width = Math.min(Math.max(0, designW), availableW);
        final int height = Math.min(Math.max(0, designH), availableH);
        final int x = container.x() + (container.w() - width) / 2;
        final int y = container.y() + (container.h() - height) / 2;
        return new GovRect(x, y, width, height).clamp(container);
    }

    /** The standard row pitch for a breakpoint. */
    public static int rowHeight(final GovLayout.Breakpoint breakpoint) {
        return breakpoint == GovLayout.Breakpoint.COMPACT ? ROW_H_COMPACT : ROW_H;
    }

    /**
     * Clamps a value into an inclusive range.
     *
     * <p>When the range is empty, the lower bound wins. That is the safe choice for geometry:
     * an accidentally inverted range produces the smallest legal value instead of a value below
     * the container.
     */
    public static int clamp(final int value, final int min, final int max) {
        if (min > max) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Chooses a sub-span of {@code available} that honours a preferred width and its bounds.
     *
     * <p>This is the width-safe replacement for {@code Math.min(max, Math.max(min, preferred))}.
     * The old shape can return {@code min} even when the container is narrower than that minimum,
     * which is exactly the forbidden {@code max(minimum, available)} pattern. Here the available
     * space always wins: when it is smaller than {@code min}, the result is the available width.
     * A non-positive available width or a negative bound collapses the span to zero.
     */
    public static int span(final int available, final int preferred, final int min, final int max) {
        if (available <= 0) {
            return 0;
        }
        final int limit = Math.min(Math.max(0, max), available);
        final int floor = Math.min(Math.max(0, min), limit);
        return Math.max(floor, Math.min(limit, preferred));
    }

    /** True when the body is too short for the labelled rail. */
    static boolean twoColumnRail(final int bodyHeight) {
        return bodyHeight < 160;
    }
}
