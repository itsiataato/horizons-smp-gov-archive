package com.horizonssmp.gov.server;


import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.horizonssmp.gov.Gov;
import com.horizonssmp.gov.common.ActionResult;
import com.horizonssmp.gov.common.BusinessKeys;
import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.GovCapability;
import com.horizonssmp.gov.common.GovFields;
import com.horizonssmp.gov.common.records.BusinessRecord;
import com.horizonssmp.gov.common.records.PermitRecord;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import static com.horizonssmp.gov.server.GovHandlerSupport.*;

public final class GovBusinessGovernanceHandlers {

    private GovBusinessGovernanceHandlers() {
    }

    /** {@code BIZ_COMPLAINT_LIST}: complaints for a business, or the viewer's own. */
    static ActionResult businessComplaintList(
            final GovActionDispatcher.ActionContext context) {
        final String rawBusiness = context.payload(BusinessKeys.KEY_BUSINESS);
        if (!rawBusiness.isBlank()) {
            final BusinessRecord business = BusinessService.find(context.data(), rawBusiness);
            if (business == null) {
                return ActionResult.failure(
                        context.request().reqId(), ErrorCode.BAD_BUSINESS,
                        BusinessService.noSuchBusinessMessage(rawBusiness));
            }
            if (!BusinessEmploymentService.canView(
                    business, employmentActor(context), context.capabilities())) {
                return ActionResult.failure(
                        context.request().reqId(), ErrorCode.FORBIDDEN,
                        "You can read only your own complaints or those of your business.");
            }
        }
        return ActionResult.success(context.request().reqId(), GovFields.encode(
                BusinessEmploymentService.complaintFields(
                        context.data(), employmentActor(context), rawBusiness,
                        parsePage(context.payload(BusinessKeys.KEY_PAGE)))));
    }

    /** {@code BIZ_COMPLAINT_FILE}: CEO-only, resolved employee, 24-hour cooldown. */
    static ActionResult businessComplaintFile(
            final GovActionDispatcher.ActionContext context) {
        final String rawEmployee = context.payload(BusinessKeys.KEY_EMPLOYEE).isBlank()
                ? context.payload(GovTargets.KEY_TARGET)
                : context.payload(BusinessKeys.KEY_EMPLOYEE);
        final GovTargets.Resolved target = GovTargets.resolve(
                context.actor().getServer(), context.data(), rawEmployee);
        if (target == null) {
            return ActionResult.failure(context.request().reqId(), ErrorCode.BAD_TARGET);
        }
        final BusinessEmploymentService.Outcome outcome =
                BusinessEmploymentService.fileComplaint(
                        context.data(), employmentActor(context),
                        context.payload(BusinessKeys.KEY_BUSINESS),
                        target.uuid(), target.name(),
                        context.payload(BusinessKeys.KEY_REASON), context.now());
        if (outcome.ok() && target.online()) {
            target.player().sendSystemMessage(Component.literal(outcome.message())
                    .withStyle(ChatFormatting.GRAY));
        }
        return toResult(context, outcome);
    }

    /** {@code BIZ_CLOSE_REQUEST}: owner or government, never changes the status. */
    static ActionResult businessCloseRequest(
            final GovActionDispatcher.ActionContext context) {
        final String reason = context.payload(BusinessKeys.KEY_CLOSE_REASON).isBlank()
                ? context.payload(BusinessKeys.KEY_REASON)
                : context.payload(BusinessKeys.KEY_CLOSE_REASON);
        final BusinessEmploymentService.Outcome outcome =
                BusinessEmploymentService.requestClose(
                        context.data(), employmentActor(context),
                        context.payload(BusinessKeys.KEY_BUSINESS), reason, context.now());
        return toResult(context, outcome);
    }

    /** {@code BIZ_CLOSE_APPROVE}: executive decision; status CLOSED, records retained. */
    static ActionResult businessCloseApprove(
            final GovActionDispatcher.ActionContext context) {
        final String decision = context.payload(BusinessKeys.KEY_DECISION).strip()
                .toLowerCase(Locale.ROOT);
        if (!decision.isEmpty() && !"approve".equals(decision) && !"approved".equals(decision)) {
            return ActionResult.failure(
                    context.request().reqId(), ErrorCode.BAD_PAYLOAD,
                    "This action approves a close; the decision must be 'approve' or empty.");
        }
        final String reason = context.payload(BusinessKeys.KEY_CLOSE_REASON).isBlank()
                ? context.payload(BusinessKeys.KEY_REASON)
                : context.payload(BusinessKeys.KEY_CLOSE_REASON);
        return toResult(context, BusinessEmploymentService.approveClose(
                context.data(), employmentActor(context),
                context.payload(BusinessKeys.KEY_BUSINESS), reason, context.now()));
    }

    /** {@code BIZ_CLOSE_REJECT}: executive decision; leaves the business trading. */
    static ActionResult businessCloseReject(
            final GovActionDispatcher.ActionContext context) {
        final String decision = context.payload(BusinessKeys.KEY_DECISION).strip()
                .toLowerCase(Locale.ROOT);
        if (!decision.isEmpty() && !"reject".equals(decision) && !"rejected".equals(decision)) {
            return ActionResult.failure(
                    context.request().reqId(), ErrorCode.BAD_PAYLOAD,
                    "This action rejects a close; the decision must be 'reject' or empty.");
        }
        final String reason = context.payload(BusinessKeys.KEY_CLOSE_REASON).isBlank()
                ? context.payload(BusinessKeys.KEY_REASON)
                : context.payload(BusinessKeys.KEY_CLOSE_REASON);
        return toResult(context, BusinessEmploymentService.rejectClose(
                context.data(), employmentActor(context),
                context.payload(BusinessKeys.KEY_BUSINESS), reason, context.now()));
    }

    /**
     * {@code HISTORY_LIST}: one page of the actor's own history, or another person's with
     * {@code player.lookup}. The optional target is resolved server-side, exactly as a note target
     * is; an absent target means the actor.
     */
    static ActionResult historyList(final GovActionDispatcher.ActionContext context) {
        UUID targetUuid = context.actor().getUUID();
        final String rawTarget = context.payload(GovTargets.KEY_TARGET).strip();
        if (!rawTarget.isEmpty()) {
            final GovTargets.Resolved target = GovTargets.resolve(
                    context.actor().getServer(), context.data(), rawTarget);
            if (target == null) {
                return ActionResult.failure(context.request().reqId(), ErrorCode.BAD_TARGET);
            }
            targetUuid = target.uuid();
        }
        if (!HistoryService.canRead(
                context.capabilities(), context.actor().getUUID(), targetUuid)) {
            return ActionResult.failure(
                    context.request().reqId(), ErrorCode.FORBIDDEN,
                    "You may read your own history, or another person's with "
                            + GovCapability.PLAYER_LOOKUP.id() + ".");
        }
        return ActionResult.success(context.request().reqId(), GovFields.encode(
                HistoryService.listFields(
                        context.data(), targetUuid,
                        parsePage(context.payload(BusinessKeys.KEY_PAGE)))));
    }


}
