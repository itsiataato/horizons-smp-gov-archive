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
abstract class GovVehiclesPanelPart04 extends GovVehiclesPanelPart03 {
    GovVehiclesPanelPart04(final net.minecraft.client.gui.screens.Screen host) {
        super(host);
    }

    void rebuildFormInfo() {
        this.registerOwnerView = GovText.clip(this.font,
                this.online.isEmpty() ? label("gui.gov.vehicles.no_online")
                        : this.registerOwner.isBlank() ? label("gui.gov.vehicles.no_owner")
                                : this.registerOwner,
                this.ownerValueRect.w());
        this.registerStatusView = GovText.clip(this.font, this.status,
                Math.max(0, this.registerStatusRect.w()));
        this.impoundSummaryView = GovText.clip(this.font,
                this.selectedPlate.isEmpty() ? label("gui.gov.vehicles.select_prompt")
                        : this.selectedPlate,
                Math.max(0, this.impoundSummaryRect.w()));
        this.impoundStatusView = GovText.clip(this.font, this.status,
                Math.max(0, this.impoundStatusRect.w()));
        this.licenseSummaryView = GovText.clip(this.font,
                this.selectedPlate.isEmpty() ? label("gui.gov.vehicles.select_prompt")
                        : this.selectedPlate,
                Math.max(0, this.licenseSummaryRect.w()));
        final String fee = this.releaseFee > 0L ? Long.toString(this.releaseFee) : "8";
        this.registerRuleView = GovText.clip(this.font, label("gui.gov.vehicles.register_rule"),
                Math.max(0, this.registerRuleRect.w()));
        this.impoundWarningView = GovText.clip(this.font,
                label("gui.gov.vehicles.impound_warning"),
                Math.max(0, this.impoundRuleRect.w()));
        this.releaseRuleView = GovText.clip(this.font,
                this.receptionConfigured
                        ? up(trArgs("gui.gov.vehicles.release_rule", fee))
                        : label("gui.gov.vehicles.reception_missing"),
                Math.max(0, this.impoundRuleRect.w()));
        this.licenseHintView = GovText.clip(this.font,
                label("gui.gov.vehicles.license_points_hint"),
                Math.max(0, this.licensePointsHintRect.w()));
        this.licenseRuleView = GovText.clip(this.font,
                label("gui.gov.vehicles.license_warning"),
                Math.max(0, this.licenseRuleRect.w()));
        this.scanPromptView = GovText.clip(this.font,
                label("gui.gov.vehicles.scan_needs_plate"),
                Math.max(0, this.scanCardRect.w() - 12));
        this.scanForbiddenView = GovText.clip(this.font,
                label("gui.gov.vehicles.scan_forbidden"),
                Math.max(0, this.scanFieldRect.w()));
        final String cls = classText(this.detail.getOrDefault(VehicleKeys.CLASS, ""));
        final String state = up(this.detail.getOrDefault(VehicleKeys.LICENSE_STATE,
                LicenseKeys.STATE_NONE));
        final int points = intField(this.detail, VehicleKeys.LICENSE_POINTS);
        final String suspended = date(longField(this.detail, VehicleKeys.LICENSE_SUSPENDED_UNTIL));
        final String expires = date(longField(this.detail, VehicleKeys.EXPIRES_AT));
        this.licenseInfoView = GovText.clip(this.font,
                label("gui.gov.vehicles.field.class") + " " + cls + "  "
                        + label("gui.gov.vehicles.field.license") + " " + state + "  "
                        + label("gui.gov.vehicles.field.points") + " " + points,
                Math.max(0, this.licenseInfoRect.w()));
        this.licenseStatusFullView = GovText.clip(this.font, this.status,
                Math.max(0, this.licenseRuleRect.w()));
        if (this.subtab == TAB_VEHICLE_LICENSES) {
            this.licenseSummaryView = GovText.clip(this.font,
                    (this.selectedPlate.isEmpty() ? label("gui.gov.vehicles.select_prompt")
                            : this.selectedPlate) + "  " + suspended + "  " + expires,
                    Math.max(0, this.licenseSummaryRect.w()));
        }
    }

    void rebuildStatus() {
        final int width = this.statusRectWidth();
        this.statusView = GovText.clip(this.font, this.status, width);
        this.pageView = GovText.clip(this.font,
                Component.translatable("gui.gov.vehicles.page", this.page + 1,
                        this.pages).getString(), this.pageTextRect.w());
    }

    int statusRectWidth() {
        return switch (this.subtab) {
            case TAB_SCAN -> Math.max(0, this.scanStatusRect.w());
            case TAB_REGISTER -> Math.max(0, this.registerStatusRect.w());
            case TAB_IMPOUND -> Math.max(0, this.impoundStatusRect.w());
            case TAB_VEHICLE_LICENSES -> Math.max(0, this.licenseStatusRect.w());
            default -> Math.max(0, this.area.w() - this.pad * 2);
        };
    }

    void rebuildButtons() {
        this.buttons.clear();
        if (this.area.w() <= 0 || this.area.h() <= 0) {
            return;
        }
        switch (this.subtab) {
            case TAB_REGISTRY -> {
                for (int i = 0; i < FILTERS.length; i++) {
                    addButton(this.filterRects[i],
                            label("gui.gov.vehicles.filter." + FILTERS[i]), null,
                            Click.FILTER, FILTERS[i], true, FILTERS[i].equals(this.listFilter));
                }
                addListButtons();
                if (this.canRelease && impoundActive() && this.detailRect.w() > 0) {
                    addButton(this.detailActionRect, label("gui.gov.vehicles.release_button"),
                            label("gui.gov.vehicles.confirm_release"), Click.RELEASE, "",
                            !mutationPending(), false);
                }
            }
            case TAB_SCAN -> addButton(this.scanButtonRect,
                    label("gui.gov.vehicles.scan_button"), null, Click.SCAN, "",
                    !mutationPending() && this.canScan, true);
            case TAB_REGISTER -> {
                addButton(this.ownerPrevRect, "<", null, Click.OWNER_PREV, "", true, false);
                addButton(this.ownerNextRect, ">", null, Click.OWNER_NEXT, "", true, false);
                for (int i = 0; i < CLASSES.length; i++) {
                    addButton(this.classRects[i],
                            classButtonLabel(CLASSES[i], this.classRects[i].w()), null,
                            Click.REGISTER_CLASS, CLASSES[i], true,
                            CLASSES[i].equals(this.registerClass));
                }
                addButton(this.registerButtonRect,
                        label("gui.gov.vehicles.register_action"), null, Click.REGISTER_SUBMIT,
                        "", !mutationPending() && this.canRegister, true);
            }
            case TAB_IMPOUND -> {
                addButton(this.impoundButtonRect, label("gui.gov.vehicles.impound_action"),
                        impoundConfirmText(), Click.IMPOUND_SUBMIT, "",
                        !mutationPending() && this.canImpound, true);
                if (this.canRelease && impoundActive()) {
                    addButton(this.releaseButtonRect,
                            label("gui.gov.vehicles.release_button"),
                            label("gui.gov.vehicles.confirm_release"), Click.RELEASE, "",
                            !mutationPending(), false);
                }
            }
            case TAB_DRIVERS_LICENSE -> {
            }
        case TAB_VEHICLE_LICENSES -> {
                addButton(this.licensePrevRect, "<", null, Click.LICENSE_PREV, "", true, false);
                addButton(this.licenseNextRect, ">", null, Click.LICENSE_NEXT, "", true, false);
                for (int i = 0; i < POINTS.length; i++) {
                    addButton(this.pointRects[i], "+" + POINTS[i], null, Click.LICENSE_POINTS,
                            POINTS[i], true, POINTS[i].equals(this.licensePoints));
                }
                addButton(this.licenseIssueRect, label("gui.gov.vehicles.license_issue"),
                        null, Click.LICENSE_ISSUE, "", !mutationPending()
                                && this.canManageLicenses && licenceOwnerKnown(), false);
                addButton(this.licenseAssessRect, label("gui.gov.vehicles.license_assess"),
                        null, Click.LICENSE_ASSESS, "", !mutationPending()
                                && this.canManageLicenses && licenceOwnerKnown(), false);
                addButton(this.licenseRevokeRect, label("gui.gov.vehicles.license_revoke"),
                        label("gui.gov.vehicles.license_revoke_confirm"), Click.LICENSE_REVOKE,
                        "", !mutationPending() && this.canManageLicenses
                                && licenceOwnerKnown(), false);
                if (this.detailRect.w() > 0) {
                    addListButtons();
                }
            }
            default -> {
                // No other mode exists.
            }
        }
        if (this.subtab == TAB_REGISTRY || this.subtab == TAB_VEHICLE_LICENSES) {
            addRetryButtons();
        }
    }

    /** Retries belong to the viewport whose request failed, never to a hidden list. */
    void addRetryButtons() {
        final boolean listVisible = this.subtab == TAB_REGISTRY
                || (this.subtab == TAB_VEHICLE_LICENSES && this.detailRect.w() > 0);
        if (listVisible && this.listState == LoadState.ERROR
                && this.listRect.h() >= RETRY_H + 24) {
            addButton(this.listRetryRect, label("gui.gov.card.retry"), null,
                    Click.RETRY_LIST, "", true, false);
        }
        if (this.detailState == LoadState.ERROR
                && this.detailBodyRect.h() >= RETRY_H + 18) {
            addButton(this.detailRetryRect, label("gui.gov.card.retry"), null,
                    Click.RETRY_DETAIL, "", true, false);
        }
    }

    void addListButtons() {
        addButton(this.prevRect, label("gui.gov.previous"), null, Click.PAGE, "-1",
                this.page > 0 && !mutationPending(), false);
        addButton(this.nextRect, label("gui.gov.next"), null, Click.PAGE, "1",
                this.page < this.pages - 1 && !mutationPending(), false);
        addButton(this.refreshRect, label("gui.gov.refresh"), null, Click.REFRESH, "",
                !mutationPending(), false);
    }

    void addButton(final GovRect rect, final String label, final String confirm,
            final Click click, final String value, final boolean enabled, final boolean primary) {
        if (rect.w() <= 0 || rect.h() <= 0 || label.isEmpty()) {
            return;
        }
        this.buttons.add(new Button(rect, label, confirm, click, value, enabled, primary));
    }

    void rebuildHits() {
        this.rowHits.clear();
        if (this.listRect.w() <= 0 || this.listRect.h() <= 0) {
            return;
        }
        if (this.subtab != TAB_REGISTRY && this.subtab != TAB_DRIVERS_LICENSE
                && this.subtab != TAB_VEHICLE_LICENSES) {
            return;
        }
        if ((this.subtab == TAB_VEHICLE_LICENSES || this.subtab == TAB_DRIVERS_LICENSE)
                && this.detailRect.w() <= 0) {
            return;
        }
        int y = this.listRect.y() + 1 - this.listScroll;
        for (int i = 0; i < this.rowViews.size(); i++) {
            final VehicleRowView view = this.rowViews.get(i);
            final int top = Math.max(y, this.listRect.y() + 1);
            final int bottom = Math.min(y + this.rowH, this.listRect.bottom() - 1);
            if (bottom > top && y + this.rowH > this.listRect.y()
                    && y < this.listRect.bottom()) {
                this.rowHits.add(new Hit(rect(this.listRect.x() + 1, top,
                        this.listRect.w() - 2, bottom - top), Click.ROW, view.plate()));
            }
            y += this.rowH;
        }
    }

    // ------------------------------------------------------------------ requests

    void requestListIfNeeded() {
        if (this.listState == LoadState.LOADING) {
            return;
        }
        if (this.listState == LoadState.NONE) {
            requestList(this.listFilter, this.pageByTab[this.subtab]);
        }
        if (this.listState == LoadState.ERROR) {
            requestList(this.listFilter, this.pageByTab[this.subtab]);
        }
    }

    void requestList(final String filter, final int requestedPage) {
        final String reqId = UUID.randomUUID().toString();
        this.pending.put(reqId, RequestKind.LIST);
        this.latestListReqId = reqId;
        this.requestedFilter = filter == null ? VehicleKeys.FILTER_ALL : filter;
        this.listState = LoadState.LOADING;
        this.listError = "";
        GovNetwork.sendRequest(OpCode.VEH_LIST, Map.of(
                VehicleKeys.KEY_FILTER, this.requestedFilter,
                VehicleKeys.KEY_PAGE, Integer.toString(Math.max(0, requestedPage))), reqId);
    }

}
