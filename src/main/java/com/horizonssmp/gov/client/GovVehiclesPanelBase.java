package com.horizonssmp.gov.client;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.horizonssmp.gov.common.GovFields;
import com.horizonssmp.gov.common.LicenseKeys;
import com.horizonssmp.gov.common.OpCode;
import com.horizonssmp.gov.common.VehicleKeys;
import com.horizonssmp.gov.network.GovActionResultPayload;
import com.horizonssmp.gov.network.GovNetwork;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import com.horizonssmp.gov.client.GovRect;
import com.horizonssmp.gov.client.GovKeyRepeat;
import com.horizonssmp.gov.client.GovVehiclesTypes.*;
import static com.horizonssmp.gov.client.GovVehiclesConstants.*;

abstract class GovVehiclesPanelBase {


    final Screen host;

    Font font;
    EditBox scanBox;
    EditBox plateBox;
    EditBox impoundPlateBox;
    EditBox impoundReasonBox;
    EditBox licenseReasonBox;
    EditBox licenseTargetBox;
    EditBox licenseClassBox;
    List<EditBox> widgets = List.of();
    boolean active;
    int subtab;

    final Map<String, RequestKind> pending = new LinkedHashMap<>();
    final List<VehicleRow> rows = new ArrayList<>();
    final List<VehicleRowView> rowViews = new ArrayList<>();
    final List<String> online = new ArrayList<>();
    final List<Hit> rowHits = new ArrayList<>();
    final List<Button> buttons = new ArrayList<>();
    final List<FieldRow> fieldRows = new ArrayList<>();
    final List<DetailLine> detailLines = new ArrayList<>();
    final List<String> scanLines = new ArrayList<>();
    final Map<String, String> detail = new LinkedHashMap<>();
    final Map<String, String> scanFields = new LinkedHashMap<>();
    final Map<String, String> labels = new HashMap<>();
    final Map<String, String> classNames = new HashMap<>();
    final Map<String, String> classShortNames = new HashMap<>();
    final Map<String, String> statusNames = new HashMap<>();

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
    String listFilter = VehicleKeys.FILTER_ALL;
    String requestedFilter = VehicleKeys.FILTER_ALL;
    String viewerUuid = "";
    String viewerName = "";
    boolean canRegister;
    boolean canImpound;
    boolean canRelease;
    boolean canScan;
    boolean canManageLicenses;
    boolean receptionConfigured;
    String paymentPoint = "";
    long releaseFee;
    int onlineTotal;

    String selectedPlate = "";
    String registerClass = "car";
    String registerOwner = "";
    String licensePoints = "2";
    Click armedClick;
    long armedAt;
    final GovKeyRepeat enterRepeat = new GovKeyRepeat();

    int listScroll;
    int listScrollMax;
    int detailScroll;
    int detailScrollMax;
    final int[] scrollByTab = new int[6];
    final int[] pageByTab = new int[6];

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
    GovRect detailBodyRect = NONE;
    GovRect detailActionRect = NONE;
    GovRect listRetryRect = NONE;
    GovRect detailRetryRect = NONE;
    GovRect summaryRect = NONE;
    GovRect bottomBar = NONE;
    GovRect pageTextRect = NONE;
    GovRect prevRect = NONE;
    GovRect nextRect = NONE;
    GovRect refreshRect = NONE;
    final GovRect[] filterRects = {NONE, NONE, NONE, NONE};
    final int[] columnX = new int[5];
    final int[] columnW = new int[5];
    final Component[] columnHeaders = new Component[5];

    GovRect scanFormRect = NONE;
    GovRect scanLabelRect = NONE;
    GovRect scanFieldRect = NONE;
    GovRect scanButtonRect = NONE;
    GovRect scanCardRect = NONE;
    GovRect scanStatusRect = NONE;

    GovRect registerFormRect = NONE;
    GovRect ownerLabelRect = NONE;
    GovRect ownerPrevRect = NONE;
    GovRect ownerNextRect = NONE;
    GovRect ownerValueRect = NONE;
    GovRect plateLabelRect = NONE;
    GovRect plateFieldRect = NONE;
    GovRect classLabelRect = NONE;
    final GovRect[] classRects = {NONE, NONE, NONE, NONE, NONE};
    GovRect registerButtonRect = NONE;
    GovRect registerStatusRect = NONE;
    GovRect registerRuleRect = NONE;

    GovRect impoundFormRect = NONE;
    GovRect impoundSummaryRect = NONE;
    GovRect impoundPlateLabelRect = NONE;
    GovRect impoundPlateFieldRect = NONE;
    GovRect impoundReasonLabelRect = NONE;
    GovRect impoundReasonFieldRect = NONE;
    GovRect impoundRuleRect = NONE;
    GovRect impoundButtonRect = NONE;
    GovRect releaseButtonRect = NONE;
    GovRect impoundStatusRect = NONE;

    GovRect licenseFormRect = NONE;
    GovRect licenseSummaryRect = NONE;
    GovRect licenseInfoRect = NONE;
    GovRect licensePointsLabelRect = NONE;
    final GovRect[] pointRects = {NONE, NONE, NONE};
    GovRect licensePointsHintRect = NONE;
    GovRect licenseReasonLabelRect = NONE;
    GovRect licenseReasonFieldRect = NONE;
    GovRect licenseTargetLabelRect = NONE;
    GovRect licenseTargetFieldRect = NONE;
    GovRect licenseClassLabelRect = NONE;
    GovRect licenseClassFieldRect = NONE;
    GovRect licenseIssueRect = NONE;
    GovRect licenseAssessRect = NONE;
    GovRect licenseRevokeRect = NONE;
    GovRect licenseStatusRect = NONE;
    GovRect licenseRuleRect = NONE;
    GovRect licensePrevRect = NONE;
    GovRect licenseNextRect = NONE;

    String titleView = "";
    String pageView = "";
    String summaryView = "";
    String detailTitle = "";
    Component detailTitleBold;
    String detailStateView = "";
    String scanStatusView = "";
    String registerOwnerView = "";
    String registerStatusView = "";
    String impoundSummaryView = "";
    String impoundStatusView = "";
    String licenseSummaryView = "";
    String licenseInfoView = "";
    String licenseStatusFullView = "";
    String registerRuleView = "";
    String impoundWarningView = "";
    String releaseRuleView = "";
    String licenseHintView = "";
    String licenseRuleView = "";
    String scanPromptView = "";
    String scanForbiddenView = "";
    GovVehiclesPanelBase(final Screen host) {
        this.host = host;
    }
}
