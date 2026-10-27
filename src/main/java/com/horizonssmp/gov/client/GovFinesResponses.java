package com.horizonssmp.gov.client;

import com.horizonssmp.gov.client.GovFinesTypes.*;
import static com.horizonssmp.gov.client.GovFinesTypes.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.horizonssmp.gov.common.FineKeys;
import com.horizonssmp.gov.common.GovFields;
import com.horizonssmp.gov.network.GovActionResultPayload;

import net.minecraft.network.chat.Component;

final class GovFinesResponses {

    static void onActionResult(final GovFinesPanel p, final GovActionResultPayload payload) {
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
                    applyList(p, payload.delta());
                } else {
                    p.listState = LoadState.ERROR;
                    p.listError = fallbackError(payload, "gui.gov.fines.error");
                    GovFinesViews.rebuildAll(p);
                }
            }
            case DETAIL -> {
                if (!payload.reqId().equals(p.latestDetailReqId)) {
                    return;
                }
                if (payload.ok()) {
                    applyDetail(p, payload.delta());
                } else {
                    p.detailState = LoadState.ERROR;
                    p.detailError = fallbackError(payload, "gui.gov.fines.detail_error");
                    GovFinesViews.rebuildAll(p);
                }
            }
            case MUTATE -> {
                if (payload.ok()) {
                    GovFinesSupport.setStatus(p, payload.delta().isBlank()
                            ? GovFinesLabels.label(p, "gui.gov.fines.recorded") : payload.delta(), false);
                    GovFinesSupport.disarm(p);
                    GovFinesActions.clearIssueFields(p);
                    GovFinesSupport.syncWidgets(p);
                    GovFinesRequests.requestList(p, p.subtab == TAB_REGISTER ? p.registerFilter
                            : FineKeys.FILTER_UNPAID, p.page);
                    if (!p.selectedId.isEmpty()) {
                        GovFinesRequests.requestDetail(p, p.selectedId);
                    }
                } else {
                    GovFinesSupport.setStatus(p, fallbackError(payload, "gui.gov.fines.error"), true);
                    GovFinesSupport.disarm(p);
                }
                GovFinesViews.rebuildAll(p);
            }
        }
    }

    static String fallbackError(final GovActionResultPayload payload, final String key) {
        return payload.errorText().isBlank()
                ? Component.translatable(key).getString()
                : payload.errorText();
    }

    static void applyList(final GovFinesPanel p, final String delta) {
        final Map<String, String> fields = GovFields.decode(delta);
        p.canIssue = "true".equals(fields.get(FineKeys.CAN_ISSUE));
        p.canViewAll = "true".equals(fields.get(FineKeys.CAN_VIEW_ALL));
        p.canWaive = "true".equals(fields.get(FineKeys.CAN_WAIVE));
        p.canCollect = "true".equals(fields.get(FineKeys.CAN_COLLECT));
        ((GovDashboardScreen) p.host).setSubtabVisibility(GovDashboardScreen.Tab.FINES,
                new boolean[] {true, p.canIssue, p.canCollect, p.canWaive});
        p.viewerUuid = fields.getOrDefault(FineKeys.VIEWER_UUID, "");
        p.receptionConfigured = "true".equals(fields.get(FineKeys.RECEPTION_CONFIGURED));
        p.paymentPoint = fields.getOrDefault(FineKeys.PAYMENT_POINT, "");
        p.total = intField(fields, FineKeys.TOTAL);
        p.page = intField(fields, FineKeys.PAGE);
        p.pages = Math.max(1, intField(fields, FineKeys.PAGES));
        p.listFilter = fields.getOrDefault(FineKeys.FILTER, p.requestedFilter);
        if (p.subtab == TAB_REGISTER) {
            p.registerFilter = p.listFilter;
        }
        p.pageByTab[p.subtab] = p.page;
        p.onlineTotal = intField(fields, FineKeys.ONLINE_COUNT);
        p.online.clear();
        for (int i = 0; i < Math.min(p.onlineTotal, FineKeys.MAX_LISTED_ONLINE); i++) {
            final String name = fields.get(FineKeys.ONLINE_PREFIX + i);
            if (name != null && !name.isBlank()) {
                p.online.add(name);
            }
        }

        p.rows.clear();
        for (int i = 0; i < FineKeys.PAGE_SIZE; i++) {
            final String prefix = FineKeys.ROW_PREFIX + i + ".";
            final String id = fields.get(prefix + FineKeys.ROW_ID);
            if (id == null || id.isBlank()) {
                continue;
            }
            p.rows.add(new FineRow(
                    id,
                    fields.getOrDefault(prefix + FineKeys.ROW_TARGET, id),
                    fields.getOrDefault(prefix + FineKeys.ROW_TARGET_UUID, ""),
                    fields.getOrDefault(prefix + FineKeys.ROW_BAND, ""),
                    longField(fields, prefix + FineKeys.ROW_AMOUNT),
                    longField(fields, prefix + FineKeys.ROW_RESTITUTION),
                    longField(fields, prefix + FineKeys.ROW_DUE),
                    fields.getOrDefault(prefix + FineKeys.ROW_ARTICLE, ""),
                    fields.getOrDefault(prefix + FineKeys.ROW_STATE, ""),
                    fields.getOrDefault(prefix + FineKeys.ROW_REASON, ""),
                    longField(fields, prefix + FineKeys.ROW_CREATED),
                    intField(fields, prefix + FineKeys.ROW_EXILE_DAYS),
                    fields.getOrDefault(prefix + FineKeys.ROW_VICTIM, ""),
                    fields.getOrDefault(prefix + FineKeys.ROW_RECEIPT, "")));
        }
        p.listState = LoadState.READY;
        p.listError = "";

        boolean selectedVisible = false;
        for (int i = 0; i < p.rows.size(); i++) {
            if (p.rows.get(i).id().equals(p.selectedId)) {
                selectedVisible = true;
                break;
            }
        }
        if (!p.selectedId.isEmpty() && !selectedVisible) {
            p.selectedId = "";
            p.detail.clear();
            p.detailState = LoadState.NONE;
            p.detailError = "";
            GovFinesSupport.disarm(p);
        }
        if (p.selectedId.isEmpty() && !p.rows.isEmpty()) {
            GovFinesSelection.selectFine(p, p.rows.get(0).id());
        } else if (selectedVisible) {
            GovFinesRequests.requestDetail(p, p.selectedId);
        }
        if (p.subtab == TAB_ISSUE) {
            GovFinesSelection.ensureIssueTarget(p);
        }
        GovFinesSupport.syncWidgets(p);
        GovFinesViews.rebuildAll(p);
    }

    static void applyDetail(final GovFinesPanel p, final String delta) {
        final Map<String, String> fields = GovFields.decode(delta);
        final String id = fields.get(FineKeys.ID);
        if (id == null || id.isBlank()) {
            p.detailState = LoadState.ERROR;
            p.detailError = Component.translatable("gui.gov.fines.detail_error").getString();
            GovFinesViews.rebuildAll(p);
            return;
        }
        p.selectedId = id;
        p.detail.clear();
        p.detail.putAll(fields);
        p.detailState = LoadState.READY;
        p.detailError = "";
        p.detailScroll = 0;
        GovFinesSupport.disarm(p);
        GovFinesViews.rebuildAll(p);
    }

    static int intField(final Map<String, String> fields, final String key) {
        return com.horizonssmp.gov.common.GovFields.intField(fields, key);
    }

    static long longField(final Map<String, String> fields, final String key) {
        return com.horizonssmp.gov.common.GovFields.longField(fields, key);
    }


}
