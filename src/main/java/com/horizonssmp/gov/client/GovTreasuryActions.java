package com.horizonssmp.gov.client;

import static com.horizonssmp.gov.client.GovTreasuryTypes.*;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import org.lwjgl.glfw.GLFW;

/**
 * The Treasury dashboard triggers for the seven wire-ready actions that shipped with builders,
 * opcodes, server handlers and response cases but no button or key path: {@code BANK_TRANSFER},
 * {@code BANK_WITHDRAW}, {@code BANK_HISTORY}, {@code PAY_PLAYER}, {@code CONTRACT_POST},
 * {@code PAYROLL_RUN} and {@code CONTRACT_CREDIT}.
 *
 * <p>The five money actions live on the Balances tab in one button row carved from the top of the
 * notes area (transfer, withdraw, history, pay, payroll); the two contract actions share one row
 * above the Contracts footer (post, contract credit). Geometry calls the compute methods during
 * layout, {@code GovTreasuryViewButtons} calls the add methods during rebuild, and
 * {@code GovTreasuryInput} delegates otherwise-unhandled keys here. Every button reuses the
 * existing {@code GovTreasuryRequests} submit verbatim — same opcode, same payload keys — so the
 * server behavior is identical to the command path.
 *
 * <p>Only {@code PAY_PLAYER} needs an input: paying the actor (the existing submit's fallback) is
 * a near-dead button, so this helper also owns a minimal target-plus-amount form that reuses the
 * {@code GovBankCreditForm} box, placement and visibility patterns. Values cross the wire
 * verbatim with no client-side validation; a blank target stops at the shared
 * {@code no_target} hint while every server refusal surfaces verbatim on the status line.
 *
 * <p>The contract-credit button and its submit follow the server's {@code canCredit} hint
 * exactly like the balances-tab CREDIT button (TASK-032: both ops mint, so both affordances
 * hide without the dual gate). The other six actions carry no hint, so their enforcement
 * stays server-side and refusals surface through the existing response cases. Hiding is
 * affordance only: the server refuses an ungated CONTRACT_CREDIT regardless of what the
 * client sends.
 */
final class GovTreasuryActions {

    GovRect transferRect = NONE;
    GovRect withdrawRect = NONE;
    GovRect historyRect = NONE;
    GovRect payRect = NONE;
    GovRect payrollRect = NONE;
    GovRect postRect = NONE;
    GovRect contractCreditRect = NONE;

    GovRect payTargetRect = NONE;
    GovRect payAmountRect = NONE;
    EditBox payTargetBox;
    EditBox payAmountBox;

    /** The balances-tab pay text fields plus the credit and bounty forms' own boxes. */
    static List<EditBox> createWidgets(final GovTreasuryPanel p, final Font font) {
        final List<EditBox> boxes = new ArrayList<>(GovBankCreditForm.createWidgets(p, font));
        boxes.addAll(GovBountyForm.createWidgets(p, font));
        final GovTreasuryActions self = p.actions;
        if (self.payTargetBox == null) {
            self.payTargetBox = GovBankCreditForm.box(font, GovBankCreditForm.MAX_TARGET,
                    "gui.gov.treasury.pay_target_hint");
            self.payAmountBox = GovBankCreditForm.box(font, GovBankCreditForm.MAX_AMOUNT,
                    "gui.gov.treasury.pay_amount_hint");
        }
        position(p);
        sync(p);
        boxes.add(self.payTargetBox);
        boxes.add(self.payAmountBox);
        return boxes;
    }

    /**
     * Carves the five-button actions row plus the pay row from the top of the notes area; the
     * credit form carves next and the notes keep the remainder. Too short for a row of controls
     * plus one line of notes, and the rows stay zeroed (keys still fire).
     */
    static void computeBalances(final GovTreasuryPanel p) {
        final GovTreasuryActions self = p.actions;
        final GovRect notes = p.balanceNotesRect;
        final int gap = 2;
        final int need = BUTTON_H + gap + BUTTON_H + gap + LINE;
        if (notes.w() <= 0 || notes.h() < need) {
            zeroBalances(p);
            return;
        }
        final int count = 5;
        final int available = Math.max(0, notes.w() - gap * (count - 1));
        int x = notes.x();
        int used = 0;
        final GovRect[] row = new GovRect[count];
        for (int index = 0; index < count; index++) {
            final int width = index == count - 1
                    ? Math.max(0, available - used)
                    : Math.max(0, available / count);
            row[index] = GovTreasuryGeometry.rect(p, x + used, notes.y(), width, BUTTON_H);
            used += width + gap;
        }
        self.transferRect = row[0];
        self.withdrawRect = row[1];
        self.historyRect = row[2];
        self.payRect = row[3];
        self.payrollRect = row[4];
        final int formY = notes.y() + BUTTON_H + gap;
        final int amountW = Math.min(64, Math.max(0, notes.w() / 3));
        final int targetW = Math.max(0, notes.w() - amountW - gap);
        self.payTargetRect = GovTreasuryGeometry.rect(p, notes.x(), formY, targetW, BUTTON_H);
        self.payAmountRect = GovTreasuryGeometry.rect(p, notes.x() + targetW + gap, formY,
                amountW, BUTTON_H);
        final int notesTop = formY + BUTTON_H + gap;
        p.balanceNotesRect = GovTreasuryGeometry.rect(p, notes.x(), notesTop, notes.w(),
                Math.max(0, notes.bottom() - notesTop));
        position(p);
        sync(p);
    }

    /** Lays the post + contract-credit row at the given y across the contracts footer width. */
    static void computeContracts(final GovTreasuryPanel p, final int rowY) {
        final GovTreasuryActions self = p.actions;
        final GovRect footer = p.contractFooterRect;
        if (footer.w() <= 0) {
            zeroContracts(p);
            return;
        }
        final int gap = 2;
        final int leftW = Math.max(0, (footer.w() - gap) / 2);
        final int rightW = Math.max(0, footer.w() - gap - leftW);
        self.postRect = GovTreasuryGeometry.rect(p, footer.x(), rowY, leftW, BUTTON_H);
        self.contractCreditRect = GovTreasuryGeometry.rect(p, footer.x() + leftW + gap, rowY,
                rightW, BUTTON_H);
    }

    /** Clears every actions rectangle and hides the pay boxes; the values survive layout. */
    static void zero(final GovTreasuryPanel p) {
        zeroBalances(p);
        zeroContracts(p);
    }

    static void zeroBalances(final GovTreasuryPanel p) {
        final GovTreasuryActions self = p.actions;
        self.transferRect = NONE;
        self.withdrawRect = NONE;
        self.historyRect = NONE;
        self.payRect = NONE;
        self.payrollRect = NONE;
        self.payTargetRect = NONE;
        self.payAmountRect = NONE;
        sync(p);
    }

    static void zeroContracts(final GovTreasuryPanel p) {
        final GovTreasuryActions self = p.actions;
        self.postRect = NONE;
        self.contractCreditRect = NONE;
    }

    /** Forgets the typed pay values and drops field focus; the next visit starts blank. */
    static void reset(final GovTreasuryPanel p) {
        final GovTreasuryActions self = p.actions;
        if (self.payTargetBox != null) {
            self.payTargetBox.setValue("");
        }
        if (self.payAmountBox != null) {
            self.payAmountBox.setValue("");
        }
        GovBountyForm.reset(p);
        clearFocus(p);
        sync(p);
    }

    static void position(final GovTreasuryPanel p) {
        final GovTreasuryActions self = p.actions;
        GovBankCreditForm.placeBox(self.payTargetBox, self.payTargetRect);
        GovBankCreditForm.placeBox(self.payAmountBox, self.payAmountRect);
    }

    /** Shows the pay boxes only on the active Balances tab with a laid-out row. */
    static void sync(final GovTreasuryPanel p) {
        final GovTreasuryActions self = p.actions;
        final boolean show = p.active && p.subtab == TAB_BALANCES
                && self.payTargetRect.w() > 0 && self.payTargetRect.h() > 0;
        GovBankCreditForm.setVisible(p, self.payTargetBox, show);
        GovBankCreditForm.setVisible(p, self.payAmountBox, show);
        GovBountyForm.sync(p);
    }

    /** Drops pay-field focus so Esc routes field first, then the dashboard. */
    static boolean clearFocus(final GovTreasuryPanel p) {
        final GovTreasuryActions self = p.actions;
        final EditBox[] boxes = {self.payTargetBox, self.payAmountBox};
        for (final EditBox box : boxes) {
            if (box != null && p.host.getFocused() == box) {
                p.host.setFocused(null);
                return true;
            }
        }
        return false;
    }

    /** Draws the pay field frames; the screen draws the boxes themselves. */
    static void drawPayForm(final GovTreasuryPanel p, final GuiGraphics graphics) {
        final GovTreasuryActions self = p.actions;
        if (self.payTargetRect.w() <= 0 || self.payTargetRect.h() <= 0) {
            return;
        }
        GovBankCreditForm.drawFrame(p, graphics, self.payTargetRect, self.payTargetBox);
        GovBankCreditForm.drawFrame(p, graphics, self.payAmountRect, self.payAmountBox);
    }

    /** The five Balances-tab buttons; capability enforcement stays server-side. */
    static void addBalanceActions(final GovTreasuryPanel p, final boolean pending) {
        final GovTreasuryActions self = p.actions;
        final boolean free = !pending;
        GovTreasuryViewButtons.addButton(p, self.transferRect,
                GovTreasuryViews.label(p, "gui.gov.treasury.transfer"),
                Action.BANK_TRANSFER, free, false);
        GovTreasuryViewButtons.addButton(p, self.withdrawRect,
                GovTreasuryViews.label(p, "gui.gov.treasury.withdraw"),
                Action.BANK_WITHDRAW, free, false);
        GovTreasuryViewButtons.addButton(p, self.historyRect,
                GovTreasuryViews.label(p, "gui.gov.treasury.history"),
                Action.BANK_HISTORY, free, false);
        GovTreasuryViewButtons.addButton(p, self.payRect,
                GovTreasuryViews.label(p, "gui.gov.treasury.pay"),
                Action.PAY_PLAYER, free, true);
        GovTreasuryViewButtons.addButton(p, self.payrollRect,
                GovTreasuryViews.label(p, "gui.gov.treasury.payroll"),
                Action.PAYROLL_RUN, free, false);
    }

    /** The two Contracts-tab buttons; the credit mint follows the canCredit hint. */
    static void addContractActions(final GovTreasuryPanel p, final boolean pending) {
        final GovTreasuryActions self = p.actions;
        final boolean free = !pending;
        GovTreasuryViewButtons.addButton(p, self.postRect,
                GovTreasuryViews.label(p, "gui.gov.treasury.post"),
                Action.CONTRACT_POST, free, false);
        if (p.canCredit) {
            GovTreasuryViewButtons.addButton(p, self.contractCreditRect,
                    GovTreasuryViews.label(p, "gui.gov.treasury.contract_credit"),
                    Action.CONTRACT_CREDIT, free, false);
        }
    }

    /**
     * Letter shortcuts for the seven actions. A visible focused field owns every key, so typing
     * a target or amount never fires an action; every submit still guards mutations itself.
     */
    static boolean keyPressed(final GovTreasuryPanel p, final int keyCode, final int scanCode,
            final int modifiers) {
        if (!p.active || p.host.visibleFieldFocused()) {
            return false;
        }
        if (p.subtab == TAB_BALANCES) {
            if (keyCode == GLFW.GLFW_KEY_T) {
                GovTreasuryInput.handleAction(p, Action.BANK_TRANSFER);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_W) {
                GovTreasuryInput.handleAction(p, Action.BANK_WITHDRAW);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_H) {
                GovTreasuryInput.handleAction(p, Action.BANK_HISTORY);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_P) {
                GovTreasuryInput.handleAction(p, Action.PAY_PLAYER);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_R) {
                GovTreasuryInput.handleAction(p, Action.PAYROLL_RUN);
                return true;
            }
        }
        if (p.subtab == TAB_CONTRACTS) {
            if (keyCode == GLFW.GLFW_KEY_O) {
                GovTreasuryInput.handleAction(p, Action.CONTRACT_POST);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_C) {
                GovTreasuryInput.handleAction(p, Action.CONTRACT_CREDIT);
                return true;
            }
        }
        return false;
    }

    static String payTarget(final GovTreasuryPanel p) {
        final EditBox box = p.actions.payTargetBox;
        return box == null ? "" : box.getValue().strip();
    }

    static String payAmount(final GovTreasuryPanel p) {
        final EditBox box = p.actions.payAmountBox;
        return box == null ? "" : box.getValue().strip();
    }
}
