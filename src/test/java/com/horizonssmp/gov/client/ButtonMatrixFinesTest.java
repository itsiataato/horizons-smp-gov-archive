package com.horizonssmp.gov.client;

import java.util.List;

import net.minecraft.network.chat.Component;

import com.horizonssmp.gov.client.GovFinesTypes.Button;
import com.horizonssmp.gov.client.GovFinesTypes.Click;
import com.horizonssmp.gov.client.GovFinesTypes.FineRow;
import com.horizonssmp.gov.client.GovFinesTypes.Hit;
import com.horizonssmp.gov.common.FineKeys;
import com.horizonssmp.gov.common.OpCode;

/**
 * TASK-037 button matrix for the fines tab: every {@link Click} through the real
 * {@code GovFinesInput.handleClick}/{@code handleHit} dispatch, including the armed
 * waive arm-then-fire and its cancel path. Runs via {@code main()}.
 */
final class ButtonMatrixFinesTest {
    private ButtonMatrixFinesTest() {
    }

    private static GovThemedEditBox box(final String value) {
        final GovThemedEditBox b = new GovThemedEditBox(null, 0, 0, 60, 9, Component.empty());
        b.setValue(value);
        return b;
    }

    private static Button button(final Click click, final String value) {
        return new Button(new GovRect(0, 0, 10, 10), "L", "", click, value, true, false);
    }

    private static FineRow fine(final String id) {
        return new FineRow(id, "Alex", "uuid-alex", "STANDARD", 100L, 0L, 0L,
                "9.11", "UNPAID", "Theft", 0L, 0, "", "");
    }

    private static GovFinesPanel fresh() {
        final GovFinesPanel p = new GovFinesPanel(null);
        p.active = true;
        p.canIssue = true;
        p.online.add("Alex");
        p.online.add("Sam");
        p.issueTarget = "Alex";
        p.issueTargetIndex = 0;
        p.issueBand = "STANDARD";
        p.issueReasonBox = box("Theft");
        p.restitutionBox = box("");
        p.exileBox = box("");
        p.articleBox = box("9.11");
        p.victimBox = box("");
        p.waiveReasonBox = box("Mercy");
        p.rows.add(fine("f-1"));
        p.rows.add(fine("f-2"));
        p.selectedId = "f-1";
        p.detail.put(FineKeys.TARGET_UUID, "uuid-alex");
        p.registerFilter = FineKeys.FILTER_ALL;
        p.page = 1;
        p.pages = 3;
        p.pending.clear();
        return p;
    }

    private static void click(final GovFinesPanel p, final Click click, final String value) {
        p.pending.clear();
        GovFinesInput.handleClick(p, button(click, value));
    }

    public static void main(final String[] args) {
        ButtonMatrix.install();
        try {
            final GovFinesPanel pFilter = fresh();
            ButtonMatrix.fire("FINES", "FILTER", () -> click(pFilter, Click.FILTER, "UNPAID"),
                    OpCode.FINE_LIST, FineKeys.KEY_FILTER, FineKeys.KEY_PAGE);

            final GovFinesPanel pRow = fresh();
            ButtonMatrix.fire("FINES", "ROW", () -> {
                pRow.pending.clear();
                GovFinesInput.handleHit(pRow,
                        new Hit(new GovRect(0, 0, 10, 10), Click.ROW, "f-2"));
            }, OpCode.FINE_INFO, FineKeys.KEY_FINE_ID);

            final GovFinesPanel pPage = fresh();
            ButtonMatrix.fire("FINES", "PAGE", () -> click(pPage, Click.PAGE, "-1"),
                    OpCode.FINE_LIST, FineKeys.KEY_PAGE);

            final GovFinesPanel pRefresh = fresh();
            ButtonMatrix.fire("FINES", "REFRESH", () -> click(pRefresh, Click.REFRESH, ""),
                    OpCode.FINE_LIST, FineKeys.KEY_FILTER);

            final GovFinesPanel pRetryList = fresh();
            ButtonMatrix.fire("FINES", "RETRY_LIST", () -> click(pRetryList, Click.RETRY_LIST, ""),
                    OpCode.FINE_LIST, FineKeys.KEY_FILTER);

            final GovFinesPanel pRetryDetail = fresh();
            ButtonMatrix.fire("FINES", "RETRY_DETAIL",
                    () -> click(pRetryDetail, Click.RETRY_DETAIL, ""),
                    OpCode.FINE_INFO, FineKeys.KEY_FINE_ID);

            final GovFinesPanel pIssue = fresh();
            ButtonMatrix.fire("FINES", "ISSUE_SUBMIT",
                    () -> click(pIssue, Click.ISSUE_SUBMIT, ""),
                    OpCode.FINE_ISSUE, "target", FineKeys.KEY_BAND, FineKeys.KEY_REASON);

            // BAND is local-only (sets issueBand + resyncs widgets). Widget sync
            // needs a live host for focus checks, so this panel stages null boxes
            // (widgets not yet created): setVisible skips nulls, rebuildAll
            // early-returns on null font, and the band change itself is proven.
            final GovFinesPanel pBand = fresh();
            pBand.issueReasonBox = null;
            pBand.restitutionBox = null;
            pBand.exileBox = null;
            pBand.articleBox = null;
            pBand.victimBox = null;
            pBand.waiveReasonBox = null;
            ButtonMatrix.fireNone("FINES", "BAND", () -> click(pBand, Click.BAND, "AGGRAVATED"),
                    () -> ButtonMatrix.check("AGGRAVATED".equals(pBand.issueBand),
                            "band did not change"));

            final GovFinesPanel pOnlinePrev = fresh();
            ButtonMatrix.fireNone("FINES", "ONLINE_PREV",
                    () -> click(pOnlinePrev, Click.ONLINE_PREV, ""),
                    () -> ButtonMatrix.check("Sam".equals(pOnlinePrev.issueTarget),
                            "issue target did not cycle, got " + pOnlinePrev.issueTarget));

            final GovFinesPanel pOnlineNext = fresh();
            pOnlineNext.issueTarget = "Sam";
            ButtonMatrix.fireNone("FINES", "ONLINE_NEXT",
                    () -> click(pOnlineNext, Click.ONLINE_NEXT, ""),
                    () -> ButtonMatrix.check("Alex".equals(pOnlineNext.issueTarget),
                            "issue target did not cycle forward"));

            final GovFinesPanel pCollect = fresh();
            ButtonMatrix.fire("FINES", "COLLECT", () -> click(pCollect, Click.COLLECT, ""),
                    OpCode.FINE_COLLECT, "target", FineKeys.KEY_FINE_ID);

            final GovFinesPanel pPay = fresh();
            ButtonMatrix.fire("FINES", "PAY", () -> click(pPay, Click.PAY, ""),
                    OpCode.FINE_PAY, "target", FineKeys.KEY_FINE_ID);

            final GovFinesPanel pWaive = fresh();
            ButtonMatrix.fire("FINES", "WAIVE_SUBMIT", () -> {
                pWaive.pending.clear();
                GovFinesInput.handleClick(pWaive, button(Click.WAIVE_SUBMIT, ""));
                ButtonMatrix.check(pWaive.armedClick == Click.WAIVE_SUBMIT,
                        "waive did not arm");
                pWaive.armedAt = System.currentTimeMillis() - 1000L;
                pWaive.pending.clear();
                GovFinesInput.handleClick(pWaive, button(Click.WAIVE_SUBMIT, ""));
            }, OpCode.FINE_WAIVE, "target", FineKeys.KEY_FINE_ID, FineKeys.KEY_REASON);

            // Cancel path: any other click disarms, so the backdated confirm re-arms
            // instead of firing the waive.
            final GovFinesPanel pWaiveCancel = fresh();
            ButtonMatrix.fireNone("FINES", "WAIVE_CANCEL", () -> {
                pWaiveCancel.pending.clear();
                GovFinesInput.handleClick(pWaiveCancel, button(Click.WAIVE_SUBMIT, ""));
                ButtonMatrix.check(pWaiveCancel.armedClick == Click.WAIVE_SUBMIT,
                        "waive did not arm");
                GovFinesInput.handleClick(pWaiveCancel, button(Click.REFRESH, ""));
                ButtonMatrix.drain();
                pWaiveCancel.pending.clear();
                GovFinesInput.handleClick(pWaiveCancel, button(Click.WAIVE_SUBMIT, ""));
                ButtonMatrix.check(pWaiveCancel.armedClick == Click.WAIVE_SUBMIT,
                        "cancelled waive must re-arm, not fire");
            }, () -> ButtonMatrix.check(pWaiveCancel.armedClick == Click.WAIVE_SUBMIT,
                    "waive arm lost"));

            // WAIVE_PREV/NEXT cycle the waive selection via selectFine, which
            // reloads detail: FINE_INFO fires (same dispatch as ROW).
            final GovFinesPanel pWaivePrev = fresh();
            ButtonMatrix.fire("FINES", "WAIVE_PREV",
                    () -> click(pWaivePrev, Click.WAIVE_PREV, ""),
                    OpCode.FINE_INFO, FineKeys.KEY_FINE_ID);

            final GovFinesPanel pWaiveNext = fresh();
            ButtonMatrix.fire("FINES", "WAIVE_NEXT",
                    () -> click(pWaiveNext, Click.WAIVE_NEXT, ""),
                    OpCode.FINE_INFO, FineKeys.KEY_FINE_ID);
        } finally {
            ButtonMatrix.uninstall();
        }
        ButtonMatrix.summary("FINES");
    }
}
