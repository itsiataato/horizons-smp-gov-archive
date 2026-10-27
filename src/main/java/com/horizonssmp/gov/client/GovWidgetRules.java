package com.horizonssmp.gov.client;

import net.minecraft.client.gui.GuiGraphics;

public final class GovWidgetRules {

    private GovWidgetRules() {
    }

    public static void rule(
            final GuiGraphics graphics, final int x, final int y, final int width,
            final int color) {
        if (graphics == null || width <= 0) {
            return;
        }
        graphics.fill(x, y, x + width, y + 1, color);
    }

    public static void rule(final GuiGraphics graphics, final GovRect rect) {
        rule(graphics, rect, GovTheme.BORDER);
    }

    public static void rule(final GuiGraphics graphics, final GovRect rect, final int color) {
        if (rect == null || rect.h() <= 0) {
            return;
        }
        rule(graphics, rect.x(), rect.y(), rect.w(), color);
    }

    public static void doubleRule(final GuiGraphics graphics, final GovRect rect) {
        if (graphics == null || !GovWidgets.hasArea(rect)) {
            return;
        }
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.y() + 1, GovTheme.BORDER);
        if (rect.h() >= 3) {
            graphics.fill(rect.x(), rect.y() + 2, rect.right(), rect.y() + 3,
                    GovTheme.BORDER_SOFT);
        } else if (rect.h() == 2) {
            graphics.fill(rect.x(), rect.y() + 1, rect.right(), rect.y() + 2,
                    GovTheme.BORDER_SOFT);
        }
    }

    public static void doubleRule(
            final GuiGraphics graphics, final int x, final int y, final int width) {
        if (graphics == null || width <= 0) {
            return;
        }
        graphics.fill(x, y, x + width, y + 1, GovTheme.BORDER);
        graphics.fill(x, y + 2, x + width, y + 3, GovTheme.BORDER_SOFT);
    }
}
