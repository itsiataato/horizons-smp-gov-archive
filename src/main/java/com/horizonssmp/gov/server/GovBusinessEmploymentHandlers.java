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
import com.horizonssmp.gov.common.GovFields;
import com.horizonssmp.gov.common.records.BusinessRecord;
import com.horizonssmp.gov.common.records.PermitRecord;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import static com.horizonssmp.gov.server.GovHandlerSupport.*;

public final class GovBusinessEmploymentHandlers {

    private GovBusinessEmploymentHandlers() {
    }

    // ------------------------------------------- business employment (M12)

    /** {@code BIZ_RENAME}: owner or a government business.manage holder. */
    static ActionResult businessRename(final GovActionDispatcher.ActionContext context) {
        final BusinessEmploymentService.Outcome outcome = BusinessEmploymentService.rename(
                context.data(), employmentActor(context),
                context.payload(BusinessKeys.KEY_BUSINESS),
                context.payload(BusinessKeys.KEY_NAME), context.now());
        return toResult(context, outcome);
    }

    /** {@code BIZ_POSITION_LIST}: the position tree with each row's authority for this viewer. */
    static ActionResult businessPositionList(
            final GovActionDispatcher.ActionContext context) {
        final BusinessRecord business = BusinessService.find(
                context.data(), context.payload(BusinessKeys.KEY_BUSINESS));
        if (business == null) {
            return ActionResult.failure(
                    context.request().reqId(), ErrorCode.BAD_BUSINESS,
                    BusinessService.noSuchBusinessMessage(
                            context.payload(BusinessKeys.KEY_BUSINESS)));
        }
        if (!BusinessEmploymentService.canView(
                business, employmentActor(context), context.capabilities())) {
            return ActionResult.failure(
                    context.request().reqId(), ErrorCode.FORBIDDEN,
                    "You can read only the positions of your own business or workplace.");
        }
        return ActionResult.success(context.request().reqId(), GovFields.encode(
                BusinessEmploymentService.positionFields(
                        context.data(), business, employmentActor(context),
                        context.capabilities(), context.now(),
                        parsePage(context.payload(BusinessKeys.KEY_PAGE)))));
    }

    /** {@code BIZ_POSITION_CREATE}: creates a position strictly below the actor. */
    static ActionResult businessPositionCreate(
            final GovActionDispatcher.ActionContext context) {
        final BusinessEmploymentService.Outcome outcome =
                BusinessEmploymentService.createPosition(
                        context.data(), employmentActor(context),
                        context.payload(BusinessKeys.KEY_BUSINESS),
                        context.payload(BusinessKeys.KEY_TITLE),
                        context.payload(BusinessKeys.KEY_PARENT),
                        context.payload(BusinessKeys.KEY_WAGE),
                        context.payload(BusinessKeys.KEY_PERMISSIONS),
                        context.payload(BusinessKeys.KEY_ORDER),
                        context.now());
        return toResult(context, outcome);
    }

    /** {@code BIZ_POSITION_EDIT}: partial edit; an absent key is left unchanged. */
    static ActionResult businessPositionEdit(
            final GovActionDispatcher.ActionContext context) {
        final BusinessEmploymentService.Outcome outcome =
                BusinessEmploymentService.editPosition(
                        context.data(), employmentActor(context),
                        context.payload(BusinessKeys.KEY_BUSINESS),
                        context.payload(BusinessKeys.KEY_POSITION),
                        optionalValue(context, BusinessKeys.KEY_TITLE),
                        optionalValue(context, BusinessKeys.KEY_WAGE),
                        optionalValue(context, BusinessKeys.KEY_PERMISSIONS),
                        optionalValue(context, BusinessKeys.KEY_ORDER),
                        context.now());
        return toResult(context, outcome);
    }

    /** {@code BIZ_POSITION_DELETE}: refuses a position named by a contract or child. */
    static ActionResult businessPositionDelete(
            final GovActionDispatcher.ActionContext context) {
        final BusinessEmploymentService.Outcome outcome =
                BusinessEmploymentService.deletePosition(
                        context.data(), employmentActor(context),
                        context.payload(BusinessKeys.KEY_BUSINESS),
                        context.payload(BusinessKeys.KEY_POSITION),
                        context.now());
        return toResult(context, outcome);
    }

    /** {@code BIZ_POSITION_MOVE}: moves a position and refuses a cycle. */
    static ActionResult businessPositionMove(
            final GovActionDispatcher.ActionContext context) {
        final BusinessEmploymentService.Outcome outcome =
                BusinessEmploymentService.movePosition(
                        context.data(), employmentActor(context),
                        context.payload(BusinessKeys.KEY_BUSINESS),
                        context.payload(BusinessKeys.KEY_POSITION),
                        context.payload(BusinessKeys.KEY_PARENT),
                        context.now());
        return toResult(context, outcome);
    }

    /** {@code BIZ_OFFER_LIST}: pending offers for a business or for the viewer. */
    static ActionResult businessOfferList(final GovActionDispatcher.ActionContext context) {
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
                        "You can read only your own offers or the offers of your business.");
            }
        }
        UUID employeeFilter = null;
        final String rawEmployee = context.payload(BusinessKeys.KEY_EMPLOYEE).strip();
        if (!rawEmployee.isEmpty()) {
            final GovTargets.Resolved resolved = GovTargets.resolve(
                    context.actor().getServer(), context.data(), rawEmployee);
            if (resolved == null) {
                return ActionResult.failure(context.request().reqId(), ErrorCode.BAD_TARGET);
            }
            employeeFilter = resolved.uuid();
        }
        return ActionResult.success(context.request().reqId(), GovFields.encode(
                BusinessEmploymentService.offerFields(
                        context.data(), employmentActor(context), rawBusiness, employeeFilter,
                        parsePage(context.payload(BusinessKeys.KEY_PAGE)))));
    }

    /** {@code BIZ_OFFER_SEND}: sends a written offer to the resolved person. */
    static ActionResult businessOfferSend(final GovActionDispatcher.ActionContext context) {
        final GovTargets.Resolved target = context.target();
        if (target == null) {
            return ActionResult.failure(context.request().reqId(), ErrorCode.BAD_TARGET);
        }
        final BusinessEmploymentService.Outcome outcome =
                BusinessEmploymentService.sendOffer(
                        context.data(), employmentActor(context),
                        context.payload(BusinessKeys.KEY_BUSINESS),
                        target.uuid(), target.name(),
                        context.payload(BusinessKeys.KEY_POSITION),
                        context.payload(BusinessKeys.KEY_WAGE),
                        context.payload(BusinessKeys.KEY_ONE_TIME),
                        context.now());
        if (outcome.ok() && target.online()) {
            target.player().sendSystemMessage(Component.literal(outcome.message())
                    .withStyle(ChatFormatting.GRAY));
        }
        return toResult(context, outcome);
    }

    /** {@code BIZ_OFFER_RESPOND}: accepts or declines the actor's own pending offer. */
    static ActionResult businessOfferRespond(
            final GovActionDispatcher.ActionContext context) {
        final String decision = context.payload(BusinessKeys.KEY_DECISION).strip()
                .toLowerCase(Locale.ROOT);
        final boolean accept;
        if ("accept".equals(decision) || "accepted".equals(decision) || "true".equals(decision)) {
            accept = true;
        } else if ("decline".equals(decision) || "declined".equals(decision)
                || "false".equals(decision)) {
            accept = false;
        } else {
            return ActionResult.failure(
                    context.request().reqId(), ErrorCode.BAD_PAYLOAD,
                    "Decision must be 'accept' or 'decline'.");
        }
        final String offer = context.payload(BusinessKeys.KEY_OFFER).isBlank()
                ? context.payload(BusinessKeys.KEY_CONTRACT)
                : context.payload(BusinessKeys.KEY_OFFER);
        final BusinessEmploymentService.Outcome outcome =
                BusinessEmploymentService.respondOffer(
                        context.data(), employmentActor(context),
                        context.payload(BusinessKeys.KEY_BUSINESS), offer, accept, context.now());
        return toResult(context, outcome);
    }

    /** {@code BIZ_CONTRACT_LIST}: contracts for a business or the viewer's own. */
    static ActionResult businessContractList(
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
                        "You can read only your own contracts or those of your business.");
            }
        }
        return ActionResult.success(context.request().reqId(), GovFields.encode(
                BusinessEmploymentService.contractFields(
                        context.data(), employmentActor(context), rawBusiness,
                        parsePage(context.payload(BusinessKeys.KEY_PAGE)))));
    }

    /** {@code BIZ_CONTRACT_END}: resign, complete, withdraw an offer or fire. */
    static ActionResult businessContractEnd(
            final GovActionDispatcher.ActionContext context) {
        final String decision = context.payload(BusinessKeys.KEY_DECISION).strip()
                .toLowerCase(Locale.ROOT);
        if (!decision.isEmpty() && !"resign".equals(decision) && !"fire".equals(decision)
                && !"complete".equals(decision) && !"withdraw".equals(decision)) {
            return ActionResult.failure(
                    context.request().reqId(), ErrorCode.BAD_PAYLOAD,
                    "Decision must be 'resign', 'fire', 'complete' or 'withdraw'.");
        }
        final String contract = context.payload(BusinessKeys.KEY_CONTRACT).isBlank()
                ? context.payload(BusinessKeys.KEY_OFFER)
                : context.payload(BusinessKeys.KEY_CONTRACT);
        final String reason = context.payload(BusinessKeys.KEY_REASON);
        final BusinessEmploymentService.Outcome outcome =
                BusinessEmploymentService.endContract(
                        context.data(), employmentActor(context),
                        context.payload(BusinessKeys.KEY_BUSINESS), contract, decision, reason,
                        context.now());
        return toResult(context, outcome);
    }

    /** {@code BIZ_CONTRACT_PROMOTE}: moves an active contract to another position. */
    static ActionResult businessContractPromote(
            final GovActionDispatcher.ActionContext context) {
        final String contract = context.payload(BusinessKeys.KEY_CONTRACT).isBlank()
                ? context.payload(BusinessKeys.KEY_OFFER)
                : context.payload(BusinessKeys.KEY_CONTRACT);
        final BusinessEmploymentService.Outcome outcome =
                BusinessEmploymentService.promoteContract(
                        context.data(), employmentActor(context),
                        context.payload(BusinessKeys.KEY_BUSINESS), contract,
                        context.payload(BusinessKeys.KEY_POSITION), context.now());
        return toResult(context, outcome);
    }

    /** {@code BIZ_PAYROLL_RUN}: records elapsed full real days at each contract's frozen wage. */
    static ActionResult businessPayrollRun(
            final GovActionDispatcher.ActionContext context) {
        final BusinessEmploymentService.Outcome outcome =
                BusinessEmploymentService.runPayroll(
                        context.data(), employmentActor(context),
                        context.payload(BusinessKeys.KEY_BUSINESS), context.now());
        return toResult(context, outcome);
    }


}
