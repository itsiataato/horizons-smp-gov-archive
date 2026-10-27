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

final class GovTreasuryResponses {

    static void onActionResult(final GovTreasuryPanel p, final GovActionResultPayload payload) {
        final RequestKind kind = p.pending.remove(payload.reqId());
        if (kind == null) {
            return;
        }
        switch (kind) {
            case BALANCE -> {
                if (!payload.reqId().equals(p.latestBalanceReqId)) {
                    return;
                }
                if (payload.ok()) {
                    final Map<String, String> fields = GovFields.decode(payload.delta());
                    p.balance = GovTreasurySupport.longField(fields, EconomyKeys.KEY_BALANCE);
                    applyCanCredit(p, "true".equals(fields.get(EconomyKeys.KEY_CAN_CREDIT)));
                    p.balanceState = LoadState.READY;
                    p.balanceError = "";
                } else {
                    p.balanceState = LoadState.ERROR;
                    p.balanceError = GovTreasurySupport.fallbackError(payload,
                            "gui.gov.treasury.error");
                    applyCanCredit(p, false);
                }
            }
            case STORE_LIST -> {
                if (!payload.reqId().equals(p.latestStoreReqId)) {
                    return;
                }
                if (payload.ok()) {
                    applyStore(p, payload.delta());
                } else {
                    p.storeState = LoadState.ERROR;
                    p.storeError = GovTreasurySupport.fallbackError(payload,
                            "gui.gov.treasury.error");
                }
            }
            case CONTRACT_LIST -> {
                if (!payload.reqId().equals(p.latestContractReqId)) {
                    return;
                }
                if (payload.ok()) {
                    applyContracts(p, payload.delta());
                } else {
                    p.contractState = LoadState.ERROR;
                    p.contractError = GovTreasurySupport.fallbackError(payload,
                            "gui.gov.treasury.error");
                }
            }
            case DEPOSIT -> {
                if (payload.ok()) {
                    GovTreasurySupport.setStatus(p, payload.delta().isBlank()
                            ? GovTreasuryViews.label(p, "gui.gov.treasury.recorded") : payload.delta(), false);
                    final Map<String, String> fields = GovFields.decode(payload.delta());
                    if (fields.containsKey(EconomyKeys.KEY_BALANCE)) {
                        p.balance = GovTreasurySupport.longField(fields, EconomyKeys.KEY_BALANCE);
                        p.storeBalance = p.balance;
                    } else {
                        GovTreasuryRequests.requestBalance(p);
                    }
                } else {
                    GovTreasurySupport.setStatus(p, GovTreasurySupport.fallbackError(payload, "gui.gov.treasury.error"), true);
                }
            }
            case STORE_BUY -> {
                if (payload.ok()) {
                    final Map<String, String> fields = GovFields.decode(payload.delta());
                    if (fields.containsKey(EconomyKeys.KEY_BALANCE)) {
                        p.storeBalance = GovTreasurySupport.longField(fields, EconomyKeys.KEY_BALANCE);
                        p.balance = p.storeBalance;
                    }
                    p.storeSelectedId = "";
                    p.stacks = STORE_MIN_STACKS;
                    GovTreasuryRequests.requestStore(p, 0);
                    GovTreasurySupport.setStatus(p, payload.delta().isBlank()
                            ? GovTreasuryViews.label(p, "gui.gov.treasury.recorded") : payload.delta(), false);
                } else {
                    GovTreasurySupport.setStatus(p, GovTreasurySupport.fallbackError(payload, "gui.gov.treasury.error"), true);
                }
            }
            case CONTRACT_ACCEPT, CONTRACT_SUBMIT, CONTRACT_APPROVE, CONTRACT_VOID -> {
                if (payload.ok()) {
                    GovTreasuryRequests.requestContracts(p, p.contractPage);
                    GovTreasurySupport.setStatus(p, payload.delta().isBlank()
                            ? GovTreasuryViews.label(p, "gui.gov.treasury.recorded") : payload.delta(), false);
                } else {
                    GovTreasurySupport.setStatus(p, GovTreasurySupport.fallbackError(payload, "gui.gov.treasury.error"), true);
                }
            }
            case BANK_TRANSFER, BANK_WITHDRAW, PAY_PLAYER -> {
                if (payload.ok()) {
                    GovTreasuryRequests.requestBalance(p);
                    GovTreasurySupport.setStatus(p, payload.delta().isBlank()
                            ? GovTreasuryViews.label(p, "gui.gov.treasury.recorded") : payload.delta(), false);
                } else {
                    GovTreasurySupport.setStatus(p, GovTreasurySupport.fallbackError(payload, "gui.gov.treasury.error"), true);
                }
            }
            case BANK_HISTORY -> {
                if (payload.ok()) {
                    GovTreasurySupport.setStatus(p, payload.delta().isBlank()
                            ? GovTreasuryViews.label(p, "gui.gov.treasury.recorded") : payload.delta(), false);
                } else {
                    GovTreasurySupport.setStatus(p, GovTreasurySupport.fallbackError(payload, "gui.gov.treasury.error"), true);
                }
            }
            case CONTRACT_POST -> {
                if (payload.ok()) {
                    GovTreasuryRequests.requestContracts(p, p.contractPage);
                    GovTreasurySupport.setStatus(p, payload.delta().isBlank()
                            ? GovTreasuryViews.label(p, "gui.gov.treasury.recorded") : payload.delta(), false);
                } else {
                    GovTreasurySupport.setStatus(p, GovTreasurySupport.fallbackError(payload, "gui.gov.treasury.error"), true);
                }
            }
            case PAYROLL_RUN, CONTRACT_CREDIT, BANK_CREDIT -> {
                if (payload.ok()) {
                    GovTreasuryRequests.requestBalance(p);
                    GovTreasurySupport.setStatus(p, payload.delta().isBlank()
                            ? GovTreasuryViews.label(p, "gui.gov.treasury.recorded") : payload.delta(), false);
                } else {
                    GovTreasurySupport.setStatus(p, GovTreasurySupport.fallbackError(payload, "gui.gov.treasury.error"), true);
                }
            }
            case BOUNTY_LIST -> {
                if (!payload.reqId().equals(p.latestBountyReqId)) {
                    return;
                }
                if (payload.ok()) {
                    applyBounty(p, payload.delta());
                } else {
                    p.bountyState = LoadState.ERROR;
                    p.bountyError = GovTreasurySupport.fallbackError(payload,
                            "gui.gov.treasury.error");
                }
            }
            case BOUNTY_PLACE, BOUNTY_CANCEL -> {
                if (payload.ok()) {
                    GovBountyRequests.requestList(p);
                    GovTreasurySupport.setStatus(p, payload.delta().isBlank()
                            ? GovTreasuryViews.label(p, "gui.gov.treasury.recorded") : payload.delta(), false);
                } else {
                    GovTreasurySupport.setStatus(p, GovTreasurySupport.fallbackError(payload, "gui.gov.treasury.error"), true);
                }
            }
        }
        GovTreasuryViews.rebuildViews(p);
    }


    static void applyStore(final GovTreasuryPanel p, final String delta) {
        final Map<String, String> fields = GovFields.decode(delta);
        p.storeBalance = GovTreasurySupport.longField(fields, "store.balance");
        p.storePage = GovTreasurySupport.intField(fields, "store.page");
        p.storePages = GovTreasurySupport.intField(fields, "store.pages");
        p.storeTotal = GovTreasurySupport.intField(fields, "store.total");
        p.storeRows.clear();
        for (int index = 0; index < STORE_CELLS; index++) {
            final String prefix = "store.item." + index + ".";
            final String id = fields.get(prefix + "id");
            if (id == null || id.isBlank()) {
                continue;
            }
            p.storeRows.add(new StoreRow(
                    id,
                    fields.getOrDefault(prefix + "name", id),
                    GovTreasurySupport.intField(fields, prefix + "tier"),
                    GovTreasurySupport.longField(fields, prefix + "price"),
                    Boolean.parseBoolean(fields.getOrDefault(prefix + "canBuy", "false"))));
        }
        p.storeState = LoadState.READY;
        p.storeError = "";
        if (!p.storeSelectedId.isEmpty() && !GovTreasurySupport.storeContains(p, p.storeSelectedId)) {
            p.storeSelectedId = "";
        }
        GovTreasurySupport.clampStacks(p);
    }


    static void applyContracts(final GovTreasuryPanel p, final String delta) {
        final Map<String, String> fields = GovFields.decode(delta);
        p.contractPage = GovTreasurySupport.intField(fields, "contract.page");
        p.contractPages = GovTreasurySupport.intField(fields, "contract.pages");
        p.contractTotal = GovTreasurySupport.intField(fields, "contract.total");
        p.contractRows.clear();
        for (int index = 0; index < 20; index++) {
            final String prefix = "contract." + index + ".";
            final String id = fields.get(prefix + "id");
            if (id == null || id.isBlank()) {
                continue;
            }
            p.contractRows.add(new ContractRow(
                    id,
                    fields.getOrDefault(prefix + "builder", ""),
                    fields.getOrDefault(prefix + "state", ""),
                    GovTreasurySupport.longField(fields, prefix + "total"),
                    fields.getOrDefault(prefix + "description", "")));
        }
        p.contractState = LoadState.READY;
        p.contractError = "";
        if (!p.contractSelectedId.isEmpty() && !GovTreasurySupport.contractContains(p, p.contractSelectedId)) {
            p.contractSelectedId = "";
        }
    }


    static void applyBounty(final GovTreasuryPanel p, final String delta) {
        final Map<String, String> fields = GovFields.decode(delta);
        GovBountyList.parse(p, fields);
        p.bountyState = LoadState.READY;
        p.bountyError = "";
    }


    /**
     * Applies the server's {@code canCredit} affordance hint from a BALANCE response.
     *
     * <p>Mirrors the Audit tab's {@code canAdmin} handling: success assigns the flag verbatim
     * ({@code "true"} exactly), while any refusal clears it so a stale {@code true} can never
     * survive a failed read. Only the BALANCE path carries the flag, so no other case touches
     * it here. When the flag flips, the balances geometry is re-run so the credit form rows
     * are carved (or reclaimed by the notes) without waiting for the next resize.
     */
    static void applyCanCredit(final GovTreasuryPanel p, final boolean next) {
        if (p.canCredit == next) {
            return;
        }
        p.canCredit = next;
        if (p.font != null && p.area.w() > 0 && p.area.h() > 0) {
            GovTreasuryGeometry.layout(p, p.area.x(), p.area.y(),
                    p.area.right(), p.area.bottom(), p.font);
        }
        GovBankCreditForm.sync(p);
    }


}
