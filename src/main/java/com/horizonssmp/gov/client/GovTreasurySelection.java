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

final class GovTreasurySelection {

    static void selectStore(final GovTreasuryPanel p, final String id) {
        if (id == null || id.isEmpty()) {
            return;
        }
        p.storeSelectedId = id;
        p.status = "";
        p.statusIsError = false;
        GovTreasurySupport.clampStacks(p);
        GovTreasuryViews.rebuildViews(p);
    }


    static void selectContract(final GovTreasuryPanel p, final String id) {
        if (id == null || id.isEmpty()) {
            return;
        }
        p.contractSelectedId = id;
        p.status = "";
        p.statusIsError = false;
        GovTreasuryViews.rebuildViews(p);
    }


    static void changeStorePage(final GovTreasuryPanel p, final int delta) {
        if (GovTreasurySupport.mutationPending(p)) {
            return;
        }
        final int next = GovMetrics.clamp(p.storePage + delta, 0,
                Math.max(0, p.storePages - 1));
        if (next == p.storePage) {
            return;
        }
        p.storePage = next;
        p.storeSelectedId = "";
        GovTreasuryRequests.requestStore(p, next);
    }


    static void changeContractPage(final GovTreasuryPanel p, final int delta) {
        if (GovTreasurySupport.mutationPending(p)) {
            return;
        }
        final int next = GovMetrics.clamp(p.contractPage + delta, 0,
                Math.max(0, p.contractPages - 1));
        if (next == p.contractPage) {
            return;
        }
        p.contractPage = next;
        p.contractSelectedId = "";
        p.contractScroll = 0;
        GovTreasuryRequests.requestContracts(p, next);
    }


    static void changeStacks(final GovTreasuryPanel p, final int delta) {
        if (GovTreasurySupport.mutationPending(p)) {
            return;
        }
        final int upper = Math.max(STORE_MIN_STACKS, GovTreasurySupport.maxAffordableStacks(p));
        p.stacks = GovMetrics.clamp(p.stacks + delta,
                STORE_MIN_STACKS, Math.min(STORE_MAX_STACKS, upper));
        GovTreasuryViews.rebuildViews(p);
    }


    static void moveStoreSelection(final GovTreasuryPanel p, final int delta) {
        if (p.storeRows.isEmpty()) {
            return;
        }
        int index = -1;
        for (int slot = 0; slot < p.storeRows.size(); slot++) {
            if (p.storeRows.get(slot).id().equals(p.storeSelectedId)) {
                index = slot;
                break;
            }
        }
        index = index < 0 ? 0 : GovMetrics.clamp(index + delta, 0,
                p.storeRows.size() - 1);
        selectStore(p, p.storeRows.get(index).id());
    }


    static void moveContractSelection(final GovTreasuryPanel p, final int delta) {
        if (p.contractRows.isEmpty()) {
            return;
        }
        int index = -1;
        for (int slot = 0; slot < p.contractRows.size(); slot++) {
            if (p.contractRows.get(slot).id().equals(p.contractSelectedId)) {
                index = slot;
                break;
            }
        }
        index = index < 0 ? 0 : GovMetrics.clamp(index + delta, 0,
                p.contractRows.size() - 1);
        selectContract(p, p.contractRows.get(index).id());
    }


    /**
     * Coarse amount quick-pick: one tap moves ten ingots inside the server bounds. An
     * empty or invalid box seeds the minimum first, so the first tap always lands on a
     * sane value instead of refusing.
     */
    static void stepBountyAmount(final GovTreasuryPanel p, final int delta) {
        if (p.bountyMax < p.bountyMin || p.bountyMin <= 0L) {
            return;
        }
        final long current = GovBountyForm.effectiveAmount(p);
        final long next = current == GovBountyForm.AMOUNT_INVALID ? p.bountyMin
                : Math.max(p.bountyMin, Math.min(p.bountyMax,
                        current + (long) delta * GovBountyForm.AMOUNT_STEP));
        GovBountyForm.setAmount(p, next);
        p.status = "";
        p.statusIsError = false;
        GovTreasuryViews.rebuildViews(p);
    }


    static void selectBounty(final GovTreasuryPanel p, final String id) {
        if (id == null || id.isEmpty()) {
            return;
        }
        if (GovBountyList.indexOf(p.bountyRows, id) < 0) {
            return;
        }
        p.bountySelectedId = id;
        if (p.bountyCancelArmed && !id.equals(p.bountyCancelArmedId)) {
            p.bountyCancelArmed = false;
            p.bountyCancelArmedId = "";
            p.bountyCancelArmedAt = 0L;
            p.status = "";
            p.statusIsError = false;
        }
        GovBountyList.reveal(p);
        GovTreasuryViews.rebuildViews(p);
    }


    static void moveBountySelection(final GovTreasuryPanel p, final int delta) {
        if (p.bountyRows.isEmpty()) {
            return;
        }
        int index = GovBountyList.indexOf(p.bountyRows, p.bountySelectedId);
        index = index < 0 ? 0 : GovMetrics.clamp(index + delta, 0,
                p.bountyRows.size() - 1);
        selectBounty(p, p.bountyRows.get(index).id());
    }


    /**
     * Shared two-step armed confirm for the per-row bounty cancel buttons.
     *
     * <p>Mirrors the cell delete arm: the first click arms and shows the confirm label, the
     * second click inside the window (past the floor) sends the cancel. Switching rows
     * disarms, and the arm is cleared before anything is sent.
     */
    static void submitBountyCancelArmed(final GovTreasuryPanel p, final String bountyId) {
        if (GovTreasurySupport.mutationPending(p)) {
            return;
        }
        final String id = bountyId == null ? "" : bountyId.strip();
        if (id.isEmpty()) {
            return;
        }
        final long now = System.currentTimeMillis();
        if (!p.bountyCancelArmed || !id.equals(p.bountyCancelArmedId)
                || now - p.bountyCancelArmedAt > ARM_WINDOW_MS) {
            p.bountyCancelArmed = true;
            p.bountyCancelArmedAt = now;
            p.bountyCancelArmedId = id;
            p.status = GovTreasuryViews.label(p, "gui.gov.treasury.cancel_confirm");
            p.statusIsError = false;
            GovTreasuryViews.rebuildViews(p);
            return;
        }
        if (now - p.bountyCancelArmedAt < ARM_FLOOR_MS) {
            return;
        }
        p.bountyCancelArmed = false;
        p.bountyCancelArmedAt = 0L;
        p.bountyCancelArmedId = "";
        GovBountyRequests.submitCancel(p, id);
    }


    static void disarmBountyCancel(final GovTreasuryPanel p) {
        p.bountyCancelArmed = false;
        p.bountyCancelArmedId = "";
        p.bountyCancelArmedAt = 0L;
    }

    // ------------------------------------------------------------------- render



}
