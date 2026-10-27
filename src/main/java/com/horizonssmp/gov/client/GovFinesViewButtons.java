package com.horizonssmp.gov.client;

import com.horizonssmp.gov.client.GovFinesTypes.*;
import static com.horizonssmp.gov.client.GovFinesTypes.*;

import java.util.List;

final class GovFinesViewButtons {

    static void rebuildButtons(final GovFinesPanel p) {
        p.buttons.clear();
        p.listRetryRect = NONE;
        p.detailRetryRect = NONE;
        if (p.area.w() <= 0 || p.area.h() <= 0) {
            return;
        }
        if (p.listState == LoadState.ERROR) {
            p.listRetryRect = retryRect(p, p.listRect);
        }
        if (p.detailState == LoadState.ERROR && p.detailRect.w() > 0) {
            p.detailRetryRect = retryRect(p, detailBodyRect(p));
        }
        switch (p.subtab) {
            case TAB_REGISTER -> {
                for (int i = 0; i < FILTERS.length; i++) {
                    addButton(p, p.filterRects[i],
                            GovFinesLabels.label(p, "gui.gov.fines.filter." + FILTERS[i]),
                            null, Click.FILTER, FILTERS[i], true,
                            FILTERS[i].equals(p.registerFilter));
                }
                addListButtons(p);
                addButton(p, p.detailRetryRect, GovFinesLabels.label(p, "gui.gov.card.retry"), null,
                        Click.RETRY_DETAIL, "", true, false);
            }
            case TAB_ISSUE -> {
                addButton(p, p.onlinePrevRect, "<", null, Click.ONLINE_PREV, "", true, false);
                addButton(p, p.onlineNextRect, ">", null, Click.ONLINE_NEXT, "", true, false);
                for (int i = 0; i < BANDS.length; i++) {
                    addButton(p, p.bandRects[i], GovFinesLabels.bandButtonLabel(p, BANDS[i], p.bandRects[i].w()),
                            null, Click.BAND, BANDS[i],
                            true, BANDS[i].equals(p.issueBand));
                }
                addButton(p, p.issueButtonRect, GovFinesLabels.label(p, "gui.gov.fines.issue_action"), null,
                        Click.ISSUE_SUBMIT, "", !GovFinesRequests.mutationPending(p) && p.canIssue, true);
            }
            case TAB_COLLECT -> {
                addButton(p, p.collectPrevRect, "<", null, Click.WAIVE_PREV, "", true, false);
                addButton(p, p.collectNextRect, ">", null, Click.WAIVE_NEXT, "", true, false);
                addButton(p, p.collectButtonRect, GovFinesLabels.label(p, "gui.gov.fines.collect"),
                        null, Click.COLLECT, "", !GovFinesRequests.mutationPending(p) && GovFinesActions.collectEligible(p), true);
                addButton(p, p.collectPayRect, GovFinesLabels.label(p, "gui.gov.fines.pay"), null, Click.PAY, "",
                        !GovFinesRequests.mutationPending(p) && GovFinesActions.payEligible(p), false);
                if (p.detailRect.w() > 0) {
                    addListButtons(p);
                }
            }
            case TAB_WAIVE -> {
                addButton(p, p.waivePrevRect, "<", null, Click.WAIVE_PREV, "", true, false);
                addButton(p, p.waiveNextRect, ">", null, Click.WAIVE_NEXT, "", true, false);
                addButton(p, p.waiveButtonRect, GovFinesLabels.label(p, "gui.gov.fines.waive"),
                        GovFinesLabels.label(p, "gui.gov.fines.waive_confirm"), Click.WAIVE_SUBMIT, "",
                        !GovFinesRequests.mutationPending(p) && GovFinesActions.waiveEligible(p), true);
                if (p.detailRect.w() > 0) {
                    addListButtons(p);
                }
            }
            default -> {
                // No other mode exists.
            }
        }
    }

    static void addListButtons(final GovFinesPanel p) {
        addButton(p, p.prevRect, GovFinesLabels.label(p, "gui.gov.previous"), null, Click.PAGE, "-1",
                p.page > 0 && !GovFinesRequests.mutationPending(p), false);
        addButton(p, p.nextRect, GovFinesLabels.label(p, "gui.gov.next"), null, Click.PAGE, "1",
                p.page < p.pages - 1 && !GovFinesRequests.mutationPending(p), false);
        addButton(p, p.refreshRect, GovFinesLabels.label(p, "gui.gov.refresh"), null, Click.REFRESH, "",
                !GovFinesRequests.mutationPending(p), false);
        addButton(p, p.listRetryRect, GovFinesLabels.label(p, "gui.gov.card.retry"), null,
                Click.RETRY_LIST, "", true, false);
    }

    /** A retry sits bottom-centre of the viewport that failed, never outside it. */
    static GovRect retryRect(final GovFinesPanel p, final GovRect area) {
        if (area == null || area.w() <= 0 || area.h() <= 0) {
            return NONE;
        }
        final int width = Math.min(RETRY_W, Math.max(0, area.w() - 8));
        final int height = Math.min(RETRY_H, Math.max(0, area.h() - 8));
        if (width <= 0 || height <= 0) {
            return NONE;
        }
        return GovFinesGeometry.rect(p, area.x() + (area.w() - width) / 2, area.bottom() - height - 4,
                width, height);
    }

    /** The scrollable detail body, below the title row. */
    static GovRect detailBodyRect(final GovFinesPanel p) {
        return new GovRect(p.detailRect.x() + 4, p.detailRect.y() + 18,
                Math.max(0, p.detailRect.w() - 8),
                Math.max(0, p.detailRect.h() - 20));
    }

    static void addButton(final GovFinesPanel p, final GovRect rect, final String label, final String confirm,
            final Click click, final String value, final boolean enabled, final boolean primary) {
        if (rect.w() <= 0 || rect.h() <= 0 || label.isEmpty()) {
            return;
        }
        p.buttons.add(new Button(rect, label, confirm, click, value, enabled, primary));
    }

    static void rebuildHits(final GovFinesPanel p) {
        p.rowHits.clear();
        if (p.listRect.w() <= 0 || p.listRect.h() <= 0) {
            return;
        }
        if (p.subtab != TAB_REGISTER && p.subtab != TAB_COLLECT
                && p.subtab != TAB_WAIVE) {
            return;
        }
        if ((p.subtab == TAB_COLLECT || p.subtab == TAB_WAIVE)
                && p.detailRect.w() <= 0) {
            return;
        }
        final int top = p.listRect.y() + 1;
        final int bottom = p.listRect.bottom() - 1;
        int y = top - p.listScroll;
        for (int i = 0; i < p.rowViews.size(); i++) {
            final FineRowView view = p.rowViews.get(i);
            // A partially clipped row is not clickable: a hit must name a row the player can see.
            if (y >= top && y + p.rowH <= bottom) {
                p.rowHits.add(new Hit(GovFinesGeometry.rect(p, p.listRect.x() + 1, y,
                        p.listRect.w() - 2, p.rowH), Click.ROW, view.id()));
            }
            y += p.rowH;
        }
    }

}
