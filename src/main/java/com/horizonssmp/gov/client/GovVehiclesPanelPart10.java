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
abstract class GovVehiclesPanelPart10 extends GovVehiclesPanelPart09 {
    GovVehiclesPanelPart10(final net.minecraft.client.gui.screens.Screen host) {
        super(host);
    }

    void clearMutationFields() {
        if (this.plateBox != null) {
            this.plateBox.setValue("");
        }
        if (this.impoundReasonBox != null) {
            this.impoundReasonBox.setValue("");
        }
        if (this.licenseReasonBox != null) {
            this.licenseReasonBox.setValue("");
        }
    }

    // ------------------------------------------------------------------ helpers

    String scanText(final Map<String, String> fields) {
        final String plate = fields.getOrDefault(VehicleKeys.SCAN_PLATE, "?");
        final String result = fields.getOrDefault(VehicleKeys.SCAN_RESULT, "UNREGISTERED");
        if ("UNREGISTERED".equals(result)) {
            return up(trArgs("gui.gov.vehicles.scan_unregistered", plate));
        }
        return up(trArgs("gui.gov.vehicles.scan_result", plate, result,
                fields.getOrDefault(VehicleKeys.SCAN_OWNER, "-"),
                fields.getOrDefault(VehicleKeys.SCAN_LICENSE_STATE, LicenseKeys.STATE_NONE)));
    }

    String statusLabel(final VehicleRow row) {
        if (row.disposal()) {
            return label("gui.gov.vehicles.state.flagged");
        }
        if (row.impound()) {
            return label("gui.gov.vehicles.state.impounded");
        }
        return statusText(row.status());
    }

    String detailStatus() {
        if ("true".equals(this.detail.get(VehicleKeys.DISPOSAL))) {
            return label("gui.gov.vehicles.state.flagged");
        }
        if (impoundActive()) {
            return label("gui.gov.vehicles.state.impounded");
        }
        return statusText(this.detail.getOrDefault(VehicleKeys.STATUS, ""));
    }

    String licenceShort(final VehicleRow row) {
        if (LicenseKeys.STATE_NONE.equals(row.license())) {
            return label("gui.gov.vehicles.license_none");
        }
        return up(trArgs("gui.gov.vehicles.points_short", up(row.license()), row.points()));
    }

    String licenceText() {
        final String state = this.detail.getOrDefault(VehicleKeys.LICENSE_STATE,
                LicenseKeys.STATE_NONE);
        final int points = intField(this.detail, VehicleKeys.LICENSE_POINTS);
        if (LicenseKeys.STATE_NONE.equals(state)) {
            return label("gui.gov.vehicles.license_none");
        }
        return up(trArgs("gui.gov.vehicles.points_full", up(state), points));
    }

    String impoundText() {
        if (!impoundActive()) {
            final long released = longField(this.detail, VehicleKeys.RELEASED_AT);
            return released > 0L
                    ? up(trArgs("gui.gov.vehicles.released_at", date(released)))
                    : label("gui.gov.vehicles.never_impounded");
        }
        return up(trArgs("gui.gov.vehicles.impounded_at",
                date(longField(this.detail, VehicleKeys.IMPOUNDED_AT))));
    }

    String disposalText() {
        return "true".equals(this.detail.get(VehicleKeys.DISPOSAL))
                ? label("gui.gov.vehicles.disposal_yes")
                : label("gui.gov.vehicles.disposal_no");
    }

}
