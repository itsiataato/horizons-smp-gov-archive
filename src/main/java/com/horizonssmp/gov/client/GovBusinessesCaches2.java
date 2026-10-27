package com.horizonssmp.gov.client;

import com.horizonssmp.gov.client.GovDashboardScreen;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import com.horizonssmp.gov.common.BusinessKeys;
import com.horizonssmp.gov.common.GovFields;
import com.horizonssmp.gov.common.OpCode;
import com.horizonssmp.gov.network.GovActionResultPayload;
import com.horizonssmp.gov.network.GovNetwork;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import com.horizonssmp.gov.client.GovBusinessesTypes.*;
import static com.horizonssmp.gov.client.GovBusinessesTypes.*;

final class GovBusinessesCaches2 {

    static     void rebuildStaffViews(final GovBusinessesPanel p) {
        p.staffViews.clear();
        p.staffErrorText = GovText.clip(p.font, p.detailError,
                Math.max(0, p.staffRows.w() - 12));
        if (p.staffList.w() <= 0 || p.staffRows.w() <= 0 || p.detail.isEmpty()) {
            p.staffHeaderText = "";
            return;
        }
        final int staff = GovBusinessesUtil.intField(p.detail, BusinessKeys.EMPLOYEE_COUNT);
        final int listed = Math.min(staff, BusinessKeys.MAX_LISTED_EMPLOYEES);
        final int removeWidth = GovBusinessesStatus.canEdit(p) ? 52 : 0;
        final int textWidth = Math.max(0, p.staffList.w() - removeWidth - 16);
        for (int index = 0; index < listed; index++) {
            final String prefix = BusinessKeys.EMPLOYEE_PREFIX + index;
            final String uuid = p.detail.getOrDefault(
                    prefix + BusinessKeys.EMPLOYEE_UUID, "");
            final String name = p.detail.getOrDefault(
                    prefix + BusinessKeys.EMPLOYEE_NAME, "");
            final String role = p.detail.getOrDefault(
                    prefix + BusinessKeys.EMPLOYEE_ROLE, "");
            final boolean online = "true".equals(
                    p.detail.get(prefix + BusinessKeys.EMPLOYEE_ONLINE));
            final long since = GovBusinessesUtil.longField(p.detail, prefix + BusinessKeys.EMPLOYEE_SINCE);
            final String line1 = GovText.clip(p.font,
                    GovBusinessesUtil.dash(name) + "  /  " + GovBusinessesStatus.roleLabel(p, role), textWidth);
            final String line2 = GovText.clip(p.font,
                    (online ? GovBusinessesUtil.upper(GovBusinessesUtil.tr("gui.gov.businesses.online"))
                            : GovBusinessesUtil.upper(GovBusinessesUtil.tr("gui.gov.businesses.offline")))
                            + "  /  " + GovBusinessesUtil.upper(GovBusinessesUtil.tr("gui.gov.businesses.field.since")) + " "
                            + GovBusinessesUtil.when(since), textWidth);
            p.staffViews.add(new StaffView(uuid, line1, line2, !uuid.isBlank()));
        }
        if (staff > listed) {
            p.staffViews.add(new StaffView("", GovText.clip(p.font,
                    GovBusinessesUtil.tr("gui.gov.businesses.staff.showing", listed, staff), textWidth), "", false));
        }
        p.staffHeaderText = GovText.clip(p.font,
                GovBusinessesUtil.upper(GovBusinessesUtil.tr("gui.gov.businesses.staff.count", staff))
                        + "  /  " + GovBusinessesUtil.upper(GovBusinessesUtil.tr("gui.gov.businesses.payroll_rate",
                                GovBusinessesUtil.longField(p.detail, BusinessKeys.PAYROLL_RATE))),
                Math.max(0, p.staffHeader.w() - 4));
    }

    static     void rebuildStaffRemoveRects(final GovBusinessesPanel p) {
        p.staffRemoveRects.clear();
        for (int index = 0; index < p.staffViews.size(); index++) {
            final StaffView view = p.staffViews.get(index);
            if (!GovBusinessesStatus.canEdit(p) || !view.removable()) {
                p.staffRemoveRects.add(EMPTY);
                continue;
            }
            final int y = p.staffRows.y() + index * p.staffPitch - p.staffScroll;
            // A clipped button is not a button. Never clamp it into the viewport, because a
            // clamped rectangle would move the hit target onto a different row.
            final GovRect remove = new GovRect(p.staffList.right() - 56, y + 2, 52, 14);
            p.staffRemoveRects.add(GovBusinessesGeometry.fullyVisible(remove, p.staffRows) ? remove : EMPTY);
        }
    }

    static     void rebuildFormValues(final GovBusinessesPanel p) {
        final GovRect typeControl = GovBusinessesHits.controlFor(p.newForm, FieldKind.TYPE);
        p.registerTypeText = GovText.clip(p.font, GovBusinessesStatus.typeLabel(p, p.registerType),
                Math.max(0, typeControl.w() - 8));

        final GovRect businessControl = GovBusinessesHits.controlFor(p.ownershipForm, FieldKind.BUSINESS);
        final String business = p.selectedId.isEmpty()
                ? p.noBusinessText : GovBusinessesUtil.dash(p.detail.getOrDefault(
                        BusinessKeys.NAME, p.selectedId));
        final int centreWidth = p.businessCentreRect.w() > 0
                ? p.businessCentreRect.w() - 4 : businessControl.w() - 12;
        p.selectedBusinessText = GovText.clip(p.font, business,
                Math.max(0, centreWidth));

        p.currentOwnerValue = GovText.clip(p.font,
                GovBusinessesUtil.dash(p.detail.get(BusinessKeys.OWNER)),
                Math.max(0, GovBusinessesHits.controlFor(p.ownershipForm, FieldKind.CURRENT_OWNER).w() - 4));
        p.businessStatusValue = GovText.clip(p.font, GovBusinessesUtil.dash(GovBusinessesStatus.businessStatus(p)),
                Math.max(0, GovBusinessesHits.controlFor(p.ownershipForm, FieldKind.STATUS).w() - 4));

        p.staffRoleText = GovText.clip(p.font, GovBusinessesStatus.roleLabel(p, p.employeeRole),
                Math.max(0, GovBusinessesHits.controlFor(p.staffAddForm, FieldKind.ROLE).w() - 8));

        p.ownershipNoteText = GovText.clip(p.font,
                GovBusinessesUtil.tr("gui.gov.businesses.transfer_warning"),
                Math.max(0, p.ownershipForm == null ? 0 : p.ownershipForm.noteRect.w()));
        p.payrollNoteText = GovText.clip(p.font, GovBusinessesCaches2.payrollSummary(p),
                Math.max(0, p.payrollForm == null ? 0 : p.payrollForm.noteRect.w()));
        p.payrollSummaryText = GovText.clip(p.font, GovBusinessesCaches2.payrollSummary(p),
                Math.max(0, p.formStatusBar.w() - 4));
        p.suspendNoteText = GovText.clip(p.font,
                GovBusinessesUtil.tr("gui.gov.businesses.suspend_warning"),
                Math.max(0, p.suspendForm == null ? 0 : p.suspendForm.noteRect.w()));
        p.linkNoteText = GovText.clip(p.font,
                GovBusinessesUtil.tr("gui.gov.businesses.link_warning"),
                Math.max(0, p.linkForm == null ? 0 : p.linkForm.noteRect.w()));

        p.registrySummaryText = GovText.clip(p.font, GovBusinessesCaches2.registrySummary(p),
                Math.max(0, p.registrySummary.w() - 4));

        p.payrollRecordArmedLabel = "";
        if (GovBusinessesArm.isArmed(p, Click.PAYROLL_RECORD)) {
            final GovRect recordRect = GovBusinessesHits.actionRect(p.payrollForm, Click.PAYROLL_RECORD);
            if (recordRect.w() > 0) {
                final String amount = p.payrollRecordArmedAmount.isEmpty()
                        ? "-" : p.payrollRecordArmedAmount;
                final String recipient = p.payrollRecordArmedRecipient.isBlank()
                        ? GovBusinessesStatus.businessName(p) : p.payrollRecordArmedRecipient;
                final String confirm = GovBusinessesUtil.tr("gui.gov.businesses.payroll_record_confirm",
                        amount, recipient);
                p.payrollRecordArmedLabel = GovText.clip(p.font, GovBusinessesUtil.upper(confirm),
                        Math.max(0, recordRect.w() - 8));
            }
        }
    }

    static     String payrollSummary(final GovBusinessesPanel p) {
        return GovBusinessesUtil.tr("gui.gov.businesses.payroll_history",
                GovBusinessesUtil.intField(p.detail, BusinessKeys.PAYROLL_COUNT),
                GovBusinessesUtil.longField(p.detail, BusinessKeys.PAYROLL_LAST_AMOUNT),
                GovBusinessesUtil.when(GovBusinessesUtil.longField(p.detail, BusinessKeys.PAYROLL_LAST_AT)),
                GovBusinessesUtil.dash(p.detail.get(BusinessKeys.PAYROLL_LAST_BY)));
    }

    static     String registrySummary(final GovBusinessesPanel p) {
        if (p.selectedId.isEmpty()) {
            return p.noSelectionText;
        }
        final String name = GovBusinessesUtil.dash(p.detail.getOrDefault(
                BusinessKeys.NAME, p.selectedId));
        return name + "  /  " + GovBusinessesUtil.dash(GovBusinessesStatus.businessStatus(p))
                + "  /  " + GovBusinessesUtil.dash(p.detail.get(BusinessKeys.OWNER))
                + "  /  STAFF " + GovBusinessesUtil.intField(p.detail, BusinessKeys.EMPLOYEE_COUNT);
    }

    static     void rebuildStatusCache(final GovBusinessesPanel p) {
        final GovRect area = p.statusRect.h() > 0 ? p.statusRect : GovBusinessesCaches2.statusBar(p);
        p.statusClipWidth = p.modal != Modal.NONE
                ? Math.max(0, p.modalRect.w() - 12)
                : Math.max(0, area.w() - 4);
        final String base = p.status.isEmpty() ? GovBusinessesCaches2.defaultStatus(p) : p.status;
        p.statusText = GovText.clip(p.font, base, p.statusClipWidth);
    }

    static     GovRect statusBar(final GovBusinessesPanel p) {
        return switch (p.subtab) {
            case MODE_REGISTRY -> p.registryStatusBar;
            case MODE_STAFF -> p.staffStatusBar;
            default -> p.formStatusBar;
        };
    }

    static     String defaultStatus(final GovBusinessesPanel p) {
        return switch (p.subtab) {
            case MODE_STAFF -> p.staffHeaderText;
            case MODE_PAYROLL -> p.payrollSummaryText;
            default -> "";
        };
    }

    static     void clampScrolls(final GovBusinessesPanel p) {
        p.registryScrollMax = Math.max(0,
                p.rowViews.size() * p.registryPitch - p.registryRows.h());
        p.registryScroll = GovMetrics.clamp(p.registryScroll, 0, p.registryScrollMax);
        int detailHeight = 0;
        for (final DetailLine line : p.detailLines) {
            detailHeight += line.height();
        }
        p.detailScrollMax = Math.max(0, detailHeight - p.detailBody.h());
        p.detailScroll = GovMetrics.clamp(p.detailScroll, 0, p.detailScrollMax);
        p.staffScrollMax = Math.max(0,
                p.staffViews.size() * p.staffPitch - p.staffRows.h());
        p.staffScroll = GovMetrics.clamp(p.staffScroll, 0, p.staffScrollMax);
    }

}
