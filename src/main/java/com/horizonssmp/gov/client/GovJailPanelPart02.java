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

abstract class GovJailPanelPart02 extends GovJailPanelPart01 {
    GovJailPanelPart02(final net.minecraft.client.gui.screens.Screen host) {
        super(host);
    }

    void layoutRelease(final int left, final int top, final int right, final int bottom,
            final int statusH) {
        this.topBars[2] = EMPTY;
        this.bottoms[2] = EMPTY;
        this.statuses[2] = rect(left, bottom - statusH, Math.max(0, right - left), statusH);
        this.bodies[2] = rect(left, top + 2, Math.max(0, right - left),
                this.statuses[2].y() - top - 2);
        this.details[2] = EMPTY;
        final GovRect body = this.bodies[2];
        if (body.w() <= 0 || body.h() <= 0) {
            this.lists[2] = EMPTY;
            this.listBodies[2] = EMPTY;
            this.summaries[2] = EMPTY;
            return;
        }
        final int buttonH = Math.min(16, Math.max(0, body.h() / 4));
        final int releaseLabelW = Math.min(52, Math.max(0, body.w() / 5));
        this.releaseButtonRect = rect(body.x() + 4, body.bottom() - buttonH,
                GovMetrics.span(body.w() - 8, body.w() / 2, 76, 110), buttonH);
        final int reasonH = Math.min(FORM_ROW, Math.max(0, body.h() / 4));
        this.releaseReasonFrame = rect(body.x() + releaseLabelW + 4,
                this.releaseButtonRect.y() - reasonH - 2,
                Math.max(0, body.w() - releaseLabelW - 8), reasonH);
        final int summaryH = Math.min(12, Math.max(0, body.h() - buttonH - reasonH - 8));
        this.releaseSummaryRect = rect(body.x() + 2,
                this.releaseReasonFrame.y() - summaryH - 2, Math.max(0, body.w() - 4),
                summaryH);
        this.lists[2] = rect(body.x(), body.y(), body.w(),
                Math.max(0, this.releaseSummaryRect.y() - body.y() - 2));
        this.listBodies[2] = this.lists[2];
        this.summaries[2] = this.releaseSummaryRect;
        placeBox(this.releaseReasonBox, this.releaseReasonFrame);
    }


    void layoutExtend(final int left, final int top, final int right, final int bottom,
            final int statusH) {
        this.topBars[3] = EMPTY;
        this.bottoms[3] = EMPTY;
        this.statuses[3] = rect(left, bottom - statusH, Math.max(0, right - left), statusH);
        this.bodies[3] = rect(left, top + 2, Math.max(0, right - left),
                this.statuses[3].y() - top - 2);
        this.details[3] = EMPTY;
        final GovRect body = this.bodies[3];
        if (body.w() <= 0 || body.h() <= 0) {
            this.lists[3] = EMPTY;
            this.listBodies[3] = EMPTY;
            this.summaries[3] = EMPTY;
            return;
        }
        final int buttonH = Math.min(16, Math.max(0, body.h() / 4));
        this.extendButtonRect = rect(body.x() + 4, body.bottom() - buttonH,
                GovMetrics.span(body.w() - 8, body.w() / 2, 76, 110), buttonH);
        final int rowH = Math.min(FORM_ROW, Math.max(0, body.h() / 4));
        final int extendLabelW = Math.min(52, Math.max(0, body.w() / 5));
        final int fieldW = Math.max(0, body.w() - extendLabelW - 8);
        final int minutesW = fieldW * 34 / 100;
        this.extendMinutesFrame = rect(body.x() + extendLabelW + 4,
                this.extendButtonRect.y() - rowH - 2, minutesW, rowH);
        this.extendReasonFrame = rect(this.extendMinutesFrame.right() + 4,
                this.extendMinutesFrame.y(),
                Math.max(0, fieldW - minutesW - 4), rowH);
        final int summaryH = Math.min(20, Math.max(0, body.h() - buttonH - rowH - 8));
        this.extendSummaryRect = rect(body.x() + 2,
                this.extendMinutesFrame.y() - summaryH - 2, Math.max(0, body.w() - 4),
                summaryH);
        this.lists[3] = rect(body.x(), body.y(), body.w(),
                Math.max(0, this.extendSummaryRect.y() - body.y() - 2));
        this.listBodies[3] = this.lists[3];
        this.summaries[3] = this.extendSummaryRect;
        placeBox(this.extendMinutesBox, this.extendMinutesFrame);
        placeBox(this.extendReasonBox, this.extendReasonFrame);
    }


    void placeBox(final EditBox box, final GovRect rect) {
        if (box == null) {
            return;
        }
        box.setX(rect.x() + 3);
        box.setY(rect.y() + 4);
        box.setWidth(Math.max(0, rect.w() - 6));
    }


    GovRect rect(final int x, final int y, final int w, final int h) {
        return new GovRect(Math.max(0, x), Math.max(0, y), Math.max(0, w), Math.max(0, h))
                .clamp(this.bounds);
    }

    /** Drops field focus so ESC can route field first, then armed action, then panel. */

    boolean clearFocus() {
        for (final EditBox box : this.widgets) {
            if (box != null && this.host.getFocused() == box) {
                this.host.setFocused(null);
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ requests


    void requestListIfNeeded() {
        if (this.listState == LoadState.NONE) {
            requestList();
        }
    }


    void requestList() {
        final String reqId = UUID.randomUUID().toString();
        this.pending.put(reqId, RequestKind.LIST);
        this.latestListReqId = reqId;
        this.listState = LoadState.LOADING;
        this.listError = "";
        GovNetwork.sendRequest(OpCode.JAIL_LIST, Map.of(
                JailKeys.KEY_FILTER, this.filter,
                JailKeys.KEY_PAGE, Integer.toString(this.page)), reqId);
        // A filter, page or refresh change invalidates every row hit at once. Rebuilding them
        // here (they stay empty until READY) is what stops a click from acting on the previous
        // result set while the new request is still in flight.
        rebuildRowHits();
        rebuildButtons();
    }


    void requestDetail(final String detentionId) {
        if (detentionId == null || detentionId.isBlank()) {
            return;
        }
        final String reqId = UUID.randomUUID().toString();
        this.pending.put(reqId, RequestKind.DETAIL);
        this.latestDetailReqId = reqId;
        this.detailState = LoadState.LOADING;
        this.detailError = "";
        GovNetwork.sendRequest(OpCode.JAIL_INFO,
                Map.of(JailKeys.KEY_DETENTION_ID, detentionId), reqId);
        // Show the loading line immediately and swap the retry control out of the cached hit
        // list, so a second click cannot re-send the same failed request before this answer.
        rebuildDetailLines();
        rebuildButtons();
    }


    void mutate(final OpCode op, final Map<String, String> payload) {
        if (mutationPending()) {
            return;
        }
        final String reqId = UUID.randomUUID().toString();
        this.pending.put(reqId, RequestKind.MUTATE);
        GovNetwork.sendRequest(op, payload, reqId);
        rebuildButtons();
    }


    boolean mutationPending() {
        return this.pending.containsValue(RequestKind.MUTATE);
    }


    void onActionResult(final GovActionResultPayload payload) {
        final RequestKind kind = this.pending.remove(payload.reqId());
        if (kind == null) {
            return;
        }
        switch (kind) {
            case LIST -> {
                if (!payload.reqId().equals(this.latestListReqId)) {
                    return;
                }
                if (payload.ok()) {
                    applyList(payload.delta());
                } else {
                    this.listState = LoadState.ERROR;
                    this.listError = fallbackError(payload, "gui.gov.jail.error");
                    rebuildRowHits();
                    rebuildButtons();
                }
            }
            case DETAIL -> {
                if (!payload.reqId().equals(this.latestDetailReqId)) {
                    return;
                }
                if (payload.ok()) {
                    applyDetail(payload.delta());
                } else {
                    this.detailState = LoadState.ERROR;
                    this.detailError = fallbackError(payload, "gui.gov.jail.detail_error");
                    updateSummaryTexts();
                    rebuildDetailLines();
                    rebuildButtons();
                }
            }
            case MUTATE -> {
                if (payload.ok()) {
                    this.status = payload.delta().isBlank() ? tr("gui.gov.jail.done")
                            : payload.delta();
                    this.statusIsError = false;
                    clearFields();
                    disarm();
                    requestList();
                    if (!this.selectedId.isEmpty()) {
                        requestDetail(this.selectedId);
                    }
                } else {
                    this.status = fallbackError(payload, "gui.gov.jail.error");
                    this.statusIsError = true;
                    disarm();
                    rebuildButtons();
                }
            }
        }
    }


}
