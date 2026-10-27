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
abstract class GovJobsPanelPart06 extends GovJobsPanelPart05 {
    GovJobsPanelPart06(final net.minecraft.client.gui.screens.Screen host) {
        super(host);
    }

    void rebuildHits() {
        this.hits.clear();
        if (this.modal != Modal.NONE) {
            this.hits.add(new Hit(this.modalClose, Click.CLOSE_MODAL, ""));
            if (this.modal == Modal.CREATE) {
                this.hits.add(new Hit(this.createField, Click.FOCUS_CREATE, ""));
                this.hits.add(new Hit(this.createSubmit, Click.CREATE_SUBMIT, ""));
                this.hits.add(new Hit(this.createCancel, Click.CREATE_CANCEL, ""));
                return;
            }
            for (int index = 0; index < this.pickerRects.size(); index++) {
                final int onlineIndex = this.pickerIndices.get(index);
                if (onlineIndex >= 0 && onlineIndex < this.onlineNames.size()) {
                    this.hits.add(new Hit(this.pickerRects.get(index), Click.PICKER_PICK,
                            this.onlineNames.get(onlineIndex)));
                }
            }
            this.hits.add(new Hit(this.pickerCancel, Click.PICKER_CANCEL, ""));
            return;
        }
        switch (this.subtab) {
            case MODE_ROSTER -> {
                addRosterHits();
                if (this.listState == ListState.ERROR && this.rosterListRetry.w() > 0) {
                    this.hits.add(new Hit(this.rosterListRetry, Click.RETRY, "list"));
                }
                if (this.detailState == DetailState.ERROR
                        && this.rosterDetailRetry.w() > 0) {
                    this.hits.add(new Hit(this.rosterDetailRetry, Click.RETRY, "detail"));
                }
                if (createEnabled()) {
                    this.hits.add(new Hit(this.rosterNew, Click.CREATE_OPEN, ""));
                }
                this.hits.add(new Hit(this.rosterPrev, Click.PAGE_PREV, ""));
                this.hits.add(new Hit(this.rosterNext, Click.PAGE_NEXT, ""));
                if (deleteEnabled()) {
                    this.hits.add(new Hit(this.rosterDelete, Click.DELETE, ""));
                }
                addRefreshHit();
            }
            case MODE_MEMBERS -> {
                addMemberHits();
                if (this.detailState == DetailState.ERROR && this.memberRetry.w() > 0) {
                    this.hits.add(new Hit(this.memberRetry, Click.RETRY, "detail"));
                }
                if (memberActionsEnabled()) {
                    this.hits.add(new Hit(this.memberAdd, Click.MEMBER_ADD_OPEN, ""));
                }
                addRefreshHit();
            }
            case MODE_CAPABILITIES -> {
                addCapHits();
                if (this.detailState == DetailState.ERROR && this.capRetry.w() > 0) {
                    this.hits.add(new Hit(this.capRetry, Click.RETRY, "detail"));
                }
                addRefreshHit();
            }
            default -> {
            }
        }
    }


    void addRefreshHit() {
        if (!mutationPending()) {
            this.hits.add(new Hit(this.refreshRect, Click.REFRESH, ""));
        }
    }


    void addRosterHits() {
        if (!this.rosterEmptyTitle.isEmpty()) {
            return;
        }
        final int first = Math.max(0, this.rosterScroll / this.rosterPitch);
        for (int index = first; index < this.rosterViews.size(); index++) {
            final int y = this.rosterRows.y() + index * this.rosterPitch - this.rosterScroll;
            if (y >= this.rosterRows.bottom()) {
                break;
            }
            if (y < this.rosterRows.y()
                    || y + this.rosterPitch > this.rosterRows.bottom()) {
                continue;
            }
            final GovRect row = new GovRect(this.rosterList.x() + 2, y,
                    Math.max(0, this.rosterList.w() - 4), this.rosterPitch)
                    .clamp(this.rosterRows);
            this.hits.add(new Hit(row, Click.ROW, this.rosterViews.get(index).id()));
        }
    }


    void addMemberHits() {
        if (this.selectedJobId.isEmpty() || this.detailState != DetailState.READY) {
            return;
        }
        final int first = Math.max(0, this.memberScroll / this.memberPitch);
        for (int index = first; index < this.memberViews.size(); index++) {
            final int y = this.memberRows.y() + index * this.memberPitch - this.memberScroll;
            if (y >= this.memberRows.bottom()) {
                break;
            }
            if (y + this.memberPitch <= this.memberRows.y()
                    || index >= this.memberRemoveRects.size()) {
                continue;
            }
            final GovRect button = this.memberRemoveRects.get(index);
            if (button.w() <= 0 || button.h() <= 0) {
                continue;
            }
            this.hits.add(new Hit(button, Click.MEMBER_REMOVE,
                    this.memberViews.get(index).uuid()));
        }
    }


    void addCapHits() {
        if (this.selectedJobId.isEmpty() || this.detailState != DetailState.READY) {
            return;
        }
        final int first = Math.max(0, this.capScroll / this.capPitch);
        for (int index = first; index < this.capViews.size(); index++) {
            final int y = this.capRows.y() + index * this.capPitch - this.capScroll;
            if (y >= this.capRows.bottom()) {
                break;
            }
            if (y + this.capPitch <= this.capRows.y()
                    || index >= this.capButtonRects.size()) {
                continue;
            }
            final GovRect button = this.capButtonRects.get(index);
            if (button.w() <= 0 || button.h() <= 0) {
                continue;
            }
            final CapView view = this.capViews.get(index);
            if (view.headingRow() || !view.editable() || view.action() == null) {
                continue;
            }
            this.hits.add(new Hit(button,
                    view.granted() ? Click.CAP_REMOVE : Click.CAP_GRANT, view.id()));
        }
    }

    // ------------------------------------------------------------------- render


    void render(final GuiGraphics graphics, final int mouseX, final int mouseY) {
        if (!this.active || this.font == null) {
            return;
        }
        switch (this.subtab) {
            case MODE_ROSTER -> drawRoster(graphics, mouseX, mouseY);
            case MODE_MEMBERS -> drawMembers(graphics, mouseX, mouseY);
            case MODE_CAPABILITIES -> drawCapabilities(graphics, mouseX, mouseY);
            default -> {
            }
        }
        drawBottomBar(graphics, mouseX, mouseY);
        if (this.modal != Modal.NONE) {
            drawModal(graphics, mouseX, mouseY);
        }
    }

    /**
     * Starts a row scissor. The one-pixel inset keeps rows from painting over the list frame;
     * the return value lets the caller balance the stack in a finally block.
     */

}
