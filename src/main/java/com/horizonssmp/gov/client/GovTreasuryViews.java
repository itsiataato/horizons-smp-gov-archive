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

final class GovTreasuryViews {

    static String label(final GovTreasuryPanel p, final String key) {
        return p.labels.computeIfAbsent(key,
                value -> Component.translatable(value).getString());
    }


    static String trArgs(final String key, final Object... args) {
        return Component.translatable(key, args).getString();
    }


    static void rebuildViews(final GovTreasuryPanel p) {
        if (p.font == null || p.area.w() <= 0 || p.area.h() <= 0) {
            return;
        }
        p.titleView = switch (p.subtab) {
            case TAB_STORE -> label(p, "gui.gov.treasury.store_title");
            case TAB_CONTRACTS -> label(p, "gui.gov.treasury.contracts_title");
            case TAB_BOUNTY -> label(p, "gui.gov.treasury.bounty_title");
            default -> label(p, "gui.gov.treasury.balances_title");
        };
        p.titleBold = GovText.upperBoldCached(p.titleView);
        rebuildBalances(p);
        rebuildStore(p);
        rebuildContracts(p);
        rebuildBounty(p);
        rebuildMessage(p);
        GovTreasuryViewButtons.rebuildButtons(p);
        GovTreasuryViewButtons.rebuildContractHits(p);
        GovTreasuryViewButtons.rebuildBountyHits(p);
    }


    static void rebuildBalances(final GovTreasuryPanel p) {
        p.balanceView = GovText.clip(p.font,
                trArgs("gui.gov.treasury.balance", p.balance), p.balanceRect.w());
        p.accountOwnView = GovText.clip(p.font,
                label(p, "gui.gov.treasury.account_own"), p.balanceRect.w() / 2);
        p.accountOwnX = Math.max(p.balanceRect.x(),
                p.balanceRect.right() - p.font.width(p.accountOwnView));
        p.depositNoteLines.clear();
        p.depositNoteLines.addAll(GovText.wrapCached(p.font,
                label(p, "gui.gov.treasury.deposit_note"), p.balanceNotesRect.w()));
        p.withdrawNoteLines.clear();
        p.withdrawNoteLines.addAll(GovText.wrapCached(p.font,
                label(p, "gui.gov.treasury.withdraw_note"), p.balanceNotesRect.w()));
    }


    static void rebuildStore(final GovTreasuryPanel p) {
        p.storeBalanceView = GovText.clip(p.font,
                trArgs("gui.gov.treasury.balance", p.storeBalance),
                p.storeBalanceRect.w());
        p.storePageView = p.storePages <= 0 ? ""
                : GovText.clip(p.font,
                        trArgs("gui.gov.treasury.page", p.storePage + 1, p.storePages),
                        p.storePageRect.w());
        p.stacksView = GovText.clip(p.font,
                label(p, "gui.gov.treasury.stacks_short") + " "
                        + trArgs("gui.gov.treasury.stacks_value", p.stacks),
                p.storeStacksRect.w());
        p.storeSelectedPrice = 0L;
        for (final StoreRow row : p.storeRows) {
            if (row.id().equals(p.storeSelectedId)) {
                p.storeSelectedPrice = row.price();
                break;
            }
        }
        GovTreasurySupport.clampStacks(p);
        final long total = p.storeSelectedId.isEmpty() ? 0L
                : p.storeSelectedPrice * p.stacks;
        p.totalView = GovText.clip(p.font,
                trArgs("gui.gov.treasury.total", total), p.storeTotalRect.w());

        for (int index = 0; index < STORE_CELLS; index++) {
            final GovRect cell = p.storeCellRects[index];
            if (index >= p.storeRows.size() || cell.w() <= 0 || cell.h() <= 0) {
                p.storeCellViews[index] = StoreCellView.EMPTY;
                continue;
            }
            final StoreRow row = p.storeRows.get(index);
            final int textW = Math.max(0, cell.w() - 4);
            final String name = GovText.clip(p.font, row.name(), textW);
            final String tier = GovText.clip(p.font,
                    trArgs("gui.gov.treasury.tier", row.tier()), textW);
            final String price = GovText.clip(p.font,
                    trArgs("gui.gov.treasury.price", row.price()), textW);
            final String combined = GovText.clip(p.font, tier + " " + price, textW);
            p.storeCellViews[index] =
                    new StoreCellView(row.id(), name, tier, price, combined);
        }
    }


    static void rebuildContracts(final GovTreasuryPanel p) {
        p.contractViews.clear();
        p.contractPageView = p.contractPages <= 0 ? ""
                : GovText.clip(p.font,
                        trArgs("gui.gov.treasury.page", p.contractPage + 1,
                                p.contractPages),
                        contractPageZone(p));
        for (final ContractRow row : p.contractRows) {
            p.contractViews.add(new ContractView(
                    GovText.clip(p.font, row.id(), p.contractColumnW[0]),
                    GovText.clip(p.font, row.state(), p.contractColumnW[1]),
                    GovText.clip(p.font, GovTreasurySupport.shortId(row.builder()), p.contractColumnW[2]),
                    GovText.clip(p.font,
                            trArgs("gui.gov.treasury.price", row.total()),
                            p.contractColumnW[3]),
                    GovText.clip(p.font, row.description(), p.contractColumnW[4])));
        }
        p.contractHeaders[0] = GovText.boldCached(label(p, "gui.gov.treasury.item"));
        p.contractHeaders[1] = GovText.boldCached(label(p, "gui.gov.treasury.state"));
        p.contractHeaders[2] = GovText.boldCached(label(p, "gui.gov.treasury.builder"));
        p.contractHeaders[3] = Component.empty();
        p.contractHeaders[4] = GovText.boldCached(label(p, "gui.gov.treasury.description"));

        p.contractScrollMax = Math.max(0,
                p.contractViews.size() * ROW_H
                        - Math.max(0, p.contractListRect.h() - 2));
        p.contractScroll = GovMetrics.clamp(p.contractScroll, 0,
                p.contractScrollMax);
    }


    static void rebuildBounty(final GovTreasuryPanel p) {
        p.bountyViews.clear();
        final String mineSuffix = label(p, "gui.gov.treasury.bounty_mine");
        for (final GovBountyList.BountyRow row : p.bountyRows) {
            final String poster = row.mine() && !mineSuffix.isEmpty()
                    ? row.poster() + " " + mineSuffix : row.poster();
            p.bountyViews.add(new GovBountyList.BountyView(
                    row.id(),
                    GovText.clip(p.font, row.target(), p.bountyColumnW[0]),
                    GovText.clip(p.font, poster, p.bountyColumnW[1]),
                    GovText.clip(p.font,
                            trArgs("gui.gov.treasury.price", row.amount()),
                            p.bountyColumnW[2])));
        }
        p.bountyCountView = GovText.clip(p.font, GovBountyList.countLine(p),
                p.bountyCountRect.w());
    }


    static void rebuildMessage(final GovTreasuryPanel p) {
        final LoadState state = currentState(p);
        final String error = currentError(p);
        final String text;
        final int color;
        if (!p.status.isEmpty()) {
            text = p.status;
            color = p.statusIsError ? GovTheme.DANGER : GovTheme.POSITIVE;
        } else if (state == LoadState.LOADING) {
            text = label(p, "gui.gov.treasury.loading");
            color = GovTheme.MUTED;
        } else if (state == LoadState.ERROR) {
            text = error.isBlank() ? label(p, "gui.gov.treasury.error") : error;
            color = GovTheme.DANGER;
        } else if (p.subtab == TAB_STORE && p.storeRows.isEmpty()
                && state == LoadState.READY) {
            text = label(p, "gui.gov.treasury.store_empty");
            color = GovTheme.MUTED;
        } else if (p.subtab == TAB_CONTRACTS && p.contractRows.isEmpty()
                && state == LoadState.READY) {
            text = label(p, "gui.gov.treasury.contracts_empty");
            color = GovTheme.MUTED;
        } else if (p.subtab == TAB_BOUNTY && p.bountyRows.isEmpty()
                && state == LoadState.READY) {
            text = label(p, "gui.gov.treasury.bounty_empty");
            color = GovTheme.MUTED;
        } else if (p.subtab == TAB_CONTRACTS) {
            text = label(p, "gui.gov.treasury.select");
            color = GovTheme.FAINT;
        } else {
            text = "";
            color = GovTheme.MUTED;
        }
        p.messageColor = color;
        p.messageView = GovText.clip(p.font, text, messageWidth(p));
    }


    static LoadState currentState(final GovTreasuryPanel p) {
        return switch (p.subtab) {
            case TAB_STORE -> p.storeState;
            case TAB_CONTRACTS -> p.contractState;
            case TAB_BOUNTY -> p.bountyState;
            default -> p.balanceState;
        };
    }


    static String currentError(final GovTreasuryPanel p) {
        return switch (p.subtab) {
            case TAB_STORE -> p.storeError;
            case TAB_CONTRACTS -> p.contractError;
            case TAB_BOUNTY -> p.bountyError;
            default -> p.balanceError;
        };
    }


    static int messageWidth(final GovTreasuryPanel p) {
        return switch (p.subtab) {
            case TAB_STORE -> p.storeStatusRect.w();
            case TAB_BOUNTY -> p.bountyStatusRect.w();
            case TAB_CONTRACTS -> Math.max(0, p.contractStatusRect.w()
                    - (p.contractPageView.isEmpty()
                            ? 0 : contractPageZone(p) + 6));
            default -> p.balanceStatusRect.w();
        };
    }


    static int contractPageZone(final GovTreasuryPanel p) {
        return Math.min(CONTRACT_PAGE_W, p.contractStatusRect.w() / 3);
    }

}
