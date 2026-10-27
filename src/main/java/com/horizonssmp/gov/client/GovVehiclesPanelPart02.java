package com.horizonssmp.gov.client;

import com.horizonssmp.gov.client.GovDashboardScreen;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.horizonssmp.gov.common.GovFields;
import com.horizonssmp.gov.common.LicenseKeys;
import com.horizonssmp.gov.common.OpCode;
import com.horizonssmp.gov.common.VehicleKeys;
import com.horizonssmp.gov.network.GovActionResultPayload;
import com.horizonssmp.gov.network.GovNetwork;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import com.horizonssmp.gov.client.GovVehiclesTypes.*;
import static com.horizonssmp.gov.client.GovVehiclesConstants.*;
abstract class GovVehiclesPanelPart02 extends GovVehiclesPanelPart01 {
    GovVehiclesPanelPart02(final net.minecraft.client.gui.screens.Screen host) {
        super(host);
    }

    void computeRegisterGeometry() {
        final int innerW = this.area.w() - this.pad * 2;
        final int formW = Math.min(innerW, 560);
        final int formX = this.area.x() + (this.area.w() - formW) / 2;
        final int formTop = this.area.y() + 2
                + (this.showTitle ? GovMetrics.LINE_TITLE + 2 : 0);
        final int formBottom = this.area.bottom() - 2;
        this.registerFormRect = rect(formX, formTop, formW,
                Math.max(0, formBottom - formTop));
        final int labelW = this.compact ? 46 : 60;
        int y = formTop;
        if (formW < 520) {
            final int ownerCol = Math.max(0, formW * 58 / 100);
            final int plateCol = Math.max(0, formW - ownerCol - GovMetrics.GAP_XS);
            final int rightX = formX + ownerCol + GovMetrics.GAP_XS;
            this.ownerLabelRect = rect(formX, y, labelW, GovMetrics.FIELD_H);
            final int navW = 14;
            this.ownerNextRect = rect(formX + ownerCol - navW, y, navW, GovMetrics.FIELD_H);
            this.ownerPrevRect = rect(this.ownerNextRect.x() - navW - 2, y, navW,
                    GovMetrics.FIELD_H);
            this.ownerValueRect = rect(this.ownerLabelRect.right() + 3, y,
                    Math.max(0, this.ownerPrevRect.x() - this.ownerLabelRect.right() - 5),
                    GovMetrics.FIELD_H);
            this.plateLabelRect = rect(rightX, y, labelW, GovMetrics.FIELD_H);
            this.plateFieldRect = rect(this.plateLabelRect.right() + 3, y,
                    Math.max(0, plateCol - labelW - 3), GovMetrics.FIELD_H);
            y += GovMetrics.FIELD_H + 3;
        } else {
            this.ownerLabelRect = rect(formX, y, labelW, GovMetrics.FIELD_H);
            final int navW = 14;
            this.ownerNextRect = rect(formX + formW - navW, y, navW, GovMetrics.FIELD_H);
            this.ownerPrevRect = rect(this.ownerNextRect.x() - navW - 2, y, navW,
                    GovMetrics.FIELD_H);
            this.ownerValueRect = rect(this.ownerLabelRect.right() + 3, y,
                    Math.max(0, this.ownerPrevRect.x() - this.ownerLabelRect.right() - 5),
                    GovMetrics.FIELD_H);
            y += GovMetrics.FIELD_H + 3;
            this.plateLabelRect = rect(formX, y, labelW, GovMetrics.FIELD_H);
            this.plateFieldRect = rect(this.plateLabelRect.right() + 3, y,
                    Math.max(0, formW - labelW - 3), GovMetrics.FIELD_H);
            y += GovMetrics.FIELD_H + 3;
        }

        this.classLabelRect = rect(formX, y, labelW, GovMetrics.FIELD_H);
        final int classAreaX = formX + labelW + 3;
        final int classAreaW = Math.max(0, formW - labelW - 3);
        final int cols = formW >= 520 ? 5 : 3;
        final int gap = 3;
        final int buttonW = Math.max(0, (classAreaW - gap * (cols - 1)) / cols);
        for (int i = 0; i < CLASSES.length; i++) {
            final int row = i / cols;
            final int col = i % cols;
            this.classRects[i] = rect(classAreaX + col * (buttonW + gap),
                    y + row * (GovMetrics.FIELD_H + gap), buttonW, GovMetrics.FIELD_H);
        }
        final int classRows = (CLASSES.length + cols - 1) / cols;
        y += classRows * (GovMetrics.FIELD_H + gap);

        final int actionH = 14;
        this.registerButtonRect = rect(formX + formW - Math.min(96, formW / 3),
                formBottom - actionH, Math.min(96, formW / 3), actionH);
        this.registerStatusRect = rect(formX, formBottom - actionH,
                Math.max(0, formW - Math.min(96, formW / 3) - 4), actionH);
        this.registerRuleRect = rect(formX, y, formW, 10);
    }

    void computeImpoundGeometry() {
        final int innerW = this.area.w() - this.pad * 2;
        final int formW = Math.min(innerW, 560);
        final int formX = this.area.x() + (this.area.w() - formW) / 2;
        final int formTop = this.area.y() + 2
                + (this.showTitle ? GovMetrics.LINE_TITLE + 2 : 0);
        final int formBottom = this.area.bottom() - 2;
        this.impoundFormRect = rect(formX, formTop, formW,
                Math.max(0, formBottom - formTop));
        final int labelW = this.compact ? 46 : 60;
        int y = formTop;
        this.impoundSummaryRect = rect(formX, y, formW, 10);
        y += 12;
        if (formW < 520) {
            final int colW = Math.max(0, (formW - GovMetrics.GAP_XS) / 2);
            final int rightX = formX + colW + GovMetrics.GAP_XS;
            this.impoundPlateLabelRect = rect(formX, y, labelW, GovMetrics.FIELD_H);
            this.impoundPlateFieldRect = rect(this.impoundPlateLabelRect.right() + 3, y,
                    Math.max(0, colW - labelW - 3), GovMetrics.FIELD_H);
            this.impoundReasonLabelRect = rect(rightX, y, labelW, GovMetrics.FIELD_H);
            this.impoundReasonFieldRect = rect(this.impoundReasonLabelRect.right() + 3, y,
                    Math.max(0, colW - labelW - 3), GovMetrics.FIELD_H);
            y += GovMetrics.FIELD_H + 3;
        } else {
            this.impoundPlateLabelRect = rect(formX, y, labelW, GovMetrics.FIELD_H);
            this.impoundPlateFieldRect = rect(this.impoundPlateLabelRect.right() + 3, y,
                    Math.max(0, formW - labelW - 3), GovMetrics.FIELD_H);
            y += GovMetrics.FIELD_H + 3;
            this.impoundReasonLabelRect = rect(formX, y, labelW, GovMetrics.FIELD_H);
            this.impoundReasonFieldRect = rect(this.impoundReasonLabelRect.right() + 3, y,
                    Math.max(0, formW - labelW - 3), GovMetrics.FIELD_H);
            y += GovMetrics.FIELD_H + 3;
        }
        this.impoundRuleRect = rect(formX, y, formW, 10);
        y += 12;
        final int actionH = 14;
        final int buttonW = Math.min(96, Math.max(0, formW / 3));
        this.impoundButtonRect = rect(formX, formBottom - actionH, buttonW, actionH);
        this.releaseButtonRect = rect(this.impoundButtonRect.right() + 4,
                formBottom - actionH, buttonW, actionH);
        this.impoundStatusRect = rect(this.releaseButtonRect.right() + 4,
                formBottom - actionH,
                Math.max(0, formX + formW - this.releaseButtonRect.right() - 4), actionH);
    }

    void computeLicenseGeometry() {
        final GovRect formArea = this.detailRect.w() > 0 ? this.detailRect : this.area;
        final int innerW = formArea.w() - this.pad * 2;
        final int formW = Math.min(innerW, 560);
        final int formX = formArea.x() + (formArea.w() - formW) / 2;
        final int formTop = formArea.y() + 2
                + (this.showTitle ? GovMetrics.LINE_TITLE + 2 : 0);
        final int formBottom = formArea.bottom() - 2;
        this.licenseFormRect = rect(formX, formTop, formW,
                Math.max(0, formBottom - formTop));
        final int labelW = this.compact ? 46 : 60;
        int y = formTop;
        this.licensePrevRect = rect(formX, y, 14, GovMetrics.FIELD_H);
        this.licenseNextRect = rect(formX + 16, y, 14, GovMetrics.FIELD_H);
        this.licenseSummaryRect = rect(formX + 34, y, Math.max(0, formW - 34),
                GovMetrics.FIELD_H);
        y += GovMetrics.FIELD_H + 3;
        this.licenseInfoRect = rect(formX, y, formW, 10);
        y += 12;
        this.licenseTargetLabelRect = rect(formX, y, labelW, GovMetrics.FIELD_H);
        this.licenseTargetFieldRect = rect(formX + labelW + 3, y,
                Math.max(0, formW - labelW - 3), GovMetrics.FIELD_H);
        y += GovMetrics.FIELD_H + 3;
        this.licenseClassLabelRect = rect(formX, y, labelW, GovMetrics.FIELD_H);
        this.licenseClassFieldRect = rect(formX + labelW + 3, y,
                Math.max(0, formW - labelW - 3), GovMetrics.FIELD_H);
        y += GovMetrics.FIELD_H + 3;
        this.licensePointsLabelRect = rect(formX, y, labelW, GovMetrics.FIELD_H);
        final int pointsAreaX = formX + labelW + 3;
        final int pointsAreaW = Math.max(0, formW - labelW - 3);
        final int pointW = Math.min(34, Math.max(0, (pointsAreaW - 16) / 4));
        for (int i = 0; i < POINTS.length; i++) {
            this.pointRects[i] = rect(pointsAreaX + i * (pointW + 4), y, pointW,
                    GovMetrics.FIELD_H);
        }
        this.licensePointsHintRect = rect(pointsAreaX + POINTS.length * (pointW + 4), y,
                Math.max(0, pointsAreaW - POINTS.length * (pointW + 4)), GovMetrics.FIELD_H);
        y += GovMetrics.FIELD_H + 3;
        this.licenseReasonLabelRect = rect(formX, y, labelW, GovMetrics.FIELD_H);
        this.licenseReasonFieldRect = rect(this.licenseReasonLabelRect.right() + 3, y,
                Math.max(0, formW - labelW - 3), GovMetrics.FIELD_H);
        y += GovMetrics.FIELD_H + 3;
        this.licenseRuleRect = rect(formX, y, formW, 10);
        y += 12;

        final int actionH = 14;
        final int actionW = Math.min(58, Math.max(0, formW / 4));
        this.licenseIssueRect = rect(formX, formBottom - actionH, actionW, actionH);
        this.licenseAssessRect = rect(this.licenseIssueRect.right() + 4,
                formBottom - actionH, actionW, actionH);
        this.licenseRevokeRect = rect(this.licenseAssessRect.right() + 4,
                formBottom - actionH, actionW, actionH);
        this.licenseStatusRect = rect(this.licenseRevokeRect.right() + 4,
                formBottom - actionH,
                Math.max(0, formX + formW - this.licenseRevokeRect.right() - 4), actionH);
    }

    void positionFields() {
        this.fieldRows.clear();
        addField(TAB_SCAN, this.scanLabelRect, this.scanFieldRect, this.scanBox,
                "gui.gov.vehicles.field.plate");
        addField(TAB_REGISTER, this.plateLabelRect, this.plateFieldRect, this.plateBox,
                "gui.gov.vehicles.field.plate");
        addField(TAB_IMPOUND, this.impoundPlateLabelRect, this.impoundPlateFieldRect,
                this.impoundPlateBox, "gui.gov.vehicles.field.plate");
        addField(TAB_IMPOUND, this.impoundReasonLabelRect, this.impoundReasonFieldRect,
                this.impoundReasonBox, "gui.gov.vehicles.field.reason");
        addField(TAB_VEHICLE_LICENSES, this.licenseTargetLabelRect, this.licenseTargetFieldRect,
                this.licenseTargetBox, "gui.gov.vehicles.field.owner");
        addField(TAB_VEHICLE_LICENSES, this.licenseClassLabelRect, this.licenseClassFieldRect,
                this.licenseClassBox, "gui.gov.vehicles.field.class");
        addField(TAB_VEHICLE_LICENSES, this.licenseReasonLabelRect, this.licenseReasonFieldRect,
                this.licenseReasonBox, "gui.gov.vehicles.field.reason");
    }

    void addField(final int tab, final GovRect labelRect, final GovRect fieldRect,
            final EditBox box, final String labelKey) {
        if (box == null || fieldRect.w() <= 0) {
            return;
        }
        final String label = GovText.clip(this.font, label(labelKey), labelRect.w());
        this.fieldRows.add(new FieldRow(tab, labelRect, fieldRect, box, label));
        layoutBox(box, fieldRect);
    }

    void layoutBox(final EditBox box, final GovRect rect) {
        if (box == null) {
            return;
        }
        box.setX(rect.x() + 3);
        box.setY(rect.y() + 3);
        box.setWidth(Math.max(0, rect.w() - 6));
    }

    GovRect rect(final int x, final int y, final int width, final int height) {
        return new GovRect(x, y, Math.max(0, width), Math.max(0, height)).clamp(this.area);
    }

    /** A retry sits bottom-centre of the viewport that failed, never outside it. */
    // ------------------------------------------------------------------- labels

    void rebuildLabels() {
        if (!this.labels.isEmpty()) {
            return;
        }
        for (final String key : LABEL_KEYS) {
            this.labels.put(key, up(tr(key)));
        }
        for (final String id : CLASSES) {
            this.classNames.put(up(id), up(tr("gui.gov.vehicles.class." + id)));
        }
        this.classShortNames.put(up("helicopter"),
                up(tr("gui.gov.vehicles.class.helicopter.short")));
        for (final String id : new String[]{"ACTIVE", "IMPOUNDED", "DISPOSED", "FLAGGED",
            "VALID"}) {
            this.statusNames.put(id, up(tr("gui.gov.vehicles.state."
                    + id.toLowerCase(Locale.ROOT))));
        }
        this.columnHeaders[0] = GovText.boldCached(label("gui.gov.vehicles.field.plate"));
        this.columnHeaders[1] = GovText.boldCached(label("gui.gov.vehicles.field.owner"));
        this.columnHeaders[2] = GovText.boldCached(label("gui.gov.vehicles.field.class"));
        this.columnHeaders[3] = GovText.boldCached(label("gui.gov.vehicles.field.status"));
        this.columnHeaders[4] = GovText.boldCached(label("gui.gov.vehicles.field.license"));
    }

}
