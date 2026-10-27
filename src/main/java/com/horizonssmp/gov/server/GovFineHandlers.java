package com.horizonssmp.gov.server;


import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.horizonssmp.gov.Gov;
import com.horizonssmp.gov.common.ActionResult;
import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.FineKeys;
import com.horizonssmp.gov.common.GovFields;
import com.horizonssmp.gov.common.records.FineRecord;
import com.horizonssmp.gov.common.records.PermitRecord;
import com.horizonssmp.gov.config.GovConfig;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import static com.horizonssmp.gov.server.GovHandlerSupport.*;

public final class GovFineHandlers {

    private GovFineHandlers() {
    }

    // --------------------------------------------------------------- fines: reads

    /**
     * {@code FINE_LIST}: one page of fines.
     *
     * <p>Without {@code fine.view.all} (or the reception {@code fine.waive}) the service scopes the
     * answer to the actor's own record, whatever target the payload names. The online list is
     * included for the issue picker, capped and with its true count.
     */
    static ActionResult fineList(final GovActionDispatcher.ActionContext context) {
        UUID targetFilter = null;
        if (FineService.canViewAll(context.capabilities())) {
            final String rawTarget = context.payload(GovTargets.KEY_TARGET).strip();
            if (!rawTarget.isEmpty() && !FineKeys.FILTER_ALL.equalsIgnoreCase(rawTarget)) {
                final GovTargets.Resolved target =
                        GovTargets.resolve(context.actor().getServer(), context.data(), rawTarget);
                if (target == null) {
                    return ActionResult.failure(context.request().reqId(), ErrorCode.BAD_TARGET);
                }
                targetFilter = target.uuid();
            }
        }

        final List<String> names;
        if (VehicleService.canViewAll(context.capabilities())) {
            names = onlineNames(context.actor().getServer());
        } else {
            names = List.of();
        }

        final Map<String, String> fields = FineService.listFields(
                context.data(),
                fineActor(context),
                context.capabilities(),
                context.payload(FineKeys.KEY_FILTER),
                targetFilter,
                parsePage(context.payload(FineKeys.KEY_PAGE)),
                names);
        return ActionResult.success(context.request().reqId(), GovFields.encode(fields));
    }

    /** {@code FINE_INFO}: one fine in full, for its target or a viewer who may read all. */
    static ActionResult fineInfo(final GovActionDispatcher.ActionContext context) {
        final String raw = context.payload(FineKeys.KEY_FINE_ID);
        final FineRecord fine = FineService.visibleFine(
                context.data(), context.actor().getUUID(), context.capabilities(), raw);
        if (fine == null) {
            return ActionResult.failure(
                    context.request().reqId(), ErrorCode.BAD_FINE,
                    FineService.noSuchFineMessage(raw));
        }
        final Map<String, String> fields = FineService.detailFields(
                context.data(), fine, context.capabilities(), context.actor().getUUID());
        return ActionResult.success(context.request().reqId(), GovFields.encode(fields));
    }

    // ----------------------------------------------------------- fines: mutations

    /**
     * {@code FINE_ISSUE}: issues a fine against the resolved person and tells them in chat.
     *
     * <p>The message is a plain chat line, not a {@code GovNotify} push: the notification channel
     * is its own milestone, and a player who reads the fine now is better served than by a wire
     * format nobody drains yet.
     */
    static ActionResult fineIssue(final GovActionDispatcher.ActionContext context) {
        final GovTargets.Resolved target = context.target();
        if (target == null) {
            return ActionResult.failure(context.request().reqId(), ErrorCode.BAD_TARGET);
        }
        final FineService.Outcome outcome = FineService.issue(
                context.data(),
                fineActor(context),
                target.uuid(),
                target.name(),
                context.payload(FineKeys.KEY_BAND),
                context.payload(FineKeys.KEY_ARTICLE),
                context.payload(FineKeys.KEY_RESTITUTION),
                context.payload(FineKeys.KEY_VICTIM),
                context.payload(FineKeys.KEY_EXILE_DAYS),
                context.payload(FineKeys.KEY_REASON),
                context.now());
        if (!outcome.ok()) {
            return toResult(context, outcome);
        }

        final FineRecord fine = context.data().fine(outcome.fineId());
        if (target.online() && fine != null) {
            final String actorName = Sanitizer.name(context.actor().getName().getString());
            final StringBuilder message = new StringBuilder(actorName)
                    .append(" issued you a ").append(fine.band().name())
                    .append(" fine of ").append(fine.amount().count()).append(" gold ingots");
            if (fine.restitution().count() > 0L) {
                message.append(" plus ").append(fine.restitution().count())
                        .append(" restitution to ").append(fine.victim());
            }
            message.append(" (fine ").append(fine.id()).append("). Reason: ").append(fine.reason());
            if (fine.exileOrdered()) {
                message.append(" Exile order: ").append(fine.exileDays()).append(" in-game days.");
            }
            message.append(" Pay at the ").append(GovConfig.paymentPoint()).append('.');
            target.player().sendSystemMessage(Component.literal(message.toString())
                    .withStyle(switch (fine.band()) {
                        case AGGRAVATED -> ChatFormatting.RED;
                        case FELONY -> ChatFormatting.GOLD;
                        default -> ChatFormatting.YELLOW;
                    }));
        }
        return toResult(context, outcome);
    }

    /** {@code FINE_PAY}: pays one of the actor's own fines in physical gold at reception. */
    static ActionResult finePay(final GovActionDispatcher.ActionContext context) {
        final GovTargets.Resolved target = context.target();
        if (target == null) {
            return ActionResult.failure(context.request().reqId(), ErrorCode.BAD_TARGET);
        }
        if (!target.uuid().equals(context.actor().getUUID())) {
            return ActionResult.failure(
                    context.request().reqId(), ErrorCode.BAD_TARGET,
                    "You can only pay your own fines.");
        }
        final FineService.Outcome outcome = FineService.paySelf(
                context.data(),
                fineActor(context),
                context.payload(FineKeys.KEY_FINE_ID),
                FineGateway.live(context.actor().getServer()),
                FineService.configuredReception(),
                context.now());
        return toResult(context, outcome);
    }

    /** {@code FINE_COLLECT}: accepts payment for the resolved payer's fine at reception. */
    static ActionResult fineCollect(final GovActionDispatcher.ActionContext context) {
        final GovTargets.Resolved target = context.target();
        if (target == null) {
            return ActionResult.failure(context.request().reqId(), ErrorCode.BAD_TARGET);
        }
        final FineService.Outcome outcome = FineService.collect(
                context.data(),
                fineActor(context),
                context.payload(FineKeys.KEY_FINE_ID),
                target.uuid(),
                target.name(),
                FineGateway.live(context.actor().getServer()),
                FineService.configuredReception(),
                context.now());
        if (outcome.ok() && target.online()) {
            final String collector = Sanitizer.name(context.actor().getName().getString());
            target.player().sendSystemMessage(Component.literal(
                            collector + " accepted your fine payment: " + outcome.message())
                    .withStyle(ChatFormatting.GRAY));
        }
        return toResult(context, outcome);
    }

    /**
     * {@code FINE_WAIVE}: clears the resolved person's unpaid fine with a recorded reason.
     *
     * <p>The dispatcher resolved the person target from the payload, so the handler verifies it is
     * the person the fine names. That keeps the audit line attached to the right player and refuses
     * a request that tries to waive one person's fine under another person's name.
     */
    static ActionResult fineWaive(final GovActionDispatcher.ActionContext context) {
        final GovTargets.Resolved target = context.target();
        if (target == null) {
            return ActionResult.failure(context.request().reqId(), ErrorCode.BAD_TARGET);
        }
        final String raw = context.payload(FineKeys.KEY_FINE_ID);
        final FineRecord fine = FineService.find(context.data(), raw);
        if (fine == null) {
            return ActionResult.failure(
                    context.request().reqId(), ErrorCode.BAD_FINE,
                    FineService.noSuchFineMessage(raw));
        }
        if (!target.uuid().equals(fine.targetUuid())) {
            return ActionResult.failure(
                    context.request().reqId(), ErrorCode.BAD_TARGET,
                    "That is not the person this fine names.");
        }
        final FineService.Outcome outcome = FineService.waive(
                context.data(), fineActor(context), raw,
                context.payload(FineKeys.KEY_REASON), context.now());
        if (outcome.ok() && target.online()) {
            final String actorName = Sanitizer.name(context.actor().getName().getString());
            target.player().sendSystemMessage(Component.literal(
                            actorName + " waived fine " + fine.id() + ": "
                                    + Sanitizer.text(context.payload(FineKeys.KEY_REASON)))
                    .withStyle(ChatFormatting.GRAY));
        }
        return toResult(context, outcome);
    }


}
