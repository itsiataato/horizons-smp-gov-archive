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
import com.horizonssmp.gov.common.OpCode;
import com.horizonssmp.gov.common.WeaponKeys;
import com.horizonssmp.gov.network.GovActionResultPayload;
import com.horizonssmp.gov.network.GovNetwork;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import com.horizonssmp.gov.client.GovWeaponsTypes.*;
import static com.horizonssmp.gov.client.GovWeaponsConstants.*;

abstract class GovWeaponsPanelPart10 extends GovWeaponsPanelPart09 {
    GovWeaponsPanelPart10(final net.minecraft.client.gui.screens.Screen host) {
        super(host);
    }


    void drawModal(final GuiGraphics graphics, final int mouseX, final int mouseY) {
        GovWidgets.modalBackdrop(graphics, this.bounds);
        final int titleW = Math.max(0, this.modalRect.w() - GovMetrics.GAP_S * 2 - CLOSE_W - 8);
        GovWidgets.modalFrame(graphics, this.font, this.modalRect,
                GovText.clip(this.font, this.modalTitle, titleW));
        if (!this.status.isEmpty()) {
            GovText.clipped(graphics, this.font, this.status,
                    this.modalRect.x() + this.modalRect.w() / 2, this.modalRect.y() + 3,
                    Math.max(0, this.modalRect.w() / 2 - 8), GovTheme.TEXT);
        }
        final int pad = 6;
        final int labelX = this.modalRect.x() + pad;
        switch (this.modal) {
            case RECORD -> drawRecordModal(graphics);
            case REGISTER -> {
                drawModalLabel(graphics, this.labelSerial, labelX, this.modalRow1);
                drawFieldFrame(graphics, this.modalSerialFrame, this.registerSerialBox);
                drawModalLabel(graphics, this.labelType, labelX, this.modalRow2);
                drawFieldFrame(graphics, this.modalTypeFrame, this.registerTypeBox);
                drawModalLabel(graphics, this.labelOwner, labelX, this.modalRow3);
                drawFieldFrame(graphics, this.modalOwnerFrame, this.ownerBox);
            }
            case TRANSFER -> {
                drawModalContext(graphics, this.modalRow1);
                drawModalLabel(graphics, this.labelOwner, labelX, this.modalRow2);
                drawFieldFrame(graphics, this.modalTransferOwnerFrame, this.transferOwnerBox);
            }
            case REVOKE, LOSS, PERMIT_REVOKE, AREA_REVOKE -> {
                drawModalContext(graphics, this.modalRow1);
                drawModalLabel(graphics, this.labelReason, labelX, this.modalRow2);
                drawFieldFrame(graphics, this.modalReasonFrame, this.reasonBox);
            }
            case PERMIT_ISSUE -> {
                drawModalLabel(graphics, this.labelCitizen, labelX, this.modalRow1);
                drawFieldFrame(graphics, this.modalTargetFrame, this.issueTargetBox);
                drawModalLabel(graphics, this.labelCategory, labelX, this.modalRow2);
                drawModalLabel(graphics, this.labelReason, labelX, this.modalRow3);
                drawFieldFrame(graphics, this.modalReasonFrame, this.reasonBox);
            }
            case PERMIT_SUSPEND -> {
                drawModalLabel(graphics, this.labelMinutes, labelX, this.modalRow1);
                drawFieldFrame(graphics, this.modalMinutesFrame, this.minutesBox);
                drawModalLabel(graphics, this.labelReason, labelX, this.modalRow2);
                drawFieldFrame(graphics, this.modalReasonFrame, this.reasonBox);
                drawModalContext(graphics, this.modalRow3);
            }
            case AREA_CREATE -> {
                drawModalLabel(graphics, this.labelDimension, labelX, this.modalRow1);
                drawFieldFrame(graphics, this.modalDimFrame, this.dimBox);
                drawFieldFrame(graphics, this.modalRadiusFrame, this.radiusBox);
                drawFieldFrame(graphics, this.modalAreaMinutesFrame, this.areaMinutesBox);
                drawModalLabel(graphics, this.labelCenter, labelX, this.modalRow2);
                drawFieldFrame(graphics, this.modalXFrame, this.xBox);
                drawFieldFrame(graphics, this.modalYFrame, this.yBox);
                drawFieldFrame(graphics, this.modalZFrame, this.zBox);
                drawModalLabel(graphics, this.labelReason, labelX, this.modalRow3);
                drawFieldFrame(graphics, this.modalAreaReasonFrame, this.areaReasonBox);
            }
            default -> {
            }
        }
        for (final Button button : this.modalButtons) {
            final boolean hovered = button.rect().contains(mouseX, mouseY);
            GovWidgets.button(graphics, this.font, button.rect(), button.label(),
                    button.enabled(), button.enabled() && hovered,
                    button.primary() || button.selected());
        }
        drawModalClose(graphics, mouseX, mouseY);
    }

    void drawButtons(final GuiGraphics graphics, final int mouseX, final int mouseY) {
        for (final Button button : this.buttons) {
            final boolean hovered = button.rect().contains(mouseX, mouseY);
            GovWidgets.button(graphics, this.font, button.rect(), button.label(),
                    button.enabled(), button.enabled() && hovered,
                    button.primary() || button.selected());
        }
    }

    void drawStatus(final GuiGraphics graphics) {
        final GovRect area = this.statuses[this.subtab];
        if (area.w() <= 0 || area.h() <= 0) {
            return;
        }
        final String text;
        if (!this.status.isEmpty()) {
            text = this.status;
        } else if (this.subtab == 2) {
            text = this.policyText;
        } else if (this.subtab == 3) {
            text = this.textClassifierPolicy;
        } else {
            text = "";
        }
        if (text.isEmpty()) {
            return;
        }
        GovText.clipped(graphics, this.font, text, area.x() + 3, area.y() + 1,
                Math.max(0, area.w() - 6), this.statusIsError ? GovTheme.TEXT : GovTheme.MUTED);
    }

    String emptyText() {
        return switch (this.subtab) {
            case 0 -> this.canViewAll ? this.textEmptyAll : this.textEmptyOwn;
            case 1 -> this.textPermitEmpty;
            case 2 -> this.textAreaEmpty;
            default -> "";
        };
    }

    void drawStatusWord(final GuiGraphics graphics, final String text,
            final boolean strong, final int x, final int y, final int width) {
        if (text == null || text.isEmpty() || width <= 0) {
            return;
        }
        if (strong) {
            graphics.fill(Math.max(0, x - 5), y + 1, Math.max(0, x - 3), y + 9,
                    GovTheme.ACCENT);
            GovText.clipped(graphics, this.font, GovText.boldCached(text), x, y, width,
                    GovTheme.TEXT);
        } else {
            GovText.clipped(graphics, this.font, text, x, y, width, GovTheme.MUTED);
        }
    }

    void drawRowText(final GuiGraphics graphics, final String lineOne,
            final String lineTwo, final String status, final boolean strong,
            final int cachedStatusWidth, final int y, final GovRect body) {
        final int statusWidth = cachedStatusWidth > 0 ? cachedStatusWidth
                : this.font.width(status);
        final int statusX = Math.max(body.x() + 6, body.right() - 6 - statusWidth);
        final int textWidth = Math.max(0, statusX - body.x() - 10);
        if (this.rowPitch >= 17) {
            GovText.clipped(graphics, this.font, lineOne, body.x() + 7, y + 1, textWidth,
                    GovTheme.TEXT);
            GovText.clipped(graphics, this.font, lineTwo, body.x() + 7, y + 10, textWidth,
                    GovTheme.MUTED);
        } else {
            GovText.clipped(graphics, this.font, lineOne, body.x() + 7, y + 2, textWidth,
                    GovTheme.TEXT);
        }
        drawStatusWord(graphics, status, strong, statusX, y + (this.rowPitch - 9) / 2,
                Math.max(0, body.right() - 6 - statusX));
    }

    void drawRow(final GuiGraphics graphics, final int index, final int y,
            final GovRect body) {
        if (this.subtab == 0) {
            final WeaponRow row = this.weaponRows.get(index);
            drawRowText(graphics, row.lineOne(), row.lineTwo(), row.statusText(),
                    row.strongStatus(), row.statusWidth(), y, body);
        } else if (this.subtab == 1) {
            final PermitRow row = this.permitRows.get(index);
            drawRowText(graphics, row.lineOne(), row.lineTwo(), row.statusText(),
                    row.strongStatus(), row.statusWidth(), y, body);
        } else {
            final AreaRow row = this.areaRows.get(index);
            drawRowText(graphics, row.lineOne(), row.lineTwo(), row.statusText(),
                    row.strongStatus(), row.statusWidth(), y, body);
        }
    }

    void drawList(final GuiGraphics graphics, final int mouseX, final int mouseY) {
        final GovRect list = this.lists[this.subtab];
        if (list.w() <= 0 || list.h() <= 0) {
            return;
        }
        graphics.fill(list.x(), list.y(), list.right(), list.bottom(), GovTheme.FIELD);
        GovTheme.border(graphics, list, GovTheme.BORDER_SOFT);
        final int headerH = this.bounds.h() < 130 ? 0 : 10;
        if (headerH > 0 && !this.listHeader.isEmpty()) {
            GovText.clipped(graphics, this.font, this.listHeader, list.x() + 4, list.y() + 1,
                    Math.max(0, list.w() - 8), GovTheme.FAINT);
            GovWidgets.rule(graphics, list.x() + 1, list.y() + headerH, list.w() - 2,
                    GovTheme.BORDER_SOFT);
        }
        final GovRect body = this.listBodies[this.subtab];
        if (body.w() <= 0 || body.h() <= 0) {
            return;
        }
        if (this.listState == LoadState.LOADING) {
            GovText.clipped(graphics, this.font, this.textLoading, body.x() + 6, body.y() + 4,
                    Math.max(0, body.w() - 12), GovTheme.MUTED);
            return;
        }
        if (this.listState == LoadState.ERROR) {
            GovText.clipped(graphics, this.font, this.textError, body.x() + 6, body.y() + 4,
                    Math.max(0, body.w() - 12), GovTheme.TEXT);
            if (!this.listError.isEmpty()) {
                GovText.clipped(graphics, this.font, this.listError, body.x() + 6,
                        body.y() + 15, Math.max(0, body.w() - 12), GovTheme.MUTED);
            }
            return;
        }
        if (listRowCount() == 0) {
            GovText.clipped(graphics, this.font, emptyText(), body.x() + 6, body.y() + 4,
                    Math.max(0, body.w() - 12), GovTheme.MUTED);
            return;
        }
        final int scroll = this.listScrollRows[this.subtab];
        graphics.enableScissor(body.x(), body.y(), body.right(), body.bottom());
        try {
            for (int i = 0; i < this.visibleRows; i++) {
                final int index = scroll + i;
                if (index >= listRowCount()) {
                    break;
                }
                final int y = body.y() + i * this.rowPitch;
                if (y + this.rowPitch > body.bottom()) {
                    break;
                }
                final boolean selected = rowId(index).equals(selectionKey());
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
                drawRow(graphics, index, y, body);
            }
        } finally {
            graphics.disableScissor();
        }
        if (this.listScrollMaxRows > 0) {
            GovWidgets.scrollbar(graphics, this.listScrollbarRect,
                    this.listScrollRows[this.subtab] * this.rowPitch,
                    this.listScrollMaxRows * this.rowPitch);
        }
    }

    void drawBottomBar(final GuiGraphics graphics) {
        final GovRect bar = this.bottoms[this.subtab];
        if (bar.w() <= 0 || bar.h() <= 0) {
            return;
        }
        GovWidgets.rule(graphics, bar.x(), bar.y(), bar.w(), GovTheme.BORDER_SOFT);
        final int buttonW = Math.min(54, Math.max(0, bar.w() / 3));
        final int left = bar.x() + 3 + buttonW * 2 + 6;
        final int right = bar.right() - GovMetrics.span(bar.w(), bar.w() / 5, 44, 60) - 6;
        final int width = Math.max(0, right - left);
        if (width > 0 && !this.pageLabel.isEmpty()) {
            GovText.centered(graphics, this.font, this.pageLabel, left, bar.y() + 3, width,
                    GovTheme.MUTED);
        }
    }

}
