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
abstract class GovVehiclesPanelPart01 extends GovVehiclesPanelAbstracts {
    GovVehiclesPanelPart01(final net.minecraft.client.gui.screens.Screen host) {
        super(host);
    }

    /** Creates the five fields once and returns the same list on every call. */
    List<EditBox> createWidgets(final Font font) {
        this.font = font;
        if (!this.widgets.isEmpty()) {
            return this.widgets;
        }
        this.scanBox = box(font, 8, "gui.gov.vehicles.scan_hint");
        this.plateBox = box(font, 8, "gui.gov.vehicles.plate_hint");
        this.impoundPlateBox = box(font, 8, "gui.gov.vehicles.plate_hint");
        this.impoundReasonBox = box(font, 200, "gui.gov.vehicles.reason_hint");
        this.licenseReasonBox = box(font, 200, "gui.gov.vehicles.reason_hint");
        this.licenseTargetBox = box(font, 16, "gui.gov.vehicles.owner_hint");
        this.licenseClassBox = box(font, 12, "gui.gov.vehicles.class_hint");
        this.widgets = List.of(this.scanBox, this.plateBox, this.impoundPlateBox,
                this.impoundReasonBox, this.licenseReasonBox, this.licenseTargetBox,
                this.licenseClassBox);
        return this.widgets;
    }

    EditBox box(final Font font, final int maxLength, final String hintKey) {
        final EditBox box = GovWidgets.field(font, 0, 0, 10, ROW_TEXT,
                Component.translatable(hintKey));
        box.setMaxLength(maxLength);
        box.setVisible(false);
        return box;
    }

    /** Shows or hides the panel, its fields and its armed confirmation. */
    void setActive(final boolean active) {
        this.active = active;
        if (!active) {
            disarm();
            this.enterRepeat.reset();
            unfocusAll();
        } else {
            requestListIfNeeded();
        }
        syncWidgets();
        rebuildButtons();
        rebuildHits();
    }

    /** Forgets every request in flight; called when the screen closes. */
    void reset() {
        this.pending.clear();
        this.latestListReqId = "";
        this.latestDetailReqId = "";
        setStatus("", false);
        disarm();
        unfocusAll();
        syncWidgets();
    }

    /**
     * Selects the shell's sub-category: 0 Registry, 1 Plate Scan, 2 Register, 3 Impound,
     * 4 Licences.
     */
    void setSubtab(final int index) {
        final int next = GovMetrics.clamp(index, 0, TAB_VEHICLE_LICENSES);
        if (next == this.subtab) {
            return;
        }
        this.scrollByTab[this.subtab] = this.listScroll;
        this.pageByTab[this.subtab] = this.page;
        this.subtab = next;
        this.listScroll = this.scrollByTab[next];
        if (next == TAB_REGISTRY) {
            this.page = this.pageByTab[TAB_REGISTRY];
        }
        this.status = "";
        this.statusIsError = false;
        disarm();
        unfocusAll();
        if (next == TAB_IMPOUND) {
            prefillImpoundPlate();
        }
        if (next == TAB_VEHICLE_LICENSES || next == TAB_DRIVERS_LICENSE) {
            ensureVehicleSelection();
        }
        syncWidgets();
        requestListIfNeeded();
        rebuildAll();
    }

    /** Recomputes every rectangle inside the content area. Called on init and resize only. */
    void layout(final int left, final int top, final int right, final int bottom,
            final Font font) {
        this.font = font;
        final int width = Math.max(0, right - left);
        final int height = Math.max(0, bottom - top);
        this.area = new GovRect(left, top, width, height);
        this.compact = width < 420 || height < 120;
        this.pad = this.compact ? GovMetrics.GAP_XS : GovMetrics.GAP_S;
        this.rowH = this.compact ? GovMetrics.ROW_H_COMPACT : GovMetrics.ROW_H;
        this.showTitle = height >= 150;
        rebuildLabels();

        computeListGeometry();
        computeScanGeometry();
        computeRegisterGeometry();
        computeImpoundGeometry();
        computeLicenseGeometry();
        positionFields();
        syncWidgets();
        rebuildAll();
    }

    void computeListGeometry() {
        final int innerLeft = this.area.x() + this.pad;
        final int innerW = this.area.w() - this.pad * 2;
        this.titleRect = rect(this.area.x() + this.pad, this.area.y() + 2, innerW,
                GovMetrics.LINE_TITLE + 2);
        int y = this.area.y() + 2 + (this.showTitle ? GovMetrics.LINE_TITLE + 2 : 0);
        final int barH = this.compact ? 14 : GovMetrics.BUTTON_H;
        this.topBar = rect(innerLeft, y, innerW, barH);
        y = this.topBar.bottom() + 2;
        final int headerH = this.compact ? 10 : 11;
        this.tableHeader = rect(innerLeft, y, innerW, headerH);
        y = this.tableHeader.bottom() + 1;

        final int bottomH = this.compact ? 14 : GovMetrics.BUTTON_H;
        this.bottomBar = rect(innerLeft, this.area.bottom() - 2 - bottomH, innerW, bottomH);
        final int buttonH = this.compact ? 12 : 14;
        final int buttonY = this.bottomBar.y() + (this.bottomBar.h() - buttonH) / 2;
        this.prevRect = rect(this.bottomBar.x(), buttonY, 34, buttonH);
        this.nextRect = rect(this.prevRect.right() + 4, buttonY, 34, buttonH);
        final int refreshW = Math.min(this.compact ? 46 : 56, this.bottomBar.w() / 3);
        this.refreshRect = rect(this.bottomBar.right() - refreshW, buttonY, refreshW, buttonH);
        final int pageX = this.nextRect.right() + 6;
        this.pageTextRect = rect(pageX, buttonY,
                Math.max(0, this.refreshRect.x() - pageX - 4), buttonH);

        if (this.area.w() >= 340) {
            final int listW = Math.max(0, innerW * 55 / 100);
            this.listRect = rect(innerLeft, y, listW,
                    Math.max(0, this.bottomBar.y() - 2 - y));
            this.detailRect = rect(this.listRect.right() + GovMetrics.GAP_XS, y,
                    Math.max(0, innerW - listW - GovMetrics.GAP_XS),
                    Math.max(0, this.bottomBar.y() - 2 - y));
            this.detailActionRect = rect(this.detailRect.x() + 4,
                    this.detailRect.bottom() - 18,
                    Math.max(0, this.detailRect.w() - 8), 14);
            this.detailBodyRect = rect(this.detailRect.x() + 1, this.detailRect.y() + 18,
                    Math.max(0, this.detailRect.w() - 2),
                    Math.max(0, this.detailActionRect.y() - 2 - (this.detailRect.y() + 18)));
            this.summaryRect = NONE;
        } else {
            this.summaryRect = rect(innerLeft, this.bottomBar.y() - 11, innerW, 10);
            this.listRect = rect(innerLeft, y, innerW,
                    Math.max(0, this.summaryRect.y() - 1 - y));
            this.detailRect = NONE;
            this.detailBodyRect = NONE;
            this.detailActionRect = NONE;
        }

        final int gap = 4;
        final int filterW = Math.max(0, (innerW - gap * 3) / 4);
        for (int i = 0; i < FILTERS.length; i++) {
            this.filterRects[i] = rect(innerLeft + i * (filterW + gap), this.topBar.y(),
                    filterW, this.topBar.h());
        }
        this.listRetryRect = retryRect(this.listRect);
        this.detailRetryRect = retryRect(this.detailBodyRect);
        computeColumns();
    }

    void computeColumns() {
        if (this.listRect.w() <= 0) {
            for (int i = 0; i < 5; i++) {
                this.columnX[i] = 0;
                this.columnW[i] = 0;
            }
            return;
        }
        final int inner = Math.max(0, this.listRect.w() - 8);
        final int[] share = {22, 20, 16, 24, 18};
        int x = this.listRect.x() + 4;
        int used = 0;
        for (int i = 0; i < 5; i++) {
            final int width = i == 4 ? Math.max(0, inner - used) : inner * share[i] / 100;
            this.columnX[i] = x;
            this.columnW[i] = Math.max(0, width);
            x += width;
            used += width;
        }
    }

    void computeScanGeometry() {
        final int innerW = this.area.w() - this.pad * 2;
        final int formW = Math.min(innerW, 560);
        final int formX = this.area.x() + (this.area.w() - formW) / 2;
        final int formTop = this.area.y() + 2
                + (this.showTitle ? GovMetrics.LINE_TITLE + 2 : 0);
        this.scanFormRect = rect(formX, formTop, formW,
                Math.max(0, this.area.bottom() - 2 - formTop));
        final int labelW = this.compact ? 40 : 54;
        final int buttonW = this.compact ? 38 : 48;
        this.scanLabelRect = rect(formX, formTop, labelW, GovMetrics.FIELD_H);
        this.scanButtonRect = rect(formX + formW - buttonW, formTop, buttonW,
                GovMetrics.FIELD_H);
        this.scanFieldRect = rect(this.scanLabelRect.right() + 3, formTop,
                Math.max(0, this.scanButtonRect.x() - this.scanLabelRect.right() - 7),
                GovMetrics.FIELD_H);
        final int cardTop = formTop + GovMetrics.FIELD_H + 4;
        this.scanCardRect = rect(formX, cardTop, formW,
                Math.max(0, this.area.bottom() - 2 - 12 - cardTop));
        this.scanStatusRect = rect(formX, this.area.bottom() - 12, formW, 10);
    }

}
