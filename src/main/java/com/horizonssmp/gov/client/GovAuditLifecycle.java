package com.horizonssmp.gov.client;

final class GovAuditLifecycle {
    static void setActive(final GovAuditPanel p, final boolean active) {
        p.active = active;
        if (!active) {
            p.commitArmed = false;
            p.exportArm = GovAuditPanel.ExportArm.NONE;
            p.enterRepeat.reset();
            GovAuditWidgets.clearWidgetFocus(p);
        } else if (p.mode == GovAuditPanel.Mode.AUDIT) {
            GovAuditRequests.requestListIfNeeded(p);
        } else {
            GovAuditRequests.requestAdminIfNeeded(p);
        }
        GovAuditWidgets.syncWidgets(p);
        GovAuditCaches.rebuildHits(p);
    }

    static void reset(final GovAuditPanel p) {
        p.pending.clear();
        p.latestListReqId = "";
        p.latestDetailReqId = "";
        p.latestAdminReqId = "";
        p.status = "";
        p.statusIsError = false;
        p.commitArmed = false;
        p.exportArm = GovAuditPanel.ExportArm.NONE;
        p.listScroll = 0;
        p.detailScroll = 0;
        GovAuditWidgets.clearWidgetFocus(p);
        GovAuditCaches.rebuildHits(p);
    }

    static void setSubtab(final GovAuditPanel p, final int index) {
        final GovAuditPanel.Mode next = index == GovAuditPanel.SUBTAB_ADMIN ? GovAuditPanel.Mode.ADMIN : GovAuditPanel.Mode.AUDIT;
        if (next == p.mode) {
            return;
        }
        p.mode = next;
        p.status = "";
        p.statusIsError = false;
        p.commitArmed = false;
        p.exportArm = GovAuditPanel.ExportArm.NONE;
        if (next == GovAuditPanel.Mode.AUDIT) {
            GovAuditRequests.requestListIfNeeded(p);
        } else {
            GovAuditRequests.requestAdminIfNeeded(p);
        }
        GovAuditWidgets.syncWidgets(p);
        GovAuditCaches.rebuildAllCaches(p);
    }
}