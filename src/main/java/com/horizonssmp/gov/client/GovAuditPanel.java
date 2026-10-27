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

import com.horizonssmp.gov.common.AuditKeys;
import com.horizonssmp.gov.common.GovExportSection;
import com.horizonssmp.gov.common.GovFields;
import com.horizonssmp.gov.common.BusinessKeys;
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

/**
 * Facade for the Audit section. Implementation lives in GovAudit* helper classes.
 */
final class GovAuditPanel {

    static final int SUBTAB_AUDIT = 0;
    static final int SUBTAB_ADMIN = 1;

    static final long ARM_MS = 5000L;
    static final long ARM_FLOOR_MS = 600L;
    static final long EXPORT_ARM_MS = 5000L;
    static final int SHORT_DETAIL_H = 160;
    static final DateTimeFormatter SHORT_TIME =
            DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneOffset.UTC);

    static final GovRect EMPTY = new GovRect(0, 0, 0, 0);

    enum Mode { AUDIT, ADMIN }

    enum ExportArm { NONE, AUDIT, SECTION }

    enum RequestKind {
        LIST, DETAIL, EXPORT, ADMIN_EXPORT, STATUS, BEGIN, COMMIT, HISTORY
    }

    enum LoadState { NONE, LOADING, READY, ERROR }

    enum Action {
        APPLY, OUTCOME, WINDOW, EXPORT_AUDIT, ROW, PREV, NEXT, REFRESH,
        SEASON_REFRESH, SEASON_BEGIN, SEASON_COMMIT, SECTION_PREV, SECTION_NEXT,
        EXPORT_SECTION, RETRY_LIST, RETRY_DETAIL
    }

    record AuditRow(long seq, long at, String actor, String action, String target, String outcome) {}

    record RowView(long seq, String line1, String line2, String result, boolean denied) {}

    record DetailLine(String label, String value, List<FormattedCharSequence> wrapped, int color) {
        int height(final Font font) {
            return this.wrapped.isEmpty() ? GovMetrics.LINE_BODY
                    : Math.max(GovMetrics.LINE_BODY, this.wrapped.size() * font.lineHeight);
        }
    }

    record Hit(GovRect rect, Action action, int index, String value) {
        boolean contains(final double mouseX, final double mouseY) {
            return rect.contains(mouseX, mouseY);
        }
    }

    final Screen host;

    Font font;
    EditBox actorBox;
    EditBox actionBox;
    EditBox targetBox;
    EditBox tokenBox;
    List<EditBox> widgets = List.of();
    boolean active;

    final Map<String, RequestKind> pending = new LinkedHashMap<>();
    final List<AuditRow> rows = new ArrayList<>();
    final List<RowView> rowViews = new ArrayList<>();
    final List<DetailLine> detailLines = new ArrayList<>();
    final List<Hit> hits = new ArrayList<>();
    final Map<String, String> detail = new LinkedHashMap<>();
    final Map<String, String> admin = new LinkedHashMap<>();
    final Map<String, String> historyFields = new LinkedHashMap<>();

    Mode mode = Mode.AUDIT;
    LoadState listState = LoadState.NONE;
    LoadState detailState = LoadState.NONE;
    LoadState adminState = LoadState.NONE;
    String listError = "";
    String detailError = "";
    String adminError = "";
    String status = "";
    boolean statusIsError;
    String latestListReqId = "";
    String latestDetailReqId = "";
    String latestAdminReqId = "";
    final GovKeyRepeat enterRepeat = new GovKeyRepeat();

    int count;
    int matching;
    int page;
    int pages = 1;
    long oldestAt;
    long newestAt;
    int retention = 50_000;
    String window = AuditKeys.DEFAULT_WINDOW;
    String outcome = AuditKeys.OUTCOME_ALL;
    boolean canExport;
    boolean canAdmin;
    long selectedSeq = -1L;
    int exportSectionIndex;

    boolean commitArmed;
    long commitArmedAt;

    ExportArm exportArm = ExportArm.NONE;
    long exportArmedAt;

    int listScroll;
    int listScrollMax;
    int detailScroll;
    int detailScrollMax;

    GovRect bounds = EMPTY;
    GovRect barRect = EMPTY;
    GovRect filterLabelRow = EMPTY;
    GovRect filterRow1 = EMPTY;
    GovRect actorRect = EMPTY;
    GovRect actionRect = EMPTY;
    GovRect targetRect = EMPTY;
    GovRect applyRect = EMPTY;
    GovRect filterRow2 = EMPTY;
    GovRect outcomeRect = EMPTY;
    GovRect windowRect = EMPTY;
    GovRect exportAuditRect = EMPTY;
    GovRect countRect = EMPTY;
    GovRect listRect = EMPTY;
    GovRect listInnerRect = EMPTY;
    GovRect listRetryRect = EMPTY;
    GovRect detailRect = EMPTY;
    GovRect detailInnerRect = EMPTY;
    GovRect detailSummaryRect = EMPTY;
    GovRect detailRetryRect = EMPTY;
    GovRect prevRect = EMPTY;
    GovRect nextRect = EMPTY;
    GovRect pageRect = EMPTY;
    GovRect refreshRect = EMPTY;

    GovRect adminHeaderRect = EMPTY;
    GovRect adminBeginRect = EMPTY;
    GovRect adminCommitRect = EMPTY;
    GovRect adminRefreshRect = EMPTY;
    GovRect tokenRect = EMPTY;
    GovRect sectionLabelRect = EMPTY;
    GovRect sectionPrevRect = EMPTY;
    GovRect sectionNextRect = EMPTY;
    GovRect sectionExportRect = EMPTY;
    GovRect adminInfoRect = EMPTY;
    GovRect adminBodyRect = EMPTY;
    GovRect adminStateRect = EMPTY;

    boolean shortDetail;
    int resultBadgeW;
    int detailLabelW;
    int detailValueW;

    String applyText;
    String exportText;
    String exportSectionText;
    String prevText;
    String nextText;
    String refreshText;
    String beginText;
    String commitText;
    String confirmCommitText;
    String confirmExportText;
    String retryText;
    String actorLabelText;
    String actionLabelText;
    String targetLabelText;
    String commitContextPrefix;
    String arrowLeftText;
    String arrowRightText;
    String loadingText;
    String errorText;
    String emptyText;
    String detailErrorText;
    String noSelectionText;
    String adminLockedLongText;
    String tokenRequiredText;
    String noneText;
    String resultOkText;
    String resultDeniedText;
    String exportLockedText;
    String adminHeaderText;
    Component adminHeaderComponent = Component.empty();
    String adminWarningText;
    String[] detailLabels = new String[0];

    String outcomeLabelText = "";
    String windowLabelText = "";
    String sectionText = "";
    String pageText = "";
    String headerText = "";
    String detailHeadText = "";
    Component detailHeadComponent = Component.empty();
    String detailSummaryText = "";

    List<FormattedCharSequence> adminWarningLines = List.of();
    List<FormattedCharSequence> adminLockedLines = List.of();
    final List<String> adminInfoLines = new ArrayList<>();

    GovAuditPanel(final Screen host) {
        this.host = host;
        GovAuditLabels.rebuildLabels(this);
    }

    List<EditBox> createWidgets(final Font font) {
        return GovAuditWidgets.createWidgets(this, font);
    }

    void setActive(final boolean active) {
        GovAuditLifecycle.setActive(this, active);
    }

    void reset() {
        GovAuditLifecycle.reset(this);
    }

    void layout(final int left, final int top, final int right, final int bottom, final Font font) {
        GovAuditGeometry.layout(this, left, top, right, bottom, font);
    }

    void setSubtab(final int index) {
        GovAuditLifecycle.setSubtab(this, index);
    }

    void onActionResult(final GovActionResultPayload payload) {
        GovAuditRequests.onActionResult(this, payload);
    }

    void render(final GuiGraphics graphics, final int mouseX, final int mouseY) {
        GovAuditRender.render(this, graphics, mouseX, mouseY);
    }

    boolean mouseClicked(final double mouseX, final double mouseY, final int button) {
        return GovAuditInput.mouseClicked(this, mouseX, mouseY, button);
    }

    boolean mouseScrolled(final double mouseX, final double mouseY, final double scrollY) {
        return GovAuditInput.mouseScrolled(this, mouseX, mouseY, scrollY);
    }

    boolean keyPressed(final int keyCode, final int scanCode, final int modifiers) {
        return GovAuditInput.keyPressed(this, keyCode, scanCode, modifiers);
    }

    boolean charTyped(final char codePoint, final int modifiers) {
        return false;
    }

    // static helpers used widely
    static String tr(final String key, final Object... args) {
        return Component.translatable(key, args).getString();
    }

    static String upper(final String text) {
        return text == null ? "" : text.toUpperCase(Locale.ROOT);
    }
}