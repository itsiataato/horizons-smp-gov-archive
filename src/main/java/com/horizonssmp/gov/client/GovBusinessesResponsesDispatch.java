package com.horizonssmp.gov.client;

import com.horizonssmp.gov.common.GovFields;
import com.horizonssmp.gov.network.GovActionResultPayload;
import net.minecraft.network.chat.Component;
import com.horizonssmp.gov.client.GovBusinessesTypes.*;

final class GovBusinessesResponsesDispatch {

    static void onActionResult(final GovBusinessesPanel p, final GovActionResultPayload payload) {
        final RequestKind kind = p.pending.remove(payload.reqId());
        if (kind == null) {
            return;
        }
        switch (kind) {
            case LIST -> {
                if (!payload.reqId().equals(p.latestListReqId)) {
                    return;
                }
                if (payload.ok()) {
                    GovBusinessesResponses.applyList(p, payload.delta());
                } else {
                    p.listState = LoadState.ERROR;
                    p.listError = GovBusinessesResponses.fallbackError(payload, "gui.gov.businesses.error");
                    GovBusinessesCaches.rebuildCaches(p);
                }
            }
            case DETAIL -> {
                if (!payload.reqId().equals(p.latestDetailReqId)) {
                    return;
                }
                if (payload.ok()) {
                    GovBusinessesResponses.applyDetail(p, payload.delta());
                } else {
                    p.detailState = LoadState.ERROR;
                    p.detailError = GovBusinessesResponses.fallbackError(payload,
                            "gui.gov.businesses.detail_error");
                    GovBusinessesCaches.rebuildCaches(p);
                }
            }
            case PAYROLL_INFO -> {
                if (payload.ok()) {
                    p.payrollInfoFields.clear();
                    p.payrollInfoFields.putAll(GovFields.decode(payload.delta()));
                    p.status = "Payroll info loaded.";
                    p.statusIsError = false;
                } else {
                    p.status = GovBusinessesResponses.fallbackError(payload, "gui.gov.businesses.error");
                    p.statusIsError = true;
                }
                GovBusinessesCaches.rebuildCaches(p);
            }
            case MUTATE -> {
                if (payload.ok()) {
                    p.status = payload.delta().isBlank() ? "Done." : payload.delta();
                    p.statusIsError = false;
                    p.modal = Modal.NONE;
                    GovBusinessesArm.disarmAll(p);
                    GovBusinessesWidgets.clearFormFields(p);
                    GovBusinessesWidgets.syncWidgets(p);
                    GovBusinessesRequests.requestList(p);
                    if (!p.selectedId.isEmpty()) {
                        GovBusinessesRequests.requestDetail(p, p.selectedId);
                    }
                } else {
                    p.status = GovBusinessesResponses.fallbackError(payload, "gui.gov.businesses.error");
                    p.statusIsError = true;
                    p.modal = Modal.NONE;
                    GovBusinessesArm.disarmAll(p);
                    GovBusinessesWidgets.syncWidgets(p);
                }
                GovBusinessesCaches.rebuildCaches(p);
            }
            case POSITION_EDIT -> {
                if (payload.ok()) {
                    p.status = payload.delta().isBlank() ? "Position updated." : payload.delta();
                    p.statusIsError = false;
                    p.modal = Modal.NONE;
                    p.selectedPosition = "";
                    GovBusinessesArm.disarmAll(p);
                    GovBusinessesWidgets.clearFormFields(p);
                    GovBusinessesWidgets.syncWidgets(p);
                    GovBusinessesRequests.requestPositionList(p);
                } else {
                    p.status = GovBusinessesResponses.fallbackError(payload, "gui.gov.businesses.error");
                    p.statusIsError = true;
                }
                GovBusinessesCaches.rebuildCaches(p);
            }
            case POSITION_LIST -> {
                if (!payload.reqId().equals(p.latestPositionReqId)) {
                    return;
                }
                if (payload.ok()) {
                    GovBusinessesResponses.applyPositionList(p, payload.delta());
                } else {
                    p.positionState = LoadState.ERROR;
                    p.positionError = GovBusinessesResponses.fallbackError(payload,
                            "gui.gov.businesses.error");
                    GovBusinessesCaches.rebuildCaches(p);
                }
            }
            case OFFER_LIST -> {
                if (!payload.reqId().equals(p.latestOfferReqId)) {
                    return;
                }
                if (payload.ok()) {
                    GovBusinessesResponses.applyOfferList(p, payload.delta());
                } else {
                    p.offerState = LoadState.ERROR;
                    p.offerError = GovBusinessesResponses.fallbackError(payload,
                            "gui.gov.businesses.error");
                    GovBusinessesCaches.rebuildCaches(p);
                }
            }
            case CONTRACT_LIST -> {
                if (!payload.reqId().equals(p.latestContractReqId)) {
                    return;
                }
                if (payload.ok()) {
                    GovBusinessesResponses.applyContractList(p, payload.delta());
                } else {
                    p.contractState = LoadState.ERROR;
                    p.contractError = GovBusinessesResponses.fallbackError(payload,
                            "gui.gov.businesses.error");
                    GovBusinessesCaches.rebuildCaches(p);
                }
            }
            case COMPLAINT_LIST -> {
                if (!payload.reqId().equals(p.latestComplaintReqId)) {
                    return;
                }
                if (payload.ok()) {
                    GovBusinessesResponses.applyComplaintList(p, payload.delta());
                } else {
                    p.complaintState = LoadState.ERROR;
                    p.complaintError = GovBusinessesResponses.fallbackError(payload,
                            "gui.gov.businesses.error");
                    GovBusinessesCaches.rebuildCaches(p);
                }
            }
        }
    }

    static String fallbackError(final GovActionResultPayload payload, final String key) {
        return payload.errorText().isBlank()
                ? Component.translatable(key).getString()
                : payload.errorText();
    }
}
