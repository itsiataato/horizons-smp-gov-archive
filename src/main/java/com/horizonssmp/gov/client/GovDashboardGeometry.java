package com.horizonssmp.gov.client;

import net.minecraft.client.gui.Font;

final class GovDashboardGeometry {
    private GovDashboardGeometry() {}

    static void layoutCardGeometry(final GovDashboardScreen s) {
        final GovRect content = s.layout.content();
        s.cardRect = GovMetrics.fit(content, 620, 260);
        final boolean compact = s.layout.breakpoint() == GovLayout.Breakpoint.COMPACT;
        s.cardPad = compact ? 8 : 10;
        s.cardHeaderH = compact ? 24 : 28;
        s.cardRowsTop = Math.min(s.cardRect.bottom(),
                s.cardRect.y() + s.cardHeaderH + 4);
        final int available = Math.max(0, s.cardRect.bottom() - 1 - s.cardRowsTop);
        int pitch = GovMetrics.clamp(available / 7, 11, 22);
        if (available < pitch) {
            pitch = Math.max(1, available);
        }
        s.cardRowPitch = pitch;
        s.cardMaxRows = GovFit.rowsThatFit(available, pitch, 7);

        final int buttonW = compact ? 52 : 58;
        final int buttonH = GovMetrics.BUTTON_H;
        final int buttonY = s.cardRect.y() + (compact ? 10 : 13);
        s.refreshRect = new GovRect(s.cardRect.right() - s.cardPad - buttonW,
                buttonY, buttonW, buttonH).clamp(s.cardRect);
        s.retryRect = new GovRect(s.cardRect.right() - s.cardPad - buttonW,
                s.cardRect.bottom() - 2 - buttonH, buttonW, buttonH).clamp(s.cardRect);
    }

    static void buildHeaderGeometry(final GovDashboardScreen s) {
        final GovRect header = s.layout.header();
        final boolean compact = s.layout.breakpoint() == GovLayout.Breakpoint.COMPACT;
        final boolean medium = s.layout.breakpoint() == GovLayout.Breakpoint.MEDIUM;
        final int pad = compact ? 6 : medium ? 10 : 14;
        final int sealSize = 18;
        s.sealRect = new GovRect(header.x() + pad,
                header.y() + (header.h() - sealSize) / 2, sealSize, sealSize).clamp(header);
        s.titleX = s.sealRect.right() + 7;
        s.headerBlockY = header.y() + (header.h() - 18) / 2;

        s.closeRect = new GovRect(header.right() - pad - 24,
                header.y() + (header.h() - 24) / 2, 24, 24).clamp(header);

        int right = s.closeRect.x() - 6;
        if (!compact) {
            final int stampW = s.font().width(s.officialText) + 10;
            s.stampRect = new GovRect(right - stampW,
                    header.y() + (header.h() - 16) / 2, stampW, 16).clamp(header);
            right = s.stampRect.x() - 8;
        } else {
            s.stampRect = GovDashboardScreen.EMPTY;
        }

        s.seasonWidth = s.font().width(s.seasonText);
        int seasonStart = right - s.seasonWidth;
        final int titleMinimum = s.font().width(s.agencyLine) + 4;
        if (seasonStart - s.titleX < titleMinimum) {
            s.seasonX = -1;
        } else {
            s.seasonX = seasonStart;
            right = seasonStart - 6;
        }
        s.titleWidth = Math.max(0, right - s.titleX - 4);
    }

    static void buildRailGeometry(final GovDashboardScreen s) {
        java.util.Arrays.fill(s.railRects, GovDashboardScreen.EMPTY);
        final GovRect rail = s.layout.rail();
        s.railTwoColumn = s.layout.twoColumnRail();
        s.railStripHeight = Math.min(rail.h(),
                GovMetrics.clamp(rail.h() / 5, 16, 46));
        s.railStripTop = rail.bottom() - s.railStripHeight;

        final int rowsTop = rail.y() + 2;
        final int rowsBottom = Math.max(rowsTop, s.railStripTop - 2);
        final int visibleCount = Math.max(1, s.visibleTabs.size());
        final int rowCount = s.railTwoColumn
                ? (visibleCount + 1) / 2 : visibleCount;
        final int area = Math.max(0, rowsBottom - rowsTop);
        s.railPitch = rowCount > 0 ? Math.max(1, area / rowCount) : 1;
        s.railRowHeight = Math.max(1, s.railPitch - 1);

        java.util.Arrays.fill(s.railRects, GovDashboardScreen.EMPTY);
        for (int index = 0; index < visibleCount; index++) {
            final int column = s.railTwoColumn ? index / rowCount : 0;
            final int row = s.railTwoColumn ? index % rowCount : index;
            final int columnWidth = s.railTwoColumn ? rail.w() / 2 : rail.w();
            final int x = rail.x() + column * columnWidth;
            final int y = rowsTop + row * s.railPitch;
            s.railRects[index] = new GovRect(x + 2, y,
                    Math.max(1, columnWidth - 4), s.railRowHeight).clamp(rail);
        }
    }

    static void rebuildSubtabRects(final GovDashboardScreen s) {
        s.subtabPrevRect = GovDashboardScreen.EMPTY;
        s.subtabNextRect = GovDashboardScreen.EMPTY;
        s.subtabOverflow = false;
        s.subtabPrevEnabled = false;
        s.subtabNextEnabled = false;
        if (s.layout == null || s.font() == null) {
            s.subtabRects = new GovRect[0];
            s.subtabScroll = 0;
            return;
        }
        final GovRect strip = s.layout.tabs();
        final java.util.List<Integer> visible = s.visibleSubtabs[s.activeTab.ordinal()];
        final int count = visible.size();
        final GovRect[] rects = new GovRect[count];
        java.util.Arrays.fill(rects, GovDashboardScreen.EMPTY);
        if (count == 0 || strip.w() <= 0 || strip.h() <= 0) {
            s.subtabRects = rects;
            s.subtabScroll = 0;
            return;
        }

        final net.minecraft.network.chat.Component[] bold = s.subtabBold[s.activeTab.ordinal()];
        final int[] widths = new int[count];
        int total = 0;
        for (int slot = 0; slot < count; slot++) {
            final int original = visible.get(slot);
            widths[slot] = Math.max(18,
                    s.font().width(bold[original]) + GovMetrics.GAP_S * 2);
            total += widths[slot];
        }

        final int arrowW = GovMetrics.clamp(strip.h(), 10, 14);
        final int viewWidth = strip.w() - arrowW * 2;
        if (total <= strip.w() || viewWidth <= 0 || count == 1) {
            s.subtabScroll = 0;
            final int spare = Math.max(0, strip.w() - total);
            final int base = spare / count;
            final int remainder = spare % count;
            int x = strip.x();
            for (int index = 0; index < count; index++) {
                final int width = widths[index] + base + (index < remainder ? 1 : 0);
                rects[index] = new GovRect(x, strip.y(), width, strip.h()).clamp(strip);
                x += width;
            }
            s.subtabRects = rects;
            return;
        }

        s.subtabOverflow = true;
        final int selected = GovMetrics.clamp(s.subtabs[s.activeTab.ordinal()], 0, count - 1);
        int start = GovMetrics.clamp(s.subtabScroll, 0, count - 1);
        if (start > selected) {
            start = selected;
        }
        while (start < selected) {
            int span = 0;
            for (int index = start; index <= selected; index++) {
                span += widths[index];
            }
            if (span <= viewWidth) {
                break;
            }
            start++;
        }
        s.subtabScroll = start;

        final int viewLeft = strip.x() + arrowW;
        final int viewEnd = viewLeft + viewWidth;
        int x = viewLeft;
        int last = start - 1;
        for (int index = start; index < count; index++) {
            if (x + widths[index] > viewEnd) {
                break;
            }
            rects[index] = new GovRect(x, strip.y(), widths[index], strip.h()).clamp(strip);
            x += widths[index];
            last = index;
        }
        if (last < start) {
            final int width = Math.min(widths[selected], viewWidth);
            rects[selected] = new GovRect(viewLeft, strip.y(), width, strip.h()).clamp(strip);
            last = selected;
        }

        s.subtabPrevRect = new GovRect(strip.x(), strip.y(), arrowW, strip.h()).clamp(strip);
        s.subtabNextRect = new GovRect(strip.right() - arrowW, strip.y(), arrowW, strip.h())
                .clamp(strip);
        s.subtabPrevEnabled = start > 0;
        s.subtabNextEnabled = last < count - 1;
        s.subtabRects = rects;
    }
}
