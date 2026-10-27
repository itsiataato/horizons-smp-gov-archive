package com.horizonssmp.gov.client;

import com.horizonssmp.gov.client.GovFinesTypes.*;
import static com.horizonssmp.gov.client.GovFinesTypes.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.horizonssmp.gov.common.FineKeys;
import com.horizonssmp.gov.common.GovFields;
import com.horizonssmp.gov.common.OpCode;
import com.horizonssmp.gov.network.GovActionResultPayload;
import com.horizonssmp.gov.network.GovNetwork;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

final class GovFinesPanel {
    final Screen host;

    Font font;
    EditBox articleBox;
    EditBox restitutionBox;
    EditBox victimBox;
    EditBox exileBox;
    EditBox issueReasonBox;
    EditBox waiveReasonBox;
    List<EditBox> widgets = List.of();
    boolean active;
    int subtab;

    final Map<String, RequestKind> pending = new LinkedHashMap<>();
    final List<FineRow> rows = new ArrayList<>();
    final List<FineRowView> rowViews = new ArrayList<>();
    final List<String> online = new ArrayList<>();
    final List<Hit> rowHits = new ArrayList<>();
    final List<Button> buttons = new ArrayList<>();
    final List<FieldRow> fieldRows = new ArrayList<>();
    final List<DetailLine> detailLines = new ArrayList<>();
    final Map<String, String> detail = new LinkedHashMap<>();
    final Map<String, String> labels = new HashMap<>();
    final Map<String, String> stateNames = new HashMap<>();
    final Map<String, String> bandNames = new HashMap<>();
    final Map<String, String> bandShortNames = new HashMap<>();

    LoadState listState = LoadState.NONE;
    LoadState detailState = LoadState.NONE;
    String listError = "";
    String detailError = "";
    String listErrorView = "";
    String detailErrorView = "";
    String status = "";
    String statusView = "";
    boolean statusIsError;
    String latestListReqId = "";
    String latestDetailReqId = "";

    int total;
    int page;
    int pages = 1;
    String registerFilter = FineKeys.FILTER_ALL;
    String listFilter = FineKeys.FILTER_ALL;
    String requestedFilter = FineKeys.FILTER_ALL;
    String viewerUuid = "";
    boolean canIssue;
    boolean canViewAll;
    boolean canWaive;
    boolean canCollect;
    boolean receptionConfigured;
    String paymentPoint = "";
    int onlineTotal;

    String selectedId = "";
    String issueBand = "PETTY";
    String issueTarget = "";
    int issueTargetIndex;
    Click armedClick;
    long armedAt;
    final GovKeyRepeat enterRepeat = new GovKeyRepeat();

    int listScroll;
    int listScrollMax;
    int detailScroll;
    int detailScrollMax;
    final int[] scrollByTab = new int[4];
    final int[] pageByTab = new int[4];

    GovRect area = NONE;
    boolean compact;
    int pad;
    int rowH;
    boolean showTitle;

    GovRect titleRect = NONE;
    GovRect topBar = NONE;
    GovRect tableHeader = NONE;
    GovRect listRect = NONE;
    GovRect detailRect = NONE;
    GovRect summaryRect = NONE;
    GovRect bottomBar = NONE;
    GovRect pageTextRect = NONE;
    GovRect prevRect = NONE;
    GovRect nextRect = NONE;
    GovRect refreshRect = NONE;
    GovRect listRetryRect = NONE;
    GovRect detailRetryRect = NONE;
    final GovRect[] filterRects = {NONE, NONE, NONE, NONE};
    final int[] columnX = new int[5];
    final int[] columnW = new int[5];
    final Component[] columnHeaders = new Component[5];

    GovRect formRect = NONE;
    GovRect hintRect = NONE;
    GovRect citizenLabelRect = NONE;
    GovRect citizenValueRect = NONE;
    GovRect onlinePrevRect = NONE;
    GovRect onlineNextRect = NONE;
    GovRect bandLabelRect = NONE;
    final GovRect[] bandRects = {NONE, NONE, NONE, NONE};
    GovRect articleLabelRect = NONE;
    GovRect articleFieldRect = NONE;
    GovRect restitutionLabelRect = NONE;
    GovRect restitutionFieldRect = NONE;
    GovRect victimLabelRect = NONE;
    GovRect victimFieldRect = NONE;
    GovRect exileLabelRect = NONE;
    GovRect exileFieldRect = NONE;
    GovRect reasonLabelRect = NONE;
    GovRect reasonFieldRect = NONE;
    GovRect issueActionRect = NONE;
    GovRect issueAmountRect = NONE;
    GovRect issueButtonRect = NONE;
    GovRect issueHintRect = NONE;

    GovRect collectFormRect = NONE;
    GovRect collectSummaryRect = NONE;
    GovRect collectTargetRect = NONE;
    GovRect collectAmountRect = NONE;
    GovRect collectPrevRect = NONE;
    GovRect collectNextRect = NONE;
    GovRect collectButtonRect = NONE;
    GovRect collectPayRect = NONE;
    GovRect collectStatusRect = NONE;

    GovRect waiveFormRect = NONE;
    GovRect waiveSummaryRect = NONE;
    GovRect waiveReasonLabelRect = NONE;
    GovRect waiveReasonFieldRect = NONE;
    GovRect waiveRuleRect = NONE;
    GovRect waivePrevRect = NONE;
    GovRect waiveNextRect = NONE;
    GovRect waiveButtonRect = NONE;
    GovRect waiveStatusRect = NONE;

    String titleView = "";
    String pageView = "";
    String summaryView = "";
    String detailTitle = "";
    Component detailTitleBold;
    String detailStateView = "";
    int detailStateX;
    int detailTitleW;
    String issueAmountView = "";
    String issueCitizenView = "";
    String issueHintView = "";
    String collectTargetView = "";
    String collectAmountView = "";
    String collectRuleView = "";
    String waiveTargetView = "";
    String waiveRuleView = "";

    GovFinesPanel(final Screen host) {
        this.host = host;
    }

    List<EditBox> createWidgets(final Font font) {
        return GovFinesWidgets.createWidgets(this, font);
    }

    void setActive(final boolean active) {
        GovFinesSupport.setActive(this, active);
    }

    void reset() {
        GovFinesSupport.reset(this);
    }

    void setSubtab(final int index) {
        GovFinesSupport.setSubtab(this, index);
    }

    void layout(final int left, final int top, final int right, final int bottom,
            final Font font) {
        GovFinesGeometry.layout(this, left, top, right, bottom, font);
    }

    void onActionResult(final GovActionResultPayload payload) {
        GovFinesResponses.onActionResult(this, payload);
    }

    void render(final GuiGraphics graphics, final int mouseX, final int mouseY) {
        GovFinesRender.render(this, graphics, mouseX, mouseY);
    }

    boolean mouseClicked(final double mouseX, final double mouseY, final int button) {
        return GovFinesInput.mouseClicked(this, mouseX, mouseY, button);
    }

    boolean mouseScrolled(final double mouseX, final double mouseY, final double scrollY) {
        return GovFinesInput.mouseScrolled(this, mouseX, mouseY, scrollY);
    }

    boolean keyPressed(final int keyCode, final int scanCode, final int modifiers) {
        return GovFinesInput.keyPressed(this, keyCode, scanCode, modifiers);
    }

    boolean charTyped(final char codePoint, final int modifiers) {
        return false;
    }
}