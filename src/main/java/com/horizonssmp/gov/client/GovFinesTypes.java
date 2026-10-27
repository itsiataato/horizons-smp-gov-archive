package com.horizonssmp.gov.client;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import com.horizonssmp.gov.common.FineKeys;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

final class GovFinesTypes {

    static final int TAB_REGISTER = 0;
    static final int TAB_ISSUE = 1;
    static final int TAB_COLLECT = 2;
    static final int TAB_WAIVE = 3;

    static final int ROW_TEXT = 9;
    static final int DETAIL_LINE = 10;
    static final int RETRY_W = 52;
    static final int RETRY_H = 16;
    static final long ARM_FLOOR_MS = 600L;
    static final long ARM_WINDOW_MS = 5000L;

    static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);

    static final String[] FILTERS = {
        FineKeys.FILTER_ALL, FineKeys.FILTER_UNPAID, FineKeys.FILTER_PAID, FineKeys.FILTER_WAIVED
    };
    static final String[] BANDS = {"PETTY", "MISDEMEANOR", "FELONY", "AGGRAVATED"};
    static final String[] STATES = {"UNPAID", "PAID", "WAIVED", "VOID"};

    static final String[] LABEL_KEYS = {
        "gui.gov.previous", "gui.gov.next", "gui.gov.refresh", "gui.gov.card.retry",
        "gui.gov.fines.number_invalid",
        "gui.gov.fines.register_title", "gui.gov.fines.issue_title",
        "gui.gov.fines.collect_title", "gui.gov.fines.waive_title_short",
        "gui.gov.fines.filter.all", "gui.gov.fines.filter.unpaid",
        "gui.gov.fines.filter.paid", "gui.gov.fines.filter.waived",
        "gui.gov.fines.issue_action", "gui.gov.fines.issue_hint",
        "gui.gov.fines.collect", "gui.gov.fines.pay",
        "gui.gov.fines.waive", "gui.gov.fines.waive_warning",
        "gui.gov.fines.waive_confirm",
        "gui.gov.fines.field.citizen",
        "gui.gov.fines.field.target", "gui.gov.fines.field.band",
        "gui.gov.fines.field.amount", "gui.gov.fines.field.restitution",
        "gui.gov.fines.field.total", "gui.gov.fines.field.article",
        "gui.gov.fines.field.issued", "gui.gov.fines.field.state",
        "gui.gov.fines.field.reason", "gui.gov.fines.field.receipt",
        "gui.gov.fines.field.paid_at", "gui.gov.fines.field.collector",
        "gui.gov.fines.field.waived_by", "gui.gov.fines.field.waive_reason",
        "gui.gov.fines.field.exile", "gui.gov.fines.field.victim",
        "gui.gov.fines.loading", "gui.gov.fines.error", "gui.gov.fines.detail_error",
        "gui.gov.fines.no_selection", "gui.gov.fines.no_target", "gui.gov.fines.no_online",
        "gui.gov.fines.reason_required", "gui.gov.fines.reception_missing",
        "gui.gov.fines.no_unpaid", "gui.gov.fines.amount_due",
        "gui.gov.fines.recorded",
        "gui.gov.fines.count", "gui.gov.fines.showing", "gui.gov.fines.page",
        "gui.gov.fines.article_hint", "gui.gov.fines.restitution_hint",
        "gui.gov.fines.victim_hint", "gui.gov.fines.exile_hint",
        "gui.gov.fines.reason_hint", "gui.gov.fines.collect_rule",
        "gui.gov.fines.empty.all", "gui.gov.fines.empty.unpaid",
        "gui.gov.fines.empty.paid", "gui.gov.fines.empty.waived",
        "gui.gov.fines.record_prefix", "gui.gov.fines.column.gold",
        "gui.gov.fines.column.age", "gui.gov.fines.none",
        "gui.gov.fines.days", "gui.gov.fines.unrecorded",
        "gui.gov.fines.self", "gui.gov.fines.age.today",
        "gui.gov.fines.age.days", "gui.gov.fines.gold_ingots",
        "gui.gov.fines.restitution_line", "gui.gov.fines.select_prompt",
        "gui.gov.fines.gold_short"
    };

    static final GovRect NONE = new GovRect(0, 0, 0, 0);

    enum RequestKind {
        LIST, DETAIL, MUTATE
    }

    enum LoadState {
        NONE, LOADING, READY, ERROR
    }

    enum Click {
        FILTER, ROW, PAGE, REFRESH, RETRY_LIST, RETRY_DETAIL,
        ISSUE_SUBMIT, BAND, ONLINE_PREV, ONLINE_NEXT,
        COLLECT, PAY, WAIVE_SUBMIT, WAIVE_PREV, WAIVE_NEXT
    }

    record FineRow(
            String id, String target, String targetUuid, String band,
            long amount, long restitution, long due, String article,
            String state, String reason, long createdAt, int exileDays,
            String victim, String receipt) {
    }

    record FineRowView(
            String id, String citizen, String article, String amount, String state, String age,
            Component stateBold) {
    }

    record Hit(GovRect rect, Click click, String value) {

        boolean contains(final double mouseX, final double mouseY) {
            return this.rect.contains(mouseX, mouseY);
        }
    }

    record Button(
            GovRect rect, String label, String confirm, Click click, String value,
            boolean enabled, boolean primary) {
    }

    record FieldRow(
            int tab, GovRect labelRect, GovRect fieldRect, EditBox box, String label) {
    }

    record DetailLine(
            String label, String value, FormattedCharSequence wrapped, int color) {
    }
}