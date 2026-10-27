package com.horizonssmp.gov.client;

import com.horizonssmp.gov.client.GovFinesTypes.*;
import static com.horizonssmp.gov.client.GovFinesTypes.*;

import java.util.Map;
import java.util.UUID;

import com.horizonssmp.gov.common.FineKeys;
import com.horizonssmp.gov.common.OpCode;
import com.horizonssmp.gov.network.GovNetwork;

final class GovFinesRequests {

    // ------------------------------------------------------------------ requests

    static void requestListIfNeeded(final GovFinesPanel p) {
        final String wanted = modeFilter(p);
        if (p.listState == LoadState.LOADING) {
            if (!wanted.equals(p.requestedFilter)) {
                requestList(p, wanted, p.pageByTab[p.subtab]);
            }
            return;
        }
        if (p.listState == LoadState.NONE) {
            requestList(p, wanted, p.pageByTab[p.subtab]);
            return;
        }
        if ((p.subtab == TAB_COLLECT || p.subtab == TAB_WAIVE)
                && !FineKeys.FILTER_UNPAID.equals(p.listFilter)) {
            requestList(p, FineKeys.FILTER_UNPAID, 0);
            return;
        }
        if (p.subtab == TAB_REGISTER && !p.registerFilter.equals(p.listFilter)) {
            requestList(p, p.registerFilter, p.pageByTab[TAB_REGISTER]);
        }
    }

    static String modeFilter(final GovFinesPanel p) {
        return p.subtab == TAB_REGISTER ? p.registerFilter : FineKeys.FILTER_UNPAID;
    }

    static void requestList(final GovFinesPanel p, final String filter, final int requestedPage) {
        final String reqId = UUID.randomUUID().toString();
        p.pending.put(reqId, RequestKind.LIST);
        p.latestListReqId = reqId;
        p.requestedFilter = filter == null ? FineKeys.FILTER_ALL : filter;
        p.listState = LoadState.LOADING;
        p.listError = "";
        GovNetwork.sendRequest(OpCode.FINE_LIST, Map.of(
                FineKeys.KEY_FILTER, p.requestedFilter,
                FineKeys.KEY_PAGE, Integer.toString(Math.max(0, requestedPage))), reqId);
    }

    static void requestDetail(final GovFinesPanel p, final String fineId) {
        if (fineId == null || fineId.isEmpty()) {
            return;
        }
        final String reqId = UUID.randomUUID().toString();
        p.pending.put(reqId, RequestKind.DETAIL);
        p.latestDetailReqId = reqId;
        p.detailState = LoadState.LOADING;
        p.detailError = "";
        GovNetwork.sendRequest(OpCode.FINE_INFO, Map.of(FineKeys.KEY_FINE_ID, fineId), reqId);
    }

    static void mutate(final GovFinesPanel p, final OpCode op, final Map<String, String> payload) {
        if (mutationPending(p)) {
            return;
        }
        final String reqId = UUID.randomUUID().toString();
        p.pending.put(reqId, RequestKind.MUTATE);
        GovNetwork.sendRequest(op, payload, reqId);
    }

    static boolean mutationPending(final GovFinesPanel p) {
        return p.pending.containsValue(RequestKind.MUTATE);
    }


}
