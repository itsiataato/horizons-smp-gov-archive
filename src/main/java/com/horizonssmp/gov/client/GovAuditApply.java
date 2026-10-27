package com.horizonssmp.gov.client;

import java.util.Map;

import com.horizonssmp.gov.common.AuditKeys;
import com.horizonssmp.gov.common.GovFields;
import com.horizonssmp.gov.network.GovActionResultPayload;

final class GovAuditApply {
    static void applyList(final GovAuditPanel p, final String delta) {
        final Map<String, String> fields = GovFields.decode(delta);
        p.count = GovAuditUtil.intField(fields, AuditKeys.COUNT, 0);
        p.matching = GovAuditUtil.intField(fields, AuditKeys.MATCHING, 0);
        p.page = GovAuditUtil.intField(fields, AuditKeys.PAGE, 0);
        p.pages = Math.max(1, GovAuditUtil.intField(fields, AuditKeys.PAGES, 1));
        p.oldestAt = GovAuditUtil.longField(fields, AuditKeys.OLDEST_AT);
        p.newestAt = GovAuditUtil.longField(fields, AuditKeys.NEWEST_AT);
        p.retention = GovAuditUtil.intField(fields, AuditKeys.RETENTION, 50_000);
        p.window = fields.getOrDefault(AuditKeys.WINDOW, AuditKeys.DEFAULT_WINDOW);
        p.outcome = fields.getOrDefault(AuditKeys.OUTCOME, AuditKeys.OUTCOME_ALL);
        p.canExport = "true".equals(fields.get(AuditKeys.CAN_EXPORT));
        p.canAdmin = "true".equals(fields.get(AuditKeys.CAN_ADMIN));
        ((GovDashboardScreen) p.host).setSubtabVisibility(GovDashboardScreen.Tab.AUDIT,
                new boolean[] {true, p.canAdmin});
        GovAuditLabels.updateOutcomeLabel(p);
        GovAuditLabels.updateWindowLabel(p);

        p.rows.clear();
        for (int i = 0; i < AuditKeys.PAGE_SIZE; i++) {
            final String prefix = AuditKeys.ROW_PREFIX + i;
            final String rawSeq = fields.get(prefix + AuditKeys.ROW_SEQ);
            if (rawSeq == null || rawSeq.isBlank()) {
                continue;
            }
            p.rows.add(new GovAuditPanel.AuditRow(
                    GovAuditUtil.longField(fields, prefix + AuditKeys.ROW_SEQ),
                    GovAuditUtil.longField(fields, prefix + AuditKeys.ROW_AT),
                    fields.getOrDefault(prefix + AuditKeys.ROW_ACTOR, ""),
                    fields.getOrDefault(prefix + AuditKeys.ROW_ACTION, ""),
                    fields.getOrDefault(prefix + AuditKeys.ROW_TARGET, ""),
                    fields.getOrDefault(prefix + AuditKeys.ROW_OUTCOME, "")));
        }
        p.listState = GovAuditPanel.LoadState.READY;
        p.listError = "";
        p.listScroll = 0;

        boolean selectedVisible = false;
        for (final GovAuditPanel.AuditRow row : p.rows) {
            if (row.seq() == p.selectedSeq) {
                selectedVisible = true;
                break;
            }
        }
        if (!selectedVisible) {
            p.selectedSeq = p.rows.isEmpty() ? -1L : p.rows.get(0).seq();
            p.detail.clear();
            p.detailState = GovAuditPanel.LoadState.NONE;
            p.detailScroll = 0;
        }
        if (p.selectedSeq > 0L) {
            GovAuditRequests.requestDetail(p, p.selectedSeq);
        }
    }

    static void applyDetail(final GovAuditPanel p, final String delta) {
        final Map<String, String> fields = GovFields.decode(delta);
        if (fields.get(AuditKeys.SEQ) == null) {
            p.detailState = GovAuditPanel.LoadState.ERROR;
            p.detailError = tr("gui.gov.audit.detail_error");
            return;
        }
        p.selectedSeq = GovAuditUtil.longField(fields, AuditKeys.SEQ);
        p.detail.clear();
        p.detail.putAll(fields);
        p.detailState = GovAuditPanel.LoadState.READY;
        p.detailError = "";
        p.detailScroll = 0;
    }

    static void applyAdmin(final GovAuditPanel p, final String delta) {
        final Map<String, String> fields = GovFields.decode(delta);
        p.admin.clear();
        p.admin.putAll(fields);
        p.adminState = GovAuditPanel.LoadState.READY;
        p.adminError = "";
        p.canAdmin = "true".equals(fields.get(AuditKeys.CAN_ADMIN)) || p.canAdmin;
    }

    static String fallback(final GovActionResultPayload payload, final String key) {
        return payload.errorText().isBlank() ? tr(key) : payload.errorText();
    }

    static String tr(final String key, final Object... args) {
        return GovAuditPanel.tr(key, args);
    }
}