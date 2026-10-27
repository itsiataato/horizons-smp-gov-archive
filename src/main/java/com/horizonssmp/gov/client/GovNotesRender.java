package com.horizonssmp.gov.client;

import com.horizonssmp.gov.client.GovNotesTypes.*;
import static com.horizonssmp.gov.client.GovNotesTypes.*;

import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

class GovNotesRender extends GovNotesRenderBase {
    GovNotesRender(final GovDashboardScreen host) {
        super(host);
    }

    protected void drawHeader(final GuiGraphics graphics) {
        if (this.headerRect.w() <= 0 || this.headerRect.h() <= 0) {
            return;
        }
        final Component title = this.mode == 0 ? this.allTitleBold
                : this.mode == 1 ? this.jobTitleBold : this.bizTitleBold;
        final View view = currentView();
        final String count = view == null ? "" : view.countText;
        final int textY = this.headerRect.y()
                + Math.max(0, (this.headerRect.h() - 9) / 2);
        int countW = 0;
        if (!count.isEmpty() && this.font != null) {
            countW = Math.min(this.headerRect.w() / 2, this.font.width(count));
        }
        final int titleW = Math.max(0, this.headerRect.w() - countW - 6);
        GovText.clipped(graphics, this.font, title, this.headerRect.x(), textY, titleW,
                GovTheme.TEXT);
        if (countW > 0) {
            GovText.clipped(graphics, this.font, count,
                    this.headerRect.right() - countW, textY, countW, GovTheme.MUTED);
        }
        GovWidgets.rule(graphics, this.headerRect.x(),
                Math.min(this.bounds.bottom() - 1, this.headerRect.bottom()),
                this.headerRect.w(), GovTheme.BORDER_SOFT);
    }

    protected void drawScopeColumn(final GuiGraphics graphics, final int mouseX,
            final int mouseY) {
        if (this.scopeRect.w() <= 0 || this.scopeRect.h() <= 0) {
            return;
        }
        if (this.mode == 0) {
            GovText.sectionTitle(graphics, this.font, this.registerText, this.scopeHeaderRect);
            final int x = this.scopeRect.x() + 2;
            final int width = Math.max(0, this.scopeRect.w() - 4);
            int y = this.scopeHeaderRect.bottom() + 4;
            if (!this.allView.countText.isEmpty() && y + 9 <= this.scopeRect.bottom()) {
                GovText.clipped(graphics, this.font, this.allView.countText, x, y, width,
                        GovTheme.MUTED);
            }
            y += 11;
            if (!this.allView.pageText.isEmpty() && y + 9 <= this.scopeRect.bottom()) {
                GovText.clipped(graphics, this.font, this.allView.pageText, x, y, width,
                        GovTheme.MUTED);
                y += 11;
            }
            if (y + 9 <= this.scopeRect.bottom()) {
                GovText.clipped(graphics, this.font, this.newestFirstText, x, y, width,
                        GovTheme.FAINT);
                y += 11;
            }
            if (!this.allView.canPost && this.allView.state == Load.READY
                    && y + 9 <= this.scopeRect.bottom()) {
                GovText.clipped(graphics, this.font, this.readOnlyText, x, y, width,
                        GovTheme.FAINT);
            }
            return;
        }

        GovText.sectionTitle(graphics, this.font, this.scopesText, this.scopeHeaderRect);
        final int infoY = this.scopeHeaderRect.bottom() + 4;
        if (infoY + 9 > this.scopeRect.bottom()) {
            return;
        }
        if (this.scopesState == Load.LOADING) {
            GovText.clipped(graphics, this.font, this.loadingText, this.scopeRect.x() + 2,
                    infoY, Math.max(0, this.scopeRect.w() - 4), GovTheme.MUTED);
            return;
        }
        if (this.scopesState == Load.ERROR) {
            GovText.clipped(graphics, this.font, this.errorTitle, this.scopeRect.x() + 2,
                    infoY, Math.max(0, this.scopeRect.w() - 4), GovTheme.MUTED);
            final int width = Math.max(0, this.scopeRect.w() - 8);
            final int stop = this.scopeRetryRect.h() > 0
                    ? this.scopeRetryRect.y() - 2 : this.scopeRect.bottom();
            int y = infoY + 11;
            for (final FormattedCharSequence line : this.scopesErrorLines) {
                if (y + 9 > stop) {
                    break;
                }
                final int lineW = this.font.width(line);
                graphics.drawString(this.font, line,
                        this.scopeRect.x() + 4 + Math.max(0, (width - lineW) / 2), y,
                        GovTheme.MUTED, false);
                y += 9;
            }
            if (this.scopeRetryRect.w() > 0 && this.scopeRetryRect.h() > 0) {
                drawButton(graphics, this.scopeRetryRect, this.retryText, true,
                        this.scopeRetryRect.contains(mouseX, mouseY), false);
            }
            return;
        }
        if (this.scopeHits.isEmpty()) {
            GovText.clipped(graphics, this.font,
                    this.mode == 1 ? this.noJobText : this.noBizText,
                    this.scopeRect.x() + 2, infoY,
                    Math.max(0, this.scopeRect.w() - 4), GovTheme.MUTED);
            return;
        }
        for (final ScopeHit hit : this.scopeHits) {
            drawScopeRow(graphics, hit, mouseX, mouseY);
        }
    }

    protected void drawScopeRow(final GuiGraphics graphics, final ScopeHit hit,
            final int mouseX, final int mouseY) {
        final GovRect rect = hit.rect();
        final ScopeRow scope = hit.scope();
        if (rect.w() <= 0 || rect.h() <= 0) {
            return;
        }
        final boolean selected = this.mode == 1
                ? scope.id().equals(this.selectedJobId)
                : scope.id().equals(this.selectedBizId);
        final boolean hovered = rect.contains(mouseX, mouseY);
        if (selected) {
            graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), GovTheme.SELECTED);
            graphics.fill(rect.x(), rect.y(), rect.x() + 3, rect.bottom(), GovTheme.ACCENT);
        } else if (hovered) {
            graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), GovTheme.HOVER);
        }
        final int textX = rect.x() + 6;
        final int textW = Math.max(0, rect.w() - 10);
        final int textY = rect.h() >= 20 ? rect.y() + 2
                : rect.y() + Math.max(0, (rect.h() - 9) / 2);
        if (textW > 0 && rect.h() >= 9) {
            if (selected) {
                GovText.clipped(graphics, this.font, scope.boldLabel(), textX, textY,
                        textW, GovTheme.TEXT);
            } else {
                GovText.clipped(graphics, this.font, scope.label(), textX, textY, textW,
                        hovered ? GovTheme.BODY : GovTheme.MUTED);
            }
        }
        if (rect.h() >= 20 && !scope.role().isEmpty() && this.font != null) {
            GovText.clipped(graphics, this.font, scope.role(), textX, rect.y() + 11,
                    textW, GovTheme.FAINT);
        }
        GovWidgets.rule(graphics, rect.x(), rect.bottom() - 1, rect.w(),
                GovTheme.BORDER_SOFT);
    }

    protected void drawScopeBar(final GuiGraphics graphics, final int mouseX,
            final int mouseY) {
        if (this.scopeBarRect.w() <= 0 || this.scopeBarRect.h() <= 0) {
            return;
        }
        final int textY = this.scopeBarRect.y()
                + Math.max(0, (this.scopeBarRect.h() - 9) / 2);
        if (this.mode == 0) {
            GovText.clipped(graphics, this.font, this.notesSubtitle,
                    this.scopeBarRect.x() + 2, textY,
                    Math.max(0, this.scopeBarRect.w() - 4), GovTheme.MUTED);
            GovWidgets.rule(graphics, this.scopeBarRect.x(),
                    Math.min(this.bodyRect.bottom() - 1, this.scopeBarRect.bottom()),
                    this.scopeBarRect.w(), GovTheme.BORDER_SOFT);
            return;
        }
        String label;
        if (this.scopesState == Load.LOADING) {
            label = this.loadingText;
        } else if (this.scopesState == Load.ERROR) {
            label = this.errorTitle;
        } else if (this.scopesState == Load.READY && currentScopes().isEmpty()) {
            label = this.mode == 1 ? this.noJobText : this.noBizText;
        } else {
            label = this.mode == 1 ? this.selectedJobLabel : this.selectedBizLabel;
            if (label.isEmpty()) {
                label = this.mode == 1 ? this.noJobText : this.noBizText;
            }
        }
        final int toggleW = this.scopeToggleRect.w();
        final int labelW = Math.max(0, this.scopeBarRect.w() - toggleW - 4);
        GovText.clipped(graphics, this.font, label, this.scopeBarRect.x() + 2, textY,
                labelW, GovTheme.MUTED);
        if (toggleW > 0) {
            final boolean enabled = this.scopesState == Load.ERROR
                    || (this.scopesState == Load.READY && !currentScopes().isEmpty());
            drawButton(graphics, this.scopeToggleRect, this.scopesText, enabled,
                    this.scopeToggleRect.contains(mouseX, mouseY), this.pickerOpen);
        }
        GovWidgets.rule(graphics, this.scopeBarRect.x(),
                Math.min(this.bodyRect.bottom() - 1, this.scopeBarRect.bottom()),
                this.scopeBarRect.w(), GovTheme.BORDER_SOFT);
    }
}
