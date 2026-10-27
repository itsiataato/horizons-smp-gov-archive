package com.horizonssmp.gov.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public final class GovWidgetButtons {

    private GovWidgetButtons() {
    }

    public static void button(
            final GuiGraphics graphics, final Font font, final GovRect rect, final String label,
            final boolean enabled, final boolean hovered, final boolean primary) {
        if (graphics == null || !GovWidgets.hasArea(rect)) {
            return;
        }
        final int fill;
        final int textColor;
        if (!enabled) {
            fill = GovTheme.DISABLED;
            textColor = GovTheme.FAINT;
        } else if (hovered) {
            fill = GovTheme.SELECTED;
            textColor = GovTheme.TEXT;
        } else if (primary) {
            fill = GovTheme.BORDER;
            textColor = GovTheme.HEADER_TEXT;
        } else {
            fill = GovTheme.PANEL;
            textColor = GovTheme.TEXT;
        }
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), fill);
        GovTheme.border(graphics, rect, enabled ? GovTheme.BORDER : GovTheme.BORDER_SOFT);
        if (font != null && label != null && !label.isEmpty() && rect.h() >= font.lineHeight) {
            final int innerX = rect.x() + GovMetrics.GAP_XS;
            final int innerW = rect.w() - GovMetrics.GAP_XS * 2;
            final int textY = rect.y() + Math.max(0, (rect.h() - font.lineHeight) / 2);
            if (innerW > 0) {
                GovText.centered(graphics, font, label, innerX, textY, innerW, textColor);
            }
        }
    }

    public static void button(
            final GuiGraphics graphics, final Font font, final GovRect rect, final Component label,
            final boolean enabled, final boolean hovered, final boolean primary) {
        button(graphics, font, rect, label == null ? null : label.getString(), enabled, hovered,
                primary);
    }

    public static void iconButton(
            final GuiGraphics graphics, final GovRect rect, final GovIcons.GovIcon icon,
            final boolean enabled, final boolean hovered) {
        if (graphics == null || !GovWidgets.hasArea(rect)) {
            return;
        }
        final int fill = !enabled ? GovTheme.DISABLED
                : hovered ? GovTheme.SELECTED : GovTheme.PANEL;
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), fill);
        GovTheme.border(graphics, rect, enabled ? GovTheme.BORDER : GovTheme.BORDER_SOFT);
        final int size = Math.min(GovMetrics.BUTTON_H,
                Math.min(rect.w() - GovMetrics.GAP_XS * 2,
                        rect.h() - GovMetrics.GAP_XS * 2));
        if (size <= 0) {
            return;
        }
        final int iconX = rect.x() + (rect.w() - size) / 2;
        final int iconY = rect.y() + (rect.h() - size) / 2;
        if (enabled) {
            GovIcons.draw(graphics, icon, iconX, iconY, size);
        } else {
            GovIcons.drawDisabled(graphics, icon, iconX, iconY, size);
        }
    }
}
