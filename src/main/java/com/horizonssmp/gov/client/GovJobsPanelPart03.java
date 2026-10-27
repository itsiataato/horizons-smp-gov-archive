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
abstract class GovJobsPanelPart03 extends GovJobsPanelPart02 {
    GovJobsPanelPart03(final net.minecraft.client.gui.screens.Screen host) {
        super(host);
    }

    void applyList(final String delta) {
        final Map<String, String> fields = GovFields.decode(delta);
        this.canManage = "true".equals(fields.get(JobKeys.CAN_MANAGE));
        this.canAssign = "true".equals(fields.get(JobKeys.CAN_ASSIGN));
        ((GovDashboardScreen) this.host).setSubtabVisibility(GovDashboardScreen.Tab.JOBS,
                new boolean[] {true, this.canManage, this.canManage});
        this.jobsTotal = intField(fields, JobKeys.JOB_COUNT);
        this.jobs.clear();
        for (int index = 0; index < Math.min(this.jobsTotal, JobKeys.MAX_LISTED_JOBS); index++) {
            final String prefix = JobKeys.JOB_ROW_PREFIX + index + ".";
            final String id = fields.get(prefix + JobKeys.ROW_ID);
            if (id == null || id.isBlank()) {
                continue;
            }
            this.jobs.add(new JobRow(
                    id,
                    fields.getOrDefault(prefix + JobKeys.ROW_NAME, id),
                    fields.getOrDefault(prefix + JobKeys.ROW_TYPE, JobKeys.TYPE_CUSTOM),
                    intField(fields, prefix + JobKeys.ROW_MEMBERS),
                    intField(fields, prefix + JobKeys.ROW_CAPS)));
        }
        this.onlineTotal = intField(fields, JobKeys.ONLINE_COUNT);
        this.onlineNames.clear();
        for (int index = 0; index < Math.min(this.onlineTotal, JobKeys.MAX_LISTED_ONLINE);
                index++) {
            final String name = fields.get(JobKeys.ONLINE_PREFIX + index);
            if (name != null && !name.isBlank()) {
                this.onlineNames.add(name);
            }
        }
        this.listState = ListState.READY;
        this.listError = "";
        this.jobPages = Math.max(1, (this.jobs.size() + JOB_PAGE_SIZE - 1) / JOB_PAGE_SIZE);

        boolean stillThere = false;
        int selectedIndex = -1;
        for (int index = 0; index < this.jobs.size(); index++) {
            if (this.jobs.get(index).id().equals(this.selectedJobId)) {
                stillThere = true;
                selectedIndex = index;
                break;
            }
        }
        if (!this.selectedJobId.isEmpty() && !stillThere) {
            this.selectedJobId = "";
            this.latestInfoReqId = "";
            this.selectedJobName = "";
            this.selectedJobType = "";
            this.selectedRank = "";
            this.selectedDepartment = "";
            this.detailState = DetailState.NONE;
            if (anyArmed()) {
                this.status = "";
                this.statusIsError = false;
            }
            disarmAll();
        }
        if (stillThere) {
            this.jobPage = GovMetrics.clamp(selectedIndex / JOB_PAGE_SIZE, 0,
                    this.jobPages - 1);
        }
        this.jobPage = GovMetrics.clamp(this.jobPage, 0, this.jobPages - 1);
        if (this.jobs.isEmpty()) {
            this.latestInfoReqId = "";
            this.selectedJobId = "";
            this.selectedJobName = "";
            this.selectedJobType = "";
            this.selectedRank = "";
            this.selectedDepartment = "";
            this.detailState = DetailState.NONE;
        } else if (this.selectedJobId.isEmpty()) {
            this.jobPage = 0;
            selectJob(this.jobs.get(0).id());
        } else if (stillThere && this.detailState != DetailState.LOADING) {
            requestInfo(this.selectedJobId);
        }
        syncCreateBox();
        rebuildCaches();
    }


    void applyInfo(final String delta) {
        final Map<String, String> fields = GovFields.decode(delta);
        final String id = fields.get(JobKeys.ID);
        if (id == null || id.isBlank()) {
            this.detailState = DetailState.ERROR;
            this.detailError = tr("gui.gov.jobs.error");
            rebuildCaches();
            return;
        }
        this.selectedJobId = id;
        this.selectedJobName = fields.getOrDefault(JobKeys.NAME, id);
        this.selectedJobType = fields.getOrDefault(JobKeys.TYPE, JobKeys.TYPE_CUSTOM);
        this.selectedRank = fields.getOrDefault(JobKeys.RANK, "");
        this.selectedDepartment = fields.getOrDefault(JobKeys.DEPARTMENT, "");

        this.jobCaps.clear();
        this.capsTotal = intField(fields, JobKeys.CAP_COUNT);
        for (int index = 0; index < Math.min(this.capsTotal, JobKeys.MAX_LISTED_CAPS);
                index++) {
            final String capability = fields.get(JobKeys.CAP_PREFIX + index);
            if (capability != null && !capability.isBlank()) {
                this.jobCaps.add(capability);
            }
        }
        this.registry.clear();
        this.registryTotal = intField(fields, JobKeys.REGISTRY_COUNT);
        for (int index = 0; index < Math.min(this.registryTotal, JobKeys.MAX_LISTED_CAPS);
                index++) {
            final String capability = fields.get(JobKeys.REGISTRY_PREFIX + index);
            if (capability != null && !capability.isBlank()) {
                this.registry.add(capability);
            }
        }
        this.members.clear();
        this.membersTotal = intField(fields, JobKeys.MEMBER_COUNT);
        for (int index = 0; index < Math.min(this.membersTotal, JobKeys.MAX_LISTED_MEMBERS);
                index++) {
            final String prefix = JobKeys.MEMBER_PREFIX + index + ".";
            final String name = fields.get(prefix + JobKeys.MEMBER_NAME);
            if (name == null || name.isBlank()) {
                continue;
            }
            this.members.add(new MemberRow(
                    name,
                    fields.getOrDefault(prefix + JobKeys.MEMBER_UUID, ""),
                    "true".equals(fields.get(prefix + JobKeys.MEMBER_ONLINE))));
        }
        this.detailState = DetailState.READY;
        this.detailError = "";
        if (anyArmed()) {
            this.status = "";
            this.statusIsError = false;
        }
        disarmAll();
        rebuildCaches();
    }

    /**
     * Parses a non-negative count and falls back locally to zero for a missing, blank, malformed
     * or overflowing value. A count is decoration around server-owned rows; it must never abort
     * the list or leave a partially built hit table behind.
     */

    void selectJob(final String jobId) {
        if (jobId == null || jobId.isEmpty()) {
            return;
        }
        this.selectedJobId = jobId;
        this.selectedJobName = "";
        this.selectedJobType = "";
        this.selectedRank = "";
        this.selectedDepartment = "";
        this.status = "";
        this.statusIsError = false;
        disarmAll();
        this.rosterScroll = 0;
        this.detailScroll = 0;
        this.memberScroll = 0;
        this.capScroll = 0;
        this.detailState = DetailState.LOADING;
        requestInfo(jobId);
        rebuildCaches();
    }


    void cycleJob(final int delta) {
        if (this.jobs.isEmpty()) {
            return;
        }
        int current = 0;
        for (int index = 0; index < this.jobs.size(); index++) {
            if (this.jobs.get(index).id().equals(this.selectedJobId)) {
                current = index;
                break;
            }
        }
        final int next = Math.floorMod(current + delta, this.jobs.size());
        this.jobPage = next / JOB_PAGE_SIZE;
        selectJob(this.jobs.get(next).id());
    }


    void changePage(final int delta) {
        final int next = GovMetrics.clamp(this.jobPage + delta, 0,
                Math.max(0, this.jobPages - 1));
        if (next == this.jobPage) {
            return;
        }
        this.jobPage = next;
        this.rosterScroll = 0;
        if (anyArmed()) {
            this.status = "";
            this.statusIsError = false;
        }
        disarmAll();
        rebuildCaches();
    }

    // -------------------------------------------------------------------- caches


    void rebuildCaches() {
        if (this.font == null) {
            return;
        }
        rebuildRosterViews();
        rebuildDetail();
        rebuildMemberViews();
        rebuildCapViews();
        rebuildHeaderTexts();
        rebuildStatusCache();
        clampScrolls();
        rebuildMemberRemoveRects();
        rebuildCapButtonRects();
        rebuildPickerRects();
        rebuildModalChrome();
        rebuildRetryRects();
        rebuildHits();
    }

    /** Clips the modal title so the close box never overlaps it and refreshes the close rect. */

}
