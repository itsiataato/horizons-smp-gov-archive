package com.horizonssmp.gov.client;

import com.horizonssmp.gov.client.GovDashboardScreen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.horizonssmp.gov.common.GovFields;
import com.horizonssmp.gov.common.JobKeys;
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

import static com.horizonssmp.gov.client.GovJobsTypes.*;
import static com.horizonssmp.gov.client.GovJobsConstants.*;

abstract class GovJobsPanelBase {

    final Screen host;
    final List<EditBox> widgetList = new ArrayList<>(1);

    Font font;
    EditBox createBox;
    boolean createBoxVisible;
    boolean active;
    int subtab;
    Modal modal = Modal.NONE;
    final GovKeyRepeat enterRepeat = new GovKeyRepeat();
    long modalOpenedAt;

    final Map<String, RequestKind> pending = new LinkedHashMap<>();
    final List<JobRow> jobs = new ArrayList<>();
    final List<String> onlineNames = new ArrayList<>();
    final List<MemberRow> members = new ArrayList<>();
    final List<String> jobCaps = new ArrayList<>();
    final List<String> registry = new ArrayList<>();
    final List<RosterView> rosterViews = new ArrayList<>();
    final List<MemberView> memberViews = new ArrayList<>();
    final List<CapView> capViews = new ArrayList<>();
    final List<DetailLine> detailLines = new ArrayList<>();
    final List<Hit> hits = new ArrayList<>();
    final List<GovRect> memberRemoveRects = new ArrayList<>();
    final List<GovRect> capButtonRects = new ArrayList<>();
    final List<GovRect> pickerRects = new ArrayList<>();
    final List<Integer> pickerIndices = new ArrayList<>();
    final List<String> pickerLabels = new ArrayList<>();
    final List<String> pickerConfirmLabels = new ArrayList<>();

    ListState listState = ListState.LOADING;
    DetailState detailState = DetailState.NONE;
    String listError = "";
    String listErrorText = "";
    String detailError = "";
    String detailErrorText = "";
    String memberErrorText = "";
    String capErrorText = "";
    String status = "";
    String statusText = "";
    String latestListReqId = "";
    String latestInfoReqId = "";
    boolean statusIsError;

    int jobsTotal;
    int onlineTotal;
    int membersTotal;
    int capsTotal;
    int registryTotal;
    boolean canManage;
    boolean canAssign;

    String selectedJobId = "";
    String selectedJobName = "";
    String selectedJobType = "";
    String selectedRank = "";
    String selectedDepartment = "";

    int jobPage;
    int jobPages = 1;
    long deleteArmedAt;
    long memberArmedAt;
    String memberArmedTarget = "";
    long capArmedAt;
    String capArmedTarget = "";
    long assignArmedAt;
    String assignArmedTarget = "";

    int rosterScroll;
    int rosterScrollMax;
    int rosterPitch = 18;
    int memberScroll;
    int memberScrollMax;
    int memberPitch = 18;
    int capScroll;
    int capScrollMax;
    int capPitch = 18;
    int detailScroll;
    int detailScrollMax;
    int pickerScroll;
    int pickerScrollMax;
    int pickerPitch = 14;

    GovRect rect = EMPTY;
    GovRect body = EMPTY;
    GovRect bar = EMPTY;
    GovRect statusRect = EMPTY;
    GovRect refreshRect = EMPTY;

    GovRect rosterList = EMPTY;
    GovRect rosterDetail = EMPTY;
    GovRect rosterSummary = EMPTY;
    GovRect rosterHeader = EMPTY;
    GovRect rosterRows = EMPTY;
    GovRect rosterScrollTrack = EMPTY;
    GovRect rosterPrev = EMPTY;
    GovRect rosterNext = EMPTY;
    GovRect rosterPage = EMPTY;
    GovRect rosterNew = EMPTY;
    GovRect rosterDelete = EMPTY;

    GovRect detailHeader = EMPTY;
    GovRect detailBody = EMPTY;
    GovRect detailScrollTrack = EMPTY;

    GovRect memberList = EMPTY;
    GovRect memberHeader = EMPTY;
    GovRect memberRows = EMPTY;
    GovRect memberScrollTrack = EMPTY;
    GovRect memberAdd = EMPTY;
    GovRect memberStatusBar = EMPTY;

    GovRect capList = EMPTY;
    GovRect capHeader = EMPTY;
    GovRect capRows = EMPTY;
    GovRect capScrollTrack = EMPTY;
    GovRect capStatusBar = EMPTY;

    GovRect modalRect = EMPTY;
    GovRect modalBody = EMPTY;
    GovRect modalClose = EMPTY;
    GovRect createLabel = EMPTY;
    GovRect createField = EMPTY;
    GovRect createSubmit = EMPTY;
    GovRect createCancel = EMPTY;
    GovRect pickerList = EMPTY;
    GovRect pickerCancel = EMPTY;

    GovRect rosterListRetry = EMPTY;
    GovRect rosterDetailRetry = EMPTY;
    GovRect memberRetry = EMPTY;
    GovRect capRetry = EMPTY;

    String pageLabel = "";
    String rosterHeaderText = "";
    String rosterSummaryText = "";
    String memberHeaderText = "";
    String capHeaderText = "";
    String rosterEmptyTitle = "";
    String rosterEmptyDetail = "";
    String memberEmptyTitle = "";
    String capEmptyTitle = "";
    String detailNameText = "";
    Component detailNameComponent = Component.empty();
    String detailTypeText = "";
    int detailTypeWidth;
    String modalTitle = "";
    String modalTitleText = "";
    String createLabelText = "";

    final String labelId;
    final String labelTitle;
    final String labelType;
    final String labelRank;
    final String labelDepartment;
    final String labelMembers;
    final String labelCaps;
    final String labelName;
    final String labelState;
    final String labelDuty;
    final String labelSince;

    final String newAction;
    final String createAction;
    final String cancelAction;
    final String deleteAction;
    final String deleteConfirm;
    final String addMemberAction;
    final String removeAction;
    final String removeConfirm;
    final String grantAction;
    final String refreshAction;

    final String loadingText;
    final String errorText;
    final String noSelectionText;
    final String noMembersText;
    final String noJobsText;
    final String noCapsText;
    final String noPlayersText;
    final String protectedText;
    final String grantedText;
    final String notGrantedText;
    final String notRecordedText;
    final String onlineText;
    final String offlineText;
    final String armConfirmText;
    final String nameRequiredText;
    final String createTitle;
    final String pickerTitle;
    final String assignAction;
    final String doneText;
    final String retryText;
    final String builtInTypeText;
    final String templateTypeText;
    final String customTypeText;
    final String protectedUpperText;
    final Component closeLetter = GovText.boldCached(CLOSE_LETTER);

    GovJobsPanelBase(final Screen host) {
        this.host = host;

        this.labelId = upper(tr("gui.gov.jobs.field.id"));
        this.labelTitle = upper(tr("gui.gov.jobs.field.title"));
        this.labelType = upper(tr("gui.gov.jobs.field.type"));
        this.labelRank = upper(tr("gui.gov.jobs.field.rank"));
        this.labelDepartment = upper(tr("gui.gov.jobs.field.department"));
        this.labelMembers = upper(tr("gui.gov.jobs.field.members"));
        this.labelCaps = upper(tr("gui.gov.jobs.field.caps"));
        this.labelName = upper(tr("gui.gov.jobs.new_name"));
        this.labelState = upper(tr("gui.gov.jobs.field.state"));
        this.labelDuty = upper(tr("gui.gov.jobs.field.duty"));
        this.labelSince = upper(tr("gui.gov.jobs.field.since"));

        this.newAction = upper(tr("gui.gov.jobs.new_action"));
        this.createAction = upper(tr("gui.gov.jobs.create"));
        this.cancelAction = upper(tr("gui.gov.jobs.cancel"));
        this.deleteAction = upper(tr("gui.gov.jobs.delete"));
        this.deleteConfirm = upper(tr("gui.gov.jobs.confirm_delete"));
        this.addMemberAction = upper(tr("gui.gov.jobs.add_member"));
        this.removeAction = upper(tr("gui.gov.jobs.remove"));
        this.removeConfirm = upper(tr("gui.gov.jobs.remove_confirm"));
        this.grantAction = upper(tr("gui.gov.jobs.grant"));
        this.refreshAction = upper(tr("gui.gov.refresh"));

        this.loadingText = tr("gui.gov.jobs.loading");
        this.errorText = tr("gui.gov.jobs.error");
        this.noSelectionText = tr("gui.gov.jobs.no_selection");
        this.noMembersText = tr("gui.gov.jobs.no_members");
        this.noJobsText = tr("gui.gov.jobs.no_jobs");
        this.noCapsText = tr("gui.gov.jobs.no_caps");
        this.noPlayersText = tr("gui.gov.jobs.no_players");
        this.protectedText = tr("gui.gov.jobs.protected");
        this.grantedText = upper(tr("gui.gov.jobs.granted"));
        this.notGrantedText = upper(tr("gui.gov.jobs.not_granted"));
        this.notRecordedText = upper(tr("gui.gov.jobs.not_recorded"));
        this.onlineText = upper(tr("gui.gov.jobs.state.online"));
        this.offlineText = upper(tr("gui.gov.jobs.state.offline"));
        this.armConfirmText = tr("gui.gov.jobs.arm_confirm");
        this.nameRequiredText = tr("gui.gov.jobs.name_required");
        this.createTitle = upper(tr("gui.gov.jobs.new_title"));
        this.pickerTitle = upper(tr("gui.gov.jobs.pick_player"));
        this.assignAction = upper(tr("gui.gov.jobs.assign"));
        // There is no gui.gov.jobs.done key; the weapons panel's shared completion string is the
        // closest existing government-wide wording, so reuse it rather than invent a new key here.
        this.doneText = tr("gui.gov.weapons.done");
        this.retryText = upper(tr("gui.gov.card.retry"));
        this.builtInTypeText = upper(tr("gui.gov.jobs.type.built_in"));
        this.templateTypeText = upper(tr("gui.gov.jobs.type.template"));
        this.customTypeText = upper(tr("gui.gov.jobs.type.custom"));
        this.protectedUpperText = upper(this.protectedText);
    }

}
