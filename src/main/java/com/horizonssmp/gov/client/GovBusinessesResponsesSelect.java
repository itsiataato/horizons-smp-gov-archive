package com.horizonssmp.gov.client;

import com.horizonssmp.gov.common.BusinessKeys;
import com.horizonssmp.gov.client.GovBusinessesTypes.*;
import java.util.Map;

final class GovBusinessesResponsesSelect {

    static void selectBusiness(final GovBusinessesPanel p, final String id) {
        if (id == null || id.isEmpty()) {
            return;
        }
        p.selectedId = id;
        p.detail.clear();
        p.detailScroll = 0;
        p.status = "";
        p.statusIsError = false;
        GovBusinessesArm.disarmAll(p);
        p.detailState = LoadState.LOADING;
        GovBusinessesRequests.requestDetail(p, id);
        GovBusinessesCaches.rebuildCaches(p);
    }

    static void selectPosition(final GovBusinessesPanel p, final String id) {
        if (id == null || id.isEmpty()) {
            return;
        }
        p.selectedPosition = id;
        GovBusinessesArm.disarmAll(p);
        p.status = "";
        p.statusIsError = false;
        // Populate the edit fields from the matching row so the editor opens pre-filled.
        for (final Map<String, String> row : p.positionRows) {
            if (id.equals(row.get(BusinessKeys.POSITION_ROW_ID))) {
                if (p.positionTitleBox != null) {
                    p.positionTitleBox.setValue(row.getOrDefault(BusinessKeys.POSITION_ROW_TITLE, ""));
                }
                if (p.positionWageBox != null) {
                    p.positionWageBox.setValue(row.getOrDefault(BusinessKeys.POSITION_ROW_WAGE, ""));
                }
                break;
            }
        }
        GovBusinessesCaches.rebuildCaches(p);
    }

    static void cycleBusiness(final GovBusinessesPanel p, final int delta) {
        if (p.rows.isEmpty()) {
            return;
        }
        int current = 0;
        for (int index = 0; index < p.rows.size(); index++) {
            if (p.rows.get(index).id().equals(p.selectedId)) {
                current = index;
                break;
            }
        }
        final int next = Math.floorMod(current + delta, p.rows.size());
        GovBusinessesResponses.selectBusiness(p, p.rows.get(next).id());
    }

    static void setFilter(final GovBusinessesPanel p, final String next) {
        if (next == null || next.equals(p.filter)) {
            return;
        }
        p.filter = next;
        p.page = 0;
        p.registryScroll = 0;
        p.listState = LoadState.LOADING;
        if (GovBusinessesArm.anyArmed(p)) {
            p.status = "";
            p.statusIsError = false;
        }
        GovBusinessesArm.disarmAll(p);
        GovBusinessesRequests.requestList(p);
    }

    static void changePage(final GovBusinessesPanel p, final int delta) {
        final int next = GovMetrics.clamp(p.page + delta, 0, Math.max(0, p.pages - 1));
        if (next == p.page) {
            return;
        }
        p.page = next;
        p.registryScroll = 0;
        p.listState = LoadState.LOADING;
        if (GovBusinessesArm.anyArmed(p)) {
            p.status = "";
            p.statusIsError = false;
        }
        GovBusinessesArm.disarmAll(p);
        GovBusinessesRequests.requestList(p);
    }
}
