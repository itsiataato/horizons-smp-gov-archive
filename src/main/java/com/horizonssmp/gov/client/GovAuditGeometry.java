package com.horizonssmp.gov.client;

import com.horizonssmp.gov.common.AuditKeys;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;

final class GovAuditGeometry {
    static void layout(final GovAuditPanel p, final int left, final int top, final int right, final int bottom, final Font font) {
        p.font = font;
        final int width = Math.max(0, right - left);
        final int height = Math.max(0, bottom - top);
        p.bounds = new GovRect(left, top, width, height);
        computeGeometry(p);
        GovAuditCaches.rebuildAllCaches(p);
        GovAuditWidgets.syncWidgets(p);
    }

    static void computeGeometry(final GovAuditPanel p) {
        if (p.bounds.w() <= 0 || p.bounds.h() <= 0) {
            p.barRect = GovAuditPanel.EMPTY;
            p.filterLabelRow = GovAuditPanel.EMPTY;
            p.filterRow1 = GovAuditPanel.EMPTY;
            p.actorRect = GovAuditPanel.EMPTY;
            p.actionRect = GovAuditPanel.EMPTY;
            p.targetRect = GovAuditPanel.EMPTY;
            p.applyRect = GovAuditPanel.EMPTY;
            p.filterRow2 = GovAuditPanel.EMPTY;
            p.outcomeRect = GovAuditPanel.EMPTY;
            p.windowRect = GovAuditPanel.EMPTY;
            p.exportAuditRect = GovAuditPanel.EMPTY;
            p.countRect = GovAuditPanel.EMPTY;
            p.listRect = GovAuditPanel.EMPTY;
            p.listInnerRect = GovAuditPanel.EMPTY;
            p.listRetryRect = GovAuditPanel.EMPTY;
            p.detailRect = GovAuditPanel.EMPTY;
            p.detailInnerRect = GovAuditPanel.EMPTY;
            p.detailSummaryRect = GovAuditPanel.EMPTY;
            p.detailRetryRect = GovAuditPanel.EMPTY;
            p.prevRect = GovAuditPanel.EMPTY;
            p.nextRect = GovAuditPanel.EMPTY;
            p.pageRect = GovAuditPanel.EMPTY;
            p.refreshRect = GovAuditPanel.EMPTY;
            p.adminHeaderRect = GovAuditPanel.EMPTY;
            p.adminBeginRect = GovAuditPanel.EMPTY;
            p.adminCommitRect = GovAuditPanel.EMPTY;
            p.adminRefreshRect = GovAuditPanel.EMPTY;
            p.tokenRect = GovAuditPanel.EMPTY;
            p.sectionLabelRect = GovAuditPanel.EMPTY;
            p.sectionPrevRect = GovAuditPanel.EMPTY;
            p.sectionNextRect = GovAuditPanel.EMPTY;
            p.sectionExportRect = GovAuditPanel.EMPTY;
            p.adminInfoRect = GovAuditPanel.EMPTY;
            p.adminBodyRect = GovAuditPanel.EMPTY;
            p.adminStateRect = GovAuditPanel.EMPTY;
            return;
        }

        final int pad = p.bounds.w() < 340 || p.bounds.h() < 120
                ? GovMetrics.GAP_XS : GovMetrics.GAP_S;
        final int barH = GovMetrics.BUTTON_H;
        p.barRect = rect(p, p.bounds.x() + pad, p.bounds.bottom() - pad - barH,
                p.bounds.w() - pad * 2, barH);

        p.filterLabelRow = rect(p, p.bounds.x() + pad, p.bounds.y() + pad,
                p.bounds.w() - pad * 2, GovMetrics.LINE_BODY);
        p.filterRow1 = rect(p, p.bounds.x() + pad,
                p.filterLabelRow.bottom() + 2,
                p.bounds.w() - pad * 2, GovMetrics.FIELD_H);
        final int applyW = GovMetrics.clamp(p.bounds.w() / 6, 34, 54);
        final int fieldW = Math.max(0,
                (p.filterRow1.w() - applyW - GovMetrics.GAP_XS * 3) / 3);
        p.actorRect = rect(p, p.filterRow1.x(), p.filterRow1.y(), fieldW,
                GovMetrics.FIELD_H);
        p.actionRect = rect(p, p.actorRect.right() + GovMetrics.GAP_XS,
                p.filterRow1.y(), fieldW, GovMetrics.FIELD_H);
        p.targetRect = rect(p, p.actionRect.right() + GovMetrics.GAP_XS,
                p.filterRow1.y(), fieldW, GovMetrics.FIELD_H);
        p.applyRect = rect(p, p.filterRow1.right() - applyW, p.filterRow1.y(), applyW,
                GovMetrics.FIELD_H);

        p.filterRow2 = rect(p, p.bounds.x() + pad,
                p.filterRow1.bottom() + GovMetrics.GAP_XS,
                p.bounds.w() - pad * 2, GovMetrics.BUTTON_H);
        final int filterButtonW = Math.max(0,
                (p.filterRow2.w() - GovMetrics.GAP_XS * 2) / 3);
        p.outcomeRect = rect(p, p.filterRow2.x(), p.filterRow2.y(), filterButtonW,
                GovMetrics.BUTTON_H);
        p.windowRect = rect(p, p.outcomeRect.right() + GovMetrics.GAP_XS,
                p.filterRow2.y(), filterButtonW, GovMetrics.BUTTON_H);
        p.exportAuditRect = rect(p, p.windowRect.right() + GovMetrics.GAP_XS,
                p.filterRow2.y(), filterButtonW, GovMetrics.BUTTON_H);

        p.countRect = rect(p, p.bounds.x() + pad,
                p.filterRow2.bottom() + GovMetrics.GAP_XS,
                p.bounds.w() - pad * 2, GovMetrics.LINE_BODY);
        final int listTop = p.countRect.bottom() + 2;
        p.shortDetail = p.bounds.h() < GovAuditPanel.SHORT_DETAIL_H;
        if (p.shortDetail) {
            p.detailSummaryRect = rect(p, p.bounds.x() + pad,
                    p.barRect.y() - GovMetrics.LINE_BODY,
                    p.bounds.w() - pad * 2, GovMetrics.LINE_BODY);
            final int listBottom = p.detailSummaryRect.y();
            p.listRect = rect(p, p.bounds.x() + pad, listTop,
                    p.bounds.w() - pad * 2, Math.max(0, listBottom - listTop));
            p.detailRect = GovAuditPanel.EMPTY;
            p.detailInnerRect = GovAuditPanel.EMPTY;
            p.detailRetryRect = GovAuditPanel.EMPTY;
        } else {
            p.detailSummaryRect = GovAuditPanel.EMPTY;
            final int available = Math.max(0, p.barRect.y() - 4 - listTop);
            int listH = Math.min(available, AuditKeys.PAGE_SIZE * GovMetrics.ROW_H + 4);
            int detailH = Math.max(0, available - listH - GovMetrics.GAP_XS);
            if (detailH < 38) {
                detailH = Math.min(38, Math.max(0, available - 40 - GovMetrics.GAP_XS));
                listH = Math.max(0, available - detailH - GovMetrics.GAP_XS);
            }
            p.listRect = rect(p, p.bounds.x() + pad, listTop,
                    p.bounds.w() - pad * 2, listH);
            p.detailRect = rect(p, p.bounds.x() + pad,
                    p.listRect.bottom() + GovMetrics.GAP_XS,
                    p.bounds.w() - pad * 2, detailH);
            p.detailInnerRect = rect(p, p.detailRect.x() + 4, p.detailRect.y() + 14,
                    Math.max(0, p.detailRect.w() - 8),
                    Math.max(0, p.detailRect.h() - 18));
            p.detailRetryRect = retryRect(p, p.detailInnerRect);
        }
        p.listInnerRect = rect(p, p.listRect.x() + 1, p.listRect.y() + 1,
                Math.max(0, p.listRect.w() - 2), Math.max(0, p.listRect.h() - 2));
        p.listRetryRect = retryRect(p, p.listInnerRect);

        final int prevW = p.bounds.w() < 340 ? 36 : 38;
        final int nextW = prevW;
        final int pageW = p.bounds.w() < 340 ? 48 : 64;
        final int refreshW = p.bounds.w() < 340 ? 44 : 56;
        int x = p.barRect.right() - refreshW;
        p.refreshRect = rect(p, x, p.barRect.y(), refreshW, barH);
        x -= pageW + GovMetrics.GAP_XS;
        p.pageRect = rect(p, x, p.barRect.y(), pageW, barH);
        x -= nextW + GovMetrics.GAP_XS;
        p.nextRect = rect(p, x, p.barRect.y(), nextW, barH);
        x -= prevW + GovMetrics.GAP_XS;
        p.prevRect = rect(p, x, p.barRect.y(), prevW, barH);

        p.adminHeaderRect = rect(p, p.bounds.x() + pad, p.bounds.y() + pad,
                p.bounds.w() - pad * 2, GovMetrics.LINE_TITLE);
        final int adminRowY = p.adminHeaderRect.bottom() + GovMetrics.GAP_XS;
        final int adminRowW = p.bounds.w() - pad * 2;
        final int beginW = GovMetrics.clamp(adminRowW / 2, 74, 180);
        final int commitW = GovMetrics.clamp(adminRowW - beginW - GovMetrics.GAP_XS,
                74, 180);
        p.adminBeginRect = rect(p, p.bounds.x() + pad, adminRowY, beginW,
                GovMetrics.BUTTON_H);
        p.adminCommitRect = rect(p, p.adminBeginRect.right() + GovMetrics.GAP_XS, adminRowY,
                commitW, GovMetrics.BUTTON_H);
        final int refreshWAdmin = GovMetrics.clamp(adminRowW / 4, 56, 110);
        final int tokenW = Math.max(0,
                adminRowW - refreshWAdmin - GovMetrics.GAP_XS);
        final int adminSecondY = p.adminBeginRect.bottom() + GovMetrics.GAP_XS;
        p.tokenRect = rect(p, p.bounds.x() + pad, adminSecondY, tokenW,
                GovMetrics.FIELD_H);
        p.adminRefreshRect = rect(p, p.tokenRect.right() + GovMetrics.GAP_XS,
                adminSecondY, refreshWAdmin, GovMetrics.BUTTON_H);
        final int sectionY = p.tokenRect.bottom() + GovMetrics.GAP_XS;
        final int sectionExportW = GovMetrics.clamp(p.bounds.w() / 3 + 8, 76, 96);
        final int arrowW = 20;
        final int sectionLabelW = Math.max(0, p.bounds.w() - pad * 2
                - arrowW * 2 - sectionExportW - GovMetrics.GAP_XS * 3);
        p.sectionLabelRect = rect(p, p.bounds.x() + pad, sectionY, sectionLabelW,
                GovMetrics.BUTTON_H);
        p.sectionPrevRect = rect(p, p.sectionLabelRect.right() + GovMetrics.GAP_XS,
                sectionY, arrowW, GovMetrics.BUTTON_H);
        p.sectionNextRect = rect(p, p.sectionPrevRect.right() + GovMetrics.GAP_XS,
                sectionY, arrowW, GovMetrics.BUTTON_H);
        p.sectionExportRect = rect(p, p.sectionNextRect.right() + GovMetrics.GAP_XS,
                sectionY, sectionExportW, GovMetrics.BUTTON_H);
        p.adminInfoRect = rect(p, p.bounds.x() + pad,
                p.sectionExportRect.bottom() + GovMetrics.GAP_XS,
                p.bounds.w() - pad * 2,
                Math.max(0, p.bounds.bottom() - pad
                        - (p.sectionExportRect.bottom() + GovMetrics.GAP_XS)));
        p.adminBodyRect = rect(p, p.bounds.x() + pad,
                p.adminHeaderRect.bottom() + GovMetrics.GAP_XS,
                p.bounds.w() - pad * 2,
                Math.max(0, p.bounds.bottom() - pad
                        - (p.adminHeaderRect.bottom() + GovMetrics.GAP_XS)));
        p.adminStateRect = rect(p, p.bounds.x() + pad,
                p.tokenRect.bottom() + 4, p.bounds.w() - pad * 2,
                Math.max(0, p.bounds.bottom() - pad - (p.tokenRect.bottom() + 4)));
        positionWidgets(p);
    }

    static void positionWidgets(final GovAuditPanel p) {
        position(p, p.actorBox, p.actorRect);
        position(p, p.actionBox, p.actionRect);
        position(p, p.targetBox, p.targetRect);
        position(p, p.tokenBox, p.tokenRect);
    }

    static void position(final GovAuditPanel p, final EditBox box, final GovRect rect) {
        if (box == null) {
            return;
        }
        box.setX(rect.x() + 4);
        box.setY(rect.y() + 4);
        box.setWidth(Math.max(1, rect.w() - 8));
    }

    static GovRect rect(final GovAuditPanel p, final int x, final int y, final int width, final int height) {
        final int w = GovMetrics.clamp(width, 0, p.bounds.w());
        final int h = GovMetrics.clamp(height, 0, p.bounds.h());
        final int rx = GovMetrics.clamp(x, p.bounds.x(), p.bounds.right() - w);
        final int ry = GovMetrics.clamp(y, p.bounds.y(), p.bounds.bottom() - h);
        return new GovRect(rx, ry, w, h);
    }

    static GovRect retryRect(final GovAuditPanel p, final GovRect inner) {
        if (p.font == null || inner.w() < 44 || inner.h() < 52) {
            return GovAuditPanel.EMPTY;
        }
        final int width = GovMetrics.clamp(p.font.width(p.retryText) + 20, 44, 88);
        return rect(p, inner.x() + (inner.w() - width) / 2,
                inner.bottom() - GovMetrics.BUTTON_H - 4, width, GovMetrics.BUTTON_H)
                .clamp(inner);
    }
}