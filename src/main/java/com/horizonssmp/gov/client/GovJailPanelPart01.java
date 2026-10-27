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

abstract class GovJailPanelPart01 extends GovJailPanelAbstracts {
    GovJailPanelPart01(final net.minecraft.client.gui.screens.Screen host) {
        super(host);
    }

    List<EditBox> createWidgets(final Font font) {
        this.font = font;
        if (!this.widgets.isEmpty()) {
            return this.widgets;
        }
        this.detainTargetBox = box(font, 32, "gui.gov.jail.target_hint");
        this.detainReasonBox = box(font, JailKeys.MAX_REASON_CHARS,
                "gui.gov.jail.reason_hint");
        this.warrantBox = box(font, JailKeys.MAX_WARRANT_REF_CHARS, "gui.gov.jail.warrant_hint");
        this.detainMinutesBox = digitsBox(font, 5, "gui.gov.jail.minutes_hint");
        this.releaseReasonBox = box(font, JailKeys.MAX_REASON_CHARS,
                "gui.gov.jail.reason_hint");
        this.extendMinutesBox = digitsBox(font, 5, "gui.gov.jail.minutes_hint");
        this.extendReasonBox = box(font, JailKeys.MAX_REASON_CHARS,
                "gui.gov.jail.extend_reason_hint");
        this.widgets = List.of(this.detainTargetBox, this.detainReasonBox, this.warrantBox,
                this.detainMinutesBox, this.releaseReasonBox, this.extendMinutesBox,
                this.extendReasonBox);
        return this.widgets;
    }


    EditBox box(final Font font, final int maxLength, final String hintKey) {
        final Component hint = Component.translatable(hintKey);
        // The shared factory already applies setTextShadow(false) and a neutral ink selection,
        // which vanilla EditBox cannot express through its public colour API. The explicit call
        // documents the contract even though the factory owns it.
        final EditBox box = GovWidgets.field(font, 0, 0, 10, 9, hint);
        box.setTextShadow(false);
        box.setMaxLength(maxLength);
        box.setVisible(false);
        return box;
    }

    /**
     * A minutes field accepts ASCII digits only, including the empty prefix while typing.
     * Unsigned fields cannot receive a sign at all, so {@code Integer.parseInt} in the submit
     * path never sees a character the client filter would have allowed.
     */

    EditBox digitsBox(final Font font, final int maxLength, final String hintKey) {
        final EditBox box = box(font, maxLength, hintKey);
        box.setFilter(value -> value != null && value.matches("\\d*"));
        return box;
    }

    /** Selects Detentions (0), Detain (1), Release (2), Extend (3) or Cells (4). */

    void setSubtab(final int index) {
        final int next = GovMetrics.clamp(index, 0, SUBTABS - 1);
        if (next == this.subtab) {
            syncWidgets();
            return;
        }
        this.subtab = next;
        disarm();
        clearFocus();
        this.status = "";
        this.statusIsError = false;
        if (next == 2 || next == 3) {
            if (!JailKeys.FILTER_ACTIVE.equals(this.filter) || this.page != 0) {
                this.filter = JailKeys.FILTER_ACTIVE;
                this.page = 0;
                this.listState = LoadState.LOADING;
                updateFilterLabel();
                requestList();
            } else if (this.listState == LoadState.NONE) {
                requestList();
            }
            ensureActiveSelection();
        } else if (this.listState == LoadState.NONE) {
            requestList();
        }
        syncWidgets();
        rebuildButtons();
        updateListTexts(next);
        updateListMetrics();
        rebuildDetailLines();
        updateSummaryTexts();
    }


    void setActive(final boolean active) {
        this.active = active;
        if (!active) {
            disarm();
            this.enterRepeat.reset();
            clearFocus();
        } else {
            requestListIfNeeded();
        }
        syncWidgets();
        rebuildButtons();
    }


    void reset() {
        this.pending.clear();
        this.latestListReqId = "";
        this.latestDetailReqId = "";
        this.status = "";
        this.statusIsError = false;
        disarm();
        clearFields();
    }

    // ------------------------------------------------------------------- layout


    void layout(final int left, final int top, final int right, final int bottom,
            final Font font) {
        this.font = font;
        final int width = Math.max(0, right - left);
        final int height = Math.max(0, bottom - top);
        this.bounds = new GovRect(left, top, width, height);
        final boolean compact = height < 130;
        final int topH = compact ? 14 : 16;
        final int bottomH = compact ? 14 : 16;
        final int statusH = compact ? 10 : 12;
        this.rowPitch = compact ? ROW_PITCH_COMPACT : ROW_PITCH;
        this.detailPitch = compact ? DETAIL_PITCH_COMPACT : DETAIL_PITCH;

        layoutDetentions(left, top, right, bottom, topH, bottomH, statusH, height >= 140);
        layoutDetain(left, top, right, bottom, statusH);
        layoutRelease(left, top, right, bottom, statusH);
        layoutExtend(left, top, right, bottom, statusH);

        rebuildButtons();
        updateListMetrics();
        rebuildDetailLines();
        updateSummaryTexts();
        updateFilterLabel();
    }


    void layoutDetentions(final int left, final int top, final int right,
            final int bottom, final int topH, final int bottomH, final int statusH,
            final boolean sideBySide) {
        final int width = Math.max(0, right - left);
        this.topBars[0] = rect(left, top, width, topH);
        this.bottoms[0] = rect(left, bottom - bottomH, width, bottomH);
        this.statuses[0] = rect(left, this.bottoms[0].y() - statusH, width, statusH);
        this.bodies[0] = rect(left, this.topBars[0].bottom() + 2, width,
                this.statuses[0].y() - this.topBars[0].bottom() - 2);
        if (this.bodies[0].h() <= 0) {
            this.lists[0] = EMPTY;
            this.listBodies[0] = EMPTY;
            this.details[0] = EMPTY;
            this.summaries[0] = EMPTY;
            return;
        }
        if (sideBySide) {
            int listW = this.bodies[0].w() * 54 / 100;
            listW = GovMetrics.clamp(listW, 0, this.bodies[0].w());
            final int gap = this.bodies[0].w() > 120 ? 4 : 0;
            this.lists[0] = rect(left, this.bodies[0].y(), listW, this.bodies[0].h());
            this.listBodies[0] = this.lists[0];
            this.details[0] = rect(this.lists[0].right() + gap, this.bodies[0].y(),
                    this.bodies[0].w() - listW - gap, this.bodies[0].h());
            this.summaries[0] = EMPTY;
        } else {
            final int summaryH = Math.min(12, this.bodies[0].h());
            this.lists[0] = rect(left, this.bodies[0].y(), this.bodies[0].w(),
                    this.bodies[0].h() - summaryH - 2);
            this.listBodies[0] = this.lists[0];
            this.summaries[0] = rect(left, this.lists[0].bottom() + 2,
                    this.bodies[0].w(), summaryH);
            this.details[0] = EMPTY;
        }
    }


    void layoutDetain(final int left, final int top, final int right, final int bottom,
            final int statusH) {
        this.topBars[1] = EMPTY;
        this.bottoms[1] = EMPTY;
        this.statuses[1] = rect(left, bottom - statusH, Math.max(0, right - left), statusH);
        this.bodies[1] = rect(left, top + 2, Math.max(0, right - left),
                this.statuses[1].y() - top - 2);
        this.lists[1] = EMPTY;
        this.listBodies[1] = EMPTY;
        this.details[1] = EMPTY;
        this.summaries[1] = EMPTY;
        final GovRect body = this.bodies[1];
        if (body.w() <= 0 || body.h() <= 0) {
            return;
        }
        final int labelW = Math.min(52, Math.max(0, body.w() / 5));
        final int fieldX = body.x() + labelW + 4;
        final int fieldW = Math.max(0, body.right() - 8 - fieldX);
        final int rowH = Math.min(FORM_ROW, Math.max(0,
                (body.h() - FORM_GAP * 4) / 5));
        final int row1 = body.y();
        final int row2 = row1 + rowH + FORM_GAP;
        final int row3 = row2 + rowH + FORM_GAP;
        final int row4 = row3 + rowH + FORM_GAP;
        final int row5 = row4 + rowH + FORM_GAP;
        final int targetW = fieldW * 62 / 100;
        this.targetFrame = rect(fieldX, row1, targetW, rowH);
        this.cellRect = rect(this.targetFrame.right() + 4, row1,
                Math.max(0, fieldW - targetW - 4), rowH);
        this.basisRect = rect(fieldX, row2, fieldW, rowH);
        final int minutesW = fieldW * 38 / 100;
        this.minutesFrame = rect(fieldX, row3, minutesW, rowH);
        this.warrantFrame = rect(this.minutesFrame.right() + 4, row3,
                Math.max(0, fieldW - minutesW - 4), rowH);
        this.consentRect = this.warrantFrame;
        this.reasonFrame = rect(fieldX, row4, fieldW, rowH);
        final int detainW = GovMetrics.span(body.w() - 8, fieldW * 55 / 100, 84, 110);
        this.detainButtonRect = rect(body.right() - 4 - detainW, row5, detainW, rowH);
        placeBox(this.detainTargetBox, this.targetFrame);
        placeBox(this.detainMinutesBox, this.minutesFrame);
        placeBox(this.warrantBox, this.warrantFrame);
        placeBox(this.detainReasonBox, this.reasonFrame);
    }


}
