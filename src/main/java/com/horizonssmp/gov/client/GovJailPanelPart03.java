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

abstract class GovJailPanelPart03 extends GovJailPanelPart02 {
    GovJailPanelPart03(final net.minecraft.client.gui.screens.Screen host) {
        super(host);
    }

    void applyList(final String delta) {
        this.detailRevision++;
        final Map<String, String> fields = GovFields.decode(delta);
        this.canDetain = "true".equals(fields.get(JailKeys.CAN_DETAIN));
        this.canRelease = "true".equals(fields.get(JailKeys.CAN_RELEASE));
        this.canViewAll = "true".equals(fields.get(JailKeys.CAN_VIEW_ALL));
        ((GovDashboardScreen) this.host).setSubtabVisibility(GovDashboardScreen.Tab.JAIL,
                new boolean[] {true, this.canDetain, this.canRelease, this.canRelease,
                        this.canDetain});
        this.nativeCuffs = !"false".equals(fields.get(JailKeys.NATIVE_CUFFS));
        this.maxNoWarrantMinutes = intField(fields, JailKeys.MAX_NO_WARRANT_MINUTES,
                JailKeys.HARD_MAX_NO_WARRANT_MINUTES);
        this.maxWarrantMinutes = intField(fields, JailKeys.MAX_WARRANT_MINUTES,
                JailKeys.DEFAULT_MAX_WARRANT_MINUTES);
        this.cellConfigured = "true".equals(fields.get(JailKeys.CELL_CONFIGURED));
        this.adapter = fields.getOrDefault(JailKeys.ADAPTER, "none");
        this.cell = fields.getOrDefault(JailKeys.CELL, "");
        this.viewerUuid = fields.getOrDefault(JailKeys.VIEWER_UUID, "");
        this.viewerName = fields.getOrDefault(JailKeys.VIEWER_NAME, "");
        this.total = intField(fields, JailKeys.TOTAL, 0);
        this.activeTotal = intField(fields, JailKeys.ACTIVE_TOTAL, 0);
        this.page = intField(fields, JailKeys.PAGE, 0);
        this.pages = Math.max(1, intField(fields, JailKeys.PAGES, 1));
        this.filter = fields.getOrDefault(JailKeys.FILTER, JailKeys.FILTER_ACTIVE);

        this.rows.clear();
        this.activeRows.clear();
        for (int i = 0; i < JailKeys.PAGE_SIZE; i++) {
            final String prefix = JailKeys.ROW_PREFIX + i;
            final String id = fields.get(prefix + JailKeys.ROW_ID);
            if (id == null || id.isBlank()) {
                continue;
            }
            final String basis = fields.getOrDefault(prefix + JailKeys.ROW_BASIS, "");
            final String rawStatus = fields.getOrDefault(prefix + JailKeys.ROW_STATUS, "");
            final long remaining = longField(fields, prefix + JailKeys.ROW_REMAINING_MS);
            final String target = fields.getOrDefault(prefix + JailKeys.ROW_TARGET, "");
            final String officer = fields.getOrDefault(prefix + JailKeys.ROW_OFFICER, "");
            final String basisText = basisLabel(basis);
            final String statusText = statusText(rawStatus);
            final String remainingText = remaining(remaining);
            final boolean activeRow = "ACTIVE".equalsIgnoreCase(rawStatus) || remaining > 0L;
            final String lineOne = target + "  /  " + officer;
            final String lineTwo = basisText + "  " + remainingText + "  " + statusText;
            final String summary = target + "  /  " + basisText + "  /  " + remainingText
                    + "  /  " + statusText;
            final int statusWidth = this.font == null ? 0 : this.font.width(statusText);
            final JailRow row = new JailRow(
                    id, target,
                    fields.getOrDefault(prefix + JailKeys.ROW_TARGET_UUID, ""), basis, basisText,
                    officer, longField(fields, prefix + JailKeys.ROW_STARTED), remaining,
                    remainingText,
                    "true".equals(fields.get(prefix + JailKeys.ROW_CUFFED)), rawStatus,
                    statusText, fields.getOrDefault(prefix + JailKeys.ROW_WARRANT, ""),
                    activeRow, lineOne, lineTwo, strongStatus(statusText), statusWidth, summary);
            this.rows.add(row);
            if (activeRow) {
                this.activeRows.add(row);
            }
        }
        this.listState = LoadState.READY;
        this.listError = "";
        this.listScrollRows[0] = 0;
        this.listScrollRows[2] = 0;
        this.listScrollRows[3] = 0;
        applyCells(fields);
        updateListTexts(this.subtab);
        updateDetainPolicy();
        updateSummaryTexts();

        boolean selectedVisible = false;
        for (final JailRow row : this.rows) {
            if (row.id().equals(this.selectedId)) {
                selectedVisible = true;
                break;
            }
        }
        if (!this.selectedId.isEmpty() && !selectedVisible) {
            this.selectedId = "";
            this.detail.clear();
            this.detailState = LoadState.NONE;
            disarm();
        }
        if (this.selectedId.isEmpty() && !this.rows.isEmpty() && this.subtab == 0) {
            selectRow(this.rows.get(0).id());
        } else if (selectedVisible && this.subtab == 0) {
            requestDetail(this.selectedId);
        }
        ensureActiveSelection();
        updateListMetrics();
        rebuildButtons();
        rebuildDetailLines();
        syncWidgets();
    }


    void applyCells(final Map<String, String> fields) {
    }


    void applyDetail(final String delta) {
        this.detailRevision++;
        final Map<String, String> fields = GovFields.decode(delta);
        final String id = fields.get(JailKeys.ID);
        if (id == null || id.isBlank()) {
            this.detailState = LoadState.ERROR;
            this.detailError = tr("gui.gov.jail.detail_error");
            updateSummaryTexts();
            rebuildDetailLines();
            rebuildButtons();
            return;
        }
        this.selectedId = id;
        this.detail.clear();
        this.detail.putAll(fields);
        this.detailState = LoadState.READY;
        this.detailError = "";
        this.detailScrollRows[0] = 0;
        updateSummaryTexts();
        rebuildDetailLines();
        rebuildButtons();
        syncWidgets();
        ensureActiveSelection();
    }

    // ---------------------------------------------------------------- selection


    void selectRow(final String id) {
        if (id == null || id.isEmpty() || id.equals(this.selectedId)) {
            return;
        }
        this.selectedId = id;
        this.status = "";
        this.statusIsError = false;
        disarm();
        if (this.subtab == 0) {
            this.detailState = LoadState.LOADING;
            requestDetail(id);
        }
        updateSummaryTexts();
        rebuildButtons();
        rebuildDetailLines();
    }


    JailRow selectedRow() {
        for (final JailRow row : this.activeRows) {
            if (row.id().equals(this.selectedId)) {
                return row;
            }
        }
        for (final JailRow row : this.rows) {
            if (row.id().equals(this.selectedId)) {
                return row;
            }
        }
        return null;
    }


    void ensureActiveSelection() {
        if (this.subtab != 2 && this.subtab != 3) {
            return;
        }
        boolean visible = false;
        for (final JailRow row : this.activeRows) {
            if (row.id().equals(this.selectedId)) {
                visible = true;
                break;
            }
        }
        if (!visible) {
            this.selectedId = this.activeRows.isEmpty() ? "" : this.activeRows.get(0).id();
            disarm();
            rebuildButtons();
        }
        updateSummaryTexts();
    }


    void setFilter(final String next) {
        if (next == null || next.equals(this.filter)) {
            return;
        }
        this.filter = next;
        this.page = 0;
        this.listState = LoadState.LOADING;
        updateFilterLabel();
        requestList();
        rebuildButtons();
    }


    void cycleFilter() {
        int index = 0;
        for (int i = 0; i < this.filterIds.length; i++) {
            if (this.filterIds[i].equals(this.filter)) {
                index = i;
                break;
            }
        }
        setFilter(this.filterIds[(index + 1) % this.filterIds.length]);
    }

    // ------------------------------------------------------------- list metrics


    void updateListMetrics() {
        final GovRect list = this.lists[this.subtab];
        final int count = listRowCount();
        final int headerH = this.bounds.h() < 130 || this.listHeader.isEmpty() ? 0 : 10;
        final int rowArea = Math.max(0, list.h() - headerH);
        this.visibleRows = this.rowPitch > 0 ? rowArea / this.rowPitch : 0;
        this.listScrollMaxRows = Math.max(0, count - this.visibleRows);
        this.listScrollRows[this.subtab] = GovMetrics.clamp(this.listScrollRows[this.subtab], 0,
                this.listScrollMaxRows);
        this.listBodies[this.subtab] = rect(list.x(), list.y() + headerH, list.w(),
                Math.max(0, list.h() - headerH));
        this.listScrollbarRect = rect(list.right() - 4, this.listBodies[this.subtab].y(), 3,
                this.listBodies[this.subtab].h());

        final GovRect detail = this.details[this.subtab];
        final int detailArea = Math.max(0, detail.h() - 16);
        this.detailScrollbarRect = rect(detail.right() - 4, detail.y() + 16, 3,
                Math.max(0, detail.h() - 18));
        this.visibleDetailRows = this.detailPitch > 0 ? detailArea / this.detailPitch : 0;
        this.detailScrollMaxRows = Math.max(0, this.detailLines.size() - this.visibleDetailRows);
        this.detailScrollRows[this.subtab] = GovMetrics.clamp(
                this.detailScrollRows[this.subtab], 0, this.detailScrollMaxRows);
        rebuildRowHits();
    }


}
