package com.horizonssmp.gov.client;

import net.minecraft.network.chat.Component;

import com.horizonssmp.gov.common.AuditKeys;
import com.horizonssmp.gov.common.OpCode;

/**
 * TASK-037 button matrix for the audit tab: every {@link GovAuditPanel.Action} through
 * the real {@code GovAuditInput.handle} dispatch. Runs via {@code main()}.
 */
final class ButtonMatrixAuditTest {
    private ButtonMatrixAuditTest() {
    }

    private static GovThemedEditBox box(final String value) {
        final GovThemedEditBox b = new GovThemedEditBox(null, 0, 0, 60, 9, Component.empty());
        b.setValue(value);
        return b;
    }

    private static GovAuditPanel fresh() {
        final GovAuditPanel p = new GovAuditPanel(null);
        p.active = true;
        p.actorBox = box("");
        p.actionBox = box("");
        p.targetBox = box("");
        p.tokenBox = box("tok");
        p.page = 1;
        p.pages = 3;
        p.selectedSeq = 7L;
        p.rowViews.add(new GovAuditPanel.RowView(7L, "l1", "l2", "ok", false));
        p.pending.clear();
        return p;
    }

    private static void hit(final GovAuditPanel p, final GovAuditPanel.Action action,
            final int index, final String value) {
        p.pending.clear();
        GovAuditInput.handle(p, new GovAuditPanel.Hit(new GovRect(0, 0, 1, 1), action, index, value));
    }

    public static void main(final String[] args) {
        ButtonMatrix.install();
        try {
            final GovAuditPanel pApply = fresh();
            ButtonMatrix.fire("AUDIT", "APPLY", () -> hit(pApply, GovAuditPanel.Action.APPLY, 0, ""),
                    OpCode.AUDIT_PAGE);

            final GovAuditPanel pOutcome = fresh();
            ButtonMatrix.fire("AUDIT", "OUTCOME", () -> hit(pOutcome, GovAuditPanel.Action.OUTCOME, 0, ""),
                    OpCode.AUDIT_PAGE);

            final GovAuditPanel pWindow = fresh();
            ButtonMatrix.fire("AUDIT", "WINDOW", () -> hit(pWindow, GovAuditPanel.Action.WINDOW, 0, ""),
                    OpCode.AUDIT_PAGE);

            // Export arms on first click, fires AUDIT_EXPORT on confirm inside the window.
            final GovAuditPanel pExport = fresh();
            ButtonMatrix.fireNone("AUDIT", "EXPORT_AUDIT_ARM",
                    () -> hit(pExport, GovAuditPanel.Action.EXPORT_AUDIT, 0, ""),
                    () -> ButtonMatrix.check(pExport.exportArm == GovAuditPanel.ExportArm.AUDIT,
                            "export did not arm"));
            pExport.exportArmedAt = System.currentTimeMillis() - 1000L;
            ButtonMatrix.fire("AUDIT", "EXPORT_AUDIT_FIRE",
                    () -> hit(pExport, GovAuditPanel.Action.EXPORT_AUDIT, 0, ""),
                    OpCode.AUDIT_EXPORT);

            final GovAuditPanel pRow = fresh();
            ButtonMatrix.fireSeam("AUDIT", "ROW", () -> hit(pRow, GovAuditPanel.Action.ROW, 0, ""),
                    OpCode.AUDIT_INFO, AuditKeys.KEY_SEQ);

            final GovAuditPanel pPrev = fresh();
            ButtonMatrix.fire("AUDIT", "PREV", () -> hit(pPrev, GovAuditPanel.Action.PREV, 0, ""),
                    OpCode.AUDIT_PAGE);

            final GovAuditPanel pNext = fresh();
            pNext.page = 0;
            ButtonMatrix.fire("AUDIT", "NEXT", () -> hit(pNext, GovAuditPanel.Action.NEXT, 0, ""),
                    OpCode.AUDIT_PAGE);

            final GovAuditPanel pRefresh = fresh();
            ButtonMatrix.fire("AUDIT", "REFRESH", () -> hit(pRefresh, GovAuditPanel.Action.REFRESH, 0, ""),
                    OpCode.AUDIT_PAGE);

            final GovAuditPanel pSeason = fresh();
            ButtonMatrix.fireSeam("AUDIT", "SEASON_REFRESH",
                    () -> hit(pSeason, GovAuditPanel.Action.SEASON_REFRESH, 0, ""),
                    OpCode.SEASON_STATUS);

            final GovAuditPanel pBegin = fresh();
            ButtonMatrix.fire("AUDIT", "SEASON_BEGIN",
                    () -> hit(pBegin, GovAuditPanel.Action.SEASON_BEGIN, 0, ""),
                    OpCode.SEASON_BEGIN);

            // Commit arms on first click, fires SEASON_COMMIT on confirm inside the window.
            final GovAuditPanel pCommit = fresh();
            ButtonMatrix.fireNone("AUDIT", "SEASON_COMMIT_ARM",
                    () -> hit(pCommit, GovAuditPanel.Action.SEASON_COMMIT, 0, ""),
                    () -> ButtonMatrix.check(pCommit.commitArmed, "commit did not arm"));
            pCommit.commitArmedAt = System.currentTimeMillis() - 1000L;
            ButtonMatrix.fire("AUDIT", "SEASON_COMMIT_FIRE",
                    () -> hit(pCommit, GovAuditPanel.Action.SEASON_COMMIT, 0, ""),
                    OpCode.SEASON_COMMIT, AuditKeys.KEY_TOKEN);

            final GovAuditPanel pSecPrev = fresh();
            ButtonMatrix.fireNone("AUDIT", "SECTION_PREV",
                    () -> hit(pSecPrev, GovAuditPanel.Action.SECTION_PREV, 0, ""),
                    () -> ButtonMatrix.check(pSecPrev.exportSectionIndex >= 0, "section index lost"));

            final GovAuditPanel pSecNext = fresh();
            ButtonMatrix.fireNone("AUDIT", "SECTION_NEXT",
                    () -> hit(pSecNext, GovAuditPanel.Action.SECTION_NEXT, 0, ""),
                    () -> ButtonMatrix.check(pSecNext.exportSectionIndex >= 0, "section index lost"));

            final GovAuditPanel pExportSec = fresh();
            ButtonMatrix.fireNone("AUDIT", "EXPORT_SECTION_ARM",
                    () -> hit(pExportSec, GovAuditPanel.Action.EXPORT_SECTION, 0, ""),
                    () -> ButtonMatrix.check(pExportSec.exportArm == GovAuditPanel.ExportArm.SECTION,
                            "section export did not arm"));
            pExportSec.exportArmedAt = System.currentTimeMillis() - 1000L;
            ButtonMatrix.fire("AUDIT", "EXPORT_SECTION_FIRE",
                    () -> hit(pExportSec, GovAuditPanel.Action.EXPORT_SECTION, 0, ""),
                    OpCode.ADMIN_EXPORT, AuditKeys.KEY_SECTION);

            final GovAuditPanel pRetryL = fresh();
            ButtonMatrix.fire("AUDIT", "RETRY_LIST",
                    () -> hit(pRetryL, GovAuditPanel.Action.RETRY_LIST, 0, ""),
                    OpCode.AUDIT_PAGE);

            final GovAuditPanel pRetryD = fresh();
            ButtonMatrix.fire("AUDIT", "RETRY_DETAIL",
                    () -> hit(pRetryD, GovAuditPanel.Action.RETRY_DETAIL, 0, ""),
                    OpCode.AUDIT_INFO, AuditKeys.KEY_SEQ);
        } finally {
            ButtonMatrix.uninstall();
        }
        ButtonMatrix.summary("AUDIT");
    }
}
