package com.horizonssmp.gov.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.horizonssmp.gov.Gov;
import com.horizonssmp.gov.common.GovFields;
import com.horizonssmp.gov.common.OpCode;
import com.horizonssmp.gov.common.SelfCardKeys;
import com.horizonssmp.gov.network.GovActionResultPayload;
import com.horizonssmp.gov.network.GovNetwork;
import com.horizonssmp.gov.network.GovOpenScreenPayload;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

/**
 * The institutional Gov shell.
 *
 * <p>The screen is an official record: an ink header band on neutral paper, a two-column section
 * rail, a sub-category strip and a document body. There is no chromatic colour anywhere; status is
 * carried by words, weight, rules and position. The shell owns all geometry: {@link GovLayout} is
 * computed once in {@link #init()} and every panel is laid out once with the resulting content
 * rectangle. No render path calls {@code layout()}.
 *
 * <p>The Dashboard sub-category is a form on paper. Every value comes from the server through one
 * {@code OPEN_TAB} request, matched by request id; while the answer is in flight the form says so,
 * and a refusal shows the server's own message with a retry action. The Law sub-category hosts
 * {@link GovLawPanel}, the page-based reader. The other sections host the existing panels and only
 * record their selected sub-category for the later panel work.
 */
class GovDashboardState extends Screen {

    /** The sections this build implements. Ids are wire values; translation keys are UI only. */
    public enum Tab {
        DASHBOARD(GovOpenScreenPayload.TAB_DASHBOARD, "gui.gov.tab.dashboard"),
        LAW(GovOpenScreenPayload.TAB_LAW, "gui.gov.tab.law"),
        JOBS(GovOpenScreenPayload.TAB_JOBS, "gui.gov.tab.jobs"),
        PEOPLE(GovOpenScreenPayload.TAB_PEOPLE, "gui.gov.tab.people"),
        NOTES(GovOpenScreenPayload.TAB_NOTES, "gui.gov.tab.notes"),
        FINES(GovOpenScreenPayload.TAB_FINES, "gui.gov.tab.fines"),
        VEHICLES(GovOpenScreenPayload.TAB_VEHICLES, "gui.gov.tab.vehicles"),
        WEAPONS(GovOpenScreenPayload.TAB_WEAPONS, "gui.gov.tab.weapons"),
        BUSINESSES(GovOpenScreenPayload.TAB_BUSINESSES, "gui.gov.tab.businesses"),
        JAIL(GovOpenScreenPayload.TAB_JAIL, "gui.gov.tab.jail"),
        AUDIT(GovOpenScreenPayload.TAB_AUDIT, "gui.gov.tab.audit"),
        TREASURY(GovOpenScreenPayload.TAB_TREASURY, "gui.gov.tab.treasury"),
        STORE(GovOpenScreenPayload.TAB_STORE, "gui.gov.tab.store");

         String id;
         String translationKey;

        Tab(final String id, final String translationKey) {
            this.id = id;
            this.translationKey = translationKey;
        }

        /** The stable wire id. */
        public String id() {
            return id;
        }

        /** The label translation key. */
        public String translationKey() {
            return translationKey;
        }

        /** The tab for a wire id; an unknown id opens the dashboard tab. */
        public static Tab fromId(final String id) {
            for (final Tab tab : values()) {
                if (tab.id.equals(id)) {
                    return tab;
                }
            }
            return DASHBOARD;
        }
    }

    /** What the self card is showing. */
     enum CardState {
        LOADING,
        READY,
        ERROR
    }

     static final GovRect EMPTY = new GovRect(0, 0, 0, 0);
     static final ResourceLocation SEAL_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Gov.MODID, "textures/gui/seal.png");

    /** The rail order demanded by the specification, which is not the enum order. */
     static final Tab[] SECTIONS = {
        Tab.DASHBOARD, Tab.LAW, Tab.FINES, Tab.VEHICLES, Tab.WEAPONS, Tab.PEOPLE,
        Tab.BUSINESSES, Tab.JAIL, Tab.JOBS, Tab.NOTES, Tab.TREASURY, Tab.AUDIT
    };

    /** One count per enum value, in {@link Tab#values()} order. */
     static final int[] SUBTAB_COUNTS = {2, 2, 3, 3, 3, 4, 6, 4, 6, 5, 2, 4, 1};

    /** One icon per enum value, in {@link Tab#values()} order. */
     static final GovIcons.GovIcon[] SECTION_ICONS = {
        GovIcons.GovIcon.DASHBOARD, GovIcons.GovIcon.LAW, GovIcons.GovIcon.JOBS,
        GovIcons.GovIcon.PEOPLE, GovIcons.GovIcon.NOTES, GovIcons.GovIcon.FINES,
        GovIcons.GovIcon.VEHICLES, GovIcons.GovIcon.WEAPONS, GovIcons.GovIcon.BUSINESSES,
        GovIcons.GovIcon.DETENTION, GovIcons.GovIcon.AUDIT, GovIcons.GovIcon.AUDIT,
        GovIcons.GovIcon.BUSINESSES
    };

     GovLawPanel lawPanel = new GovLawPanel((GovDashboardScreen) this);
     GovJobsPanel jobsPanel = new GovJobsPanel((GovDashboardScreen) this);
     GovPeoplePanel peoplePanel = new GovPeoplePanel((GovDashboardScreen) this);
     GovNotesPanel notesPanel = new GovNotesPanel((GovDashboardScreen) this);
     GovFinesPanel finesPanel = new GovFinesPanel((GovDashboardScreen) this);
     GovVehiclesPanel vehiclesPanel = new GovVehiclesPanel((GovDashboardScreen) this);
     GovWeaponsPanel weaponsPanel = new GovWeaponsPanel((GovDashboardScreen) this);
     GovBusinessesPanel businessesPanel = new GovBusinessesPanel((GovDashboardScreen) this);
     GovJailPanel jailPanel = new GovJailPanel((GovDashboardScreen) this);
     GovAuditPanel auditPanel = new GovAuditPanel((GovDashboardScreen) this);
     GovTreasuryPanel treasuryPanel = new GovTreasuryPanel((GovDashboardScreen) this);

     int[] subtabs = new int[Tab.values().length];
     GovRect[] railRects = new GovRect[SECTIONS.length];
     List<Tab> visibleTabs = new ArrayList<>(List.of(Tab.DASHBOARD, Tab.LAW, Tab.FINES, Tab.VEHICLES, Tab.WEAPONS, Tab.BUSINESSES, Tab.JAIL, Tab.TREASURY));
    @SuppressWarnings("unchecked")
     List<Integer>[] visibleSubtabs = new ArrayList[Tab.values().length];
     Component[] sectionLabels = new Component[Tab.values().length];
     Component[] sectionBold = new Component[Tab.values().length];
     String[] sectionNames = new String[Tab.values().length];
     Component[][] subtabLabels = new Component[Tab.values().length][];
     Component[][] subtabBold = new Component[Tab.values().length][];
     GovRect[] subtabRects = new GovRect[0];
     String tabPrevText = "<";
     String tabNextText = ">";
     GovRect subtabPrevRect = EMPTY;
     GovRect subtabNextRect = EMPTY;
     boolean subtabOverflow;
     boolean subtabPrevEnabled;
     boolean subtabNextEnabled;
     int subtabScroll;

    // Cached shell text. Nothing in this block is built in render().
     Component sealLetter = GovText.boldCached("G");
     Component closeLetter = GovText.boldCached("X");
     Component systemLine;
     Component agencyLine;
     String seasonText;
     String officialText;
     String footerLeft;
     String footerOnly;
     String footerRight;
     String openText;
     String appointmentWide;
     String appointmentShort;
     String noAppointment;
     String onDuty;
     String offDuty;
     String recordTitle;
     String restrictionsTitle;
     Component recordTitleBold;
     Component restrictionsTitleBold;
     Component errorTitleBold;
     String formText;
     String refreshText;
     String retryText;
     String restrictedText;
     String loadingText;
     String errorTitle;
     String receptionNote;
     String noRestrictions;
     String clearText;
     String flaggedText;
     String heldText;
     String dueText;
     Component clearStamp;
     Component flaggedStamp;
     Component heldStamp;
     Component dueStamp;
     String[] cardLabels = new String[7];

     GovLayout layout;
     Tab activeTab;
     int hoveredRail = -1;

     GovRect closeRect = EMPTY;
     GovRect stampRect = EMPTY;
     GovRect sealRect = EMPTY;
     GovRect refreshRect = EMPTY;
     GovRect retryRect = EMPTY;
     GovRect cardRect = EMPTY;
     int titleX;
     int titleWidth;
     int headerBlockY;
     int seasonX = -1;
     int seasonWidth;
     int cardPad;
     int cardHeaderH;
     int cardRowsTop;
     int cardRowPitch = 11;
     int cardMaxRows;

     int railStripTop;
     int railStripHeight;
     int railPitch;
     int railRowHeight;
     boolean railTwoColumn;

     CardState cardState = CardState.LOADING;
     Map<String, String> cardFields = new LinkedHashMap<>();
     boolean canPeopleTab = false;
     boolean canNotesTab = false;
     boolean canAuditTab = false;
     boolean canJobsManageTab = false;
     String cardErrorLine = "";
     String pendingReqId = "";
     String cardStatus = "";
     String railJob = "";
     String railDuty = "";
     List<CardRow> cardRows = List.of();
     List<RestrictionRow> restrictions = List.of();
     List<FormattedCharSequence> receptionNoteLines = List.of();
     List<FormattedCharSequence> cardErrorLines = List.of();

    /** Opens on the Dashboard tab. */
    GovDashboardState() {
        this(Tab.DASHBOARD);
    }

    /** Opens on a specific tab. */
    GovDashboardState(final Tab tab) {
        super(Component.translatable("gui.gov.title"));
        GovDashboardInit.init(this, tab);
    }

     net.minecraft.client.gui.Font font() {
        return this.font;
    }

    static String tr(final String key, final Object... args) {
        return Component.translatable(key, args).getString();
    }

     static String upper(final String text) {
        return text == null ? "" : text.toUpperCase(Locale.ROOT);
    }

     record CardRow(String label, String value, Component stamp) {
    }

     record RestrictionRow(Component stamp, String detail) {
    }
}
