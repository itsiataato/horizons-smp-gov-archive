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
abstract class GovVehiclesPanelPart09 extends GovVehiclesPanelPart08 {
    GovVehiclesPanelPart09(final net.minecraft.client.gui.screens.Screen host) {
        super(host);
    }

    EditBox registerOwnerBox;

    List<EditBox> createWidgets(final Font font) {
        super.createWidgets(font);
        if (this.registerOwnerBox == null) {
            this.registerOwnerBox = box(font, GovOfflineTarget.MAX_NAME,
                    "gui.gov.vehicles.owner_hint");
        }
        this.widgets = List.of(this.scanBox, this.plateBox, this.impoundPlateBox,
                this.impoundReasonBox, this.licenseReasonBox, this.licenseTargetBox,
                this.licenseClassBox, this.registerOwnerBox);
        return this.widgets;
    }

    void positionFields() {
        super.positionFields();
        addField(TAB_REGISTER, this.ownerLabelRect, this.ownerValueRect,
                this.registerOwnerBox, "gui.gov.vehicles.field.owner");
    }

    void syncWidgets() {
        super.syncWidgets();
        final boolean show = this.active && this.subtab == TAB_REGISTER;
        setVisible(this.registerOwnerBox, show);
        if (show && this.registerOwnerBox != null
                && this.registerOwnerBox.getValue().isBlank()
                && !this.registerOwner.isBlank()) {
            this.registerOwnerBox.setValue(this.registerOwner);
        }
    }

    void rebuildFormInfo() {
        super.rebuildFormInfo();
        this.registerOwnerView = "";
    }

    boolean licenceOwnerKnown() {
        return !this.detail.getOrDefault(VehicleKeys.OWNER_UUID, "").isBlank();
    }

    String defaultOwner() {
        if (!this.viewerName.isBlank() && this.online.contains(this.viewerName)) {
            return this.viewerName;
        }
        return this.online.isEmpty() ? "" : this.online.get(0);
    }

    void cycleOwner(final int delta) {
        if (this.online.isEmpty()) {
            return;
        }
        final String prev = this.registerOwner;
        this.registerOwner = GovOfflineTarget.stepQuickPick(this.online, prev, delta);
        if (this.registerOwnerBox != null) {
            final String typed = this.registerOwnerBox.getValue();
            if (typed.isBlank() || typed.equals(prev)) {
                this.registerOwnerBox.setValue(this.registerOwner);
            }
        }
        this.status = "";
        rebuildFormInfo();
        rebuildStatus();
    }

    void submitRegister() {
        if (mutationPending() || !this.canRegister) {
            return;
        }
        final String plate = this.plateBox == null ? "" : this.plateBox.getValue().strip();
        if (plate.isEmpty()) {
            setStatus(label("gui.gov.vehicles.plate_required"), true);
            rebuildAll();
            return;
        }
        final String typed = this.registerOwnerBox == null ? ""
                : this.registerOwnerBox.getValue();
        final String owner = GovOfflineTarget.orQuickPick(typed, this.registerOwner);
        if (owner.isBlank()) {
            setStatus(label(GovOfflineTarget.blankKey(this.online.isEmpty(),
                    "gui.gov.vehicles.no_online", "gui.gov.vehicles.no_owner")), true);
            rebuildAll();
            return;
        }
        this.registerOwner = owner;
        mutate(OpCode.VEH_REGISTER, Map.of(
                VehicleKeys.KEY_PLATE, normalizePlate(plate),
                VehicleKeys.KEY_CLASS, this.registerClass,
                "target", owner));
        rebuildButtons();
    }

    /** The armed confirmation names the plate that will be towed. */
    String impoundConfirmText() {
        final String plate = normalizePlate(this.impoundPlateBox == null ? ""
                : this.impoundPlateBox.getValue().strip());
        return plate.isEmpty() ? label("gui.gov.vehicles.impound_confirm")
                : trArgs("gui.gov.vehicles.impound_title", plate);
    }

    void submitImpound() {
        if (mutationPending() || !this.canImpound) {
            return;
        }
        final String plate = normalizePlate(this.impoundPlateBox == null ? ""
                : this.impoundPlateBox.getValue().strip());
        if (plate.isEmpty()) {
            setStatus(label("gui.gov.vehicles.plate_required"), true);
            rebuildAll();
            return;
        }
        final String reason = this.impoundReasonBox == null
                ? "" : this.impoundReasonBox.getValue().strip();
        if (reason.isEmpty()) {
            setStatus(label("gui.gov.vehicles.reason_required"), true);
            rebuildAll();
            return;
        }
        final long now = System.currentTimeMillis();
        if (this.armedClick == Click.IMPOUND_SUBMIT) {
            final long elapsed = now - this.armedAt;
            if (elapsed < ARM_FLOOR_MS) {
                // A second activation inside the floor is ignored and does not restart the timer.
                return;
            }
            if (elapsed <= ARM_WINDOW_MS) {
                this.armedClick = null;
                mutate(OpCode.VEH_IMPOUND, Map.of(
                        VehicleKeys.KEY_PLATE, plate,
                        VehicleKeys.KEY_REASON, reason));
                rebuildButtons();
                return;
            }
        }
        this.armedClick = Click.IMPOUND_SUBMIT;
        this.armedAt = now;
        setStatus(label("gui.gov.vehicles.impound_confirm") + " " + plate, false);
        rebuildAll();
    }

    void releaseSelected() {
        if (mutationPending() || !this.canRelease || this.selectedPlate.isEmpty()) {
            return;
        }
        final long now = System.currentTimeMillis();
        if (this.armedClick == Click.RELEASE) {
            final long elapsed = now - this.armedAt;
            if (elapsed < ARM_FLOOR_MS) {
                return;
            }
            if (elapsed <= ARM_WINDOW_MS) {
                this.armedClick = null;
                mutate(OpCode.VEH_RELEASE, Map.of(VehicleKeys.KEY_PLATE, this.selectedPlate));
                rebuildButtons();
                return;
            }
        }
        this.armedClick = Click.RELEASE;
        this.armedAt = now;
    }

    void issueLicense() {
        if (mutationPending() || !this.canManageLicenses) {
            return;
        }
        String target = this.licenseTargetBox == null ? "" : this.licenseTargetBox.getValue().strip();
        String licenseClass = this.licenseClassBox == null ? "" : this.licenseClassBox.getValue().strip();
        if (target.isBlank()) {
            target = this.detail.getOrDefault(VehicleKeys.OWNER_UUID, "");
        }
        if (licenseClass.isBlank()) {
            licenseClass = this.detail.getOrDefault(VehicleKeys.CLASS, "");
        }
        if (target.isBlank() || licenseClass.isBlank()) {
            setStatus(label("gui.gov.vehicles.detail_error"), true);
            rebuildAll();
            return;
        }
        mutate(OpCode.LICENSE_ISSUE, Map.of(
                "target", target,
                LicenseKeys.KEY_CLASS, licenseClass));
        rebuildButtons();
    }

    void assessPoints() {
        if (mutationPending() || !this.canManageLicenses) {
            return;
        }
        String target = this.licenseTargetBox == null ? "" : this.licenseTargetBox.getValue().strip();
        String licenseClass = this.licenseClassBox == null ? "" : this.licenseClassBox.getValue().strip();
        if (target.isBlank()) {
            target = this.detail.getOrDefault(VehicleKeys.OWNER_UUID, "");
        }
        if (licenseClass.isBlank()) {
            licenseClass = this.detail.getOrDefault(VehicleKeys.CLASS, "");
        }
        final String reason = this.licenseReasonBox == null
                ? "" : this.licenseReasonBox.getValue().strip();
        if (reason.isEmpty()) {
            setStatus(label("gui.gov.vehicles.reason_required"), true);
            rebuildAll();
            return;
        }
        if (target.isBlank() || licenseClass.isBlank()) {
            setStatus(label("gui.gov.vehicles.detail_error"), true);
            rebuildAll();
            return;
        }
        mutate(OpCode.LICENSE_POINTS, Map.of(
                "target", target,
                LicenseKeys.KEY_CLASS, licenseClass,
                LicenseKeys.KEY_POINTS, this.licensePoints,
                LicenseKeys.KEY_REASON, reason));
        rebuildButtons();
    }

    void revokeLicense() {
        if (mutationPending() || !this.canManageLicenses) {
            return;
        }
        String ownerUuid = this.licenseTargetBox == null ? "" : this.licenseTargetBox.getValue().strip();
        String licenseClass = this.licenseClassBox == null ? "" : this.licenseClassBox.getValue().strip();
        if (ownerUuid.isBlank()) {
            ownerUuid = this.detail.getOrDefault(VehicleKeys.OWNER_UUID, "");
        }
        if (licenseClass.isBlank()) {
            licenseClass = this.detail.getOrDefault(VehicleKeys.CLASS, "");
        }
        final String reason = this.licenseReasonBox == null
                ? "" : this.licenseReasonBox.getValue().strip();
        if (reason.isEmpty()) {
            setStatus(label("gui.gov.vehicles.reason_required"), true);
            rebuildAll();
            return;
        }
        final long now = System.currentTimeMillis();
        if (this.armedClick == Click.LICENSE_REVOKE) {
            final long elapsed = now - this.armedAt;
            if (elapsed < ARM_FLOOR_MS) {
                return;
            }
            if (elapsed <= ARM_WINDOW_MS) {
                this.armedClick = null;
                if (ownerUuid.isBlank() || licenseClass.isBlank()) {
                    setStatus(label("gui.gov.vehicles.detail_error"), true);
                    rebuildAll();
                    return;
                }
                mutate(OpCode.LICENSE_REVOKE, Map.of(
                        "target", ownerUuid,
                        LicenseKeys.KEY_CLASS, licenseClass,
                        LicenseKeys.KEY_REASON, reason));
                rebuildButtons();
                return;
            }
        }
        this.armedClick = Click.LICENSE_REVOKE;
        this.armedAt = now;
    }

}
