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
import com.horizonssmp.gov.common.JailKeys;
import com.horizonssmp.gov.common.OpCode;
import com.horizonssmp.gov.network.GovActionResultPayload;
import com.horizonssmp.gov.network.GovNetwork;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import static com.horizonssmp.gov.client.GovJailTypes.*;
import static com.horizonssmp.gov.client.GovJailConstants.*;

abstract class GovJailPanelPart04 extends GovJailPanelPart03 {
    GovJailPanelPart04(final net.minecraft.client.gui.screens.Screen host) {
        super(host);
    }

    int listRowCount() {
        return switch (this.subtab) {
            case 0 -> this.rows.size();
            case 2, 3 -> this.activeRows.size();
            default -> 0;
        };
    }


    JailRow rowAt(final int index) {
        return switch (this.subtab) {
            case 0 -> index < this.rows.size() ? this.rows.get(index) : null;
            case 2, 3 -> index < this.activeRows.size() ? this.activeRows.get(index) : null;
            default -> null;
        };
    }


    String rowId(final int index) {
        final JailRow row = rowAt(index);
        return row == null ? "" : row.id();
    }


    void rebuildRowHits() {
        this.rowHits.clear();
        // Hits only exist for a rendered result set: while a request is in flight or failed,
        // the previous page's rows are still in memory but must not be clickable.
        if (this.listState != LoadState.READY) {
            return;
        }
        final GovRect list = this.listBodies[this.subtab];
        if (list.w() <= 0 || list.h() <= 0 || this.rowPitch <= 0) {
            return;
        }
        final int scroll = this.listScrollRows[this.subtab];
        for (int i = 0; i < this.visibleRows; i++) {
            final int index = scroll + i;
            if (index >= listRowCount()) {
                break;
            }
            final int y = list.y() + i * this.rowPitch;
            final GovRect hit = rect(list.x() + 1, y, Math.max(0, list.w() - 2),
                    Math.max(0, this.rowPitch - 1));
            if (hit.h() > 0) {
                this.rowHits.add(new Hit(hit, Click.ROW, rowId(index)));
            }
        }
    }

    // ------------------------------------------------------------------ caching


    void updateListTexts(final int mode) {
        if (mode != this.subtab) {
            return;
        }
        if (this.listState == LoadState.READY && mode == 0) {
            this.listHeader = this.total > this.rows.size()
                    ? tr("gui.gov.jail.showing", this.rows.size(), this.total)
                    : tr("gui.gov.jail.count", this.total, this.activeTotal);
        } else {
            this.listHeader = "";
        }
        this.pageLabel = caps(tr("gui.gov.jail.page", this.page + 1, this.pages));
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
    }


    void updateDetainPolicy() {
        this.detainPolicy = tr("gui.gov.jail.detain_policy", this.maxNoWarrantMinutes,
                this.maxWarrantMinutes);
        this.cellText = this.cellConfigured && !this.cell.isBlank()
                ? caps(this.labelCell) + " " + this.cell : this.textNoCell;
    }


    void updateSummaryTexts() {
        this.detailTitle = "";
        this.summaryText = "";
        this.extendCeiling = "";
        if (this.subtab == 0 && this.detailState == LoadState.ERROR
                && !this.detailError.isEmpty()) {
            // The stacked layout has no detail pane, so the failure reason would otherwise be
            // invisible: put the server's reason in the one line that is always drawn.
            this.summaryText = this.detailError;
            return;
        }
        final JailRow row = selectedRow();
        if (row == null) {
            this.summaryText = this.textNoSelection;
            return;
        }
        if (this.subtab == 0) {
            this.summaryText = row.summary();
            this.detailTitle = tr("gui.gov.jail.record_title", row.target());
        } else if (this.subtab == 2) {
            this.summaryText = row.summary();
        } else if (this.subtab == 3) {
            this.summaryText = row.summary();
            this.extendCeiling = "WARRANT".equalsIgnoreCase(row.basis())
                    ? tr("gui.gov.jail.warrant_ceiling", this.maxWarrantMinutes)
                    : this.textExtendNeedsWarrant;
        }
    }


    void rebuildDetailLines() {
        final GovRect detail = this.details[this.subtab];
        final int width = Math.max(0, detail.w() - 12);
        final String key = this.subtab + ":" + this.selectedId + ":" + width + ":"
                + this.detailState.name() + ":" + this.detailRevision;
        if (key.equals(this.detailLinesKey) && width == this.detailLinesWidth) {
            return;
        }
        this.detailLinesKey = key;
        this.detailLinesWidth = width;
        this.detailLines.clear();
        if (width <= 0 || detail.h() <= 0 || this.subtab != 0) {
            this.detailScrollMaxRows = 0;
            return;
        }
        buildDetail(width);
        this.detailScrollMaxRows = Math.max(0, this.detailLines.size()
                - Math.max(0, this.visibleDetailRows));
        this.detailScrollRows[0] = GovMetrics.clamp(this.detailScrollRows[0], 0,
                this.detailScrollMaxRows);
    }


    void buildDetail(final int width) {
        if (this.detailState == LoadState.LOADING) {
            this.detailLines.add(DetailLine.row("", this.textLoading, GovTheme.MUTED));
            return;
        }
        if (this.detailState == LoadState.ERROR) {
            this.detailLines.add(DetailLine.row("", this.textError, GovTheme.TEXT));
            if (!this.detailError.isEmpty()) {
                for (final FormattedCharSequence line : GovText.wrapCached(this.font,
                        this.detailError, Math.max(1, width - 4))) {
                    this.detailLines.add(DetailLine.text(line, GovTheme.MUTED));
                }
            }
            return;
        }
        if (this.detail.isEmpty()) {
            this.detailLines.add(DetailLine.row("", this.textNoSelection, GovTheme.MUTED));
            return;
        }
        this.detailLines.add(DetailLine.row(this.labelId,
                dash(this.detail.get(JailKeys.ID)), GovTheme.FAINT));
        this.detailLines.add(DetailLine.row(this.labelOfficer,
                dash(this.detail.get(JailKeys.OFFICER)), GovTheme.BODY));
        this.detailLines.add(DetailLine.row(this.labelBasis,
                basisLabel(this.detail.get(JailKeys.BASIS)), GovTheme.BODY));
        this.detailLines.add(DetailLine.row(this.labelStarted,
                when(longField(this.detail, JailKeys.STARTED_AT)), GovTheme.FAINT));
        this.detailLines.add(DetailLine.row(this.labelTerm,
                dash(this.detail.get(JailKeys.MAX_MINUTES)) + " MIN  ("
                        + when(longField(this.detail, JailKeys.EXPIRES_AT)) + ")",
                GovTheme.BODY));
        this.detailLines.add(DetailLine.row(this.labelRemaining,
                remaining(longField(this.detail, JailKeys.REMAINING_MS)), GovTheme.BODY));
        this.detailLines.add(DetailLine.row(this.labelStatus,
                statusText(this.detail.get(JailKeys.STATUS)), GovTheme.TEXT));
        this.detailLines.add(DetailLine.row(this.labelWarrant,
                this.detail.getOrDefault(JailKeys.WARRANT_REF, "").isBlank()
                        ? this.textNoWarrant : this.detail.get(JailKeys.WARRANT_REF),
                GovTheme.BODY));
        this.detailLines.add(DetailLine.row(this.labelCuffs,
                "true".equals(this.detail.get(JailKeys.CUFFED))
                        ? tr("gui.gov.jail.cuffed") : tr("gui.gov.jail.uncuffed"),
                GovTheme.BODY));
        this.detailLines.add(DetailLine.separator());
        this.detailLines.add(DetailLine.row(this.labelReason, "", GovTheme.MUTED));
        final String reason = this.detail.getOrDefault(JailKeys.REASON, "");
        for (final FormattedCharSequence line : GovText.wrapCached(this.font, reason,
                Math.max(1, width - 4))) {
            this.detailLines.add(DetailLine.text(line, GovTheme.BODY));
        }
        this.detailLines.add(DetailLine.separator());
        this.detailLines.add(DetailLine.row(this.labelRelease, "", GovTheme.MUTED));
        if (longField(this.detail, JailKeys.RELEASED_AT) <= 0L) {
            this.detailLines.add(DetailLine.row("", this.textNotReleased, GovTheme.MUTED));
        } else {
            this.detailLines.add(DetailLine.row("",
                    when(longField(this.detail, JailKeys.RELEASED_AT)) + "  "
                            + dash(this.detail.get(JailKeys.RELEASED_BY)),
                    GovTheme.BODY));
            final String releaseReason = this.detail.getOrDefault(JailKeys.RELEASE_REASON, "");
            for (final FormattedCharSequence line : GovText.wrapCached(this.font, releaseReason,
                    Math.max(1, width - 4))) {
                this.detailLines.add(DetailLine.text(line, GovTheme.MUTED));
            }
        }
        this.detailLines.add(DetailLine.separator());
        this.detailLines.add(DetailLine.row(this.labelConfinement,
                tr("gui.gov.jail.adapter", dash(this.adapter),
                        this.cell.isBlank() ? this.textNoCell : this.cell),
                GovTheme.BODY));
        final String cellLocation = this.detail.getOrDefault(JailKeys.CELL_LOCATION, "");
        this.detailLines.add(DetailLine.row(this.labelCell,
                cellLocation.isBlank() ? this.textNoCell : cellLocation, GovTheme.FAINT));
        final int extensions = intField(this.detail, JailKeys.EXTENSION_COUNT, 0);
        if (extensions > 0) {
            this.detailLines.add(DetailLine.row(this.labelExtensions,
                    Integer.toString(extensions), GovTheme.MUTED));
        }
        this.detailLines.add(DetailLine.separator());
        for (final FormattedCharSequence line : GovText.wrapCached(this.font, this.textSurrender,
                Math.max(1, width - 4))) {
            this.detailLines.add(DetailLine.text(line, GovTheme.MUTED));
        }
        if (!this.nativeCuffs) {
            for (final FormattedCharSequence line : GovText.wrapCached(this.font,
                    this.textCuffsDisabled, Math.max(1, width - 4))) {
                this.detailLines.add(DetailLine.text(line, GovTheme.MUTED));
            }
        }
    }

    // ------------------------------------------------------------------ buttons


}
