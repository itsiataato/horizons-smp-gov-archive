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

abstract class GovJailPanelPart05 extends GovJailPanelPart04 {
    GovJailPanelPart05(final net.minecraft.client.gui.screens.Screen host) {
        super(host);
    }

    void rebuildButtons() {
        this.buttons.clear();
        if (this.subtab == 0) {
            final GovRect top = this.topBars[0];
            if (top.h() > 0) {
                final int filterW = GovMetrics.span(top.w(), top.w() / 3, 60, 86);
                addButton(top.x() + 4, top.y() + 1, filterW, top.h() - 2, Click.FILTER, "",
                        this.filterLabel, true, true, false);
                final JailRow row = selectedRow();
                final boolean cuffed = row != null && "true".equals(
                        this.detail.get(JailKeys.CUFFED));
                final boolean cuff = row != null && "true".equals(
                        this.detail.get(JailKeys.CAN_CUFF_THIS));
                final boolean uncuff = row != null && "true".equals(
                        this.detail.get(JailKeys.CAN_UNCUFF_THIS));
                // One state-flip button: CUFF while the record is uncuffed, UNCUFF once it
                // is cuffed. The Click still routes to its matching opcode in Part08, so the
                // server semantics and permission gates are unchanged; the freed slot goes
                // to PUT IN CELL, which carries the longest top-bar label.
                final int flipW = GovMetrics.span(top.w(), top.w() / 6, 42, 60);
                final int cellPutW = GovMetrics.span(top.w(), top.w() / 5, 52, 110);
                addButton(top.right() - flipW - cellPutW - 8, top.y() + 1, cellPutW,
                        top.h() - 2, Click.CELL, "", this.buttonPutCell,
                        row != null && this.cellConfigured && !mutationPending(), false, false);
                addButton(top.right() - flipW - 4, top.y() + 1, flipW, top.h() - 2,
                        cuffed ? Click.UNCUFF : Click.CUFF, "",
                        cuffed ? this.buttonUncuff : this.buttonCuff,
                        (cuffed ? uncuff : cuff) && !mutationPending(), false, false);
            }
            final GovRect bar = this.bottoms[0];
            if (bar.h() > 0) {
                final int buttonW = Math.min(54, Math.max(0, bar.w() / 3));
                final int y = bar.y() + 1;
                final int h = Math.max(0, bar.h() - 2);
                addButton(bar.x() + 3, y, buttonW, h, Click.PREV, "", this.buttonPrev,
                        this.page > 0 && !mutationPending(), false, false);
                addButton(bar.x() + 3 + buttonW + 3, y, buttonW, h, Click.NEXT, "",
                        this.buttonNext, this.page < this.pages - 1 && !mutationPending(),
                        false, false);
                final int refreshW = GovMetrics.span(bar.w(), bar.w() / 5, 44, 60);
                addButton(bar.right() - refreshW - 3, y, refreshW, h, Click.REFRESH, "",
                        refreshLabel(), !mutationPending(), false, false);
            }
            if (this.listState == LoadState.ERROR) {
                addRetryButton(this.listBodies[0], Click.RETRY_LIST, true);
            }
            if (this.detailState == LoadState.ERROR && !this.selectedId.isEmpty()) {
                addRetryButton(this.details[0], Click.RETRY_DETAIL, false);
            }
        } else if (this.subtab == 1) {
            final GovRect body = this.bodies[1];
            if (body.h() <= 0) {
                return;
            }
            addButton(this.basisRect.x(), this.basisRect.y(), this.basisRect.w(),
                    this.basisRect.h(), Click.BASIS, "", basisLabel(this.detainBasis), true,
                    false, false);
            if ("consent".equals(this.detainBasis)) {
                addButton(this.consentRect.x(), this.consentRect.y(), this.consentRect.w(),
                        this.consentRect.h(), Click.CONSENT, "",
                        this.detainConsent ? this.consentYes : this.consentNo,
                        true, false, this.detainConsent);
            }
            final int detainW = this.detainButtonRect.w();
            final int row5 = this.detainButtonRect.y();
            addButton(this.detainButtonRect.x(), row5, detainW, this.detainButtonRect.h(),
                    Click.DETAIN, "",
                    this.detainArmed ? confirmLabel(this.buttonDetain) : this.buttonDetain,
                    this.canDetain && !mutationPending(), true, this.detainArmed);
        } else if (this.subtab == 2) {
            addButton(this.releaseButtonRect.x(), this.releaseButtonRect.y(),
                    this.releaseButtonRect.w(), this.releaseButtonRect.h(), Click.RELEASE, "",
                    this.releaseArmed ? confirmLabel(this.buttonRelease) : this.buttonRelease,
                    this.canRelease && !this.selectedId.isEmpty() && !mutationPending(), true,
                    this.releaseArmed);
            addButton(this.releaseButtonRect.right() + 4, this.releaseButtonRect.y(),
                    GovMetrics.span(this.bodies[2].w(), this.bodies[2].w() / 5, 46, 62),
                    this.releaseButtonRect.h(), Click.REFRESH, "", refreshLabel(),
                    !mutationPending(), false, false);
            if (this.listState == LoadState.ERROR) {
                addRetryButton(this.listBodies[2], Click.RETRY_LIST, true);
            }
        } else {
            addButton(this.extendButtonRect.x(), this.extendButtonRect.y(),
                    this.extendButtonRect.w(), this.extendButtonRect.h(), Click.EXTEND, "",
                    this.extendArmed ? confirmLabel(this.buttonExtend) : this.buttonExtend,
                    this.canRelease && canExtendSelected() && !mutationPending(), true,
                    this.extendArmed);
            addButton(this.extendButtonRect.right() + 4, this.extendButtonRect.y(),
                    GovMetrics.span(this.bodies[3].w(), this.bodies[3].w() / 5, 46, 62),
                    this.extendButtonRect.h(), Click.REFRESH, "", refreshLabel(),
                    !mutationPending(), false, false);
            if (this.listState == LoadState.ERROR) {
                addRetryButton(this.listBodies[3], Click.RETRY_LIST, true);
            }
        }
        GovJailCellPrompt.rebuild(this);
    }

    /**
     * Places the error-body Retry control inside the failed surface: top-right beside the title,
     * or bottom-right in a detail pane whose first lines are the reason. The pager also switches
     * its Refresh label to Retry, so a surface too small for this control still offers the action.
     */

    void addRetryButton(final GovRect area, final Click click, final boolean top) {
        if (area.w() <= 0 || area.h() < 22 || this.font == null) {
            return;
        }
        final int width = GovMetrics.span(area.w() - 8, this.font.width(this.buttonRetry) + 16,
                40, 72);
        final int height = Math.min(GovMetrics.BUTTON_H, Math.max(0, area.h() - 4));
        if (width < 20 || height < 10) {
            return;
        }
        final int x = area.right() - 4 - width;
        final int y = top ? area.y() + 2 : area.bottom() - height - 4;
        addButton(x, y, width, height, click, "", this.buttonRetry, !mutationPending(), false,
                false);
    }


    String refreshLabel() {
        final boolean failed = this.listState == LoadState.ERROR
                || (this.subtab == 0 && this.detailState == LoadState.ERROR
                        && !this.selectedId.isEmpty());
        return failed ? this.buttonRetry : this.buttonRefresh;
    }

    /**
     * The pager's label follows the failed surface, so its action must too: a failed list is
     * re-requested, while a compact layout with only the detail failed retries that detail.
     */

    void refreshOrRetry() {
        if (this.listState == LoadState.ERROR) {
            requestList();
        } else if (this.subtab == 0 && this.detailState == LoadState.ERROR
                && !this.selectedId.isEmpty()) {
            requestDetail(this.selectedId);
        } else {
            requestList();
        }
    }


    GovRect buttonRect(final Click click) {
        for (final Button entry : this.buttons) {
            if (entry.click() == click) {
                return entry.rect();
            }
        }
        return null;
    }


    String confirmLabel(final String action) {
        return this.textConfirmPrefix + " " + action.toUpperCase(Locale.ROOT);
    }

    void addButton(final int x, final int y, final int w, final int h,
            final Click click, final String value, final String label, final boolean enabled,
            final boolean primary, final boolean selected) {
        if (w <= 0 || h <= 0) {
            return;
        }
        final GovRect rect = rect(x, y, w, h);
        if (rect.w() <= 0 || rect.h() <= 0) {
            return;
        }
        this.buttons.add(new Button(rect, click, value, label, enabled, primary, selected));
    }


    boolean canExtendSelected() {
        final JailRow row = selectedRow();
        return row != null && "warrant".equalsIgnoreCase(row.basis());
    }

    // ------------------------------------------------------------------ submit


    void submitDetain() {
        final String target = value(this.detainTargetBox);
        final String reason = value(this.detainReasonBox);
        final String warrant = value(this.warrantBox);
        final String minutes = value(this.detainMinutesBox);
        if (target.isEmpty()) {
            fail(this.textTargetRequired);
            return;
        }
        if (reason.isEmpty()) {
            fail(this.textReasonRequired);
            return;
        }
        if ("consent".equals(this.detainBasis) && !this.detainConsent) {
            fail(this.textConsentRequired);
            return;
        }
        if ("warrant".equals(this.detainBasis)) {
            if (warrant.isEmpty()) {
                fail(this.textWarrantRequired);
                return;
            }
            if (!isPositive(minutes)) {
                fail(this.textMinutesRequired);
                return;
            }
        } else if (!minutes.isEmpty() && !isPositive(minutes)) {
            fail(this.textMinutesInvalid);
            return;
        }
        if (!armIfNeeded(Click.DETAIN)) {
            return;
        }
        final Map<String, String> payload = new LinkedHashMap<>();
        payload.put(JailKeys.KEY_TARGET, target);
        payload.put(JailKeys.KEY_BASIS, this.detainBasis);
        payload.put(JailKeys.KEY_REASON, reason);
        payload.put(JailKeys.KEY_WARRANT_REF, warrant);
        payload.put(JailKeys.KEY_MINUTES, minutes);
        payload.put(JailKeys.KEY_CONSENT_CONFIRMED, Boolean.toString(this.detainConsent));
        mutate(OpCode.JAIL_COMMIT, payload);
    }


}
