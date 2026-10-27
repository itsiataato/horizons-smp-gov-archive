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

abstract class GovWeaponsPanelPart11 extends GovWeaponsPanelPart10 {
    GovWeaponsPanelPart11(final net.minecraft.client.gui.screens.Screen host) {
        super(host);
    }


    void render(final GuiGraphics graphics, final int mouseX, final int mouseY) {
        if (!this.active || this.font == null) {
            return;
        }
        drawTopBar(graphics);
        if (this.subtab == 3) {
            drawClassifier(graphics, mouseX, mouseY);
        } else {
            drawActions(graphics);
            drawList(graphics, mouseX, mouseY);
            drawDetailOrSummary(graphics, mouseX, mouseY);
            drawBottomBar(graphics);
        }
        drawButtons(graphics, mouseX, mouseY);
        drawStatus(graphics);
        if (this.modal != Modal.NONE) {
            drawModal(graphics, mouseX, mouseY);
        }
    }

    static boolean inFrame(final EditBox box, final EditBox owner, final GovRect frame,
            final double mouseX, final double mouseY) {
        return box == owner && frame.w() > 0 && frame.h() > 0
                && frame.contains(mouseX, mouseY);
    }

    boolean fieldContains(final EditBox box, final double mouseX, final double mouseY) {
        if (box == null || !box.visible) {
            return false;
        }
        if (this.modal == Modal.NONE) {
            return (this.subtab == 1 && inFrame(box, this.permitTargetBox,
                        this.permitFilterFrame, mouseX, mouseY))
                    || (this.subtab == 3 && inFrame(box, this.classifyBox, this.classifyFrame,
                        mouseX, mouseY));
        }
        return switch (this.modal) {
            case REGISTER -> inFrame(box, this.registerSerialBox, this.modalSerialFrame,
                    mouseX, mouseY)
                    || inFrame(box, this.registerTypeBox, this.modalTypeFrame, mouseX, mouseY)
                    || inFrame(box, this.ownerBox, this.modalOwnerFrame, mouseX, mouseY);
            case TRANSFER -> inFrame(box, this.transferOwnerBox, this.modalTransferOwnerFrame,
                    mouseX, mouseY);
            case REVOKE, LOSS, PERMIT_REVOKE, AREA_REVOKE ->
                    inFrame(box, this.reasonBox, this.modalReasonFrame, mouseX, mouseY);
            case PERMIT_ISSUE -> inFrame(box, this.issueTargetBox, this.modalTargetFrame,
                    mouseX, mouseY)
                    || inFrame(box, this.reasonBox, this.modalReasonFrame, mouseX, mouseY);
            case PERMIT_SUSPEND -> inFrame(box, this.minutesBox, this.modalMinutesFrame,
                    mouseX, mouseY)
                    || inFrame(box, this.reasonBox, this.modalReasonFrame, mouseX, mouseY);
            case AREA_CREATE -> inFrame(box, this.dimBox, this.modalDimFrame, mouseX, mouseY)
                    || inFrame(box, this.xBox, this.modalXFrame, mouseX, mouseY)
                    || inFrame(box, this.yBox, this.modalYFrame, mouseX, mouseY)
                    || inFrame(box, this.zBox, this.modalZFrame, mouseX, mouseY)
                    || inFrame(box, this.radiusBox, this.modalRadiusFrame, mouseX, mouseY)
                    || inFrame(box, this.areaMinutesBox, this.modalAreaMinutesFrame, mouseX,
                        mouseY)
                    || inFrame(box, this.areaReasonBox, this.modalAreaReasonFrame, mouseX,
                        mouseY);
            default -> false;
        };
    }

    void focus(final EditBox box) {
        if (box != null && box.visible) {
            this.host.setFocused(box);
        }
    }

    void focusModalField(final double mouseX, final double mouseY) {
        if (this.registerSerialBox != null && this.registerSerialBox.visible
                && this.modalSerialFrame.contains(mouseX, mouseY)) {
            focus(this.registerSerialBox);
        } else if (this.registerTypeBox != null && this.registerTypeBox.visible
                && this.modalTypeFrame.contains(mouseX, mouseY)) {
            focus(this.registerTypeBox);
        } else if (this.ownerBox != null && this.ownerBox.visible
                && this.modalOwnerFrame.contains(mouseX, mouseY)) {
            focus(this.ownerBox);
        } else if (this.transferOwnerBox != null && this.transferOwnerBox.visible
                && this.modalTransferOwnerFrame.contains(mouseX, mouseY)) {
            focus(this.transferOwnerBox);
        } else if (this.issueTargetBox != null && this.issueTargetBox.visible
                && this.modalTargetFrame.contains(mouseX, mouseY)) {
            focus(this.issueTargetBox);
        } else if (this.reasonBox != null && this.reasonBox.visible
                && this.modalReasonFrame.contains(mouseX, mouseY)) {
            focus(this.reasonBox);
        } else if (this.minutesBox != null && this.minutesBox.visible
                && this.modalMinutesFrame.contains(mouseX, mouseY)) {
            focus(this.minutesBox);
        } else if (this.areaMinutesBox != null && this.areaMinutesBox.visible
                && this.modalAreaMinutesFrame.contains(mouseX, mouseY)) {
            focus(this.areaMinutesBox);
        } else if (this.dimBox != null && this.dimBox.visible
                && this.modalDimFrame.contains(mouseX, mouseY)) {
            focus(this.dimBox);
        } else if (this.xBox != null && this.xBox.visible
                && this.modalXFrame.contains(mouseX, mouseY)) {
            focus(this.xBox);
        } else if (this.yBox != null && this.yBox.visible
                && this.modalYFrame.contains(mouseX, mouseY)) {
            focus(this.yBox);
        } else if (this.zBox != null && this.zBox.visible
                && this.modalZFrame.contains(mouseX, mouseY)) {
            focus(this.zBox);
        } else if (this.radiusBox != null && this.radiusBox.visible
                && this.modalRadiusFrame.contains(mouseX, mouseY)) {
            focus(this.radiusBox);
        } else if (this.areaReasonBox != null && this.areaReasonBox.visible
                && this.modalAreaReasonFrame.contains(mouseX, mouseY)) {
            focus(this.areaReasonBox);
        }
    }

    void cycleFilter() {
        int index = 0;
        for (int i = 0; i < this.filterIds.length; i++) {
            if (this.filterIds[i].equals(this.filter)) {
                index = i;
                break;
            }
        }
        setFilter(this.filterIds[(index + 1) % this.filterIds.length]);
    }

    void cycleClass() {
        final boolean permit = this.modal == Modal.PERMIT_ISSUE;
        final String[] ids = permit ? this.permitClassIds : this.registerClassIds;
        final String current = permit ? this.permitClass : this.registerClass;
        int index = 0;
        for (int i = 0; i < ids.length; i++) {
            if (ids[i].equals(current)) {
                index = i;
                break;
            }
        }
        final String next = ids[(index + 1) % ids.length];
        if (permit) {
            this.permitClass = next;
        } else {
            this.registerClass = next;
        }
    }

    void changePage(final int delta) {
        final int next = GovMetrics.clamp(currentPage() + delta, 0, currentPages() - 1);
        if (next == currentPage()) {
            return;
        }
        if (this.subtab == 0) {
            this.weaponPage = next;
            requestWeaponList();
        } else if (this.subtab == 1) {
            this.permitPage = next;
            requestPermitList();
        } else if (this.subtab == 2) {
            this.areaPage = next;
            requestAreaList();
        }
        updateListTexts(this.subtab);
        rebuildButtons();
    }

    void handleClick(final Click click, final String value) {
        if (click == null) {
            return;
        }
        switch (click) {
            case FILTER -> cycleFilter();
            case ROW -> {
                if (this.subtab == 0) {
                    selectWeapon(value);
                } else if (this.subtab == 1) {
                    selectPermit(value);
                } else if (this.subtab == 2) {
                    selectArea(value);
                }
            }
            case PREV -> changePage(-1);
            case NEXT -> changePage(1);
            case REFRESH -> refreshSection();
            case REGISTER_OPEN -> openModal(Modal.REGISTER);
            case RECORD_OPEN -> openModal(Modal.RECORD);
            case TRANSFER_OPEN -> openModal(Modal.TRANSFER);
            case REVOKE_OPEN -> openModal(Modal.REVOKE);
            case LOSS_OPEN -> openModal(Modal.LOSS);
            case PERMIT_LOAD -> {
                this.permitTarget = value(this.permitTargetBox).strip();
                this.permitPage = 0;
                requestPermitList();
            }
            case PERMIT_ISSUE_OPEN -> openModal(Modal.PERMIT_ISSUE);
            case PERMIT_REVOKE_OPEN -> openModal(Modal.PERMIT_REVOKE);
            case PERMIT_SUSPEND_OPEN -> openModal(Modal.PERMIT_SUSPEND);
            case AREA_CREATE_OPEN -> openModal(Modal.AREA_CREATE);
            case AREA_REVOKE_OPEN -> openModal(Modal.AREA_REVOKE);
            case CLASSIFY -> requestClassify(false);
            case HELD -> requestClassify(true);
            case CLASS_PICK -> {
                cycleClass();
                rebuildModalButtons();
            }
            case SUBMIT -> submitModal();
            case CANCEL -> {
                this.status = "";
                this.statusIsError = false;
                closeModal();
            }
            case CLOSE_MODAL -> {
                this.status = "";
                this.statusIsError = false;
                closeModal();
            }
            case RETRY -> {
                if ("detail".equals(value) && this.subtab == 0
                        && !this.selectedSerial.isBlank()) {
                    requestWeaponDetail(this.selectedSerial);
                    rebuildDetailLines();
                } else {
                    refreshSection();
                }
                updateListTexts(this.subtab);
                rebuildButtons();
            }
            default -> {
            }
        }
    }

    void focusMainField(final double mouseX, final double mouseY) {
        if (this.subtab == 1 && this.permitFilterFrame.contains(mouseX, mouseY)
                && this.permitTargetBox != null && this.permitTargetBox.visible) {
            this.host.setFocused(this.permitTargetBox);
        }
        if (this.subtab == 3 && this.classifyFrame.contains(mouseX, mouseY)
                && this.classifyBox != null && this.classifyBox.visible) {
            this.host.setFocused(this.classifyBox);
        }
    }

}
