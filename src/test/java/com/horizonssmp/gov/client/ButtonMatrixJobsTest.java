package com.horizonssmp.gov.client;

import net.minecraft.network.chat.Component;

import com.horizonssmp.gov.client.GovJobsTypes.Click;
import com.horizonssmp.gov.client.GovJobsTypes.Hit;
import com.horizonssmp.gov.client.GovJobsTypes.Modal;
import com.horizonssmp.gov.common.JobKeys;
import com.horizonssmp.gov.common.OpCode;

/**
 * TASK-037 button matrix for the jobs tab: every {@link Click} through the real
 * {@code handleHit} dispatch (ROW via the real {@code selectJob} row handler). Armed
 * actions are driven arm-then-fire with a backdated stamp inside the arm window, plus
 * an explicit cancel path. Modals are staged via the {@code modal} field so
 * {@code closeModal} never touches a null host. Runs via {@code main()}.
 */
final class ButtonMatrixJobsTest {
    private ButtonMatrixJobsTest() {
    }

    private static GovThemedEditBox box(final String value) {
        final GovThemedEditBox b = new GovThemedEditBox(null, 0, 0, 60, 9, Component.empty());
        b.setValue(value);
        return b;
    }

    private static Hit hit(final Click click, final String value) {
        return new Hit(new GovRect(0, 0, 10, 10), click, value);
    }

    private static GovJobsPanel fresh() {
        final GovJobsPanel p = new GovJobsPanel(null);
        p.active = true;
        p.canManage = true;
        p.canAssign = true;
        p.selectedJobId = "job-1";
        p.selectedJobType = JobKeys.TYPE_CUSTOM;
        p.detailState = GovJobsTypes.DetailState.READY;
        p.createBox = box("Night Watch");
        p.jobPage = 1;
        p.jobPages = 3;
        p.pending.clear();
        return p;
    }

    private static void hit(final GovJobsPanel p, final Click click, final String value) {
        p.pending.clear();
        p.handleHit(hit(click, value));
    }

    private static long backdate() {
        return System.currentTimeMillis() - 1000L;
    }

    public static void main(final String[] args) {
        ButtonMatrix.install();
        try {
            final GovJobsPanel pRow = fresh();
            ButtonMatrix.fire("JOBS", "ROW", () -> hit(pRow, Click.ROW, "job-2"),
                    OpCode.JOB_INFO, JobKeys.KEY_JOB);

            final GovJobsPanel pPrev = fresh();
            ButtonMatrix.fireNone("JOBS", "PAGE_PREV", () -> hit(pPrev, Click.PAGE_PREV, ""),
                    () -> ButtonMatrix.check(pPrev.jobPage == 0,
                            "page did not step back, got " + pPrev.jobPage));

            final GovJobsPanel pNext = fresh();
            ButtonMatrix.fireNone("JOBS", "PAGE_NEXT", () -> hit(pNext, Click.PAGE_NEXT, ""),
                    () -> ButtonMatrix.check(pNext.jobPage == 2,
                            "page did not step forward, got " + pNext.jobPage));

            final GovJobsPanel pRefresh = fresh();
            ButtonMatrix.fire("JOBS", "REFRESH", () -> hit(pRefresh, Click.REFRESH, ""),
                    OpCode.JOB_LIST);

            final GovJobsPanel pRetryList = fresh();
            ButtonMatrix.fire("JOBS", "RETRY_LIST", () -> hit(pRetryList, Click.RETRY, "list"),
                    OpCode.JOB_LIST);

            final GovJobsPanel pRetryInfo = fresh();
            ButtonMatrix.fire("JOBS", "RETRY_INFO", () -> hit(pRetryInfo, Click.RETRY, ""),
                    OpCode.JOB_INFO, JobKeys.KEY_JOB);

            final GovJobsPanel pOpen = fresh();
            ButtonMatrix.fireNone("JOBS", "CREATE_OPEN", () -> hit(pOpen, Click.CREATE_OPEN, ""),
                    () -> ButtonMatrix.check(pOpen.modal == Modal.CREATE,
                            "create modal did not open, got " + pOpen.modal));

            final GovJobsPanel pCreate = fresh();
            pCreate.modal = Modal.CREATE;
            ButtonMatrix.fire("JOBS", "CREATE_SUBMIT",
                    () -> hit(pCreate, Click.CREATE_SUBMIT, ""),
                    OpCode.JOB_CREATE, JobKeys.KEY_NAME);

            final GovJobsPanel pCreateCancel = fresh();
            pCreateCancel.modal = Modal.CREATE;
            ButtonMatrix.fireNone("JOBS", "CREATE_CANCEL",
                    () -> hit(pCreateCancel, Click.CREATE_CANCEL, ""),
                    () -> ButtonMatrix.check(pCreateCancel.modal == Modal.NONE,
                            "create modal did not close"));

            final GovJobsPanel pFocus = fresh();
            ButtonMatrix.fireBlock("JOBS", "FOCUS_CREATE",
                    () -> hit(pFocus, Click.FOCUS_CREATE, ""),
                    "host focus needs a live screen");

            final GovJobsPanel pDelete = fresh();
            ButtonMatrix.fire("JOBS", "DELETE", () -> {
                hit(pDelete, Click.DELETE, "");
                ButtonMatrix.check(pDelete.deleteArmedAt > 0, "delete did not arm");
                pDelete.deleteArmedAt = backdate();
                hit(pDelete, Click.DELETE, "");
            }, OpCode.JOB_DELETE, JobKeys.KEY_JOB);

            // Cancel path: REFRESH disarms (resetArmStatus) and reloads, leaving
            // detail LOADING headless (no server reply), so DELETE stays disabled.
            // The cancel is proven by the arm being gone with nothing fired.
            final GovJobsPanel pDeleteCancel = fresh();
            ButtonMatrix.fireNone("JOBS", "DELETE_CANCEL", () -> {
                hit(pDeleteCancel, Click.DELETE, "");
                ButtonMatrix.check(pDeleteCancel.deleteArmedAt > 0, "delete did not arm");
                pDeleteCancel.pending.clear();
                pDeleteCancel.handleHit(hit(Click.REFRESH, ""));
                ButtonMatrix.drain();
                ButtonMatrix.check(pDeleteCancel.deleteArmedAt == 0,
                        "refresh did not disarm the delete");
            }, () -> ButtonMatrix.check(pDeleteCancel.deleteArmedAt == 0,
                    "delete arm survived cancel"));

            final GovJobsPanel pPicker = fresh();
            ButtonMatrix.fireNone("JOBS", "MEMBER_ADD_OPEN",
                    () -> hit(pPicker, Click.MEMBER_ADD_OPEN, ""),
                    () -> ButtonMatrix.check(pPicker.modal == Modal.PICKER,
                            "picker did not open, got " + pPicker.modal));

            final GovJobsPanel pRemove = fresh();
            ButtonMatrix.fire("JOBS", "MEMBER_REMOVE", () -> {
                hit(pRemove, Click.MEMBER_REMOVE, "uuid-sam");
                ButtonMatrix.check("uuid-sam".equals(pRemove.memberArmedTarget),
                        "member remove did not arm");
                pRemove.memberArmedAt = backdate();
                hit(pRemove, Click.MEMBER_REMOVE, "uuid-sam");
            }, OpCode.JOB_ASSIGN, "target", JobKeys.KEY_JOB);

            final GovJobsPanel pPick = fresh();
            ButtonMatrix.fire("JOBS", "PICKER_PICK", () -> {
                hit(pPick, Click.PICKER_PICK, "Alex");
                ButtonMatrix.check("Alex".equals(pPick.assignArmedTarget),
                        "assign did not arm");
                pPick.assignArmedAt = backdate();
                hit(pPick, Click.PICKER_PICK, "Alex");
            }, OpCode.JOB_ASSIGN, "target", JobKeys.KEY_JOB);

            final GovJobsPanel pPickerCancel = fresh();
            pPickerCancel.modal = Modal.PICKER;
            ButtonMatrix.fireNone("JOBS", "PICKER_CANCEL",
                    () -> hit(pPickerCancel, Click.PICKER_CANCEL, ""),
                    () -> ButtonMatrix.check(pPickerCancel.modal == Modal.NONE,
                            "picker did not close"));

            final GovJobsPanel pGrant = fresh();
            ButtonMatrix.fire("JOBS", "CAP_GRANT", () -> hit(pGrant, Click.CAP_GRANT, "gov.arrest"),
                    OpCode.JOB_EDIT, JobKeys.KEY_JOB, JobKeys.KEY_CAPABILITY, JobKeys.KEY_MODE);

            final GovJobsPanel pCap = fresh();
            ButtonMatrix.fire("JOBS", "CAP_REMOVE", () -> {
                hit(pCap, Click.CAP_REMOVE, "gov.arrest");
                ButtonMatrix.check("gov.arrest".equals(pCap.capArmedTarget),
                        "cap remove did not arm");
                pCap.capArmedAt = backdate();
                hit(pCap, Click.CAP_REMOVE, "gov.arrest");
            }, OpCode.JOB_EDIT, JobKeys.KEY_JOB, JobKeys.KEY_CAPABILITY, JobKeys.KEY_MODE);

            final GovJobsPanel pClose = fresh();
            pClose.modal = Modal.PICKER;
            ButtonMatrix.fireNone("JOBS", "CLOSE_MODAL",
                    () -> hit(pClose, Click.CLOSE_MODAL, ""),
                    () -> ButtonMatrix.check(pClose.modal == Modal.NONE,
                            "close did not shut the modal"));
        } finally {
            ButtonMatrix.uninstall();
        }
        ButtonMatrix.summary("JOBS");
    }
}
