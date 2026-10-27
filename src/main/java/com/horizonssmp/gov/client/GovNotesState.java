package com.horizonssmp.gov.client;

import com.horizonssmp.gov.client.GovNotesTypes.*;
import static com.horizonssmp.gov.client.GovNotesTypes.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.horizonssmp.gov.common.GovFields;
import com.horizonssmp.gov.common.NoteKeys;
import com.horizonssmp.gov.common.OpCode;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import com.horizonssmp.gov.client.GovDashboardScreen;

class GovNotesState {
    protected final GovDashboardScreen host;

    protected Font font;
    protected boolean active;
    protected final GovKeyRepeat enterRepeat = new GovKeyRepeat();
    protected int mode;

    protected final Map<String, Pending> pending = new LinkedHashMap<>();
    protected String latestScopesReqId = "";
    protected boolean scopesPending;
    protected long scopesSentAt;
    protected boolean mutationPending;
    protected long mutationSentAt;

    protected final View allView = new View(0, "", "", false);
    protected final Map<String, View> jobViews = new HashMap<>();
    protected final Map<String, BusinessState> bizStates = new HashMap<>();

    protected List<ScopeRow> jobScopes = NO_SCOPES;
    protected List<ScopeRow> businessScopes = NO_SCOPES;
    protected Load scopesState = Load.NONE;
    protected String scopesError = "";
    protected String selectedJobId = "";
    protected String selectedJobLabel = "";
    protected String selectedBizId = "";
    protected String selectedBizLabel = "";

    protected String status = "";
    protected boolean statusIsError;
    protected String deleteArmedNoteId = "";
    protected long deleteArmedAt;
    protected String editingNoteId = "";
    protected View editingView;
    protected String editingTarget = "";
    protected String editingTargetLabel = "";

    protected EditBox targetBox;
    protected EditBox textBox;
    protected List<EditBox> widgets = List.of();
    protected boolean hasText;
    protected boolean hasTarget;
    protected boolean targetDirty;
    protected boolean pickerOpen;

    // Cached labels.
    protected String allTitle;
    protected String jobTitle;
    protected String bizTitle;
    protected Component allTitleBold;
    protected Component jobTitleBold;
    protected Component bizTitleBold;
    protected String notesSubtitle;
    protected String registerText;
    protected String scopesText;
    protected String boardText;
    protected String personalText;
    protected String postText;
    protected String saveText;
    protected String editText;
    protected String deleteText;
    protected String confirmDeleteText;
    protected String cancelText;
    protected String retryText;
    protected String refreshText;
    protected String loadText;
    protected String previousText;
    protected String nextText;
    protected String readOnlyText;
    protected String teamText;
    protected String noJobText;
    protected String noBizText;
    protected String noNotesText;
    protected String enterTargetText;
    protected String pressRefreshText;
    protected String loadingText;
    protected String errorTitle;
    protected String mutationErrorText;
    protected String savedText;
    protected String unknownAuthorText;
    protected String newestFirstText;
    protected Component noJobBold;
    protected Component noBizBold;
    protected Component noNotesBold;
    protected Component enterTargetBold;
    protected Component pressRefreshBold;
    protected Component errorTitleBold;
    protected Component targetHint;
    protected Component noteHint;

    // Geometry
    protected GovRect bounds = EMPTY;
    protected GovRect inner = EMPTY;
    protected GovRect headerRect = EMPTY;
    protected GovRect footerRect = EMPTY;
    protected GovRect bodyRect = EMPTY;
    protected GovRect scopeRect = EMPTY;
    protected GovRect mainRect = EMPTY;
    protected GovRect listRect = EMPTY;
    protected GovRect composerRect = EMPTY;
    protected GovRect scopeBarRect = EMPTY;
    protected GovRect scopeToggleRect = EMPTY;
    protected GovRect scopeHeaderRect = EMPTY;
    protected GovRect scopeRetryRect = EMPTY;
    protected GovRect bizSwitchRect = EMPTY;
    protected GovRect boardRect = EMPTY;
    protected GovRect personalRect = EMPTY;
    protected GovRect prevRect = EMPTY;
    protected GovRect nextRect = EMPTY;
    protected GovRect refreshRect = EMPTY;
    protected GovRect postRect = EMPTY;
    protected GovRect cancelRect = EMPTY;
    protected GovRect targetFieldRect = EMPTY;
    protected GovRect textFieldRect = EMPTY;
    protected GovRect retryRect = EMPTY;

    protected boolean wide;
    protected int footerTextY;
    protected int pageX;
    protected int pageW;
    protected int statusX;
    protected int statusW;
    protected int editTargetW;

    protected List<ScopeHit> scopeHits = NO_SCOPE_HITS;
    protected List<ScopeHit> pickerHits = NO_SCOPE_HITS;
    protected List<FormattedCharSequence> scopesErrorLines = NO_LINES;

    GovNotesState(final GovDashboardScreen host) {
        this.host = host;
    }
}
