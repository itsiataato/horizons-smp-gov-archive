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

abstract class GovWeaponsPanelPart06 extends GovWeaponsPanelPart05 {
    GovWeaponsPanelPart06(final net.minecraft.client.gui.screens.Screen host) {
        super(host);
    }


    void applyAreaList(final String delta) {
        this.detailRevision++;
        final Map<String, String> fields = GovFields.decode(delta);
        this.canRegister = "true".equals(fields.get(WeaponKeys.CAN_REGISTER));
        this.areaTotal = intField(fields, WeaponKeys.AREA_TOTAL);
        this.areaPage = intField(fields, WeaponKeys.AREA_PAGE);
        this.areaPages = Math.max(1, intField(fields, WeaponKeys.AREA_PAGES));
        this.areaRows.clear();
        for (int i = 0; i < WeaponKeys.AREA_PAGE_SIZE; i++) {
            final String prefix = WeaponKeys.AREA_PREFIX + i;
            final String id = fields.get(prefix + WeaponKeys.AREA_ID);
            if (id == null || id.isBlank()) {
                continue;
            }
            final String rawStatus = fields.getOrDefault(prefix + WeaponKeys.AREA_STATUS, "");
            final String statusText = statusText(rawStatus);
            final String dimension = fields.getOrDefault(prefix + WeaponKeys.AREA_DIMENSION, "");
            final long validFrom = longField(fields, prefix + WeaponKeys.AREA_VALID_FROM);
            final long validUntil = longField(fields, prefix + WeaponKeys.AREA_VALID_UNTIL);
            final String window = when(validFrom) + " - " + when(validUntil);
            final String lineOne = id + "  " + dimension;
            final String lineTwo = statusText + "  " + window;
            final int statusWidth = this.font == null ? 0 : this.font.width(statusText);
            this.areaRows.add(new AreaRow(
                    id, dimension, intField(fields, prefix + WeaponKeys.AREA_X),
                    intField(fields, prefix + WeaponKeys.AREA_Y),
                    intField(fields, prefix + WeaponKeys.AREA_Z),
                    intField(fields, prefix + WeaponKeys.AREA_RADIUS), rawStatus, statusText,
                    validFrom, validUntil, window,
                    fields.getOrDefault(prefix + WeaponKeys.AREA_REASON, ""),
                    fields.getOrDefault(prefix + WeaponKeys.AREA_ISSUED_BY, ""),
                    fields.getOrDefault(prefix + WeaponKeys.AREA_BOX, ""),
                    lineOne, lineTwo, strongStatus(statusText), statusWidth));
        }
        this.listState = LoadState.READY;
        this.listError = "";
        this.listScrollRows[2] = 0;
        updateListTexts(2);
        updateSummaryTexts();
        boolean visible = false;
        for (final AreaRow row : this.areaRows) {
            if (row.id().equals(this.selectedAreaId)) {
                visible = true;
                break;
            }
        }
        if (!this.selectedAreaId.isEmpty() && !visible) {
            this.selectedAreaId = "";
            disarm();
        }
        if (this.selectedAreaId.isEmpty() && !this.areaRows.isEmpty()
                && this.subtab == 2) {
            this.selectedAreaId = this.areaRows.get(0).id();
        }
        updateSummaryTexts();
        updateListMetrics();
        rebuildButtons();
        rebuildDetailLines();
        syncWidgets();
    }

    void applyClassification(final String delta) {
        this.classification.clear();
        this.classification.putAll(GovFields.decode(delta));
        this.status = "";
        this.statusIsError = false;
        rebuildClassification();
        rebuildButtons();
    }

    void applyDetail(final String delta) {
        this.detailRevision++;
        final Map<String, String> fields = GovFields.decode(delta);
        final String serial = fields.get(WeaponKeys.SERIAL);
        if (serial == null || serial.isBlank()) {
            this.detailState = LoadState.ERROR;
            this.detailError = tr("gui.gov.weapons.detail_error");
            rebuildButtons();
            return;
        }
        this.selectedSerial = serial;
        this.detail.clear();
        this.detail.putAll(fields);
        this.detailState = LoadState.READY;
        this.detailError = "";
        this.detailScrollRows[0] = 0;
        updateSummaryTexts();
        rebuildDetailLines();
        rebuildButtons();
        syncWidgets();
    }

    void selectWeapon(final String serial) {
        if (serial == null || serial.isBlank() || serial.equals(this.selectedSerial)) {
            return;
        }
        this.selectedSerial = serial;
        this.status = "";
        this.statusIsError = false;
        this.detailState = LoadState.LOADING;
        disarm();
        requestWeaponDetail(serial);
        updateSummaryTexts();
        rebuildButtons();
        rebuildDetailLines();
    }

    void applyWeaponList(final String delta) {
        this.detailRevision++;
        final Map<String, String> fields = GovFields.decode(delta);
        this.canRegister = "true".equals(fields.get(WeaponKeys.CAN_REGISTER));
        this.canViewAll = "true".equals(fields.get(WeaponKeys.CAN_VIEW_ALL));
        this.canMilitary = "true".equals(fields.get(WeaponKeys.CAN_MILITARY));
        ((GovDashboardScreen) this.host).setSubtabVisibility(GovDashboardScreen.Tab.WEAPONS,
                new boolean[] {true, true, true, this.canMilitary});
        this.hasTownCores = "true".equals(fields.get(WeaponKeys.TOWN_CORES));
        this.seasonEnd = longField(fields, WeaponKeys.SEASON_END);
        this.viewerName = fields.getOrDefault(WeaponKeys.VIEWER_NAME, "");
        this.viewerUuid = fields.getOrDefault(WeaponKeys.VIEWER_UUID, "");
        this.weaponTotal = intField(fields, WeaponKeys.TOTAL);
        this.weaponPage = intField(fields, WeaponKeys.PAGE);
        this.weaponPages = Math.max(1, intField(fields, WeaponKeys.PAGES));
        this.filter = fields.getOrDefault(WeaponKeys.FILTER, WeaponKeys.FILTER_ALL);

        this.weaponRows.clear();
        for (int i = 0; i < WeaponKeys.PAGE_SIZE; i++) {
            final String prefix = WeaponKeys.ROW_PREFIX + i;
            final String serial = fields.get(prefix + WeaponKeys.ROW_SERIAL);
            if (serial == null || serial.isBlank()) {
                continue;
            }
            final String classId = fields.getOrDefault(prefix + WeaponKeys.ROW_CLASS, "");
            final String rawStatus = fields.getOrDefault(prefix + WeaponKeys.ROW_STATUS, "");
            final String permit = fields.getOrDefault(prefix + WeaponKeys.ROW_PERMIT, "");
            final boolean military = "true".equals(fields.get(prefix + WeaponKeys.ROW_MILITARY));
            final String statusText = statusText(rawStatus);
            final String authority = authorityText(rawStatus, permit);
            final String classText = military ? tr("gui.gov.weapons.military_grade")
                    : classLabel(classId);
            final String permitText = permit == null || permit.isBlank() ? "-" : permitLabel(permit);
            final String owner = fields.getOrDefault(prefix + WeaponKeys.ROW_OWNER, "");
            final String type = fields.getOrDefault(prefix + WeaponKeys.ROW_TYPE, "");
            final String lineOne = serial + "  " + owner;
            final String lineTwo = classText + "  " + authority + "  " + permitText + "  " + type;
            final int statusWidth = this.font == null ? 0 : this.font.width(authority);
            this.weaponRows.add(new WeaponRow(
                    fields.getOrDefault(prefix + WeaponKeys.ROW_ID, ""), serial, classId,
                    classText, type, owner,
                    fields.getOrDefault(prefix + WeaponKeys.ROW_OWNER_UUID, ""), rawStatus,
                    statusText, authority, permit, permitText, military,
                    longField(fields, prefix + WeaponKeys.ROW_REGISTERED_AT),
                    lineOne, lineTwo, strongStatus(authority), statusWidth));
        }
        this.listState = LoadState.READY;
        this.listError = "";
        this.listScrollRows[0] = 0;
        updateListTexts(0);
        updateSummaryTexts();

        boolean visible = false;
        for (final WeaponRow row : this.weaponRows) {
            if (row.serial().equals(this.selectedSerial)) {
                visible = true;
                break;
            }
        }
        if (!this.selectedSerial.isEmpty() && !visible) {
            this.selectedSerial = "";
            this.detail.clear();
            this.detailState = LoadState.NONE;
            disarm();
        }
        if (this.selectedSerial.isEmpty() && !this.weaponRows.isEmpty() && this.subtab == 0) {
            selectWeapon(this.weaponRows.get(0).serial());
        } else if (visible && this.subtab == 0) {
            requestWeaponDetail(this.selectedSerial);
        }
        updateListMetrics();
        rebuildButtons();
        rebuildDetailLines();
        syncWidgets();
    }

}
