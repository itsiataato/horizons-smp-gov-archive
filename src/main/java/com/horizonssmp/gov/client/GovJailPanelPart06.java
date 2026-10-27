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

abstract class GovJailPanelPart06 extends GovJailPanelPart05 {
    GovJailPanelPart06(final net.minecraft.client.gui.screens.Screen host) {
        super(host);
    }

    void submitRelease() {
        final JailRow row = selectedRow();
        final String reason = value(this.releaseReasonBox);
        if (row == null) {
            fail(this.textNoSelection);
            return;
        }
        if (reason.isEmpty()) {
            fail(this.textReasonRequired);
            return;
        }
        if (!armIfNeeded(Click.RELEASE)) {
            return;
        }
        mutate(OpCode.JAIL_RELEASE, Map.of(
                JailKeys.KEY_TARGET, targetOf(row),
                JailKeys.KEY_REASON, reason));
    }


    void submitExtend() {
        final JailRow row = selectedRow();
        final String minutes = value(this.extendMinutesBox);
        final String reason = value(this.extendReasonBox);
        if (row == null) {
            fail(this.textNoSelection);
            return;
        }
        if (!"warrant".equalsIgnoreCase(row.basis())) {
            fail(this.textExtendNeedsWarrant);
            return;
        }
        if (!isPositive(minutes)) {
            fail(this.textMinutesRequired);
            return;
        }
        if (reason.isEmpty()) {
            fail(this.textReasonRequired);
            return;
        }
        if (!armIfNeeded(Click.EXTEND)) {
            return;
        }
        mutate(OpCode.JAIL_EXTEND, Map.of(
                JailKeys.KEY_TARGET, targetOf(row),
                JailKeys.KEY_MINUTES, minutes,
                JailKeys.KEY_REASON, reason));
    }


    void submitCell() {
        final JailRow row = selectedRow();
        if (row == null) {
            fail(this.textNoSelection);
            return;
        }
        mutate(OpCode.JAIL_CELL, Map.of(
                JailKeys.KEY_TARGET, targetOf(row),
                JailKeys.KEY_CELL, String.valueOf(this.cellNumber)));
    }


    String targetOf(final JailRow row) {
        if (row == null) {
            return "";
        }
        return row.targetUuid().isBlank() ? row.target() : row.targetUuid();
    }


    boolean armIfNeeded(final Click action) {
        final long now = System.currentTimeMillis();
        boolean armed = action == Click.RELEASE ? this.releaseArmed
                : action == Click.EXTEND ? this.extendArmed : this.detainArmed;
        if (armed && now - this.armedAt > ARM_MS) {
            setArmed(action, false);
            armed = false;
        }
        if (!armed) {
            // Only one destructive action may be armed: armedAt is shared, so leaving a second
            // arm set would let it confirm against the other action's timestamp.
            this.detainArmed = false;
            this.releaseArmed = false;
            this.extendArmed = false;
            setArmed(action, true);
            this.armedAt = now;
            this.status = confirmLabel(action == Click.RELEASE ? this.buttonRelease
                    : action == Click.EXTEND ? this.buttonExtend : this.buttonDetain);
            this.statusIsError = false;
            rebuildButtons();
            return false;
        }
        if (now - this.armedAt < ARM_FLOOR_MS) {
            return false;
        }
        setArmed(action, false);
        return true;
    }


    void setArmed(final Click action, final boolean armed) {
        if (action == Click.RELEASE) {
            this.releaseArmed = armed;
        } else if (action == Click.EXTEND) {
            this.extendArmed = armed;
        } else if (action == Click.DETAIN) {
            this.detainArmed = armed;
        }
        if (!armed) {
            this.armedAt = 0L;
        }
        rebuildButtons();
    }


    void disarm() {
        if (this.detainArmed || this.releaseArmed || this.extendArmed) {
            this.detainArmed = false;
            this.releaseArmed = false;
            this.extendArmed = false;
            this.armedAt = 0L;
            rebuildButtons();
        }
        // A mutate answer, tab switch or deactivate also settles the cell prompt, whose box
        // would otherwise stay visible over the next surface.
        GovJailCellPrompt.close(this);
    }


    void fail(final String message) {
        this.status = message;
        this.statusIsError = true;
        disarm();
        rebuildButtons();
    }


    void clearFields() {
        setValue(this.detainTargetBox, "");
        setValue(this.detainReasonBox, "");
        setValue(this.warrantBox, "");
        setValue(this.detainMinutesBox, "");
        setValue(this.releaseReasonBox, "");
        setValue(this.extendMinutesBox, "");
        setValue(this.extendReasonBox, "");
        this.detainConsent = false;
    }


    void setValue(final EditBox box, final String value) {
        if (box != null) {
            box.setValue(value == null ? "" : value);
        }
    }

    // ------------------------------------------------------------------ widgets


    void syncWidgets() {
        final boolean on = this.active;
        setVisible(this.detainTargetBox, on && this.subtab == 1);
        setVisible(this.detainReasonBox, on && this.subtab == 1);
        setVisible(this.warrantBox, on && this.subtab == 1
                && "warrant".equals(this.detainBasis));
        setVisible(this.detainMinutesBox, on && this.subtab == 1);
        setVisible(this.releaseReasonBox, on && this.subtab == 2);
        setVisible(this.extendMinutesBox, on && this.subtab == 3);
        setVisible(this.extendReasonBox, on && this.subtab == 3);
    }


    void setVisible(final EditBox box, final boolean visible) {
        if (box == null) {
            return;
        }
        box.setVisible(visible);
        if (!visible && this.host.getFocused() == box) {
            this.host.setFocused(null);
        }
    }

    // -------------------------------------------------------------------- render


    void render(final GuiGraphics graphics, final int mouseX, final int mouseY) {
        if (!this.active || this.font == null) {
            return;
        }
        if (this.subtab == 0) {
            drawDetentions(graphics, mouseX, mouseY);
        } else if (this.subtab == 1) {
            drawDetain(graphics, mouseX, mouseY);
        } else if (this.subtab == 2) {
            drawRelease(graphics, mouseX, mouseY);
        } else {
            drawExtend(graphics, mouseX, mouseY);
        }
        drawButtons(graphics, mouseX, mouseY);
        drawStatus(graphics);
        GovJailCellPrompt.paint(this, graphics, mouseX, mouseY);
    }


    void drawDetentions(final GuiGraphics graphics, final int mouseX, final int mouseY) {
        final GovRect top = this.topBars[0];
        if (top.h() > 0) {
            GovWidgets.rule(graphics, top.x(), top.bottom(), top.w(), GovTheme.BORDER_SOFT);
        }
        drawList(graphics, mouseX, mouseY, 0);
        final GovRect detail = this.details[0];
        if (detail.w() > 0 && detail.h() > 0) {
            drawDetail(graphics, detail);
        } else {
            final GovRect summary = this.summaries[0];
            if (summary.w() > 0 && summary.h() > 0) {
                GovText.clipped(graphics, this.font, this.summaryText, summary.x() + 2,
                        summary.y() + 1, Math.max(0, summary.w() - 4), GovTheme.MUTED);
            }
        }
        final GovRect bar = this.bottoms[0];
        if (bar.h() > 0) {
            GovWidgets.rule(graphics, bar.x(), bar.y(), bar.w(), GovTheme.BORDER_SOFT);
            final int buttonW = Math.min(54, Math.max(0, bar.w() / 3));
            final int left = bar.x() + 3 + buttonW * 2 + 6;
            final int right = bar.right()
                    - GovMetrics.span(bar.w(), bar.w() / 5, 44, 60) - 6;
            if (right > left && !this.pageLabel.isEmpty()) {
                GovText.centered(graphics, this.font, this.pageLabel, left, bar.y() + 3,
                        right - left, GovTheme.MUTED);
            }
        }
    }


}
