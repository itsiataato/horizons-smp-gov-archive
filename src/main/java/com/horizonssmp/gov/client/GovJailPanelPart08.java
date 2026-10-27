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

abstract class GovJailPanelPart08 extends GovJailPanelPart07 {
    GovJailPanelPart08(final net.minecraft.client.gui.screens.Screen host) {
        super(host);
    }

    void drawStatus(final GuiGraphics graphics) {
        final GovRect area = this.statuses[this.subtab];
        if (area.w() <= 0 || area.h() <= 0) {
            return;
        }
        final String text;
        if (!this.status.isEmpty()) {
            text = this.status;
        } else if (this.subtab == 1) {
            text = this.detainPolicy;
        } else if (this.subtab == 2) {
            text = this.releasePolicy;
        } else if (this.subtab == 3) {
            text = this.extendPolicy;
        } else {
            text = "";
        }
        if (!text.isEmpty()) {
            GovText.clipped(graphics, this.font, text, area.x() + 3, area.y() + 1,
                    Math.max(0, area.w() - 6), GovTheme.MUTED);
        }
    }

    void drawFormLabel(final GuiGraphics graphics, final String label, final int x,
            final int y, final int width) {
        GovText.clipped(graphics, this.font, GovText.upperBoldCached(label), x, y + 4,
                Math.max(0, width - 2), GovTheme.MUTED);
    }

    void drawFieldFrame(final GuiGraphics graphics, final GovRect frame,
            final EditBox box) {
        if (frame.w() <= 0 || frame.h() <= 0 || box == null || !box.visible) {
            return;
        }
        GovWidgets.fieldFrame(graphics, frame, box.isFocused());
    }

    // --------------------------------------------------------------------- input


    boolean mouseClicked(final double mouseX, final double mouseY, final int button) {
        if (!this.active || button != 0) {
            return false;
        }
        // The cell prompt is modal: Confirm/Cancel dispatch, everything else is swallowed so
        // the list behind it stays inert.
        if (this.cellPromptOpen) {
            final Click prompt = GovJailCellPrompt.click(this, mouseX, mouseY);
            if (prompt != null) {
                handleClick(prompt);
            }
            return true;
        }
        // Field clicks focus their box; every other click drops the caret before a button can
        // execute while an invisible-of-mind text field still owns the next keystroke.
        if (!focusField(mouseX, mouseY)) {
            clearFocus();
        }
        for (final Button entry : this.buttons) {
            if (entry.enabled() && entry.rect().contains(mouseX, mouseY)) {
                handleClick(entry.click());
                return true;
            }
        }
        for (final Hit hit : this.rowHits) {
            if (hit.rect().contains(mouseX, mouseY)) {
                selectRow(hit.value());
                return true;
            }
        }
        return false;
    }

    /** Returns true when the click landed in a visible field; otherwise focus is cleared. */

    boolean focusField(final double mouseX, final double mouseY) {
        boolean focused = false;
        if (this.subtab == 1) {
            focused |= focusIf(this.detainTargetBox, this.targetFrame, mouseX, mouseY);
            focused |= focusIf(this.detainMinutesBox, this.minutesFrame, mouseX, mouseY);
            focused |= focusIf(this.warrantBox, this.warrantFrame, mouseX, mouseY);
            focused |= focusIf(this.detainReasonBox, this.reasonFrame, mouseX, mouseY);
        } else if (this.subtab == 2) {
            focused |= focusIf(this.releaseReasonBox, this.releaseReasonFrame, mouseX, mouseY);
        } else if (this.subtab == 3) {
            focused |= focusIf(this.extendMinutesBox, this.extendMinutesFrame, mouseX, mouseY);
            focused |= focusIf(this.extendReasonBox, this.extendReasonFrame, mouseX, mouseY);
        }
        return focused;
    }

    boolean focusIf(final EditBox box, final GovRect frame, final double mouseX,
            final double mouseY) {
        if (box != null && box.visible && frame.contains(mouseX, mouseY)) {
            this.host.setFocused(box);
            return true;
        }
        return false;
    }


    void handleClick(final Click click) {
        if (click == null) {
            return;
        }
        switch (click) {
            case FILTER -> cycleFilter();
            case ROW -> {
                // Row hits are handled before this switch.
            }
            case PREV -> changePage(-1);
            case NEXT -> changePage(1);
            case REFRESH -> refreshOrRetry();
            case RETRY_LIST -> requestList();
            case RETRY_DETAIL -> requestDetail(this.selectedId);
            case BASIS -> {
                this.detainBasis = nextBasis();
                this.detainArmed = false;
                this.status = "";
                syncWidgets();
                rebuildButtons();
            }
            case CONSENT -> {
                this.detainConsent = !this.detainConsent;
                this.detainArmed = false;
                rebuildButtons();
            }
            case DETAIN -> submitDetain();
            case RELEASE -> submitRelease();
            case EXTEND -> submitExtend();
            case CUFF -> mutate(OpCode.CUFF,
                    Map.of(JailKeys.KEY_TARGET, detailTarget()));
            case UNCUFF -> mutate(OpCode.UNCUFF,
                    Map.of(JailKeys.KEY_TARGET, detailTarget()));
            case CELL -> GovJailCellPrompt.open(this);
            case CELL_CONFIRM -> GovJailCellPrompt.confirm(this);
            case CELL_CANCEL -> GovJailCellPrompt.close(this);
            default -> {
            }
        }
    }


    String nextBasis() {
        if ("consent".equals(this.detainBasis)) {
            return "violent";
        }
        if ("violent".equals(this.detainBasis)) {
            return "warrant";
        }
        return "consent";
    }


    String detailTarget() {
        final String uuid = this.detail.get(JailKeys.TARGET_UUID);
        if (uuid != null && !uuid.isBlank()) {
            return uuid;
        }
        final JailRow row = selectedRow();
        return row == null ? "" : targetOf(row);
    }


    void changePage(final int delta) {
        final int next = GovMetrics.clamp(this.page + delta, 0, Math.max(0, this.pages - 1));
        if (next == this.page) {
            return;
        }
        this.page = next;
        this.listState = LoadState.LOADING;
        requestList();
        this.pageLabel = caps(tr("gui.gov.jail.page", this.page + 1, this.pages));
        rebuildButtons();
    }


    boolean mouseScrolled(final double mouseX, final double mouseY, final double scrollY) {
        if (!this.active) {
            return false;
        }
        if (this.cellPromptOpen) {
            return true;
        }
        final int direction = scrollY < 0 ? 1 : -1;
        final GovRect list = this.listBodies[this.subtab];
        if (list.contains(mouseX, mouseY) && this.listScrollMaxRows > 0) {
            this.listScrollRows[this.subtab] = GovMetrics.clamp(
                    this.listScrollRows[this.subtab] + direction, 0, this.listScrollMaxRows);
            rebuildRowHits();
            return true;
        }
        final GovRect detail = this.details[this.subtab];
        if (detail.contains(mouseX, mouseY) && this.detailScrollMaxRows > 0) {
            this.detailScrollRows[this.subtab] = GovMetrics.clamp(
                    this.detailScrollRows[this.subtab] + direction, 0,
                    this.detailScrollMaxRows);
            return true;
        }
        return false;
    }


}
