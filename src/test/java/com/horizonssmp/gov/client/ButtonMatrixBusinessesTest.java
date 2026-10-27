package com.horizonssmp.gov.client;

import static com.horizonssmp.gov.client.ButtonMatrixBusinessesSupport.armFire;
import static com.horizonssmp.gov.client.ButtonMatrixBusinessesSupport.fresh;
import static com.horizonssmp.gov.client.ButtonMatrixBusinessesSupport.hit;

import com.horizonssmp.gov.client.GovBusinessesTypes.Click;
import com.horizonssmp.gov.client.GovBusinessesTypes.Modal;
import com.horizonssmp.gov.common.BusinessKeys;
import com.horizonssmp.gov.common.OpCode;

/**
 * TASK-037 button matrix for the businesses tab: every {@link Click} through the real
 * {@code GovBusinessesInput.handleHit} dispatch. Armed submits run arm-then-fire with a
 * backdated stamp. Staging lives in {@link ButtonMatrixBusinessesSupport} (300-line rule).
 * Runs via {@code main()}.
 */
final class ButtonMatrixBusinessesTest {
    private ButtonMatrixBusinessesTest() {
    }

    public static void main(final String[] args) {
        ButtonMatrix.install();
        try {
            final GovBusinessesPanel pFilter = fresh();
            ButtonMatrix.fire("BIZ", "FILTER", () -> hit(pFilter, Click.FILTER, "active"),
                    OpCode.BIZ_LIST, BusinessKeys.KEY_PAGE);

            final GovBusinessesPanel pRow = fresh();
            ButtonMatrix.fire("BIZ", "ROW", () -> hit(pRow, Click.ROW, "b-9"),
                    OpCode.BIZ_INFO, BusinessKeys.KEY_BUSINESS);

            final GovBusinessesPanel pPrev = fresh();
            ButtonMatrix.fire("BIZ", "PAGE_PREV", () -> hit(pPrev, Click.PAGE_PREV, ""),
                    OpCode.BIZ_LIST, BusinessKeys.KEY_PAGE);

            final GovBusinessesPanel pNext = fresh();
            ButtonMatrix.fire("BIZ", "PAGE_NEXT", () -> hit(pNext, Click.PAGE_NEXT, ""),
                    OpCode.BIZ_LIST, BusinessKeys.KEY_PAGE);

            final GovBusinessesPanel pRefresh = fresh();
            ButtonMatrix.fire("BIZ", "REFRESH", () -> hit(pRefresh, Click.REFRESH, ""),
                    OpCode.BIZ_LIST, BusinessKeys.KEY_PAGE);

            final GovBusinessesPanel pRetryL = fresh();
            ButtonMatrix.fire("BIZ", "RETRY_LIST", () -> hit(pRetryL, Click.RETRY, "list"),
                    OpCode.BIZ_LIST, BusinessKeys.KEY_PAGE);

            final GovBusinessesPanel pRetryD = fresh();
            ButtonMatrix.fire("BIZ", "RETRY_DETAIL", () -> hit(pRetryD, Click.RETRY, "detail"),
                    OpCode.BIZ_INFO, BusinessKeys.KEY_BUSINESS);

            final GovBusinessesPanel pCloseM = fresh();
            pCloseM.modal = Modal.SUSPEND;
            ButtonMatrix.fireNoneSeam("BIZ", "CLOSE_MODAL",
                    () -> hit(pCloseM, Click.CLOSE_MODAL, ""),
                    () -> ButtonMatrix.check(pCloseM.modal == Modal.NONE, "modal stuck"),
                    "modal sync needs a live host");

            // Field focus needs a live host: headed-only seam.
            final GovBusinessesPanel pFocus = fresh();
            ButtonMatrix.fireBlock("BIZ", "FOCUS_FIELD",
                    () -> hit(pFocus, Click.FOCUS_FIELD, "NAME"),
                    "field focus needs a live host");

            final GovBusinessesPanel pRegType = fresh();
            final String before = pRegType.registerType;
            ButtonMatrix.fireNone("BIZ", "REGISTER_TYPE",
                    () -> hit(pRegType, Click.REGISTER_TYPE, ""),
                    () -> ButtonMatrix.check(!pRegType.registerType.equals(before)
                            || !pRegType.registerType.isEmpty(), "register type lost"));

            final GovBusinessesPanel pReg = fresh();
            ButtonMatrix.fire("BIZ", "REGISTER_SUBMIT",
                    () -> hit(pReg, Click.REGISTER_SUBMIT, ""),
                    OpCode.BIZ_REGISTER, BusinessKeys.KEY_NAME, BusinessKeys.KEY_TYPE);

            final GovBusinessesPanel pBizPrev = fresh();
            ButtonMatrix.fire("BIZ", "BUSINESS_PREV",
                    () -> hit(pBizPrev, Click.BUSINESS_PREV, ""),
                    OpCode.BIZ_INFO, BusinessKeys.KEY_BUSINESS);

            final GovBusinessesPanel pBizNext = fresh();
            ButtonMatrix.fire("BIZ", "BUSINESS_NEXT",
                    () -> hit(pBizNext, Click.BUSINESS_NEXT, ""),
                    OpCode.BIZ_INFO, BusinessKeys.KEY_BUSINESS);

            final GovBusinessesPanel pTransfer = fresh();
            ButtonMatrix.fire("BIZ", "TRANSFER_SUBMIT",
                    () -> armFire(pTransfer, Click.TRANSFER_SUBMIT),
                    OpCode.BIZ_TRANSFER, BusinessKeys.KEY_BUSINESS);

            final GovBusinessesPanel pSuspOpen = fresh();
            ButtonMatrix.fireNoneSeam("BIZ", "SUSPEND_OPEN",
                    () -> hit(pSuspOpen, Click.SUSPEND_OPEN, ""),
                    () -> ButtonMatrix.check(pSuspOpen.modal == Modal.SUSPEND, "suspend modal shut"),
                    "modal sync needs a live host");

            final GovBusinessesPanel pClose = fresh();
            ButtonMatrix.fire("BIZ", "CLOSE_SUBMIT",
                    () -> armFire(pClose, Click.CLOSE_SUBMIT),
                    OpCode.BIZ_REVOKE, BusinessKeys.KEY_BUSINESS);

            final GovBusinessesPanel pDelete = fresh();
            ButtonMatrix.fire("BIZ", "DELETE_SUBMIT",
                    () -> armFire(pDelete, Click.DELETE_SUBMIT),
                    OpCode.BIZ_DELETE, BusinessKeys.KEY_BUSINESS);

            final GovBusinessesPanel pSuspend = fresh();
            ButtonMatrix.fire("BIZ", "SUSPEND_SUBMIT",
                    () -> armFire(pSuspend, Click.SUSPEND_SUBMIT),
                    OpCode.BIZ_SUSPEND, BusinessKeys.KEY_BUSINESS, BusinessKeys.KEY_REASON);

            final GovBusinessesPanel pSuspCancel = fresh();
            pSuspCancel.modal = Modal.SUSPEND;
            ButtonMatrix.fireNoneSeam("BIZ", "SUSPEND_CANCEL",
                    () -> hit(pSuspCancel, Click.SUSPEND_CANCEL, ""),
                    () -> ButtonMatrix.check(pSuspCancel.modal == Modal.NONE, "modal stuck"),
                    "modal sync needs a live host");

            final GovBusinessesPanel pStaffOpen = fresh();
            ButtonMatrix.fireNoneSeam("BIZ", "STAFF_ADD_OPEN",
                    () -> hit(pStaffOpen, Click.STAFF_ADD_OPEN, ""),
                    () -> ButtonMatrix.check(pStaffOpen.modal == Modal.STAFF_ADD,
                            "staff modal shut"),
                    "modal sync needs a live host");

            final GovBusinessesPanel pLinkOpen = fresh();
            ButtonMatrix.fireNoneSeam("BIZ", "STAFF_LINK_OPEN",
                    () -> hit(pLinkOpen, Click.STAFF_LINK_OPEN, ""),
                    () -> ButtonMatrix.check(pLinkOpen.modal == Modal.LINK, "link modal shut"),
                    "modal sync needs a live host");

            final GovBusinessesPanel pRole = fresh();
            ButtonMatrix.fireNone("BIZ", "STAFF_ROLE",
                    () -> hit(pRole, Click.STAFF_ROLE, ""),
                    () -> ButtonMatrix.check(pRole.employeeRole != null, "role lost"));

            final GovBusinessesPanel pStaffAdd = fresh();
            ButtonMatrix.fire("BIZ", "STAFF_ADD_SUBMIT",
                    () -> hit(pStaffAdd, Click.STAFF_ADD_SUBMIT, ""),
                    OpCode.BIZ_EMPLOYEE_ADD, BusinessKeys.KEY_BUSINESS);

            final GovBusinessesPanel pStaffCancel = fresh();
            pStaffCancel.modal = Modal.STAFF_ADD;
            ButtonMatrix.fireNoneSeam("BIZ", "STAFF_ADD_CANCEL",
                    () -> hit(pStaffCancel, Click.STAFF_ADD_CANCEL, ""),
                    () -> ButtonMatrix.check(pStaffCancel.modal == Modal.NONE, "modal stuck"),
                    "modal sync needs a live host");

            final GovBusinessesPanel pStaffRm = fresh();
            ButtonMatrix.fireNone("BIZ", "STAFF_REMOVE_ARM",
                    () -> hit(pStaffRm, Click.STAFF_REMOVE, "uuid-x"),
                    () -> ButtonMatrix.check("uuid-x".equals(pStaffRm.staffRemoveTarget),
                            "staff remove did not arm"));
            pStaffRm.staffRemoveArmedAt = System.currentTimeMillis() - 1000L;
            ButtonMatrix.fire("BIZ", "STAFF_REMOVE_FIRE",
                    () -> hit(pStaffRm, Click.STAFF_REMOVE, "uuid-x"),
                    OpCode.BIZ_EMPLOYEE_REMOVE, BusinessKeys.KEY_BUSINESS);

            final GovBusinessesPanel pLink = fresh();
            ButtonMatrix.fire("BIZ", "LINK_SUBMIT", () -> hit(pLink, Click.LINK_SUBMIT, ""),
                    OpCode.BIZ_LINK, BusinessKeys.KEY_BUSINESS, BusinessKeys.KEY_JOB);

            final GovBusinessesPanel pUnlink = fresh();
            ButtonMatrix.fire("BIZ", "LINK_UNLINK", () -> hit(pUnlink, Click.LINK_UNLINK, ""),
                    OpCode.BIZ_LINK, BusinessKeys.KEY_BUSINESS);

            final GovBusinessesPanel pLinkCancel = fresh();
            pLinkCancel.modal = Modal.LINK;
            ButtonMatrix.fireNoneSeam("BIZ", "LINK_CANCEL",
                    () -> hit(pLinkCancel, Click.LINK_CANCEL, ""),
                    () -> ButtonMatrix.check(pLinkCancel.modal == Modal.NONE, "modal stuck"),
                    "modal sync needs a live host");

            final GovBusinessesPanel pPaySet = fresh();
            ButtonMatrix.fire("BIZ", "PAYROLL_SET", () -> hit(pPaySet, Click.PAYROLL_SET, ""),
                    OpCode.BIZ_PAYROLL_SET, BusinessKeys.KEY_BUSINESS);

            final GovBusinessesPanel pPayRec = fresh();
            ButtonMatrix.fire("BIZ", "PAYROLL_RECORD",
                    () -> armFire(pPayRec, Click.PAYROLL_RECORD),
                    OpCode.BIZ_PAYROLL_RECORD, BusinessKeys.KEY_BUSINESS);

            final GovBusinessesPanel pRename = fresh();
            ButtonMatrix.fire("BIZ", "RENAME_SUBMIT",
                    () -> hit(pRename, Click.RENAME_SUBMIT, ""),
                    OpCode.BIZ_RENAME, BusinessKeys.KEY_BUSINESS, BusinessKeys.KEY_NAME);

            final GovBusinessesPanel pPayRun = fresh();
            ButtonMatrix.fire("BIZ", "PAYROLL_RUN_SUBMIT",
                    () -> hit(pPayRun, Click.PAYROLL_RUN_SUBMIT, ""),
                    OpCode.BIZ_PAYROLL_RUN, BusinessKeys.KEY_BUSINESS);

            final GovBusinessesPanel pPosC = fresh();
            ButtonMatrix.fire("BIZ", "POSITION_CREATE_SUBMIT",
                    () -> hit(pPosC, Click.POSITION_CREATE_SUBMIT, ""),
                    OpCode.BIZ_POSITION_CREATE, BusinessKeys.KEY_BUSINESS);

            final GovBusinessesPanel pPosD = fresh();
            ButtonMatrix.fire("BIZ", "POSITION_DELETE_SUBMIT",
                    () -> hit(pPosD, Click.POSITION_DELETE_SUBMIT, ""),
                    OpCode.BIZ_POSITION_DELETE, BusinessKeys.KEY_BUSINESS);

            final GovBusinessesPanel pPosM = fresh();
            ButtonMatrix.fire("BIZ", "POSITION_MOVE_SUBMIT",
                    () -> hit(pPosM, Click.POSITION_MOVE_SUBMIT, ""),
                    OpCode.BIZ_POSITION_MOVE, BusinessKeys.KEY_BUSINESS);

            final GovBusinessesPanel pPosE = fresh();
            ButtonMatrix.fire("BIZ", "POSITION_EDIT_SUBMIT",
                    () -> hit(pPosE, Click.POSITION_EDIT_SUBMIT, ""),
                    OpCode.BIZ_POSITION_EDIT, BusinessKeys.KEY_BUSINESS);

            final GovBusinessesPanel pOfferS = fresh();
            ButtonMatrix.fire("BIZ", "OFFER_SEND_SUBMIT",
                    () -> hit(pOfferS, Click.OFFER_SEND_SUBMIT, ""),
                    OpCode.BIZ_OFFER_SEND, BusinessKeys.KEY_BUSINESS);

            final GovBusinessesPanel pOfferR = fresh();
            ButtonMatrix.fire("BIZ", "OFFER_RESPOND_SUBMIT",
                    () -> hit(pOfferR, Click.OFFER_RESPOND_SUBMIT, ""),
                    OpCode.BIZ_OFFER_RESPOND, BusinessKeys.KEY_BUSINESS);

            final GovBusinessesPanel pEnd = fresh();
            ButtonMatrix.fire("BIZ", "CONTRACT_END_SUBMIT",
                    () -> hit(pEnd, Click.CONTRACT_END_SUBMIT, ""),
                    OpCode.BIZ_CONTRACT_END, BusinessKeys.KEY_BUSINESS);

            final GovBusinessesPanel pPromote = fresh();
            ButtonMatrix.fire("BIZ", "CONTRACT_PROMOTE_SUBMIT",
                    () -> hit(pPromote, Click.CONTRACT_PROMOTE_SUBMIT, ""),
                    OpCode.BIZ_CONTRACT_PROMOTE, BusinessKeys.KEY_BUSINESS);

            final GovBusinessesPanel pComplaint = fresh();
            ButtonMatrix.fire("BIZ", "COMPLAINT_FILE_SUBMIT",
                    () -> hit(pComplaint, Click.COMPLAINT_FILE_SUBMIT, ""),
                    OpCode.BIZ_COMPLAINT_FILE, BusinessKeys.KEY_BUSINESS);

            final GovBusinessesPanel pCloseReq = fresh();
            ButtonMatrix.fire("BIZ", "CLOSE_REQUEST_SUBMIT",
                    () -> hit(pCloseReq, Click.CLOSE_REQUEST_SUBMIT, ""),
                    OpCode.BIZ_CLOSE_REQUEST, BusinessKeys.KEY_BUSINESS);

            final GovBusinessesPanel pCloseApp = fresh();
            ButtonMatrix.fire("BIZ", "CLOSE_APPROVE_SUBMIT",
                    () -> hit(pCloseApp, Click.CLOSE_APPROVE_SUBMIT, ""),
                    OpCode.BIZ_CLOSE_APPROVE, BusinessKeys.KEY_BUSINESS);

            final GovBusinessesPanel pCloseRej = fresh();
            ButtonMatrix.fire("BIZ", "CLOSE_REJECT_SUBMIT",
                    () -> hit(pCloseRej, Click.CLOSE_REJECT_SUBMIT, ""),
                    OpCode.BIZ_CLOSE_REJECT, BusinessKeys.KEY_BUSINESS);
        } finally {
            ButtonMatrix.uninstall();
        }
        ButtonMatrix.summary("BIZ");
    }
}
