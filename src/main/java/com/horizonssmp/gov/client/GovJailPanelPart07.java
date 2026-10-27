package com.horizonssmp.gov.client;

import com.horizonssmp.gov.client.GovDashboardScreen;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.horizonssmp.gov.common.GovFields;
import com.horizonssmp.gov.common.JailKeys;
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

import static com.horizonssmp.gov.client.GovJailTypes.*;
import static com.horizonssmp.gov.client.GovJailConstants.*;

abstract class GovJailPanelPart07 extends GovJailPanelPart06 {
    GovJailPanelPart07(final net.minecraft.client.gui.screens.Screen host) {
        super(host);
    }

    void drawDetain(final GuiGraphics graphics, final int mouseX, final int mouseY) {
        final GovRect body = this.bodies[1];
        if (body.w() <= 0 || body.h() <= 0) {
            return;
        }
        final int labelW = Math.min(52, Math.max(0, body.w() / 5));
        final int labelX = body.x() + 2;
        final int rowH = Math.min(FORM_ROW, Math.max(0, (body.h() - FORM_GAP * 4) / 5));
        final int row1 = body.y();
        final int row2 = row1 + rowH + FORM_GAP;
        final int row3 = row2 + rowH + FORM_GAP;
        final int row4 = row3 + rowH + FORM_GAP;
        drawFormLabel(graphics, this.labelCitizen, labelX, row1, labelW);
        drawFieldFrame(graphics, this.targetFrame, this.detainTargetBox);
        GovText.clipped(graphics, this.font, this.cellText, this.cellRect.x(), row1 + 4,
                Math.max(0, this.cellRect.w() - 2), GovTheme.FAINT);
        drawFormLabel(graphics, this.labelBasis, labelX, row2, labelW);
        drawFormLabel(graphics, this.labelMinutes, labelX, row3, labelW);
        drawFieldFrame(graphics, this.minutesFrame, this.detainMinutesBox);
        if ("warrant".equals(this.detainBasis)) {
            drawFieldFrame(graphics, this.warrantFrame, this.warrantBox);
        } else if ("violent".equals(this.detainBasis)) {
            GovText.clipped(graphics, this.font, this.textNoWarrantRequired,
                    this.warrantFrame.x(), row3 + 4, Math.max(0, this.warrantFrame.w() - 2),
                    GovTheme.FAINT);
        }
        drawFormLabel(graphics, this.labelReason, labelX, row4, labelW);
        drawFieldFrame(graphics, this.reasonFrame, this.detainReasonBox);
    }


    void drawRelease(final GuiGraphics graphics, final int mouseX, final int mouseY) {
        drawList(graphics, mouseX, mouseY, 2);
        final GovRect summary = this.releaseSummaryRect;
        if (summary.h() > 0) {
            GovText.clipped(graphics, this.font, this.summaryText, summary.x() + 2,
                    summary.y() + 1, Math.max(0, summary.w() - 4), GovTheme.MUTED);
        }
        drawFormLabel(graphics, this.labelReason, this.bodies[2].x() + 2,
                this.releaseReasonFrame.y(), Math.min(52, Math.max(0, this.bodies[2].w() / 5)));
        drawFieldFrame(graphics, this.releaseReasonFrame, this.releaseReasonBox);
    }


    void drawExtend(final GuiGraphics graphics, final int mouseX, final int mouseY) {
        drawList(graphics, mouseX, mouseY, 3);
        final GovRect summary = this.extendSummaryRect;
        if (summary.h() > 0) {
            GovText.clipped(graphics, this.font, this.summaryText, summary.x() + 2,
                    summary.y() + 1, Math.max(0, summary.w() - 4), GovTheme.MUTED);
            if (summary.h() >= 20) {
                GovText.clipped(graphics, this.font, this.extendCeiling, summary.x() + 2,
                        summary.y() + 10, Math.max(0, summary.w() - 4), GovTheme.TEXT);
            }
        }
        final int labelW = Math.min(52, Math.max(0, this.bodies[3].w() / 5));
        drawFormLabel(graphics, this.labelMinutes, this.bodies[3].x() + 2,
                this.extendMinutesFrame.y(), labelW);
        drawFieldFrame(graphics, this.extendMinutesFrame, this.extendMinutesBox);
        drawFieldFrame(graphics, this.extendReasonFrame, this.extendReasonBox);
    }

    void drawList(final GuiGraphics graphics, final int mouseX, final int mouseY,
            final int mode) {
        final GovRect list = this.lists[mode];
        if (list.w() <= 0 || list.h() <= 0) {
            return;
        }
        graphics.fill(list.x(), list.y(), list.right(), list.bottom(), GovTheme.FIELD);
        GovTheme.border(graphics, list, GovTheme.BORDER_SOFT);
        final int headerH = this.bounds.h() < 130 || this.listHeader.isEmpty() ? 0 : 10;
        if (headerH > 0 && !this.listHeader.isEmpty()) {
            GovText.clipped(graphics, this.font, this.listHeader, list.x() + 4, list.y() + 1,
                    Math.max(0, list.w() - 8), GovTheme.FAINT);
            GovWidgets.rule(graphics, list.x() + 1, list.y() + headerH, list.w() - 2,
                    GovTheme.BORDER_SOFT);
        }
        final GovRect body = this.listBodies[mode];
        if (body.w() <= 0 || body.h() <= 0) {
            return;
        }
        if (this.listState == LoadState.LOADING) {
            GovText.clipped(graphics, this.font, this.textLoading, body.x() + 6, body.y() + 4,
                    Math.max(0, body.w() - 12), GovTheme.MUTED);
            return;
        }
        if (this.listState == LoadState.ERROR) {
            // The Retry control is painted in this body's top-right; keep the title and the
            // wrapped server reason inside the remaining width instead of underneath it.
            final GovRect retry = buttonRect(Click.RETRY_LIST);
            final int textX = body.x() + 6;
            final int textRight = retry == null ? body.right() - 6
                    : Math.min(body.right() - 6, retry.x() - 4);
            final int textW = Math.max(0, textRight - textX);
            GovText.clipped(graphics, this.font, this.textError, textX, body.y() + 4,
                    textW, GovTheme.TEXT);
            if (!this.listError.isEmpty()) {
                GovText.clipped(graphics, this.font, this.listError, textX,
                        body.y() + 15, textW, GovTheme.MUTED);
            }
            return;
        }
        if (listRowCount() == 0) {
            GovText.clipped(graphics, this.font,
                    JailKeys.FILTER_ACTIVE.equals(this.filter) ? this.textEmptyActive
                            : this.textEmptyFilter,
                    body.x() + 6, body.y() + 4, Math.max(0, body.w() - 12), GovTheme.MUTED);
            return;
        }
        final int scroll = this.listScrollRows[mode];
        for (int i = 0; i < this.visibleRows; i++) {
            final int index = scroll + i;
            if (index >= listRowCount()) {
                break;
            }
            final int y = body.y() + i * this.rowPitch;
            final boolean selected = rowId(index).equals(this.selectedId);
            final boolean hovered = i < this.rowHits.size()
                    && this.rowHits.get(i).rect().contains(mouseX, mouseY);
            if (selected) {
                graphics.fill(body.x() + 1, y, body.right() - 1, y + this.rowPitch - 1,
                        GovTheme.SELECTED);
                graphics.fill(body.x() + 1, y, body.x() + 4, y + this.rowPitch - 1,
                        GovTheme.ACCENT);
            } else if (hovered) {
                graphics.fill(body.x() + 1, y, body.right() - 1, y + this.rowPitch - 1,
                        GovTheme.HOVER);
            }
            final JailRow row = rowAt(index);
            if (row != null) {
                drawRowText(graphics, row, y, body);
            }
        }
        if (this.listScrollMaxRows > 0) {
            GovWidgets.scrollbar(graphics, this.listScrollbarRect,
                    this.listScrollRows[mode] * this.rowPitch,
                    this.listScrollMaxRows * this.rowPitch);
        }
    }

    void drawRowText(final GuiGraphics graphics, final JailRow row, final int y,
            final GovRect body) {
        final int statusWidth = row.statusWidth() > 0 ? row.statusWidth()
                : this.font.width(row.statusText());
        final int statusX = Math.max(body.x() + 6, body.right() - 6 - statusWidth);
        final int textWidth = Math.max(0, statusX - body.x() - 10);
        if (this.rowPitch >= 17) {
            GovText.clipped(graphics, this.font, row.lineOne(), body.x() + 7, y + 1, textWidth,
                    GovTheme.TEXT);
            GovText.clipped(graphics, this.font, row.lineTwo(), body.x() + 7, y + 10,
                    textWidth, GovTheme.MUTED);
        } else {
            GovText.clipped(graphics, this.font, row.lineOne(), body.x() + 7, y + 2, textWidth,
                    GovTheme.TEXT);
        }
        final int width = Math.max(0, body.right() - 6 - statusX);
        if (width > 0) {
            if (row.strongStatus()) {
                graphics.fill(Math.max(0, statusX - 5), y + 1, Math.max(0, statusX - 3),
                        y + 9, GovTheme.ACCENT);
                GovText.clipped(graphics, this.font, GovText.boldCached(row.statusText()),
                        statusX, y + (this.rowPitch - 9) / 2, width, GovTheme.TEXT);
            } else {
                GovText.clipped(graphics, this.font, row.statusText(), statusX,
                        y + (this.rowPitch - 9) / 2, width, GovTheme.MUTED);
            }
        }
    }


    void drawDetail(final GuiGraphics graphics, final GovRect detail) {
        graphics.fill(detail.x(), detail.y(), detail.right(), detail.bottom(), GovTheme.FIELD);
        GovTheme.border(graphics, detail, GovTheme.BORDER_SOFT);
        if (!this.detailTitle.isEmpty()) {
            GovText.clipped(graphics, this.font, this.detailTitle, detail.x() + 5, detail.y() + 3,
                    Math.max(0, detail.w() - 10), GovTheme.TEXT);
        }
        GovWidgets.rule(graphics, detail.x(), detail.y() + 14, detail.w(), GovTheme.BORDER_SOFT);
        final int bodyTop = detail.y() + 16;
        final int bodyH = Math.max(0, detail.h() - 18);
        final int visible = this.detailPitch > 0 ? bodyH / this.detailPitch : 0;
        if (visible <= 0) {
            return;
        }
        final int maxScroll = Math.max(0, this.detailLines.size() - visible);
        final int scroll = GovMetrics.clamp(this.detailScrollRows[0], 0, maxScroll);
        final int labelW = Math.min(54, Math.max(0, detail.w() / 3));
        final int valueX = detail.x() + 6 + labelW;
        final int valueW = Math.max(0, detail.right() - 6 - valueX);
        for (int i = 0; i < visible; i++) {
            final int index = scroll + i;
            if (index >= this.detailLines.size()) {
                break;
            }
            final DetailLine line = this.detailLines.get(index);
            final int y = bodyTop + i * this.detailPitch;
            if (line.rule()) {
                GovWidgets.rule(graphics, detail.x() + 4, y, detail.w() - 8,
                        GovTheme.BORDER_SOFT);
            } else if (line.sequence() != null) {
                graphics.drawString(this.font, line.sequence(), detail.x() + 6, y,
                        line.color(), false);
            } else if (line.label() != null && !line.label().isEmpty()) {
                GovText.clipped(graphics, this.font, GovText.upperBoldCached(line.label()),
                        detail.x() + 6, y, Math.max(0, labelW - 4), GovTheme.MUTED);
                GovText.clipped(graphics, this.font, line.value(), valueX, y, valueW,
                        line.color());
            } else {
                GovText.clipped(graphics, this.font, line.value(), detail.x() + 6, y,
                        Math.max(0, detail.w() - 12), line.color());
            }
        }
        if (maxScroll > 0) {
            GovWidgets.scrollbar(graphics, this.detailScrollbarRect,
                    scroll * this.detailPitch, maxScroll * this.detailPitch);
        }
    }


    void drawButtons(final GuiGraphics graphics, final int mouseX, final int mouseY) {
        for (final Button button : this.buttons) {
            final boolean hovered = button.rect().contains(mouseX, mouseY);
            GovWidgets.button(graphics, this.font, button.rect(), button.label(),
                    button.enabled(), button.enabled() && hovered,
                    button.primary() || button.selected());
        }
    }


}
