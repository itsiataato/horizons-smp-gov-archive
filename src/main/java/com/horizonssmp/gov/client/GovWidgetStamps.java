package com.horizonssmp.gov.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public final class GovWidgetStamps {

    private GovWidgetStamps() {
    }

    public static void stamp(
            final GuiGraphics graphics, final Font font, final GovRect rect, final String text,
            final boolean strong) {
        if (graphics == null || !GovWidgets.hasArea(rect)) {
            return;
        }
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), GovTheme.PANEL);
        GovTheme.border(graphics, rect, GovTheme.STAMP);
        if (strong) {
            final GovRect inner = rect.inset(1);
            if (GovWidgets.hasArea(inner)) {
                GovTheme.border(graphics, inner, GovTheme.STAMP);
            }
        }
        if (font != null && text != null && !text.isEmpty() && rect.h() >= font.lineHeight) {
            final int innerW = rect.w() - GovMetrics.GAP_XS * 2;
            final int textY = rect.y() + Math.max(0, (rect.h() - font.lineHeight) / 2);
            if (innerW > 0) {
                GovText.centered(graphics, font, text, rect.x() + GovMetrics.GAP_XS, textY,
                        innerW, GovTheme.STAMP);
            }
        }
    }

    public static void stamp(
            final GuiGraphics graphics, final Font font, final GovRect rect, final Component text,
            final boolean strong) {
        stamp(graphics, font, rect, text == null ? null : text.getString(), strong);
    }
}
