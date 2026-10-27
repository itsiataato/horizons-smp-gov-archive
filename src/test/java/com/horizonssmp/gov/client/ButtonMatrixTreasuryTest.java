package com.horizonssmp.gov.client;

import net.minecraft.network.chat.Component;

import com.horizonssmp.gov.client.GovTreasuryTypes.Action;
import com.horizonssmp.gov.client.GovTreasuryTypes.LoadState;
import com.horizonssmp.gov.client.GovTreasuryTypes.StoreRow;
import com.horizonssmp.gov.common.EconomyKeys;
import com.horizonssmp.gov.common.OpCode;
import com.horizonssmp.gov.server.GovTargets;

/**
 * TASK-037 button matrix for the treasury tab: every {@link Action} through the real
 * {@code GovTreasuryInput.handleAction} dispatch. Runs via {@code main()}.
 */
final class ButtonMatrixTreasuryTest {
    private ButtonMatrixTreasuryTest() {
    }

    private static GovThemedEditBox box(final String value) {
        final GovThemedEditBox b = new GovThemedEditBox(null, 0, 0, 60, 9, Component.empty());
        b.setValue(value);
        return b;
    }

    private static GovTreasuryPanel fresh() {
        final GovTreasuryPanel p = new GovTreasuryPanel(null);
        p.active = true;
        p.storeRows.add(new StoreRow("item-1", "Bread", 1, 100L, true));
        p.storeSelectedId = "item-1";
        p.stacks = 1;
        p.storeBalance = 10_000L;
        p.storePage = 1;
        p.storePages = 3;
        p.contractSelectedId = "c-1";
        p.contractPage = 0;
        p.contractPages = 3;
        p.canCredit = true;
        p.credit.targetBox = box("Alex");
        p.credit.amountBox = box("25");
        p.credit.reasonBox = box("Aid");
        p.actions.payTargetBox = box("Alex");
        p.actions.payAmountBox = box("5");
        p.bountyState = LoadState.READY;
        p.bountyMin = 10L;
        p.bountyMax = 1000L;
        p.bounty.targetBox = box("Notch");
        p.bounty.amountBox = box("50");
        p.pending.clear();
        return p;
    }

    private static void act(final GovTreasuryPanel p, final Action action) {
        p.pending.clear();
        GovTreasuryInput.handleAction(p, action);
    }

    public static void main(final String[] args) {
        ButtonMatrix.install();
        try {
            final GovTreasuryPanel pPrev = fresh();
            pPrev.subtab = GovTreasuryTypes.TAB_STORE;
            ButtonMatrix.fire("TREASURY", "PREV", () -> act(pPrev, Action.PREV),
                    OpCode.STORE_LIST, EconomyKeys.KEY_PAGE);

            final GovTreasuryPanel pNext = fresh();
            pNext.subtab = GovTreasuryTypes.TAB_CONTRACTS;
            ButtonMatrix.fire("TREASURY", "NEXT", () -> act(pNext, Action.NEXT),
                    OpCode.CONTRACT_LIST, EconomyKeys.KEY_PAGE);

            final GovTreasuryPanel pMinus = fresh();
            pMinus.stacks = 2;
            ButtonMatrix.fireNone("TREASURY", "MINUS", () -> act(pMinus, Action.MINUS),
                    () -> ButtonMatrix.check(pMinus.stacks == 1, "stacks did not step down"));

            final GovTreasuryPanel pPlus = fresh();
            ButtonMatrix.fireNone("TREASURY", "PLUS", () -> act(pPlus, Action.PLUS),
                    () -> ButtonMatrix.check(pPlus.stacks == 2, "stacks did not step up"));

            final GovTreasuryPanel pBuy = fresh();
            ButtonMatrix.fire("TREASURY", "BUY", () -> act(pBuy, Action.BUY),
                    OpCode.STORE_BUY, EconomyKeys.KEY_ITEM, EconomyKeys.KEY_STACKS);

            // CLOSE is headed-only: it closes the live screen via the host, never
            // sending a request. Headless the null host throws, which proves the
            // dispatch reached the headed-only seam.
            final GovTreasuryPanel pClose = fresh();
            ButtonMatrix.fireBlock("TREASURY", "CLOSE", () -> act(pClose, Action.CLOSE),
                    "screen close needs a live host");

            final GovTreasuryPanel pDeposit = fresh();
            ButtonMatrix.fire("TREASURY", "DEPOSIT", () -> act(pDeposit, Action.DEPOSIT),
                    OpCode.BANK_DEPOSIT, EconomyKeys.KEY_AMOUNT);

            final GovTreasuryPanel pRefBal = fresh();
            pRefBal.subtab = GovTreasuryTypes.TAB_BALANCES;
            ButtonMatrix.fire("TREASURY", "REFRESH(balances)", () -> act(pRefBal, Action.REFRESH),
                    OpCode.BANK_BALANCE, EconomyKeys.KEY_ACCOUNT);

            final GovTreasuryPanel pRefStore = fresh();
            pRefStore.subtab = GovTreasuryTypes.TAB_STORE;
            ButtonMatrix.fire("TREASURY", "REFRESH(store)", () -> act(pRefStore, Action.REFRESH),
                    OpCode.STORE_LIST, EconomyKeys.KEY_PAGE);

            final GovTreasuryPanel pRefCon = fresh();
            pRefCon.subtab = GovTreasuryTypes.TAB_CONTRACTS;
            ButtonMatrix.fire("TREASURY", "REFRESH(contracts)",
                    () -> act(pRefCon, Action.REFRESH),
                    OpCode.CONTRACT_LIST, EconomyKeys.KEY_PAGE);

            final GovTreasuryPanel pRefBounty = fresh();
            pRefBounty.subtab = GovTreasuryTypes.TAB_BOUNTY;
            ButtonMatrix.fire("TREASURY", "REFRESH(bounty)", () -> act(pRefBounty, Action.REFRESH),
                    OpCode.BOUNTY_LIST, "target");

            final GovTreasuryPanel pAccept = fresh();
            ButtonMatrix.fire("TREASURY", "ACCEPT", () -> act(pAccept, Action.ACCEPT),
                    OpCode.CONTRACT_ACCEPT, EconomyKeys.KEY_CONTRACT);

            final GovTreasuryPanel pSubmit = fresh();
            ButtonMatrix.fire("TREASURY", "SUBMIT", () -> act(pSubmit, Action.SUBMIT),
                    OpCode.CONTRACT_SUBMIT, EconomyKeys.KEY_CONTRACT);

            final GovTreasuryPanel pApprove = fresh();
            ButtonMatrix.fire("TREASURY", "APPROVE", () -> act(pApprove, Action.APPROVE),
                    OpCode.CONTRACT_APPROVE, EconomyKeys.KEY_CONTRACT);

            final GovTreasuryPanel pVoid = fresh();
            ButtonMatrix.fire("TREASURY", "VOID", () -> act(pVoid, Action.VOID),
                    OpCode.CONTRACT_VOID, EconomyKeys.KEY_CONTRACT, EconomyKeys.KEY_REASON);

            final GovTreasuryPanel pXfer = fresh();
            ButtonMatrix.fire("TREASURY", "BANK_TRANSFER", () -> act(pXfer, Action.BANK_TRANSFER),
                    OpCode.BANK_TRANSFER, EconomyKeys.KEY_FROM, EconomyKeys.KEY_TO,
                    EconomyKeys.KEY_AMOUNT);

            final GovTreasuryPanel pWithdraw = fresh();
            ButtonMatrix.fire("TREASURY", "BANK_WITHDRAW",
                    () -> act(pWithdraw, Action.BANK_WITHDRAW),
                    OpCode.BANK_WITHDRAW, EconomyKeys.KEY_AMOUNT, GovTargets.KEY_TARGET);

            final GovTreasuryPanel pHist = fresh();
            ButtonMatrix.fire("TREASURY", "BANK_HISTORY", () -> act(pHist, Action.BANK_HISTORY),
                    OpCode.BANK_HISTORY, EconomyKeys.KEY_ACCOUNT, EconomyKeys.KEY_PAGE);

            final GovTreasuryPanel pPay = fresh();
            ButtonMatrix.fire("TREASURY", "PAY_PLAYER", () -> act(pPay, Action.PAY_PLAYER),
                    OpCode.PAY_PLAYER, GovTargets.KEY_TARGET, EconomyKeys.KEY_AMOUNT);

            final GovTreasuryPanel pPost = fresh();
            ButtonMatrix.fire("TREASURY", "CONTRACT_POST", () -> act(pPost, Action.CONTRACT_POST),
                    OpCode.CONTRACT_POST, GovTargets.KEY_TARGET);

            final GovTreasuryPanel pPayroll = fresh();
            ButtonMatrix.fire("TREASURY", "PAYROLL_RUN", () -> act(pPayroll, Action.PAYROLL_RUN),
                    OpCode.PAYROLL_RUN, EconomyKeys.KEY_SCOPE);

            final GovTreasuryPanel pCredit = fresh();
            ButtonMatrix.fire("TREASURY", "CONTRACT_CREDIT",
                    () -> act(pCredit, Action.CONTRACT_CREDIT),
                    OpCode.CONTRACT_CREDIT, EconomyKeys.KEY_ACCOUNT, EconomyKeys.KEY_AMOUNT);

            final GovTreasuryPanel pBankCredit = fresh();
            ButtonMatrix.fire("TREASURY", "CREDIT", () -> act(pBankCredit, Action.CREDIT),
                    OpCode.BANK_CREDIT, GovTargets.KEY_TARGET, EconomyKeys.KEY_AMOUNT,
                    EconomyKeys.KEY_REASON);

            final GovTreasuryPanel pBMinus = fresh();
            ButtonMatrix.fireNone("TREASURY", "BOUNTY_AMOUNT_MINUS",
                    () -> act(pBMinus, Action.BOUNTY_AMOUNT_MINUS),
                    () -> ButtonMatrix.check(pBMinus.bounty.amountBox.getValue().equals("40"),
                            "bounty amount did not step to 40, got "
                                    + pBMinus.bounty.amountBox.getValue()));

            final GovTreasuryPanel pBPlus = fresh();
            ButtonMatrix.fireNone("TREASURY", "BOUNTY_AMOUNT_PLUS",
                    () -> act(pBPlus, Action.BOUNTY_AMOUNT_PLUS),
                    () -> ButtonMatrix.check(pBPlus.bounty.amountBox.getValue().equals("60"),
                            "bounty amount did not step to 60, got "
                                    + pBPlus.bounty.amountBox.getValue()));

            final GovTreasuryPanel pPlace = fresh();
            ButtonMatrix.fire("TREASURY", "BOUNTY_PLACE", () -> act(pPlace, Action.BOUNTY_PLACE),
                    OpCode.BOUNTY_PLACE, "target", "amount");

            final GovTreasuryPanel pBRef = fresh();
            ButtonMatrix.fire("TREASURY", "BOUNTY_REFRESH",
                    () -> act(pBRef, Action.BOUNTY_REFRESH),
                    OpCode.BOUNTY_LIST, "target");
        } finally {
            ButtonMatrix.uninstall();
        }
        ButtonMatrix.summary("TREASURY");
    }
}
