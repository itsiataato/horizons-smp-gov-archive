package com.horizonssmp.gov.client;

import com.horizonssmp.gov.client.GovFinesTypes.*;
import static com.horizonssmp.gov.client.GovFinesTypes.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.horizonssmp.gov.common.FineKeys;
import com.horizonssmp.gov.common.GovFields;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

final class GovFinesViews {

    // ------------------------------------------------------------------- caches

    static void rebuildAll(final GovFinesPanel p) {
        if (p.font == null || p.area.w() <= 0 || p.area.h() <= 0) {
            return;
        }
        p.titleView = switch (p.subtab) {
            case TAB_ISSUE -> GovFinesLabels.label(p, "gui.gov.fines.issue_title");
            case TAB_COLLECT -> GovFinesLabels.label(p, "gui.gov.fines.collect_title");
            case TAB_WAIVE -> GovFinesLabels.label(p, "gui.gov.fines.waive_title_short");
            default -> GovFinesLabels.label(p, "gui.gov.fines.register_title");
        };
        rebuildRows(p);
        rebuildDetail(p);
        rebuildFormInfo(p);
        rebuildStatus(p);
        GovFinesViewButtons.rebuildButtons(p);
        GovFinesViewButtons.rebuildHits(p);
    }

    static void rebuildRows(final GovFinesPanel p) {
        p.rowViews.clear();
        if (p.listRect.w() <= 0) {
            p.listScrollMax = 0;
            p.listScroll = 0;
            return;
        }
        final long now = System.currentTimeMillis();
        for (int i = 0; i < p.rows.size(); i++) {
            final FineRow row = p.rows.get(i);
            final String state = GovFinesLabels.stateText(p, row.state());
            p.rowViews.add(new FineRowView(
                    row.id(),
                    GovText.clip(p.font, GovFinesHelpers.dash(row.target()), p.columnW[0]),
                    GovText.clip(p.font, GovFinesHelpers.dash(row.article()), p.columnW[1]),
                    GovText.clip(p.font,
                            GovFinesLabels.up(GovFinesLabels.trArgs("gui.gov.fines.gold_short", row.amount())),
                            p.columnW[2]),
                    GovText.clip(p.font, state, Math.max(0, p.columnW[3] - 2)),
                    GovText.clip(p.font, GovFinesHelpers.age(row.createdAt(), now), p.columnW[4]),
                    GovText.boldCached(state)));
        }
        p.listScrollMax = Math.max(0,
                p.rowViews.size() * p.rowH - Math.max(0, p.listRect.h() - 2));
        p.listScroll = GovMetrics.clamp(p.listScroll, 0, p.listScrollMax);

        p.listErrorView = GovText.clip(p.font, p.listError,
                Math.max(0, p.listRect.w() - 16));
    }

    static void rebuildDetail(final GovFinesPanel p) {
        p.detailLines.clear();
        p.detailScrollMax = 0;
        p.detailTitle = "";
        p.detailTitleBold = null;
        p.detailStateView = "";
        p.detailStateX = 0;
        p.detailTitleW = 0;
        p.summaryView = "";
        p.detailErrorView = GovText.clip(p.font, p.detailError,
                Math.max(0, (p.detailRect.w() > 0 ? p.detailRect.w()
                        : p.summaryRect.w()) - 16));

        if (p.selectedId.isEmpty() || p.detailState == LoadState.NONE) {
            p.summaryView = GovText.clip(p.font, GovFinesLabels.label(p, "gui.gov.fines.select_prompt"),
                    Math.max(0, p.summaryRect.w()));
            return;
        }
        if (p.detailState != LoadState.READY) {
            return;
        }
        final String id = p.detail.getOrDefault(FineKeys.ID, p.selectedId);
        final String state = GovFinesLabels.stateText(p, p.detail.getOrDefault(FineKeys.STATE, ""));
        final String target = GovFinesHelpers.dash(p.detail.getOrDefault(FineKeys.TARGET, ""));
        final String due = GovFinesHelpers.dash(p.detail.getOrDefault(FineKeys.DUE, "0"));
        p.detailTitle = GovFinesLabels.up(GovFinesLabels.trArgs("gui.gov.fines.record_prefix", id));
        p.detailTitleBold = GovText.boldCached(p.detailTitle);
        p.detailStateView = GovText.clip(p.font, state,
                Math.max(0, p.detailRect.w() / 3));
        final int titleX = p.detailRect.x() + 6;
        p.detailStateX = Math.max(titleX, p.detailRect.right() - 6
                - p.font.width(p.detailStateView));
        p.detailTitleW = Math.max(0,
                p.detailStateX - GovMetrics.GAP_XS - titleX);
        p.summaryView = GovText.clip(p.font,
                p.detailTitle + "  " + target + "  "
                        + GovFinesLabels.up(GovFinesLabels.trArgs("gui.gov.fines.gold_short", due)) + "  " + state,
                Math.max(0, p.summaryRect.w()));

        final int labelW = p.detailRect.w() > 0
                ? GovMetrics.span(p.detailRect.w(), p.detailRect.w() / 3, 40, 80) : 60;
        final int valueW = Math.max(0,
                (p.detailRect.w() > 0 ? p.detailRect.w() : p.summaryRect.w())
                        - labelW - 14);
        addDetail(p, "gui.gov.fines.field.target", target, GovTheme.BODY);
        addDetail(p, "gui.gov.fines.field.article", GovFinesHelpers.articleText(p), GovTheme.BODY);
        addDetail(p, "gui.gov.fines.field.amount",
                GovFinesHelpers.gold(p.detail.getOrDefault(FineKeys.AMOUNT, "0")), GovTheme.BODY);
        addDetail(p, "gui.gov.fines.field.restitution", GovFinesHelpers.restitutionText(p), GovTheme.BODY);
        addDetail(p, "gui.gov.fines.field.total", GovFinesHelpers.gold(due), GovTheme.TEXT);
        addDetail(p, "gui.gov.fines.field.state", state, GovTheme.TEXT);
        addDetail(p, "gui.gov.fines.field.issued", GovFinesHelpers.date(GovFinesResponses.longField(p.detail, FineKeys.CREATED)),
                GovTheme.MUTED);
        addDetail(p, "gui.gov.fines.field.victim",
                GovFinesHelpers.dash(p.detail.getOrDefault(FineKeys.VICTIM, "")), GovTheme.BODY);
        final int exile = GovFinesResponses.intField(p.detail, FineKeys.EXILE_DAYS);
        addDetail(p, "gui.gov.fines.field.exile",
                exile > 0 ? GovFinesLabels.up(GovFinesLabels.trArgs("gui.gov.fines.days", exile))
                        : GovFinesLabels.label(p, "gui.gov.fines.none"),
                exile > 0 ? GovTheme.DANGER : GovTheme.MUTED);
        addWrappedDetail(p, "gui.gov.fines.field.reason",
                p.detail.getOrDefault(FineKeys.REASON, ""), valueW);
        final String paid = p.detail.getOrDefault(FineKeys.RECEIPT, "");
        if (!paid.isBlank()) {
            addDetail(p, "gui.gov.fines.field.receipt", paid, GovTheme.MUTED);
            addDetail(p, "gui.gov.fines.field.paid_at",
                    GovFinesHelpers.date(GovFinesResponses.longField(p.detail, FineKeys.PAID_AT)), GovTheme.MUTED);
            addDetail(p, "gui.gov.fines.field.collector", GovFinesHelpers.collectorText(p), GovTheme.BODY);
        }
        final String waivedBy = p.detail.getOrDefault(FineKeys.WAIVED_BY, "");
        if (!waivedBy.isBlank()) {
            addDetail(p, "gui.gov.fines.field.waived_by", waivedBy, GovTheme.MUTED);
            addWrappedDetail(p, "gui.gov.fines.field.waive_reason",
                    p.detail.getOrDefault(FineKeys.WAIVED_REASON, ""), valueW);
        }
        if (p.detailRect.h() > 0) {
            final int bodyH = Math.max(0, p.detailRect.h() - 20);
            p.detailScrollMax = Math.max(0,
                    p.detailLines.size() * DETAIL_LINE + 8 - bodyH);
        }
        p.detailScroll = GovMetrics.clamp(p.detailScroll, 0, p.detailScrollMax);
    }

    static void addDetail(final GovFinesPanel p, final String labelKey, final String value, final int color) {
        final int labelW = p.detailRect.w() > 0
                ? GovMetrics.span(p.detailRect.w(), p.detailRect.w() / 3, 40, 80) : 60;
        final int valueW = Math.max(0,
                (p.detailRect.w() > 0 ? p.detailRect.w() : p.summaryRect.w())
                        - labelW - 14);
        p.detailLines.add(new DetailLine(
                GovText.clip(p.font, GovFinesLabels.label(p, labelKey), labelW),
                GovText.clip(p.font, value == null || value.isBlank() ? "-" : value, valueW),
                null, color));
    }

    static void addWrappedDetail(final GovFinesPanel p, final String labelKey, final String value, final int valueW) {
        final int labelW = p.detailRect.w() > 0
                ? GovMetrics.span(p.detailRect.w(), p.detailRect.w() / 3, 40, 80) : 60;
        p.detailLines.add(new DetailLine(
                GovText.clip(p.font, GovFinesLabels.label(p, labelKey), labelW), "", null, GovTheme.MUTED));
        final List<FormattedCharSequence> wrapped = GovText.wrapCached(p.font,
                value == null || value.isBlank() ? "-" : value, valueW);
        for (int i = 0; i < wrapped.size(); i++) {
            p.detailLines.add(new DetailLine("", "", wrapped.get(i), GovTheme.BODY));
        }
    }

    static void rebuildFormInfo(final GovFinesPanel p) {
        p.issueAmountView = GovText.clip(p.font,
                GovFinesLabels.bandText(p, p.issueBand) + "  "
                        + GovFinesHelpers.gold(Long.toString(GovFinesHelpers.bandGold(p.issueBand))),
                p.issueAmountRect.w());
        p.issueCitizenView = GovText.clip(p.font,
                p.online.isEmpty() ? GovFinesLabels.label(p, "gui.gov.fines.no_online")
                        : p.issueTarget.isBlank() ? GovFinesLabels.label(p, "gui.gov.fines.no_target")
                                : p.issueTarget,
                p.citizenValueRect.w());
        p.issueHintView = GovText.clip(p.font, GovFinesLabels.label(p, "gui.gov.fines.issue_hint"),
                Math.max(0, p.issueHintRect.w()));
        p.collectRuleView = GovText.clip(p.font,
                p.receptionConfigured ? GovFinesLabels.label(p, "gui.gov.fines.collect_rule")
                        : GovFinesLabels.label(p, "gui.gov.fines.reception_missing"),
                Math.max(0, p.collectFormRect.w() - 8));
        p.waiveRuleView = GovText.clip(p.font, GovFinesLabels.label(p, "gui.gov.fines.waive_warning"),
                Math.max(0, p.waiveRuleRect.w()));

        if (p.subtab == TAB_COLLECT) {
            if (p.selectedId.isEmpty() || p.detailState != LoadState.READY) {
                p.collectTargetView = GovText.clip(p.font,
                        GovFinesLabels.label(p, "gui.gov.fines.select_prompt"), p.collectTargetRect.w());
                p.collectAmountView = GovText.clip(p.font, GovFinesLabels.label(p, "gui.gov.fines.none"),
                        p.collectAmountRect.w());
            } else {
                final String target = GovFinesHelpers.dash(p.detail.getOrDefault(FineKeys.TARGET, ""));
                final String article = GovFinesHelpers.dash(p.detail.getOrDefault(FineKeys.ARTICLE, ""));
                final String state = GovFinesLabels.stateText(p, p.detail.getOrDefault(FineKeys.STATE, ""));
                final String due = GovFinesHelpers.dash(p.detail.getOrDefault(FineKeys.DUE, "0"));
                p.collectTargetView = GovText.clip(p.font,
                        target + "  " + article + "  " + state, p.collectTargetRect.w());
                p.collectAmountView = GovText.clip(p.font, GovFinesHelpers.gold(due),
                        p.collectAmountRect.w());
            }
        }
        if (p.subtab == TAB_WAIVE) {
            if (p.selectedId.isEmpty() || p.detailState != LoadState.READY) {
                p.waiveTargetView = GovText.clip(p.font,
                        GovFinesLabels.label(p, "gui.gov.fines.select_prompt"), p.waiveSummaryRect.w());
            } else {
                final String target = GovFinesHelpers.dash(p.detail.getOrDefault(FineKeys.TARGET, ""));
                final String due = GovFinesHelpers.dash(p.detail.getOrDefault(FineKeys.DUE, "0"));
                p.waiveTargetView = GovText.clip(p.font,
                        target + "  " + GovFinesLabels.up(GovFinesLabels.trArgs("gui.gov.fines.gold_short", due)),
                        p.waiveSummaryRect.w());
            }
        }
    }

    static void rebuildStatus(final GovFinesPanel p) {
        final int width = statusWidth(p);
        p.statusView = GovText.clip(p.font, p.status, width);
        p.pageView = GovText.clip(p.font,
                Component.translatable("gui.gov.fines.page", p.page + 1, p.pages).getString(),
                p.pageTextRect.w());
    }

    static int statusWidth(final GovFinesPanel p) {
        return switch (p.subtab) {
            case TAB_ISSUE -> Math.max(0, p.issueAmountRect.w());
            case TAB_COLLECT -> Math.max(0, p.collectStatusRect.w());
            case TAB_WAIVE -> Math.max(0, p.waiveStatusRect.w());
            default -> p.detailRect.w() > 0
                    ? Math.max(0, p.pageTextRect.w())
                    : Math.max(0, p.summaryRect.w());
        };
    }


}
