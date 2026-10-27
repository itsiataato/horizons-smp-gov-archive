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

final class GovTreasuryRender {

    static void render(final GovTreasuryPanel p, final GuiGraphics graphics, final int mouseX, final int mouseY) {
        if (p.area.w() <= 0 || p.area.h() <= 0 || p.font == null) {
            return;
        }
        if (p.showTitle && p.titleRect.w() > 0) {
            graphics.drawString(p.font, p.titleBold, p.titleRect.x(),
                    p.titleRect.y(), GovTheme.TEXT, false);
            if (p.titleRect.h() > GovMetrics.LINE_TITLE) {
                final int ruleY = p.titleRect.y() + GovMetrics.LINE_TITLE;
                graphics.fill(p.titleRect.x(), ruleY, p.titleRect.right(),
                        ruleY + 1, GovTheme.BORDER);
            }
        }
        switch (p.subtab) {
            case TAB_BALANCES -> drawBalances(p, graphics, mouseX, mouseY);
            case TAB_STORE -> drawStore(p, graphics, mouseX, mouseY);
            case TAB_CONTRACTS -> drawContracts(p, graphics, mouseX, mouseY);
            case TAB_BOUNTY -> drawBounty(p, graphics, mouseX, mouseY);
            default -> {
                // No other sub-category exists.
            }
        }
    }


    static void drawBalances(final GovTreasuryPanel p, final GuiGraphics graphics, final int mouseX, final int mouseY) {
        final int balanceY = p.balanceRect.y()
                + Math.max(0, (p.balanceRect.h() - LINE) / 2);
        graphics.drawString(p.font, p.balanceView, p.balanceRect.x(), balanceY,
                GovTheme.TEXT, false);
        graphics.drawString(p.font, p.accountOwnView, p.accountOwnX, balanceY,
                GovTheme.MUTED, false);
        if (p.balanceRuleRect.w() > 0) {
            graphics.fill(p.balanceRuleRect.x(), p.balanceRuleRect.y(),
                    p.balanceRuleRect.right(), p.balanceRuleRect.y() + 1,
                    GovTheme.BORDER_SOFT);
        }
        GovBankCreditForm.drawForm(p, graphics);
        GovTreasuryActions.drawPayForm(p, graphics);
        int y = p.balanceNotesRect.y();
        final int noteBottom = p.balanceNotesRect.bottom();
        for (int index = 0; index < p.depositNoteLines.size(); index++) {
            if (y + LINE > noteBottom) {
                break;
            }
            graphics.drawString(p.font, p.depositNoteLines.get(index),
                    p.balanceNotesRect.x(), y, GovTheme.BODY, false);
            y += LINE;
        }
        y += 4;
        for (int index = 0; index < p.withdrawNoteLines.size(); index++) {
            if (y + LINE > noteBottom) {
                break;
            }
            graphics.drawString(p.font, p.withdrawNoteLines.get(index),
                    p.balanceNotesRect.x(), y, GovTheme.MUTED, false);
            y += LINE;
        }
        if (p.balanceStatusRect.w() > 0) {
            final int statusY = p.balanceStatusRect.y()
                    + Math.max(0, (p.balanceStatusRect.h() - LINE) / 2);
            graphics.drawString(p.font, p.messageView, p.balanceStatusRect.x(),
                    statusY, p.messageColor, false);
        }
        GovTreasuryRenderDetails.drawFooterRule(p, graphics, p.balanceFooterRect);
        GovTreasuryRenderDetails.drawButtons(p, graphics, mouseX, mouseY);
    }


    static void drawStore(final GovTreasuryPanel p, final GuiGraphics graphics, final int mouseX, final int mouseY) {
        if (p.storeHeaderRect.w() > 0) {
            final int textY = p.storeHeaderRect.y()
                    + Math.max(0, (p.storeHeaderRect.h() - LINE) / 2);
            graphics.drawString(p.font, p.storeBalanceView, p.storeBalanceRect.x(),
                    textY, GovTheme.TEXT, false);
            graphics.drawString(p.font, p.storePageView, p.storePageRect.x(),
                    textY, GovTheme.MUTED, false);
            graphics.fill(p.storeHeaderRect.x(), p.storeHeaderRect.bottom() - 1,
                    p.storeHeaderRect.right(), p.storeHeaderRect.bottom(),
                    GovTheme.BORDER_SOFT);
        }
        for (int index = 0; index < STORE_CELLS; index++) {
            final GovRect cell = p.storeCellRects[index];
            final StoreCellView view = p.storeCellViews[index];
            if (cell.w() <= 0 || cell.h() <= 0) {
                continue;
            }
            final boolean selected = view.filled()
                    && view.id().equals(p.storeSelectedId);
            final boolean hovered = view.filled() && cell.contains(mouseX, mouseY);
            final int fill = selected ? GovTheme.SELECTED
                    : hovered ? GovTheme.HOVER
                            : view.filled() ? GovTheme.FIELD : GovTheme.PANEL_ALT;
            graphics.fill(cell.x(), cell.y(), cell.right(), cell.bottom(), fill);
            GovTheme.border(graphics, cell,
                    selected ? GovTheme.ACCENT : GovTheme.BORDER_SOFT);
            if (!view.filled() || cell.h() < LINE + 1) {
                continue;
            }
            final int textX = cell.x() + 2;
            if (cell.h() >= LINE * 3 + 3) {
                graphics.drawString(p.font, view.name(), textX, cell.y() + 2,
                        GovTheme.TEXT, false);
                graphics.drawString(p.font, view.tier(), textX, cell.y() + LINE + 3,
                        GovTheme.BODY, false);
                graphics.drawString(p.font, view.price(), textX, cell.y() + LINE * 2 + 3,
                        GovTheme.MUTED, false);
            } else if (cell.h() >= LINE * 2 + 2) {
                graphics.drawString(p.font, view.name(), textX, cell.y() + 2,
                        GovTheme.TEXT, false);
                graphics.drawString(p.font, view.combined(), textX, cell.y() + LINE + 3,
                        GovTheme.BODY, false);
            } else {
                graphics.drawString(p.font, view.name(), textX,
                        cell.y() + Math.max(0, (cell.h() - LINE) / 2),
                        selected ? GovTheme.TEXT : GovTheme.BODY, false);
            }
        }
        if (p.storeStatusRect.w() > 0) {
            final int statusY = p.storeStatusRect.y()
                    + Math.max(0, (p.storeStatusRect.h() - LINE) / 2);
            graphics.drawString(p.font, p.messageView, p.storeStatusRect.x(),
                    statusY, p.messageColor, false);
        }
        final int footerTextY = p.storeFooterRect.y()
                + Math.max(0, (p.storeFooterRect.h() - LINE) / 2);
        graphics.drawString(p.font, p.stacksView, p.storeStacksRect.x(),
                footerTextY, GovTheme.BODY, false);
        graphics.drawString(p.font, p.totalView, p.storeTotalRect.x(),
                footerTextY, GovTheme.TEXT, false);
        GovTreasuryRenderDetails.drawFooterRule(p, graphics, p.storeFooterRect);
        GovTreasuryRenderDetails.drawButtons(p, graphics, mouseX, mouseY);
    }


    static void drawContracts(final GovTreasuryPanel p, final GuiGraphics graphics, final int mouseX, final int mouseY) {
        if (p.contractHeaderRect.w() > 0) {
            graphics.fill(p.contractHeaderRect.x(), p.contractHeaderRect.y(),
                    p.contractHeaderRect.right(), p.contractHeaderRect.bottom(),
                    GovTheme.PANEL_ALT);
            graphics.fill(p.contractHeaderRect.x(),
                    p.contractHeaderRect.bottom() - 1,
                    p.contractHeaderRect.right(), p.contractHeaderRect.bottom(),
                    GovTheme.BORDER);
            final int headerY = p.contractHeaderRect.y()
                    + Math.max(0, (p.contractHeaderRect.h() - LINE) / 2);
            for (int index = 0; index < 5; index++) {
                if (p.contractColumnW[index] > 0) {
                    graphics.drawString(p.font, p.contractHeaders[index],
                            p.contractColumnX[index], headerY, GovTheme.MUTED, false);
                }
            }
        }
        if (p.contractListRect.w() > 0 && p.contractListRect.h() > 0) {
            graphics.fill(p.contractListRect.x(), p.contractListRect.y(),
                    p.contractListRect.right(), p.contractListRect.bottom(),
                    GovTheme.FIELD);
            GovTheme.border(graphics, p.contractListRect, GovTheme.BORDER);
            if (p.contractState == LoadState.READY
                    && !p.contractViews.isEmpty()) {
                GovTreasuryRenderDetails.drawContractRows(p, graphics, mouseX, mouseY);
            }
        }
        if (p.contractStatusRect.w() > 0) {
            final int statusY = p.contractStatusRect.y()
                    + Math.max(0, (p.contractStatusRect.h() - LINE) / 2);
            graphics.drawString(p.font, p.messageView, p.contractStatusRect.x(),
                    statusY, p.messageColor, false);
            if (!p.contractPageView.isEmpty()) {
                final int pageX = p.contractStatusRect.right() - GovTreasuryViews.contractPageZone(p);
                graphics.drawString(p.font, p.contractPageView, pageX, statusY,
                        GovTheme.MUTED, false);
            }
        }
        GovTreasuryRenderDetails.drawFooterRule(p, graphics, p.contractFooterRect);
        GovTreasuryRenderDetails.drawButtons(p, graphics, mouseX, mouseY);
    }


    static void drawBounty(final GovTreasuryPanel p, final GuiGraphics graphics, final int mouseX, final int mouseY) {
        if (p.bountyFormRect.w() > 0) {
            GovBountyForm.drawForm(p, graphics);
            graphics.fill(p.bountyFormRect.x(), p.bountyFormRect.bottom() - 1,
                    p.bountyFormRect.right(), p.bountyFormRect.bottom(),
                    GovTheme.BORDER_SOFT);
        }
        if (p.bountyCountRect.w() > 0) {
            final int countY = p.bountyCountRect.y()
                    + Math.max(0, (p.bountyCountRect.h() - LINE) / 2);
            graphics.drawString(p.font, p.bountyCountView, p.bountyCountRect.x(),
                    countY, GovTheme.MUTED, false);
        }
        if (p.bountyListRect.w() > 0 && p.bountyListRect.h() > 0) {
            graphics.fill(p.bountyListRect.x(), p.bountyListRect.y(),
                    p.bountyListRect.right(), p.bountyListRect.bottom(),
                    GovTheme.FIELD);
            GovTheme.border(graphics, p.bountyListRect, GovTheme.BORDER);
            if (p.bountyState == LoadState.READY
                    && !p.bountyViews.isEmpty()) {
                GovTreasuryRenderDetails.drawBountyRows(p, graphics, mouseX, mouseY);
            }
        }
        if (p.bountyStatusRect.w() > 0) {
            final int statusY = p.bountyStatusRect.y()
                    + Math.max(0, (p.bountyStatusRect.h() - LINE) / 2);
            graphics.drawString(p.font, p.messageView, p.bountyStatusRect.x(),
                    statusY, p.messageColor, false);
        }
        GovTreasuryRenderDetails.drawFooterRule(p, graphics, p.bountyFooterRect);
        GovTreasuryRenderDetails.drawButtons(p, graphics, mouseX, mouseY);
    }

}
