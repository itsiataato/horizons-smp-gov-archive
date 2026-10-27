package com.horizonssmp.gov.client;

import com.horizonssmp.gov.network.GovActionResultPayload;

final class GovBusinessesResponses {

    static void onActionResult(final GovBusinessesPanel p, final GovActionResultPayload payload) {
        GovBusinessesResponsesDispatch.onActionResult(p, payload);
    }

    static String fallbackError(final GovActionResultPayload payload, final String key) {
        return GovBusinessesResponsesDispatch.fallbackError(payload, key);
    }

    static void applyList(final GovBusinessesPanel p, final String delta) {
        GovBusinessesResponsesApply.applyList(p, delta);
    }

    static void applyDetail(final GovBusinessesPanel p, final String delta) {
        GovBusinessesResponsesApply.applyDetail(p, delta);
    }

    static void applyPositionList(final GovBusinessesPanel p, final String delta) {
        GovBusinessesResponsesApply.applyPositionList(p, delta);
    }

    static void applyOfferList(final GovBusinessesPanel p, final String delta) {
        GovBusinessesResponsesApply.applyOfferList(p, delta);
    }

    static void applyContractList(final GovBusinessesPanel p, final String delta) {
        GovBusinessesResponsesApply.applyContractList(p, delta);
    }

    static void applyComplaintList(final GovBusinessesPanel p, final String delta) {
        GovBusinessesResponsesApply.applyComplaintList(p, delta);
    }

    static void selectBusiness(final GovBusinessesPanel p, final String id) {
        GovBusinessesResponsesSelect.selectBusiness(p, id);
    }

    static void selectPosition(final GovBusinessesPanel p, final String id) {
        GovBusinessesResponsesSelect.selectPosition(p, id);
    }

    static void cycleBusiness(final GovBusinessesPanel p, final int delta) {
        GovBusinessesResponsesSelect.cycleBusiness(p, delta);
    }

    static void setFilter(final GovBusinessesPanel p, final String next) {
        GovBusinessesResponsesSelect.setFilter(p, next);
    }

    static void changePage(final GovBusinessesPanel p, final int delta) {
        GovBusinessesResponsesSelect.changePage(p, delta);
    }
}
