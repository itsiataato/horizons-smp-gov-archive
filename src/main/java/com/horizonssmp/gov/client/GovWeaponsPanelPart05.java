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

abstract class GovWeaponsPanelPart05 extends GovWeaponsPanelPart04 {
    GovWeaponsPanelPart05(final net.minecraft.client.gui.screens.Screen host) {
        super(host);
    }


    void layoutWidgets(final boolean compact) {
        // Classifier input row.
        final GovRect classifyBar = this.topBars[3];
        final int classifyButtonW = compact ? 54 : 58;
        final int heldButtonW = compact ? 42 : 46;
        final int inputX = classifyBar.x() + 4;
        final int inputW = Math.max(0, classifyBar.w() - classifyButtonW - heldButtonW - 16);
        this.classifyFrame = rect(inputX, classifyBar.y() + 1, inputW,
                Math.min(FORM_ROW, Math.max(0, classifyBar.h() - 2)));
        placeBox(this.classifyBox, this.classifyFrame);

        // Permits filter row.
        final GovRect permitBar = this.topBars[1];
        final int loadW = compact ? 40 : 46;
        final int permitLabelW = Math.min(48, Math.max(0, permitBar.w() / 5));
        this.permitFilterFrame = rect(permitBar.x() + permitLabelW + 2, permitBar.y() + 1,
                Math.max(0, permitBar.w() - permitLabelW - loadW - 12),
                Math.min(FORM_ROW, Math.max(0, permitBar.h() - 2)));
        placeBox(this.permitTargetBox, this.permitFilterFrame);

        // Modal rows. The fit box is never larger than the content, so every frame is clamped.
        final int pad = 6;
        final int modalHeaderH = Math.min(this.modalRect.h(), GovMetrics.LINE_TITLE
                + GovMetrics.GAP_XS * 2);
        final int buttonY = this.modalRect.bottom() - 19;
        final int availableRows = Math.max(0,
                buttonY - (this.modalRect.y() + modalHeaderH + 4) - FORM_GAP * 2);
        final int rowH = Math.min(FORM_ROW, availableRows / 3);
        this.modalRowH = rowH;
        this.modalLabelW = GovMetrics.span(this.modalRect.w(), this.modalRect.w() / 3, 40, 64);
        this.modalFieldX = this.modalRect.x() + pad + this.modalLabelW + 4;
        final int fieldW = Math.max(0, this.modalRect.w() - pad * 2 - this.modalLabelW - 4);
        this.modalRow1 = this.modalRect.y() + modalHeaderH + 4;
        this.modalRow2 = this.modalRow1 + rowH + FORM_GAP;
        this.modalRow3 = this.modalRow2 + rowH + FORM_GAP;

        this.modalSerialFrame = rect(this.modalFieldX, this.modalRow1, fieldW, rowH);
        this.modalTypeFrame = rect(this.modalFieldX, this.modalRow2, fieldW, rowH);
        this.modalOwnerFrame = rect(this.modalFieldX, this.modalRow3,
                Math.max(0, fieldW * 58 / 100), rowH);
        this.modalClassFrame = rect(this.modalOwnerFrame.right() + 4, this.modalRow3,
                Math.max(0, fieldW - this.modalOwnerFrame.w() - 4), rowH);
        this.modalIssueClassFrame = rect(this.modalFieldX, this.modalRow2, fieldW, rowH);
        this.modalTransferOwnerFrame = rect(this.modalFieldX, this.modalRow2, fieldW, rowH);
        this.modalTargetFrame = rect(this.modalFieldX, this.modalRow1, fieldW, rowH);
        this.modalReasonFrame = rect(this.modalFieldX, this.modalRow2, fieldW, rowH);
        this.modalMinutesFrame = rect(this.modalFieldX, this.modalRow1, fieldW, rowH);
        this.modalDimFrame = rect(this.modalFieldX, this.modalRow1,
                Math.max(0, fieldW * 50 / 100), rowH);
        this.modalRadiusFrame = rect(this.modalDimFrame.right() + 4, this.modalRow1,
                Math.max(0, fieldW * 20 / 100), rowH);
        this.modalAreaMinutesFrame = rect(this.modalRadiusFrame.right() + 4, this.modalRow1,
                Math.max(0, fieldW - this.modalDimFrame.w() - this.modalRadiusFrame.w() - 8),
                rowH);
        this.modalXFrame = rect(this.modalFieldX, this.modalRow2,
                Math.max(0, fieldW / 3 - 2), rowH);
        this.modalYFrame = rect(this.modalXFrame.right() + 3, this.modalRow2,
                Math.max(0, fieldW / 3 - 2), rowH);
        this.modalZFrame = rect(this.modalYFrame.right() + 3, this.modalRow2,
                Math.max(0, fieldW - this.modalXFrame.w() * 2 - 6), rowH);
        this.modalAreaReasonFrame = rect(this.modalFieldX, this.modalRow3, fieldW, rowH);

        placeBox(this.registerSerialBox, this.modalSerialFrame);
        placeBox(this.registerTypeBox, this.modalTypeFrame);
        placeBox(this.ownerBox, this.modalOwnerFrame);
        placeBox(this.transferOwnerBox, this.modalTransferOwnerFrame);
        placeBox(this.issueTargetBox, this.modalTargetFrame);
        placeBox(this.reasonBox, this.modalReasonFrame);
        placeBox(this.minutesBox, this.modalMinutesFrame);
        placeBox(this.areaMinutesBox, this.modalAreaMinutesFrame);
        placeBox(this.dimBox, this.modalDimFrame);
        placeBox(this.xBox, this.modalXFrame);
        placeBox(this.yBox, this.modalYFrame);
        placeBox(this.zBox, this.modalZFrame);
        placeBox(this.radiusBox, this.modalRadiusFrame);
        placeBox(this.areaReasonBox, this.modalAreaReasonFrame);
    }

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

        // Registry, Permits and Areas are list modes with shell chrome.
        layoutListMode(0, false, height >= 140, left, top, right, bottom, topH, bottomH, statusH);
        layoutListMode(1, true, height >= 140, left, top, right, bottom, topH, bottomH, statusH);
        layoutListMode(2, false, height >= 140, left, top, right, bottom, topH, bottomH, statusH);

        // Classifier is a form with a single input row and no bottom bar.
        this.topBars[3] = rect(left, top, width, topH);
        this.bottoms[3] = EMPTY;
        this.statuses[3] = rect(left, bottom - statusH, width, statusH);
        this.bodies[3] = rect(left, this.topBars[3].bottom() + 2, width,
                this.statuses[3].y() - this.topBars[3].bottom() - 2);
        this.actions[3] = EMPTY;
        this.lists[3] = EMPTY;
        this.listBodies[3] = EMPTY;
        this.details[3] = this.bodies[3];
        this.summaries[3] = EMPTY;

        this.modalRect = GovMetrics.fit(this.bounds, 320, 180);
        layoutWidgets(compact);
        updateListMetrics();
        rebuildButtons();
        rebuildModalButtons();
        rebuildDetailLines();
        if (!this.classification.isEmpty()) {
            rebuildClassification();
        }
        updateFilterLabel();
    }

    void requestWeaponDetail(final String serial) {
        if (serial == null || serial.isBlank()) {
            return;
        }
        final String reqId = UUID.randomUUID().toString();
        this.pending.put(reqId, RequestKind.DETAIL);
        this.latestDetailReqId = reqId;
        this.detailState = LoadState.LOADING;
        this.detailError = "";
        GovNetwork.sendRequest(OpCode.WPN_INFO, Map.of(WeaponKeys.KEY_SERIAL, serial), reqId);
    }

    void requestClassify(final boolean held) {
        if (mutationPending()) {
            return;
        }
        final String itemId = held ? "" : (this.classifyBox == null
                ? "" : this.classifyBox.getValue().strip());
        if (!held && itemId.isEmpty()) {
            this.status = this.textClassifyRequired;
            this.statusIsError = true;
            return;
        }
        final String reqId = UUID.randomUUID().toString();
        this.pending.put(reqId, RequestKind.CLASSIFY);
        GovNetwork.sendRequest(OpCode.WPN_CLASSIFY, Map.of(WeaponKeys.KEY_ITEM_ID, itemId), reqId);
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

    static String fallbackError(final GovActionResultPayload payload, final String key) {
        return payload.errorText().isBlank() ? tr(key) : payload.errorText();
    }

    void refreshSection() {
        if (this.subtab == 0) {
            requestWeaponList();
            if (!this.selectedSerial.isEmpty()) {
                requestWeaponDetail(this.selectedSerial);
            }
        } else if (this.subtab == 1) {
            requestPermitList();
        } else if (this.subtab == 2) {
            requestAreaList();
        }
    }

    // ------------------------------------------------------------- data updates

    static int intField(final Map<String, String> fields, final String key) {
        return GovFields.intField(fields, key);
    }

    static boolean strongStatus(final String text) {
        return text != null && (text.equals(caps(tr("gui.gov.weapons.status.blocked")))
                || text.equals(caps(tr("gui.gov.weapons.status.no_permit")))
                || text.equals(caps(tr("gui.gov.weapons.status.revoked")))
                || text.equals(caps(tr("gui.gov.weapons.status.expired")))
                || text.equals(caps(tr("gui.gov.weapons.status.suspended"))));
    }

    void applyPermitList(final String delta) {
        this.detailRevision++;
        final Map<String, String> fields = GovFields.decode(delta);
        this.canRegister = "true".equals(fields.get(WeaponKeys.CAN_REGISTER));
        this.canMilitary = "true".equals(fields.get(WeaponKeys.CAN_MILITARY));
        this.permitTotal = intField(fields, WeaponKeys.PERMIT_TOTAL);
        this.permitPage = intField(fields, WeaponKeys.PERMIT_PAGE);
        this.permitPages = Math.max(1, intField(fields, WeaponKeys.PERMIT_PAGES));
        this.permitRows.clear();
        for (int i = 0; i < WeaponKeys.PERMIT_PAGE_SIZE; i++) {
            final String prefix = WeaponKeys.PERMIT_PREFIX + i;
            final String id = fields.get(prefix + WeaponKeys.PERMIT_ID);
            if (id == null || id.isBlank()) {
                continue;
            }
            final String classId = fields.getOrDefault(prefix + WeaponKeys.PERMIT_CLASS, "");
            final String rawStatus = fields.getOrDefault(prefix + WeaponKeys.PERMIT_STATUS, "");
            final String statusText = statusText(rawStatus);
            final String classLabel = permitLabel(classId);
            final String holder = fields.getOrDefault(prefix + WeaponKeys.PERMIT_HOLDER, "");
            final long expiresAt = longField(fields, prefix + WeaponKeys.PERMIT_EXPIRES_AT);
            final String expiry = expiresAt > 0L ? when(expiresAt)
                    : tr("gui.gov.weapons.no_expiry");
            final String lineOne = holder + "  " + classLabel;
            final String lineTwo = statusText + "  " + expiry;
            final int statusWidth = this.font == null ? 0 : this.font.width(statusText);
            this.permitRows.add(new PermitRow(
                    id, holder,
                    fields.getOrDefault(prefix + WeaponKeys.PERMIT_HOLDER_UUID, ""),
                    classId, classLabel, rawStatus, statusText,
                    fields.getOrDefault(prefix + WeaponKeys.PERMIT_ISSUED_BY, ""),
                    longField(fields, prefix + WeaponKeys.PERMIT_ISSUED_AT), expiresAt, expiry,
                    fields.getOrDefault(prefix + WeaponKeys.PERMIT_REASON, ""),
                    "true".equals(fields.get(prefix + WeaponKeys.PERMIT_MILITARY)),
                    "true".equals(fields.get(prefix + WeaponKeys.PERMIT_CAN_MANAGE)),
                    lineOne, lineTwo, strongStatus(statusText), statusWidth));
        }
        this.listState = LoadState.READY;
        this.listError = "";
        this.listScrollRows[1] = 0;
        updateListTexts(1);
        updateSummaryTexts();
        boolean visible = false;
        for (final PermitRow row : this.permitRows) {
            if (row.id().equals(this.selectedPermitId)) {
                visible = true;
                break;
            }
        }
        if (!this.selectedPermitId.isEmpty() && !visible) {
            this.selectedPermitId = "";
            disarm();
        }
        if (this.selectedPermitId.isEmpty() && !this.permitRows.isEmpty()
                && this.subtab == 1) {
            this.selectedPermitId = this.permitRows.get(0).id();
        }
        updateSummaryTexts();
        updateListMetrics();
        rebuildButtons();
        rebuildDetailLines();
        syncWidgets();
    }

}
