package com.horizonssmp.gov.client;

import com.horizonssmp.gov.client.GovMetrics;
import com.horizonssmp.gov.client.GovRect;
import com.horizonssmp.gov.client.GovTheme;
import net.minecraft.client.gui.Font;

final class GovTreasuryTypes {

    static final int TAB_BALANCES = 0;
    static final int TAB_STORE = 1;
    static final int TAB_CONTRACTS = 2;
    static final int TAB_BOUNTY = 3;

    /** Two-click confirm floor and window for the per-row bounty cancel, as FINE_WAIVE. */
    static final long ARM_FLOOR_MS = 600L;
    static final long ARM_WINDOW_MS = 5000L;

    static final int STORE_COLUMNS = 9;
    static final int STORE_ROWS = 4;
    static final int STORE_CELLS = STORE_COLUMNS * STORE_ROWS;
    static final int STORE_MIN_STACKS = 1;
    static final int STORE_MAX_STACKS = 10;

    static final int ROW_H = GovMetrics.ROW_H_COMPACT;
    static final int LINE = 9;
    static final int PAD = GovMetrics.GAP_XS;
    static final int FOOTER_H = GovMetrics.BUTTON_H;
    static final int STATUS_H = 10;
    static final int TITLE_H = GovMetrics.LINE_TITLE + 2;
    static final int BUTTON_H = GovMetrics.BUTTON_H;
    static final int CONTRACT_NAV = 16;
    static final int CONTRACT_NAV_GAP = 2;
    static final int CONTRACT_PAGE_W = 56;
    static final int DEPOSIT_AMOUNT = 64;

    static final String ACCOUNT_ME = "me";
    static final String VOID_REASON = "No longer required.";
    static final String PAGE_ZERO = "0";

    static final GovRect NONE = new GovRect(0, 0, 0, 0);

    enum RequestKind {
        BALANCE(false),
        STORE_LIST(false),
        CONTRACT_LIST(false),
        BOUNTY_LIST(false),
        BOUNTY_PLACE(true),
        BOUNTY_CANCEL(true),
        DEPOSIT(true),
        STORE_BUY(true),
        CONTRACT_ACCEPT(true),
        CONTRACT_SUBMIT(true),
        CONTRACT_APPROVE(true),
        CONTRACT_VOID(true),
        BANK_TRANSFER(true),
        BANK_WITHDRAW(true),
        BANK_HISTORY(false),
        PAY_PLAYER(true),
        CONTRACT_POST(true),
        PAYROLL_RUN(true),
        CONTRACT_CREDIT(true),
        BANK_CREDIT(true);

        final boolean mutation;

        RequestKind(final boolean mutation) {
            this.mutation = mutation;
        }
    }

    enum LoadState {
        NONE, LOADING, READY, ERROR
    }

    enum Action {
        PREV, NEXT, MINUS, PLUS, BUY, CLOSE,
        DEPOSIT, REFRESH,
        ACCEPT, SUBMIT, APPROVE, VOID,
        BANK_TRANSFER, BANK_WITHDRAW, BANK_HISTORY, PAY_PLAYER,
        CONTRACT_POST, PAYROLL_RUN, CONTRACT_CREDIT, CREDIT,
        BOUNTY_AMOUNT_MINUS, BOUNTY_AMOUNT_PLUS,
        BOUNTY_PLACE, BOUNTY_REFRESH
    }

    record StoreRow(String id, String name, int tier, long price, boolean canBuy) {
    }

    record StoreCellView(
            String id, String name, String tier, String price, String combined) {

        static final StoreCellView EMPTY =
                new StoreCellView("", "", "", "", "");

        boolean filled() {
            return !this.id.isEmpty();
        }
    }

    record ContractRow(
            String id, String builder, String state, long total, String description) {
    }

    record ContractView(
            String id, String state, String builder, String total, String description) {
    }

    record ContractHit(GovRect rect, String id) {
    }

    record Button(
            GovRect rect, String label, Action action, boolean enabled, boolean primary) {
    }

}
