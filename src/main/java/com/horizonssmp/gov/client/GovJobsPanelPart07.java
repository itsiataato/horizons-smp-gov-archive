package com.horizonssmp.gov.client;

import com.horizonssmp.gov.client.GovDashboardScreen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.horizonssmp.gov.common.GovFields;
import com.horizonssmp.gov.common.JobKeys;
import com.horizonssmp.gov.common.OpCode;
import com.horizonssmp.gov.network.GovActionResultPayload;
import com.horizonssmp.gov.network.GovNetwork;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import static com.horizonssmp.gov.client.GovJobsTypes.*;
import static com.horizonssmp.gov.client.GovJobsConstants.*;
abstract class GovJobsPanelPart07 extends GovJobsPanelPart06 {
    GovJobsPanelPart07(final net.minecraft.client.gui.screens.Screen host) {
        super(host);
    }

    void drawRoster(final GuiGraphics graphics, final int mouseX, final int mouseY) {
        if (this.rosterList.w() <= 0 || this.rosterList.h() <= 0) {
            return;
        }
        graphics.fill(this.rosterList.x(), this.rosterList.y(), this.rosterList.right(),
                this.rosterList.bottom(), GovTheme.FIELD);
        GovTheme.border(graphics, this.rosterList, GovTheme.BORDER);
        graphics.drawString(this.font, this.rosterHeaderText, this.rosterHeader.x(),
                this.rosterHeader.y() + 1,
                this.statusRect.h() == 0 && this.statusIsError && !this.status.isEmpty()
                        ? GovTheme.DANGER : GovTheme.MUTED, false);
        GovWidgets.rule(graphics, this.rosterHeader.x(), this.rosterHeader.bottom() - 1,
                this.rosterHeader.w(), GovTheme.BORDER_SOFT);

        if (!this.rosterEmptyTitle.isEmpty()) {
            GovWidgets.emptyState(graphics, this.font, this.rosterRows,
                    this.rosterEmptyTitle, this.rosterEmptyDetail);
            if (this.listState == ListState.ERROR && this.rosterListRetry.w() > 0) {
                GovWidgets.button(graphics, this.font, this.rosterListRetry, this.retryText,
                        true, this.rosterListRetry.contains(mouseX, mouseY), false);
            }
        } else {
            final boolean scissored = clipRows(graphics, this.rosterRows);
            try {
                final int first = Math.max(0, this.rosterScroll / this.rosterPitch);
                for (int index = first; index < this.rosterViews.size(); index++) {
                    final int y = this.rosterRows.y() + index * this.rosterPitch
                            - this.rosterScroll;
                    if (y >= this.rosterRows.bottom()) {
                        break;
                    }
                    // A partial row is skipped rather than clipped: half a line at the viewport
                    // edge reads as text bleeding out of the list.
                    if (y < this.rosterRows.y()
                            || y + this.rosterPitch > this.rosterRows.bottom()) {
                        continue;
                    }
                    drawRosterRow(graphics, this.rosterViews.get(index), y, mouseX, mouseY);
                }
            } finally {
                if (scissored) {
                    graphics.disableScissor();
                }
            }
        }
        GovWidgets.scrollbar(graphics, this.rosterScrollTrack, this.rosterScroll,
                this.rosterScrollMax);

        if (this.rosterSummary.w() > 0) {
            GovWidgets.rule(graphics, this.rosterSummary.x(), this.rosterSummary.y(),
                    this.rosterSummary.w(), GovTheme.BORDER_SOFT);
            graphics.drawString(this.font, this.rosterSummaryText,
                    this.rosterSummary.x() + 2, this.rosterSummary.y() + 1,
                    GovTheme.MUTED, false);
        } else {
            drawRosterDetail(graphics, mouseX, mouseY);
        }
    }

    void drawRosterRow(final GuiGraphics graphics, final RosterView view, final int y,
            final int mouseX, final int mouseY) {
        final boolean selected = view.id().equals(this.selectedJobId);
        final boolean hovered = GovTheme.inside(mouseX, mouseY, this.rosterList.x() + 2, y,
                Math.max(0, this.rosterList.w() - 4), this.rosterPitch);
        if (selected) {
            graphics.fill(this.rosterList.x() + 2, y, this.rosterList.right() - 2,
                    y + this.rosterPitch, GovTheme.SELECTED);
            graphics.fill(this.rosterList.x() + 2, y, this.rosterList.x() + 5,
                    y + this.rosterPitch, GovTheme.ACCENT);
        } else if (hovered) {
            graphics.fill(this.rosterList.x() + 2, y, this.rosterList.right() - 2,
                    y + this.rosterPitch, GovTheme.HOVER);
        }
        final int textX = this.rosterList.x() + 8;
        graphics.drawString(this.font, view.name(), textX, y + 2,
                selected ? GovTheme.TEXT : GovTheme.BODY, false);
        if (view.typeWidth() > 0) {
            final int badgeX = this.rosterList.right() - 4 - view.typeWidth();
            graphics.fill(badgeX, y + 2, badgeX + view.typeWidth(), y + 13,
                    GovTheme.PANEL_ALT);
            GovTheme.border(graphics, badgeX, y + 2, view.typeWidth(), 11, GovTheme.BORDER);
            GovText.centered(graphics, this.font, view.type(), badgeX + 2, y + 4,
                    view.typeWidth() - 4, GovTheme.TEXT);
        }
        graphics.drawString(this.font, view.meta(), textX, y + 9, GovTheme.FAINT, false);
    }

    void drawRosterDetail(final GuiGraphics graphics, final int mouseX,
            final int mouseY) {
        if (this.rosterDetail.w() <= 0 || this.rosterDetail.h() <= 0) {
            return;
        }
        graphics.fill(this.rosterDetail.x(), this.rosterDetail.y(),
                this.rosterDetail.right(), this.rosterDetail.bottom(), GovTheme.FIELD);
        GovTheme.border(graphics, this.rosterDetail, GovTheme.BORDER);

        if (this.selectedJobId.isEmpty() || this.detailState == DetailState.NONE) {
            GovWidgets.emptyState(graphics, this.font, this.detailBody, this.noSelectionText,
                    "");
            return;
        }
        if (this.detailState == DetailState.LOADING) {
            GovWidgets.emptyState(graphics, this.font, this.detailBody, this.loadingText, "");
            return;
        }
        if (this.detailState == DetailState.ERROR) {
            GovWidgets.emptyState(graphics, this.font, this.detailBody, this.errorText,
                    this.detailErrorText);
            if (this.rosterDetailRetry.w() > 0) {
                GovWidgets.button(graphics, this.font, this.rosterDetailRetry, this.retryText,
                        true, this.rosterDetailRetry.contains(mouseX, mouseY), false);
            }
            return;
        }

        graphics.drawString(this.font, this.detailNameComponent,
                this.detailHeader.x(), this.detailHeader.y() + 1, GovTheme.TEXT, false);
        if (this.detailTypeWidth > 0) {
            final int badgeX = this.detailHeader.right() - this.detailTypeWidth;
            graphics.fill(badgeX, this.detailHeader.y(), badgeX + this.detailTypeWidth,
                    this.detailHeader.y() + 12, GovTheme.PANEL_ALT);
            GovTheme.border(graphics, badgeX, this.detailHeader.y(),
                    this.detailTypeWidth, 12, GovTheme.BORDER);
            GovText.centered(graphics, this.font, this.detailTypeText, badgeX + 2,
                    this.detailHeader.y() + 2, this.detailTypeWidth - 4, GovTheme.TEXT);
        }

        int y = this.detailBody.y() - this.detailScroll;
        for (final DetailLine line : this.detailLines) {
            if (y + line.height() > this.detailBody.y()
                    && y < this.detailBody.bottom()) {
                drawDetailLine(graphics, line, y);
            }
            y += line.height();
        }
        GovWidgets.scrollbar(graphics, this.detailScrollTrack, this.detailScroll,
                this.detailScrollMax);
    }


    void drawDetailLine(final GuiGraphics graphics, final DetailLine line, final int y) {
        if (line.wrapped() != null) {
            for (int index = 0; index < line.wrapped().size(); index++) {
                final int lineY = y + index * 10;
                if (lineY >= this.detailBody.y() && lineY + 10 <= this.detailBody.bottom()) {
                    graphics.drawString(this.font, line.wrapped().get(index),
                            this.detailBody.x(), lineY, GovTheme.BODY, false);
                }
            }
            return;
        }
        if (line.heading()) {
            if (line.bold() != null) {
                graphics.drawString(this.font, line.bold(),
                        this.detailBody.x(), y + 2, GovTheme.MUTED, false);
            }
            return;
        }
        if (line.label() != null) {
            graphics.drawString(this.font, line.label(), this.detailBody.x(), y + 2,
                    GovTheme.MUTED, false);
            graphics.drawString(this.font, line.value(), this.detailBody.x()
                    + GovMetrics.clamp(this.detailBody.w() / 3, 54, 110), y + 2,
                    GovTheme.TEXT, false);
        } else if (line.value() != null) {
            graphics.drawString(this.font, line.value(), this.detailBody.x(), y + 1,
                    GovTheme.BODY, false);
        }
    }


}
