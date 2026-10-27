package com.horizonssmp.gov.client;

import java.util.Map;
import java.util.UUID;

import com.horizonssmp.gov.common.AuditKeys;
import com.horizonssmp.gov.common.BusinessKeys;
import com.horizonssmp.gov.common.GovFields;
import com.horizonssmp.gov.common.OpCode;
import com.horizonssmp.gov.network.GovActionResultPayload;
import com.horizonssmp.gov.network.GovNetwork;

final class GovAuditRequests {
    static void requestListIfNeeded(final GovAuditPanel p) {
        if (p.listState == GovAuditPanel.LoadState.NONE) {
            requestList(p);
        }
    }

    static void requestList(final GovAuditPanel p) {
        p.exportArm = GovAuditPanel.ExportArm.NONE;
        final String reqId = UUID.randomUUID().toString();
        p.pending.put(reqId, GovAuditPanel.RequestKind.LIST);
        p.latestListReqId = reqId;
        p.listState = GovAuditPanel.LoadState.LOADING;
        p.listError = "";
        GovNetwork.sendRequest(OpCode.AUDIT_PAGE, Map.of(
                AuditKeys.KEY_ACTOR, GovAuditUtil.text(p.actorBox),
                AuditKeys.KEY_ACTION, GovAuditUtil.text(p.actionBox),
                AuditKeys.KEY_TARGET, GovAuditUtil.text(p.targetBox),
                AuditKeys.KEY_OUTCOME, p.outcome,
                AuditKeys.KEY_WINDOW, p.window,
                AuditKeys.KEY_PAGE, Integer.toString(p.page)), reqId);
        GovAuditCaches.rebuildHits(p);
    }

    static void requestDetail(final GovAuditPanel p, final long seq) {
        if (seq <= 0L) {
            return;
        }
        final String reqId = UUID.randomUUID().toString();
        p.pending.put(reqId, GovAuditPanel.RequestKind.DETAIL);
        p.latestDetailReqId = reqId;
        p.detailState = GovAuditPanel.LoadState.LOADING;
        p.detailError = "";
        GovNetwork.sendRequest(OpCode.AUDIT_INFO,
                Map.of(AuditKeys.KEY_SEQ, Long.toString(seq)), reqId);
        GovAuditCaches.rebuildHits(p);
    }

    static void requestAdminIfNeeded(final GovAuditPanel p) {
        if (p.adminState == GovAuditPanel.LoadState.NONE) {
            requestAdmin(p);
        }
    }

    static void requestAdmin(final GovAuditPanel p) {
        if (p.adminState == GovAuditPanel.LoadState.LOADING) {
            return;
        }
        p.exportArm = GovAuditPanel.ExportArm.NONE;
        final String reqId = UUID.randomUUID().toString();
        p.pending.put(reqId, GovAuditPanel.RequestKind.STATUS);
        p.latestAdminReqId = reqId;
        p.adminState = GovAuditPanel.LoadState.LOADING;
        p.adminError = "";
        GovNetwork.sendRequest(OpCode.SEASON_STATUS, Map.of(), reqId);
        GovAuditWidgets.syncWidgets(p);
    }

    static void requestHistory(final GovAuditPanel p) {
        final String reqId = UUID.randomUUID().toString();
        p.pending.put(reqId, GovAuditPanel.RequestKind.HISTORY);
        GovNetwork.sendRequest(OpCode.HISTORY_LIST, Map.of(BusinessKeys.KEY_PAGE, "0"), reqId);
        GovAuditWidgets.syncWidgets(p);
    }

    static void mutate(final GovAuditPanel p, final OpCode op, final Map<String, String> payload,
            final GovAuditPanel.RequestKind kind) {
        if (GovAuditCaches.mutationPending(p)) {
            return;
        }
        final String reqId = UUID.randomUUID().toString();
        p.pending.put(reqId, kind);
        p.latestAdminReqId = reqId;
        GovNetwork.sendRequest(op, payload, reqId);
    }

    static void onActionResult(final GovAuditPanel p, final GovActionResultPayload payload) {
        final GovAuditPanel.RequestKind kind = p.pending.remove(payload.reqId());
        if (kind == null) {
            return;
        }
        switch (kind) {
            case LIST -> {
                if (!payload.reqId().equals(p.latestListReqId)) {
                    return;
                }
                if (payload.ok()) {
                    GovAuditApply.applyList(p, payload.delta());
                } else {
                    p.listState = GovAuditPanel.LoadState.ERROR;
                    p.listError = GovAuditApply.fallback(payload, "gui.gov.audit.error");
                }
            }
            case DETAIL -> {
                if (!payload.reqId().equals(p.latestDetailReqId)) {
                    return;
                }
                if (payload.ok()) {
                    GovAuditApply.applyDetail(p, payload.delta());
                } else {
                    p.detailState = GovAuditPanel.LoadState.ERROR;
                    p.detailError = GovAuditApply.fallback(payload, "gui.gov.audit.detail_error");
                }
            }
            case EXPORT -> {
                if (payload.ok()) {
                    final Map<String, String> fields = GovFields.decode(payload.delta());
                    p.status = tr("gui.gov.audit.export_done",
                            GovAuditUtil.value(fields, AuditKeys.EXPORT_PATH),
                            GovAuditUtil.value(fields, AuditKeys.EXPORT_BYTES),
                            GovAuditUtil.value(fields, AuditKeys.EXPORT_SHA256));
                    p.statusIsError = false;
                    p.page = 0;
                    requestList(p);
                } else {
                    p.status = GovAuditApply.fallback(payload, "gui.gov.audit.export_error");
                    p.statusIsError = true;
                }
            }
            case ADMIN_EXPORT -> {
                if (payload.ok()) {
                    final Map<String, String> fields = GovFields.decode(payload.delta());
                    p.status = tr("gui.gov.audit.export_done",
                            GovAuditUtil.value(fields, AuditKeys.EXPORT_PATH),
                            GovAuditUtil.value(fields, AuditKeys.EXPORT_BYTES),
                            GovAuditUtil.value(fields, AuditKeys.EXPORT_SHA256));
                    p.statusIsError = false;
                } else {
                    p.status = GovAuditApply.fallback(payload, "gui.gov.audit.export_error");
                    p.statusIsError = true;
                }
            }
            case STATUS -> {
                if (!payload.reqId().equals(p.latestAdminReqId)) {
                    return;
                }
                if (payload.ok()) {
                    GovAuditApply.applyAdmin(p, payload.delta());
                } else {
                    p.adminState = GovAuditPanel.LoadState.ERROR;
                    p.adminError = GovAuditApply.fallback(payload, "gui.gov.audit.admin_error");
                    p.status = p.adminError;
                    p.statusIsError = true;
                }
            }
            case BEGIN -> {
                if (payload.ok()) {
                    final Map<String, String> fields = GovFields.decode(payload.delta());
                    p.admin.putAll(fields);
                    p.adminState = GovAuditPanel.LoadState.READY;
                    final String token = fields.getOrDefault(AuditKeys.SEASON_TOKEN, "");
                    if (p.tokenBox != null) {
                        p.tokenBox.setValue(token);
                    }
                    p.status = tr("gui.gov.audit.begin_done",
                            GovAuditUtil.value(fields, AuditKeys.SEASON_COUNTS_TEXT),
                            GovAuditUtil.shortTime(GovAuditUtil.longField(fields, AuditKeys.SEASON_EXPIRES_AT)));
                    p.statusIsError = false;
                } else {
                    p.status = GovAuditApply.fallback(payload, "gui.gov.audit.admin_error");
                    p.statusIsError = true;
                }
                p.commitArmed = false;
                requestAdmin(p);
            }
            case COMMIT -> {
                p.commitArmed = false;
                if (payload.ok()) {
                    final Map<String, String> fields = GovFields.decode(payload.delta());
                    p.admin.putAll(fields);
                    p.adminState = GovAuditPanel.LoadState.READY;
                    p.status = tr("gui.gov.audit.commit_done",
                            GovAuditUtil.value(fields, AuditKeys.SEASON_COUNTS_TEXT),
                            GovAuditUtil.value(fields, AuditKeys.SEASON_BACKUP_PATH),
                            GovAuditUtil.value(fields, AuditKeys.SEASON_BACKUP_SHA256));
                    p.statusIsError = false;
                    p.detail.clear();
                    p.selectedSeq = -1L;
                    p.page = 0;
                    p.listState = GovAuditPanel.LoadState.NONE;
                    requestList(p);
                    requestAdmin(p);
                } else {
                    p.status = GovAuditApply.fallback(payload, "gui.gov.audit.admin_error");
                    p.statusIsError = true;
                    requestAdmin(p);
                }
            }
            case HISTORY -> {
                if (payload.ok()) {
                    p.historyFields.clear();
                    p.historyFields.putAll(GovFields.decode(payload.delta()));
                    p.status = "History loaded.";
                    p.statusIsError = false;
                } else {
                    p.status = GovAuditApply.fallback(payload, "gui.gov.audit.error");
                    p.statusIsError = true;
                }
            }
        }
        GovAuditCaches.rebuildAllCaches(p);
        GovAuditWidgets.syncWidgets(p);
    }

    static String tr(final String key, final Object... args) {
        return GovAuditPanel.tr(key, args);
    }
}
