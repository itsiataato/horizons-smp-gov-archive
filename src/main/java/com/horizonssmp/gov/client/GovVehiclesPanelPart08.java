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
abstract class GovVehiclesPanelPart08 extends GovVehiclesPanelPart07 {
    GovVehiclesPanelPart08(final net.minecraft.client.gui.screens.Screen host) {
        super(host);
    }

    boolean mouseClicked(final double mouseX, final double mouseY, final int button) {
        if (!this.active || button != 0) {
            return false;
        }
        // A click anywhere outside the focused field leaves it, before the click is routed.
        if (this.host.getFocused() instanceof EditBox focused
                && this.widgets.contains(focused)
                && !fieldContains(focused, mouseX, mouseY)) {
            this.host.setFocused(null);
        }
        for (int i = 0; i < this.buttons.size(); i++) {
            final Button hit = this.buttons.get(i);
            if (hit.enabled() && hit.rect().contains(mouseX, mouseY)) {
                handleClick(hit);
                return true;
            }
        }
        for (int i = 0; i < this.rowHits.size(); i++) {
            final Hit hit = this.rowHits.get(i);
            if (hit.contains(mouseX, mouseY)) {
                selectVehicle(hit.value());
                return true;
            }
        }
        for (int i = 0; i < this.fieldRows.size(); i++) {
            final FieldRow row = this.fieldRows.get(i);
            if (row.tab() == this.subtab && row.box() != null && row.box().visible
                    && row.fieldRect().contains(mouseX, mouseY)) {
                this.host.setFocused(row.box());
                return true;
            }
        }
        return false;
    }

    boolean mouseScrolled(final double mouseX, final double mouseY, final double scrollY) {
        if (!this.active) {
            return false;
        }
        if (this.listRect.w() > 0 && this.listRect.contains(mouseX, mouseY)
                && this.listScrollMax > 0
                && (this.subtab == TAB_REGISTRY
                        || (this.subtab == TAB_VEHICLE_LICENSES && this.detailRect.w() > 0))) {
            this.listScroll = GovMetrics.clamp(this.listScroll - (int) (scrollY * 16),
                    0, this.listScrollMax);
            this.scrollByTab[this.subtab] = this.listScroll;
            rebuildHits();
            return true;
        }
        if (this.detailRect.w() > 0 && this.detailRect.contains(mouseX, mouseY)
                && this.detailScrollMax > 0) {
            this.detailScroll = GovMetrics.clamp(this.detailScroll - (int) (scrollY * 16),
                    0, this.detailScrollMax);
            return true;
        }
        return false;
    }

    /** True when the mouse is over the visible field row that owns the box. */
    boolean fieldContains(final EditBox box, final double mouseX, final double mouseY) {
        for (int i = 0; i < this.fieldRows.size(); i++) {
            final FieldRow row = this.fieldRows.get(i);
            if (row.box() == box) {
                return row.tab() == this.subtab && row.box().visible
                        && row.fieldRect().contains(mouseX, mouseY);
            }
        }
        return false;
    }

    /**
     * Escape disarms or blurs; Enter submits the visible form once per 250 ms. Impound, release
     * and licence revocation are destructive, so Enter only confirms them once armed by a click.
     */
    boolean keyPressed(final int keyCode, final int scanCode, final int modifiers) {
        if (!this.active) {
            return false;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (this.armedClick != null) {
                disarm();
                return true;
            }
            if (this.host.getFocused() != null) {
                this.host.setFocused(null);
                return true;
            }
            return false;
        }
        if (GovKeyRepeat.isEnter(keyCode)) {
            if (this.enterRepeat.accepts(System.currentTimeMillis())) {
                submitEnter();
            }
            return true;
        }
        if (this.host.getFocused() != null) {
            return false;
        }
        if (keyCode == GLFW.GLFW_KEY_UP) {
            moveSelection(-1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DOWN) {
            moveSelection(1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_PAGE_UP && isListSubtab()) {
            changePage(-1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_PAGE_DOWN && isListSubtab()) {
            changePage(1);
            return true;
        }
        return false;
    }

    boolean charTyped(final char codePoint, final int modifiers) {
        return false;
    }

    void moveSelection(final int delta) {
        if (this.subtab == TAB_SCAN || this.subtab == TAB_REGISTER
                || this.subtab == TAB_IMPOUND || this.rows.isEmpty()) {
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

    /** Subtabs that share the pageable registry list (keyboard + pointer parity). */
    boolean isListSubtab() {
        return this.subtab == TAB_REGISTRY || this.subtab == TAB_DRIVERS_LICENSE
                || this.subtab == TAB_VEHICLE_LICENSES;
    }

    void changePage(final int delta) {
        final int next = GovMetrics.clamp(this.page + delta, 0, this.pages - 1);
        if (next == this.page) {
            return;
        }
        this.page = next;
        this.pageByTab[TAB_REGISTRY] = next;
        this.listState = LoadState.LOADING;
        disarm();
        requestList(this.listFilter, next);
        rebuildAll();
    }

    void handleClick(final Button button) {
        if (button.click() != Click.IMPOUND_SUBMIT
                && button.click() != Click.RELEASE
                && button.click() != Click.LICENSE_REVOKE) {
            disarm();
        }
        switch (button.click()) {
            case FILTER -> setFilter(button.value());
            case PAGE -> changePage("-1".equals(button.value()) ? -1 : 1);
            case RETRY_LIST -> {
                disarm();
                requestList(this.listFilter, this.pageByTab[this.subtab]);
                rebuildAll();
            }
            case RETRY_DETAIL -> {
                disarm();
                requestDetail(this.selectedPlate);
                rebuildAll();
            }
            case REFRESH -> {
                disarm();
                requestList(this.listFilter, this.page);
                if (!this.selectedPlate.isEmpty()) {
                    requestDetail(this.selectedPlate);
                }
                rebuildAll();
            }
            case SCAN -> requestScan(this.scanBox == null ? "" : this.scanBox.getValue());
            case OWNER_PREV -> cycleOwner(-1);
            case OWNER_NEXT -> cycleOwner(1);
            case REGISTER_CLASS -> {
                this.registerClass = button.value();
                this.status = "";
                rebuildAll();
            }
            case REGISTER_SUBMIT -> submitRegister();
            case IMPOUND_SUBMIT -> submitImpound();
            case RELEASE -> releaseSelected();
            case LICENSE_PREV -> cycleVehicle(-1);
            case LICENSE_NEXT -> cycleVehicle(1);
            case LICENSE_POINTS -> {
                this.licensePoints = button.value();
                this.status = "";
                rebuildAll();
            }
            case LICENSE_ISSUE -> issueLicense();
            case LICENSE_ASSESS -> assessPoints();
            case LICENSE_REVOKE -> revokeLicense();
            default -> {
                // No other click is reachable.
            }
        }
    }

    // ------------------------------------------------------------------- actions

    /** Enter on the visible form, with destructive actions restricted to an armed confirmation. */
    void submitEnter() {
        final long now = System.currentTimeMillis();
        switch (this.subtab) {
            case TAB_SCAN -> requestScan(this.scanBox == null ? "" : this.scanBox.getValue());
            case TAB_REGISTER -> submitRegister();
            case TAB_IMPOUND -> {
                if (isArmed(Click.IMPOUND_SUBMIT, now)) {
                    submitImpound();
                } else if (isArmed(Click.RELEASE, now)) {
                    releaseSelected();
                }
            }
            case TAB_VEHICLE_LICENSES -> {
                if (isArmed(Click.LICENSE_REVOKE, now)) {
                    revokeLicense();
                } else if (this.licenseReasonBox != null
                        && !this.licenseReasonBox.getValue().isBlank()) {
                    assessPoints();
                } else {
                    issueLicense();
                }
            }
            case TAB_REGISTRY -> {
                if (isArmed(Click.RELEASE, now)) {
                    releaseSelected();
                }
            }
            default -> {
                // No other tab exists.
            }
        }
    }

    boolean impoundActive() {
        return "true".equals(this.detail.getOrDefault(VehicleKeys.IMPOUND_ACTIVE, "false"));
    }

}
