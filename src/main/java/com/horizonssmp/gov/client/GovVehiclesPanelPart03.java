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
abstract class GovVehiclesPanelPart03 extends GovVehiclesPanelPart02 {
    GovVehiclesPanelPart03(final net.minecraft.client.gui.screens.Screen host) {
        super(host);
    }

    String label(final String key) {
        final String value = this.labels.get(key);
        return value == null ? "" : value;
    }

    String classText(final String id) {
        final String value = this.classNames.get(up(id));
        return value == null ? up(id) : value;
    }

    String classButtonLabel(final String id, final int width) {
        final String full = classText(id);
        if (this.font.width(full) + GovMetrics.GAP_XS * 2 <= width) {
            return full;
        }
        final String shortName = this.classShortNames.get(up(id));
        return shortName == null || shortName.startsWith("gui.gov.") ? full : shortName;
    }

    String statusText(final String id) {
        final String value = this.statusNames.get(up(id));
        return value == null ? up(id) : value;
    }

    // ------------------------------------------------------------------- caches

    void rebuildAll() {
        if (this.font == null || this.area.w() <= 0 || this.area.h() <= 0) {
            return;
        }
        this.titleView = switch (this.subtab) {
            case TAB_SCAN -> label("gui.gov.vehicles.scan_title");
            case TAB_REGISTER -> label("gui.gov.vehicles.register_title");
            case TAB_IMPOUND -> label("gui.gov.vehicles.impound_title_short");
            case TAB_DRIVERS_LICENSE -> label("gui.gov.vehicles.drivers_title");
            case TAB_VEHICLE_LICENSES -> label("gui.gov.vehicles.license_title");
            default -> label("gui.gov.vehicles.registry_title");
        };
        rebuildRows();
        rebuildDetail();
        rebuildScan();
        rebuildFormInfo();
        rebuildStatus();
        rebuildButtons();
        rebuildHits();
    }

    void rebuildRows() {
        this.rowViews.clear();
        if (this.listRect.w() <= 0) {
            this.listScrollMax = 0;
            this.listScroll = 0;
            return;
        }
        for (int i = 0; i < this.rows.size(); i++) {
            final VehicleRow row = this.rows.get(i);
            final String state = statusLabel(row);
            this.rowViews.add(new VehicleRowView(
                    row.id(),
                    GovText.clip(this.font, dash(row.plate()), this.columnW[0]),
                    GovText.clip(this.font, dash(row.owner()), this.columnW[1]),
                    GovText.clip(this.font, classText(row.vehicleClass()), this.columnW[2]),
                    GovText.clip(this.font, state, Math.max(0, this.columnW[3] - 2)),
                    GovText.clip(this.font, licenceShort(row), this.columnW[4]),
                    GovText.boldCached(state)));
        }
        this.listScrollMax = Math.max(0,
                this.rowViews.size() * this.rowH - Math.max(0, this.listRect.h() - 2));
        this.listScroll = GovMetrics.clamp(this.listScroll, 0, this.listScrollMax);

        this.listErrorView = GovText.clip(this.font, this.listError,
                Math.max(0, this.listRect.w() - 16));
    }

    void rebuildDetail() {
        this.detailLines.clear();
        this.detailScrollMax = 0;
        this.detailTitle = "";
        this.detailTitleBold = null;
        this.detailStateView = "";
        this.summaryView = "";
        final int errorW = this.detailRect.w() > 0 ? this.detailRect.w()
                : this.summaryRect.w();
        this.detailErrorView = GovText.clip(this.font, this.detailError,
                Math.max(0, errorW - 16));

        if (this.selectedPlate.isEmpty() || this.detailState == LoadState.NONE) {
            this.summaryView = GovText.clip(this.font, label("gui.gov.vehicles.select_prompt"),
                    Math.max(0, this.summaryRect.w()));
            return;
        }
        if (this.detailState != LoadState.READY) {
            return;
        }
        final String plate = this.detail.getOrDefault(VehicleKeys.PLATE, this.selectedPlate);
        final String state = detailStatus();
        this.detailStateView = GovText.clip(this.font, state,
                Math.max(0, this.detailRect.w() / 3));
        final int stateW = this.detailStateView.isEmpty() ? 0
                : this.font.width(this.detailStateView);
        final int titleW = Math.max(0,
                this.detailRect.w() - 12 - (stateW > 0 ? stateW + 6 : 0));
        this.detailTitle = up(trArgs("gui.gov.vehicles.record_prefix", plate));
        this.detailTitleBold = GovText.boldCached(
                GovText.clip(this.font, this.detailTitle, titleW));
        final String owner = dash(this.detail.getOrDefault(VehicleKeys.OWNER, ""));
        this.summaryView = GovText.clip(this.font,
                plate + "  " + owner + "  " + state, Math.max(0, this.summaryRect.w()));

        final int labelW = this.detailRect.w() > 0
                ? GovMetrics.span(this.detailRect.w(), this.detailRect.w() / 3, 40, 80) : 60;
        final int valueW = Math.max(0,
                (this.detailRect.w() > 0 ? this.detailRect.w() : this.summaryRect.w())
                        - labelW - 14);
        addDetail("gui.gov.vehicles.field.owner", owner, GovTheme.BODY);
        addDetail("gui.gov.vehicles.field.class",
                classText(this.detail.getOrDefault(VehicleKeys.CLASS, "")), GovTheme.BODY);
        addDetail("gui.gov.vehicles.field.status", state, GovTheme.TEXT);
        addDetail("gui.gov.vehicles.field.license", licenceText(), GovTheme.BODY);
        addDetail("gui.gov.vehicles.field.points",
                Integer.toString(intField(this.detail, VehicleKeys.LICENSE_POINTS)),
                GovTheme.BODY);
        addDetail("gui.gov.vehicles.field.suspended",
                date(longField(this.detail, VehicleKeys.LICENSE_SUSPENDED_UNTIL)),
                GovTheme.MUTED);
        addDetail("gui.gov.vehicles.field.expires",
                date(longField(this.detail, VehicleKeys.EXPIRES_AT)), GovTheme.MUTED);
        addDetail("gui.gov.vehicles.field.impound", impoundText(), GovTheme.BODY);
        addWrappedDetail("gui.gov.vehicles.field.impound_reason",
                this.detail.getOrDefault(VehicleKeys.IMPOUND_REASON, ""), valueW);
        final String location = this.detail.getOrDefault(VehicleKeys.IMPOUND_LOCATION, "");
        if (!location.isBlank()) {
            addDetail("gui.gov.vehicles.field.location", location, GovTheme.MUTED);
        }
        addDetail("gui.gov.vehicles.field.registered",
                date(longField(this.detail, VehicleKeys.REGISTERED_AT)) + "  "
                        + dash(this.detail.getOrDefault(VehicleKeys.REGISTERED_BY, "")),
                GovTheme.MUTED);
        addDetail("gui.gov.vehicles.field.fee",
                gold(this.detail.getOrDefault(VehicleKeys.FEE, "0")), GovTheme.BODY);
        if (impoundActive()) {
            addDetail("gui.gov.vehicles.field.impounded_at",
                    date(longField(this.detail, VehicleKeys.IMPOUNDED_AT)), GovTheme.MUTED);
            addDetail("gui.gov.vehicles.field.impounded_by",
                    dash(this.detail.getOrDefault(VehicleKeys.IMPOUNDED_BY, "")),
                    GovTheme.MUTED);
        } else if (longField(this.detail, VehicleKeys.RELEASED_AT) > 0L) {
            addDetail("gui.gov.vehicles.field.released",
                    date(longField(this.detail, VehicleKeys.RELEASED_AT)) + "  "
                            + dash(this.detail.getOrDefault(VehicleKeys.RELEASED_BY, "")),
                    GovTheme.MUTED);
        }
        addDetail("gui.gov.vehicles.field.disposal", disposalText(),
                "true".equals(this.detail.get(VehicleKeys.DISPOSAL))
                        ? GovTheme.DANGER : GovTheme.MUTED);
        if (this.detailRect.h() > 0) {
            final int bodyH = this.detailBodyRect.h() > 0 ? this.detailBodyRect.h()
                    : this.detailRect.h() - 20;
            this.detailScrollMax = Math.max(0,
                    this.detailLines.size() * DETAIL_LINE + 8 - bodyH);
        }
        this.detailScroll = GovMetrics.clamp(this.detailScroll, 0, this.detailScrollMax);
    }

    void addDetail(final String labelKey, final String value, final int color) {
        final int labelW = this.detailRect.w() > 0
                ? GovMetrics.span(this.detailRect.w(), this.detailRect.w() / 3, 40, 80) : 60;
        final int valueW = Math.max(0,
                (this.detailRect.w() > 0 ? this.detailRect.w() : this.summaryRect.w())
                        - labelW - 14);
        this.detailLines.add(new DetailLine(
                GovText.clip(this.font, label(labelKey), labelW),
                GovText.clip(this.font, value == null || value.isBlank() ? "-" : value, valueW),
                null, color));
    }

    void addWrappedDetail(final String labelKey, final String value, final int valueW) {
        final int labelW = this.detailRect.w() > 0
                ? GovMetrics.span(this.detailRect.w(), this.detailRect.w() / 3, 40, 80) : 60;
        this.detailLines.add(new DetailLine(
                GovText.clip(this.font, label(labelKey), labelW), "", null, GovTheme.MUTED));
        final List<FormattedCharSequence> wrapped = GovText.wrapCached(this.font,
                value == null || value.isBlank() ? "-" : value, valueW);
        for (int i = 0; i < wrapped.size(); i++) {
            this.detailLines.add(new DetailLine("", "", wrapped.get(i), GovTheme.BODY));
        }
    }

    void rebuildScan() {
        this.scanStatusView = GovText.clip(this.font, this.status,
                Math.max(0, this.scanStatusRect.w()));
        this.scanLines.clear();
        if (this.scanFields.isEmpty()) {
            return;
        }
        final String result = this.scanFields.getOrDefault(VehicleKeys.SCAN_RESULT,
                "UNREGISTERED");
        final int width = Math.max(0, this.scanCardRect.w() - 12);
        if ("UNREGISTERED".equals(result)) {
            addScanLine(label("gui.gov.vehicles.scan_not_registered"), width);
            addScanLine(label("gui.gov.vehicles.field.plate") + "  "
                    + dash(this.scanFields.getOrDefault(VehicleKeys.SCAN_PLATE, "")), width);
            return;
        }
        addScanLine(label("gui.gov.vehicles.field.owner") + "  "
                + dash(this.scanFields.getOrDefault(VehicleKeys.SCAN_OWNER, "")), width);
        addScanLine(label("gui.gov.vehicles.field.class") + "  "
                + classText(this.scanFields.getOrDefault(VehicleKeys.SCAN_CLASS, "")), width);
        addScanLine(label("gui.gov.vehicles.field.status") + "  "
                + statusText(this.scanFields.getOrDefault(VehicleKeys.SCAN_STATUS, "")), width);
        addScanLine(label("gui.gov.vehicles.field.license") + "  "
                + up(this.scanFields.getOrDefault(VehicleKeys.SCAN_LICENSE_STATE,
                        LicenseKeys.STATE_NONE)), width);
        if ("true".equals(this.scanFields.get(VehicleKeys.SCAN_IMPOUND))) {
            addScanLine(label("gui.gov.vehicles.field.impound") + "  "
                    + dash(this.scanFields.getOrDefault(VehicleKeys.SCAN_IMPOUND_REASON, "")),
                    width);
        }
    }

    void addScanLine(final String text, final int width) {
        this.scanLines.add(GovText.clip(this.font, text, width));
    }

}
