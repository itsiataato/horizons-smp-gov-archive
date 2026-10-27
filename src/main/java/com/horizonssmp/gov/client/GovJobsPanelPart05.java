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
abstract class GovJobsPanelPart05 extends GovJobsPanelPart04 {
    GovJobsPanelPart05(final net.minecraft.client.gui.screens.Screen host) {
        super(host);
    }

    void rebuildHeaderTexts() {
        if (this.font == null) {
            return;
        }
        final String rosterHeader = this.status.isEmpty() || this.statusRect.h() > 0
                ? upper(tr("gui.gov.jobs.roster_title")) + "  " + this.jobsTotal
                : this.status;
        this.rosterHeaderText = GovText.clip(this.font, rosterHeader,
                Math.max(0, this.rosterHeader.w() - 4));
        this.pageLabel = GovText.clip(this.font,
                tr("gui.gov.jobs.page_short", this.jobPage + 1, this.jobPages),
                Math.max(0, this.rosterPage.w() - 4));

        final String memberHeader = upper(tr("gui.gov.jobs.members")) + "  "
                + this.membersTotal + "  /  " + this.labelRank + " "
                + dash(this.selectedRank);
        this.memberHeaderText = GovText.clip(this.font, memberHeader,
                Math.max(0, this.memberHeader.w() - 4));

        final String jobName = this.selectedJobName.isEmpty()
                ? this.selectedJobId : this.selectedJobName;
        final String capHeader = upper(tr("gui.gov.jobs.capabilities")) + "  "
                + this.capsTotal + "  /  " + dash(jobName);
        this.capHeaderText = GovText.clip(this.font, capHeader,
                Math.max(0, this.capHeader.w() - 4));

        this.rosterSummaryText = GovText.clip(this.font, rosterSummary(),
                Math.max(0, this.rosterSummary.w() - 4));
    }


    String rosterSummary() {
        if (this.selectedJobId.isEmpty()) {
            return this.noSelectionText;
        }
        final String name = this.selectedJobName.isEmpty()
                ? this.selectedJobId : this.selectedJobName;
        return dash(name) + "  /  " + typeLabel(this.selectedJobType)
                + "  /  " + this.labelMembers + " " + this.membersTotal
                + "  /  " + this.labelCaps + " " + this.capsTotal;
    }


    void rebuildStatusCache() {
        final GovRect area = statusBar();
        final String base = this.status.isEmpty() ? defaultStatus() : this.status;
        final int width = this.modal == Modal.CREATE ? this.modalBody.w()
                : this.statusRect.h() > 0 ? this.statusRect.w() - 4
                : area.w() > 0 ? area.w() - 4 : 0;
        this.statusText = GovText.clip(this.font, base, Math.max(0, width));
    }


    GovRect statusBar() {
        return switch (this.subtab) {
            case MODE_MEMBERS -> this.memberStatusBar;
            case MODE_CAPABILITIES -> this.capStatusBar;
            default -> EMPTY;
        };
    }


    String defaultStatus() {
        return switch (this.subtab) {
            case MODE_MEMBERS -> this.memberHeaderText;
            case MODE_CAPABILITIES -> this.capHeaderText;
            default -> "";
        };
    }


    void clampScrolls() {
        this.rosterScrollMax = Math.max(0,
                this.rosterViews.size() * this.rosterPitch - this.rosterRows.h());
        this.rosterScroll = GovMetrics.clamp(this.rosterScroll, 0, this.rosterScrollMax);
        this.memberScrollMax = Math.max(0,
                this.memberViews.size() * this.memberPitch - this.memberRows.h());
        this.memberScroll = GovMetrics.clamp(this.memberScroll, 0, this.memberScrollMax);
        this.capScrollMax = Math.max(0,
                this.capViews.size() * this.capPitch - this.capRows.h());
        this.capScroll = GovMetrics.clamp(this.capScroll, 0, this.capScrollMax);
        int detailHeight = 0;
        for (final DetailLine line : this.detailLines) {
            detailHeight += line.height();
        }
        this.detailScrollMax = Math.max(0, detailHeight - this.detailBody.h());
        this.detailScroll = GovMetrics.clamp(this.detailScroll, 0, this.detailScrollMax);
        this.pickerScrollMax = Math.max(0,
                this.onlineNames.size() * this.pickerPitch - this.pickerList.h());
        this.pickerScroll = GovMetrics.clamp(this.pickerScroll, 0, this.pickerScrollMax);
    }


    void rebuildMemberRemoveRects() {
        this.memberRemoveRects.clear();
        for (int index = 0; index < this.memberViews.size(); index++) {
            final MemberView view = this.memberViews.get(index);
            if (!this.canAssign || !view.removable()) {
                this.memberRemoveRects.add(EMPTY);
                continue;
            }
            final int top = this.memberRows.y() + index * this.memberPitch - this.memberScroll;
            // An undrawn row must not keep a live button: a partly scrolled row is skipped by the
            // renderer, so its REMOVE control is dropped here rather than clamped onto a neighbour.
            if (top < this.memberRows.y()
                    || top + this.memberPitch > this.memberRows.bottom()) {
                this.memberRemoveRects.add(EMPTY);
                continue;
            }
            this.memberRemoveRects.add(rowButtonRect(this.memberRows,
                    this.memberList.right() - 4 - ROW_BUTTON_W, top));
        }
    }


    void rebuildCapButtonRects() {
        this.capButtonRects.clear();
        for (int index = 0; index < this.capViews.size(); index++) {
            final CapView view = this.capViews.get(index);
            if (view.headingRow() || !view.editable() || view.action() == null) {
                this.capButtonRects.add(EMPTY);
                continue;
            }
            final int top = this.capRows.y() + index * this.capPitch - this.capScroll;
            if (top < this.capRows.y()
                    || top + this.capPitch > this.capRows.bottom()) {
                this.capButtonRects.add(EMPTY);
                continue;
            }
            this.capButtonRects.add(rowButtonRect(this.capRows,
                    this.capList.right() - 4 - ROW_BUTTON_W, top));
        }
    }

    /**
     * A row button exists only when the whole button fits inside the visible row viewport.
     * Clamping it would slide the hit box onto a neighbouring row and send that row's identity,
     * so a partial button is dropped instead of moved.
     */

    void rebuildPickerRects() {
        this.pickerRects.clear();
        this.pickerIndices.clear();
        this.pickerLabels.clear();
        this.pickerConfirmLabels.clear();
        if (this.modal != Modal.PICKER || this.pickerList.w() <= 0
                || this.pickerList.h() <= 0) {
            return;
        }
        final int first = Math.max(0, this.pickerScroll / this.pickerPitch);
        for (int index = first; index < this.onlineNames.size(); index++) {
            final int y = this.pickerList.y() + index * this.pickerPitch - this.pickerScroll;
            if (y >= this.pickerList.bottom()) {
                break;
            }
            if (y + this.pickerPitch <= this.pickerList.y()) {
                continue;
            }
            final String name = this.onlineNames.get(index);
            // Both labels are cached together so render can choose by arm state without building
            // a string or translating every frame.
            this.pickerRects.add(new GovRect(this.pickerList.x(), y,
                    this.pickerList.w(), this.pickerPitch - 1).clamp(this.pickerList));
            this.pickerIndices.add(index);
            this.pickerLabels.add(name);
            this.pickerConfirmLabels.add(name + "  /  " + this.armConfirmText);
        }
    }

    // --------------------------------------------------------------------- hits


}
