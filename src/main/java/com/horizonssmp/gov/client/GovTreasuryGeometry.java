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

final class GovTreasuryGeometry {

    static void layout(final GovTreasuryPanel p, final int left, final int top, final int right, final int bottom,
            final Font font) {
        p.font = font;
        final int width = Math.max(0, right - left);
        final int height = Math.max(0, bottom - top);
        p.area = new GovRect(left, top, width, height);
        p.showTitle = height >= 120;
        if (width <= 0 || height <= 0 || font == null) {
            p.titleRect = NONE;
            p.balanceRect = NONE;
            p.balanceRuleRect = NONE;
            p.balanceNotesRect = NONE;
            p.balanceStatusRect = NONE;
            p.balanceFooterRect = NONE;
            p.storeHeaderRect = NONE;
            p.storeStatusRect = NONE;
            p.storeGridRect = NONE;
            p.storeFooterRect = NONE;
            p.storeRefreshRect = NONE;
            p.contractHeaderRect = NONE;
            p.contractListRect = NONE;
            p.contractStatusRect = NONE;
            p.contractFooterRect = NONE;
            p.contractPrevRect = NONE;
            p.contractNextRect = NONE;
            Arrays.fill(p.storeCellRects, NONE);
            Arrays.fill(p.contractButtonRects, NONE);
            GovBountyGeometry.zero(p);
            GovBankCreditForm.zero(p);
            GovTreasuryActions.zero(p);
            return;
        }
        computeBalancesGeometry(p);
        computeStoreGeometry(p);
        computeContractsGeometry(p);
        GovBountyGeometry.compute(p);
        GovTreasuryViews.rebuildViews(p);
    }

    // ---------------------------------------------------------------- geometry


    static GovRect rect(final GovTreasuryPanel p, final int x, final int y, final int width, final int height) {
        return new GovRect(x, y, Math.max(0, width), Math.max(0, height)).clamp(p.area);
    }


    static void computeBalancesGeometry(final GovTreasuryPanel p) {
        final int innerX = p.area.x() + PAD;
        final int innerW = Math.max(0, p.area.w() - PAD * 2);
        final int footerY = Math.max(p.area.y() + PAD,
                p.area.bottom() - PAD - FOOTER_H);
        int y = p.area.y() + PAD;
        if (p.showTitle) {
            p.titleRect = rect(p, innerX, y, innerW, TITLE_H);
            y += TITLE_H + 2;
        } else {
            p.titleRect = NONE;
        }
        p.balanceRect = rect(p, innerX, y, innerW, LINE + 3);
        p.balanceRuleRect = rect(p, innerX, p.balanceRect.bottom() + 1, innerW, 1);
        p.balanceStatusRect = rect(p, innerX, Math.max(y,
                footerY - 2 - STATUS_H), innerW, STATUS_H);
        final int notesTop = p.balanceRuleRect.bottom() + 5;
        p.balanceNotesRect = rect(p, innerX, notesTop, innerW,
                Math.max(0, p.balanceStatusRect.y() - 3 - notesTop));
        GovTreasuryActions.computeBalances(p);
        GovBankCreditForm.compute(p);
        p.balanceFooterRect = rect(p, innerX, footerY, innerW, FOOTER_H);

        final int gap = GovMetrics.GAP_XS;
        final int available = Math.max(0, innerW - gap * 2);
        final int closeW = Math.min(44, available / 3);
        final int refreshW = Math.min(56, Math.max(0, (available - closeW) / 2));
        final int depositW = Math.max(0, available - closeW - refreshW);
        int x = innerX;
        p.depositRect = rect(p, x, footerY, depositW, BUTTON_H);
        x += depositW + gap;
        p.balanceRefreshRect = rect(p, x, footerY, refreshW, BUTTON_H);
        p.balanceCloseRect = rect(p, innerX + innerW - closeW, footerY, closeW, BUTTON_H);
    }


    static void computeStoreGeometry(final GovTreasuryPanel p) {
        final int innerX = p.area.x() + PAD;
        final int innerW = Math.max(0, p.area.w() - PAD * 2);
        final int footerY = Math.max(p.area.y() + PAD,
                p.area.bottom() - PAD - FOOTER_H);
        int y = p.area.y() + PAD;
        if (p.showTitle) {
            p.titleRect = rect(p, innerX, y, innerW, TITLE_H);
            y += TITLE_H + 2;
        } else {
            p.titleRect = NONE;
        }
        p.storeHeaderRect = rect(p, innerX, y, innerW, BUTTON_H);
        p.storeNextRect = rect(p, innerX + innerW - BUTTON_H, y, BUTTON_H, BUTTON_H);
        p.storePrevRect = rect(p, p.storeNextRect.x() - 2 - BUTTON_H, y,
                BUTTON_H, BUTTON_H);
        final int pageW = Math.min(64, Math.max(0, innerW / 3));
        p.storePageRect = rect(p, p.storePrevRect.x() - 4 - pageW, y, pageW, BUTTON_H);
        p.storeBalanceRect = rect(p, innerX, y,
                Math.max(0, p.storePageRect.x() - innerX - 4), BUTTON_H);

        p.storeStatusRect = rect(p, innerX, Math.max(y + BUTTON_H + 2,
                footerY - 2 - STATUS_H), innerW, STATUS_H);
        final int gridTop = p.storeHeaderRect.bottom() + 4;
        final int gridBottom = p.storeStatusRect.y() - 2;
        p.storeGridRect = rect(p, innerX, gridTop, innerW,
                Math.max(0, gridBottom - gridTop));

        final int gap = 1;
        final int gridW = p.storeGridRect.w();
        final int gridH = p.storeGridRect.h();
        final int cellW = gridW > 0
                ? Math.max(0, (gridW - gap * (STORE_COLUMNS - 1)) / STORE_COLUMNS) : 0;
        final int cellH = gridH > 0
                ? Math.max(0, (gridH - gap * (STORE_ROWS - 1)) / STORE_ROWS) : 0;
        for (int row = 0; row < STORE_ROWS; row++) {
            for (int column = 0; column < STORE_COLUMNS; column++) {
                final int x = p.storeGridRect.x() + column * (cellW + gap);
                final int cellY = p.storeGridRect.y() + row * (cellH + gap);
                final int width = column == STORE_COLUMNS - 1
                        ? Math.max(0, p.storeGridRect.right() - x) : cellW;
                final int height = row == STORE_ROWS - 1
                        ? Math.max(0, p.storeGridRect.bottom() - cellY) : cellH;
                p.storeCellRects[row * STORE_COLUMNS + column] =
                        new GovRect(x, cellY, width, height).clamp(p.storeGridRect);
            }
        }
        p.storeFooterRect = rect(p, innerX, footerY, innerW, FOOTER_H);

        final int footerGap = 2;
        final int closeW = Math.min(42, Math.max(0, innerW / 5));
        final int buyW = Math.min(48, Math.max(0, innerW / 5));
        final int refreshW = Math.min(52, Math.max(0, innerW / 5));
        p.storeCloseRect = rect(p, innerX + innerW - closeW, footerY, closeW, BUTTON_H);
        p.storeBuyRect = rect(p, p.storeCloseRect.x() - footerGap - buyW, footerY,
                buyW, BUTTON_H);
        p.storeRefreshRect = rect(p, p.storeBuyRect.x() - footerGap - refreshW, footerY,
                refreshW, BUTTON_H);
        p.storeMinusRect = rect(p, innerX, footerY, BUTTON_H, BUTTON_H);
        final int stacksW = Math.min(58, Math.max(0, innerW / 4));
        p.storeStacksRect = rect(p, p.storeMinusRect.right() + footerGap, footerY,
                stacksW, BUTTON_H);
        p.storePlusRect = rect(p, p.storeStacksRect.right() + footerGap, footerY,
                BUTTON_H, BUTTON_H);
        final int totalX = p.storePlusRect.right() + 2;
        p.storeTotalRect = rect(p, totalX, footerY,
                Math.max(0, p.storeRefreshRect.x() - 2 - totalX), BUTTON_H);
    }


    static void computeContractsGeometry(final GovTreasuryPanel p) {
        final int innerX = p.area.x() + PAD;
        final int innerW = Math.max(0, p.area.w() - PAD * 2);
        final int footerY = Math.max(p.area.y() + PAD,
                p.area.bottom() - PAD - FOOTER_H);
        int y = p.area.y() + PAD;
        if (p.showTitle) {
            p.titleRect = rect(p, innerX, y, innerW, TITLE_H);
            y += TITLE_H + 2;
        } else {
            p.titleRect = NONE;
        }
        p.contractHeaderRect = rect(p, innerX, y, innerW, CONTRACT_NAV);
        p.contractNextRect = rect(p, p.contractHeaderRect.right() - CONTRACT_NAV, y,
                CONTRACT_NAV, CONTRACT_NAV);
        p.contractPrevRect = rect(p, p.contractNextRect.x() - CONTRACT_NAV_GAP - CONTRACT_NAV,
                y, CONTRACT_NAV, CONTRACT_NAV);
        p.contractFooterRect = rect(p, innerX, footerY, innerW, FOOTER_H);
        final int actionsY = footerY - 2 - BUTTON_H;
        p.contractStatusRect = rect(p, innerX, Math.max(y + CONTRACT_NAV + 1,
                actionsY - 2 - STATUS_H), innerW, STATUS_H);
        final int listTop = p.contractHeaderRect.bottom() + 1;
        p.contractListRect = rect(p, innerX, listTop, innerW,
                Math.max(0, p.contractStatusRect.y() - 2 - listTop));
        computeContractColumns(p);
        computeContractButtons(p);
        GovTreasuryActions.computeContracts(p, actionsY);
    }


    static void computeContractColumns(final GovTreasuryPanel p) {
        final int reserved = CONTRACT_NAV * 2 + CONTRACT_NAV_GAP + 4;
        final int inner = Math.max(0, p.contractListRect.w() - 8 - reserved);
        final int[] share = {30, 14, 18, 14, 24};
        int x = p.contractListRect.x() + 4;
        int used = 0;
        for (int index = 0; index < 5; index++) {
            final int width = index == 4
                    ? Math.max(0, inner - used) : inner * share[index] / 100;
            p.contractColumnX[index] = x;
            p.contractColumnW[index] = Math.max(0, width);
            x += width;
            used += width;
        }
    }


    static void computeContractButtons(final GovTreasuryPanel p) {
        final int innerW = p.contractListRect.w() > 0
                ? p.contractListRect.w() : p.area.w() - PAD * 2;
        final int x = p.area.x() + PAD;
        final int gap = 2;
        final int[] weights = {38, 40, 48, 32, 44, 38};
        int totalWeight = 0;
        for (final int weight : weights) {
            totalWeight += weight;
        }
        final int available = Math.max(0, innerW - gap * (weights.length - 1));
        int used = 0;
        for (int index = 0; index < weights.length; index++) {
            final int width = index == weights.length - 1
                    ? Math.max(0, available - used)
                    : Math.max(0, available * weights[index] / totalWeight);
            p.contractButtonRects[index] = rect(p, x + used,
                    p.area.bottom() - PAD - FOOTER_H, width, BUTTON_H);
            used += width + gap;
        }
    }

    // ------------------------------------------------------------------ caches


}
