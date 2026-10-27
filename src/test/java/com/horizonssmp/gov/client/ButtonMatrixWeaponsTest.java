package com.horizonssmp.gov.client;

import net.minecraft.network.chat.Component;

import com.horizonssmp.gov.client.GovWeaponsTypes.Click;
import com.horizonssmp.gov.client.GovWeaponsTypes.Modal;
import com.horizonssmp.gov.common.OpCode;
import com.horizonssmp.gov.common.WeaponKeys;

/**
 * TASK-037 button matrix for the weapons tab: every {@link Click} through the real
 * {@code handleClick} dispatch (ROW via the real {@code selectWeapon}/{@code selectPermit}/
 * {@code selectArea} row handlers). Armed submits are driven arm-then-fire with a backdated
 * stamp; modal submits stage {@code modal} + boxes directly. Runs via {@code main()}.
 */
final class ButtonMatrixWeaponsTest {
    private ButtonMatrixWeaponsTest() {
    }

    private static GovThemedEditBox box(final String value) {
        final GovThemedEditBox b = new GovThemedEditBox(null, 0, 0, 50, 9, Component.empty());
        b.setValue(value);
        return b;
    }

    private static GovWeaponsPanel fresh() {
        final GovWeaponsPanel p = new GovWeaponsPanel(null);
        p.active = true;
        p.subtab = 0;
        p.selectedSerial = "SN-0";
        p.viewerName = "Viewer";
        p.permitTarget = "Alex";
        p.filter = WeaponKeys.FILTER_ALL;
        p.weaponPage = 1;
        p.weaponPages = 3;
        p.permitPage = 1;
        p.permitPages = 3;
        p.areaPage = 1;
        p.areaPages = 3;
        p.selectedPermitId = "";
        p.selectedAreaId = "";
        p.classifyBox = box("minecraft:iron_sword");
        p.registerSerialBox = box("SN-9");
        p.registerTypeBox = box("Rifle");
        p.ownerBox = box("Alex");
        p.transferOwnerBox = box("Sam");
        p.permitTargetBox = box("Alex");
        p.issueTargetBox = box("Bob");
        p.reasonBox = box("Stolen");
        p.minutesBox = box("30");
        p.dimBox = box("minecraft:overworld");
        p.xBox = box("10");
        p.yBox = box("64");
        p.zBox = box("20");
        p.radiusBox = box("16");
        p.areaMinutesBox = box("60");
        p.areaReasonBox = box("Event");
        p.pending.clear();
        return p;
    }

    private static void click(final GovWeaponsPanel p, final Click click, final String value) {
        p.pending.clear();
        p.handleClick(click, value);
    }

    /** First SUBMIT arms the OPEN action; backdated stamp lets the second SUBMIT fire. */
    private static void armSubmit(final GovWeaponsPanel p) {
        p.pending.clear();
        p.handleClick(Click.SUBMIT, "");
        p.armedAt = System.currentTimeMillis() - 1000L;
        p.pending.clear();
        p.handleClick(Click.SUBMIT, "");
    }

    private static void open(final String name, final Click click, final Modal expect) {
        final GovWeaponsPanel p = fresh();
        ButtonMatrix.fireNoneSeam("WEAPONS", name, () -> click(p, click, ""),
                () -> ButtonMatrix.check(p.modal == expect, "modal is " + p.modal),
                "modal sync needs a live host");
    }

    public static void main(final String[] args) {
        ButtonMatrix.install();
        try {
            final GovWeaponsPanel pNone = fresh();
            ButtonMatrix.fireNone("WEAPONS", "NONE", () -> click(pNone, Click.NONE, ""),
                    () -> ButtonMatrix.check(pNone.modal == Modal.NONE, "modal moved"));

            final GovWeaponsPanel pFilter = fresh();
            ButtonMatrix.fire("WEAPONS", "FILTER", () -> click(pFilter, Click.FILTER, ""),
                    OpCode.WPN_LIST, WeaponKeys.KEY_FILTER, WeaponKeys.KEY_PAGE);

            final GovWeaponsPanel pRowW = fresh();
            ButtonMatrix.fire("WEAPONS", "ROW_WEAPON", () -> click(pRowW, Click.ROW, "SN-1"),
                    OpCode.WPN_INFO, WeaponKeys.KEY_SERIAL);

            final GovWeaponsPanel pRowP = fresh();
            pRowP.subtab = 1;
            ButtonMatrix.fireNone("WEAPONS", "ROW_PERMIT", () -> click(pRowP, Click.ROW, "p-1"),
                    () -> ButtonMatrix.check("p-1".equals(pRowP.selectedPermitId),
                            "permit selection lost"));

            final GovWeaponsPanel pRowA = fresh();
            pRowA.subtab = 2;
            ButtonMatrix.fireNone("WEAPONS", "ROW_AREA", () -> click(pRowA, Click.ROW, "a-1"),
                    () -> ButtonMatrix.check("a-1".equals(pRowA.selectedAreaId),
                            "area selection lost"));

            final GovWeaponsPanel pPrev = fresh();
            ButtonMatrix.fire("WEAPONS", "PREV", () -> click(pPrev, Click.PREV, ""),
                    OpCode.WPN_LIST, WeaponKeys.KEY_PAGE);

            final GovWeaponsPanel pNext = fresh();
            pNext.subtab = 1;
            ButtonMatrix.fire("WEAPONS", "NEXT", () -> click(pNext, Click.NEXT, ""),
                    OpCode.WPN_PERMIT_LIST, WeaponKeys.KEY_PAGE);

            final GovWeaponsPanel pRefresh = fresh();
            pRefresh.selectedSerial = "";
            ButtonMatrix.fire("WEAPONS", "REFRESH", () -> click(pRefresh, Click.REFRESH, ""),
                    OpCode.WPN_LIST, WeaponKeys.KEY_FILTER);

            final GovWeaponsPanel pRefreshA = fresh();
            pRefreshA.subtab = 2;
            ButtonMatrix.fire("WEAPONS", "REFRESH_AREA", () -> click(pRefreshA, Click.REFRESH, ""),
                    OpCode.AREA_PERMIT_LIST, WeaponKeys.KEY_PAGE);

            open("REGISTER_OPEN", Click.REGISTER_OPEN, Modal.REGISTER);
            open("RECORD_OPEN", Click.RECORD_OPEN, Modal.RECORD);
            open("TRANSFER_OPEN", Click.TRANSFER_OPEN, Modal.TRANSFER);
            open("REVOKE_OPEN", Click.REVOKE_OPEN, Modal.REVOKE);
            open("LOSS_OPEN", Click.LOSS_OPEN, Modal.LOSS);
            open("PERMIT_ISSUE_OPEN", Click.PERMIT_ISSUE_OPEN, Modal.PERMIT_ISSUE);
            open("PERMIT_REVOKE_OPEN", Click.PERMIT_REVOKE_OPEN, Modal.PERMIT_REVOKE);
            open("PERMIT_SUSPEND_OPEN", Click.PERMIT_SUSPEND_OPEN, Modal.PERMIT_SUSPEND);
            open("AREA_REVOKE_OPEN", Click.AREA_REVOKE_OPEN, Modal.AREA_REVOKE);

            final GovWeaponsPanel pAreaCreate = fresh();
            ButtonMatrix.fireBlock("WEAPONS", "AREA_CREATE_OPEN",
                    () -> click(pAreaCreate, Click.AREA_CREATE_OPEN, ""),
                    "fillAreaDefaults needs a live player");

            final GovWeaponsPanel pLoad = fresh();
            ButtonMatrix.fire("WEAPONS", "PERMIT_LOAD", () -> click(pLoad, Click.PERMIT_LOAD, ""),
                    OpCode.WPN_PERMIT_LIST, "target", WeaponKeys.KEY_PAGE);

            final GovWeaponsPanel pClassify = fresh();
            ButtonMatrix.fire("WEAPONS", "CLASSIFY", () -> click(pClassify, Click.CLASSIFY, ""),
                    OpCode.WPN_CLASSIFY, WeaponKeys.KEY_ITEM_ID);

            final GovWeaponsPanel pHeld = fresh();
            ButtonMatrix.fire("WEAPONS", "HELD", () -> click(pHeld, Click.HELD, ""),
                    OpCode.WPN_CLASSIFY, WeaponKeys.KEY_ITEM_ID);

            final GovWeaponsPanel pPick = fresh();
            ButtonMatrix.fireNone("WEAPONS", "CLASS_PICK", () -> click(pPick, Click.CLASS_PICK, ""),
                    () -> ButtonMatrix.check("s".equals(pPick.registerClass),
                            "register class did not cycle, got " + pPick.registerClass));

            final GovWeaponsPanel pRec = fresh();
            pRec.modal = Modal.RECORD;
            ButtonMatrix.fireNoneSeam("WEAPONS", "SUBMIT_RECORD", () -> click(pRec, Click.SUBMIT, ""),
                    () -> ButtonMatrix.check(pRec.modal == Modal.NONE, "record did not close"),
                    "record close sync needs a live host");

            final GovWeaponsPanel pReg = fresh();
            pReg.modal = Modal.REGISTER;
            ButtonMatrix.fire("WEAPONS", "SUBMIT_REGISTER", () -> click(pReg, Click.SUBMIT, ""),
                    OpCode.WPN_REGISTER, "target", WeaponKeys.KEY_CLASS,
                    WeaponKeys.KEY_SERIAL, WeaponKeys.KEY_TYPE);

            final GovWeaponsPanel pTransfer = fresh();
            pTransfer.modal = Modal.TRANSFER;
            ButtonMatrix.fire("WEAPONS", "SUBMIT_TRANSFER", () -> armSubmit(pTransfer),
                    OpCode.WPN_TRANSFER, WeaponKeys.KEY_SERIAL, "target");

            final GovWeaponsPanel pRevoke = fresh();
            pRevoke.modal = Modal.REVOKE;
            ButtonMatrix.fire("WEAPONS", "SUBMIT_REVOKE", () -> armSubmit(pRevoke),
                    OpCode.WPN_REVOKE, WeaponKeys.KEY_SERIAL, WeaponKeys.KEY_REASON);

            final GovWeaponsPanel pLoss = fresh();
            pLoss.modal = Modal.LOSS;
            ButtonMatrix.fire("WEAPONS", "SUBMIT_LOSS", () -> click(pLoss, Click.SUBMIT, ""),
                    OpCode.WPN_REPORT_LOSS, WeaponKeys.KEY_SERIAL, WeaponKeys.KEY_REASON);

            final GovWeaponsPanel pIssue = fresh();
            pIssue.modal = Modal.PERMIT_ISSUE;
            ButtonMatrix.fire("WEAPONS", "SUBMIT_PERMIT_ISSUE",
                    () -> click(pIssue, Click.SUBMIT, ""),
                    OpCode.WPN_PERMIT_ISSUE, "target", WeaponKeys.KEY_PERMIT_CLASS,
                    WeaponKeys.KEY_REASON);

            final GovWeaponsPanel pRevokeP = fresh();
            pRevokeP.modal = Modal.PERMIT_REVOKE;
            ButtonMatrix.fire("WEAPONS", "SUBMIT_PERMIT_REVOKE", () -> armSubmit(pRevokeP),
                    OpCode.WPN_PERMIT_REVOKE, "target", WeaponKeys.KEY_PERMIT_CLASS,
                    WeaponKeys.KEY_REASON);

            final GovWeaponsPanel pSuspend = fresh();
            pSuspend.modal = Modal.PERMIT_SUSPEND;
            ButtonMatrix.fire("WEAPONS", "SUBMIT_PERMIT_SUSPEND",
                    () -> click(pSuspend, Click.SUBMIT, ""),
                    OpCode.WPN_PERMIT_SUSPEND, "target", WeaponKeys.KEY_PERMIT_CLASS,
                    WeaponKeys.KEY_MINUTES, WeaponKeys.KEY_REASON);

            final GovWeaponsPanel pArea = fresh();
            pArea.modal = Modal.AREA_CREATE;
            ButtonMatrix.fire("WEAPONS", "SUBMIT_AREA_CREATE",
                    () -> click(pArea, Click.SUBMIT, ""),
                    OpCode.AREA_PERMIT_CREATE, WeaponKeys.KEY_DIMENSION, WeaponKeys.KEY_X,
                    WeaponKeys.KEY_Y, WeaponKeys.KEY_Z, WeaponKeys.KEY_RADIUS,
                    WeaponKeys.KEY_VALID_MINUTES, WeaponKeys.KEY_REASON);

            final GovWeaponsPanel pAreaR = fresh();
            pAreaR.modal = Modal.AREA_REVOKE;
            pAreaR.selectedAreaId = "area-1";
            ButtonMatrix.fire("WEAPONS", "SUBMIT_AREA_REVOKE", () -> armSubmit(pAreaR),
                    OpCode.AREA_PERMIT_REVOKE, WeaponKeys.KEY_AREA_ID, WeaponKeys.KEY_REASON);

            // Cancel path: arming TRANSFER then opening REGISTER disarms; the register
            // submit fires, never the cancelled transfer. REGISTER_OPEN stages the modal
            // BEFORE its headed-only host-focus tail throws, so catch that seam throw
            // inside and continue to the final SUBMIT (which fires clean).
            final GovWeaponsPanel pCancel = fresh();
            pCancel.modal = Modal.TRANSFER;
            ButtonMatrix.fire("WEAPONS", "TRANSFER_CANCELLED_BY_REGISTER", () -> {
                pCancel.pending.clear();
                pCancel.handleClick(Click.SUBMIT, "");
                ButtonMatrix.check(pCancel.armedAction == Click.TRANSFER_OPEN,
                        "transfer did not arm");
                pCancel.pending.clear();
                try {
                    pCancel.handleClick(Click.REGISTER_OPEN, "");
                } catch (final NullPointerException expectedSeam) {
                    // Modal staging happens before the host-focus tail; keep going.
                }
                ButtonMatrix.check(pCancel.armedAction == Click.NONE, "register did not disarm");
                ButtonMatrix.check(pCancel.modal == Modal.REGISTER, "register did not open");
                // REGISTER_OPEN clears the form (real user retypes); re-stage headless.
                pCancel.registerSerialBox.setValue("SN-9");
                pCancel.registerTypeBox.setValue("Rifle");
                pCancel.ownerBox.setValue("Alex");
                pCancel.pending.clear();
                pCancel.handleClick(Click.SUBMIT, "");
            }, OpCode.WPN_REGISTER, WeaponKeys.KEY_SERIAL);

            final GovWeaponsPanel pCancelB = fresh();
            pCancelB.modal = Modal.REGISTER;
            ButtonMatrix.fireNoneSeam("WEAPONS", "CANCEL", () -> click(pCancelB, Click.CANCEL, ""),
                    () -> ButtonMatrix.check(pCancelB.modal == Modal.NONE, "cancel did not close"),
                    "cancel sync needs a live host");

            final GovWeaponsPanel pClose = fresh();
            pClose.modal = Modal.LOSS;
            ButtonMatrix.fireNoneSeam("WEAPONS", "CLOSE_MODAL",
                    () -> click(pClose, Click.CLOSE_MODAL, ""),
                    () -> ButtonMatrix.check(pClose.modal == Modal.NONE, "close did not shut"),
                    "close sync needs a live host");

            final GovWeaponsPanel pRetryD = fresh();
            ButtonMatrix.fire("WEAPONS", "RETRY_DETAIL",
                    () -> click(pRetryD, Click.RETRY, "detail"),
                    OpCode.WPN_INFO, WeaponKeys.KEY_SERIAL);

            final GovWeaponsPanel pRetryL = fresh();
            pRetryL.selectedSerial = "";
            ButtonMatrix.fire("WEAPONS", "RETRY_LIST", () -> click(pRetryL, Click.RETRY, ""),
                    OpCode.WPN_LIST, WeaponKeys.KEY_FILTER);
        } finally {
            ButtonMatrix.uninstall();
        }
        ButtonMatrix.summary("WEAPONS");
    }
}
