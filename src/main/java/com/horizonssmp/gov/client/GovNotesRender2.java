package com.horizonssmp.gov.client;

import com.horizonssmp.gov.client.GovNotesTypes.*;
import static com.horizonssmp.gov.client.GovNotesTypes.*;

import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

class GovNotesRender2 extends GovNotesRender {
    GovNotesRender2(final GovDashboardScreen host) {
        super(host);
    }

    protected void drawScopePicker(final GuiGraphics graphics, final int mouseX,
            final int mouseY) {
        if (this.mainRect.w() <= 0 || this.mainRect.h() <= 0) {
            return;
        }
        graphics.fill(this.mainRect.x(), this.mainRect.y(), this.mainRect.right(),
                this.mainRect.bottom(), GovTheme.FIELD);
        GovTheme.border(graphics, this.mainRect, GovTheme.BORDER);
        if (this.pickerHits.isEmpty()) {
            if (this.scopesState == Load.ERROR) {
                drawScopeErrorPane(graphics, mouseX, mouseY);
                return;
            }
            GovText.centered(graphics, this.font,
                    this.scopesState == Load.LOADING ? this.loadingText
                            : this.mode == 1 ? this.noJobText : this.noBizText,
                    this.mainRect.x() + 4,
                    this.mainRect.y() + Math.max(0, (this.mainRect.h() - 9) / 2),
                    Math.max(0, this.mainRect.w() - 8), GovTheme.MUTED);
            return;
        }
        for (final ScopeHit hit : this.pickerHits) {
            final GovRect rect = hit.rect();
            final ScopeRow scope = hit.scope();
            final boolean selected = this.mode == 1
                    ? scope.id().equals(this.selectedJobId)
                    : scope.id().equals(this.selectedBizId);
            final boolean hovered = rect.contains(mouseX, mouseY);
            if (selected) {
                graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(),
                        GovTheme.SELECTED);
                graphics.fill(rect.x(), rect.y(), rect.x() + 3, rect.bottom(),
                        GovTheme.ACCENT);
            } else if (hovered) {
                graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(),
                        GovTheme.HOVER);
            }
            final int textX = rect.x() + 6;
            final int textY = rect.y() + Math.max(0, (rect.h() - 9) / 2);
            int roleW = 0;
            if (rect.w() >= 160 && !scope.role().isEmpty() && this.font != null) {
                roleW = Math.min(rect.w() / 3, this.font.width(scope.role()) + 4);
            }
            final int textW = Math.max(0, rect.w() - 10 - roleW);
            if (textW > 0 && rect.h() >= 9) {
                if (selected) {
                    GovText.clipped(graphics, this.font, scope.boldLabel(), textX, textY,
                            textW, GovTheme.TEXT);
                } else {
                    GovText.clipped(graphics, this.font, scope.label(), textX, textY,
                            textW, hovered ? GovTheme.BODY : GovTheme.MUTED);
                }
            }
            if (roleW > 0) {
                GovText.clipped(graphics, this.font, scope.role(),
                        rect.right() - roleW, textY, roleW, GovTheme.FAINT);
            }
            GovWidgets.rule(graphics, rect.x(), rect.bottom() - 1, rect.w(),
                    GovTheme.BORDER_SOFT);
        }
    }

    protected void drawScopeErrorPane(final GuiGraphics graphics, final int mouseX,
            final int mouseY) {
        if (this.mainRect.w() <= 0 || this.mainRect.h() <= 0) {
            return;
        }
        final int width = Math.max(0, this.mainRect.w() - 8);
        final int titleY = this.mainRect.y() + 4;
        final int stop = this.scopeRetryRect.h() > 0
                ? this.scopeRetryRect.y() - 2 : this.mainRect.bottom();
        if (stop < titleY + 9) {
            return;
        }
        final int titleW = Math.min(width, this.font.width(this.errorTitleBold));
        GovText.clipped(graphics, this.font, this.errorTitleBold,
                this.mainRect.x() + 4 + Math.max(0, (width - titleW) / 2), titleY, width,
                GovTheme.TEXT);
        int y = titleY + 12;
        for (final FormattedCharSequence line : this.scopesErrorLines) {
            if (y + 9 > stop) {
                break;
            }
            final int lineW = this.font.width(line);
            graphics.drawString(this.font, line,
                    this.mainRect.x() + 4 + Math.max(0, (width - lineW) / 2), y,
                    GovTheme.MUTED, false);
            y += 9;
        }
        if (this.scopeRetryRect.w() > 0 && this.scopeRetryRect.h() > 0) {
            drawButton(graphics, this.scopeRetryRect, this.retryText, true,
                    this.scopeRetryRect.contains(mouseX, mouseY), false);
        }
    }

}
