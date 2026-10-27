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

abstract class GovWeaponsPanelPart07 extends GovWeaponsPanelPart06 {
    GovWeaponsPanelPart07(final net.minecraft.client.gui.screens.Screen host) {
        super(host);
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
                    applyWeaponList(payload.delta());
                } else {
                    this.listState = LoadState.ERROR;
                    this.listError = fallbackError(payload, "gui.gov.weapons.error");
                    rebuildRowHits();
                    rebuildButtons();
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
                    this.detailError = fallbackError(payload, "gui.gov.weapons.detail_error");
                    rebuildDetailLines();
                    rebuildButtons();
                }
            }
            case PERMIT_LIST -> {
                if (!payload.reqId().equals(this.latestPermitReqId)) {
                    return;
                }
                if (payload.ok()) {
                    applyPermitList(payload.delta());
                } else {
                    this.listState = LoadState.ERROR;
                    this.listError = fallbackError(payload, "gui.gov.weapons.permit_error");
                    rebuildRowHits();
                    rebuildButtons();
                }
            }
            case AREA_LIST -> {
                if (!payload.reqId().equals(this.latestAreaReqId)) {
                    return;
                }
                if (payload.ok()) {
                    applyAreaList(payload.delta());
                } else {
                    this.listState = LoadState.ERROR;
                    this.listError = fallbackError(payload, "gui.gov.weapons.area_error");
                    rebuildRowHits();
                    rebuildButtons();
                }
            }
            case CLASSIFY -> {
                if (payload.ok()) {
                    applyClassification(payload.delta());
                } else {
                    this.status = fallbackError(payload, "gui.gov.weapons.classify_error");
                    this.statusIsError = true;
                    this.classification.clear();
                    rebuildClassification();
                }
            }
            case MUTATE -> {
                if (payload.ok()) {
                    this.status = payload.delta().isBlank()
                            ? tr("gui.gov.weapons.done") : payload.delta();
                    this.statusIsError = false;
                    closeModal();
                    disarm();
                    refreshSection();
                } else {
                    this.status = fallbackError(payload, "gui.gov.weapons.error");
                    this.statusIsError = true;
                    disarm();
                    rebuildButtons();
                }
            }
        }
    }

    void selectPermit(final String id) {
        if (id == null || id.isBlank() || id.equals(this.selectedPermitId)) {
            return;
        }
        this.selectedPermitId = id;
        this.status = "";
        this.statusIsError = false;
        disarm();
        updateSummaryTexts();
        rebuildButtons();
        rebuildDetailLines();
    }

    void selectArea(final String id) {
        if (id == null || id.isBlank() || id.equals(this.selectedAreaId)) {
            return;
        }
        this.selectedAreaId = id;
        this.status = "";
        this.statusIsError = false;
        disarm();
        updateSummaryTexts();
        rebuildButtons();
        rebuildDetailLines();
    }

    void setFilter(final String next) {
        if (next == null || next.equals(this.filter)) {
            return;
        }
        this.filter = next;
        this.weaponPage = 0;
        this.listState = LoadState.LOADING;
        updateFilterLabel();
        requestWeaponList();
        rebuildButtons();
    }

    // ------------------------------------------------------------- list metrics

    void fillAreaDefaults() {
        String dimension = "minecraft:overworld";
        int x = 0;
        int y = 64;
        int z = 0;
        final var player = Minecraft.getInstance().player;
        if (player != null) {
            dimension = player.level().dimension().location().toString();
            x = player.getBlockX();
            y = player.getBlockY();
            z = player.getBlockZ();
        }
        setValue(this.dimBox, dimension);
        setValue(this.xBox, Integer.toString(x));
        setValue(this.yBox, Integer.toString(y));
        setValue(this.zBox, Integer.toString(z));
        setValue(this.radiusBox, "16");
        setValue(this.areaMinutesBox, "60");
        setValue(this.areaReasonBox, "");
    }

    // ------------------------------------------------------------------ widgets

    void openModal(final Modal next) {
        if (next == Modal.NONE) {
            closeModal();
            return;
        }
        this.modal = next;
        this.modalOpenedAt = System.currentTimeMillis();
        disarm();
        this.status = "";
        this.statusIsError = false;
        this.modalTitle = switch (next) {
            case RECORD -> this.buttonRecord;            case REGISTER -> tr("gui.gov.weapons.register_title");
            case TRANSFER -> tr("gui.gov.weapons.transfer_title");
            case REVOKE -> tr("gui.gov.weapons.revoke_title");
            case LOSS -> tr("gui.gov.weapons.loss_title");
            case PERMIT_ISSUE -> tr("gui.gov.weapons.permit_issue_title");
            case PERMIT_REVOKE -> tr("gui.gov.weapons.permit_revoke_title");
            case PERMIT_SUSPEND -> tr("gui.gov.weapons.permit_suspend_title");
            case AREA_CREATE -> tr("gui.gov.weapons.area_create_title");
            case AREA_REVOKE -> tr("gui.gov.weapons.area_revoke_title");
            default -> "";
        };
        this.modalSubmitLabel = switch (next) {
            case RECORD -> this.buttonClose;
            case REGISTER -> caps(tr("gui.gov.weapons.register_submit"));
            case TRANSFER -> caps(tr("gui.gov.weapons.transfer_submit"));
            case REVOKE -> caps(tr("gui.gov.weapons.revoke_submit"));
            case LOSS -> caps(tr("gui.gov.weapons.loss_submit"));
            case PERMIT_ISSUE -> caps(tr("gui.gov.weapons.permit_issue_submit"));
            case PERMIT_REVOKE -> caps(tr("gui.gov.weapons.permit_revoke_submit"));
            case PERMIT_SUSPEND -> caps(tr("gui.gov.weapons.permit_suspend_submit"));
            case AREA_CREATE -> caps(tr("gui.gov.weapons.area_create_submit"));
            case AREA_REVOKE -> caps(tr("gui.gov.weapons.area_revoke_submit"));
            default -> "";
        };
        this.modalContext = switch (next) {
            case PERMIT_REVOKE, PERMIT_SUSPEND -> {
                final PermitRow row = selectedPermit();
                yield row == null ? this.permitTarget
                        : row.holder() + "  " + row.classLabel();
            }
            case AREA_REVOKE -> this.selectedAreaId;
            case TRANSFER, REVOKE, LOSS, RECORD -> this.selectedSerial;
            default -> "";
        };
        if (next == Modal.REGISTER) {
            setValue(this.registerSerialBox, "");
            setValue(this.registerTypeBox, "");
            setValue(this.ownerBox, this.viewerName);
        } else if (next == Modal.TRANSFER) {
            setValue(this.transferOwnerBox, this.viewerName);
        } else if (next == Modal.PERMIT_ISSUE) {
            setValue(this.issueTargetBox, this.permitTarget.isBlank()
                    ? this.viewerName : this.permitTarget);
        } else if (next == Modal.PERMIT_SUSPEND) {
            setValue(this.minutesBox, this.suspendMinutes);
        } else if (next == Modal.AREA_CREATE) {
            fillAreaDefaults();
        }
        if (next == Modal.REVOKE || next == Modal.LOSS || next == Modal.PERMIT_REVOKE
                || next == Modal.AREA_REVOKE) {
            setValue(this.reasonBox, "");
        }
        if (next == Modal.AREA_REVOKE) {
            setValue(this.areaReasonBox, "");
        }
        syncWidgets();
        rebuildModalButtons();
    }

    String value(final EditBox box) {
        return box == null ? "" : box.getValue().strip();
    }

    void fail(final String message) {
        this.status = message;
        this.statusIsError = true;
        rebuildButtons();
        rebuildModalButtons();
    }

    static Integer parseIntOrNull(final String raw) {
        final String text = raw == null ? "" : raw.trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(text);
        } catch (final NumberFormatException e) {
            return null;
        }
    }

}
