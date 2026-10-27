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
abstract class GovVehiclesPanelPart05 extends GovVehiclesPanelPart04 {
    GovVehiclesPanelPart05(final net.minecraft.client.gui.screens.Screen host) {
        super(host);
    }

    void requestDetail(final String plate) {
        if (plate == null || plate.isBlank()) {
            return;
        }
        final String reqId = UUID.randomUUID().toString();
        this.pending.put(reqId, RequestKind.DETAIL);
        this.latestDetailReqId = reqId;
        this.detailState = LoadState.LOADING;
        this.detailError = "";
        GovNetwork.sendRequest(OpCode.VEH_INFO, Map.of(VehicleKeys.KEY_PLATE, plate), reqId);
    }

    void requestScan(final String rawPlate) {
        if (mutationPending()) {
            return;
        }
        final String plate = normalizePlate(rawPlate);
        if (plate.isEmpty()) {
            setStatus(label("gui.gov.vehicles.scan_needs_plate"), true);
            rebuildAll();
            return;
        }
        final String reqId = UUID.randomUUID().toString();
        this.pending.put(reqId, RequestKind.SCAN);
        GovNetwork.sendRequest(OpCode.PLATE_SCAN, Map.of(VehicleKeys.KEY_PLATE, plate), reqId);
        rebuildButtons();
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
                    this.listState = LoadState.ERROR;
                    this.listError = fallbackError(payload, "gui.gov.vehicles.error");
                    rebuildAll();
                }
            }
            case DETAIL -> {
                if (!payload.reqId().equals(this.latestDetailReqId)) {
                    return;
                }
                if (payload.ok()) {
                    applyDetail(payload.delta());
                } else {
                    this.detailState = LoadState.ERROR;
                    this.detailError = fallbackError(payload, "gui.gov.vehicles.detail_error");
                    rebuildAll();
                }
            }
            case SCAN -> {
                if (payload.ok()) {
                    final Map<String, String> fields = GovFields.decode(payload.delta());
                    this.scanFields.clear();
                    this.scanFields.putAll(fields);
                    final String plate = fields.getOrDefault(VehicleKeys.SCAN_PLATE, "");
                    if (!plate.isBlank() && !"UNREGISTERED".equals(
                            fields.getOrDefault(VehicleKeys.SCAN_RESULT, ""))) {
                        selectVehicle(plate);
                    }
                    setStatus(scanText(fields), false);
                    requestList(this.listFilter, this.page);
                } else {
                    this.scanFields.clear();
                    setStatus(fallbackError(payload, "gui.gov.vehicles.scan_error"), true);
                }
                rebuildAll();
            }
            case MUTATE -> {
                if (payload.ok()) {
                    setStatus(payload.delta().isBlank()
                            ? label("gui.gov.vehicles.recorded") : payload.delta(), false);
                    disarm();
                    clearMutationFields();
                    syncWidgets();
                    requestList(this.listFilter, this.page);
                    if (!this.selectedPlate.isEmpty()) {
                        requestDetail(this.selectedPlate);
                    }
                } else {
                    setStatus(fallbackError(payload, "gui.gov.vehicles.error"), true);
                    disarm();
                }
                rebuildAll();
            }
        }
    }

    void applyList(final String delta) {
        final Map<String, String> fields = GovFields.decode(delta);
        this.canRegister = "true".equals(fields.get(VehicleKeys.CAN_REGISTER));
        this.canImpound = "true".equals(fields.get(VehicleKeys.CAN_IMPOUND));
        this.canRelease = "true".equals(fields.get(VehicleKeys.CAN_RELEASE));
        this.canScan = "true".equals(fields.get(VehicleKeys.CAN_SCAN));
        this.canManageLicenses = "true".equals(fields.get(VehicleKeys.CAN_MANAGE_LICENSES));
        ((GovDashboardScreen) this.host).setSubtabVisibility(GovDashboardScreen.Tab.VEHICLES,
                new boolean[] {true, this.canScan, this.canRegister, this.canImpound, true, this.canManageLicenses});
        this.viewerUuid = fields.getOrDefault(VehicleKeys.VIEWER_UUID, "");
        this.viewerName = fields.getOrDefault(VehicleKeys.VIEWER_NAME, "");
        this.receptionConfigured = "true".equals(fields.get(VehicleKeys.RECEPTION_CONFIGURED));
        this.paymentPoint = fields.getOrDefault(VehicleKeys.PAYMENT_POINT, "");
        this.releaseFee = longField(fields, VehicleKeys.RELEASE_FEE);
        this.total = intField(fields, VehicleKeys.TOTAL);
        this.page = intField(fields, VehicleKeys.PAGE);
        this.pages = Math.max(1, intField(fields, VehicleKeys.PAGES));
        this.listFilter = fields.getOrDefault(VehicleKeys.FILTER, this.requestedFilter);
        this.pageByTab[TAB_REGISTRY] = this.page;
        this.onlineTotal = intField(fields, VehicleKeys.ONLINE_COUNT);
        this.online.clear();
        for (int i = 0; i < Math.min(this.onlineTotal, VehicleKeys.MAX_LISTED_ONLINE); i++) {
            final String name = fields.get(VehicleKeys.ONLINE_PREFIX + i);
            if (name != null && !name.isBlank()) {
                this.online.add(name);
            }
        }

        this.rows.clear();
        for (int i = 0; i < VehicleKeys.PAGE_SIZE; i++) {
            final String prefix = VehicleKeys.ROW_PREFIX + i;
            final String id = fields.get(prefix + VehicleKeys.ROW_ID);
            if (id == null || id.isBlank()) {
                continue;
            }
            this.rows.add(new VehicleRow(
                    id,
                    fields.getOrDefault(prefix + VehicleKeys.ROW_PLATE, ""),
                    fields.getOrDefault(prefix + VehicleKeys.ROW_CLASS, ""),
                    fields.getOrDefault(prefix + VehicleKeys.ROW_OWNER, ""),
                    fields.getOrDefault(prefix + VehicleKeys.ROW_OWNER_UUID, ""),
                    fields.getOrDefault(prefix + VehicleKeys.ROW_STATUS, ""),
                    "true".equals(fields.get(prefix + VehicleKeys.ROW_IMPOUND)),
                    fields.getOrDefault(prefix + VehicleKeys.ROW_IMPOUND_REASON, ""),
                    fields.getOrDefault(prefix + VehicleKeys.ROW_LICENSE, LicenseKeys.STATE_NONE),
                    intField(fields, prefix + VehicleKeys.ROW_POINTS),
                    "true".equals(fields.get(prefix + VehicleKeys.ROW_DISPOSAL))));
        }
        this.listState = LoadState.READY;
        this.listError = "";

        boolean selectedVisible = false;
        for (int i = 0; i < this.rows.size(); i++) {
            if (this.rows.get(i).plate().equals(this.selectedPlate)) {
                selectedVisible = true;
                break;
            }
        }
        if (!this.selectedPlate.isEmpty() && !selectedVisible) {
            this.selectedPlate = "";
            this.detail.clear();
            this.detailState = LoadState.NONE;
            this.detailError = "";
            disarm();
        }
        if (this.selectedPlate.isEmpty() && !this.rows.isEmpty()) {
            selectVehicle(this.rows.get(0).plate());
        } else if (selectedVisible) {
            requestDetail(this.selectedPlate);
        }
        if (this.registerOwner.isBlank()) {
            this.registerOwner = defaultOwner();
        }
        if (this.subtab == TAB_IMPOUND) {
            prefillImpoundPlate();
        }
        syncWidgets();
        rebuildAll();
    }

    void applyDetail(final String delta) {
        final Map<String, String> fields = GovFields.decode(delta);
        final String plate = fields.get(VehicleKeys.PLATE);
        if (plate == null || plate.isBlank()) {
            this.detailState = LoadState.ERROR;
            this.detailError = Component.translatable("gui.gov.vehicles.detail_error").getString();
            rebuildAll();
            return;
        }
        this.selectedPlate = plate;
        this.detail.clear();
        this.detail.putAll(fields);
        this.detailState = LoadState.READY;
        this.detailError = "";
        this.detailScroll = 0;
        disarm();
        rebuildAll();
    }

    // ---------------------------------------------------------------- selection

    void selectVehicle(final String plate) {
        if (plate == null || plate.isEmpty()) {
            return;
        }
        this.selectedPlate = plate;
        this.status = "";
        this.detailState = LoadState.LOADING;
        disarm();
        requestDetail(plate);
        rebuildAll();
    }

}
