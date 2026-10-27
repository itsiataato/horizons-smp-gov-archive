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

final class GovTreasuryInput {

    static boolean mouseClicked(final GovTreasuryPanel p, final double mouseX, final double mouseY, final int button) {
        if (!p.active || button != 0) {
            return false;
        }
        for (int index = 0; index < p.buttons.size(); index++) {
            final Button hit = p.buttons.get(index);
            if (hit.enabled() && hit.rect().contains(mouseX, mouseY)) {
                handleAction(p, hit.action());
                return true;
            }
        }
        if (p.subtab == TAB_STORE) {
            for (int index = 0; index < STORE_CELLS; index++) {
                final StoreCellView view = p.storeCellViews[index];
                if (view.filled() && p.storeCellRects[index].contains(mouseX, mouseY)) {
                    GovTreasurySelection.selectStore(p, view.id());
                    return true;
                }
            }
        }
        if (p.subtab == TAB_CONTRACTS) {
            for (int index = 0; index < p.contractHits.size(); index++) {
                final ContractHit hit = p.contractHits.get(index);
                if (hit.rect().contains(mouseX, mouseY)) {
                    GovTreasurySelection.selectContract(p, hit.id());
                    return true;
                }
            }
        }
        if (p.subtab == TAB_BOUNTY) {
            for (int index = 0; index < p.bountyCancelHits.size(); index++) {
                final ContractHit hit = p.bountyCancelHits.get(index);
                if (hit.rect().contains(mouseX, mouseY)) {
                    GovTreasurySelection.submitBountyCancelArmed(p, hit.id());
                    return true;
                }
            }
            for (int index = 0; index < p.bountyHits.size(); index++) {
                final ContractHit hit = p.bountyHits.get(index);
                if (hit.rect().contains(mouseX, mouseY)) {
                    GovTreasurySelection.selectBounty(p, hit.id());
                    return true;
                }
            }
        }
        return false;
    }


    static boolean mouseScrolled(final GovTreasuryPanel p, final double mouseX, final double mouseY, final double scrollY) {
        if (!p.active) {
            return false;
        }
        if (p.subtab == TAB_BOUNTY) {
            if (p.bountyListRect.w() <= 0
                    || !p.bountyListRect.contains(mouseX, mouseY)
                    || p.bountyScrollMax <= 0) {
                return false;
            }
            p.bountyScroll = GovMetrics.clamp(
                    p.bountyScroll - (int) (scrollY * ROW_H), 0,
                    p.bountyScrollMax);
            GovTreasuryViewButtons.rebuildBountyHits(p);
            return true;
        }
        if (p.subtab != TAB_CONTRACTS) {
            return false;
        }
        if (p.contractListRect.w() <= 0
                || !p.contractListRect.contains(mouseX, mouseY)
                || p.contractScrollMax <= 0) {
            return false;
        }
        p.contractScroll = GovMetrics.clamp(
                p.contractScroll - (int) (scrollY * ROW_H), 0,
                p.contractScrollMax);
        GovTreasuryViewButtons.rebuildContractHits(p);
        return true;
    }


    static boolean keyPressed(final GovTreasuryPanel p, final int keyCode, final int scanCode, final int modifiers) {
        if (!p.active) {
            return false;
        }
        if (p.subtab == TAB_STORE) {
            if (keyCode == GLFW.GLFW_KEY_LEFT) {
                GovTreasurySelection.moveStoreSelection(p, -1);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_RIGHT) {
                GovTreasurySelection.moveStoreSelection(p, 1);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_UP) {
                GovTreasurySelection.moveStoreSelection(p, -STORE_COLUMNS);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_DOWN) {
                GovTreasurySelection.moveStoreSelection(p, STORE_COLUMNS);
                return true;
            }
        }
        if (p.subtab == TAB_CONTRACTS) {
            if (keyCode == GLFW.GLFW_KEY_UP) {
                GovTreasurySelection.moveContractSelection(p, -1);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_DOWN) {
                GovTreasurySelection.moveContractSelection(p, 1);
                return true;
            }
        }
        if (p.subtab == TAB_BOUNTY) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE && p.bountyCancelArmed) {
                GovTreasurySelection.disarmBountyCancel(p);
                p.status = "";
                p.statusIsError = false;
                GovTreasuryViews.rebuildViews(p);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_UP) {
                GovTreasurySelection.moveBountySelection(p, -1);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_DOWN) {
                GovTreasurySelection.moveBountySelection(p, 1);
                return true;
            }
            if (!p.host.visibleFieldFocused()
                    && (keyCode == GLFW.GLFW_KEY_MINUS || keyCode == GLFW.GLFW_KEY_KP_SUBTRACT)) {
                GovTreasurySelection.stepBountyAmount(p, -1);
                return true;
            }
            if (!p.host.visibleFieldFocused()
                    && (keyCode == GLFW.GLFW_KEY_EQUAL || keyCode == GLFW.GLFW_KEY_KP_ADD)) {
                GovTreasurySelection.stepBountyAmount(p, 1);
                return true;
            }
            if (GovKeyRepeat.isEnter(keyCode)) {
                if (p.enterRepeat.accepts(System.currentTimeMillis())) {
                    if (p.bountyCancelArmed) {
                        GovTreasurySelection.submitBountyCancelArmed(p, p.bountyCancelArmedId);
                    } else {
                        GovBountyRequests.submitPlace(p);
                    }
                }
                return true;
            }
        }
        if (p.subtab == TAB_BALANCES) {
            if (GovKeyRepeat.isEnter(keyCode)) {
                if (p.enterRepeat.accepts(System.currentTimeMillis())) {
                    if (isPayBoxFocused(p)) {
                        GovTreasuryRequests.submitPayPlayer(p);
                    } else {
                        GovBankCreditRequests.submit(p);
                    }
                }
                return true;
            }
        }
        return GovTreasuryActions.keyPressed(p, keyCode, scanCode, modifiers);
    }


    static boolean isPayBoxFocused(final GovTreasuryPanel p) {
        if (p.actions.payTargetBox != null && p.host.getFocused() == p.actions.payTargetBox) {
            return true;
        }
        return p.actions.payAmountBox != null && p.host.getFocused() == p.actions.payAmountBox;
    }


    static void handleAction(final GovTreasuryPanel p, final Action action) {
        switch (action) {
            case PREV -> {
                if (p.subtab == TAB_CONTRACTS) {
                    GovTreasurySelection.changeContractPage(p, -1);
                } else {
                    GovTreasurySelection.changeStorePage(p, -1);
                }
            }
            case NEXT -> {
                if (p.subtab == TAB_CONTRACTS) {
                    GovTreasurySelection.changeContractPage(p, 1);
                } else {
                    GovTreasurySelection.changeStorePage(p, 1);
                }
            }
            case MINUS -> GovTreasurySelection.changeStacks(p, -1);
            case PLUS -> GovTreasurySelection.changeStacks(p, 1);
            case BUY -> GovTreasuryRequests.submitBuy(p);
            case CLOSE -> p.host.onClose();
            case DEPOSIT -> GovTreasuryRequests.submitDeposit(p);
            case REFRESH -> {
                switch (p.subtab) {
                    case TAB_STORE -> GovTreasuryRequests.requestStore(p, p.storePage);
                    case TAB_CONTRACTS -> GovTreasuryRequests.requestContracts(p, p.contractPage);
                    case TAB_BOUNTY -> GovBountyRequests.requestList(p);
                    default -> GovTreasuryRequests.requestBalance(p);
                }
            }
            case ACCEPT -> GovTreasuryRequests.submitContract(p, OpCode.CONTRACT_ACCEPT,
                    RequestKind.CONTRACT_ACCEPT, false);
            case SUBMIT -> GovTreasuryRequests.submitContract(p, OpCode.CONTRACT_SUBMIT,
                    RequestKind.CONTRACT_SUBMIT, false);
            case APPROVE -> GovTreasuryRequests.submitContract(p, OpCode.CONTRACT_APPROVE,
                    RequestKind.CONTRACT_APPROVE, false);
            case VOID -> GovTreasuryRequests.submitContract(p, OpCode.CONTRACT_VOID,
                    RequestKind.CONTRACT_VOID, true);
            case BANK_TRANSFER -> GovTreasuryRequests.submitBankTransfer(p);
            case BANK_WITHDRAW -> GovTreasuryRequests.submitBankWithdraw(p);
            case BANK_HISTORY -> GovTreasuryRequests.requestBankHistory(p);
            case PAY_PLAYER -> GovTreasuryRequests.submitPayPlayer(p);
            case CONTRACT_POST -> GovTreasuryRequests.submitContractPost(p);
            case PAYROLL_RUN -> GovTreasuryRequests.submitPayrollRun(p);
            case CONTRACT_CREDIT -> GovTreasuryRequests.submitContractCredit(p);
            case CREDIT -> GovBankCreditRequests.submit(p);
            case BOUNTY_AMOUNT_MINUS -> GovTreasurySelection.stepBountyAmount(p, -1);
            case BOUNTY_AMOUNT_PLUS -> GovTreasurySelection.stepBountyAmount(p, 1);
            case BOUNTY_PLACE -> GovBountyRequests.submitPlace(p);
            case BOUNTY_REFRESH -> GovBountyRequests.requestList(p);
        }
    }


}
