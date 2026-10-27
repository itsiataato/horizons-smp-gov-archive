package com.horizonssmp.gov.client;

import com.horizonssmp.gov.client.GovFinesTypes.*;
import static com.horizonssmp.gov.client.GovFinesTypes.*;

import com.horizonssmp.gov.common.FineKeys;

final class GovFinesSelection {

    // ---------------------------------------------------------------- selection

    static void selectFine(final GovFinesPanel p, final String id) {
        if (id == null || id.isEmpty()) {
            return;
        }
        p.selectedId = id;
        p.status = "";
        p.detailState = LoadState.LOADING;
        GovFinesSupport.disarm(p);
        GovFinesRequests.requestDetail(p, id);
        GovFinesViews.rebuildAll(p);
    }

    static void setFilter(final GovFinesPanel p, final String next) {
        if (next == null || next.equals(p.registerFilter)) {
            return;
        }
        p.registerFilter = next;
        p.pageByTab[TAB_REGISTER] = 0;
        p.listState = LoadState.LOADING;
        GovFinesSupport.disarm(p);
        GovFinesRequests.requestList(p, next, 0);
        GovFinesViews.rebuildAll(p);
    }

    static void ensureIssueTarget(final GovFinesPanel p) {
        if (p.online.isEmpty()) {
            p.issueTarget = "";
            p.issueTargetIndex = 0;
            return;
        }
        int index = p.online.indexOf(p.issueTarget);
        if (index < 0) {
            index = 0;
            p.issueTarget = p.online.get(0);
        }
        p.issueTargetIndex = index;
    }

    static void cycleIssueTarget(final GovFinesPanel p, final int delta) {
        if (p.online.isEmpty()) {
            return;
        }
        int index = p.online.indexOf(p.issueTarget);
        if (index < 0) {
            index = 0;
        }
        index = (index + delta + p.online.size()) % p.online.size();
        p.issueTargetIndex = index;
        p.issueTarget = p.online.get(index);
        p.status = "";
        GovFinesViews.rebuildFormInfo(p);
        GovFinesViews.rebuildStatus(p);
    }

    static void ensureUnpaidSelection(final GovFinesPanel p) {
        if (p.rows.isEmpty()) {
            return;
        }
        int firstUnpaid = -1;
        boolean selectedUnpaid = false;
        for (int i = 0; i < p.rows.size(); i++) {
            if ("UNPAID".equals(p.rows.get(i).state())) {
                if (firstUnpaid < 0) {
                    firstUnpaid = i;
                }
                if (p.rows.get(i).id().equals(p.selectedId)) {
                    selectedUnpaid = true;
                }
            }
        }
        if (firstUnpaid < 0) {
            p.selectedId = "";
            p.detail.clear();
            p.detailState = LoadState.NONE;
            return;
        }
        if (!selectedUnpaid) {
            GovFinesSelection.selectFine(p, p.rows.get(firstUnpaid).id());
        }
    }

    static void cycleSelected(final GovFinesPanel p, final int delta) {
        if (p.rows.isEmpty()) {
            return;
        }
        int index = -1;
        for (int i = 0; i < p.rows.size(); i++) {
            if (p.rows.get(i).id().equals(p.selectedId)) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            index = 0;
        } else {
            index = GovMetrics.clamp(index + delta, 0, p.rows.size() - 1);
        }
        GovFinesSelection.selectFine(p, p.rows.get(index).id());
    }


}
