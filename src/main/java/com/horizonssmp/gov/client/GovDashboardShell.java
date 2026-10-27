package com.horizonssmp.gov.client;

import net.minecraft.client.gui.GuiGraphics;

final class GovDashboardShell {
    private GovDashboardShell() {}

    static void paint(final GovDashboardScreen s, final GuiGraphics graphics, final int mouseX, final int mouseY) {
        final GovRect panel = s.layout.panel();
        final GovRect header = s.layout.header();
        final GovRect rail = s.layout.rail();
        final GovRect tabs = s.layout.tabs();
        final GovRect content = s.layout.content();
        final GovRect footer = s.layout.footer();

        graphics.fill(0, 0, s.width, s.height, GovTheme.OVERLAY);
        graphics.fill(panel.x(), panel.y(), panel.right(), panel.bottom(), GovTheme.PANEL);
        graphics.fill(header.x(), header.y(), header.right(), header.bottom(), GovTheme.HEADER);
        // The last two rows of the header rect are the paper gutter that carries the double
        // rule, so the strong rule is ink on paper rather than ink on ink.
        graphics.fill(header.x(), Math.max(header.y(), header.bottom() - 2),
                header.right(), header.bottom(), GovTheme.PANEL);
        graphics.fill(rail.x(), rail.y(), rail.right(), rail.bottom(), GovTheme.SIDEBAR);
        graphics.fill(tabs.x(), tabs.y(), tabs.right(), tabs.bottom(), GovTheme.PANEL_ALT);
        graphics.fill(content.x(), content.y(), content.right(), content.bottom(), GovTheme.FIELD);

        GovDashboardRender.drawHeader((GovDashboardScreen) s, graphics, mouseX, mouseY);
        GovDashboardRender.drawRail((GovDashboardScreen) s, graphics, mouseX, mouseY);
        // Hairline between the rail and the document column.
        if (rail.w() > 0) {
            graphics.fill(rail.right() - 1, rail.y(), rail.right(), rail.bottom(),
                    GovTheme.BORDER_SOFT);
        }
        GovDashboardRender.drawSubtabs((GovDashboardScreen) s, graphics, mouseX, mouseY);

        final boolean scissored = content.w() > 2 && content.h() > 2;
        if (scissored) {
            graphics.enableScissor(content.x() + 1, content.y() + 1,
                    content.right() - 1, content.bottom() - 1);
        }
        try {
            GovDashboardRender.drawContent((GovDashboardScreen) s, graphics, mouseX, mouseY);
        } finally {
            if (scissored) {
                graphics.disableScissor();
            }
        }

        GovDashboardRender.drawFooter((GovDashboardScreen) s, graphics);
        GovTheme.border(graphics, panel, GovTheme.BORDER);

    }

    static void tooltip(final GovDashboardScreen s, final GuiGraphics graphics, final int mouseX, final int mouseY) {
        if (s.hoveredRail >= 0 && s.hoveredRail < s.visibleTabs.size()) {
            final int tabIndex = s.visibleTabs.get(s.hoveredRail).ordinal();
            GovWidgets.tooltip(graphics, s.font(), mouseX + 10, mouseY + 10,
                    s.sectionNames[tabIndex], s.openText,
                    s.width, s.height);
        }
    }
}
