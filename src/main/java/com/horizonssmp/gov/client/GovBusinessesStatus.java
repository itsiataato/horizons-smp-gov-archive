package com.horizonssmp.gov.client;


import com.horizonssmp.gov.client.GovDashboardScreen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.horizonssmp.gov.common.BusinessKeys;
import com.horizonssmp.gov.common.GovFields;
import com.horizonssmp.gov.common.OpCode;
import com.horizonssmp.gov.network.GovActionResultPayload;
import com.horizonssmp.gov.network.GovNetwork;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import com.horizonssmp.gov.client.GovBusinessesTypes.*;
import static com.horizonssmp.gov.client.GovBusinessesTypes.*;

final class GovBusinessesStatus {

    static     void setStatus(final GovBusinessesPanel p, final String text, final boolean error) {
        p.status = text == null ? "" : text;
        p.statusIsError = error;
        GovBusinessesCaches2.rebuildStatusCache(p);
    }

    static     boolean canEdit(final GovBusinessesPanel p) {
        return "true".equals(p.detail.get(BusinessKeys.CAN_EDIT_THIS));
    }

    static     boolean canRecordPayroll(final GovBusinessesPanel p) {
        return "true".equals(p.detail.get(BusinessKeys.CAN_RECORD_PAYROLL_THIS))
                || p.canPayroll;
    }

    static     boolean canManageBusiness(final GovBusinessesPanel p) {
        return p.canManage
                || "true".equals(p.detail.get(BusinessKeys.CAN_MANAGE_THIS));
    }

    static     boolean staffActionsEnabled(final GovBusinessesPanel p) {
        return !p.selectedId.isEmpty() && p.detailState == LoadState.READY
                && GovBusinessesStatus.canEdit(p) && !GovBusinessesRequests.mutationPending(p);
    }

    static     boolean actionEnabled(final GovBusinessesPanel p, final Click click) {
        if (GovBusinessesRequests.mutationPending(p)) {
            return false;
        }
        return switch (click) {
            case TRANSFER_SUBMIT, SUSPEND_OPEN, CLOSE_SUBMIT, DELETE_SUBMIT ->
                !p.selectedId.isEmpty() && p.detailState == LoadState.READY
                && GovBusinessesStatus.canManageBusiness(p);
            case REGISTER_SUBMIT -> p.canRegister;
            case PAYROLL_SET -> !p.selectedId.isEmpty()
                && p.detailState == LoadState.READY && GovBusinessesStatus.canEdit(p);
            case PAYROLL_RECORD -> !p.selectedId.isEmpty()
                && p.detailState == LoadState.READY && GovBusinessesStatus.canRecordPayroll(p);
            case STAFF_ADD_SUBMIT, LINK_SUBMIT, LINK_UNLINK ->
                !p.selectedId.isEmpty() && p.detailState == LoadState.READY
                && GovBusinessesStatus.canEdit(p);
            default -> true;
        };
    }

    static     String businessName(final GovBusinessesPanel p) {
        final String name = p.detail.get(BusinessKeys.NAME);
        return name == null || name.isBlank() ? p.selectedId : name;
    }

    static     String businessStatus(final GovBusinessesPanel p) {
        return p.detail.getOrDefault(BusinessKeys.STATUS, "");
    }

    static     String jobText(final GovBusinessesPanel p) {
        final String jobId = p.detail.getOrDefault(BusinessKeys.JOB, "");
        if (jobId.isBlank()) {
            return GovBusinessesUtil.tr("gui.gov.businesses.no_job");
        }
        final String jobName = p.detail.getOrDefault(BusinessKeys.JOB_NAME, jobId);
        return jobName + "  (" + jobId + ")";
    }

    static     String typeLabel(final GovBusinessesPanel p, final String id) {
        if (id == null || id.isBlank()) {
            return "-";
        }
        final String needle = id.toLowerCase(Locale.ROOT);
        for (int index = 0; index < TYPE_IDS.length; index++) {
            if (TYPE_IDS[index].equals(needle)) {
                return GovBusinessesUtil.upper(p.typeLabels[index]);
            }
        }
        return id;
    }

    static     String roleLabel(final GovBusinessesPanel p, final String id) {
        if (id == null || id.isBlank()) {
            return "-";
        }
        final String needle = id.toLowerCase(Locale.ROOT);
        for (int index = 0; index < ROLE_IDS.length; index++) {
            if (ROLE_IDS[index].equals(needle)) {
                return GovBusinessesUtil.upper(p.roleLabels[index]);
            }
        }
        return id;
    }

}
