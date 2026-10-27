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

abstract class GovWeaponsPanelPart04 extends GovWeaponsPanelPart03 {
    GovWeaponsPanelPart04(final net.minecraft.client.gui.screens.Screen host) {
        super(host);
    }


    void updateSummaryTexts() {
        this.weaponSummary = "";
        this.permitSummary = "";
        this.areaSummary = "";
        switch (this.subtab) {
            case 0 -> {
                final WeaponRow weapon = selectedWeapon();
                if (weapon != null) {
                    this.weaponSummary = weapon.serial() + "  /  " + weapon.owner() + "  /  "
                            + weapon.authorityText() + "  /  " + weapon.permitText();
                    this.detailTitle = tr("gui.gov.weapons.weapon_title", weapon.serial());
                } else {
                    this.detailTitle = this.labelSerial;
                }
            }
            case 1 -> {
                final PermitRow permit = selectedPermit();
                if (permit != null) {
                    this.permitSummary = permit.holder() + "  /  " + permit.classLabel()
                            + "  /  " + permit.statusText() + "  /  " + permit.expiryText();
                    this.detailTitle = permit.classLabel() + "  " + permit.holder();
                } else {
                    this.detailTitle = this.labelHolder;
                }
            }
            case 2 -> {
                final AreaRow area = selectedArea();
                if (area != null) {
                    this.areaSummary = area.id() + "  /  " + area.issuedBy() + "  /  "
                            + area.statusText() + "  /  " + area.windowText();
                    this.detailTitle = tr("gui.gov.weapons.area_title", area.id());
                } else {
                    this.detailTitle = this.labelArea;
                }
            }
            default -> this.detailTitle = this.labelItem;
        }
        updateFilterLabel();
    }

    void requestWeaponList() {
        final String reqId = UUID.randomUUID().toString();
        this.pending.put(reqId, RequestKind.LIST);
        this.latestListReqId = reqId;
        this.listState = LoadState.LOADING;
        this.listError = "";
        rebuildRowHits();
        GovNetwork.sendRequest(OpCode.WPN_LIST, Map.of(
                WeaponKeys.KEY_FILTER, this.filter,
                WeaponKeys.KEY_PAGE, Integer.toString(this.weaponPage)), reqId);
        rebuildButtons();
    }

    void updateListTexts(final int mode) {
        if (mode != this.subtab) {
            return;
        }
        final int total = mode == 0 ? this.weaponTotal
                : mode == 1 ? this.permitTotal : this.areaTotal;
        final int shown = listRowCount();
        if (this.listState == LoadState.READY) {
            this.listHeader = total > shown
                    ? tr("gui.gov.weapons.showing", shown, total)
                    : tr("gui.gov.weapons.count", total);
        } else {
            this.listHeader = "";
        }
        final int page = mode == 0 ? this.weaponPage
                : mode == 1 ? this.permitPage : this.areaPage;
        final int pages = mode == 0 ? this.weaponPages
                : mode == 1 ? this.permitPages : this.areaPages;
        this.pageLabel = caps(tr("gui.gov.weapons.page", page + 1, pages));
    }

    void requestAreaList() {
        final String reqId = UUID.randomUUID().toString();
        this.pending.put(reqId, RequestKind.AREA_LIST);
        this.latestAreaReqId = reqId;
        this.listState = LoadState.LOADING;
        this.listError = "";
        rebuildRowHits();
        GovNetwork.sendRequest(OpCode.AREA_PERMIT_LIST,
                Map.of(WeaponKeys.KEY_PAGE, Integer.toString(this.areaPage)), reqId);
        rebuildButtons();
    }

    void setSubtab(final int index) {
        final int next = GovMetrics.clamp(index, 0, SUBTABS - 1);
        if (next == this.subtab) {
            syncWidgets();
            return;
        }
        this.subtab = next;
        closeModal();
        disarm();
        this.status = "";
        this.statusIsError = false;
        if (next == 0 && this.listState == LoadState.NONE) {
            requestWeaponList();
        } else if (next == 1 && this.latestPermitReqId.isEmpty()) {
            requestPermitList();
        } else if (next == 2 && this.latestAreaReqId.isEmpty()) {
            requestAreaList();
        }
        syncWidgets();
        updateListTexts(next);
        updateListMetrics();
        rebuildButtons();
        rebuildDetailLines();
        updateSummaryTexts();
    }

    void requestSubtabIfNeeded() {
        if (this.subtab == 0 && this.listState == LoadState.NONE) {
            requestWeaponList();
        } else if (this.subtab == 1 && this.latestPermitReqId.isEmpty()) {
            requestPermitList();
        } else if (this.subtab == 2 && this.latestAreaReqId.isEmpty()) {
            requestAreaList();
        }
    }

    void reset() {
        this.pending.clear();
        this.latestListReqId = "";
        this.latestDetailReqId = "";
        this.latestPermitReqId = "";
        this.latestAreaReqId = "";
        this.status = "";
        this.statusIsError = false;
        closeModal();
        disarm();
    }

    // ------------------------------------------------------------------- layout

    void setActive(final boolean active) {
        this.active = active;
        if (!active) {
            closeModal();
            disarm();
            this.enterRepeat.reset();
            unfocusAll();
        } else {
            requestSubtabIfNeeded();
        }
        syncWidgets();
        rebuildButtons();
    }

    void layoutListMode(final int index, final boolean withActions,
            final boolean sideBySide, final int left, final int top, final int right,
            final int bottom, final int topH, final int bottomH, final int statusH) {
        final int width = Math.max(0, right - left);
        this.topBars[index] = rect(left, top, width, topH);
        this.bottoms[index] = rect(left, bottom - bottomH, width, bottomH);
        this.statuses[index] = rect(left, this.bottoms[index].y() - statusH, width, statusH);
        this.bodies[index] = rect(left, this.topBars[index].bottom() + 2, width,
                this.statuses[index].y() - this.topBars[index].bottom() - 2);
        if (withActions) {
            final int actionH = Math.min(16, this.bodies[index].h());
            this.actions[index] = rect(left, this.bodies[index].y(), width, actionH);
        } else {
            this.actions[index] = EMPTY;
        }
        final int contentTop = this.actions[index].h() > 0
                ? this.actions[index].bottom() + 2 : this.bodies[index].y();
        final int contentH = Math.max(0, this.bodies[index].bottom() - contentTop);
        if (contentH <= 0) {
            this.lists[index] = EMPTY;
            this.listBodies[index] = EMPTY;
            this.details[index] = EMPTY;
            this.summaries[index] = EMPTY;
            return;
        }
        if (sideBySide) {
            int listW = contentH > 0 ? this.bodies[index].w() * 54 / 100 : 0;
            listW = GovMetrics.clamp(listW, 0, this.bodies[index].w());
            final int gap = this.bodies[index].w() > 120 ? 4 : 0;
            this.lists[index] = rect(left, contentTop, listW, contentH);
            this.listBodies[index] = this.lists[index];
            this.details[index] = rect(this.lists[index].right() + gap, contentTop,
                    this.bodies[index].w() - listW - gap, contentH);
            this.summaries[index] = EMPTY;
        } else {
            final int summaryH = Math.min(12, contentH);
            this.lists[index] = rect(left, contentTop, this.bodies[index].w(),
                    contentH - summaryH - 2);
            this.listBodies[index] = this.lists[index];
            this.summaries[index] = rect(left, this.lists[index].bottom() + 2,
                    this.bodies[index].w(), summaryH);
            this.details[index] = EMPTY;
        }
    }

    void rebuildClassification() {
        if (this.classification.isEmpty()) {
            this.classifyCategory = "";
            this.classifyPermit = "";
            this.classifyCitizen = "";
            this.classifyReason = "";
            this.classifyBlocked = false;
            this.classifyReasonLines = List.of();
            return;
        }
        final String id = this.classification.getOrDefault(WeaponKeys.ITEM_ID, "?");
        final String classLabel = this.classification.getOrDefault(WeaponKeys.ITEM_CLASS_LABEL,
                this.classification.getOrDefault(WeaponKeys.ITEM_CLASS, "NONE"));
        final boolean nuclear = "true".equals(this.classification.get(WeaponKeys.ITEM_NUCLEAR));
        final boolean explosive = "true".equals(this.classification.get(WeaponKeys.ITEM_EXPLOSIVE));
        final boolean military = "true".equals(this.classification.get(WeaponKeys.ITEM_MILITARY));
        final String permit = this.classification.getOrDefault(WeaponKeys.ITEM_PERMIT_CLASS, "");
        final String explanation = this.classification.getOrDefault(WeaponKeys.ITEM_EXPLANATION, "");
        this.classifyCategory = classLabel.toUpperCase(Locale.ROOT);
        this.classifyPermit = nuclear ? this.textNoPath
                : explosive && permit.isBlank() ? permitLabel("blasting")
                : permit.isBlank() ? "-" : permitLabel(permit);
        this.classifyBlocked = nuclear || explosive;
        this.classifyCitizen = this.classifyBlocked || military ? this.textNo : this.textYes;
        this.classifyReason = nuclear ? this.textNoCitizenPath
                : explosive ? this.textExplosivePath
                : explanation.isBlank() ? id : explanation;
        final int labelW = Math.min(86, Math.max(0, this.details[3].w() / 3));
        final int valueX = this.details[3].x() + labelW + 4;
        final int width = Math.max(0, this.details[3].right() - valueX - 4);
        this.classifyReasonLines = GovText.wrapCached(this.font, this.classifyReason, width);
    }

    // ---------------------------------------------------------------- selection

    void placeBox(final EditBox box, final GovRect rect) {
        if (box == null) {
            return;
        }
        box.setX(rect.x() + 3);
        box.setY(rect.y() + 4);
        box.setWidth(Math.max(0, rect.w() - 6));
    }

}
