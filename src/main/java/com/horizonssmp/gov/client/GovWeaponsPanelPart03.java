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

abstract class GovWeaponsPanelPart03 extends GovWeaponsPanelPart02 {
    GovWeaponsPanelPart03(final net.minecraft.client.gui.screens.Screen host) {
        super(host);
    }


    void buildWeaponDetail(final int width) {
        if (this.detailState == LoadState.LOADING) {
            this.detailLines.add(DetailLine.row("", this.textLoading, GovTheme.MUTED));
            return;
        }
        if (this.detailState == LoadState.ERROR) {
            this.detailLines.add(DetailLine.row("", this.detailError, GovTheme.TEXT));
            return;
        }
        if (this.detail.isEmpty()) {
            this.detailLines.add(DetailLine.row("", this.textNoSelection, GovTheme.MUTED));
            return;
        }
        this.detailLines.add(DetailLine.row(this.labelClass,
                dash(this.detail.get(WeaponKeys.CLASS)), GovTheme.BODY));
        this.detailLines.add(DetailLine.row(this.labelType,
                dash(this.detail.get(WeaponKeys.TYPE)), GovTheme.BODY));
        this.detailLines.add(DetailLine.row(this.labelOwner,
                dash(this.detail.get(WeaponKeys.OWNER)), GovTheme.BODY));
        this.detailLines.add(DetailLine.row(this.labelStatus,
                statusText(this.detail.get(WeaponKeys.STATUS)), GovTheme.BODY));
        this.detailLines.add(DetailLine.row(this.labelAuthority,
                authorityText(this.detail.get(WeaponKeys.STATUS),
                        this.detail.get(WeaponKeys.PERMIT)), GovTheme.TEXT));
        this.detailLines.add(DetailLine.row(this.labelPermit,
                this.detail.getOrDefault(WeaponKeys.PERMIT, "").isBlank()
                        ? "-" : permitLabel(this.detail.get(WeaponKeys.PERMIT)), GovTheme.MUTED));
        this.detailLines.add(DetailLine.row(this.labelRegistered,
                when(longField(this.detail, WeaponKeys.REGISTERED_AT)) + "  "
                        + dash(this.detail.get(WeaponKeys.REGISTERED_BY)), GovTheme.FAINT));
        this.detailLines.add(DetailLine.row(this.labelTransfers,
                dash(this.detail.get(WeaponKeys.TRANSFER_COUNT)), GovTheme.MUTED));
        this.detailLines.add(DetailLine.row(this.labelDealer,
                dash(this.detail.get(WeaponKeys.DEALER)), GovTheme.FAINT));
        this.detailLines.add(DetailLine.row(this.labelSale,
                longField(this.detail, WeaponKeys.SOLD_AT) <= 0L ? "-"
                        : when(longField(this.detail, WeaponKeys.SOLD_AT)), GovTheme.FAINT));
        this.detailLines.add(DetailLine.row(this.labelSource,
                dash(this.detail.get(WeaponKeys.SOURCE)), GovTheme.FAINT));
    }

    void buildAreaDetail(final int width) {
        final AreaRow row = selectedArea();
        if (row == null) {
            this.detailLines.add(DetailLine.row("", this.textNoSelection, GovTheme.MUTED));
            return;
        }
        this.detailLines.add(DetailLine.row(this.labelArea, row.id(), GovTheme.BODY));
        this.detailLines.add(DetailLine.row(this.labelHolder, dash(row.issuedBy()), GovTheme.BODY));
        this.detailLines.add(DetailLine.row(this.labelPurpose, "", GovTheme.MUTED));
        for (final FormattedCharSequence line : GovText.wrapCached(this.font,
                dash(row.reason()), Math.max(1, width - 4))) {
            this.detailLines.add(DetailLine.text(line, GovTheme.MUTED));
        }
        this.detailLines.add(DetailLine.row(this.labelWindow, row.windowText(), GovTheme.FAINT));
        this.detailLines.add(DetailLine.row(this.labelStatus, row.statusText(), GovTheme.TEXT));
        this.detailLines.add(DetailLine.row(this.labelDimension, row.dimension(), GovTheme.BODY));
        this.detailLines.add(DetailLine.row(this.labelCenter,
                row.x() + " " + row.y() + " " + row.z() + "  r" + row.radius(), GovTheme.BODY));
        this.detailLines.add(DetailLine.row(this.labelBox, dash(row.box()), GovTheme.FAINT));
    }

    // ------------------------------------------------------------------ buttons

    String selectionKey() {
        return switch (this.subtab) {
            case 0 -> this.selectedSerial;
            case 1 -> this.selectedPermitId;
            case 2 -> this.selectedAreaId;
            default -> "";
        };
    }

    void buildPermitDetail(final int width) {
        final PermitRow row = selectedPermit();
        if (row == null) {
            this.detailLines.add(DetailLine.row("", this.textNoSelection, GovTheme.MUTED));
            return;
        }
        this.detailLines.add(DetailLine.row(this.labelHolder, row.holder(), GovTheme.BODY));
        this.detailLines.add(DetailLine.row(this.labelClass, row.classLabel(), GovTheme.BODY));
        this.detailLines.add(DetailLine.row(this.labelStatus, row.statusText(), GovTheme.TEXT));
        this.detailLines.add(DetailLine.row(this.labelIssued,
                when(row.issuedAt()) + "  " + dash(row.issuedBy()), GovTheme.FAINT));
        this.detailLines.add(DetailLine.row(this.labelExpires, row.expiryText(), GovTheme.FAINT));
        if (row.reason() != null && !row.reason().isBlank()) {
            this.detailLines.add(DetailLine.separator());
            this.detailLines.add(DetailLine.row(this.labelReason, "", GovTheme.MUTED));
            for (final FormattedCharSequence line : GovText.wrapCached(this.font, row.reason(),
                    Math.max(1, width - 4))) {
                this.detailLines.add(DetailLine.text(line, GovTheme.MUTED));
            }
        }
    }

    void rebuildDetailLines() {
        final GovRect detail = this.details[this.subtab];
        final int width = Math.max(0, detail.w() - 12);
        final String key = this.subtab + ":" + selectionKey() + ":" + width + ":"
                + this.detailState.name() + ":" + this.detailRevision;
        if (key.equals(this.detailLinesKey) && width == this.detailLinesWidth) {
            return;
        }
        this.detailLinesKey = key;
        this.detailLinesWidth = width;
        this.detailLines.clear();
        if (width <= 0 || detail.h() <= 0) {
            return;
        }
        if (this.subtab == 0) {
            buildWeaponDetail(width);
        } else if (this.subtab == 1) {
            buildPermitDetail(width);
        } else if (this.subtab == 2) {
            buildAreaDetail(width);
        }
        this.detailScrollMaxRows = Math.max(0, this.detailLines.size()
                - Math.max(0, this.visibleDetailRows));
        this.detailScrollRows[this.subtab] = GovMetrics.clamp(
                this.detailScrollRows[this.subtab], 0, this.detailScrollMaxRows);
    }

    String rowId(final int index) {
        return switch (this.subtab) {
            case 0 -> index < this.weaponRows.size() ? this.weaponRows.get(index).serial() : "";
            case 1 -> index < this.permitRows.size() ? this.permitRows.get(index).id() : "";
            case 2 -> index < this.areaRows.size() ? this.areaRows.get(index).id() : "";
            default -> "";
        };
    }

    // ------------------------------------------------------------------ caching

    int listRowCount() {
        return switch (this.subtab) {
            case 0 -> this.weaponRows.size();
            case 1 -> this.permitRows.size();
            case 2 -> this.areaRows.size();
            default -> 0;
        };
    }

    void rebuildRowHits() {
        this.rowHits.clear();
        final GovRect list = this.listBodies[this.subtab];
        if (this.listState != LoadState.READY || list.w() <= 0 || list.h() <= 0
                || this.rowPitch <= 0) {
            return;
        }
        final int scroll = this.listScrollRows[this.subtab];
        for (int i = 0; i < this.visibleRows; i++) {
            final int index = scroll + i;
            if (index >= listRowCount()) {
                break;
            }
            final int y = list.y() + i * this.rowPitch;
            if (y + this.rowPitch > list.bottom()) {
                break;
            }
            final GovRect hit = rect(list.x() + 1, y, Math.max(0, list.w() - 2),
                    Math.max(0, this.rowPitch - 1));
            if (hit.h() > 0) {
                this.rowHits.add(new Hit(hit, Click.ROW, rowId(index)));
            }
        }
    }

    void requestPermitList() {
        final String reqId = UUID.randomUUID().toString();
        this.pending.put(reqId, RequestKind.PERMIT_LIST);
        this.latestPermitReqId = reqId;
        this.listState = LoadState.LOADING;
        this.listError = "";
        rebuildRowHits();
        final Map<String, String> payload = new LinkedHashMap<>();
        final String target = this.permitTarget.isBlank() ? this.viewerName : this.permitTarget;
        if (!target.isBlank()) {
            payload.put("target", target);
        }
        payload.put(WeaponKeys.KEY_PAGE, Integer.toString(this.permitPage));
        GovNetwork.sendRequest(OpCode.WPN_PERMIT_LIST, payload, reqId);
        rebuildButtons();
    }

    static GovRect retryRect(final GovRect viewport) {
        if (viewport.w() <= 0 || viewport.h() <= 0) {
            return EMPTY;
        }
        final int width = Math.min(RETRY_W, viewport.w());
        final int height = Math.min(RETRY_H, viewport.h());
        return new GovRect(viewport.x() + (viewport.w() - width) / 2,
                viewport.bottom() - height, width, height);
    }

    void updateListMetrics() {
        final GovRect list = this.lists[this.subtab];
        final int rows = listRowCount();
        final int headerH = this.bounds.h() < 130 ? 0 : 10;
        final int rowArea = Math.max(0, list.h() - headerH);
        this.visibleRows = this.rowPitch > 0 ? rowArea / this.rowPitch : 0;
        this.listScrollMaxRows = Math.max(0, rows - this.visibleRows);
        this.listScrollRows[this.subtab] = GovMetrics.clamp(this.listScrollRows[this.subtab], 0,
                this.listScrollMaxRows);
        this.listBodies[this.subtab] = rect(list.x(), list.y() + headerH, list.w(),
                Math.max(0, list.h() - headerH));
        this.listScrollbarRect = rect(list.right() - 4, this.listBodies[this.subtab].y(), 3,
                this.listBodies[this.subtab].h());
        this.listRetryRect = retryRect(this.listBodies[this.subtab]);

        final GovRect detail = this.details[this.subtab];
        final int actionH = this.subtab == 0 && detail.h() > 0 ? 18 : 0;
        final int detailArea = Math.max(0, detail.h() - 16 - actionH);
        this.detailScrollbarRect = rect(detail.right() - 4, detail.y() + 16, 3,
                Math.max(0, detail.h() - 18 - actionH));
        this.detailRetryRect = retryRect(detailBodyViewport(detail, actionH));
        this.visibleDetailRows = this.detailPitch > 0 ? detailArea / this.detailPitch : 0;
        this.detailScrollMaxRows = Math.max(0, this.detailLines.size() - this.visibleDetailRows);
        this.detailScrollRows[this.subtab] = GovMetrics.clamp(
                this.detailScrollRows[this.subtab], 0, this.detailScrollMaxRows);
        rebuildRowHits();
    }

    /** The detail paper below its title rule and above the registry action row. */

    WeaponRow selectedWeapon() {
        for (final WeaponRow row : this.weaponRows) {
            if (row.serial().equals(this.selectedSerial)) {
                return row;
            }
        }
        return null;
    }

    void updateFilterLabel() {
        int index = 0;
        for (int i = 0; i < this.filterIds.length; i++) {
            if (this.filterIds[i].equals(this.filter)) {
                index = i;
                break;
            }
        }
        this.filterLabel = this.buttonFilter + " " + this.filterLabels[index];
        this.policyText = this.bounds.h() < 130 ? this.textPolicyShort : this.textPolicyLong;
    }

}
