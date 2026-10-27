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
import com.horizonssmp.gov.common.OpCode;
import com.horizonssmp.gov.common.WeaponKeys;
import com.horizonssmp.gov.network.GovActionResultPayload;
import com.horizonssmp.gov.network.GovNetwork;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import com.horizonssmp.gov.client.GovWeaponsTypes.*;
import static com.horizonssmp.gov.client.GovWeaponsConstants.*;

abstract class GovWeaponsPanelPart08 extends GovWeaponsPanelPart07 {
    GovWeaponsPanelPart08(final net.minecraft.client.gui.screens.Screen host) {
        super(host);
    }


    void submitPermitSuspend() {
        final String reason = value(this.reasonBox);
        final String minutes = value(this.minutesBox);
        final PermitRow row = selectedPermit();
        final String target = row == null
                ? (this.permitTarget.isBlank() ? this.viewerName : this.permitTarget)
                : (row.holderUuid().isBlank() ? row.holder() : row.holderUuid());
        final String classId = row == null ? this.permitClass : row.permitClass();
        if (reason.isEmpty() || target.isEmpty()) {
            fail(this.textReasonRequired);
            return;
        }
        final Integer parsedMinutes = parseIntOrNull(minutes);
        if (parsedMinutes == null || parsedMinutes <= 0) {
            fail(this.textFieldsRequired);
            return;
        }
        final String normalizedMinutes = Integer.toString(parsedMinutes);
        this.suspendMinutes = normalizedMinutes;
        mutate(OpCode.WPN_PERMIT_SUSPEND, Map.of(
                "target", target,
                WeaponKeys.KEY_PERMIT_CLASS, classId,
                WeaponKeys.KEY_MINUTES, normalizedMinutes,
                WeaponKeys.KEY_REASON, reason));
    }

    void submitPermitIssue() {
        final String target = value(this.issueTargetBox);
        if (target.isEmpty()) {
            fail(this.textFieldsRequired);
            return;
        }
        mutate(OpCode.WPN_PERMIT_ISSUE, Map.of(
                "target", target,
                WeaponKeys.KEY_PERMIT_CLASS, this.permitClass,
                WeaponKeys.KEY_REASON, value(this.reasonBox)));
    }

    void submitLoss() {
        final String reason = value(this.reasonBox);
        if (reason.isEmpty() || this.selectedSerial.isEmpty()) {
            fail(this.textReasonRequired);
            return;
        }
        mutate(OpCode.WPN_REPORT_LOSS, Map.of(
                WeaponKeys.KEY_SERIAL, this.selectedSerial,
                WeaponKeys.KEY_REASON, reason));
    }

    boolean armIfNeeded(final Click action) {
        if (!isArmed(action)) {
            this.armedAction = action;
            this.armedAt = System.currentTimeMillis();
            this.status = tr("gui.gov.weapons.arm_action");
            this.statusIsError = false;
            rebuildModalButtons();
            rebuildButtons();
            return false;
        }
        if (System.currentTimeMillis() - this.armedAt < ARM_FLOOR_MS) {
            return false;
        }
        disarm();
        return true;
    }

    void submitRevoke() {
        final String reason = value(this.reasonBox);
        if (reason.isEmpty() || this.selectedSerial.isEmpty()) {
            fail(this.textReasonRequired);
            return;
        }
        if (!armIfNeeded(Click.REVOKE_OPEN)) {
            return;
        }
        mutate(OpCode.WPN_REVOKE, Map.of(
                WeaponKeys.KEY_SERIAL, this.selectedSerial,
                WeaponKeys.KEY_REASON, reason));
    }

    void submitRegister() {
        final String serial = value(this.registerSerialBox);
        final String type = value(this.registerTypeBox);
        final String owner = value(this.ownerBox);
        if (serial.isEmpty() || type.isEmpty() || owner.isEmpty()) {
            fail(this.textFieldsRequired);
            return;
        }
        mutate(OpCode.WPN_REGISTER, Map.of(
                "target", owner,
                WeaponKeys.KEY_CLASS, this.registerClass,
                WeaponKeys.KEY_SERIAL, serial,
                WeaponKeys.KEY_TYPE, type));
    }

    void submitAreaRevoke() {
        final String reason = value(this.reasonBox);
        if (reason.isEmpty() || this.selectedAreaId.isEmpty()) {
            fail(this.textReasonRequired);
            return;
        }
        if (!armIfNeeded(Click.AREA_REVOKE_OPEN)) {
            return;
        }
        mutate(OpCode.AREA_PERMIT_REVOKE, Map.of(
                WeaponKeys.KEY_AREA_ID, this.selectedAreaId,
                WeaponKeys.KEY_REASON, reason));
    }

    void submitPermitRevoke() {
        final String reason = value(this.reasonBox);
        final PermitRow row = selectedPermit();
        final String target = row == null
                ? (this.permitTarget.isBlank() ? this.viewerName : this.permitTarget)
                : (row.holderUuid().isBlank() ? row.holder() : row.holderUuid());
        final String classId = row == null ? this.permitClass : row.permitClass();
        if (reason.isEmpty() || target.isEmpty()) {
            fail(this.textReasonRequired);
            return;
        }
        if (!armIfNeeded(Click.PERMIT_REVOKE_OPEN)) {
            return;
        }
        mutate(OpCode.WPN_PERMIT_REVOKE, Map.of(
                "target", target,
                WeaponKeys.KEY_PERMIT_CLASS, classId,
                WeaponKeys.KEY_REASON, reason));
    }

    void submitAreaCreate() {
        final String reason = value(this.areaReasonBox);
        if (reason.isEmpty()) {
            fail(this.textReasonRequired);
            return;
        }
        final Integer x = parseIntOrNull(value(this.xBox));
        final Integer y = parseIntOrNull(value(this.yBox));
        final Integer z = parseIntOrNull(value(this.zBox));
        if (x == null || y == null || z == null) {
            fail(this.textFieldsRequired);
            return;
        }
        final Integer radius = parseIntOrNull(value(this.radiusBox));
        final Integer validMinutes = parseIntOrNull(value(this.areaMinutesBox));
        if (radius == null || radius <= 0 || validMinutes == null || validMinutes <= 0) {
            fail(this.textFieldsRequired);
            return;
        }
        mutate(OpCode.AREA_PERMIT_CREATE, Map.of(
                WeaponKeys.KEY_DIMENSION, value(this.dimBox),
                WeaponKeys.KEY_X, Integer.toString(x),
                WeaponKeys.KEY_Y, Integer.toString(y),
                WeaponKeys.KEY_Z, Integer.toString(z),
                WeaponKeys.KEY_RADIUS, Integer.toString(radius),
                WeaponKeys.KEY_VALID_MINUTES, Integer.toString(validMinutes),
                WeaponKeys.KEY_REASON, reason));
    }

    void submitTransfer() {
        final String owner = value(this.transferOwnerBox);
        if (owner.isEmpty() || this.selectedSerial.isEmpty()) {
            fail(this.textFieldsRequired);
            return;
        }
        if (!armIfNeeded(Click.TRANSFER_OPEN)) {
            return;
        }
        mutate(OpCode.WPN_TRANSFER, Map.of(
                WeaponKeys.KEY_SERIAL, this.selectedSerial,
                "target", owner));
    }

    void submitModal() {
        if (mutationPending()) {
            return;
        }
        switch (this.modal) {
            case RECORD -> closeModal();
            case REGISTER -> submitRegister();
            case TRANSFER -> submitTransfer();
            case REVOKE -> submitRevoke();
            case LOSS -> submitLoss();
            case PERMIT_ISSUE -> submitPermitIssue();
            case PERMIT_REVOKE -> submitPermitRevoke();
            case PERMIT_SUSPEND -> submitPermitSuspend();
            case AREA_CREATE -> submitAreaCreate();
            case AREA_REVOKE -> submitAreaRevoke();
            default -> {
            }
        }
    }

    void submitEnter() {
        if (this.modal != Modal.NONE) {
            final Click arm = submitArmAction();
            if (arm != Click.NONE && !isArmed(arm)) {
                return;
            }
            submitModal();
            return;
        }
        switch (this.subtab) {
            case 0 -> {
                if (!this.selectedSerial.isEmpty()) {
                    openModal(Modal.RECORD);
                }
            }
            case 1 -> {
                this.permitTarget = value(this.permitTargetBox).strip();
                this.permitPage = 0;
                requestPermitList();
            }
            case 3 -> requestClassify(false);
            default -> {
                // Registry without a selection and Areas have no primary form action.
            }
        }
    }

}
