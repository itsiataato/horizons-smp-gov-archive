package com.horizonssmp.gov.client;

import java.util.Map;

import com.horizonssmp.gov.common.AuditKeys;
import com.horizonssmp.gov.common.GovExportSection;
import com.horizonssmp.gov.common.OpCode;

final class GovAuditSelection {
    static void selectRow(final GovAuditPanel p, final long seq) {
        if (seq <= 0L || (seq == p.selectedSeq && p.detailState == GovAuditPanel.LoadState.READY)) {
            return;
        }
        p.selectedSeq = seq;
        p.status = "";
        p.statusIsError = false;
        p.commitArmed = false;
        p.exportArm = GovAuditPanel.ExportArm.NONE;
        p.detailState = GovAuditPanel.LoadState.LOADING;
        p.detail.clear();
        p.detailScroll = 0;
        GovAuditRequests.requestDetail(p, seq);
        GovAuditCaches.rebuildAllCaches(p);
        GovAuditWidgets.syncWidgets(p);
    }

    static void cycleOutcome(final GovAuditPanel p) {
        p.status = "";
        p.statusIsError = false;
        final int index = Math.max(0, AuditKeys.OUTCOMES.indexOf(p.outcome));
        p.outcome = AuditKeys.OUTCOMES.get((index + 1) % AuditKeys.OUTCOMES.size());
        p.page = 0;
        p.commitArmed = false;
        p.exportArm = GovAuditPanel.ExportArm.NONE;
        GovAuditLabels.updateOutcomeLabel(p);
        GovAuditRequests.requestList(p);
    }

    static void cycleWindow(final GovAuditPanel p) {
        p.status = "";
        p.statusIsError = false;
        final int index = Math.max(0, AuditKeys.WINDOWS.indexOf(p.window));
        p.window = AuditKeys.WINDOWS.get((index + 1) % AuditKeys.WINDOWS.size());
        p.page = 0;
        p.commitArmed = false;
        p.exportArm = GovAuditPanel.ExportArm.NONE;
        GovAuditLabels.updateWindowLabel(p);
        GovAuditRequests.requestList(p);
    }

    static void cycleSection(final GovAuditPanel p, final int delta) {
        p.status = "";
        p.statusIsError = false;
        p.commitArmed = false;
        p.exportArm = GovAuditPanel.ExportArm.NONE;
        final int count = GovExportSection.values().length;
        p.exportSectionIndex =
                Math.floorMod(p.exportSectionIndex + delta, count);
        GovAuditLabels.updateSectionText(p);
        GovAuditCaches.rebuildHits(p);
    }

    static GovExportSection selectedSection(final GovAuditPanel p) {
        return GovAuditLabels.selectedSection(p);
    }

    static void handleCommit(final GovAuditPanel p, final boolean allowArm) {
        if (GovAuditCaches.mutationPending(p)) {
            return;
        }
        final String token = p.tokenBox == null ? "" : p.tokenBox.getValue().strip();
        if (token.isEmpty()) {
            p.status = p.tokenRequiredText;
            p.statusIsError = true;
            p.commitArmed = false;
            return;
        }
        final long now = System.currentTimeMillis();
        final long elapsed = now - p.commitArmedAt;
        if (p.commitArmed && elapsed < GovAuditPanel.ARM_FLOOR_MS) {
            return;
        }
        if (p.commitArmed && elapsed <= GovAuditPanel.ARM_MS) {
            p.commitArmed = false;
            GovAuditRequests.mutate(p, OpCode.SEASON_COMMIT, Map.of(AuditKeys.KEY_TOKEN, token),
                    GovAuditPanel.RequestKind.COMMIT);
        } else {
            p.commitArmed = false;
            if (!allowArm) {
                p.status = "";
                p.statusIsError = false;
                return;
            }
            p.commitArmed = true;
            p.commitArmedAt = now;
            final String tail = token.length() <= 4 ? token
                    : token.substring(token.length() - 4);
            final String sections = GovAuditUtil.value(p.admin, AuditKeys.SEASON_SECTIONS);
            final String scope = sections.isEmpty()
                    ? GovAuditPanel.upper(tr("gui.gov.audit.admin.counts",
                            GovAuditUtil.value(p.admin, AuditKeys.SEASON_COUNTS_TEXT)))
                    : GovAuditPanel.upper(tr("gui.gov.audit.admin.sections", sections));
            p.status = p.confirmCommitText + " - " + p.commitContextPrefix + "..." + tail
                    + " - " + scope;
            p.statusIsError = false;
        }
    }

    static void handleExport(final GovAuditPanel p, final GovAuditPanel.ExportArm target) {
        if (target == GovAuditPanel.ExportArm.NONE || GovAuditCaches.mutationPending(p)) {
            return;
        }
        final long now = System.currentTimeMillis();
        if (p.exportArm == target) {
            final long elapsed = now - p.exportArmedAt;
            if (elapsed < GovAuditPanel.ARM_FLOOR_MS) {
                return;
            }
            if (elapsed <= GovAuditPanel.EXPORT_ARM_MS) {
                p.exportArm = GovAuditPanel.ExportArm.NONE;
                p.status = "";
                p.statusIsError = false;
                if (target == GovAuditPanel.ExportArm.AUDIT) {
                    GovAuditRequests.mutate(p, OpCode.AUDIT_EXPORT, Map.of(), GovAuditPanel.RequestKind.EXPORT);
                } else {
                    GovAuditRequests.mutate(p, OpCode.ADMIN_EXPORT,
                            Map.of(AuditKeys.KEY_SECTION, selectedSection(p).id()),
                            GovAuditPanel.RequestKind.ADMIN_EXPORT);
                }
                return;
            }
        }
        p.exportArm = target;
        p.exportArmedAt = now;
        p.status = p.confirmExportText + " - " + exportScopeLabel(p, target);
        p.statusIsError = false;
    }

    static String exportScopeLabel(final GovAuditPanel p, final GovAuditPanel.ExportArm target) {
        return target == GovAuditPanel.ExportArm.AUDIT ? GovAuditPanel.upper(tr("gui.gov.audit.mode.audit"))
                : p.sectionText;
    }

    static String tr(final String key, final Object... args) {
        return GovAuditPanel.tr(key, args);
    }
}