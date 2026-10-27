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
abstract class GovJobsPanelPart01 extends GovJobsPanelAbstracts {
    GovJobsPanelPart01(final net.minecraft.client.gui.screens.Screen host) {
        super(host);
    }

    EditBox createNameBox(final Font font) {
        this.font = font;
        if (this.createBox == null) {
            // GovWidgets.field owns the neutral selection, shadowless text and non-blue hint.
            this.createBox = GovWidgets.field(font, 0, 0, 10, 9,
                    Component.translatable("gui.gov.jobs.new_name"));
            this.createBox.setMaxLength(64);
            this.createBox.setVisible(false);
            this.widgetList.add(this.createBox);
        }
        return this.createBox;
    }

    /** The required widget factory; it returns the same cached list on every call. */

    List<EditBox> createWidgets(final Font font) {
        createNameBox(font);
        return this.widgetList;
    }

    // ----------------------------------------------------------------- lifecycle


    void setActive(final boolean active) {
        this.active = active;
        if (!active) {
            this.modal = Modal.NONE;
            disarmAll();
            this.enterRepeat.reset();
            clearFocus();
        } else {
            requestListIfNeeded();
        }
        syncCreateBox();
        rebuildCaches();
    }


    void reset() {
        this.pending.clear();
        this.latestListReqId = "";
        this.latestInfoReqId = "";
        this.status = "";
        this.statusIsError = false;
        this.modal = Modal.NONE;
        disarmAll();
        if (this.createBox != null) {
            this.createBox.setValue("");
        }
        syncCreateBox();
        rebuildCaches();
    }

    /**
     * Selects a sub-category. Mapping: 0 Roster, 1 Members, 2 Capabilities.
     */

    void setSubtab(final int index) {
        final int next = GovMetrics.clamp(index, 0, MODE_CAPABILITIES);
        if (next == this.subtab && this.modal == Modal.NONE) {
            return;
        }
        this.subtab = next;
        this.modal = Modal.NONE;
        // Arm prompts belong to the previous mode's row; a tab change cancels both.
        if (this.status.contains(this.armConfirmText)) {
            this.status = "";
            this.statusIsError = false;
        }
        disarmAll();
        clearFocus();
        syncCreateBox();
        rebuildCaches();
    }

    // -------------------------------------------------------------------- layout

    /** Computes every rectangle once, for all three modes and both modals. */
    void layout(final int left, final int top, final int right, final int bottom,
            final Font font) {
        this.font = font;
        final int width = Math.max(0, right - left);
        final int height = Math.max(0, bottom - top);
        this.rect = new GovRect(left, top, width, height);

        final int pad = width < 360 ? 4 : width < 600 ? 6 : 8;
        final GovRect inner = this.rect.inset(pad);
        final int barHeight = GovMetrics.BUTTON_H;
        final int statusHeight = height >= 150 ? 10 : 0;
        final int gap = 4;
        final int bodyHeight = Math.max(0,
                inner.h() - barHeight - (statusHeight > 0 ? statusHeight + gap : 0));
        this.body = new GovRect(inner.x(), inner.y(), inner.w(), bodyHeight);
        this.bar = new GovRect(inner.x(), inner.bottom() - barHeight, inner.w(), barHeight);
        this.statusRect = statusHeight > 0
                ? new GovRect(inner.x(), this.body.bottom() + gap, inner.w(), statusHeight)
                : EMPTY;

        final int refreshW = GovMetrics.clamp(this.bar.w() / 5, 52, 64);
        this.refreshRect = new GovRect(this.bar.right() - refreshW, this.bar.y(),
                refreshW, this.bar.h()).clamp(this.bar);

        layoutRoster();
        layoutMembers();
        layoutCapabilities();
        layoutBar();
        layoutModals();

        positionBox(this.createBox, this.createField);
        rebuildCaches();
    }


    void layoutRoster() {
        final boolean compact = this.body.w() < 360 || this.body.h() < 120;
        if (compact) {
            final int summaryHeight = 10;
            final int listHeight = Math.max(0, this.body.h() - summaryHeight - 4);
            this.rosterList = new GovRect(this.body.x(), this.body.y(),
                    this.body.w(), listHeight).clamp(this.body);
            this.rosterSummary = new GovRect(this.body.x(), this.rosterList.bottom() + 4,
                    this.body.w(), summaryHeight).clamp(this.body);
            this.rosterDetail = EMPTY;
        } else {
            int listWidth = GovMetrics.clamp(this.body.w() * 42 / 100, 120, 300);
            listWidth = Math.min(listWidth, Math.max(0, this.body.w() - 120));
            this.rosterList = new GovRect(this.body.x(), this.body.y(),
                    listWidth, this.body.h()).clamp(this.body);
            this.rosterDetail = new GovRect(this.rosterList.right() + 4, this.body.y(),
                    Math.max(0, this.body.w() - listWidth - 4), this.body.h()).clamp(this.body);
            this.rosterSummary = EMPTY;
        }

        this.rosterHeader = new GovRect(this.rosterList.x() + 2, this.rosterList.y() + 1,
                Math.max(0, this.rosterList.w() - 4), 10).clamp(this.rosterList);
        final int rowsTop = this.rosterHeader.bottom() + 1;
        this.rosterRows = new GovRect(this.rosterList.x() + 1, rowsTop,
                Math.max(0, this.rosterList.w() - 2),
                Math.max(0, this.rosterList.bottom() - 1 - rowsTop)).clamp(this.rosterList);
        this.rosterPitch = 18;
        this.rosterScrollTrack = new GovRect(this.rosterList.right() - 4,
                this.rosterRows.y(), 3, this.rosterRows.h()).clamp(this.rosterList);

        if (this.rosterDetail.w() > 0 && this.rosterDetail.h() > 0) {
            this.detailHeader = new GovRect(this.rosterDetail.x() + 6,
                    this.rosterDetail.y() + 4,
                    Math.max(0, this.rosterDetail.w() - 12), 12).clamp(this.rosterDetail);
            this.detailBody = new GovRect(this.rosterDetail.x() + 6,
                    this.detailHeader.bottom() + 4,
                    Math.max(0, this.rosterDetail.w() - 12),
                    Math.max(0, this.rosterDetail.bottom() - 4
                            - (this.detailHeader.bottom() + 4))).clamp(this.rosterDetail);
            this.detailScrollTrack = new GovRect(this.rosterDetail.right() - 4,
                    this.detailBody.y(), 3, this.detailBody.h()).clamp(this.rosterDetail);
        } else {
            this.detailHeader = EMPTY;
            this.detailBody = EMPTY;
            this.detailScrollTrack = EMPTY;
        }
    }


    void layoutMembers() {
        this.memberList = new GovRect(this.body.x(), this.body.y(), this.body.w(),
                this.body.h()).clamp(this.body);
        this.memberHeader = new GovRect(this.memberList.x() + 2, this.memberList.y() + 1,
                Math.max(0, this.memberList.w() - 4), 10).clamp(this.memberList);
        final int rowsTop = this.memberHeader.bottom() + 1;
        this.memberRows = new GovRect(this.memberList.x() + 1, rowsTop,
                Math.max(0, this.memberList.w() - 2),
                Math.max(0, this.memberList.bottom() - 1 - rowsTop)).clamp(this.memberList);
        this.memberPitch = 18;
        this.memberScrollTrack = new GovRect(this.memberList.right() - 4,
                this.memberRows.y(), 3, this.memberRows.h()).clamp(this.memberList);
    }


    void layoutCapabilities() {
        this.capList = new GovRect(this.body.x(), this.body.y(), this.body.w(),
                this.body.h()).clamp(this.body);
        this.capHeader = new GovRect(this.capList.x() + 2, this.capList.y() + 1,
                Math.max(0, this.capList.w() - 4), 10).clamp(this.capList);
        final int rowsTop = this.capHeader.bottom() + 1;
        this.capRows = new GovRect(this.capList.x() + 1, rowsTop,
                Math.max(0, this.capList.w() - 2),
                Math.max(0, this.capList.bottom() - 1 - rowsTop)).clamp(this.capList);
        this.capPitch = 18;
        this.capScrollTrack = new GovRect(this.capList.right() - 4,
                this.capRows.y(), 3, this.capRows.h()).clamp(this.capList);
    }


    void layoutBar() {
        this.rosterNew = new GovRect(this.bar.x(), this.bar.y(),
                GovMetrics.clamp(this.bar.w() / 8, 28, 44), this.bar.h()).clamp(this.bar);
        this.rosterPrev = new GovRect(this.rosterNew.right() + 3, this.bar.y(), 20,
                this.bar.h()).clamp(this.bar);
        this.rosterNext = new GovRect(this.rosterPrev.right() + 3, this.bar.y(), 20,
                this.bar.h()).clamp(this.bar);
        this.rosterPage = new GovRect(this.rosterNext.right() + 3, this.bar.y(), 34,
                this.bar.h()).clamp(this.bar);
        final int deleteWidth = GovMetrics.clamp(this.bar.w() / 5, 52, 60);
        this.rosterDelete = new GovRect(this.refreshRect.x() - 3 - deleteWidth, this.bar.y(),
                deleteWidth, this.bar.h()).clamp(this.bar);

        this.memberAdd = new GovRect(this.bar.x(), this.bar.y(),
                GovMetrics.clamp(this.bar.w() / 4, 44, 80), this.bar.h()).clamp(this.bar);
        this.memberStatusBar = new GovRect(this.memberAdd.right() + 4, this.bar.y(),
                Math.max(0, this.refreshRect.x() - 4 - (this.memberAdd.right() + 4)),
                this.bar.h()).clamp(this.bar);
        this.capStatusBar = new GovRect(this.bar.x(), this.bar.y(),
                Math.max(0, this.refreshRect.x() - 4 - this.bar.x()), this.bar.h())
                .clamp(this.bar);
    }


}
