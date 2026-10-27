package com.horizonssmp.gov.client;

import net.minecraft.network.chat.Component;

import com.horizonssmp.gov.client.GovBusinessesTypes.Click;
import com.horizonssmp.gov.client.GovBusinessesTypes.Hit;
import com.horizonssmp.gov.client.GovBusinessesTypes.LoadState;
import com.horizonssmp.gov.common.BusinessKeys;

/**
 * TASK-037 shared staging for the businesses matrix: staged panel + hit/arm helpers.
 * Split from {@code ButtonMatrixBusinessesTest} to keep every file at or below 300 lines.
 */
final class ButtonMatrixBusinessesSupport {
    private ButtonMatrixBusinessesSupport() {
    }

    static GovThemedEditBox box(final String value) {
        final GovThemedEditBox b = new GovThemedEditBox(null, 0, 0, 60, 9, Component.empty());
        b.setValue(value);
        return b;
    }

    static GovBusinessesPanel fresh() {
        final GovBusinessesPanel p = new GovBusinessesPanel(null);
        p.active = true;
        p.selectedId = "b-1";
        p.detailState = LoadState.READY;
        p.canRegister = true;
        p.canManage = true;
        p.canPayroll = true;
        p.detail.put(BusinessKeys.CAN_EDIT_THIS, "true");
        p.detail.put(BusinessKeys.CAN_MANAGE_THIS, "true");
        p.detail.put(BusinessKeys.CAN_RECORD_PAYROLL_THIS, "true");
        p.nameBox = box("Acme");
        p.ownerBox = box("Alex");
        p.transferBox = box("Sam");
        p.suspendReasonBox = box("reason");
        p.staffPlayerBox = box("Bob");
        p.linkJobBox = box("Guard");
        p.rateBox = box("10");
        p.scheduleBox = box("daily");
        p.amountBox = box("5");
        p.paymentNoteBox = box("note");
        p.positionTitleBox = box("Guard");
        p.positionWageBox = box("10");
        // List paging + business cycling need staged rows; position edit needs a selection.
        p.pages = 3;
        p.page = 1;
        p.rows.add(new GovBusinessesTypes.BizRow("b-1", "Acme", "Alex", "uuid-1",
                "shop", "active", 1, "", ""));
        p.rows.add(new GovBusinessesTypes.BizRow("b-2", "Beta", "Sam", "uuid-2",
                "shop", "active", 1, "", ""));
        p.selectedPosition = "pos-1";
        p.pending.clear();
        return p;
    }

    static void hit(final GovBusinessesPanel p, final Click click, final String value) {
        p.pending.clear();
        GovBusinessesInput.handleHit(p, new Hit(new GovRect(0, 0, 1, 1), click, value));
    }

    /** First SUBMIT arms; backdated stamp lets the second SUBMIT fire. */
    static void armFire(final GovBusinessesPanel p, final Click click) {
        p.pending.clear();
        GovBusinessesInput.handleHit(p, new Hit(new GovRect(0, 0, 1, 1), click, ""));
        GovBusinessesArm.setArmed(p, click, System.currentTimeMillis() - 1000L);
        p.pending.clear();
        GovBusinessesInput.handleHit(p, new Hit(new GovRect(0, 0, 1, 1), click, ""));
    }
}
