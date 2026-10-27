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

final class GovBusinessesActions {

    static     void submitRegister(final GovBusinessesPanel p) {
        if (GovBusinessesRequests.mutationPending(p) || !p.canRegister) {
            return;
        }
        final String name = p.nameBox == null ? "" : p.nameBox.getValue().strip();
        if (name.isEmpty()) {
            GovBusinessesStatus.setStatus(p, p.nameRequiredText, true);
            return;
        }
        final Map<String, String> payload = new LinkedHashMap<>();
        payload.put(BusinessKeys.KEY_NAME, name);
        payload.put(BusinessKeys.KEY_TYPE, p.registerType);
        final String owner = p.ownerBox == null ? "" : p.ownerBox.getValue().strip();
        if (!owner.isEmpty()) {
            payload.put("target", owner);
        }
        GovBusinessesRequests.mutate(p, OpCode.BIZ_REGISTER, payload);
    }

    static     void submitTransfer(final GovBusinessesPanel p) {
        if (GovBusinessesRequests.mutationPending(p) || p.selectedId.isEmpty()
                || p.detailState != LoadState.READY || !GovBusinessesStatus.canManageBusiness(p)) {
            return;
        }
        final String owner = p.transferBox == null
                ? "" : p.transferBox.getValue().strip();
        if (owner.isEmpty()) {
            GovBusinessesStatus.setStatus(p, p.transferRequiredText, true);
            return;
        }
        if (!GovBusinessesArm.arm(p, Click.TRANSFER_SUBMIT)) {
            return;
        }
        GovBusinessesRequests.mutate(p, OpCode.BIZ_TRANSFER, Map.of(
                BusinessKeys.KEY_BUSINESS, p.selectedId,
                "target", owner));
    }

    static     void submitSuspend(final GovBusinessesPanel p) {
        if (GovBusinessesRequests.mutationPending(p) || p.selectedId.isEmpty()
                || p.detailState != LoadState.READY || !GovBusinessesStatus.canManageBusiness(p)) {
            return;
        }
        final String reason = p.suspendReasonBox == null
                ? "" : p.suspendReasonBox.getValue().strip();
        if (reason.isEmpty()) {
            GovBusinessesStatus.setStatus(p, p.reasonRequiredText, true);
            return;
        }
        if (!GovBusinessesArm.arm(p, Click.SUSPEND_SUBMIT)) {
            return;
        }
        GovBusinessesRequests.mutate(p, OpCode.BIZ_SUSPEND, Map.of(
                BusinessKeys.KEY_BUSINESS, p.selectedId,
                BusinessKeys.KEY_REASON, reason));
    }

    static     void submitClose(final GovBusinessesPanel p) {
        if (GovBusinessesRequests.mutationPending(p) || p.selectedId.isEmpty()
                || p.detailState != LoadState.READY || !GovBusinessesStatus.canManageBusiness(p)) {
            return;
        }
        if (!GovBusinessesArm.arm(p, Click.CLOSE_SUBMIT)) {
            return;
        }
        GovBusinessesRequests.mutate(p, OpCode.BIZ_REVOKE, Map.of(BusinessKeys.KEY_BUSINESS, p.selectedId));
    }

    static     void submitDelete(final GovBusinessesPanel p) {
        if (GovBusinessesRequests.mutationPending(p) || p.selectedId.isEmpty()
                || p.detailState != LoadState.READY || !GovBusinessesStatus.canManageBusiness(p)) {
            return;
        }
        if (!GovBusinessesArm.arm(p, Click.DELETE_SUBMIT)) {
            return;
        }
        GovBusinessesRequests.mutate(p, OpCode.BIZ_DELETE, Map.of(BusinessKeys.KEY_BUSINESS, p.selectedId));
    }

    static     void submitStaffAdd(final GovBusinessesPanel p) {
        if (GovBusinessesRequests.mutationPending(p) || p.selectedId.isEmpty()
                || p.detailState != LoadState.READY || !GovBusinessesStatus.canEdit(p)) {
            return;
        }
        final String player = p.staffPlayerBox == null
                ? "" : p.staffPlayerBox.getValue().strip();
        if (player.isEmpty()) {
            GovBusinessesStatus.setStatus(p, p.staffRequiredText, true);
            return;
        }
        GovBusinessesRequests.mutate(p, OpCode.BIZ_EMPLOYEE_ADD, Map.of(
                BusinessKeys.KEY_BUSINESS, p.selectedId,
                "target", player,
                BusinessKeys.KEY_ROLE, p.employeeRole));
    }

    static     void submitStaffRemove(final GovBusinessesPanel p, final String uuid) {
        if (GovBusinessesRequests.mutationPending(p) || p.selectedId.isEmpty()
                || p.detailState != LoadState.READY || !GovBusinessesStatus.canEdit(p)
                || uuid == null || uuid.isBlank()) {
            return;
        }
        if (!GovBusinessesArm.armStaffRemove(p, uuid)) {
            return;
        }
        GovBusinessesRequests.mutate(p, OpCode.BIZ_EMPLOYEE_REMOVE, Map.of(
                BusinessKeys.KEY_BUSINESS, p.selectedId,
                "target", uuid));
    }

    static     void submitLink(final GovBusinessesPanel p) {
        if (GovBusinessesRequests.mutationPending(p) || p.selectedId.isEmpty()
                || p.detailState != LoadState.READY || !GovBusinessesStatus.canEdit(p)) {
            return;
        }
        final String job = p.linkJobBox == null ? "" : p.linkJobBox.getValue().strip();
        if (job.isEmpty()) {
            GovBusinessesStatus.setStatus(p, p.linkRequiredText, true);
            return;
        }
        GovBusinessesRequests.mutate(p, OpCode.BIZ_LINK, Map.of(
                BusinessKeys.KEY_BUSINESS, p.selectedId,
                BusinessKeys.KEY_JOB, job));
    }

    static     void submitUnlink(final GovBusinessesPanel p) {
        if (GovBusinessesRequests.mutationPending(p) || p.selectedId.isEmpty()
                || p.detailState != LoadState.READY || !GovBusinessesStatus.canEdit(p)) {
            return;
        }
        GovBusinessesRequests.mutate(p, OpCode.BIZ_LINK, Map.of(
                BusinessKeys.KEY_BUSINESS, p.selectedId,
                BusinessKeys.KEY_JOB, ""));
    }

    static     void submitPayrollSet(final GovBusinessesPanel p) {
        if (GovBusinessesRequests.mutationPending(p) || p.selectedId.isEmpty()
                || p.detailState != LoadState.READY || !GovBusinessesStatus.canEdit(p)) {
            return;
        }
        final String rate = p.rateBox == null ? "" : p.rateBox.getValue().strip();
        if (rate.isEmpty()) {
            GovBusinessesStatus.setStatus(p, p.rateRequiredText, true);
            return;
        }
        final int parsed;
        try {
            parsed = Integer.parseInt(rate);
        } catch (final NumberFormatException e) {
            GovBusinessesStatus.setStatus(p, p.rateInvalidText, true);
            return;
        }
        GovBusinessesRequests.mutate(p, OpCode.BIZ_PAYROLL_SET, Map.of(
                BusinessKeys.KEY_BUSINESS, p.selectedId,
                BusinessKeys.KEY_RATE, Integer.toString(parsed),
                BusinessKeys.KEY_SCHEDULE,
                p.scheduleBox == null ? "" : p.scheduleBox.getValue().strip()));
    }

    static     void submitPayrollRecord(final GovBusinessesPanel p) {
        if (GovBusinessesRequests.mutationPending(p) || p.selectedId.isEmpty()
                || p.detailState != LoadState.READY || !GovBusinessesStatus.canRecordPayroll(p)) {
            return;
        }
        final String amount = p.amountBox == null
                ? "" : p.amountBox.getValue().strip();
        if (amount.isEmpty()) {
            GovBusinessesStatus.setStatus(p, p.amountRequiredText, true);
            return;
        }
        final int parsed;
        try {
            parsed = Integer.parseInt(amount);
        } catch (final NumberFormatException e) {
            GovBusinessesStatus.setStatus(p, p.amountInvalidText, true);
            return;
        }
        if (parsed <= 0) {
            GovBusinessesStatus.setStatus(p, p.amountInvalidText, true);
            return;
        }
        final boolean confirming = GovBusinessesArm.isArmed(p, Click.PAYROLL_RECORD);
        final String sendAmount = confirming && !p.payrollRecordArmedAmount.isEmpty()
                ? p.payrollRecordArmedAmount : Integer.toString(parsed);
        final String note = p.paymentNoteBox == null
                ? "" : p.paymentNoteBox.getValue().strip();
        if (!GovBusinessesArm.arm(p, Click.PAYROLL_RECORD)) {
            return;
        }
        GovBusinessesRequests.mutate(p, OpCode.BIZ_PAYROLL_RECORD, Map.of(
                BusinessKeys.KEY_BUSINESS, p.selectedId,
                BusinessKeys.KEY_AMOUNT, sendAmount,
                BusinessKeys.KEY_NOTE, note));
    }

    static     void submitRename(final GovBusinessesPanel p) {
        if (GovBusinessesRequests.mutationPending(p) || p.selectedId.isEmpty() || p.detailState != LoadState.READY) { return; }
        final String name = p.transferBox == null ? "" : p.transferBox.getValue().strip();
        if (name.isEmpty()) { GovBusinessesStatus.setStatus(p, "Name required", true); return; }
        GovBusinessesRequests.mutate(p, OpCode.BIZ_RENAME, Map.of(BusinessKeys.KEY_BUSINESS, p.selectedId, BusinessKeys.KEY_NAME, name));
    }













}
