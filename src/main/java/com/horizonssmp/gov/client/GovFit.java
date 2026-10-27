package com.horizonssmp.gov.client;

/**
 * Element-level fit math for the Gov dashboard.
 *
 * <p>The shell stays at its normal size and each element fits inside the space it is
 * given: lists show only whole rows, and bold titles shrink until they fit. Pure static
 * integer math with no Minecraft reference, so the headless suites cover every branch.
 * Method names avoid the {@code render}/{@code draw} prefixes so the render-contract
 * scanner treats them as layout code.
 */
final class GovFit {
    private GovFit() {
    }

    /**
     * Whole rows of height {@code rowH} that fit in {@code available} pixels, capped at
     * {@code max}. Zero when any input is degenerate.
     */
    static int rowsThatFit(final int available, final int rowH, final int max) {
        if (available <= 0 || rowH <= 0 || max <= 0) {
            return 0;
        }
        return Math.min(available / rowH, max);
    }

    /** True when a row at {@code y} with height {@code h} lies wholly inside the bounds. */
    static boolean fitsWithin(final int y, final int h, final int top, final int bottom) {
        return y >= top && y + h <= bottom;
    }

    /**
     * Next clip width when a bold title of width {@code boldW} overflows {@code innerW}.
     * Steps down by the exact overflow plus one so the loop always terminates.
     */
    static int shrinkStep(final int fit, final int boldW, final int innerW) {
        return Math.max(0, fit - (boldW - innerW) - 1);
    }
}
