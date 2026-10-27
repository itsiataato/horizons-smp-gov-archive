package com.horizonssmp.gov.client;

import static com.horizonssmp.gov.client.GovTreasuryTypes.*;

import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;

/**
 * The Bounty-tab place form: a free-text target plus a numeric amount.
 *
 * <p>The dashboard parity path for {@code /gov bounty place}. The old target picker only
 * cycled online players, so an offline name could never be entered; this form sends the
 * typed name verbatim and the server resolves it through online players, stored files
 * and the profile cache, refusing typos with {@code BAD_TARGET}. The amount box takes
 * digits directly and clamps to the {@code minAmount}/{@code maxAmount} bounds the list
 * answer carries; the minus/plus buttons stay as a coarse quick-pick of ten ingots.
 *
 * <p>The boxes follow the bank-credit lifecycle: created once through
 * {@link GovBankCreditForm#box}, placed with the credit {@code placeBox} offsets, shown
 * only on the active Bounty tab with a laid-out row, and cleared by reset. There is no
 * client-side gating beyond the empty-target hint: every refusal ({@code BAD_TARGET},
 * {@code BAD_AMOUNT}, self-bounty) surfaces verbatim through the shared status line and
 * the server stays authoritative.
 */
final class GovBountyForm {

    /** One coarse stepper tap moves the amount by this many ingots. */
    static final int AMOUNT_STEP = 10;

    /** Sentinel for an amount box the parser rejects; real amounts are never negative. */
    static final long AMOUNT_INVALID = -1L;

    EditBox targetBox;
    EditBox amountBox;

    static List<EditBox> createWidgets(final GovTreasuryPanel p, final Font font) {
        final GovBountyForm form = p.bounty;
        if (form.targetBox == null) {
            form.targetBox = GovBankCreditForm.box(font, GovBankCreditForm.MAX_TARGET,
                    "gui.gov.treasury.bounty_target_hint");
            form.amountBox = GovBankCreditForm.box(font, GovBankCreditForm.MAX_AMOUNT,
                    "gui.gov.treasury.bounty_amount_hint");
        }
        position(p);
        sync(p);
        return List.of(form.targetBox, form.amountBox);
    }

    static void position(final GovTreasuryPanel p) {
        final GovBountyForm form = p.bounty;
        GovBankCreditForm.placeBox(form.targetBox, p.bountyTargetRect);
        GovBankCreditForm.placeBox(form.amountBox, p.bountyAmountRect);
    }

    /** Shows the boxes only on the active Bounty tab with a laid-out form row. */
    static void sync(final GovTreasuryPanel p) {
        final GovBountyForm form = p.bounty;
        final boolean show = p.active && p.subtab == TAB_BOUNTY
                && p.bountyTargetRect.w() > 0 && p.bountyTargetRect.h() > 0;
        GovBankCreditForm.setVisible(p, form.targetBox, show);
        GovBankCreditForm.setVisible(p, form.amountBox, show);
    }

    /** Forgets the typed values and drops field focus; the next visit starts blank. */
    static void reset(final GovTreasuryPanel p) {
        final GovBountyForm form = p.bounty;
        if (form.targetBox != null) {
            form.targetBox.setValue("");
        }
        if (form.amountBox != null) {
            form.amountBox.setValue("");
        }
        clearFocus(p);
        sync(p);
    }

    /** Drops field focus so Esc routes field first, then the dashboard. */
    static boolean clearFocus(final GovTreasuryPanel p) {
        final GovBountyForm form = p.bounty;
        final EditBox[] boxes = {form.targetBox, form.amountBox};
        for (final EditBox box : boxes) {
            if (box != null && p.host.getFocused() == box) {
                p.host.setFocused(null);
                return true;
            }
        }
        return false;
    }

    /** The typed target name, stripped; empty when nothing was typed. */
    static String target(final GovTreasuryPanel p) {
        final EditBox box = p.bounty.targetBox;
        return box == null ? "" : box.getValue().strip();
    }

    /** The raw typed amount text, stripped; empty when nothing was typed. */
    static String amountText(final GovTreasuryPanel p) {
        final EditBox box = p.bounty.amountBox;
        return box == null ? "" : box.getValue().strip();
    }

    /**
     * Parses a typed amount: optional leading plus, ASCII digits, fits a long. Returns
     * {@link #AMOUNT_INVALID} for blank, signed-negative, decimal, junk or overflowing
     * text; zero parses (bounds decide whether it can be placed).
     */
    static long parseAmount(final String text) {
        if (text == null) {
            return AMOUNT_INVALID;
        }
        String clean = text.strip();
        if (clean.isEmpty()) {
            return AMOUNT_INVALID;
        }
        if (clean.charAt(0) == '+') {
            clean = clean.substring(1);
        }
        int digits = 0;
        while (digits + 1 < clean.length() && clean.charAt(digits) == '0') {
            digits++;
        }
        clean = clean.substring(digits);
        if (clean.isEmpty() || clean.length() > 19) {
            return AMOUNT_INVALID;
        }
        for (int index = 0; index < clean.length(); index++) {
            final char digit = clean.charAt(index);
            if (digit < '0' || digit > '9') {
                return AMOUNT_INVALID;
            }
        }
        try {
            return Long.parseLong(clean);
        } catch (final NumberFormatException overflow) {
            return AMOUNT_INVALID;
        }
    }

    /** Clamps a parsed amount into the server bounds both sides already agreed on. */
    static long clampAmount(final long value, final long min, final long max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * The box amount clamped to the panel bounds, or {@link #AMOUNT_INVALID} when the
     * box holds no parseable number. An empty box is invalid, never silently the min.
     */
    static long effectiveAmount(final GovTreasuryPanel p) {
        final long parsed = parseAmount(amountText(p));
        if (parsed == AMOUNT_INVALID) {
            return AMOUNT_INVALID;
        }
        return clampAmount(parsed, p.bountyMin, p.bountyMax);
    }

    /** Writes an amount back to the box; a missing box drops the write. */
    static void setAmount(final GovTreasuryPanel p, final long value) {
        if (p.bounty.amountBox != null) {
            p.bounty.amountBox.setValue(Long.toString(value));
        }
    }

    /**
     * Seeds an untouched amount box with the server minimum after a list answer, so the
     * first coarse tap steps from a sane value instead of an empty box. A typed value
     * is never overwritten.
     */
    static void seedAmount(final GovTreasuryPanel p) {
        if (p.bounty.amountBox == null || !p.bounty.amountBox.getValue().isBlank()) {
            return;
        }
        if (p.bountyMin > 0L && p.bountyMax >= p.bountyMin) {
            p.bounty.amountBox.setValue(Long.toString(p.bountyMin));
        }
    }

    /** Draws the field frames; the screen draws the boxes themselves. */
    static void drawForm(final GovTreasuryPanel p, final GuiGraphics graphics) {
        if (p.bountyFormRect.w() <= 0 || p.bountyFormRect.h() <= 0) {
            return;
        }
        GovBankCreditForm.drawFrame(p, graphics, p.bountyTargetRect, p.bounty.targetBox);
        GovBankCreditForm.drawFrame(p, graphics, p.bountyAmountRect, p.bounty.amountBox);
    }
}
