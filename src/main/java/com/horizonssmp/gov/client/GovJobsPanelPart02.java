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
abstract class GovJobsPanelPart02 extends GovJobsPanelPart01 {
    GovJobsPanelPart02(final net.minecraft.client.gui.screens.Screen host) {
        super(host);
    }

    void layoutModals() {
        final GovRect container = this.rect.w() > 0 && this.rect.h() > 0 ? this.rect : this.body;
        this.modalRect = GovMetrics.fit(container, 320, 160);
        if (this.modalRect.w() <= 0 || this.modalRect.h() <= 0) {
            this.modalClose = EMPTY;
            this.modalBody = EMPTY;
            this.createLabel = EMPTY;
            this.createField = EMPTY;
            this.createSubmit = EMPTY;
            this.createCancel = EMPTY;
            this.pickerList = EMPTY;
            this.pickerCancel = EMPTY;
            return;
        }
        this.modalBody = new GovRect(this.modalRect.x() + 6, this.modalRect.y() + 22,
                Math.max(0, this.modalRect.w() - 12),
                Math.max(0, this.modalRect.h() - 28)).clamp(this.modalRect);
        // The header band is 20 px tall; an 11x9 close box sits inside it without touching the
        // title, which is clipped to leave this reserve free. A modal too small for both drops
        // the close box rather than drawing it outside the frame.
        this.modalClose = this.modalRect.w() >= CLOSE_W + 8 && this.modalRect.h() >= 20
                ? new GovRect(this.modalRect.right() - 13, this.modalRect.y() + 5,
                        CLOSE_W, CLOSE_H)
                : EMPTY;

        final int labelWidth = GovMetrics.clamp(this.modalBody.w() * 30 / 100, 56, 100);
        final int rowHeight = 14;
        this.createLabel = new GovRect(this.modalBody.x(), this.modalBody.y(),
                labelWidth, rowHeight).clamp(this.modalBody);
        this.createLabelText = GovText.clip(this.font, this.labelName,
                Math.max(0, labelWidth - 2));
        this.createField = new GovRect(this.createLabel.right() + 4, this.modalBody.y(),
                Math.max(0, this.modalBody.w() - labelWidth - 4), rowHeight)
                .clamp(this.modalBody);

        final int actionY = this.modalBody.bottom() - GovMetrics.BUTTON_H;
        final int actionWidth = GovMetrics.clamp((this.modalBody.w() - 4) / 2, 40, 120);
        this.createCancel = new GovRect(this.modalBody.right() - actionWidth, actionY,
                actionWidth, GovMetrics.BUTTON_H).clamp(this.modalBody);
        this.createSubmit = new GovRect(this.createCancel.x() - 4 - actionWidth, actionY,
                actionWidth, GovMetrics.BUTTON_H).clamp(this.modalBody);

        this.pickerCancel = new GovRect(this.modalBody.x(), actionY,
                GovMetrics.clamp(this.modalBody.w() / 3, 40, 80), GovMetrics.BUTTON_H)
                .clamp(this.modalBody);
        this.pickerList = new GovRect(this.modalBody.x(), this.modalBody.y(),
                this.modalBody.w(), Math.max(0, actionY - 4 - this.modalBody.y()))
                .clamp(this.modalBody);
    }


    void positionBox(final EditBox box, final GovRect rect) {
        if (box == null) {
            return;
        }
        box.setX(rect.x() + 3);
        box.setY(rect.y() + 3);
        box.setWidth(Math.max(0, rect.w() - 6));
    }


    void syncCreateBox() {
        if (this.createBox == null) {
            return;
        }
        final boolean visible = this.active && this.modal == Modal.CREATE;
        if (visible != this.createBoxVisible) {
            this.createBoxVisible = visible;
            this.createBox.setVisible(visible);
            if (!visible && this.host.getFocused() == this.createBox) {
                this.host.setFocused(null);
            }
        }
    }


    void clearFocus() {
        if (this.createBox != null && this.host.getFocused() == this.createBox) {
            this.host.setFocused(null);
        }
    }

    /**
     * Blurs the create field only while it is visible. A hidden field can still hold focus after a
     * modal change, so visibility is part of the test rather than an incidental detail.
     */

    boolean blurVisibleField() {
        if (this.createBox != null && this.createBox.isVisible()
                && this.host.getFocused() == this.createBox) {
            this.host.setFocused(null);
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ requests


    void requestListIfNeeded() {
        if (!this.pending.containsValue(RequestKind.LIST) && this.listState != ListState.READY) {
            requestList();
        }
    }


    void requestList() {
        final String reqId = UUID.randomUUID().toString();
        this.pending.put(reqId, RequestKind.LIST);
        this.latestListReqId = reqId;
        this.listState = ListState.LOADING;
        this.listError = "";
        GovNetwork.sendRequest(OpCode.JOB_LIST, Map.of(), reqId);
        rebuildCaches();
    }


    void requestInfo(final String jobId) {
        if (jobId == null || jobId.isBlank()) {
            return;
        }
        final String reqId = UUID.randomUUID().toString();
        this.pending.put(reqId, RequestKind.INFO);
        this.latestInfoReqId = reqId;
        this.detailState = DetailState.LOADING;
        this.detailError = "";
        GovNetwork.sendRequest(OpCode.JOB_INFO, Map.of(JobKeys.KEY_JOB, jobId), reqId);
        rebuildCaches();
    }


    void mutate(final OpCode op, final Map<String, String> payload) {
        if (mutationPending()) {
            return;
        }
        final String reqId = UUID.randomUUID().toString();
        this.pending.put(reqId, RequestKind.MUTATE);
        GovNetwork.sendRequest(op, payload, reqId);
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
                    this.listState = ListState.ERROR;
                    this.listError = fallbackError(payload, "gui.gov.jobs.error");
                    rebuildCaches();
                }
            }
            case INFO -> {
                if (!payload.reqId().equals(this.latestInfoReqId)) {
                    return;
                }
                if (payload.ok()) {
                    applyInfo(payload.delta());
                } else {
                    this.detailState = DetailState.ERROR;
                    this.detailError = fallbackError(payload, "gui.gov.jobs.error");
                    rebuildCaches();
                }
            }
            case MUTATE -> {
                if (payload.ok()) {
                    this.status = payload.delta().isBlank() ? this.doneText : payload.delta();
                    this.statusIsError = false;
                    this.modal = Modal.NONE;
                    disarmAll();
                    if (this.createBox != null) {
                        this.createBox.setValue("");
                    }
                    syncCreateBox();
                    requestList();
                    if (!this.selectedJobId.isEmpty()) {
                        requestInfo(this.selectedJobId);
                    }
                } else {
                    this.status = fallbackError(payload, "gui.gov.jobs.error");
                    this.statusIsError = true;
                    this.modal = Modal.NONE;
                    disarmAll();
                    syncCreateBox();
                }
                rebuildCaches();
            }
        }
    }


}
