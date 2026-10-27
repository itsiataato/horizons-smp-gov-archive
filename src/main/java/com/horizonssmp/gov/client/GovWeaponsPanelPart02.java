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

abstract class GovWeaponsPanelPart02 extends GovWeaponsPanelPart01 {
    GovWeaponsPanelPart02(final net.minecraft.client.gui.screens.Screen host) {
        super(host);
    }


    void rebuildButtons() {
        this.buttons.clear();
        final GovRect top = this.topBars[this.subtab];
        if (this.subtab == 0) {
            final int filterW = GovMetrics.span(top.w(), top.w() / 3, 58, 78);
            addButton(top.x() + 4, top.y() + 1, filterW, top.h() - 2, Click.FILTER, "",
                    this.filterLabel, true, true, false);
            if (this.canRegister) {
                final int registerW = GovMetrics.span(top.w(), top.w() / 4, 66, 84);
                addButton(top.right() - registerW - 4, top.y() + 1, registerW, top.h() - 2,
                        Click.REGISTER_OPEN, "", this.buttonRegister, true, false, false);
            }
            if (this.details[0].h() <= 0) {
                final int recordW = GovMetrics.span(top.w(), top.w() / 5, 48, 64);
                final int x = top.x() + 4 + filterW + 4;
                if (x + recordW <= top.right() - 4) {
                    addButton(x, top.y() + 1, recordW, top.h() - 2, Click.RECORD_OPEN, "",
                            this.buttonRecord, !this.selectedSerial.isEmpty(), false, false);
                }
            }
            if (this.details[0].h() > 0 && !this.selectedSerial.isEmpty()) {
                addDetailActions(this.details[0]);
            }
        } else if (this.subtab == 1) {
            final int loadW = GovMetrics.span(top.w(), top.w() / 6, 36, 48);
            addButton(top.right() - loadW - 4, top.y() + 1, loadW, top.h() - 2,
                    Click.PERMIT_LOAD, "", this.buttonLoad, !mutationPending(), false, false);
            final GovRect action = this.actions[1];
            if (action.h() > 0) {
                final int gap = 3;
                final int count = 3;
                final int buttonW = Math.max(0, (action.w() - 8 - gap * (count - 1)) / count);
                int x = action.x() + 4;
                addButton(x, action.y(), buttonW, action.h(), Click.PERMIT_ISSUE_OPEN, "",
                        this.buttonIssue, this.canRegister && !mutationPending(), true, false);
                x += buttonW + gap;
                addButton(x, action.y(), buttonW, action.h(), Click.PERMIT_REVOKE_OPEN, "",
                        this.buttonRevoke, this.canRegister && selectedPermit() != null
                                && !mutationPending(), false, false);
                x += buttonW + gap;
                addButton(x, action.y(), buttonW, action.h(), Click.PERMIT_SUSPEND_OPEN, "",
                        this.buttonSuspend,
                        this.canRegister && selectedPermit() != null && !mutationPending(),
                        false, false);
            }
        } else if (this.subtab == 2) {
            final int createW = GovMetrics.span(top.w(), top.w() / 3, 62, 92);
            addButton(top.x() + 4, top.y() + 1, createW, top.h() - 2, Click.AREA_CREATE_OPEN,
                    "", this.buttonCreateArea, this.canRegister && !mutationPending(), true, false);
            final int revokeW = GovMetrics.span(top.w(), top.w() / 4, 56, 76);
            addButton(top.right() - revokeW - 4, top.y() + 1, revokeW, top.h() - 2,
                    Click.AREA_REVOKE_OPEN, "", this.buttonRevoke,
                    this.canRegister && selectedArea() != null && !mutationPending(),
                    false, false);
        } else {
            final int classifyW = GovMetrics.span(top.w(), top.w() / 5, 44, 58);
            final int heldW = GovMetrics.span(top.w(), top.w() / 4, 76, 104);
            addButton(top.right() - classifyW - heldW - 8, top.y() + 1, classifyW,
                    top.h() - 2, Click.CLASSIFY, "", this.buttonClassify, !mutationPending(),
                    true, false);
            addButton(top.right() - heldW - 4, top.y() + 1, heldW, top.h() - 2, Click.HELD, "",
                    this.buttonHeld, !mutationPending(), false, false);
        }
        if (this.subtab != 3) {
            addRetryButtons();
        }
        if (this.bottoms[this.subtab].h() > 0) {
            addPageButtons(this.bottoms[this.subtab]);
        }
    }

    /** Retries belong to the viewport whose request failed, never to a hidden panel. */

    String permitLabel(final String id) {
        final String value = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
        for (int i = 0; i < this.permitClassIds.length; i++) {
            if (this.permitClassIds[i].equals(value)) {
                return this.permitClassLabels[i];
            }
        }
        return value.isBlank() ? "-" : value.toUpperCase(Locale.ROOT);
    }

    Click submitArmAction() {
        return switch (this.modal) {
            case TRANSFER -> Click.TRANSFER_OPEN;
            case REVOKE -> Click.REVOKE_OPEN;
            case PERMIT_REVOKE -> Click.PERMIT_REVOKE_OPEN;
            case AREA_REVOKE -> Click.AREA_REVOKE_OPEN;
            default -> Click.NONE;
        };
    }

    /**
     * Enter submits the open modal once per 250 ms, or the visible main form. A destructive modal
     * is sent only after a click armed it; otherwise the Enter is ignored and the arm label stays.
     */

    String classLabel(final String id) {
        final String value = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "l" -> tr("gui.gov.weapons.class.l");
            case "s" -> tr("gui.gov.weapons.class.s");
            case "r" -> tr("gui.gov.weapons.class.r");
            case "p" -> tr("gui.gov.weapons.class.p");
            case "h" -> tr("gui.gov.weapons.class.h");
            default -> value.isBlank() ? "-" : value.toUpperCase(Locale.ROOT);
        };
    }

    boolean isArmed(final Click action) {
        return action != Click.NONE && this.armedAction == action
                && System.currentTimeMillis() - this.armedAt <= ARM_MS;
    }

    String submitLabel() {
        if (this.modal == Modal.RECORD) {
            return this.buttonClose;
        }
        final Click arm = submitArmAction();
        if (arm != Click.NONE && isArmed(arm)) {
            return this.textConfirmPrefix + " "
                    + this.modalSubmitLabel.toUpperCase(Locale.ROOT);
        }
        return this.modalSubmitLabel;
    }

    void rebuildModalButtons() {
        this.modalButtons.clear();
        this.modalCloseRect = EMPTY;
        if (this.modal == Modal.NONE || this.modalRect.w() <= 0 || this.modalRect.h() <= 0) {
            return;
        }
        // The header band is 20 px tall; an 11x9 close box sits inside it, and the title is
        // clipped to leave this reserve free.
        this.modalCloseRect = rect(this.modalRect.right() - CLOSE_W - 2,
                this.modalRect.y() + 5, CLOSE_W, CLOSE_H);
        this.modalButtons.add(new Button(this.modalCloseRect, Click.CLOSE_MODAL, "", "", true,
                false, false));
        final int buttonH = 14;
        final int y = this.modalRect.bottom() - buttonH - 5;
        final int cancelW = GovMetrics.span(this.modalRect.w(), this.modalRect.w() / 5, 38, 48);
        final int submitW = GovMetrics.span(this.modalRect.w(), this.modalRect.w() / 3, 64, 96);
        final int cancelX = this.modalRect.right() - cancelW - 6;
        final int submitX = cancelX - submitW - 4;
        if (submitX >= this.modalRect.x() + 6) {
            final String label = submitLabel();
            this.modalButtons.add(new Button(
                    rect(submitX, y, submitW, buttonH), Click.SUBMIT, "", label,
                    !mutationPending(), true, isArmed(submitArmAction())));
        }
        this.modalButtons.add(new Button(rect(cancelX, y, cancelW, buttonH), Click.CANCEL,
                "", this.buttonCancel, true, false, false));
        if (this.modal == Modal.REGISTER) {
            this.modalButtons.add(new Button(this.modalClassFrame, Click.CLASS_PICK, "",
                    classLabel(this.registerClass), true, true, true));
        } else if (this.modal == Modal.PERMIT_ISSUE) {
            this.modalButtons.add(new Button(this.modalIssueClassFrame, Click.CLASS_PICK, "",
                    permitLabel(this.permitClass), true, true, true));
        }
        if (this.modal == Modal.RECORD) {
            final int actionW = Math.max(0, (this.modalRect.w() - 24) / 3);
            int x = this.modalRect.x() + 6;
            final int actionY = y - 20;
            if (actionW > 0 && actionY > this.modalRect.y() + 18) {
                this.modalButtons.add(new Button(rect(x, actionY, actionW, buttonH),
                        Click.TRANSFER_OPEN, "", this.buttonTransfer,
                        this.canRegister && !mutationPending(), false, false));
                x += actionW + 4;
                this.modalButtons.add(new Button(rect(x, actionY, actionW, buttonH),
                        Click.REVOKE_OPEN, "", this.buttonRevoke,
                        this.canRegister && !mutationPending(), false, false));
                x += actionW + 4;
                this.modalButtons.add(new Button(rect(x, actionY, actionW, buttonH),
                        Click.LOSS_OPEN, "", this.buttonLoss,
                        this.canRegister && !mutationPending(), false, false));
            }
        }
    }

    void unfocusAll() {
        for (final EditBox box : this.widgets) {
            if (box != null && this.host.getFocused() == box) {
                this.host.setFocused(null);
            }
        }
    }

    // ------------------------------------------------------------------ requests

    void disarm() {
        if (this.armedAction != Click.NONE) {
            this.armedAction = Click.NONE;
            this.armedAt = 0L;
            rebuildModalButtons();
            rebuildButtons();
        }
    }

    void closeModal() {
        this.modal = Modal.NONE;
        this.modalTitle = "";
        this.modalSubmitLabel = "";
        disarm();
        unfocusAll();
        syncWidgets();
        rebuildButtons();
        rebuildModalButtons();
    }

    static long longField(final Map<String, String> fields, final String key) {
        return com.horizonssmp.gov.common.GovFields.longField(fields, key);
    }

    /** Parses a trimmed field, treating blank and overflow as absent rather than zero. */

    static String when(final long epochMillis) {
        return com.horizonssmp.gov.common.GovFields.when(epochMillis);
    }

    String statusText(final String raw) {
        final String value = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        return switch (value) {
            case "ACTIVE", "VALID" -> caps(tr("gui.gov.weapons.status.active"));
            case "SUSPENDED" -> caps(tr("gui.gov.weapons.status.suspended"));
            case "REVOKED" -> caps(tr("gui.gov.weapons.status.revoked"));
            case "EXPIRED" -> caps(tr("gui.gov.weapons.status.expired"));
            case "LOST" -> caps(tr("gui.gov.weapons.status.lost"));
            case "PENDING" -> caps(tr("gui.gov.weapons.status.pending"));
            default -> value.isBlank() ? "-" : value;
        };
    }

    static String dash(final String value) {
        return com.horizonssmp.gov.common.GovFields.dash(value);
    }

    String authorityText(final String rawStatus, final String permit) {
        final String value = rawStatus == null ? "" : rawStatus.trim().toUpperCase(Locale.ROOT);
        if ("REVOKED".equals(value) || "LOST".equals(value) || "EXPIRED".equals(value)) {
            return caps(tr("gui.gov.weapons.status.blocked"));
        }
        if (permit == null || permit.isBlank() || "-".equals(permit)) {
            return caps(tr("gui.gov.weapons.status.no_permit"));
        }
        return caps(tr("gui.gov.weapons.status.clear"));
    }

}
