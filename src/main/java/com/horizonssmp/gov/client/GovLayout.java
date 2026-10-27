package com.horizonssmp.gov.client;

/**
 * The single computed layout for the Gov shell.
 *
 * <p>The shell computes this once in {@code init()} / on resize and hands the resulting rectangles
 * to the panels. No render path recomputes a breakpoint or reads {@code Screen.width}. Every
 * rectangle is derived from the panel and is clipped to its container, so a short window produces
 * small rectangles rather than negative ones.
 *
 * @param panel the paper panel, inset by the breakpoint margin
 * @param header the ink header band at the top of the panel
 * @param rail the section rail on the left of the body
 * @param tabs the sub-category strip at the top of the content area
 * @param content the panel content below the tab strip
 * @param footer the footer band at the bottom of the panel
 * @param breakpoint the width class used to choose the metrics
 * @param railWidth the actual rail width after clamping to the body
 * @param tabHeight the actual tab height after clamping to the content area
 * @param rowHeight the standard list row pitch for this breakpoint
 * @param gutter the standard gap between panes
 * @param twoColumnRail true when the rail is split into two icon columns
 */
public record GovLayout(
        GovRect panel,
        GovRect header,
        GovRect rail,
        GovRect tabs,
        GovRect content,
        GovRect footer,
        Breakpoint breakpoint,
        int railWidth,
        int tabHeight,
        int rowHeight,
        int gutter,
        boolean twoColumnRail) {

    /** The logical width classes from the GUI specification. */
    public enum Breakpoint {
        COMPACT,
        MEDIUM,
        WIDE
    }

    /**
     * Derives the shell layout for a logical screen size.
     *
     * <p>Breakpoints are measured on logical width: WIDE at 700 or more, MEDIUM from 480 to 699,
     * COMPACT below 480. The vertical bands are taken from the panel in order (header, body,
     * footer), then the body is split into the rail and the content column. Every subtraction is
     * clamped at zero, so even a screen smaller than two margins still produces valid rectangles.
     */
    public static GovLayout of(final int screenW, final int screenH) {
        final int screenWidth = Math.max(0, screenW);
        final int screenHeight = Math.max(0, screenH);
        final Breakpoint breakpoint = breakpointFor(screenWidth);

        final int margin = switch (breakpoint) {
            case WIDE -> GovMetrics.MARGIN_WIDE;
            case MEDIUM -> GovMetrics.MARGIN_MEDIUM;
            case COMPACT -> GovMetrics.MARGIN_COMPACT;
        };
        final int headerH = breakpoint == Breakpoint.COMPACT
                ? GovMetrics.HEADER_H_COMPACT : GovMetrics.HEADER_H;
        final int footerH = breakpoint == Breakpoint.COMPACT
                ? GovMetrics.FOOTER_H_COMPACT : GovMetrics.FOOTER_H;
        final int tabH = breakpoint == Breakpoint.COMPACT
                ? GovMetrics.TAB_H_COMPACT : GovMetrics.TAB_H;
        final int railW = switch (breakpoint) {
            case WIDE -> GovMetrics.RAIL_W_WIDE;
            case MEDIUM -> GovMetrics.RAIL_W_MEDIUM;
            case COMPACT -> GovMetrics.RAIL_W_COMPACT;
        };
        final int gutter = switch (breakpoint) {
            case WIDE -> GovMetrics.GAP;
            case MEDIUM -> GovMetrics.GAP_S;
            case COMPACT -> GovMetrics.GAP_XS;
        };

        // Keep the origin on-screen even when the screen is narrower than two margins.
        final int panelX = Math.min(margin, screenWidth);
        final int panelY = Math.min(margin, screenHeight);
        final int panelW = Math.max(0, screenWidth - panelX * 2);
        final int panelH = Math.max(0, screenHeight - panelY * 2);
        final GovRect panel = new GovRect(panelX, panelY, panelW, panelH);

        // The header and footer never take more than the panel has. A short panel therefore keeps
        // a zero-height body instead of producing a negative one.
        final int actualHeaderH = Math.min(headerH, panel.h());
        final int actualFooterH = Math.min(footerH, Math.max(0, panel.h() - actualHeaderH));
        final int bodyH = Math.max(0, panel.h() - actualHeaderH - actualFooterH);
        final GovRect header = new GovRect(panel.x(), panel.y(), panel.w(), actualHeaderH);
        final GovRect footer = new GovRect(
                panel.x(), panel.bottom() - actualFooterH, panel.w(), actualFooterH);
        final GovRect body = new GovRect(
                panel.x(), panel.y() + actualHeaderH, panel.w(), bodyH);

        // The rail is clamped to the body width so the content column is never negative.
        final int actualRailW = Math.min(railW, body.w());
        final GovRect rail = body.withWidth(actualRailW);
        final GovRect bodyRight = new GovRect(
                body.x() + actualRailW, body.y(), body.w() - actualRailW, body.h());

        final int actualTabH = Math.min(tabH, bodyRight.h());
        final GovRect tabs = bodyRight.withHeight(actualTabH);
        final GovRect content = new GovRect(
                bodyRight.x(), bodyRight.y() + actualTabH, bodyRight.w(),
                bodyRight.h() - actualTabH);

        return new GovLayout(panel, header, rail, tabs, content, footer, breakpoint,
                actualRailW, actualTabH, GovMetrics.rowHeight(breakpoint), gutter,
                GovMetrics.twoColumnRail(bodyH));
    }

    private static Breakpoint breakpointFor(final int width) {
        if (width >= 700) {
            return Breakpoint.WIDE;
        }
        if (width >= 480) {
            return Breakpoint.MEDIUM;
        }
        return Breakpoint.COMPACT;
    }
}
