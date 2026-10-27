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

final class GovBusinessesActions2 {

    static     void submitPayrollRun(final GovBusinessesPanel p) {
        if (GovBusinessesRequests.mutationPending(p) || p.selectedId.isEmpty() || p.detailState != LoadState.READY) { return; }
        GovBusinessesRequests.mutate(p, OpCode.BIZ_PAYROLL_RUN, Map.of(BusinessKeys.KEY_BUSINESS, p.selectedId));
    }

    static     void submitPositionCreate(final GovBusinessesPanel p) {
        if (GovBusinessesRequests.mutationPending(p) || p.selectedId.isEmpty() || p.detailState != LoadState.READY) { return; }
        final String title = p.transferBox == null ? "" : p.transferBox.getValue().strip();
        final String wage = p.rateBox == null ? "" : p.rateBox.getValue().strip();
        final String parent = p.scheduleBox == null ? "" : p.scheduleBox.getValue().strip();
        GovBusinessesRequests.mutate(p, OpCode.BIZ_POSITION_CREATE, Map.of(
                BusinessKeys.KEY_BUSINESS, p.selectedId,
                BusinessKeys.KEY_TITLE, title,
                BusinessKeys.KEY_WAGE, wage,
                BusinessKeys.KEY_PARENT, parent,
                BusinessKeys.KEY_ORDER, "0",
                BusinessKeys.KEY_PERMISSIONS, ""));
    }

    static     void submitPositionDelete(final GovBusinessesPanel p) {
        if (GovBusinessesRequests.mutationPending(p) || p.selectedId.isEmpty() || p.detailState != LoadState.READY) { return; }
        final String position = p.scheduleBox == null ? "" : p.scheduleBox.getValue().strip();
        if (position.isEmpty()) { GovBusinessesStatus.setStatus(p, "Position id required", true); return; }
        GovBusinessesRequests.mutate(p, OpCode.BIZ_POSITION_DELETE, Map.of(BusinessKeys.KEY_BUSINESS, p.selectedId, BusinessKeys.KEY_POSITION, position));
    }

    static     void submitPositionMove(final GovBusinessesPanel p) {
        if (GovBusinessesRequests.mutationPending(p) || p.selectedId.isEmpty() || p.detailState != LoadState.READY) { return; }
        final String position = p.scheduleBox == null ? "" : p.scheduleBox.getValue().strip();
        final String parent = p.amountBox == null ? "" : p.amountBox.getValue().strip();
        if (position.isEmpty()) { GovBusinessesStatus.setStatus(p, "Position id required", true); return; }
        GovBusinessesRequests.mutate(p, OpCode.BIZ_POSITION_MOVE, Map.of(BusinessKeys.KEY_BUSINESS, p.selectedId, BusinessKeys.KEY_POSITION, position, BusinessKeys.KEY_PARENT, parent));
    }

    static     void submitPositionEdit(final GovBusinessesPanel p) {
        if (GovBusinessesRequests.mutationPending(p) || p.selectedId.isEmpty()
                || p.selectedPosition.isEmpty()) {
            return;
        }
        final String title = p.positionTitleBox == null ? "" : p.positionTitleBox.getValue().strip();
        final String wage = p.positionWageBox == null ? "" : p.positionWageBox.getValue().strip();
        GovBusinessesRequests.requestPositionEdit(p, p.selectedPosition, title, wage, "", "");
    }

    static     void submitOfferSend(final GovBusinessesPanel p) {
        if (GovBusinessesRequests.mutationPending(p) || p.selectedId.isEmpty() || p.detailState != LoadState.READY) { return; }
        final String position = p.scheduleBox == null ? "" : p.scheduleBox.getValue().strip();
        final String wage = p.rateBox == null ? "" : p.rateBox.getValue().strip();
        final String oneTime = p.paymentNoteBox == null ? "" : p.paymentNoteBox.getValue().strip();
        final String target = p.transferBox == null ? "" : p.transferBox.getValue().strip();
        if (target.isEmpty()) { GovBusinessesStatus.setStatus(p, "Target player required", true); return; }
        GovBusinessesRequests.mutate(p, OpCode.BIZ_OFFER_SEND, Map.of(
                BusinessKeys.KEY_BUSINESS, p.selectedId,
                BusinessKeys.KEY_POSITION, position,
                BusinessKeys.KEY_WAGE, wage,
                BusinessKeys.KEY_ONE_TIME, oneTime,
                "target", target));
    }

    static     void submitOfferRespond(final GovBusinessesPanel p) {
        if (GovBusinessesRequests.mutationPending(p) || p.selectedId.isEmpty() || p.detailState != LoadState.READY) { return; }
        final String offer = p.amountBox == null ? "" : p.amountBox.getValue().strip();
        final String decision = p.transferBox == null ? "" : p.transferBox.getValue().strip();
        if (offer.isEmpty()) { GovBusinessesStatus.setStatus(p, "Offer id required", true); return; }
        GovBusinessesRequests.mutate(p, OpCode.BIZ_OFFER_RESPOND, Map.of(
                BusinessKeys.KEY_BUSINESS, p.selectedId,
                BusinessKeys.KEY_OFFER, offer,
                BusinessKeys.KEY_CONTRACT, offer,
                BusinessKeys.KEY_DECISION, decision));
    }

    static     void submitContractEnd(final GovBusinessesPanel p) {
        if (GovBusinessesRequests.mutationPending(p) || p.selectedId.isEmpty() || p.detailState != LoadState.READY) { return; }
        final String contract = p.amountBox == null ? "" : p.amountBox.getValue().strip();
        final String decision = p.transferBox == null ? "" : p.transferBox.getValue().strip();
        final String reason = p.paymentNoteBox == null ? "" : p.paymentNoteBox.getValue().strip();
        if (contract.isEmpty()) { GovBusinessesStatus.setStatus(p, "Contract id required", true); return; }
        GovBusinessesRequests.mutate(p, OpCode.BIZ_CONTRACT_END, Map.of(
                BusinessKeys.KEY_BUSINESS, p.selectedId,
                BusinessKeys.KEY_CONTRACT, contract,
                BusinessKeys.KEY_OFFER, contract,
                BusinessKeys.KEY_DECISION, decision,
                BusinessKeys.KEY_REASON, reason));
    }

    static     void submitContractPromote(final GovBusinessesPanel p) {
        if (GovBusinessesRequests.mutationPending(p) || p.selectedId.isEmpty() || p.detailState != LoadState.READY) { return; }
        final String contract = p.amountBox == null ? "" : p.amountBox.getValue().strip();
        final String position = p.scheduleBox == null ? "" : p.scheduleBox.getValue().strip();
        if (contract.isEmpty()) { GovBusinessesStatus.setStatus(p, "Contract id required", true); return; }
        GovBusinessesRequests.mutate(p, OpCode.BIZ_CONTRACT_PROMOTE, Map.of(
                BusinessKeys.KEY_BUSINESS, p.selectedId,
                BusinessKeys.KEY_CONTRACT, contract,
                BusinessKeys.KEY_OFFER, contract,
                BusinessKeys.KEY_POSITION, position));
    }

    static     void submitComplaintFile(final GovBusinessesPanel p) {
        if (GovBusinessesRequests.mutationPending(p) || p.selectedId.isEmpty() || p.detailState != LoadState.READY) { return; }
        final String reason = p.paymentNoteBox == null ? "" : p.paymentNoteBox.getValue().strip();
        final String target = p.transferBox == null ? "" : p.transferBox.getValue().strip();
        final String employee = p.amountBox == null ? "" : p.amountBox.getValue().strip();
        if (reason.isEmpty()) { GovBusinessesStatus.setStatus(p, "Reason required", true); return; }
        GovBusinessesRequests.mutate(p, OpCode.BIZ_COMPLAINT_FILE, Map.of(
                BusinessKeys.KEY_BUSINESS, p.selectedId,
                BusinessKeys.KEY_REASON, reason,
                "target", target,
                BusinessKeys.KEY_EMPLOYEE, employee));
    }

    static     void submitCloseRequest(final GovBusinessesPanel p) {
        if (GovBusinessesRequests.mutationPending(p) || p.selectedId.isEmpty() || p.detailState != LoadState.READY) { return; }
        final String reason = p.paymentNoteBox == null ? "" : p.paymentNoteBox.getValue().strip();
        GovBusinessesRequests.mutate(p, OpCode.BIZ_CLOSE_REQUEST, Map.of(
                BusinessKeys.KEY_BUSINESS, p.selectedId,
                BusinessKeys.KEY_CLOSE_REASON, reason,
                BusinessKeys.KEY_REASON, reason));
    }

    static     void submitCloseApprove(final GovBusinessesPanel p) {
        if (GovBusinessesRequests.mutationPending(p) || p.selectedId.isEmpty() || p.detailState != LoadState.READY) { return; }
        final String reason = p.paymentNoteBox == null ? "" : p.paymentNoteBox.getValue().strip();
        GovBusinessesRequests.mutate(p, OpCode.BIZ_CLOSE_APPROVE, Map.of(
                BusinessKeys.KEY_BUSINESS, p.selectedId,
                BusinessKeys.KEY_DECISION, "approve",
                BusinessKeys.KEY_CLOSE_REASON, reason,
                BusinessKeys.KEY_REASON, reason));
    }

    static     void submitCloseReject(final GovBusinessesPanel p) {
        if (GovBusinessesRequests.mutationPending(p) || p.selectedId.isEmpty() || p.detailState != LoadState.READY) { return; }
        final String reason = p.paymentNoteBox == null ? "" : p.paymentNoteBox.getValue().strip();
        GovBusinessesRequests.mutate(p, OpCode.BIZ_CLOSE_REJECT, Map.of(
                BusinessKeys.KEY_BUSINESS, p.selectedId,
                BusinessKeys.KEY_DECISION, "reject",
                BusinessKeys.KEY_CLOSE_REASON, reason,
                BusinessKeys.KEY_REASON, reason));
    }

}
