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

final class GovTreasuryRenderDetails {

    static void drawContractRows(final GovTreasuryPanel p, final GuiGraphics graphics, final int mouseX,
            final int mouseY) {
        final int top = p.contractListRect.y() + 1;
        final int bottom = p.contractListRect.bottom() - 1;
        graphics.enableScissor(p.contractListRect.x() + 1, top,
                p.contractListRect.right() - 1, bottom);
        try {
            int y = top - p.contractScroll;
            for (int index = 0; index < p.contractViews.size(); index++) {
                final ContractView view = p.contractViews.get(index);
                if (GovFit.fitsWithin(y, ROW_H, top, bottom)) {
                    final boolean selected = view.id().equals(p.contractSelectedId);
                    final boolean hovered = mouseX >= p.contractListRect.x() + 1
                            && mouseX < p.contractListRect.right() - 1
                            && mouseY >= y && mouseY < y + ROW_H;
                    if (selected) {
                        graphics.fill(p.contractListRect.x() + 1, y,
                                p.contractListRect.right() - 1, y + ROW_H,
                                GovTheme.SELECTED);
                        graphics.fill(p.contractListRect.x() + 1, y,
                                p.contractListRect.x() + 3, y + ROW_H,
                                GovTheme.ACCENT);
                    } else if (hovered) {
                        graphics.fill(p.contractListRect.x() + 1, y,
                                p.contractListRect.right() - 1, y + ROW_H,
                                GovTheme.HOVER);
                    }
                    final int textY = y + Math.max(0, (ROW_H - LINE) / 2);
                    final int color = selected ? GovTheme.TEXT : GovTheme.BODY;
                    graphics.drawString(p.font, view.id(), p.contractColumnX[0],
                            textY, color, false);
                    graphics.drawString(p.font, view.state(), p.contractColumnX[1],
                            textY, GovTheme.TEXT, false);
                    graphics.drawString(p.font, view.builder(), p.contractColumnX[2],
                            textY, color, false);
                    graphics.drawString(p.font, view.total(), p.contractColumnX[3],
                            textY, color, false);
                    graphics.drawString(p.font, view.description(),
                            p.contractColumnX[4], textY, GovTheme.MUTED, false);
                    if (index + 1 < p.contractViews.size()) {
                        graphics.fill(p.contractListRect.x() + 4,
                                y + ROW_H - 1, p.contractListRect.right() - 4,
                                y + ROW_H, GovTheme.BORDER_SOFT);
                    }
                }
                y += ROW_H;
            }
        } finally {
            graphics.disableScissor();
        }
    }


    /**
     * Draws one row per bounty with its cancel button; the empty state stays in the status
     * line, so it is not repeated here. Only mine rows carry a cancel button showing
     * {@code X}, or {@code Sure?} while armed; the server enforces the owner-only rule.
     */
    static void drawBountyRows(final GovTreasuryPanel p, final GuiGraphics graphics,
            final int mouseX, final int mouseY) {
        final int top = p.bountyListRect.y() + 1;
        final int bottom = p.bountyListRect.bottom() - 1;
        graphics.enableScissor(p.bountyListRect.x() + 1, top,
                p.bountyListRect.right() - 1, bottom);
        try {
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
                final boolean selected = view.id().equals(p.bountySelectedId);
                final boolean armed = p.bountyCancelArmed
                        && view.id().equals(p.bountyCancelArmedId);
                boolean hovered = false;
                for (final ContractHit hit : p.bountyHits) {
                    if (hit.id().equals(view.id()) && hit.rect().contains(mouseX, mouseY)) {
                        hovered = true;
                        break;
                    }
                }
                if (selected || armed) {
                    graphics.fill(p.bountyListRect.x() + 1, y,
                            p.bountyListRect.right() - 1, y + ROW_H,
                            GovTheme.SELECTED);
                    graphics.fill(p.bountyListRect.x() + 1, y,
                            p.bountyListRect.x() + 3, y + ROW_H,
                            GovTheme.ACCENT);
                } else if (hovered) {
                    graphics.fill(p.bountyListRect.x() + 1, y,
                            p.bountyListRect.right() - 1, y + ROW_H,
                            GovTheme.HOVER);
                }
                final int textY = y + Math.max(0, (ROW_H - LINE) / 2);
                final int color = selected ? GovTheme.TEXT : GovTheme.BODY;
                graphics.drawString(p.font, view.target(), p.bountyColumnX[0],
                        textY, color, false);
                graphics.drawString(p.font, view.poster(), p.bountyColumnX[1],
                        textY, color, false);
                graphics.drawString(p.font, view.amount(), p.bountyColumnX[2],
                        textY, color, false);
                if (index + 1 < p.bountyViews.size()) {
                    graphics.fill(p.bountyListRect.x() + 4,
                            y + ROW_H - 1, p.bountyListRect.right() - 4,
                            y + ROW_H, GovTheme.BORDER_SOFT);
                }
                if (row.mine()) {
                    drawBountyCancel(p, graphics, mouseX, mouseY, view.id(), y, armed);
                }
            }
        } finally {
            graphics.disableScissor();
        }
    }


    static void drawBountyCancel(final GovTreasuryPanel p, final GuiGraphics graphics,
            final int mouseX, final int mouseY, final String id, final int y,
            final boolean armed) {
        GovRect cancel = null;
        for (final ContractHit hit : p.bountyCancelHits) {
            if (hit.id().equals(id)) {
                cancel = hit.rect();
                break;
            }
        }
        if (cancel == null || cancel.w() <= 0 || cancel.h() <= 0) {
            return;
        }
        final String label = armed ? "Sure?" : "X";
        final boolean hovered = cancel.contains(mouseX, mouseY);
        graphics.fill(cancel.x(), cancel.y(), cancel.right(), cancel.bottom(),
                hovered ? GovTheme.SELECTED : GovTheme.PANEL);
        GovTheme.border(graphics, cancel,
                armed ? GovTheme.ACCENT : GovTheme.BORDER_SOFT);
        final int textX = cancel.x()
                + Math.max(0, (cancel.w() - p.font.width(label)) / 2);
        final int textY = cancel.y()
                + Math.max(0, (cancel.h() - LINE) / 2);
        graphics.drawString(p.font, label, textX, textY, GovTheme.TEXT, false);
    }


    static void drawFooterRule(final GovTreasuryPanel p, final GuiGraphics graphics, final GovRect footer) {        if (footer.w() <= 0) {
            return;
        }
        graphics.fill(footer.x(), footer.y(), footer.right(), footer.y() + 1,
                GovTheme.BORDER_SOFT);
    }


    static void drawButtons(final GovTreasuryPanel p, final GuiGraphics graphics, final int mouseX, final int mouseY) {
        for (int index = 0; index < p.buttons.size(); index++) {
            final Button button = p.buttons.get(index);
            final boolean hovered = button.enabled()
                    && button.rect().contains(mouseX, mouseY);
            final int fill;
            final int textColor;
            if (!button.enabled()) {
                fill = GovTheme.DISABLED;
                textColor = GovTheme.FAINT;
            } else if (hovered) {
                fill = GovTheme.SELECTED;
                textColor = GovTheme.TEXT;
            } else if (button.primary()) {
                fill = GovTheme.BORDER;
                textColor = GovTheme.HEADER_TEXT;
            } else {
                fill = GovTheme.PANEL;
                textColor = GovTheme.TEXT;
            }
            graphics.fill(button.rect().x(), button.rect().y(), button.rect().right(),
                    button.rect().bottom(), fill);
            GovTheme.border(graphics, button.rect(),
                    button.enabled() ? GovTheme.BORDER : GovTheme.BORDER_SOFT);
            final int textX = button.rect().x()
                    + Math.max(0, (button.rect().w()
                            - p.font.width(button.label())) / 2);
            final int textY = button.rect().y()
                    + Math.max(0, (button.rect().h() - LINE) / 2);
            graphics.drawString(p.font, button.label(), textX, textY, textColor, false);
        }
    }

    // -------------------------------------------------------------------- input



}
