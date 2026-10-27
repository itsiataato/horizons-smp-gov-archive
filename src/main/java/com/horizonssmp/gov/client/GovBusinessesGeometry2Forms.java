package com.horizonssmp.gov.client;

import java.util.ArrayList;
import java.util.List;
import com.horizonssmp.gov.client.GovBusinessesTypes.*;
import static com.horizonssmp.gov.client.GovBusinessesTypes.*;

final class GovBusinessesGeometry2Forms {

    static void layoutForms(final GovBusinessesPanel p) {
        final List<FieldSpec> newFields = List.of(
                new FieldSpec(FieldKind.NAME, p.labelName, null, false),
                new FieldSpec(FieldKind.TYPE, p.labelType, Click.REGISTER_TYPE, false),
                new FieldSpec(FieldKind.OWNER, p.labelOwner, null, false));
        p.newForm = buildForm(p, p.body, newFields, List.of(
                new ActionSpec(p.registerAction, null, Click.REGISTER_SUBMIT, true)));

        final List<FieldSpec> ownershipFields = List.of(
                new FieldSpec(FieldKind.BUSINESS, p.labelBusiness, Click.BUSINESS_NEXT, false),
                new FieldSpec(FieldKind.CURRENT_OWNER, p.labelOwner, null, true),
                new FieldSpec(FieldKind.STATUS, p.labelStatus, null, true),
                new FieldSpec(FieldKind.TRANSFER_OWNER, p.labelNewOwner, null, false));
        p.ownershipForm = buildForm(p, p.body, ownershipFields, List.of(
                new ActionSpec(p.transferAction, p.removeConfirm,
                        Click.TRANSFER_SUBMIT, false),
                new ActionSpec(p.suspendAction, null, Click.SUSPEND_OPEN, false),
                new ActionSpec(p.closeAction, p.removeConfirm, Click.CLOSE_SUBMIT, false),
                new ActionSpec(p.deleteAction, p.removeConfirm,
                        Click.DELETE_SUBMIT, false)));
        final GovRect businessControl = GovBusinessesHits.controlFor(p.ownershipForm, FieldKind.BUSINESS);
        final int selectorWidth = Math.min(14, Math.max(0, businessControl.w() / 3));
        p.businessPrevRect = new GovRect(businessControl.x(), businessControl.y(),
                selectorWidth, businessControl.h()).clamp(businessControl);
        p.businessNextRect = new GovRect(businessControl.right() - selectorWidth,
                businessControl.y(), selectorWidth, businessControl.h()).clamp(businessControl);
        p.businessCentreRect = new GovRect(p.businessPrevRect.right(),
                businessControl.y(),
                Math.max(0, p.businessNextRect.x() - p.businessPrevRect.right()),
                businessControl.h()).clamp(businessControl);

        final List<FieldSpec> payrollFields = List.of(
                new FieldSpec(FieldKind.RATE, p.labelRate, null, false),
                new FieldSpec(FieldKind.SCHEDULE, p.labelSchedule, null, false),
                new FieldSpec(FieldKind.AMOUNT, p.labelAmount, null, false),
                new FieldSpec(FieldKind.PAYMENT_NOTE, p.labelPaymentNote, null, false));
        p.payrollForm = buildForm(p, p.body, payrollFields, List.of(
                new ActionSpec(p.setRateAction, null, Click.PAYROLL_SET, false),
                new ActionSpec(p.recordAction, p.removeConfirm,
                        Click.PAYROLL_RECORD, false)));

        // Employment form (positions/offers/contracts/complaints/close/rename/payroll run).
        final List<FieldSpec> employmentFields = List.of(
                new FieldSpec(FieldKind.TRANSFER_OWNER, p.labelNewOwner, null, false),
                new FieldSpec(FieldKind.RATE, p.labelRate, null, false),
                new FieldSpec(FieldKind.SCHEDULE, p.labelSchedule, null, false),
                new FieldSpec(FieldKind.AMOUNT, p.labelAmount, null, false),
                new FieldSpec(FieldKind.PAYMENT_NOTE, p.labelPaymentNote, null, false));
        p.employmentForm = buildForm(p, p.body, employmentFields, List.of(
                new ActionSpec(p.renameAction, null, Click.RENAME_SUBMIT, false),
                new ActionSpec(p.payrollRunAction, null, Click.PAYROLL_RUN_SUBMIT, false),
                new ActionSpec(p.positionCreateAction, null, Click.POSITION_CREATE_SUBMIT, false),
                new ActionSpec(p.positionDeleteAction, null, Click.POSITION_DELETE_SUBMIT, false),
                new ActionSpec(p.positionMoveAction, null, Click.POSITION_MOVE_SUBMIT, false),
                new ActionSpec(p.offerSendAction, null, Click.OFFER_SEND_SUBMIT, false),
                new ActionSpec(p.offerRespondAction, null, Click.OFFER_RESPOND_SUBMIT, false),
                new ActionSpec(p.contractEndAction, p.removeConfirm, Click.CONTRACT_END_SUBMIT, false),
                new ActionSpec(p.contractPromoteAction, null, Click.CONTRACT_PROMOTE_SUBMIT, false),
                new ActionSpec(p.complaintFileAction, null, Click.COMPLAINT_FILE_SUBMIT, false),
                new ActionSpec(p.closeRequestAction, null, Click.CLOSE_REQUEST_SUBMIT, false),
                new ActionSpec(p.closeApproveAction, p.removeConfirm, Click.CLOSE_APPROVE_SUBMIT, false),
                new ActionSpec(p.closeRejectAction, p.removeConfirm, Click.CLOSE_REJECT_SUBMIT, false)));
    }

    static FormGeometry buildForm(final GovBusinessesPanel p, final GovRect area, final List<FieldSpec> fields,
            final List<ActionSpec> actions) {
        if (area == null || area.w() <= 0 || area.h() <= 0) {
            return new FormGeometry(EMPTY, List.of(), List.of(), EMPTY);
        }
        final int rowHeight = area.h() < 90 ? 14 : 16;
        final int gap = 3;
        final boolean twoColumn = area.w() >= 240 && fields.size() > 1;
        final int columnGap = 6;
        final int columnWidth = twoColumn ? (area.w() - columnGap) / 2 : area.w();
        final int labelWidth = twoColumn
                ? GovMetrics.clamp(columnWidth * 34 / 100, 48, 120)
                : GovMetrics.clamp(columnWidth * 30 / 100, 50, 96);
        final int controlWidth = Math.max(0, columnWidth - labelWidth - 4);
        final int rowCount = twoColumn ? (fields.size() + 1) / 2 : fields.size();

        final List<FormRow> rows = new ArrayList<>(fields.size());
        for (int index = 0; index < fields.size(); index++) {
            final FieldSpec spec = fields.get(index);
            final int column = twoColumn ? index % 2 : 0;
            final int row = twoColumn ? index / 2 : index;
            final int x = area.x() + column * (columnWidth + columnGap);
            final int y = area.y() + row * (rowHeight + gap);
            final GovRect labelRect = new GovRect(x, y, labelWidth, rowHeight).clamp(area);
            final GovRect controlRect = new GovRect(x + labelWidth + 4, y, controlWidth,
                    rowHeight).clamp(area);
            final String label = GovText.clip(p.font, spec.label(),
                    Math.max(0, labelWidth - 2));
            rows.add(new FormRow(spec.kind(), label, labelRect, controlRect,
                    spec.click(), spec.readonly()));
        }

        final int actionHeight = GovMetrics.BUTTON_H;
        final int actionY = area.bottom() - actionHeight;
        final int actionGap = 4;
        final int actionCount = actions.size();
        final int actionWidth = actionCount > 0
                ? GovMetrics.clamp((area.w() - actionGap * (actionCount - 1)) / actionCount,
                        28, 140)
                : 0;
        final int usedWidth = actionWidth * actionCount
                + actionGap * Math.max(0, actionCount - 1);
        final int startX = area.x() + Math.max(0, (area.w() - usedWidth) / 2);
        final List<FormAction> actionRows = new ArrayList<>(actionCount);
        for (int index = 0; index < actionCount; index++) {
            final ActionSpec spec = actions.get(index);
            final GovRect actionRect = new GovRect(startX + index * (actionWidth + actionGap),
                    actionY, actionWidth, actionHeight).clamp(area);
            final int actionLabelWidth = Math.max(0, actionWidth - 8);
            actionRows.add(new FormAction(
                    GovText.clip(p.font, spec.label(), actionLabelWidth),
                    spec.armedLabel() == null ? null
                            : GovText.clip(p.font, spec.armedLabel(), actionLabelWidth),
                    spec.click(), actionRect, spec.primary()));
        }

        final int noteTop = area.y() + rowCount * (rowHeight + gap);
        final int noteBottom = actionY - gap;
        GovRect noteRect = new GovRect(area.x(), noteTop, area.w(),
                Math.max(0, noteBottom - noteTop)).clamp(area);
        if (noteRect.h() < 9) {
            noteRect = EMPTY;
        }
        return new FormGeometry(area, List.copyOf(rows), List.copyOf(actionRows), noteRect);
    }
}
