package com.horizonssmp.gov.client;

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

final class GovTreasuryViewButtons {

    static void rebuildButtons(final GovTreasuryPanel p) {
        p.buttons.clear();
        if (p.area.w() <= 0 || p.area.h() <= 0) {
            return;
        }
        final boolean pending = GovTreasurySupport.mutationPending(p);
        switch (p.subtab) {
            case TAB_BALANCES -> {
                addButton(p, p.depositRect, GovTreasuryViews.label(p, "gui.gov.treasury.deposit"),
                        Action.DEPOSIT, !pending, true);
                addButton(p, p.balanceRefreshRect, GovTreasuryViews.label(p, "gui.gov.treasury.refresh"),
                        Action.REFRESH, !pending, false);
                if (p.canCredit) {
                    addButton(p, p.credit.placeRect, GovTreasuryViews.label(p, "gui.gov.treasury.credit"),
                            Action.CREDIT, !pending, true);
                }
                addButton(p, p.balanceCloseRect, GovTreasuryViews.label(p, "gui.gov.treasury.close"),
                        Action.CLOSE, true, false);
                GovTreasuryActions.addBalanceActions(p, pending);
            }
            case TAB_STORE -> {
                addButton(p, p.storePrevRect, "<", Action.PREV,
                        p.storePage > 0 && !pending, false);
                addButton(p, p.storeNextRect, ">", Action.NEXT,
                        p.storePage + 1 < p.storePages && !pending, false);
                addButton(p, p.storeMinusRect, "-", Action.MINUS,
                        p.stacks > STORE_MIN_STACKS && !pending, false);
                addButton(p, p.storePlusRect, "+", Action.PLUS,
                        p.stacks < GovTreasurySupport.maxAffordableStacks(p) && !pending, false);
                addButton(p, p.storeBuyRect, GovTreasuryViews.label(p, "gui.gov.treasury.buy"), Action.BUY,
                        GovTreasurySupport.canBuySelected(p) && !pending, true);
                addButton(p, p.storeRefreshRect, GovTreasuryViews.label(p, "gui.gov.treasury.refresh"),
                        Action.REFRESH, !pending, false);
                addButton(p, p.storeCloseRect, GovTreasuryViews.label(p, "gui.gov.treasury.close"),
                        Action.CLOSE, true, false);
            }
            case TAB_CONTRACTS -> {
                addButton(p, p.contractPrevRect, "<", Action.PREV,
                        p.contractPage > 0 && !pending, false);
                addButton(p, p.contractNextRect, ">", Action.NEXT,
                        p.contractPage + 1 < p.contractPages && !pending, false);
                final String state = GovTreasurySupport.selectedContractState(p);
                addButton(p, p.contractButtonRects[0], GovTreasuryViews.label(p, "gui.gov.treasury.accept"),
                        Action.ACCEPT, "POSTED".equals(state) && !pending, false);
                addButton(p, p.contractButtonRects[1], GovTreasuryViews.label(p, "gui.gov.treasury.submit"),
                        Action.SUBMIT, "ACCEPTED".equals(state) && !pending, false);
                addButton(p, p.contractButtonRects[2], GovTreasuryViews.label(p, "gui.gov.treasury.approve"),
                        Action.APPROVE, "SUBMITTED".equals(state) && !pending, false);
                addButton(p, p.contractButtonRects[3], GovTreasuryViews.label(p, "gui.gov.treasury.void"),
                        Action.VOID, GovTreasurySupport.nonTerminal(state) && !pending, false);
                addButton(p, p.contractButtonRects[4], GovTreasuryViews.label(p, "gui.gov.treasury.refresh"),
                        Action.REFRESH, !pending, false);
                addButton(p, p.contractButtonRects[5], GovTreasuryViews.label(p, "gui.gov.treasury.close"),
                        Action.CLOSE, true, false);
                GovTreasuryActions.addContractActions(p, pending);
            }
            case TAB_BOUNTY -> {
                final boolean ready = p.bountyState == LoadState.READY;
                final boolean sane = p.bountyMin > 0L && p.bountyMax >= p.bountyMin;
                final long shown = GovBountyForm.effectiveAmount(p);
                addButton(p, p.bountyAmountMinusRect, "-", Action.BOUNTY_AMOUNT_MINUS,
                        ready && sane && shown > p.bountyMin && !pending, false);
                addButton(p, p.bountyAmountPlusRect, "+", Action.BOUNTY_AMOUNT_PLUS,
                        ready && sane && shown != p.bountyMax && !pending, false);
                addButton(p, p.bountyPlaceRect, GovTreasuryViews.label(p, "gui.gov.treasury.bounty_place"),
                        Action.BOUNTY_PLACE,
                        GovTreasurySupport.bountyCanPlace(p), true);
                addButton(p, p.bountyRefreshRect, GovTreasuryViews.label(p, "gui.gov.treasury.refresh"),
                        Action.BOUNTY_REFRESH, !pending, false);
                addButton(p, p.bountyCloseRect, GovTreasuryViews.label(p, "gui.gov.treasury.close"),
                        Action.CLOSE, true, false);
            }
            default -> {
                // No other sub-category exists.
            }
        }
    }


    static void addButton(final GovTreasuryPanel p, final GovRect rect, final String rawLabel, final Action action,
            final boolean enabled, final boolean primary) {
        if (rect.w() <= 0 || rect.h() <= 0 || rawLabel.isEmpty()) {
            return;
        }
        final String clipped = GovText.clip(p.font, rawLabel,
                Math.max(0, rect.w() - 8));
        p.buttons.add(new Button(rect, clipped, action, enabled, primary));
    }


    static void rebuildContractHits(final GovTreasuryPanel p) {
        p.contractHits.clear();
        if (p.contractListRect.w() <= 0 || p.contractListRect.h() <= 0) {
            return;
        }
        final int top = p.contractListRect.y() + 1;
        final int bottom = p.contractListRect.bottom() - 1;
        int y = top - p.contractScroll;
        for (final ContractView view : p.contractViews) {
            if (y >= top && y + ROW_H <= bottom) {
                p.contractHits.add(new ContractHit(
                        new GovRect(p.contractListRect.x() + 1, y,
                                Math.max(0, p.contractListRect.w() - 2), ROW_H),
                        view.id()));
            }
            y += ROW_H;
        }
    }


    /**
     * Rebuilds the bounty row-body hits plus one cancel hit per visible mine row.
     *
     * <p>Only rows the viewer posted carry a cancel button; the server enforces the
     * owner-only rule, so a button on any other row would only ever refuse. Hits stay
     * empty until the list is READY, so a click can never act on a stale result set.
     */
    static void rebuildBountyHits(final GovTreasuryPanel p) {
        p.bountyHits.clear();
        p.bountyCancelHits.clear();
        if (p.bountyListRect.w() <= 0 || p.bountyListRect.h() <= 0) {
            return;
        }
        if (p.bountyState != LoadState.READY) {
            return;
        }
        final int top = p.bountyListRect.y() + 1;
        final int bottom = p.bountyListRect.bottom() - 1;
        final boolean free = !GovTreasurySupport.mutationPending(p);
        for (int slot = 0; slot < p.bountyVisibleRows; slot++) {
            final int index = p.bountyScroll + slot;
            if (index >= p.bountyViews.size() || index >= p.bountyRows.size()) {
                break;
            }
            final int y = p.bountyListRect.y() + slot * ROW_H;
            if (y < top || y + ROW_H > bottom) {
                continue;
            }
            final GovBountyList.BountyView view = p.bountyViews.get(index);
            final GovBountyList.BountyRow row = p.bountyRows.get(index);
            final GovRect hit = new GovRect(p.bountyListRect.x() + 1, y,
                    Math.max(0, p.bountyListRect.w() - 2), ROW_H);
            p.bountyHits.add(new ContractHit(hit, view.id()));
            if (!row.mine()) {
                continue;
            }
            final int cancelW = GovMetrics.span(hit.w(), 30, 26, 40);
            final GovRect cancel = new GovRect(
                    Math.max(hit.x(), hit.right() - cancelW - 2), y + 1,
                    cancelW, Math.max(0, hit.h() - 2)).clamp(p.bountyListRect);
            if (cancel.w() > 0 && cancel.h() > 0 && free) {
                p.bountyCancelHits.add(new ContractHit(cancel, view.id()));
            }
        }
    }



}
