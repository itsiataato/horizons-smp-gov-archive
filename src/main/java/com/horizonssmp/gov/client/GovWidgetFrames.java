package com.horizonssmp.gov.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

public final class GovWidgetFrames {

    private GovWidgetFrames() {
    }

    public static void fieldFrame(
            final GuiGraphics graphics, final GovRect rect, final boolean focused) {
        if (graphics == null || !GovWidgets.hasArea(rect)) {
            return;
        }
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), GovTheme.FIELD);
        GovTheme.border(graphics, rect, focused ? GovTheme.ACCENT : GovTheme.BORDER_SOFT);
    }

    public static EditBox field(
            final Font font, final int x, final int y, final int width, final int height,
            final Component hint) {
        final GovThemedEditBox box = new GovThemedEditBox(font, x, y, width, height, hint);
        box.setBordered(false);
        box.setTextShadow(false);
        box.setTextColor(GovTheme.TEXT);
        box.setTextColorUneditable(GovTheme.MUTED);
        return box;
    }

    public static void modalBackdrop(final GuiGraphics graphics, final GovRect content) {
        if (graphics == null || !GovWidgets.hasArea(content)) {
            return;
        }
        graphics.fill(content.x(), content.y(), content.right(), content.bottom(),
                GovTheme.OVERLAY);
    }

    public static void modalFrame(
            final GuiGraphics graphics, final Font font, final GovRect rect, final String title) {
        if (graphics == null || !GovWidgets.hasArea(rect)) {
            return;
        }
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), GovTheme.PANEL);
        GovTheme.border(graphics, rect, GovTheme.BORDER);
        final int headerH = Math.min(rect.h(),
                GovMetrics.LINE_TITLE + GovMetrics.GAP_XS * 2);
        if (headerH > 0) {
            graphics.fill(rect.x(), rect.y(), rect.right(), rect.y() + headerH,
                    GovTheme.PANEL_ALT);
            graphics.fill(rect.x(), rect.y() + headerH - 1, rect.right(), rect.y() + headerH,
                    GovTheme.BORDER);
        }
        if (font != null && title != null && !title.isEmpty() && headerH >= font.lineHeight) {
            final int innerW = rect.w() - GovMetrics.GAP_S * 2;
            final int textY = rect.y() + Math.max(0, (headerH - font.lineHeight) / 2);
            if (innerW > 0) {
                GovText.clipped(graphics, font, GovText.boldCached(title),
                        rect.x() + GovMetrics.GAP_S, textY, innerW, GovTheme.TEXT);
            }
        }
    }

    public static void modalFrame(
            final GuiGraphics graphics, final Font font, final GovRect rect,
            final Component title) {
        modalFrame(graphics, font, rect, title == null ? null : title.getString());
    }
}
