package com.horizonssmp.gov.client;

import com.horizonssmp.gov.common.WeaponKeys;
import com.horizonssmp.gov.client.GovRect;
import com.horizonssmp.gov.client.GovKeyRepeat;
import com.horizonssmp.gov.network.GovActionResultPayload;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.util.FormattedCharSequence;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.horizonssmp.gov.client.GovWeaponsConstants.*;
import com.horizonssmp.gov.client.GovWeaponsTypes.Click;
import com.horizonssmp.gov.client.GovWeaponsTypes.Modal;
import com.horizonssmp.gov.client.GovWeaponsTypes.RequestKind;
import com.horizonssmp.gov.client.GovWeaponsTypes.LoadState;
import com.horizonssmp.gov.client.GovWeaponsTypes.WeaponRow;
import com.horizonssmp.gov.client.GovWeaponsTypes.PermitRow;
import com.horizonssmp.gov.client.GovWeaponsTypes.AreaRow;
import com.horizonssmp.gov.client.GovWeaponsTypes.Button;
import com.horizonssmp.gov.client.GovWeaponsTypes.Hit;
import com.horizonssmp.gov.client.GovWeaponsTypes.DetailLine;

abstract class GovWeaponsPanelBase {

    final Screen host;

    Font font;
    boolean active;
    int subtab;

    EditBox classifyBox;
    EditBox registerSerialBox;
    EditBox registerTypeBox;
    EditBox ownerBox;
    EditBox transferOwnerBox;
    EditBox permitTargetBox;
    EditBox issueTargetBox;
    EditBox reasonBox;
    EditBox dimBox;
    EditBox xBox;
    EditBox yBox;
    EditBox zBox;
    EditBox radiusBox;
    EditBox minutesBox;
    EditBox areaMinutesBox;
    EditBox areaReasonBox;
    List<EditBox> widgets = List.of();

    final Map<String, RequestKind> pending = new LinkedHashMap<>();
    final List<WeaponRow> weaponRows = new ArrayList<>();
    final List<PermitRow> permitRows = new ArrayList<>();
    final List<AreaRow> areaRows = new ArrayList<>();
    final Map<String, String> detail = new LinkedHashMap<>();
    final Map<String, String> classification = new LinkedHashMap<>();

    LoadState listState = LoadState.NONE;
    LoadState detailState = LoadState.NONE;
    String listError = "";
    String detailError = "";
    String status = "";
    boolean statusIsError;
    String latestListReqId = "";
    String latestDetailReqId = "";
    String latestPermitReqId = "";
    String latestAreaReqId = "";

    int weaponTotal;
    int weaponPage;
    int weaponPages = 1;
    int permitTotal;
    int permitPage;
    int permitPages = 1;
    int areaTotal;
    int areaPage;
    int areaPages = 1;
    String filter = WeaponKeys.FILTER_ALL;
    String viewerName = "";
    String viewerUuid = "";
    String permitTarget = "";
    boolean canRegister;
    boolean canViewAll;
    boolean canMilitary;
    boolean hasTownCores;
    long seasonEnd;

    String selectedSerial = "";
    String selectedPermitId = "";
    String selectedAreaId = "";
    String registerClass = "l";
    String permitClass = "l";
    String suspendMinutes = "60";

    final int[] listScrollRows = new int[SUBTABS];
    final int[] detailScrollRows = new int[SUBTABS];
    int visibleRows;
    int visibleDetailRows;
    int listScrollMaxRows;
    int detailScrollMaxRows;
    int rowPitch = ROW_PITCH;
    int detailPitch = DETAIL_PITCH;

    GovRect bounds = EMPTY;
    GovRect modalRect = EMPTY;
    GovRect modalCloseRect = EMPTY;
    final GovRect[] topBars = new GovRect[SUBTABS];
    final GovRect[] bottoms = new GovRect[SUBTABS];
    final GovRect[] statuses = new GovRect[SUBTABS];
    final GovRect[] bodies = new GovRect[SUBTABS];
    final GovRect[] actions = new GovRect[SUBTABS];
    final GovRect[] lists = new GovRect[SUBTABS];
    final GovRect[] listBodies = new GovRect[SUBTABS];
    final GovRect[] details = new GovRect[SUBTABS];
    final GovRect[] summaries = new GovRect[SUBTABS];
    GovRect listScrollbarRect = EMPTY;
    GovRect detailScrollbarRect = EMPTY;
    GovRect listRetryRect = EMPTY;
    GovRect detailRetryRect = EMPTY;

    GovRect classifyFrame = EMPTY;
    GovRect permitFilterFrame = EMPTY;
    GovRect modalOwnerFrame = EMPTY;
    GovRect modalTransferOwnerFrame = EMPTY;
    GovRect modalTargetFrame = EMPTY;
    GovRect modalReasonFrame = EMPTY;
    GovRect modalSerialFrame = EMPTY;
    GovRect modalTypeFrame = EMPTY;
    GovRect modalClassFrame = EMPTY;
    GovRect modalIssueClassFrame = EMPTY;
    GovRect modalDimFrame = EMPTY;
    GovRect modalXFrame = EMPTY;
    GovRect modalYFrame = EMPTY;
    GovRect modalZFrame = EMPTY;
    GovRect modalRadiusFrame = EMPTY;
    GovRect modalMinutesFrame = EMPTY;
    GovRect modalAreaMinutesFrame = EMPTY;
    GovRect modalAreaReasonFrame = EMPTY;
    int modalLabelW;
    int modalFieldX;
    int modalRow1;
    int modalRow2;
    int modalRow3;
    int modalRowH;

    final List<Button> buttons = new ArrayList<>();
    final List<Button> modalButtons = new ArrayList<>();
    final List<Hit> rowHits = new ArrayList<>();
    final List<DetailLine> detailLines = new ArrayList<>();
    int detailLinesWidth = -1;
    String detailLinesKey = "";
    int detailRevision;

    Modal modal = Modal.NONE;
    long modalOpenedAt;
    Click armedAction = Click.NONE;
    long armedAt;
    final GovKeyRepeat enterRepeat = new GovKeyRepeat();
    String modalTitle = "";
    String modalSubmitLabel = "";
    String modalContext = "";

    String listHeader = "";
    String pageLabel = "";
    String filterLabel = "";
    String policyText = "";
    String weaponSummary = "";
    String permitSummary = "";
    String areaSummary = "";
    String detailTitle = "";
    String classifyCategory = "";
    String classifyPermit = "";
    String classifyCitizen = "";
    String classifyReason = "";
    boolean classifyBlocked;
    List<FormattedCharSequence> classifyReasonLines = List.of();

    String labelCitizen;
    String labelCategory;
    String labelReason;
    String labelMinutes;
    String labelSerial;
    String labelType;
    String labelOwner;
    String labelDimension;
    String labelRadius;
    String labelItem;
    String labelPermit;
    String labelStatus;
    String labelHolder;
    String labelIssued;
    String labelExpires;
    String labelIssuedBy;
    String labelWindow;
    String labelPurpose;
    String labelArea;
    String labelBox;
    String labelCenter;
    String labelClass;
    String labelRegistered;
    String labelTransfers;
    String labelDealer;
    String labelSale;
    String labelSource;
    String labelAuthority;
    String labelPermitRequired;
    String labelCitizenAllowed;

    String buttonFilter;
    String buttonRegister;
    String buttonRecord;
    String buttonTransfer;
    String buttonRevoke;
    String buttonLoss;
    String buttonIssue;
    String buttonLoad;
    String buttonCreateArea;
    String buttonClassify;
    String buttonHeld;
    String buttonSuspend;
    String buttonPrev;
    String buttonNext;
    String buttonRefresh;
    String buttonCancel;
    String buttonClose;
    String buttonRetry;

    String textLoading;
    String textError;
    String textEmptyAll;
    String textEmptyOwn;
    String textPermitEmpty;
    String textAreaEmpty;
    String textNoSelection;
    String textReasonRequired;
    String textFieldsRequired;
    String textClassifyRequired;
    String textNoCitizenPath;
    String textExplosivePath;
    String textNoPath;
    String textYes;
    String textNo;
    String textConfirmPrefix;
    String textPolicyShort;
    String textPolicyLong;
    String textClassifierPolicy;

    String[] permitClassIds = {"l", "s", "r", "p", "h", "d", "g", "blasting"};
    String[] registerClassIds = {"l", "s", "r", "p", "h"};
    String[] permitClassLabels;
    String[] filterIds = {
        WeaponKeys.FILTER_ALL, WeaponKeys.FILTER_ACTIVE,
        WeaponKeys.FILTER_REVOKED, WeaponKeys.FILTER_LOST
    };
    String[] filterLabels;


    GovWeaponsPanelBase(final Screen host) {
        this.host = host;
        GovWeaponsInit.init(this);
    }

    static String tr(final String key, final Object... args) {
        return net.minecraft.network.chat.Component.translatable(key, args).getString();
    }

    static String caps(final String text) {
        return text == null ? "" : text.toUpperCase(java.util.Locale.ROOT);
    }

    static String buttonLabel(final String key) {
        return caps(tr(key)).replace("...", "").strip();
    }
}
