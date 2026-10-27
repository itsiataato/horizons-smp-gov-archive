package com.horizonssmp.gov.client;

import static com.horizonssmp.gov.client.GovTreasuryTypes.*;

import java.util.Arrays;

/**
 * The bounty subtab geometry: the place form row, the count line and the list.
 *
 * <p>Split from {@code GovTreasuryGeometry} so that file stays under the 300-line rule. The
 * layout mirrors the contracts subtab: a top control row, a scrollable list, a status line and
 * a footer with refresh and close. The place form is a free-text target box plus a numeric
 * amount box with two coarse stepper buttons and the place button, so the row subdivides
 * into the two fields, the minus/plus pair and the place button.
 */
final class GovBountyGeometry {

    private GovBountyGeometry() {
    }

    /** Clears every bounty rectangle for a degenerate layout; the boxes hide with it. */
    static void zero(final GovTreasuryPanel p) {
        p.bountyFormRect = NONE;
        p.bountyTargetRect = NONE;
        p.bountyAmountMinusRect = NONE;
        p.bountyAmountRect = NONE;
        p.bountyAmountPlusRect = NONE;
        p.bountyPlaceRect = NONE;
        p.bountyCountRect = NONE;
        p.bountyListRect = NONE;
        p.bountyStatusRect = NONE;
        p.bountyFooterRect = NONE;
        p.bountyRefreshRect = NONE;
        p.bountyCloseRect = NONE;
        Arrays.fill(p.bountyColumnX, 0);
        Arrays.fill(p.bountyColumnW, 0);
        p.bountyVisibleRows = 0;
        GovBountyList.clampScroll(p);
        GovBountyForm.sync(p);
    }

    static void compute(final GovTreasuryPanel p) {
        final int innerX = p.area.x() + PAD;
        final int innerW = Math.max(0, p.area.w() - PAD * 2);
        final int footerY = Math.max(p.area.y() + PAD,
                p.area.bottom() - PAD - FOOTER_H);
        int y = p.area.y() + PAD;
        if (p.showTitle) {
            p.titleRect = GovTreasuryGeometry.rect(p, innerX, y, innerW, TITLE_H);
            y += TITLE_H + 2;
        } else {
            p.titleRect = NONE;
        }
        p.bountyFormRect = GovTreasuryGeometry.rect(p, innerX, y, innerW, BUTTON_H);
        computeFormRow(p);
        y = p.bountyFormRect.bottom() + 2;
        p.bountyCountRect = GovTreasuryGeometry.rect(p, innerX, y, innerW, LINE + 2);
        p.bountyStatusRect = GovTreasuryGeometry.rect(p, innerX, Math.max(y + LINE + 3,
                footerY - 2 - STATUS_H), innerW, STATUS_H);
        final int listTop = p.bountyCountRect.bottom() + 1;
        p.bountyListRect = GovTreasuryGeometry.rect(p, innerX, listTop, innerW,
                Math.max(0, p.bountyStatusRect.y() - 2 - listTop));
        p.bountyFooterRect = GovTreasuryGeometry.rect(p, innerX, footerY, innerW, FOOTER_H);
        final int closeW = Math.min(42, Math.max(0, innerW / 5));
        final int refreshW = Math.min(52, Math.max(0, innerW / 5));
        p.bountyCloseRect = GovTreasuryGeometry.rect(p, innerX + innerW - closeW, footerY,
                closeW, BUTTON_H);
        p.bountyRefreshRect = GovTreasuryGeometry.rect(p,
                p.bountyCloseRect.x() - 2 - refreshW, footerY, refreshW, BUTTON_H);
        computeColumns(p);
        p.bountyVisibleRows = GovFit.rowsThatFit(p.bountyListRect.h(), ROW_H,
                Integer.MAX_VALUE);
        GovBountyList.clampScroll(p);
        if (!p.bountySelectedId.isEmpty()) {
            GovBountyList.reveal(p);
        }
        GovBountyForm.position(p);
        GovBountyForm.sync(p);
    }

    private static void computeFormRow(final GovTreasuryPanel p) {
        final GovRect form = p.bountyFormRect;
        if (form.w() <= 0 || form.h() <= 0) {
            p.bountyTargetRect = NONE;
            p.bountyAmountMinusRect = NONE;
            p.bountyAmountRect = NONE;
            p.bountyAmountPlusRect = NONE;
            p.bountyPlaceRect = NONE;
            return;
        }
        final int gap = 2;
        final int nav = BUTTON_H;
        final int placeW = Math.min(64, Math.max(0, form.w() / 4));
        final int amountW = Math.min(72, Math.max(48, form.w() / 6));
        final int fixed = nav * 2 + gap * 4 + placeW + amountW;
        final int targetW = Math.max(0, form.w() - fixed);
        int x = form.x();
        p.bountyTargetRect = GovTreasuryGeometry.rect(p, x, form.y(), targetW, BUTTON_H);
        x += targetW + gap;
        p.bountyAmountRect = GovTreasuryGeometry.rect(p, x, form.y(), amountW, BUTTON_H);
        x += amountW + gap;
        p.bountyAmountMinusRect = GovTreasuryGeometry.rect(p, x, form.y(), nav, BUTTON_H);
        x += nav + gap;
        p.bountyAmountPlusRect = GovTreasuryGeometry.rect(p, x, form.y(), nav, BUTTON_H);
        p.bountyPlaceRect = GovTreasuryGeometry.rect(p, form.right() - placeW, form.y(),
                placeW, BUTTON_H);
    }

    private static void computeColumns(final GovTreasuryPanel p) {
        final int reserved = 44;
        final int inner = Math.max(0, p.bountyListRect.w() - 8 - reserved);
        final int[] share = {34, 34, 32};
        int x = p.bountyListRect.x() + 4;
        int used = 0;
        for (int index = 0; index < 3; index++) {
            final int width = index == 2
                    ? Math.max(0, inner - used) : inner * share[index] / 100;
            p.bountyColumnX[index] = x;
            p.bountyColumnW[index] = Math.max(0, width);
            x += width;
            used += width;
        }
    }
}
