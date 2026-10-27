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

final class GovBusinessesLifecycle {

    static     void setActive(final GovBusinessesPanel p, final boolean active) {
        p.active = active;
        if (!active) {
            p.modal = Modal.NONE;
            p.enterRepeat.reset();
            GovBusinessesArm.disarmAll(p);
            GovBusinessesWidgets.clearFocus(p);
        } else {
            GovBusinessesRequests.requestListIfNeeded(p);
        }
        GovBusinessesWidgets.syncWidgets(p);
        GovBusinessesCaches.rebuildCaches(p);
    }

    static     void reset(final GovBusinessesPanel p) {
        p.pending.clear();
        p.latestListReqId = "";
        p.latestDetailReqId = "";
        p.status = "";
        p.statusIsError = false;
        p.modal = Modal.NONE;
        p.registerType = "shop";
        p.employeeRole = "worker";
        p.enterRepeat.reset();
        GovBusinessesArm.disarmAll(p);
        GovBusinessesWidgets.clearFormFields(p);
        GovBusinessesWidgets.syncWidgets(p);
        GovBusinessesCaches.rebuildCaches(p);
    }

    static     void setSubtab(final GovBusinessesPanel p, final int index) {
        final int next = GovMetrics.clamp(index, 0, MODE_EMPLOYMENT);
        if (next == p.subtab && p.modal == Modal.NONE) {
            return;
        }
        p.subtab = next;
        p.modal = Modal.NONE;
        GovBusinessesArm.disarmAll(p);
        GovBusinessesWidgets.clearFocus(p);
        GovBusinessesWidgets.syncWidgets(p);
        GovBusinessesCaches.rebuildCaches(p);
        if (next == MODE_EMPLOYMENT && p.positionState == LoadState.NONE
                && !p.selectedId.isEmpty()) {
            GovBusinessesRequests.requestPositionList(p);
            GovBusinessesRequests.requestOfferList(p);
            GovBusinessesRequests.requestContractList(p);
            GovBusinessesRequests.requestComplaintList(p);
        }
    }

    static void init(final GovBusinessesPanel p, final Screen host) {
        p.host = host;

        p.labelId = GovBusinessesUtil.upper(GovBusinessesUtil.tr("gui.gov.businesses.field.id"));
        p.labelOwner = GovBusinessesUtil.upper(GovBusinessesUtil.tr("gui.gov.businesses.field.owner"));
        p.labelNewOwner = GovBusinessesUtil.upper(GovBusinessesUtil.tr("gui.gov.businesses.field.new_owner"));
        p.labelType = GovBusinessesUtil.upper(GovBusinessesUtil.tr("gui.gov.businesses.field.type"));
        p.labelStatus = GovBusinessesUtil.upper(GovBusinessesUtil.tr("gui.gov.businesses.field.status"));
        p.labelRegistered = GovBusinessesUtil.upper(GovBusinessesUtil.tr("gui.gov.businesses.field.registered"));
        p.labelJob = GovBusinessesUtil.upper(GovBusinessesUtil.tr("gui.gov.businesses.field.job"));
        p.labelStaff = GovBusinessesUtil.upper(GovBusinessesUtil.tr("gui.gov.businesses.field.staff"));
        p.labelSuspension = GovBusinessesUtil.upper(GovBusinessesUtil.tr("gui.gov.businesses.field.suspension"));
        p.labelNotes = GovBusinessesUtil.upper(GovBusinessesUtil.tr("gui.gov.businesses.field.notes"));
        p.labelPayroll = GovBusinessesUtil.upper(GovBusinessesUtil.tr("gui.gov.businesses.field.payroll"));
        p.labelRate = GovBusinessesUtil.upper(GovBusinessesUtil.tr("gui.gov.businesses.field.rate"));
        p.labelSchedule = GovBusinessesUtil.upper(GovBusinessesUtil.tr("gui.gov.businesses.field.schedule"));
        p.labelAmount = GovBusinessesUtil.upper(GovBusinessesUtil.tr("gui.gov.businesses.field.amount"));
        p.labelPaymentNote = GovBusinessesUtil.upper(GovBusinessesUtil.tr("gui.gov.businesses.field.payment_note"));
        p.labelBalance = GovBusinessesUtil.upper(GovBusinessesUtil.tr("gui.gov.businesses.field.balance"));
        p.labelBusiness = GovBusinessesUtil.upper(GovBusinessesUtil.tr("gui.gov.businesses.field.business"));
        p.labelName = GovBusinessesUtil.upper(GovBusinessesUtil.tr("gui.gov.businesses.field.name"));
        p.labelReason = GovBusinessesUtil.upper(GovBusinessesUtil.tr("gui.gov.businesses.field.reason"));
        p.labelRole = GovBusinessesUtil.upper(GovBusinessesUtil.tr("gui.gov.businesses.field.role"));
        p.labelPlayer = GovBusinessesUtil.upper(GovBusinessesUtil.tr("gui.gov.businesses.staff_player_hint"));
        p.labelLinkJob = GovBusinessesUtil.upper(GovBusinessesUtil.tr("gui.gov.businesses.link_job_hint"));

        p.registerAction = GovBusinessesUtil.upper(GovBusinessesUtil.tr("gui.gov.businesses.register_action"));
        p.transferAction = GovBusinessesUtil.upper(GovBusinessesUtil.tr("gui.gov.businesses.transfer_submit"));
        p.suspendAction = GovBusinessesUtil.upper(GovBusinessesUtil.tr("gui.gov.businesses.suspend_submit"));
        p.closeAction = GovBusinessesUtil.upper(GovBusinessesUtil.tr("gui.gov.businesses.close_submit"));
        p.deleteAction = GovBusinessesUtil.upper(GovBusinessesUtil.tr("gui.gov.businesses.delete_submit"));
        p.addAction = GovBusinessesUtil.upper(GovBusinessesUtil.tr("gui.gov.businesses.staff.add"));
        p.removeAction = GovBusinessesUtil.upper(GovBusinessesUtil.tr("gui.gov.businesses.staff.remove"));
        p.removeConfirm = GovBusinessesUtil.upper(GovBusinessesUtil.tr("gui.gov.businesses.confirm"));
        p.linkAction = GovBusinessesUtil.upper(GovBusinessesUtil.tr("gui.gov.businesses.link_button"));
        p.unlinkAction = GovBusinessesUtil.upper(GovBusinessesUtil.tr("gui.gov.businesses.link_unlink"));
        p.setRateAction = GovBusinessesUtil.upper(GovBusinessesUtil.tr("gui.gov.businesses.payroll_set"));
        p.recordAction = GovBusinessesUtil.upper(GovBusinessesUtil.tr("gui.gov.businesses.payroll_record"));
        p.renameAction = GovBusinessesUtil.upper(GovBusinessesUtil.tr("gui.gov.businesses.rename_submit"));
        p.payrollRunAction = GovBusinessesUtil.upper(GovBusinessesUtil.tr("gui.gov.businesses.payroll_run_submit"));
        p.positionCreateAction = GovBusinessesUtil.upper(GovBusinessesUtil.tr("gui.gov.businesses.position_create"));
        p.positionDeleteAction = GovBusinessesUtil.upper(GovBusinessesUtil.tr("gui.gov.businesses.position_delete"));
        p.positionMoveAction = GovBusinessesUtil.upper(GovBusinessesUtil.tr("gui.gov.businesses.position_move"));
        p.offerSendAction = GovBusinessesUtil.upper(GovBusinessesUtil.tr("gui.gov.businesses.offer_send"));
        p.offerRespondAction = GovBusinessesUtil.upper(GovBusinessesUtil.tr("gui.gov.businesses.offer_respond"));
        p.contractEndAction = GovBusinessesUtil.upper(GovBusinessesUtil.tr("gui.gov.businesses.contract_end"));
        p.contractPromoteAction = GovBusinessesUtil.upper(GovBusinessesUtil.tr("gui.gov.businesses.contract_promote"));
        p.complaintFileAction = GovBusinessesUtil.upper(GovBusinessesUtil.tr("gui.gov.businesses.complaint_file"));
        p.closeRequestAction = GovBusinessesUtil.upper(GovBusinessesUtil.tr("gui.gov.businesses.close_request"));
        p.closeApproveAction = GovBusinessesUtil.upper(GovBusinessesUtil.tr("gui.gov.businesses.close_approve"));
        p.closeRejectAction = GovBusinessesUtil.upper(GovBusinessesUtil.tr("gui.gov.businesses.close_reject"));
        p.cancelAction = GovBusinessesUtil.upper(GovBusinessesUtil.tr("gui.gov.businesses.cancel"));
        p.refreshAction = GovBusinessesUtil.upper(GovBusinessesUtil.tr("gui.gov.refresh"));
        p.previousAction = GovBusinessesUtil.upper(GovBusinessesUtil.tr("gui.gov.previous"));
        p.nextAction = GovBusinessesUtil.upper(GovBusinessesUtil.tr("gui.gov.next"));
        p.addStaffAction = GovBusinessesUtil.upper(GovBusinessesUtil.tr("gui.gov.businesses.staff.add"));
        p.linkStaffAction = GovBusinessesUtil.upper(GovBusinessesUtil.tr("gui.gov.businesses.link_button"));

        p.loadingText = GovBusinessesUtil.tr("gui.gov.businesses.loading");
        p.errorText = GovBusinessesUtil.tr("gui.gov.businesses.error");
        p.detailErrorTitle = GovBusinessesUtil.tr("gui.gov.businesses.detail_error");
        p.noSelectionText = GovBusinessesUtil.tr("gui.gov.businesses.no_selection");
        p.noBusinessText = GovBusinessesUtil.tr("gui.gov.businesses.no_business");
        p.noStaffText = GovBusinessesUtil.tr("gui.gov.businesses.staff.none");
        p.noSuspensionText = GovBusinessesUtil.tr("gui.gov.businesses.no_suspension");
        p.noBalanceText = GovBusinessesUtil.tr("gui.gov.businesses.balance_none");
        p.displayOnlyText = GovBusinessesUtil.tr("gui.gov.businesses.display_only");
        p.emptyAllText = GovBusinessesUtil.tr("gui.gov.businesses.empty.all");
        p.emptyFilterText = GovBusinessesUtil.tr("gui.gov.businesses.empty.filter");
        p.nameRequiredText = GovBusinessesUtil.tr("gui.gov.businesses.name_required");
        p.reasonRequiredText = GovBusinessesUtil.tr("gui.gov.businesses.reason_required");
        p.rateRequiredText = GovBusinessesUtil.tr("gui.gov.businesses.rate_required");
        p.amountRequiredText = GovBusinessesUtil.tr("gui.gov.businesses.amount_required");
        p.amountInvalidText = GovBusinessesUtil.tr("gui.gov.businesses.amount_invalid");
        p.rateInvalidText = GovBusinessesUtil.tr("gui.gov.businesses.rate_invalid");
        p.transferRequiredText = GovBusinessesUtil.tr("gui.gov.businesses.transfer_required");
        p.staffRequiredText = GovBusinessesUtil.tr("gui.gov.businesses.employee_required");
        p.linkRequiredText = GovBusinessesUtil.tr("gui.gov.businesses.link_required");
        p.armConfirmText = GovBusinessesUtil.tr("gui.gov.businesses.arm_confirm");
        p.retryText = GovBusinessesUtil.upper(GovBusinessesUtil.tr("gui.gov.card.retry"));

        for (int index = 0; index < FILTER_IDS.length; index++) {
            p.filterLabels[index] = GovBusinessesUtil.upper(
                    GovBusinessesUtil.tr("gui.gov.businesses.filter." + FILTER_IDS[index]));
        }
        for (int index = 0; index < TYPE_IDS.length; index++) {
            p.typeLabels[index] = GovBusinessesUtil.tr("gui.gov.businesses.type." + TYPE_IDS[index]);
        }
        for (int index = 0; index < ROLE_IDS.length; index++) {
            p.roleLabels[index] = GovBusinessesUtil.tr("gui.gov.businesses.role." + ROLE_IDS[index]);
        }
    }

}
