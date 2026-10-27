package com.horizonssmp.gov.client;

import com.horizonssmp.gov.client.GovTreasuryTypes.*;
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

/**
 * The Treasury section: bank balances, the government store and building contracts.
 *
 * <p>This panel owns no tabs of its own. The shell supplies the sub-category, and every read and
 * mutation is server-authoritative: the panel shows only the last answer it received and never
 * invents a balance, a catalogue row or a contract state.
 *
 * <p>All display strings and row views are built during layout or when a response changes data.
 * {@code render} only draws those cached strings and rectangles; it never translates, formats,
 * clips or measures text.
 */
final class GovTreasuryPanel {
    final GovDashboardScreen host;

    Font font;
    boolean active;
    int subtab;

    final Map<String, RequestKind> pending = new LinkedHashMap<>();
    final Map<String, String> labels = new LinkedHashMap<>();
    final List<StoreRow> storeRows = new ArrayList<>();
    final List<ContractRow> contractRows = new ArrayList<>();
    final List<ContractView> contractViews = new ArrayList<>();
    final List<ContractHit> contractHits = new ArrayList<>();
    final List<Button> buttons = new ArrayList<>();
    final List<FormattedCharSequence> depositNoteLines = new ArrayList<>();
    final List<FormattedCharSequence> withdrawNoteLines = new ArrayList<>();

    LoadState balanceState = LoadState.NONE;
    LoadState storeState = LoadState.NONE;
    LoadState contractState = LoadState.NONE;
    String balanceError = "";
    String storeError = "";
    String contractError = "";
    String latestBalanceReqId = "";
    String latestStoreReqId = "";
    String latestContractReqId = "";
    boolean balanceRequested;

    long balance;
    boolean canCredit;
    long storeBalance;
    int storePage;
    int storePages;
    int storeTotal;
    int stacks = STORE_MIN_STACKS;
    String storeSelectedId = "";

    int contractPage;
    int contractPages;
    int contractTotal;
    String contractSelectedId = "";
    int contractScroll;
    int contractScrollMax;

    String status = "";
    boolean statusIsError;

    GovRect area = NONE;
    boolean showTitle;
    GovRect titleRect = NONE;
    Component titleBold;
    String titleView = "";

    GovRect balanceRect = NONE;
    GovRect balanceRuleRect = NONE;
    GovRect balanceNotesRect = NONE;
    GovRect balanceStatusRect = NONE;
    GovRect balanceFooterRect = NONE;
    GovRect depositRect = NONE;
    GovRect balanceRefreshRect = NONE;
    GovRect balanceCloseRect = NONE;
    String balanceView = "";
    String accountOwnView = "";
    int accountOwnX;

    GovRect storeHeaderRect = NONE;
    GovRect storeStatusRect = NONE;
    GovRect storeGridRect = NONE;
    GovRect storeFooterRect = NONE;
    GovRect storeBalanceRect = NONE;
    GovRect storePageRect = NONE;
    GovRect storePrevRect = NONE;
    GovRect storeNextRect = NONE;
    GovRect storeMinusRect = NONE;
    GovRect storeStacksRect = NONE;
    GovRect storePlusRect = NONE;
    GovRect storeTotalRect = NONE;
    GovRect storeBuyRect = NONE;
    GovRect storeRefreshRect = NONE;
    GovRect storeCloseRect = NONE;
    final GovRect[] storeCellRects = new GovRect[STORE_CELLS];
    final StoreCellView[] storeCellViews = new StoreCellView[STORE_CELLS];
    String storeBalanceView = "";
    String storePageView = "";
    String stacksView = "";
    String totalView = "";
    long storeSelectedPrice;

    GovRect contractHeaderRect = NONE;
    GovRect contractListRect = NONE;
    GovRect contractStatusRect = NONE;
    GovRect contractFooterRect = NONE;
    GovRect contractPrevRect = NONE;
    GovRect contractNextRect = NONE;
    final GovRect[] contractButtonRects = new GovRect[6];
    final int[] contractColumnX = new int[5];
    final int[] contractColumnW = new int[5];
    final Component[] contractHeaders = new Component[5];
    String messageView = "";
    int messageColor = GovTheme.MUTED;
    String contractPageView = "";

    LoadState bountyState = LoadState.NONE;
    String bountyError = "";
    String latestBountyReqId = "";
    final List<GovBountyList.BountyRow> bountyRows = new ArrayList<>();
    final List<GovBountyList.BountyView> bountyViews = new ArrayList<>();
    final List<ContractHit> bountyHits = new ArrayList<>();
    final List<ContractHit> bountyCancelHits = new ArrayList<>();
    final List<String> bountyOnline = new ArrayList<>();
    long bountyMin;
    long bountyMax;
    int bountyTotal;
    String bountySelectedId = "";
    int bountyScroll;
    int bountyScrollMax;
    int bountyVisibleRows;
    boolean bountyCancelArmed;
    String bountyCancelArmedId = "";
    long bountyCancelArmedAt;
    String bountyCountView = "";

    GovRect bountyFormRect = NONE;
    GovRect bountyTargetRect = NONE;
    GovRect bountyAmountMinusRect = NONE;
    GovRect bountyAmountRect = NONE;
    GovRect bountyAmountPlusRect = NONE;
    GovRect bountyPlaceRect = NONE;
    GovRect bountyCountRect = NONE;
    GovRect bountyListRect = NONE;
    GovRect bountyStatusRect = NONE;
    GovRect bountyFooterRect = NONE;
    GovRect bountyRefreshRect = NONE;
    GovRect bountyCloseRect = NONE;
    final int[] bountyColumnX = new int[3];
    final int[] bountyColumnW = new int[3];

    final GovBankCreditForm credit = new GovBankCreditForm();

    final GovBountyForm bounty = new GovBountyForm();

    final GovTreasuryActions actions = new GovTreasuryActions();
    /** Debounces Enter so a held key cannot submit a form twice within 250 ms. */
    final GovKeyRepeat enterRepeat = new GovKeyRepeat();

    GovTreasuryPanel(final GovDashboardScreen host) {
        this.host = host;
        Arrays.fill(this.storeCellRects, NONE);
        Arrays.fill(this.storeCellViews, StoreCellView.EMPTY);
        Arrays.fill(this.contractButtonRects, NONE);
        for (int index = 0; index < this.contractHeaders.length; index++) {
            this.contractHeaders[index] = Component.empty();
        }
    }

    /** The bank-credit, pay and bounty text fields; every other subtab is button-only. */
    List<EditBox> createWidgets(final Font font) {
        this.font = font;
        return GovTreasuryActions.createWidgets(this, font);
    }

    void setActive(final boolean active) {
        this.active = active;
        if (active && !this.balanceRequested) {
            this.balanceRequested = true;
            GovTreasuryRequests.requestBalance(this);
        }
        GovTreasuryViews.rebuildViews(this);
        GovBankCreditForm.sync(this);
        GovTreasuryActions.sync(this);
    }

    /** Forgets every request in flight and every value received. */
    void reset() {
        this.pending.clear();
        this.latestBalanceReqId = "";
        this.latestStoreReqId = "";
        this.latestContractReqId = "";
        this.balanceRequested = false;
        this.balanceState = LoadState.NONE;
        this.storeState = LoadState.NONE;
        this.contractState = LoadState.NONE;
        this.balanceError = "";
        this.storeError = "";
        this.contractError = "";
        this.balance = 0L;
        this.canCredit = false;
        this.storeBalance = 0L;
        this.storePage = 0;
        this.storePages = 0;
        this.storeTotal = 0;
        this.stacks = STORE_MIN_STACKS;
        this.storeSelectedId = "";
        this.storeSelectedPrice = 0L;
        this.storeRows.clear();
        this.contractRows.clear();
        this.contractViews.clear();
        this.contractHits.clear();
        this.contractPage = 0;
        this.contractPages = 0;
        this.contractTotal = 0;
        this.contractSelectedId = "";
        this.contractScroll = 0;
        this.contractScrollMax = 0;
        GovBountyList.resetBounty(this);
        GovBankCreditForm.reset(this);
        GovTreasuryActions.reset(this);
        this.status = "";
        this.statusIsError = false;
        GovTreasuryViews.rebuildViews(this);
    }

    /**
     * Selects the shell sub-category: 0 Balances, 1 Store, 2 Contracts, 3 Bounty. When the
     * section is visible, the selected sub-category immediately asks for its own page.
     */
    void setSubtab(final int index) {
        final int next = GovMetrics.clamp(index, TAB_BALANCES, TAB_BOUNTY);
        if (next == this.subtab) {
            return;
        }
        this.subtab = next;
        this.status = "";
        this.statusIsError = false;
        if (this.active) {
            GovTreasuryRequests.requestForSubtab(this);
        }
        GovTreasuryViews.rebuildViews(this);
        GovBankCreditForm.sync(this);
        GovTreasuryActions.sync(this);
    }

    void layout(final int left, final int top, final int right, final int bottom,
            final Font font) {
        GovTreasuryGeometry.layout(this, left, top, right, bottom, font);
    }

    void render(final GuiGraphics graphics, final int mouseX, final int mouseY) {
        GovTreasuryRender.render(this, graphics, mouseX, mouseY);
    }

    boolean mouseClicked(final double mouseX, final double mouseY, final int button) {
        return GovTreasuryInput.mouseClicked(this, mouseX, mouseY, button);
    }

    boolean mouseScrolled(final double mouseX, final double mouseY, final double scrollY) {
        return GovTreasuryInput.mouseScrolled(this, mouseX, mouseY, scrollY);
    }

    boolean keyPressed(final int keyCode, final int scanCode, final int modifiers) {
        return GovTreasuryInput.keyPressed(this, keyCode, scanCode, modifiers);
    }

    void onActionResult(final GovActionResultPayload payload) {
        GovTreasuryResponses.onActionResult(this, payload);
    }
}
