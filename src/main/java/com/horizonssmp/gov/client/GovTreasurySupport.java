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

final class GovTreasurySupport {

    static String shortId(final String id) {
        if (id == null) {
            return "";
        }
        return id.length() <= 8 ? id : id.substring(0, 8);
    }


    static boolean nonTerminal(final String state) {
        return "POSTED".equals(state) || "ACCEPTED".equals(state)
                || "SUBMITTED".equals(state);
    }


    static String selectedContractState(final GovTreasuryPanel p) {
        for (final ContractRow row : p.contractRows) {
            if (row.id().equals(p.contractSelectedId)) {
                return row.state();
            }
        }
        return "";
    }


    static boolean mutationPending(final GovTreasuryPanel p) {
        for (final RequestKind kind : p.pending.values()) {
            if (kind.mutation) {
                return true;
            }
        }
        return false;
    }

    // ----------------------------------------------------------------- requests


    static String fallbackError(final GovActionResultPayload payload, final String key) {
        return payload.errorText().isBlank()
                ? Component.translatable(key).getString() : payload.errorText();
    }


    static boolean storeContains(final GovTreasuryPanel p, final String id) {
        for (final StoreRow row : p.storeRows) {
            if (row.id().equals(id)) {
                return true;
            }
        }
        return false;
    }


    static StoreRow selectedStoreRow(final GovTreasuryPanel p) {
        for (final StoreRow row : p.storeRows) {
            if (row.id().equals(p.storeSelectedId)) {
                return row;
            }
        }
        return null;
    }

    /** The stack ceiling an affordable purchase allows; zero when nothing is selected. */


    static int maxAffordableStacks(final GovTreasuryPanel p) {
        final StoreRow row = selectedStoreRow(p);
        if (row == null || row.price() <= 0L || p.storeBalance <= 0L) {
            return 0;
        }
        final long affordable = p.storeBalance / row.price();
        return (int) Math.max(0L, Math.min(STORE_MAX_STACKS, affordable));
    }

    /** Never lets a stored stack count exceed what the balance can pay for. */


    static void clampStacks(final GovTreasuryPanel p) {
        final int upper = Math.max(STORE_MIN_STACKS, maxAffordableStacks(p));
        p.stacks = GovMetrics.clamp(p.stacks, STORE_MIN_STACKS,
                Math.min(STORE_MAX_STACKS, upper));
    }


    static boolean canBuySelected(final GovTreasuryPanel p) {
        final StoreRow row = selectedStoreRow(p);
        if (row == null || !row.canBuy() || row.price() <= 0L
                || p.stacks < STORE_MIN_STACKS || p.storeBalance <= 0L) {
            return false;
        }
        return row.price() <= p.storeBalance / p.stacks;
    }


    /**
     * True when the place form has a typed target, a parseable amount, sane bounds
     * and no mutation in flight. The target is free text now (offline names welcome);
     * the server resolves it and refuses typos with {@code BAD_TARGET}.
     */
    static boolean bountyCanPlace(final GovTreasuryPanel p) {
        if (p.bountyState != LoadState.READY || mutationPending(p)) {
            return false;
        }
        if (p.bountyMax < p.bountyMin || p.bountyMin <= 0L) {
            return false;
        }
        return !GovBountyForm.target(p).isEmpty()
                && GovBountyForm.effectiveAmount(p) != GovBountyForm.AMOUNT_INVALID;
    }


    static boolean contractContains(final GovTreasuryPanel p, final String id) {
        for (final ContractRow row : p.contractRows) {
            if (row.id().equals(id)) {
                return true;
            }
        }
        return false;
    }


    static int intField(final Map<String, String> fields, final String key) {
        return com.horizonssmp.gov.common.GovFields.intField(fields, key);
    }


    static long longField(final Map<String, String> fields, final String key) {
        return com.horizonssmp.gov.common.GovFields.longField(fields, key);
    }


    static void setStatus(final GovTreasuryPanel p, final String text, final boolean error) {
        p.status = text == null ? "" : text;
        p.statusIsError = error;
    }

    // ---------------------------------------------------------------- selection


    static String currentPlayerName() {
        final net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        return mc != null && mc.player != null ? mc.player.getName().getString() : "";
    }


}
