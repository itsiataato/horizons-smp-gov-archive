package com.horizonssmp.gov.client;

import net.minecraft.network.chat.Component;

import com.horizonssmp.gov.client.GovJailTypes.Click;
import com.horizonssmp.gov.client.GovJailTypes.JailRow;
import com.horizonssmp.gov.common.JailKeys;
import com.horizonssmp.gov.common.OpCode;

/**
 * TASK-037 button matrix for the jail tab: every {@link Click} value through the real
 * {@code handleClick} dispatch (ROW via the real row-hit handler {@code selectRow}, which
 * is what {@code mouseClicked} calls). Armed actions are driven arm-then-fire with a
 * backdated stamp, plus explicit cancel paths. Runs via {@code main()}.
 */
final class ButtonMatrixJailTest {
    private ButtonMatrixJailTest() {
    }

    private static GovThemedEditBox box(final String value) {
        final GovThemedEditBox b = new GovThemedEditBox(null, 0, 0, 50, 9, Component.empty());
        b.setValue(value);
        return b;
    }

    private static JailRow row(final String id, final String basis) {
        return new JailRow(id, "Alex", "", basis, "Basis", "Sam", 0L, 0L,
                "", false, "ACTIVE", "Held", "", true, "Alex", "held", false, 0, "");
    }

    /** Fully staged panel: selection, detail target, every submit box, pager mid-range. */
    private static GovJailPanel fresh() {
        final GovJailPanel p = new GovJailPanel(null);
        p.active = true;
        p.subtab = 0;
        p.rows.add(row("d-1", "consent"));
        p.rows.add(row("d-2", "warrant"));
        p.activeRows.add(p.rows.get(0));
        p.activeRows.add(p.rows.get(1));
        p.selectedId = "d-1";
        p.detail.put(JailKeys.TARGET_UUID, "uuid-alex");
        p.detainTargetBox = box("Alex");
        p.detainReasonBox = box("Contraband");
        p.warrantBox = box("");
        p.detainMinutesBox = box("");
        p.detainBasis = "violent";
        p.detainConsent = false;
        p.releaseReasonBox = box("Served");
        p.extendMinutesBox = box("30");
        p.extendReasonBox = box("Hearing");
        p.cellNameBox = box("Cell A");
        p.cellXBox = box("10");
        p.cellYBox = box("64");
        p.cellZBox = box("-3");
        p.cellDimBox = box("minecraft:overworld");
        p.cellDeleteBox = box("Cell A");
        p.page = 1;
        p.pages = 3;
        p.pending.clear();
        return p;
    }

    /** First click arms, backdated stamp passes the 600ms floor, second click fires. */
    private static void armFire(final GovJailPanel p, final Click click) {
        p.pending.clear();
        p.handleClick(click);
        p.armedAt = System.currentTimeMillis() - 1000L;
        p.pending.clear();
        p.handleClick(click);
    }

    public static void main(final String[] args) {
        ButtonMatrix.install();
        try {
            GovJailPanel p = fresh();
            ButtonMatrix.fire("JAIL", "FILTER", () -> {
                p.pending.clear();
                p.handleClick(Click.FILTER);
            }, OpCode.JAIL_LIST, JailKeys.KEY_FILTER, JailKeys.KEY_PAGE);

            final GovJailPanel pRow = fresh();
            ButtonMatrix.fire("JAIL", "ROW", () -> {
                pRow.pending.clear();
                // Real row-hit handler: mouseClicked calls selectRow(hit.value()).
                pRow.selectRow("d-2");
            }, OpCode.JAIL_INFO, JailKeys.KEY_DETENTION_ID);

            final GovJailPanel pPrev = fresh();
            ButtonMatrix.fire("JAIL", "PREV", () -> {
                pPrev.pending.clear();
                pPrev.handleClick(Click.PREV);
            }, OpCode.JAIL_LIST, JailKeys.KEY_PAGE);

            final GovJailPanel pNext = fresh();
            pNext.page = 0;
            ButtonMatrix.fire("JAIL", "NEXT", () -> {
                pNext.pending.clear();
                pNext.handleClick(Click.NEXT);
            }, OpCode.JAIL_LIST, JailKeys.KEY_PAGE);

            final GovJailPanel pRefresh = fresh();
            ButtonMatrix.fire("JAIL", "REFRESH", () -> {
                pRefresh.pending.clear();
                pRefresh.handleClick(Click.REFRESH);
            }, OpCode.JAIL_LIST, JailKeys.KEY_FILTER);

            final GovJailPanel pRetryList = fresh();
            ButtonMatrix.fire("JAIL", "RETRY_LIST", () -> {
                pRetryList.pending.clear();
                pRetryList.handleClick(Click.RETRY_LIST);
            }, OpCode.JAIL_LIST, JailKeys.KEY_FILTER);

            final GovJailPanel pRetryDetail = fresh();
            ButtonMatrix.fire("JAIL", "RETRY_DETAIL", () -> {
                pRetryDetail.pending.clear();
                pRetryDetail.handleClick(Click.RETRY_DETAIL);
            }, OpCode.JAIL_INFO, JailKeys.KEY_DETENTION_ID);

            final GovJailPanel pBasis = fresh();
            // Headed-only: BASIS cycles state, then syncWidgets reads host focus (null headless).
            ButtonMatrix.fireBlock("JAIL", "BASIS", () -> {
                pBasis.pending.clear();
                pBasis.handleClick(Click.BASIS);
            }, "syncWidgets reads host focus");

            final GovJailPanel pConsent = fresh();
            final boolean[] beforeConsent = {pConsent.detainConsent};
            ButtonMatrix.fireNone("JAIL", "CONSENT", () -> pConsent.handleClick(Click.CONSENT),
                    () -> ButtonMatrix.check(pConsent.detainConsent != beforeConsent[0],
                            "consent did not toggle"));

            final GovJailPanel pDetain = fresh();
            ButtonMatrix.fire("JAIL", "DETAIN", () -> armFire(pDetain, Click.DETAIN),
                    OpCode.JAIL_COMMIT, JailKeys.KEY_TARGET, JailKeys.KEY_BASIS,
                    JailKeys.KEY_REASON, JailKeys.KEY_CONSENT_CONFIRMED);

            // Cancel path: arming RELEASE then DETAIN drops the release arm; the
            // backdated confirm fires the detain, never the cancelled release.
            final GovJailPanel pCancel = fresh();
            ButtonMatrix.fire("JAIL", "DETAIN_CANCELS_RELEASE", () -> {
                pCancel.pending.clear();
                pCancel.handleClick(Click.RELEASE);
                ButtonMatrix.check(pCancel.releaseArmed, "release did not arm");
                pCancel.handleClick(Click.DETAIN);
                ButtonMatrix.check(!pCancel.releaseArmed, "detain did not cancel release arm");
                pCancel.armedAt = System.currentTimeMillis() - 1000L;
                pCancel.pending.clear();
                pCancel.handleClick(Click.DETAIN);
            }, OpCode.JAIL_COMMIT, JailKeys.KEY_TARGET);

            final GovJailPanel pRelease = fresh();
            ButtonMatrix.fire("JAIL", "RELEASE", () -> armFire(pRelease, Click.RELEASE),
                    OpCode.JAIL_RELEASE, JailKeys.KEY_TARGET, JailKeys.KEY_REASON);

            final GovJailPanel pExtend = fresh();
            pExtend.selectedId = "d-2";
            ButtonMatrix.fire("JAIL", "EXTEND", () -> armFire(pExtend, Click.EXTEND),
                    OpCode.JAIL_EXTEND, JailKeys.KEY_TARGET, JailKeys.KEY_MINUTES,
                    JailKeys.KEY_REASON);

            final GovJailPanel pCuff = fresh();
            ButtonMatrix.fire("JAIL", "CUFF", () -> {
                pCuff.pending.clear();
                pCuff.handleClick(Click.CUFF);
            }, OpCode.CUFF, JailKeys.KEY_TARGET);

            final GovJailPanel pUncuff = fresh();
            ButtonMatrix.fire("JAIL", "UNCUFF", () -> {
                pUncuff.pending.clear();
                pUncuff.handleClick(Click.UNCUFF);
            }, OpCode.UNCUFF, JailKeys.KEY_TARGET);

            // CELL opens the prompt, which needs a real Font: headless it is a
            // dispatch-exercised no-op. CONFIRM/CANCEL run against a staged prompt.
            final GovJailPanel pCell = fresh();
            ButtonMatrix.fireNone("JAIL", "CELL", () -> {
                pCell.pending.clear();
                pCell.handleClick(Click.CELL);
            }, () -> ButtonMatrix.check(!pCell.cellPromptOpen,
                    "prompt must stay closed without a font headless"));

            final GovJailPanel pConfirm = fresh();
            pConfirm.cellPromptOpen = true;
            pConfirm.cellPromptMax = 20;
            pConfirm.cellPromptBox = box("3");
            ButtonMatrix.fire("JAIL", "CELL_CONFIRM", () -> {
                pConfirm.pending.clear();
                pConfirm.handleClick(Click.CELL_CONFIRM);
            }, OpCode.JAIL_CELL, JailKeys.KEY_TARGET, JailKeys.KEY_CELL);

            final GovJailPanel pClose = fresh();
            pClose.cellPromptOpen = true;
            pClose.cellPromptMax = 20;
            pClose.cellPromptBox = box("3");
            ButtonMatrix.fireNone("JAIL", "CELL_CANCEL", () -> {
                pClose.pending.clear();
                pClose.handleClick(Click.CELL_CANCEL);
            }, () -> ButtonMatrix.check(!pClose.cellPromptOpen, "prompt did not close"));

            final GovJailPanel pHere = fresh();
            // Headed-only: CELL_HERE fills coords from the live client player position.
            ButtonMatrix.fireBlock("JAIL", "CELL_HERE", () -> {
                pHere.pending.clear();
                pHere.handleClick(Click.CELL_HERE);
            }, "reads Minecraft.getInstance().player");

            final GovJailPanel pCreate = fresh();
            ButtonMatrix.fire("JAIL", "CELL_CREATE", () -> {
                pCreate.pending.clear();
                pCreate.handleClick(Click.CELL_CREATE);
            }, OpCode.JAIL_CELL_CREATE, JailKeys.KEY_CELL_NAME, JailKeys.KEY_CELL_X,
                    JailKeys.KEY_CELL_DIMENSION);

            final GovJailPanel pDelete = fresh();
            ButtonMatrix.fire("JAIL", "CELL_DELETE", () -> {
                pDelete.pending.clear();
                pDelete.handleClick(Click.CELL_DELETE);
                ButtonMatrix.check(pDelete.cellDeleteArmed, "delete did not arm");
                pDelete.cellDeleteArmedAt = System.currentTimeMillis() - 1000L;
                pDelete.pending.clear();
                pDelete.handleClick(Click.CELL_DELETE);
            }, OpCode.JAIL_CELL_DELETE, JailKeys.KEY_CELL_NAME);

            final GovJailPanel pRowDelete = fresh();
            pRowDelete.cellDeleteBox = box("Cell B");
            ButtonMatrix.fire("JAIL", "CELL_DELETE_ROW", () -> {
                pRowDelete.pending.clear();
                pRowDelete.submitCellDeleteRow("Cell B");
                ButtonMatrix.check(pRowDelete.cellDeleteArmed, "row delete did not arm");
                pRowDelete.cellDeleteArmedAt = System.currentTimeMillis() - 1000L;
                pRowDelete.pending.clear();
                pRowDelete.submitCellDeleteRow("Cell B");
            }, OpCode.JAIL_CELL_DELETE, JailKeys.KEY_CELL_NAME);

            final GovJailPanel pNone = fresh();
            final String[] beforeSel = {pNone.selectedId};
            ButtonMatrix.fireNone("JAIL", "NONE", () -> {
                pNone.pending.clear();
                pNone.handleClick(Click.NONE);
            }, () -> ButtonMatrix.check(pNone.selectedId.equals(beforeSel[0]),
                    "NONE must leave selection alone"));
        } finally {
            ButtonMatrix.uninstall();
        }
        ButtonMatrix.summary("JAIL");
    }
}
