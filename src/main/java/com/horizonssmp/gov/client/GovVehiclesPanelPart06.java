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
abstract class GovVehiclesPanelPart06 extends GovVehiclesPanelPart05 {
    GovVehiclesPanelPart06(final net.minecraft.client.gui.screens.Screen host) {
        super(host);
    }

    void setFilter(final String next) {
        if (next == null || next.equals(this.listFilter)) {
            return;
        }
        this.pageByTab[TAB_REGISTRY] = 0;
        this.listState = LoadState.LOADING;
        disarm();
        requestList(next, 0);
        rebuildAll();
    }

    void ensureVehicleSelection() {
        if (this.selectedPlate.isEmpty() && !this.rows.isEmpty()) {
            selectVehicle(this.rows.get(0).plate());
        }
    }

    void cycleVehicle(final int delta) {
        if (this.rows.isEmpty()) {
            return;
        }
        int index = -1;
        for (int i = 0; i < this.rows.size(); i++) {
            if (this.rows.get(i).plate().equals(this.selectedPlate)) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            index = 0;
        } else {
            index = GovMetrics.clamp(index + delta, 0, this.rows.size() - 1);
        }
        selectVehicle(this.rows.get(index).plate());
    }

    void prefillImpoundPlate() {
        if (this.impoundPlateBox != null && this.impoundPlateBox.getValue().isBlank()
                && !this.selectedPlate.isEmpty()) {
            this.impoundPlateBox.setValue(this.selectedPlate);
        }
    }

    void syncWidgets() {
        setVisible(this.scanBox,
                this.active && this.subtab == TAB_SCAN && this.canScan);
        setVisible(this.plateBox, this.active && this.subtab == TAB_REGISTER);
        setVisible(this.impoundPlateBox, this.active && this.subtab == TAB_IMPOUND);
        setVisible(this.impoundReasonBox, this.active && this.subtab == TAB_IMPOUND);
        setVisible(this.licenseReasonBox, this.active && this.subtab == TAB_VEHICLE_LICENSES);
        setVisible(this.licenseTargetBox, this.active && this.subtab == TAB_VEHICLE_LICENSES);
        setVisible(this.licenseClassBox, this.active && this.subtab == TAB_VEHICLE_LICENSES);
    }

    void setVisible(final EditBox box, final boolean visible) {
        if (box == null) {
            return;
        }
        box.setVisible(visible);
        if (!visible && this.host.getFocused() == box) {
            this.host.setFocused(null);
        }
    }

    void unfocusAll() {
        if (this.widgets.isEmpty()) {
            return;
        }
        for (int i = 0; i < this.widgets.size(); i++) {
            final EditBox box = this.widgets.get(i);
            if (box != null && this.host.getFocused() == box) {
                this.host.setFocused(null);
            }
        }
    }

    void disarm() {
        this.armedClick = null;
        this.armedAt = 0L;
    }

    boolean isArmed(final Click click, final long now) {
        return this.armedClick == click
                && now - this.armedAt >= ARM_FLOOR_MS
                && now - this.armedAt <= ARM_WINDOW_MS;
    }

    boolean isArmedVisual(final Click click, final long now) {
        return this.armedClick == click && now - this.armedAt >= 0L
                && now - this.armedAt <= ARM_WINDOW_MS;
    }

    void setStatus(final String text, final boolean error) {
        this.status = text == null ? "" : text;
        this.statusIsError = error;
    }

    // -------------------------------------------------------------------- render

    void render(final GuiGraphics graphics, final int mouseX, final int mouseY) {
        if (this.area.w() <= 0 || this.area.h() <= 0) {
            return;
        }
        if (this.showTitle && this.titleRect.w() > 0) {
            GovText.sectionTitle(graphics, this.font, this.titleView, this.titleRect);
        }
        switch (this.subtab) {
            case TAB_REGISTRY -> drawRegistry(graphics, mouseX, mouseY);
            case TAB_SCAN -> drawScan(graphics, mouseX, mouseY);
            case TAB_REGISTER -> drawRegister(graphics, mouseX, mouseY);
            case TAB_IMPOUND -> drawImpound(graphics, mouseX, mouseY);
            case TAB_DRIVERS_LICENSE -> drawLicences(graphics, mouseX, mouseY, false);
            case TAB_VEHICLE_LICENSES -> drawLicences(graphics, mouseX, mouseY, true);
            default -> {
                // No other mode exists.
            }
        }
    }

    void drawRegistry(final GuiGraphics graphics, final int mouseX, final int mouseY) {
        drawList(graphics, mouseX, mouseY);
        if (this.detailRect.w() > 0) {
            drawDetail(graphics, mouseX, mouseY);
        } else {
            drawSummary(graphics);
        }
        drawBottomBar(graphics);
    }

    void drawList(final GuiGraphics graphics, final int mouseX, final int mouseY) {
        if (this.listRect.w() <= 0 || this.listRect.h() <= 0) {
            return;
        }
        graphics.fill(this.listRect.x(), this.listRect.y(), this.listRect.right(),
                this.listRect.bottom(), GovTheme.FIELD);
        GovTheme.border(graphics, this.listRect, GovTheme.BORDER);
        drawTableHeader(graphics);

        if (this.listState == LoadState.LOADING) {
            drawText(graphics, label("gui.gov.vehicles.loading"), this.listRect.x() + 6,
                    this.listRect.y() + 6, this.listRect.w() - 12, GovTheme.MUTED);
            return;
        }
        if (this.listState == LoadState.ERROR) {
            drawText(graphics, label("gui.gov.vehicles.error"), this.listRect.x() + 6,
                    this.listRect.y() + 6, this.listRect.w() - 12, GovTheme.DANGER);
            drawText(graphics, this.listErrorView, this.listRect.x() + 6,
                    this.listRect.y() + 18, this.listRect.w() - 12, GovTheme.MUTED);
            return;
        }
        if (this.rows.isEmpty()) {
            final String empty = VehicleKeys.FILTER_ALL.equals(this.listFilter)
                    ? label("gui.gov.vehicles.empty.all")
                    : this.canRegister || this.canScan
                            ? label("gui.gov.vehicles.empty.filter")
                            : label("gui.gov.vehicles.empty.own");
            drawText(graphics, empty, this.listRect.x() + 6, this.listRect.y() + 6,
                    this.listRect.w() - 12, GovTheme.MUTED);
            return;
        }

        graphics.enableScissor(this.listRect.x() + 1, this.listRect.y() + 1,
                this.listRect.right() - 1, this.listRect.bottom() - 1);
        try {
            int y = this.listRect.y() + 1 - this.listScroll;
            for (int i = 0; i < this.rowViews.size(); i++) {
                final VehicleRowView view = this.rowViews.get(i);
                if (GovFit.fitsWithin(y, this.rowH, this.listRect.y() + 1,
                        this.listRect.bottom() - 1)) {
                    final boolean selected = view.plate().equals(this.selectedPlate);
                    final boolean hovered = this.listRect.contains(mouseX, mouseY)
                            && mouseX >= this.listRect.x() + 1
                            && mouseX < this.listRect.right() - 1
                            && mouseY >= y && mouseY < y + this.rowH;
                    if (selected) {
                        graphics.fill(this.listRect.x() + 1, y, this.listRect.right() - 1,
                                y + this.rowH, GovTheme.SELECTED);
                        graphics.fill(this.listRect.x() + 1, y, this.listRect.x() + 3,
                                y + this.rowH, GovTheme.ACCENT);
                    } else if (hovered) {
                        graphics.fill(this.listRect.x() + 1, y, this.listRect.right() - 1,
                                y + this.rowH, GovTheme.HOVER);
                    }
                    final int textY = y + (this.rowH - ROW_TEXT) / 2;
                    final int color = selected ? GovTheme.TEXT : GovTheme.BODY;
                    graphics.drawString(this.font, view.plate(), this.columnX[0], textY, color,
                            false);
                    graphics.drawString(this.font, view.owner(), this.columnX[1], textY, color,
                            false);
                    graphics.drawString(this.font, view.vehicleClass(), this.columnX[2], textY,
                            color, false);
                    graphics.drawString(this.font, view.statusBold(), this.columnX[3], textY,
                            GovTheme.TEXT, false);
                    graphics.drawString(this.font, view.licence(), this.columnX[4], textY,
                            GovTheme.MUTED, false);
                    if (i + 1 < this.rowViews.size()) {
                        graphics.fill(this.listRect.x() + 4, y + this.rowH - 1,
                                this.listRect.right() - 4, y + this.rowH, GovTheme.BORDER_SOFT);
                    }
                }
                y += this.rowH;
            }
        } finally {
            graphics.disableScissor();
        }

        if (this.listScrollMax > 0) {
            final GovRect track = rect(this.listRect.right() - 4, this.listRect.y() + 2,
                    2, Math.max(0, this.listRect.h() - 4));
            GovWidgets.scrollbar(graphics, track, this.listScroll, this.listScrollMax);
        }
    }

    void drawTableHeader(final GuiGraphics graphics) {
        if (this.tableHeader.w() <= 0 || this.tableHeader.h() <= 0) {
            return;
        }
        graphics.fill(this.tableHeader.x(), this.tableHeader.y(), this.tableHeader.right(),
                this.tableHeader.bottom(), GovTheme.PANEL_ALT);
        graphics.fill(this.tableHeader.x(), this.tableHeader.bottom() - 1,
                this.tableHeader.right(), this.tableHeader.bottom(), GovTheme.BORDER);
        final int textY = this.tableHeader.y()
                + Math.max(0, (this.tableHeader.h() - ROW_TEXT) / 2);
        for (int i = 0; i < 5; i++) {
            if (this.columnW[i] > 0) {
                GovText.clipped(graphics, this.font, this.columnHeaders[i], this.columnX[i],
                        textY, this.columnW[i], GovTheme.MUTED);
            }
        }
    }

    void drawSummary(final GuiGraphics graphics) {
        if (this.summaryRect.w() <= 0) {
            return;
        }
        final String text = this.status.isEmpty() ? this.summaryView : this.statusView;
        final int color = this.status.isEmpty() ? GovTheme.MUTED
                : this.statusIsError ? GovTheme.DANGER : GovTheme.POSITIVE;
        drawText(graphics, text, this.summaryRect.x(), this.summaryRect.y(),
                this.summaryRect.w(), color);
    }

}
