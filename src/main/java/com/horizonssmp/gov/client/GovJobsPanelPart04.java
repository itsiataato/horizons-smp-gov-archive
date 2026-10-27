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
abstract class GovJobsPanelPart04 extends GovJobsPanelPart03 {
    GovJobsPanelPart04(final net.minecraft.client.gui.screens.Screen host) {
        super(host);
    }

    void rebuildModalChrome() {
        if (this.modal == Modal.NONE || this.modalRect.w() <= 0) {
            this.modalTitleText = "";
            return;
        }
        // Reserve 8 px gap plus the 11 px close box, and the 8 px right padding of the header.
        this.modalTitleText = GovText.clip(this.font, this.modalTitle,
                Math.max(0, this.modalRect.w() - GovMetrics.GAP_S * 2 - CLOSE_W - 8));
    }


    void rebuildRetryRects() {
        this.rosterListRetry = retryRect(this.rosterRows);
        this.rosterDetailRetry = retryRect(this.detailBody);
        this.memberRetry = retryRect(this.memberRows);
        this.capRetry = retryRect(this.capRows);
    }

    /** A retry sits bottom-centre of the viewport that failed, never outside it. */

    void rebuildRosterViews() {
        this.rosterViews.clear();
        this.jobPages = Math.max(1, (this.jobs.size() + JOB_PAGE_SIZE - 1) / JOB_PAGE_SIZE);
        this.jobPage = GovMetrics.clamp(this.jobPage, 0, this.jobPages - 1);
        if (this.rosterList.w() <= 0 || this.rosterRows.w() <= 0) {
            this.rosterEmptyTitle = "";
            this.rosterEmptyDetail = "";
            return;
        }
        final int start = this.jobPage * JOB_PAGE_SIZE;
        final int end = Math.min(this.jobs.size(), start + JOB_PAGE_SIZE);
        final int rowWidth = Math.max(0, this.rosterList.w() - 8);
        for (int index = start; index < end; index++) {
            final JobRow job = this.jobs.get(index);
            final String type = typeLabel(job.type());
            final int typeWidth = Math.min(Math.max(0, rowWidth / 3),
                    this.font.width(type) + 8);
            final int nameWidth = Math.max(0, rowWidth - typeWidth - 6);
            final String name = GovText.clip(this.font, dash(job.name()), nameWidth);
            final String meta = dash(job.id()) + "  /  "
                    + this.labelMembers + " " + job.members()
                    + "  /  " + this.labelCaps + " " + job.caps();
            this.rosterViews.add(new RosterView(job.id(), name,
                    GovText.clip(this.font, meta, nameWidth), type, typeWidth));
        }

        if (this.listState == ListState.LOADING) {
            this.rosterEmptyTitle = this.loadingText;
            this.rosterEmptyDetail = "";
        } else if (this.listState == ListState.ERROR) {
            this.rosterEmptyTitle = this.errorText;
            this.rosterEmptyDetail = GovText.clip(this.font, this.listError,
                    Math.max(0, this.rosterRows.w() - 12));
        } else if (this.jobs.isEmpty()) {
            this.rosterEmptyTitle = this.noJobsText;
            this.rosterEmptyDetail = "";
        } else {
            this.rosterEmptyTitle = "";
            this.rosterEmptyDetail = "";
        }
    }


    void rebuildDetail() {
        this.detailLines.clear();
        this.detailTypeWidth = 0;
        this.detailNameText = "";
        this.detailNameComponent = Component.empty();
        this.detailTypeText = "";
        this.detailErrorText = GovText.clip(this.font, this.detailError,
                Math.max(0, this.detailBody.w() - 12));
        if (this.rosterDetail.w() <= 0 || this.detailBody.w() <= 0) {
            return;
        }
        if (this.selectedJobId.isEmpty() || this.detailState != DetailState.READY) {
            return;
        }

        final int valueX = this.detailBody.x()
                + GovMetrics.clamp(this.detailBody.w() / 3, 54, 110);
        final int valueWidth = Math.max(0, this.detailBody.right() - valueX);
        addDetail(this.labelId, dash(this.selectedJobId), valueX, valueWidth);
        addDetail(this.labelTitle, dash(this.selectedJobName), valueX, valueWidth);
        addDetail(this.labelType, typeLabel(this.selectedJobType), valueX, valueWidth);
        addDetail(this.labelRank, dash(this.selectedRank), valueX, valueWidth);
        addDetail(this.labelDepartment, dash(this.selectedDepartment), valueX, valueWidth);
        addDetail(this.labelMembers, Integer.toString(this.membersTotal), valueX, valueWidth);
        addDetail(this.labelCaps, Integer.toString(this.capsTotal), valueX, valueWidth);
        if (JobKeys.TYPE_BUILTIN.equals(this.selectedJobType)) {
            addText(this.protectedText);
        }

        final String type = typeLabel(this.selectedJobType);
        this.detailTypeWidth = Math.min(Math.max(0, this.detailHeader.w() / 3),
                this.font.width(type) + 8);
        this.detailNameText = GovText.clip(this.font, dash(this.selectedJobName),
                Math.max(0, this.detailHeader.w() - this.detailTypeWidth - 6));
        this.detailNameComponent = GovText.boldCached(this.detailNameText);
        this.detailTypeText = GovText.clip(this.font, type,
                Math.max(0, this.detailTypeWidth - 4));
    }

    void addDetail(final String label, final String value, final int valueX,
            final int valueWidth) {
        final String visibleLabel = GovText.clip(this.font, label,
                Math.max(0, valueX - this.detailBody.x() - 4));
        final String visibleValue = GovText.clip(this.font, dash(value), valueWidth);
        this.detailLines.add(new DetailLine(visibleLabel, visibleValue, null, 12, false, null));
    }


    void addText(final String text) {
        this.detailLines.add(new DetailLine(null,
                GovText.clip(this.font, text, Math.max(0, this.detailBody.w() - 8)),
                null, 11, false, null));
    }


    void rebuildMemberViews() {
        this.memberViews.clear();
        this.memberErrorText = GovText.clip(this.font, this.detailError,
                Math.max(0, this.memberRows.w() - 12));
        if (this.memberList.w() <= 0 || this.memberRows.w() <= 0
                || this.detailState != DetailState.READY) {
            return;
        }
        final boolean canRemove = this.canAssign && !this.selectedJobId.isEmpty();
        final int removeWidth = canRemove ? 52 : 0;
        final int textWidth = Math.max(0, this.memberList.w() - removeWidth - 16);
        final String rank = dash(this.selectedRank);
        // Every row shares the same rank/duty/since block; only the online state varies, so two
        // cached variants replace a translatable lookup and three concatenations per row.
        final String baseMeta = this.labelRank + " " + rank
                + "  /  " + this.labelState + " ";
        final String tailMeta = "  /  " + this.labelDuty + " " + this.notRecordedText
                + "  /  " + this.labelSince + " " + this.notRecordedText;
        final String onlineMeta = baseMeta + this.onlineText + tailMeta;
        final String offlineMeta = baseMeta + this.offlineText + tailMeta;
        for (final MemberRow member : this.members) {
            final String meta = member.online() ? onlineMeta : offlineMeta;
            this.memberViews.add(new MemberView(member.uuid(),
                    GovText.clip(this.font, dash(member.name()), textWidth),
                    GovText.clip(this.font, meta, textWidth), !member.uuid().isBlank()));
        }
        if (this.membersTotal > this.members.size()) {
            this.memberViews.add(new MemberView("", GovText.clip(this.font,
                    tr("gui.gov.jobs.truncated", this.members.size(), this.membersTotal),
                    textWidth), "", false));
        }
    }


    void rebuildCapViews() {
        this.capViews.clear();
        this.capErrorText = GovText.clip(this.font, this.detailError,
                Math.max(0, this.capRows.w() - 12));
        if (this.capList.w() <= 0 || this.capRows.w() <= 0
                || this.detailState != DetailState.READY) {
            return;
        }
        final boolean editable = this.canManage
                && !JobKeys.TYPE_BUILTIN.equals(this.selectedJobType);
        final List<String> granted = new ArrayList<>();
        final List<String> denied = new ArrayList<>();
        final int textWidth = Math.max(0, this.capList.w() - 68);
        final boolean registryKnown = !this.registry.isEmpty();
        final List<String> source = registryKnown ? this.registry : this.jobCaps;
        for (final String capability : source) {
            if (this.jobCaps.contains(capability)) {
                granted.add(capability);
            } else {
                denied.add(capability);
            }
        }
        if (!registryKnown) {
            denied.clear();
        }
        if (!granted.isEmpty()) {
            this.capViews.add(new CapView("", this.grantedText, null, null, false,
                    true, true));
            for (final String capability : granted) {
                this.capViews.add(capView(capability, true, editable, textWidth));
            }
        }
        if (!denied.isEmpty()) {
            this.capViews.add(new CapView("", this.notGrantedText, null, null, false,
                    false, true));
            for (final String capability : denied) {
                this.capViews.add(capView(capability, false, editable, textWidth));
            }
        }
        if (this.capsTotal > this.jobCaps.size()) {
            this.capViews.add(new CapView("", GovText.clip(this.font,
                    tr("gui.gov.jobs.truncated", this.jobCaps.size(), this.capsTotal),
                    textWidth), "", null, false, false, false));
        }
    }

    CapView capView(final String capability, final boolean granted,
            final boolean editable, final int textWidth) {
        final String state = granted ? this.grantedText : this.notGrantedText;
        final boolean builtIn = JobKeys.TYPE_BUILTIN.equals(this.selectedJobType);
        final String line2 = state + (builtIn ? "  /  " + this.protectedUpperText : "");
        return new CapView(capability,
                GovText.clip(this.font, capability, textWidth),
                GovText.clip(this.font, line2, textWidth),
                editable ? (granted ? this.removeAction : this.grantAction) : null,
                editable, granted, false);
    }


}
