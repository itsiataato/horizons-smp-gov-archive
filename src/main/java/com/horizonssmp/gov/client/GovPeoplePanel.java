package com.horizonssmp.gov.client;

import com.horizonssmp.gov.client.GovDashboardScreen;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.horizonssmp.gov.common.GovFields;
import com.horizonssmp.gov.common.NoteKeys;
import com.horizonssmp.gov.common.OpCode;
import com.horizonssmp.gov.common.PersonKeys;
import com.horizonssmp.gov.network.GovActionResultPayload;
import com.horizonssmp.gov.network.GovNetwork;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

/**
 * Facade for the People section. Implementation lives in GovPeople* helper classes.
 */
final class GovPeoplePanel {

    static final int SUBTAB_SEARCH = GovPeoplePanelModel.SUBTAB_SEARCH;
    static final int SUBTAB_PERSON = GovPeoplePanelModel.SUBTAB_PERSON;
    static final int SUBTAB_NOTES = GovPeoplePanelModel.SUBTAB_NOTES;

    static final long ARM_FLOOR_MS = GovPeoplePanelModel.ARM_FLOOR_MS;
    static final long ARM_MS = GovPeoplePanelModel.ARM_MS;
    static final int MIN_LINE_PITCH = GovPeoplePanelModel.MIN_LINE_PITCH;
    static final int NOTE_ROW_MIN = GovPeoplePanelModel.NOTE_ROW_MIN;
    static final int NOTE_ROW_MAX = GovPeoplePanelModel.NOTE_ROW_MAX;
    static final DateTimeFormatter SHORT_TIME = GovPeoplePanelModel.SHORT_TIME;

    static final GovRect EMPTY = GovPeoplePanelModel.EMPTY;

    final Screen host;

    Font font;
    EditBox searchBox;
    EditBox noteBox;
    List<EditBox> widgets = List.of();
    boolean active;
    boolean searchBoxVisible;
    boolean noteBoxVisible;

    final Map<String, GovPeoplePanelModel.RequestKind> pending = new LinkedHashMap<>();
    final List<GovPeoplePanelModel.PersonRow> people = new ArrayList<>();
    final List<GovPeoplePanelModel.PersonView> personViews = new ArrayList<>();
    final List<GovPeoplePanelModel.NoteRow> notes = new ArrayList<>();
    final List<GovPeoplePanelModel.NoteView> noteViews = new ArrayList<>();
    final List<GovRect> noteEditRects = new ArrayList<>();
    final List<GovRect> noteDeleteRects = new ArrayList<>();
    final List<GovPeoplePanelModel.DetailLine> detailLines = new ArrayList<>();
    final List<GovPeoplePanelModel.Hit> hits = new ArrayList<>();

    GovPeoplePanelModel.LoadState searchState = GovPeoplePanelModel.LoadState.IDLE;
    GovPeoplePanelModel.LoadState detailState = GovPeoplePanelModel.LoadState.NONE;
    GovPeoplePanelModel.LoadState notesState = GovPeoplePanelModel.LoadState.NONE;
    String searchError = "";
    String detailError = "";
    String status = "";
    boolean statusIsError;

    int peopleTotal;
    String latestSearchReqId = "";
    String latestDetailReqId = "";
    String latestNotesReqId = "";

    String selectedUuid = "";
    String selectedName = "";
    int selectedNoteIndex = -1;

    String cardName = "";
    String cardUuid = "";
    boolean cardOnline;
    String cardJob = "";
    String cardJobId = "";
    String cardRank = "";
    int cardFines;
    String cardLicense = "";
    String cardWeapons = "";
    boolean canViewNotes;
    boolean canManageNotes;
    boolean canPostNotes;

    int notesTotal;
    int notesPage;
    int notesPages = 1;

    String editingNoteId = "";
    String deleteArmedNoteId = "";
    long deleteArmedAt;
    final GovKeyRepeat enterRepeat = new GovKeyRepeat();

    int searchScroll;
    int searchScrollMax;
    int personScroll;
    int personScrollMax;
    int notesScroll;
    int notesScrollMax;

    int mode = SUBTAB_SEARCH;

    GovRect bounds = EMPTY;
    GovRect barRect = EMPTY;
    GovRect refreshRect = EMPTY;
    GovRect openSearchRect = EMPTY;
    GovRect searchFieldRect = EMPTY;
    GovRect searchButtonRect = EMPTY;
    GovRect countRect = EMPTY;
    GovRect listRect = EMPTY;
    GovRect listInnerRect = EMPTY;
    GovRect headerRect = EMPTY;
    GovRect personFormRect = EMPTY;
    GovRect personFormInnerRect = EMPTY;
    GovRect notesHeaderRect = EMPTY;
    GovRect notesListRect = EMPTY;
    GovRect notesListInnerRect = EMPTY;
    GovRect composerRect = EMPTY;
    GovRect noteFieldRect = EMPTY;
    GovRect noteActionRect = EMPTY;
    GovRect noteCancelRect = EMPTY;
    GovRect notesStatusRect = EMPTY;
    GovRect notePrevRect = EMPTY;
    GovRect noteNextRect = EMPTY;
    GovRect notesPageRect = EMPTY;
    GovRect notesRefreshRect = EMPTY;

    boolean personTwoColumn;
    boolean searchNarrow;
    int searchRowH = GovMetrics.ROW_H;
    int personPitch = MIN_LINE_PITCH;
    int personRowsPerColumn = 1;
    int noteRowHeight = NOTE_ROW_MIN;
    int noteMaxLines = 1;
    int noteEditW;
    int noteDeleteW;
    int noteTextW;
    int personLabelW;
    int personValueW;
    int personFullValueW;

    String searchButtonText;
    String refreshText;
    String openSearchText;
    String prevText;
    String nextText;
    String idleText;
    String loadingText;
    String searchErrorText;
    String detailErrorText;
    String notesErrorText;
    String noResultsText;
    String noCitizenText;
    String noSelectionText;
    String noNotesText;
    String notesHiddenText;
    String onlineText;
    String offlineText;
    String unknownAuthorText;
    String noteAddText;
    String noteSaveText;
    String noteCancelText;
    String noteEditText;
    String noteDeleteText;
    String noteConfirmDeleteText;
    String noteScopeText;
    String personTitleText;
    Component personTitleComponent = Component.empty();
    String uuidLabel;
    String notAvailableText;
    String rankLabel;
    String standingLabel;
    String dueText;
    String clearText;
    String unemployedText;
    String doneText;

    String[] detailLabels = new String[0];

    String searchCountText = "";
    String cardStatusText = "";
    String cardUuidText = "";
    int personTitleWidth;
    String notesTitleText = "";
    Component notesTitleComponent = Component.empty();
    int notesTitleWidth;
    String notesCountText = "";
    String notesPageText = "";

    GovPeoplePanel(final Screen host) {
        this.host = host;
        GovPeopleLabels.rebuildLabels(this);
    }

    List<EditBox> createWidgets(final Font font) {
        return GovPeopleWidgets.createWidgets(this, font);
    }

    EditBox createSearchBox(final Font font) {
        return GovPeopleWidgets.createSearchBox(this, font);
    }

    EditBox createNoteBox(final Font font) {
        return GovPeopleWidgets.createNoteBox(this, font);
    }

    void setActive(final boolean active) {
        GovPeopleLifecycle.setActive(this, active);
    }

    void reset() {
        GovPeopleLifecycle.reset(this);
    }

    void layout(final int left, final int top, final int right, final int bottom, final Font font) {
        GovPeopleGeometry.layout(this, left, top, right, bottom, font);
    }

    void setSubtab(final int index) {
        GovPeopleLifecycle.setSubtab(this, index);
    }

    void openPerson(final String uuid, final String name) {
        GovPeopleSelection.openPerson(this, uuid, name);
    }

    void onActionResult(final GovActionResultPayload payload) {
        GovPeopleRequests.onActionResult(this, payload);
    }

    void render(final GuiGraphics graphics, final int mouseX, final int mouseY) {
        GovPeopleRender.render(this, graphics, mouseX, mouseY);
    }

    boolean mouseClicked(final double mouseX, final double mouseY, final int button) {
        return GovPeopleInput.mouseClicked(this, mouseX, mouseY, button);
    }

    boolean mouseScrolled(final double mouseX, final double mouseY, final double scrollY) {
        return GovPeopleInput.mouseScrolled(this, mouseX, mouseY, scrollY);
    }

    boolean keyPressed(final int keyCode, final int scanCode, final int modifiers) {
        return GovPeopleInput.keyPressed(this, keyCode, scanCode, modifiers);
    }

    boolean charTyped(final char codePoint, final int modifiers) {
        return false;
    }

    static String tr(final String key, final Object... args) {
        return GovPeoplePanelModel.tr(key, args);
    }

    static String upper(final String text) {
        return GovPeoplePanelModel.upper(text);
    }
}
