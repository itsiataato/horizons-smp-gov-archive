package com.horizonssmp.gov.client;
import com.horizonssmp.gov.client.GovTreasuryTypes.StoreRow;
import com.horizonssmp.gov.client.GovTreasuryTypes.StoreCellView;
import com.horizonssmp.gov.client.GovTreasuryTypes.ContractRow;
import com.horizonssmp.gov.client.GovTreasuryTypes.ContractView;
import com.horizonssmp.gov.client.GovTreasuryTypes.ContractHit;
import com.horizonssmp.gov.client.GovTreasuryTypes.Button;
import com.horizonssmp.gov.client.GovTreasuryTypes.RequestKind;
import com.horizonssmp.gov.client.GovTreasuryTypes.LoadState;
import com.horizonssmp.gov.client.GovTreasuryTypes.Action;
import static com.horizonssmp.gov.client.GovTreasuryTypes.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.horizonssmp.gov.common.EconomyKeys;
import com.horizonssmp.gov.common.GovFields;
import com.horizonssmp.gov.common.OpCode;
import com.horizonssmp.gov.server.GovTargets;
import com.horizonssmp.gov.network.GovActionResultPayload;
import com.horizonssmp.gov.network.GovNetwork;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

final class GovTreasuryRequests {

    static void requestForSubtab(final GovTreasuryPanel p) {
        switch (p.subtab) {
            case TAB_BALANCES -> requestBalance(p);
            case TAB_STORE -> requestStore(p, 0);
            case TAB_CONTRACTS -> requestContracts(p, p.contractPage);
            case TAB_BOUNTY -> GovBountyRequests.requestList(p);
            default -> {
                // No other sub-category exists.
            }
        }
    }


    static void requestBalance(final GovTreasuryPanel p) {
        if (p.pending.containsValue(RequestKind.BALANCE)) {
            return;
        }
        final String reqId = UUID.randomUUID().toString();
        p.pending.put(reqId, RequestKind.BALANCE);
        p.latestBalanceReqId = reqId;
        p.balanceState = LoadState.LOADING;
        p.balanceError = "";
        p.status = "";
        p.statusIsError = false;
        GovNetwork.sendRequest(OpCode.BANK_BALANCE,
                Map.of(EconomyKeys.KEY_ACCOUNT, ACCOUNT_ME), reqId);
        GovTreasuryViews.rebuildViews(p);
    }


    static void requestStore(final GovTreasuryPanel p, final int page) {
        final String reqId = UUID.randomUUID().toString();
        p.pending.put(reqId, RequestKind.STORE_LIST);
        p.latestStoreReqId = reqId;
        p.storeState = LoadState.LOADING;
        p.storeError = "";
        p.status = "";
        p.statusIsError = false;
        GovNetwork.sendRequest(OpCode.STORE_LIST,
                Map.of(EconomyKeys.KEY_PAGE, Integer.toString(Math.max(0, page))), reqId);
        GovTreasuryViews.rebuildViews(p);
    }


    static void requestContracts(final GovTreasuryPanel p, final int page) {
        final String reqId = UUID.randomUUID().toString();
        p.pending.put(reqId, RequestKind.CONTRACT_LIST);
        p.latestContractReqId = reqId;
        p.contractState = LoadState.LOADING;
        p.contractError = "";
        p.status = "";
        p.statusIsError = false;
        GovNetwork.sendRequest(OpCode.CONTRACT_LIST,
                Map.of(EconomyKeys.KEY_PAGE, Integer.toString(Math.max(0, page))), reqId);
        GovTreasuryViews.rebuildViews(p);
    }


    static void submitDeposit(final GovTreasuryPanel p) {
        if (GovTreasurySupport.mutationPending(p)) {
            return;
        }
        final String reqId = UUID.randomUUID().toString();
        p.pending.put(reqId, RequestKind.DEPOSIT);
        p.status = "";
        GovNetwork.sendRequest(OpCode.BANK_DEPOSIT, Map.of(
                EconomyKeys.KEY_AMOUNT, Integer.toString(DEPOSIT_AMOUNT),
                EconomyKeys.KEY_NONCE, UUID.randomUUID().toString()), reqId);
        GovTreasuryViews.rebuildViews(p);
    }


    static void submitBankTransfer(final GovTreasuryPanel p) {
        if (GovTreasurySupport.mutationPending(p)) {
            return;
        }
        final String reqId = UUID.randomUUID().toString();
        p.pending.put(reqId, RequestKind.BANK_TRANSFER);
        p.status = "";
        GovNetwork.sendRequest(OpCode.BANK_TRANSFER, Map.of(
                EconomyKeys.KEY_FROM, "me",
                EconomyKeys.KEY_TO, "gov",
                EconomyKeys.KEY_AMOUNT, Integer.toString(DEPOSIT_AMOUNT),
                EconomyKeys.KEY_REASON, "Treasury transfer.",
                EconomyKeys.KEY_NONCE, UUID.randomUUID().toString()), reqId);
        GovTreasuryViews.rebuildViews(p);
    }


    static void submitBankWithdraw(final GovTreasuryPanel p) {
        if (GovTreasurySupport.mutationPending(p)) {
            return;
        }
        final String reqId = UUID.randomUUID().toString();
        p.pending.put(reqId, RequestKind.BANK_WITHDRAW);
        p.status = "";
        final Map<String, String> payload = new LinkedHashMap<>();
        payload.put(EconomyKeys.KEY_AMOUNT, Integer.toString(DEPOSIT_AMOUNT));
        payload.put(EconomyKeys.KEY_NONCE, UUID.randomUUID().toString());
        payload.put(GovTargets.KEY_TARGET, GovTreasurySupport.currentPlayerName());
        GovNetwork.sendRequest(OpCode.BANK_WITHDRAW, payload, reqId);
        GovTreasuryViews.rebuildViews(p);
    }


    static void requestBankHistory(final GovTreasuryPanel p) {
        if (p.pending.containsValue(RequestKind.BANK_HISTORY)) {
            return;
        }
        final String reqId = UUID.randomUUID().toString();
        p.pending.put(reqId, RequestKind.BANK_HISTORY);
        p.status = "";
        GovNetwork.sendRequest(OpCode.BANK_HISTORY, Map.of(
                EconomyKeys.KEY_ACCOUNT, "me",
                EconomyKeys.KEY_PAGE, PAGE_ZERO), reqId);
        GovTreasuryViews.rebuildViews(p);
    }


    static void submitPayPlayer(final GovTreasuryPanel p) {
        if (GovTreasurySupport.mutationPending(p)) {
            return;
        }
        final String target = GovTreasuryActions.payTarget(p);
        if (target.isEmpty()) {
            GovTreasurySupport.setStatus(p,
                    GovTreasuryViews.label(p, "gui.gov.treasury.no_target"), true);
            GovTreasuryViews.rebuildViews(p);
            return;
        }
        String amount = GovTreasuryActions.payAmount(p);
        if (amount.isEmpty()) {
            amount = Integer.toString(DEPOSIT_AMOUNT);
        }
        final String reqId = UUID.randomUUID().toString();
        p.pending.put(reqId, RequestKind.PAY_PLAYER);
        p.status = "";
        final Map<String, String> payload = new LinkedHashMap<>();
        payload.put(EconomyKeys.KEY_AMOUNT, amount);
        payload.put(EconomyKeys.KEY_NONCE, UUID.randomUUID().toString());
        payload.put(GovTargets.KEY_TARGET, target);
        GovNetwork.sendRequest(OpCode.PAY_PLAYER, payload, reqId);
        GovTreasuryViews.rebuildViews(p);
    }


    static void submitContractPost(final GovTreasuryPanel p) {
        if (GovTreasurySupport.mutationPending(p)) {
            return;
        }
        final String reqId = UUID.randomUUID().toString();
        p.pending.put(reqId, RequestKind.CONTRACT_POST);
        p.status = "";
        final Map<String, String> payload = new LinkedHashMap<>();
        payload.put("materials", Integer.toString(DEPOSIT_AMOUNT));
        payload.put("labor", Integer.toString(DEPOSIT_AMOUNT));
        payload.put("description", "Treasury contract.");
        payload.put(EconomyKeys.KEY_NONCE, UUID.randomUUID().toString());
        payload.put(GovTargets.KEY_TARGET, GovTreasurySupport.currentPlayerName());
        GovNetwork.sendRequest(OpCode.CONTRACT_POST, payload, reqId);
        GovTreasuryViews.rebuildViews(p);
    }


    static void submitPayrollRun(final GovTreasuryPanel p) {
        if (GovTreasurySupport.mutationPending(p)) {
            return;
        }
        final String reqId = UUID.randomUUID().toString();
        p.pending.put(reqId, RequestKind.PAYROLL_RUN);
        p.status = "";
        GovNetwork.sendRequest(OpCode.PAYROLL_RUN, Map.of(
                EconomyKeys.KEY_SCOPE, "gov",
                EconomyKeys.KEY_REASON, "Payroll run.",
                EconomyKeys.KEY_NONCE, UUID.randomUUID().toString()), reqId);
        GovTreasuryViews.rebuildViews(p);
    }


    static void submitContractCredit(final GovTreasuryPanel p) {
        if (!p.canCredit || GovTreasurySupport.mutationPending(p)) {
            return;
        }
        final String reqId = UUID.randomUUID().toString();
        p.pending.put(reqId, RequestKind.CONTRACT_CREDIT);
        p.status = "";
        GovNetwork.sendRequest(OpCode.CONTRACT_CREDIT, Map.of(
                EconomyKeys.KEY_ACCOUNT, "me",
                EconomyKeys.KEY_FROM, "",
                EconomyKeys.KEY_TO, "",
                EconomyKeys.KEY_AMOUNT, Integer.toString(DEPOSIT_AMOUNT),
                EconomyKeys.KEY_REASON, "Console credit.",
                GovTargets.KEY_TARGET, "me"), reqId);
        GovTreasuryViews.rebuildViews(p);
    }


    static void submitBuy(final GovTreasuryPanel p) {
        if (GovTreasurySupport.mutationPending(p) || !GovTreasurySupport.canBuySelected(p)) {
            return;
        }
        final String reqId = UUID.randomUUID().toString();
        p.pending.put(reqId, RequestKind.STORE_BUY);
        p.status = "";
        GovNetwork.sendRequest(OpCode.STORE_BUY, Map.of(
                EconomyKeys.KEY_ITEM, p.storeSelectedId,
                EconomyKeys.KEY_STACKS, Integer.toString(p.stacks),
                EconomyKeys.KEY_NONCE, UUID.randomUUID().toString()), reqId);
        GovTreasuryViews.rebuildViews(p);
    }


    static void submitContract(final GovTreasuryPanel p, final OpCode op, final RequestKind kind,
            final boolean withReason) {
        if (GovTreasurySupport.mutationPending(p) || p.contractSelectedId.isEmpty()) {
            return;
        }
        final String reqId = UUID.randomUUID().toString();
        p.pending.put(reqId, kind);
        p.status = "";
        final Map<String, String> payload = withReason
                ? Map.of(EconomyKeys.KEY_CONTRACT, p.contractSelectedId,
                        EconomyKeys.KEY_REASON, VOID_REASON,
                        EconomyKeys.KEY_NONCE, UUID.randomUUID().toString())
                : Map.of(EconomyKeys.KEY_CONTRACT, p.contractSelectedId,
                        EconomyKeys.KEY_NONCE, UUID.randomUUID().toString());
        GovNetwork.sendRequest(op, payload, reqId);
        GovTreasuryViews.rebuildViews(p);
    }

}
