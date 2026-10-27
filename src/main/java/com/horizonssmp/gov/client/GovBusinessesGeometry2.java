package com.horizonssmp.gov.client;

import java.util.List;
import static com.horizonssmp.gov.client.GovBusinessesTypes.*;

final class GovBusinessesGeometry2 {

    static void layoutRegistry(final GovBusinessesPanel p) {
        final boolean compact = p.body.w() < 360 || p.body.h() < 120;
        if (compact) {
            final int summaryHeight = 10;
            final int listHeight = Math.max(0, p.body.h() - summaryHeight - 4);
            p.registryList = new GovRect(p.body.x(), p.body.y(),
                    p.body.w(), listHeight).clamp(p.body);
            p.registrySummary = new GovRect(p.body.x(), p.registryList.bottom() + 4,
                    p.body.w(), summaryHeight).clamp(p.body);
            p.registryDetail = EMPTY;
        } else {
            int listWidth = GovMetrics.clamp(p.body.w() * 42 / 100, 120, 300);
            listWidth = Math.min(listWidth, Math.max(0, p.body.w() - 120));
            p.registryList = new GovRect(p.body.x(), p.body.y(),
                    listWidth, p.body.h()).clamp(p.body);
            p.registryDetail = new GovRect(p.registryList.right() + 4, p.body.y(),
                    Math.max(0, p.body.w() - listWidth - 4), p.body.h()).clamp(p.body);
            p.registrySummary = EMPTY;
        }

        final int filterHeight = p.body.h() < 120 ? 12 : 14;
        p.registryFilterRow = new GovRect(p.registryList.x() + 1,
                p.registryList.y() + 1, Math.max(0, p.registryList.w() - 2), filterHeight)
                .clamp(p.registryList);
        final int headerHeight = 10;
        p.registryHeader = new GovRect(p.registryList.x() + 2,
                p.registryFilterRow.bottom() + 2,
                Math.max(0, p.registryList.w() - 4), headerHeight).clamp(p.registryList);
        final int rowsTop = p.registryHeader.bottom() + 1;
        final int rowsBottom = p.registryList.bottom() - 1;
        p.registryRows = new GovRect(p.registryList.x() + 1, rowsTop,
                Math.max(0, p.registryList.w() - 2),
                Math.max(0, rowsBottom - rowsTop)).clamp(p.registryList);
        p.registryPitch = 18;
        p.registryScrollTrack = new GovRect(p.registryList.right() - 4,
                p.registryRows.y(), 3, p.registryRows.h()).clamp(p.registryList);
        p.listRetry = GovBusinessesGeometry.retryRect(p.registryRows);

        p.filterRects = new GovRect[FILTER_IDS.length];
        final int filterGap = 3;
        final int filterW = GovMetrics.clamp(
                (p.registryFilterRow.w() - filterGap * (FILTER_IDS.length - 1))
                        / FILTER_IDS.length,
                18, 64);
        int filterX = p.registryFilterRow.x();
        for (int index = 0; index < FILTER_IDS.length; index++) {
            p.filterRects[index] = new GovRect(filterX, p.registryFilterRow.y(),
                    filterW, p.registryFilterRow.h()).clamp(p.registryFilterRow);
            p.filterDisplayLabels[index] = GovText.clip(p.font,
                    p.filterLabels[index], Math.max(0, filterW - 8));
            filterX += filterW + filterGap;
        }

        p.registryPrev = new GovRect(p.bar.x(), p.bar.y(), 24, p.bar.h())
                .clamp(p.bar);
        p.registryNext = new GovRect(p.registryPrev.right() + 3, p.bar.y(),
                24, p.bar.h()).clamp(p.bar);
        p.registryPage = new GovRect(p.registryNext.right() + 4, p.bar.y(),
                Math.max(0, p.refreshRect.x() - 4 - (p.registryNext.right() + 4)),
                p.bar.h()).clamp(p.bar);
        p.registryStatusBar = new GovRect(p.registryPage.x() + 32, p.bar.y(),
                Math.max(0, p.registryPage.w() - 32), p.bar.h()).clamp(p.bar);

        if (p.registryDetail.w() > 0 && p.registryDetail.h() > 0) {
            p.detailHeader = new GovRect(p.registryDetail.x() + 6,
                    p.registryDetail.y() + 4,
                    Math.max(0, p.registryDetail.w() - 12), 12).clamp(p.registryDetail);
            p.detailBody = new GovRect(p.registryDetail.x() + 6,
                    p.detailHeader.bottom() + 4,
                    Math.max(0, p.registryDetail.w() - 12),
                    Math.max(0, p.registryDetail.bottom() - 4
                            - (p.detailHeader.bottom() + 4))).clamp(p.registryDetail);
            p.detailScrollTrack = new GovRect(p.registryDetail.right() - 4,
                    p.detailBody.y(), 3, p.detailBody.h()).clamp(p.registryDetail);
            p.detailRetry = GovBusinessesGeometry.retryRect(p.detailBody);
        } else {
            p.detailHeader = EMPTY;
            p.detailBody = EMPTY;
            p.detailScrollTrack = EMPTY;
            p.detailRetry = EMPTY;
        }
    }

    static void layoutStaff(final GovBusinessesPanel p) {
        p.staffList = new GovRect(p.body.x(), p.body.y(), p.body.w(),
                p.body.h()).clamp(p.body);
        p.staffHeader = new GovRect(p.staffList.x() + 2, p.staffList.y() + 1,
                Math.max(0, p.staffList.w() - 4), 10).clamp(p.staffList);
        final int rowsTop = p.staffHeader.bottom() + 1;
        p.staffRows = new GovRect(p.staffList.x() + 1, rowsTop,
                Math.max(0, p.staffList.w() - 2),
                Math.max(0, p.staffList.bottom() - 1 - rowsTop)).clamp(p.staffList);
        p.staffPitch = 18;
        p.staffScrollTrack = new GovRect(p.staffList.right() - 4, p.staffRows.y(),
                3, p.staffRows.h()).clamp(p.staffList);
        p.staffRetry = GovBusinessesGeometry.retryRect(p.staffRows);

        final int addW = GovMetrics.clamp(p.bar.w() / 5, 44, 64);
        final int linkW = addW;
        p.staffAdd = new GovRect(p.bar.x(), p.bar.y(), addW, p.bar.h())
                .clamp(p.bar);
        p.staffLink = new GovRect(p.staffAdd.right() + 4, p.bar.y(), linkW,
                p.bar.h()).clamp(p.bar);
        p.staffStatusBar = new GovRect(p.staffLink.right() + 4, p.bar.y(),
                Math.max(0, p.refreshRect.x() - 4 - (p.staffLink.right() + 4)),
                p.bar.h()).clamp(p.bar);
    }

    static void layoutForms(final GovBusinessesPanel p) {
        GovBusinessesGeometry2Forms.layoutForms(p);
    }

    static void layoutEmployment(final GovBusinessesPanel p) {
        // Split the body: position list on top, compact edit fields below.
        final int listHeight = Math.max(0, p.body.h() * 60 / 100);
        p.positionList = new GovRect(p.body.x(), p.body.y(), p.body.w(), listHeight).clamp(p.body);
        p.positionHeader = new GovRect(p.positionList.x() + 2, p.positionList.y() + 1,
                Math.max(0, p.positionList.w() - 4), 10).clamp(p.positionList);
        final int rowsTop = p.positionHeader.bottom() + 1;
        p.positionListRows = new GovRect(p.positionList.x() + 1, rowsTop,
                Math.max(0, p.positionList.w() - 2),
                Math.max(0, p.positionList.bottom() - 1 - rowsTop)).clamp(p.positionList);
        p.positionPitch = 18;
        p.positionScrollTrack = new GovRect(p.positionList.right() - 4, p.positionListRows.y(),
                3, p.positionListRows.h()).clamp(p.positionList);

        final int editTop = p.positionList.bottom() + 4;
        final int editHeight = Math.max(0, p.body.bottom() - editTop - 1);
        p.positionEditArea = new GovRect(p.body.x(), editTop, p.body.w(), editHeight)
                .clamp(p.body);
        final int fieldH = GovMetrics.FIELD_H;
        final int gap = 4;
        final int labelW = GovMetrics.clamp(p.positionEditArea.w() * 28 / 100, 60, 120);
        final int ctrlW = Math.max(0, p.positionEditArea.w() - labelW - 12);
        final int titleY = p.positionEditArea.y() + 2;
        final int wageY = titleY + fieldH + gap;
        p.positionTitleLabel = new GovRect(p.positionEditArea.x() + 2, titleY,
                labelW, fieldH).clamp(p.positionEditArea);
        p.positionTitleCtrl = new GovRect(p.positionTitleLabel.right() + 4, titleY,
                ctrlW, fieldH).clamp(p.positionEditArea);
        p.positionWageLabel = new GovRect(p.positionEditArea.x() + 2, wageY,
                labelW, fieldH).clamp(p.positionEditArea);
        p.positionWageCtrl = new GovRect(p.positionWageLabel.right() + 4, wageY,
                ctrlW, fieldH).clamp(p.positionEditArea);
        final int btnW = GovMetrics.clamp(ctrlW / 3, 44, 80);
        p.positionEditBtn = new GovRect(p.positionEditArea.right() - btnW - 2, wageY,
                btnW, fieldH).clamp(p.positionEditArea);
    }

    static void layoutModal(final GovBusinessesPanel p) {
        final GovRect container = p.rect.w() > 0 && p.rect.h() > 0 ? p.rect : p.body;
        p.modalRect = GovMetrics.fit(container, 320, 140);
        if (p.modalRect.w() <= 0 || p.modalRect.h() <= 0) {
            p.modalClose = EMPTY;
            p.suspendForm = null;
            p.staffAddForm = null;
            p.linkForm = null;
            return;
        }
        // The header band is 20 px tall; the close box stays inside it and never grows the frame.
        p.modalClose = p.modalRect.w() >= CLOSE_W + 8 && p.modalRect.h() >= 20
                ? new GovRect(p.modalRect.right() - 5 - CLOSE_W,
                        p.modalRect.y() + 5, CLOSE_W, CLOSE_H)
                : EMPTY;
        final GovRect modalBody = new GovRect(p.modalRect.x() + 6,
                p.modalRect.y() + 22,
                Math.max(0, p.modalRect.w() - 12),
                Math.max(0, p.modalRect.h() - 28)).clamp(p.modalRect);
        p.suspendForm = GovBusinessesGeometry2Forms.buildForm(p, modalBody, List.of(
                new FieldSpec(FieldKind.REASON, p.labelReason, null, false)), List.of(
                new ActionSpec(p.suspendAction, p.removeConfirm,
                        Click.SUSPEND_SUBMIT, true),
                new ActionSpec(p.cancelAction, null, Click.SUSPEND_CANCEL, false)));
        p.staffAddForm = GovBusinessesGeometry2Forms.buildForm(p, modalBody, List.of(
                new FieldSpec(FieldKind.STAFF_PLAYER, p.labelPlayer, null, false),
                new FieldSpec(FieldKind.ROLE, p.labelRole, Click.STAFF_ROLE, false)), List.of(
                new ActionSpec(p.addAction, null, Click.STAFF_ADD_SUBMIT, true),
                new ActionSpec(p.cancelAction, null, Click.STAFF_ADD_CANCEL, false)));
        p.linkForm = GovBusinessesGeometry2Forms.buildForm(p, modalBody, List.of(
                new FieldSpec(FieldKind.LINK_JOB, p.labelLinkJob, null, false)), List.of(
                new ActionSpec(p.linkAction, null, Click.LINK_SUBMIT, true),
                new ActionSpec(p.unlinkAction, null, Click.LINK_UNLINK, false),
                new ActionSpec(p.cancelAction, null, Click.LINK_CANCEL, false)));
    }
}
