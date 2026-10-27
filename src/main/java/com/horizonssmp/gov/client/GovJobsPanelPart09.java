package com.horizonssmp.gov.client;

import com.horizonssmp.gov.client.GovDashboardScreen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.horizonssmp.gov.common.GovFields;
import com.horizonssmp.gov.common.JobKeys;
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

import static com.horizonssmp.gov.client.GovJobsTypes.*;
import static com.horizonssmp.gov.client.GovJobsConstants.*;
abstract class GovJobsPanelPart09 extends GovJobsPanelPart08 {
    GovJobsPanelPart09(final net.minecraft.client.gui.screens.Screen host) {
        super(host);
    }

    void drawModal(final GuiGraphics graphics, final int mouseX, final int mouseY) {
        if (this.modalRect.w() <= 0 || this.modalRect.h() <= 0) {
            return;
        }
        GovWidgets.modalBackdrop(graphics, this.rect);
        GovWidgets.modalFrame(graphics, this.font, this.modalRect, this.modalTitleText);
        if (this.modalClose.w() > 0) {
            final boolean closeHover = this.modalClose.contains(mouseX, mouseY);
            if (closeHover) {
                graphics.fill(this.modalClose.x(), this.modalClose.y(),
                        this.modalClose.right(), this.modalClose.bottom(),
                        GovTheme.SELECTED);
            }
            GovTheme.border(graphics, this.modalClose, GovTheme.BORDER);
            GovText.centered(graphics, this.font, this.closeLetter,
                    this.modalClose.x() + this.modalClose.w() / 2, this.modalClose.y(),
                    GovTheme.TEXT);
        }
        if (this.modal == Modal.CREATE) {
            graphics.drawString(this.font, this.createLabelText, this.createLabel.x(),
                    this.createLabel.y() + 3, GovTheme.MUTED, false);
            GovWidgets.fieldFrame(graphics, this.createField,
                    this.createBox != null && this.createBox.isFocused());
            if (this.statusIsError && !this.status.isEmpty()) {
                graphics.drawString(this.font, this.statusText, this.modalBody.x(),
                        this.createField.bottom() + 4, GovTheme.DANGER, false);
            }
            GovWidgets.button(graphics, this.font, this.createSubmit, this.createAction,
                    !mutationPending(), this.createSubmit.contains(mouseX, mouseY), true);
            GovWidgets.button(graphics, this.font, this.createCancel, this.cancelAction,
                    true, this.createCancel.contains(mouseX, mouseY), false);
        } else if (this.modal == Modal.PICKER) {
            if (this.onlineNames.isEmpty()) {
                GovWidgets.emptyState(graphics, this.font, this.pickerList,
                        this.noPlayersText, "");
            } else {
                final boolean scissored = this.pickerList.w() > 0
                        && this.pickerList.h() > 0;
                if (scissored) {
                    graphics.enableScissor(this.pickerList.x(), this.pickerList.y(),
                            this.pickerList.right(), this.pickerList.bottom());
                }
                try {
                    for (int index = 0; index < this.pickerRects.size(); index++) {
                        final int onlineIndex = this.pickerIndices.get(index);
                        if (onlineIndex < 0 || onlineIndex >= this.onlineNames.size()) {
                            continue;
                        }
                        final GovRect row = this.pickerRects.get(index);
                        final boolean armed = isAssignArmed(this.onlineNames.get(onlineIndex));
                        final String label = armed
                                ? this.pickerConfirmLabels.get(index)
                                : this.pickerLabels.get(index);
                        GovWidgets.button(graphics, this.font, row,
                                label, !mutationPending(),
                                row.contains(mouseX, mouseY), armed);
                    }
                } finally {
                    if (scissored) {
                        graphics.disableScissor();
                    }
                }
            }
            GovWidgets.button(graphics, this.font, this.pickerCancel, this.cancelAction,
                    true, this.pickerCancel.contains(mouseX, mouseY), false);
        }
    }

    // --------------------------------------------------------------------- input


    boolean mouseClicked(final double mouseX, final double mouseY, final int button) {
        if (!this.active || button != 0) {
            return false;
        }
        // Any click that misses the field leaves it. The shell has already routed the click to the
        // vanilla widget, so this only clears a miss and never disturbs caret placement inside it.
        if (this.createBox != null && this.createBox.isVisible()
                && this.host.getFocused() == this.createBox
                && !this.createBox.isMouseOver(mouseX, mouseY)) {
            this.host.setFocused(null);
        }
        if (this.modal != Modal.NONE) {
            for (final Hit hit : this.hits) {
                if (hit.rect().contains(mouseX, mouseY)) {
                    handleHit(hit);
                    return true;
                }
            }
            if (System.currentTimeMillis() - this.modalOpenedAt >= MODAL_GUARD_MS
                    && !this.modalRect.contains(mouseX, mouseY)) {
                closeModal();
            }
            return true;
        }
        for (final Hit hit : this.hits) {
            if (hit.rect().contains(mouseX, mouseY)) {
                handleHit(hit);
                return true;
            }
        }
        return false;
    }


    void handleHit(final Hit hit) {
        switch (hit.click()) {
            case ROW -> selectJob(hit.value());
            case PAGE_PREV -> changePage(-1);
            case PAGE_NEXT -> changePage(1);
            case REFRESH -> {
                resetArmStatus();
                requestList();
                if (!this.selectedJobId.isEmpty()) {
                    requestInfo(this.selectedJobId);
                }
            }
            case RETRY -> {
                resetArmStatus();
                if ("list".equals(hit.value())) {
                    requestList();
                } else if (!this.selectedJobId.isEmpty()) {
                    requestInfo(this.selectedJobId);
                }
            }
            case CREATE_OPEN -> openCreateModal();
            case CREATE_SUBMIT -> createJob();
            case CREATE_CANCEL -> closeModal();
            case FOCUS_CREATE -> {
                if (this.createBox != null && this.createBox.visible) {
                    this.host.setFocused(this.createBox);
                }
            }
            case DELETE -> deleteJob();
            case MEMBER_ADD_OPEN -> openPicker();
            case MEMBER_REMOVE -> removeMember(hit.value());
            case PICKER_PICK -> assignTo(hit.value());
            case PICKER_CANCEL -> closeModal();
            case CAP_REMOVE -> removeCapability(hit.value());
            case CAP_GRANT -> grantCapability(hit.value());
            case CLOSE_MODAL -> closeModal();
            default -> {
            }
        }
    }


    boolean mouseScrolled(final double mouseX, final double mouseY, final double scrollY) {
        if (!this.active) {
            return false;
        }
        if (this.modal == Modal.PICKER) {
            this.pickerScroll = GovMetrics.clamp(
                    this.pickerScroll - (int) (scrollY * 14), 0, this.pickerScrollMax);
            rebuildPickerRects();
            rebuildHits();
            return true;
        }
        if (this.modal == Modal.CREATE) {
            return true;
        }
        final int step = (int) (scrollY * 18);
        if (this.subtab == MODE_ROSTER && this.rosterRows.contains(mouseX, mouseY)) {
            this.rosterScroll = GovMetrics.clamp(this.rosterScroll - step,
                    0, this.rosterScrollMax);
            rebuildHits();
            return true;
        }
        if (this.subtab == MODE_ROSTER && this.detailBody.contains(mouseX, mouseY)) {
            this.detailScroll = GovMetrics.clamp(this.detailScroll - step,
                    0, this.detailScrollMax);
            rebuildHits();
            return true;
        }
        if (this.subtab == MODE_MEMBERS && this.memberRows.contains(mouseX, mouseY)) {
            this.memberScroll = GovMetrics.clamp(this.memberScroll - step,
                    0, this.memberScrollMax);
            rebuildMemberRemoveRects();
            rebuildHits();
            return true;
        }
        if (this.subtab == MODE_CAPABILITIES && this.capRows.contains(mouseX, mouseY)) {
            this.capScroll = GovMetrics.clamp(this.capScroll - step,
                    0, this.capScrollMax);
            rebuildCapButtonRects();
            rebuildHits();
            return true;
        }
        return false;
    }


}
