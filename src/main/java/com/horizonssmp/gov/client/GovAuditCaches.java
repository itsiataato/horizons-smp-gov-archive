package com.horizonssmp.gov.client;

import java.util.List;

import com.horizonssmp.gov.common.AuditKeys;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

final class GovAuditCaches {
    static void rebuildAllCaches(final GovAuditPanel p) {
        if (p.font == null || p.bounds.w() <= 0 || p.bounds.h() <= 0) {
            return;
        }
        rebuildRowViews(p);
        rebuildDetailLines(p);
        rebuildAdminInfo(p);
        rebuildStatusTexts(p);
        rebuildHits(p);
    }

    static void rebuildRowViews(final GovAuditPanel p) {
        p.rowViews.clear();
        if (p.font == null || p.listRect.w() <= 0) {
            return;
        }
        p.resultBadgeW = Math.max(p.font.width(p.resultOkText),
                p.font.width(p.resultDeniedText)) + 8;
        final int textW = Math.max(1,
                p.listRect.w() - p.resultBadgeW - 20);
        for (final GovAuditPanel.AuditRow row : p.rows) {
            final boolean denied = GovAuditUtil.isDenied(row.outcome());
            final String result = GovAuditUtil.isOk(row.outcome()) ? p.resultOkText
                    : denied ? p.resultDeniedText : "-";
            final String line1 = "#" + row.seq() + "  " + GovAuditUtil.shortTime(row.at());
            final String line2 = row.actor() + "  " + row.action()
                    + (row.target().isBlank() ? "" : "  " + row.target());
            p.rowViews.add(new GovAuditPanel.RowView(row.seq(),
                    GovText.clip(p.font, line1, textW),
                    GovText.clip(p.font, line2, textW),
                    result, denied));
        }
        final int visibleRows = Math.max(1, p.listRect.h() / GovMetrics.ROW_H);
        p.listScrollMax = Math.max(0,
                (p.rowViews.size() - visibleRows) * GovMetrics.ROW_H);
        p.listScroll = GovMetrics.clamp(p.listScroll, 0, p.listScrollMax);
        p.listScroll = (p.listScroll / GovMetrics.ROW_H) * GovMetrics.ROW_H;
    }

    static void rebuildDetailLines(final GovAuditPanel p) {
        p.detailLines.clear();
        p.detailScrollMax = 0;
        if (p.font == null) {
            return;
        }
        if (p.selectedSeq <= 0L || p.detail.isEmpty()) {
            p.detailHeadText = "";
            p.detailHeadComponent = Component.empty();
            rebuildDetailSummary(p);
            return;
        }
        if (p.shortDetail) {
            p.detailHeadText = "";
            p.detailHeadComponent = Component.empty();
            rebuildDetailSummary(p);
            return;
        }
        p.detailLabelW = Math.min(80,
                Math.max(0, p.detailInnerRect.w() / 5));
        p.detailValueW = Math.max(1,
                p.detailInnerRect.w() - p.detailLabelW - GovMetrics.GAP_XS);
        final String result = GovAuditUtil.resultText(p, p.detail.get(AuditKeys.DETAIL_OUTCOME));
        p.detailHeadText = "#" + p.detail.getOrDefault(AuditKeys.SEQ, "?")
                + "  " + p.detail.getOrDefault(AuditKeys.ACTION, "?")
                + "  " + result;
        p.detailHeadComponent = GovText.boldCached(p.detailHeadText);
        addDetailLine(p, 0, GovAuditUtil.dash(p.detail.get(AuditKeys.AT_ISO)), false);
        addDetailLine(p, 1, actorValue(p), false);
        addDetailLine(p, 2, GovAuditUtil.dash(p.detail.get(AuditKeys.TARGET)), false);
        addDetailLine(p, 3, result, false);
        addDetailLine(p, 4, GovAuditUtil.dash(p.detail.get(AuditKeys.PAYLOAD)), true);
        addDetailLine(p, 5, GovAuditUtil.dash(p.detail.get(AuditKeys.REASON)), true);
        int height = 0;
        for (final GovAuditPanel.DetailLine line : p.detailLines) {
            height += line.height(p.font);
        }
        p.detailScrollMax = Math.max(0, height - p.detailInnerRect.h());
        p.detailScroll = GovMetrics.clamp(p.detailScroll, 0, p.detailScrollMax);
        rebuildDetailSummary(p);
    }

    static void addDetailLine(final GovAuditPanel p, final int index, final String value, final boolean wrap) {
        final String safe = value == null || value.isBlank() ? "-" : value;
        final int color = index == 3 ? GovAuditUtil.resultColor(p.detail.get(AuditKeys.DETAIL_OUTCOME))
                : GovTheme.BODY;
        if (wrap) {
            final List<FormattedCharSequence> lines = List.copyOf(
                    p.font.split(net.minecraft.network.chat.Component.literal(safe), p.detailValueW));
            p.detailLines.add(new GovAuditPanel.DetailLine(p.detailLabels[index], "", lines, color));
        } else {
            p.detailLines.add(new GovAuditPanel.DetailLine(p.detailLabels[index],
                    GovText.clip(p.font, safe, Math.max(1, p.detailValueW)),
                    List.of(), color));
        }
    }

    static String actorValue(final GovAuditPanel p) {
        final String actor = GovAuditUtil.dash(p.detail.get(AuditKeys.ACTOR));
        final String job = p.detail.get(AuditKeys.ACTOR_JOB);
        return job == null || job.isBlank() ? actor : actor + " (" + job + ")";
    }

    static void rebuildDetailSummary(final GovAuditPanel p) {
        if (p.detailSummaryRect.w() <= 0 || p.font == null) {
            p.detailSummaryText = "";
            return;
        }
        String raw = "";
        if (p.selectedSeq > 0L && p.detailState == GovAuditPanel.LoadState.ERROR) {
            raw = p.detailError.isBlank() ? p.detailErrorText
                    : p.detailErrorText + "  " + p.detailError;
        } else if (p.selectedSeq > 0L && !p.detail.isEmpty()) {
            raw = "#" + p.detail.getOrDefault(AuditKeys.SEQ, "?")
                    + "  " + GovAuditUtil.shortTime(GovAuditUtil.longField(p.detail, AuditKeys.AT))
                    + "  " + GovAuditUtil.dash(p.detail.get(AuditKeys.ACTOR))
                    + "  " + GovAuditUtil.dash(p.detail.get(AuditKeys.ACTION))
                    + "  " + GovAuditUtil.dash(p.detail.get(AuditKeys.TARGET))
                    + "  " + GovAuditUtil.resultText(p, p.detail.get(AuditKeys.DETAIL_OUTCOME));
        } else if (p.selectedSeq > 0L) {
            for (final GovAuditPanel.RowView view : p.rowViews) {
                if (view.seq() == p.selectedSeq) {
                    raw = "#" + view.seq() + "  " + view.line2() + "  " + view.result();
                    break;
                }
            }
        }
        p.detailSummaryText = GovText.clip(p.font, raw, p.detailSummaryRect.w());
    }

    static void rebuildAdminInfo(final GovAuditPanel p) {
        p.adminInfoLines.clear();
        p.adminWarningLines = List.of();
        p.adminLockedLines = List.of();
        if (p.font == null || p.adminInfoRect.w() <= 0) {
            return;
        }
        p.adminWarningLines = List.copyOf(
                p.font.split(net.minecraft.network.chat.Component.literal(p.adminWarningText),
                        p.adminInfoRect.w()));
        p.adminLockedLines = List.copyOf(
                p.font.split(net.minecraft.network.chat.Component.literal(p.adminLockedLongText),
                        p.adminInfoRect.w()));
        if (p.admin.isEmpty()) {
            return;
        }
        p.adminInfoLines.add(tr("gui.gov.audit.admin.sections",
                GovAuditUtil.value(p.admin, AuditKeys.SEASON_SECTIONS)));
        p.adminInfoLines.add(tr("gui.gov.audit.admin.counts",
                GovAuditUtil.value(p.admin, AuditKeys.SEASON_COUNTS_TEXT)));
        p.adminInfoLines.add(tr("gui.gov.audit.admin.keep",
                GovAuditUtil.value(p.admin, AuditKeys.SEASON_KEEP)));
        p.adminInfoLines.add(tr("gui.gov.audit.admin.token_policy",
                GovAuditUtil.value(p.admin, AuditKeys.SEASON_TOKEN_SECONDS), pendingText(p)));
        p.adminInfoLines.add(tr("gui.gov.audit.admin.dirs",
                GovAuditUtil.value(p.admin, AuditKeys.SEASON_EXPORT_DIR),
                GovAuditUtil.value(p.admin, AuditKeys.SEASON_BACKUP_DIR)));
    }

    static String pendingText(final GovAuditPanel p) {
        final long expiry = GovAuditUtil.longField(p.admin, AuditKeys.SEASON_EXPIRES_AT);
        return expiry <= 0L ? p.noneText : GovAuditUtil.shortTime(expiry);
    }

    static void rebuildStatusTexts(final GovAuditPanel p) {
        if (p.font == null) {
            return;
        }
        p.pageText = p.bounds.w() < 340
                ? (p.page + 1) + "/" + p.pages
                : GovAuditPanel.upper(tr("gui.gov.page", p.page + 1, p.pages));
        p.headerText = GovAuditPanel.upper(tr("gui.gov.audit.header", p.count, p.retention,
                p.matching, GovAuditUtil.shortTime(p.oldestAt), GovAuditUtil.shortTime(p.newestAt)));
    }

    static void rebuildHits(final GovAuditPanel p) {
        p.hits.clear();
        if (!p.active || p.bounds.w() <= 0 || p.bounds.h() <= 0) {
            return;
        }
        if (p.mode == GovAuditPanel.Mode.AUDIT) {
            addHit(p, p.applyRect, GovAuditPanel.Action.APPLY, -1, "");
            addHit(p, p.outcomeRect, GovAuditPanel.Action.OUTCOME, -1, "");
            addHit(p, p.windowRect, GovAuditPanel.Action.WINDOW, -1, "");
            if (p.canExport && !mutationPending(p)) {
                addHit(p, p.exportAuditRect, GovAuditPanel.Action.EXPORT_AUDIT, -1, "");
            }
            if (p.listState == GovAuditPanel.LoadState.READY) {
                final int first = p.listScroll / GovMetrics.ROW_H;
                final int last = Math.min(p.rowViews.size(),
                        first + p.listRect.h() / GovMetrics.ROW_H);
                int y = p.listRect.y() + 1 - p.listScroll;
                for (int index = 0; index < p.rowViews.size(); index++) {
                    if (index >= first && index < last
                            && y >= p.listInnerRect.y()
                            && y + GovMetrics.ROW_H <= p.listInnerRect.bottom()) {
                        addHit(p, rect(p, p.listRect.x() + 2, y,
                                Math.max(0, p.listRect.w() - 6),
                                GovMetrics.ROW_H - 2).clamp(p.listInnerRect),
                                GovAuditPanel.Action.ROW, index, "");
                    }
                    y += GovMetrics.ROW_H;
                }
            }
            if (p.page > 0) {
                addHit(p, p.prevRect, GovAuditPanel.Action.PREV, -1, "");
            }
            if (p.page + 1 < p.pages) {
                addHit(p, p.nextRect, GovAuditPanel.Action.NEXT, -1, "");
            }
            addHit(p, p.refreshRect, GovAuditPanel.Action.REFRESH, -1, "");
            if (p.listState == GovAuditPanel.LoadState.ERROR) {
                addHit(p, p.listRetryRect, GovAuditPanel.Action.RETRY_LIST, -1, "");
            }
            if (!p.shortDetail && p.detailState == GovAuditPanel.LoadState.ERROR) {
                addHit(p, p.detailRetryRect, GovAuditPanel.Action.RETRY_DETAIL, -1, "");
            }
        } else if (p.canAdmin) {
            final boolean controls = adminControlsVisible(p);
            if (controls && !mutationPending(p)) {
                addHit(p, p.adminBeginRect, GovAuditPanel.Action.SEASON_BEGIN, -1, "");
                addHit(p, p.adminCommitRect, GovAuditPanel.Action.SEASON_COMMIT, -1, "");
                addHit(p, p.sectionPrevRect, GovAuditPanel.Action.SECTION_PREV, -1, "");
                addHit(p, p.sectionNextRect, GovAuditPanel.Action.SECTION_NEXT, -1, "");
                addHit(p, p.sectionExportRect, GovAuditPanel.Action.EXPORT_SECTION, -1, "");
            }
            if (controls || p.adminState == GovAuditPanel.LoadState.LOADING
                    || p.adminState == GovAuditPanel.LoadState.ERROR) {
                addHit(p, p.adminRefreshRect, GovAuditPanel.Action.SEASON_REFRESH, -1, "");
            }
        }
    }

    static boolean adminControlsVisible(final GovAuditPanel p) {
        return p.canAdmin
                && !(p.adminState == GovAuditPanel.LoadState.LOADING && p.admin.isEmpty())
                && !(p.adminState == GovAuditPanel.LoadState.ERROR && p.admin.isEmpty());
    }

    static void addHit(final GovAuditPanel p, final GovRect rect, final GovAuditPanel.Action action, final int index,
            final String value) {
        if (rect == null || rect.w() <= 0 || rect.h() <= 0) {
            return;
        }
        p.hits.add(new GovAuditPanel.Hit(rect, action, index, value));
    }

    static boolean mutationPending(final GovAuditPanel p) {
        return p.pending.containsValue(GovAuditPanel.RequestKind.EXPORT)
                || p.pending.containsValue(GovAuditPanel.RequestKind.ADMIN_EXPORT)
                || p.pending.containsValue(GovAuditPanel.RequestKind.BEGIN)
                || p.pending.containsValue(GovAuditPanel.RequestKind.COMMIT);
    }

    static String tr(final String key, final Object... args) {
        return GovAuditPanel.tr(key, args);
    }

    static GovRect rect(final GovAuditPanel p, final int x, final int y, final int w, final int h) {
        return GovAuditGeometry.rect(p, x, y, w, h);
    }
}