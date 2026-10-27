package com.horizonssmp.gov.client;

import com.horizonssmp.gov.client.GovNotesTypes.*;
import static com.horizonssmp.gov.client.GovNotesTypes.*;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

class GovNotesRenderBase extends GovNotesActions {
    GovNotesRenderBase(final GovDashboardScreen host) {
        super(host);
    }

    protected void drawLoading(final GuiGraphics graphics, final GovRect area) {
        if (area.w() <= 0 || area.h() <= 0) {
            return;
        }
        GovText.centered(graphics, this.font, this.loadingText, area.x() + 4,
                area.y() + Math.max(0, (area.h() - 9) / 2),
                Math.max(0, area.w() - 8), GovTheme.MUTED);
    }

    protected void drawEmpty(final GuiGraphics graphics, final GovRect area,
            final Component title) {
        if (area.w() <= 0 || area.h() <= 0) {
            return;
        }
        final int width = Math.max(0, area.w() - 8);
        final int titleW = Math.min(width, this.font.width(title));
        final int x = area.x() + 4 + Math.max(0, (width - titleW) / 2);
        final int y = area.y() + Math.max(0, (area.h() - 9) / 2);
        GovText.clipped(graphics, this.font, title, x, y, width, GovTheme.BODY);
    }

    protected void drawButton(final GuiGraphics graphics, final GovRect rect, final String label,
            final boolean enabled, final boolean hovered, final boolean primary) {
        if (rect == null || rect.w() <= 0 || rect.h() <= 0) {
            return;
        }
        drawButton(graphics, rect.x(), rect.y(), rect.w(), rect.h(), label, enabled, hovered,
                primary);
    }

    protected void drawButton(final GuiGraphics graphics, final int x, final int y,
            final int width, final int height, final String label, final boolean enabled,
            final boolean hovered, final boolean primary) {
        if (width <= 0 || height <= 0) {
            return;
        }
        final int fill = !enabled ? GovTheme.DISABLED
                : hovered ? GovTheme.SELECTED
                : primary ? GovTheme.BORDER : GovTheme.PANEL;
        final int text = !enabled ? GovTheme.FAINT
                : hovered ? GovTheme.TEXT
                : primary ? GovTheme.HEADER_TEXT : GovTheme.TEXT;
        graphics.fill(x, y, x + width, y + height, fill);
        GovTheme.border(graphics, x, y, width, height,
                enabled ? GovTheme.BORDER : GovTheme.BORDER_SOFT);
        if (this.font != null && label != null && !label.isEmpty() && height >= 9) {
            final int innerX = x + 2;
            final int innerW = width - 4;
            if (innerW > 0) {
                GovText.centered(graphics, this.font, label, innerX,
                        y + Math.max(0, (height - 9) / 2), innerW, text);
            }
        }
    }
}
