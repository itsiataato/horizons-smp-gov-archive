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

final class GovBusinessesRequests {

    static     void requestListIfNeeded(final GovBusinessesPanel p) {
        if (p.listState == LoadState.NONE) {
            GovBusinessesRequests.requestList(p);
        }
    }

    static     void requestList(final GovBusinessesPanel p) {
        final String reqId = UUID.randomUUID().toString();
        p.pending.put(reqId, RequestKind.LIST);
        p.latestListReqId = reqId;
        p.listState = LoadState.LOADING;
        p.listError = "";
        GovNetwork.sendRequest(OpCode.BIZ_LIST, Map.of(
                BusinessKeys.KEY_FILTER, p.filter,
                BusinessKeys.KEY_PAGE, Integer.toString(p.page)), reqId);
        GovBusinessesCaches.rebuildCaches(p);
    }

    static     void requestDetail(final GovBusinessesPanel p, final String businessId) {
        if (businessId == null || businessId.isBlank()) {
            return;
        }
        final String reqId = UUID.randomUUID().toString();
        p.pending.put(reqId, RequestKind.DETAIL);
        p.latestDetailReqId = reqId;
        p.detailState = LoadState.LOADING;
        p.detailError = "";
        GovNetwork.sendRequest(OpCode.BIZ_INFO,
                Map.of(BusinessKeys.KEY_BUSINESS, businessId), reqId);
        GovBusinessesCaches.rebuildCaches(p);
    }

    static     void mutate(final GovBusinessesPanel p, final OpCode op, final Map<String, String> payload) {
        if (GovBusinessesRequests.mutationPending(p)) {
            return;
        }
        final String reqId = UUID.randomUUID().toString();
        p.pending.put(reqId, RequestKind.MUTATE);
        GovNetwork.sendRequest(op, payload, reqId);
    }

    static     void requestPayrollInfo(final GovBusinessesPanel p) {
        if (p.selectedId == null || p.selectedId.isBlank()) {
            return;
        }
        final String reqId = UUID.randomUUID().toString();
        p.pending.put(reqId, RequestKind.PAYROLL_INFO);
        GovNetwork.sendRequest(OpCode.PAYROLL_INFO, Map.of(BusinessKeys.KEY_BUSINESS, p.selectedId), reqId);
    }

    /** Fetch the position tree for the selected business. */
    static     void requestPositionList(final GovBusinessesPanel p) {
        if (p.selectedId == null || p.selectedId.isBlank()) {
            return;
        }
        final String reqId = UUID.randomUUID().toString();
        p.pending.put(reqId, RequestKind.POSITION_LIST);
        p.latestPositionReqId = reqId;
        p.positionState = LoadState.LOADING;
        p.positionError = "";
        GovNetwork.sendRequest(OpCode.BIZ_POSITION_LIST, Map.of(
                BusinessKeys.KEY_BUSINESS, p.selectedId,
                BusinessKeys.KEY_PAGE, Integer.toString(p.positionPage)), reqId);
    }

    /** Partially edit a position; absent keys are left unchanged by the server. */
    static     void requestPositionEdit(final GovBusinessesPanel p, final String positionId,
            final String title, final String wage, final String permissions, final String order) {
        if (p.selectedId == null || p.selectedId.isBlank()
                || positionId == null || positionId.isBlank()) {
            return;
        }
        final String reqId = UUID.randomUUID().toString();
        p.pending.put(reqId, RequestKind.POSITION_EDIT);
        final Map<String, String> payload = new LinkedHashMap<>();
        payload.put(BusinessKeys.KEY_BUSINESS, p.selectedId);
        payload.put(BusinessKeys.KEY_POSITION, positionId);
        if (!title.isBlank()) {
            payload.put(BusinessKeys.KEY_TITLE, title);
        }
        if (!wage.isBlank()) {
            payload.put(BusinessKeys.KEY_WAGE, wage);
        }
        if (!permissions.isBlank()) {
            payload.put(BusinessKeys.KEY_PERMISSIONS, permissions);
        }
        if (!order.isBlank()) {
            payload.put(BusinessKeys.KEY_ORDER, order);
        }
        GovNetwork.sendRequest(OpCode.BIZ_POSITION_EDIT, payload, reqId);
    }

    /** Fetch pending offers for the selected business (or the viewer's own). */
    static     void requestOfferList(final GovBusinessesPanel p) {
        if (p.selectedId == null || p.selectedId.isBlank()) {
            return;
        }
        final String reqId = UUID.randomUUID().toString();
        p.pending.put(reqId, RequestKind.OFFER_LIST);
        p.latestOfferReqId = reqId;
        p.offerState = LoadState.LOADING;
        p.offerError = "";
        GovNetwork.sendRequest(OpCode.BIZ_OFFER_LIST, Map.of(
                BusinessKeys.KEY_BUSINESS, p.selectedId,
                BusinessKeys.KEY_PAGE, Integer.toString(p.offerPage)), reqId);
    }

    /** Fetch contracts for the selected business (or the viewer's own). */
    static     void requestContractList(final GovBusinessesPanel p) {
        if (p.selectedId == null || p.selectedId.isBlank()) {
            return;
        }
        final String reqId = UUID.randomUUID().toString();
        p.pending.put(reqId, RequestKind.CONTRACT_LIST);
        p.latestContractReqId = reqId;
        p.contractState = LoadState.LOADING;
        p.contractError = "";
        GovNetwork.sendRequest(OpCode.BIZ_CONTRACT_LIST, Map.of(
                BusinessKeys.KEY_BUSINESS, p.selectedId,
                BusinessKeys.KEY_PAGE, Integer.toString(p.contractPage)), reqId);
    }

    /** Fetch complaints for the selected business (or the viewer's own). */
    static     void requestComplaintList(final GovBusinessesPanel p) {
        if (p.selectedId == null || p.selectedId.isBlank()) {
            return;
        }
        final String reqId = UUID.randomUUID().toString();
        p.pending.put(reqId, RequestKind.COMPLAINT_LIST);
        p.latestComplaintReqId = reqId;
        p.complaintState = LoadState.LOADING;
        p.complaintError = "";
        GovNetwork.sendRequest(OpCode.BIZ_COMPLAINT_LIST, Map.of(
                BusinessKeys.KEY_BUSINESS, p.selectedId,
                BusinessKeys.KEY_PAGE, Integer.toString(p.complaintPage)), reqId);
    }

    static     boolean mutationPending(final GovBusinessesPanel p) {
        return p.pending.containsValue(RequestKind.MUTATE)
                || p.pending.containsValue(RequestKind.POSITION_EDIT);
    }

}
