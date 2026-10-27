package com.horizonssmp.gov.client;

import com.horizonssmp.gov.client.GovDashboardScreen;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.horizonssmp.gov.common.GovFields;
import com.horizonssmp.gov.common.JailKeys;
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

import static com.horizonssmp.gov.client.GovJailTypes.*;
import static com.horizonssmp.gov.client.GovJailConstants.*;

class GovJailPanelBase {

    final Screen host;
    final GovKeyRepeat enterRepeat = new GovKeyRepeat();

    Font font;
    boolean active;
    int subtab;

    EditBox detainTargetBox;
    EditBox detainReasonBox;
    EditBox warrantBox;
    EditBox detainMinutesBox;
    EditBox releaseReasonBox;
    EditBox extendMinutesBox;
    EditBox extendReasonBox;
    List<EditBox> widgets = List.of();

    final Map<String, RequestKind> pending = new LinkedHashMap<>();
    final List<JailRow> rows = new ArrayList<>();
    final List<JailRow> activeRows = new ArrayList<>();
    final Map<String, String> detail = new LinkedHashMap<>();

    LoadState listState = LoadState.NONE;
    LoadState detailState = LoadState.NONE;
    String listError = "";
    String detailError = "";
    String status = "";
    boolean statusIsError;
    String latestListReqId = "";
    String latestDetailReqId = "";

    int total;
    int activeTotal;
    int page;
    int pages = 1;
    String filter = JailKeys.FILTER_ACTIVE;
    String viewerUuid = "";
    String viewerName = "";
    boolean canDetain;
    boolean canRelease;
    boolean canViewAll;
    boolean nativeCuffs = true;
    int maxNoWarrantMinutes = JailKeys.HARD_MAX_NO_WARRANT_MINUTES;
    int maxWarrantMinutes = JailKeys.DEFAULT_MAX_WARRANT_MINUTES;
    boolean cellConfigured;
    String adapter = "none";
    String cell = "";

    String selectedId = "";
    String detainBasis = "consent";
    boolean detainConsent;
    boolean detainArmed;
    boolean releaseArmed;
    boolean extendArmed;
    int cellNumber = 1;
    boolean cellPromptOpen;
    GovRect cellPromptRect = EMPTY;
    GovRect cellPromptField = EMPTY;
    EditBox cellPromptBox;
    String cellPromptHint = "";
    int cellPromptMax = 20;
    long armedAt;

    final int[] listScrollRows = new int[SUBTABS];
    final int[] detailScrollRows = new int[SUBTABS];
    int visibleRows;
    int visibleDetailRows;
    int listScrollMaxRows;
    int detailScrollMaxRows;
    int rowPitch = ROW_PITCH;
    int detailPitch = DETAIL_PITCH;

    GovRect bounds = EMPTY;
    final GovRect[] topBars = new GovRect[SUBTABS];
    final GovRect[] bottoms = new GovRect[SUBTABS];
    final GovRect[] statuses = new GovRect[SUBTABS];
    final GovRect[] bodies = new GovRect[SUBTABS];
    final GovRect[] lists = new GovRect[SUBTABS];
    final GovRect[] listBodies = new GovRect[SUBTABS];
    final GovRect[] details = new GovRect[SUBTABS];
    final GovRect[] summaries = new GovRect[SUBTABS];

    GovRect targetFrame = EMPTY;
    GovRect cellRect = EMPTY;
    GovRect basisRect = EMPTY;
    GovRect minutesFrame = EMPTY;
    GovRect warrantFrame = EMPTY;
    GovRect reasonFrame = EMPTY;
    GovRect consentRect = EMPTY;
    GovRect detainButtonRect = EMPTY;
    GovRect releaseReasonFrame = EMPTY;
    GovRect releaseSummaryRect = EMPTY;
    GovRect releaseButtonRect = EMPTY;
    GovRect extendMinutesFrame = EMPTY;
    GovRect extendReasonFrame = EMPTY;
    GovRect extendSummaryRect = EMPTY;
    GovRect extendButtonRect = EMPTY;
    GovRect listScrollbarRect = EMPTY;
    GovRect detailScrollbarRect = EMPTY;

    final List<Button> buttons = new ArrayList<>();
    final List<Hit> rowHits = new ArrayList<>();
    final List<DetailLine> detailLines = new ArrayList<>();
    int detailLinesWidth = -1;
    String detailLinesKey = "";
    int detailRevision;

    String listHeader = "";
    String pageLabel = "";
    String filterLabel = "";
    String detailTitle = "";
    String summaryText = "";
    String detainPolicy = "";
    String releasePolicy = "";
    String extendPolicy = "";
    String extendCeiling = "";
    String cellText = "";

    final String labelCitizen;
    final String labelCell;
    final String labelBasis;
    final String labelMinutes;
    final String labelWarrant;
    final String labelReason;
    final String labelOfficer;
    final String labelStarted;
    final String labelTerm;
    final String labelRemaining;
    final String labelStatus;
    final String labelId;
    final String labelCuffs;
    final String labelRelease;
    final String labelConfinement;
    final String labelExtensions;

    final String buttonFilter;
    final String buttonPrev;
    final String buttonNext;
    final String buttonRefresh;
    final String buttonRetry;
    final String buttonDetain;
    final String buttonRelease;
    final String buttonExtend;
    final String buttonCuff;
    final String buttonUncuff;
    final String buttonPutCell;

    final String textLoading;
    final String textError;
    final String textEmptyActive;
    final String textEmptyFilter;
    final String textNoSelection;
    final String textReasonRequired;
    final String textTargetRequired;
    final String textConsentRequired;
    final String textWarrantRequired;
    final String textMinutesRequired;
    final String textMinutesInvalid;
    final String textNoWarrant;
    final String textNotReleased;
    final String textNoCell;
    final String textSurrender;
    final String textCuffsDisabled;
    final String textReleasePolicy;
    final String textExtendPolicy;
    final String textExtendNeedsWarrant;
    final String textConfirmPrefix;
    final String textNoWarrantRequired;
    final String consentYes;
    final String consentNo;

    final String[] filterIds = {
        JailKeys.FILTER_ACTIVE, JailKeys.FILTER_RELEASED,
        JailKeys.FILTER_EXPIRED, JailKeys.FILTER_ALL
    };
    final String[] filterLabels;

    GovJailPanelBase(final Screen host) {
        this.host = host;

        this.labelCitizen = tr("gui.gov.jail.citizen");
        this.labelCell = tr("gui.gov.jail.field.cell");
        this.labelBasis = tr("gui.gov.jail.field.basis");
        this.labelMinutes = tr("gui.gov.jail.field.minutes");
        this.labelWarrant = tr("gui.gov.jail.field.warrant");
        this.labelReason = tr("gui.gov.jail.field.reason");
        this.labelOfficer = tr("gui.gov.jail.field.officer");
        this.labelStarted = tr("gui.gov.jail.field.started");
        this.labelTerm = tr("gui.gov.jail.field.term");
        this.labelRemaining = tr("gui.gov.jail.field.remaining");
        this.labelStatus = tr("gui.gov.jail.field.status");
        this.labelId = tr("gui.gov.jail.field.id");
        this.labelCuffs = tr("gui.gov.jail.field.cuffs");
        this.labelRelease = tr("gui.gov.jail.field.release");
        this.labelConfinement = tr("gui.gov.jail.field.confinement");
        this.labelExtensions = tr("gui.gov.jail.extensions_label");

        this.buttonFilter = caps(tr("gui.gov.jail.filter_button"));
        this.buttonPrev = caps(tr("gui.gov.previous"));
        this.buttonNext = caps(tr("gui.gov.next"));
        this.buttonRefresh = caps(tr("gui.gov.refresh"));
        this.buttonRetry = caps(tr("gui.gov.card.retry"));
        this.buttonDetain = caps(tr("gui.gov.jail.detain_submit"));
        this.buttonRelease = caps(tr("gui.gov.jail.release_button"));
        this.buttonExtend = caps(tr("gui.gov.jail.extend_submit"));
        this.buttonCuff = caps(tr("gui.gov.jail.cuff_button"));
        this.buttonUncuff = caps(tr("gui.gov.jail.uncuff_button"));
        this.buttonPutCell = caps(tr("gui.gov.jail.cell_put_button"));

        this.textLoading = tr("gui.gov.jail.loading");
        this.textError = tr("gui.gov.jail.error");
        this.textEmptyActive = tr("gui.gov.jail.empty.active");
        this.textEmptyFilter = tr("gui.gov.jail.empty.filter");
        this.textNoSelection = tr("gui.gov.jail.no_selection");
        this.textReasonRequired = tr("gui.gov.jail.reason_required");
        this.textTargetRequired = tr("gui.gov.jail.target_required");
        this.textConsentRequired = tr("gui.gov.jail.consent_required");
        this.textWarrantRequired = tr("gui.gov.jail.warrant_required");
        this.textMinutesRequired = tr("gui.gov.jail.minutes_required");
        this.textMinutesInvalid = tr("gui.gov.jail.minutes_invalid");
        this.textNoWarrant = tr("gui.gov.jail.no_warrant");
        this.textNotReleased = tr("gui.gov.jail.not_released");
        this.textNoCell = tr("gui.gov.jail.no_cell");
        this.cellText = this.textNoCell;
        this.textSurrender = tr("gui.gov.jail.surrender_warning");
        this.textCuffsDisabled = tr("gui.gov.jail.cuffs_disabled");
        this.textReleasePolicy = tr("gui.gov.jail.release_policy");
        this.textExtendPolicy = tr("gui.gov.jail.extend_policy");
        this.textExtendNeedsWarrant = tr("gui.gov.jail.extend_needs_warrant");
        this.textConfirmPrefix = tr("gui.gov.jail.confirm_prefix");
        this.textNoWarrantRequired = tr("gui.gov.jail.no_warrant_required");
        this.consentYes = tr("gui.gov.jail.consent_yes");
        this.consentNo = tr("gui.gov.jail.consent_no");

        this.filterLabels = new String[] {
            caps(tr("gui.gov.jail.filter.active")), caps(tr("gui.gov.jail.filter.released")),
            caps(tr("gui.gov.jail.filter.expired")), caps(tr("gui.gov.jail.filter.all"))
        };
        this.filterLabel = this.buttonFilter + " " + this.filterLabels[0];
        this.detainPolicy = tr("gui.gov.jail.detain_policy", this.maxNoWarrantMinutes,
                this.maxWarrantMinutes);
        this.releasePolicy = this.textReleasePolicy;
        this.extendPolicy = this.textExtendPolicy;
        java.util.Arrays.fill(this.topBars, EMPTY);
        java.util.Arrays.fill(this.bottoms, EMPTY);
        java.util.Arrays.fill(this.statuses, EMPTY);
        java.util.Arrays.fill(this.bodies, EMPTY);
        java.util.Arrays.fill(this.lists, EMPTY);
        java.util.Arrays.fill(this.listBodies, EMPTY);
        java.util.Arrays.fill(this.details, EMPTY);
        java.util.Arrays.fill(this.summaries, EMPTY);
    }


}
