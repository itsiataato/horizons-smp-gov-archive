package com.horizonssmp.gov.server;


import java.util.HashSet;
import java.util.List;
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
import net.minecraft.server.level.ServerPlayer;

import static com.horizonssmp.gov.server.GovHandlerSupport.*;

public final class GovBusinessHandlers {

    private GovBusinessHandlers() {
    }

    // ------------------------------------------------------------ businesses

    /**
     * {@code BIZ_LIST}: one page of the registry.
     *
     * <p>A viewer with a business or payroll authority sees every business and may filter by
     * owner; a citizen sees only the businesses they own or work for. The online list is supplied
     * for the owner and employee pickers, capped and with its true count.
     */
    static ActionResult businessList(final GovActionDispatcher.ActionContext context) {
        UUID ownerFilter = null;
        if (BusinessService.canViewAll(context.capabilities())) {
            final String rawTarget = context.payload(GovTargets.KEY_TARGET).strip();
            if (!rawTarget.isEmpty() && !"all".equalsIgnoreCase(rawTarget)) {
                final GovTargets.Resolved target =
                        GovTargets.resolve(context.actor().getServer(), context.data(), rawTarget);
                if (target == null) {
                    return ActionResult.failure(context.request().reqId(), ErrorCode.BAD_TARGET);
                }
                ownerFilter = target.uuid();
            }
        }
        final List<String> names;
        if (VehicleService.canViewAll(context.capabilities())) {
            names = onlineNames(context.actor().getServer());
        } else {
            names = List.of();
        }
        final Map<String, String> fields = BusinessService.listFields(
                context.data(),
                context.actor().getUUID(),
                context.actor().getName().getString(),
                context.capabilities(),
                context.payload(BusinessKeys.KEY_FILTER),
                ownerFilter,
                parsePage(context.payload(BusinessKeys.KEY_PAGE)),
                names);
        return ActionResult.success(context.request().reqId(), GovFields.encode(fields));
    }

    /** {@code BIZ_INFO}: one business card, scoped to the owner, its staff and the registry. */
    static ActionResult businessInfo(final GovActionDispatcher.ActionContext context) {
        final String raw = context.payload(BusinessKeys.KEY_BUSINESS);
        final BusinessRecord business = BusinessService.find(context.data(), raw);
        if (business == null) {
            return ActionResult.failure(
                    context.request().reqId(), ErrorCode.BAD_BUSINESS,
                    BusinessService.noSuchBusinessMessage(raw));
        }
        if (!BusinessService.canViewAll(context.capabilities())
                && !BusinessService.visibleTo(
                        context.data(), context.actor().getUUID(), business.id())) {
            return ActionResult.failure(
                    context.request().reqId(), ErrorCode.FORBIDDEN,
                    "You can only read the registry entry for your own business or workplace.");
        }
        final Set<UUID> online = new HashSet<>();
        for (final ServerPlayer player : context.actor().getServer().getPlayerList().getPlayers()) {
            online.add(player.getUUID());
        }
        final Map<String, String> fields = BusinessService.detailFields(
                context.data(), business, context.capabilities(),
                context.actor().getUUID(), online);
        return ActionResult.success(context.request().reqId(), GovFields.encode(fields));
    }

    // -------------------------------------------------- businesses: mutations

    /**
     * {@code BIZ_REGISTER}: registers a business, owned by the actor unless a target owner is
     * named. The optional owner is resolved here, against the same server-side sources every
     * person target uses.
     */
    static ActionResult businessRegister(final GovActionDispatcher.ActionContext context) {
        UUID ownerUuid = null;
        String ownerName = "";
        final String rawOwner = context.payload(GovTargets.KEY_TARGET).strip();
        if (!rawOwner.isEmpty()) {
            final GovTargets.Resolved target =
                    GovTargets.resolve(context.actor().getServer(), context.data(), rawOwner);
            if (target == null) {
                return ActionResult.failure(context.request().reqId(), ErrorCode.BAD_TARGET);
            }
            ownerUuid = target.uuid();
            ownerName = target.name();
        }
        final BusinessService.Outcome outcome = BusinessService.register(
                context.data(),
                businessActor(context),
                context.payload(BusinessKeys.KEY_NAME),
                context.payload(BusinessKeys.KEY_TYPE),
                ownerUuid,
                ownerName,
                context.now());
        return toResult(context, outcome);
    }

    /** {@code BIZ_TRANSFER}: transfers a business to the resolved new owner. */
    static ActionResult businessTransfer(final GovActionDispatcher.ActionContext context) {
        final GovTargets.Resolved target = context.target();
        if (target == null) {
            return ActionResult.failure(context.request().reqId(), ErrorCode.BAD_TARGET);
        }
        final String raw = context.payload(BusinessKeys.KEY_BUSINESS);
        final BusinessRecord business = BusinessService.find(context.data(), raw);
        final BusinessService.Outcome outcome = BusinessService.transfer(
                context.data(), businessActor(context), raw,
                target.uuid(), target.name(), context.now());
        if (outcome.ok() && target.online() && business != null) {
            target.player().sendSystemMessage(Component.literal(
                            Sanitizer.name(context.actor().getName().getString())
                                    + " transferred business '" + business.name() + "' to you.")
                    .withStyle(ChatFormatting.GRAY));
        }
        return toResult(context, outcome);
    }

    /** {@code BIZ_SUSPEND}: suspends a business with a required reason. */
    static ActionResult businessSuspend(final GovActionDispatcher.ActionContext context) {
        final BusinessService.Outcome outcome = BusinessService.suspend(
                context.data(),
                businessActor(context),
                context.payload(BusinessKeys.KEY_BUSINESS),
                context.payload(BusinessKeys.KEY_REASON),
                context.now());
        return toResult(context, outcome);
    }

    /** {@code BIZ_REVOKE}: closes a business registration. The record is kept. */
    static ActionResult businessClose(final GovActionDispatcher.ActionContext context) {
        final BusinessService.Outcome outcome = BusinessService.close(
                context.data(),
                businessActor(context),
                context.payload(BusinessKeys.KEY_BUSINESS),
                context.now());
        return toResult(context, outcome);
    }

    /** {@code BIZ_DELETE}: deletes a business registration completely. */
    static ActionResult businessDelete(final GovActionDispatcher.ActionContext context) {
        final BusinessService.Outcome outcome = BusinessService.delete(
                context.data(),
                businessActor(context),
                context.payload(BusinessKeys.KEY_BUSINESS),
                context.now());
        return toResult(context, outcome);
    }

    /** {@code BIZ_EMPLOYEE_ADD}: adds the resolved person to the business staff list. */
    static ActionResult businessEmployeeAdd(final GovActionDispatcher.ActionContext context) {
        final GovTargets.Resolved target = context.target();
        if (target == null) {
            return ActionResult.failure(context.request().reqId(), ErrorCode.BAD_TARGET);
        }
        final BusinessService.Outcome outcome = BusinessService.addEmployee(
                context.data(),
                businessActor(context),
                context.payload(BusinessKeys.KEY_BUSINESS),
                target.uuid(),
                target.name(),
                context.payload(BusinessKeys.KEY_ROLE),
                context.now());
        if (outcome.ok() && target.online()) {
            target.player().sendSystemMessage(Component.literal(outcome.message())
                    .withStyle(ChatFormatting.GRAY));
        }
        return toResult(context, outcome);
    }

    /** {@code BIZ_EMPLOYEE_REMOVE}: removes the resolved person from the business staff list. */
    static ActionResult businessEmployeeRemove(
            final GovActionDispatcher.ActionContext context) {
        final GovTargets.Resolved target = context.target();
        if (target == null) {
            return ActionResult.failure(context.request().reqId(), ErrorCode.BAD_TARGET);
        }
        final BusinessService.Outcome outcome = BusinessService.removeEmployee(
                context.data(),
                businessActor(context),
                context.payload(BusinessKeys.KEY_BUSINESS),
                target.uuid());
        if (outcome.ok() && target.online()) {
            target.player().sendSystemMessage(Component.literal(outcome.message())
                    .withStyle(ChatFormatting.GRAY));
        }
        return toResult(context, outcome);
    }

    /** {@code BIZ_LINK}: links one job definition, or unlinks with an empty job id. */
    static ActionResult businessLink(final GovActionDispatcher.ActionContext context) {
        final BusinessService.Outcome outcome = BusinessService.link(
                context.data(),
                businessActor(context),
                context.payload(BusinessKeys.KEY_BUSINESS),
                context.payload(BusinessKeys.KEY_JOB));
        return toResult(context, outcome);
    }

    /** {@code BIZ_PAYROLL_SET}: sets the stated rate and payment schedule; no gold moves. */
    static ActionResult businessPayrollSet(final GovActionDispatcher.ActionContext context) {
        final BusinessService.Outcome outcome = BusinessService.setPayroll(
                context.data(),
                businessActor(context),
                context.payload(BusinessKeys.KEY_BUSINESS),
                context.payload(BusinessKeys.KEY_RATE),
                context.payload(BusinessKeys.KEY_SCHEDULE));
        return toResult(context, outcome);
    }

    /** {@code BIZ_PAYROLL_RECORD}: records a physical payment an official witnessed. */
    static ActionResult businessPayrollRecord(
            final GovActionDispatcher.ActionContext context) {
        final BusinessService.Outcome outcome = BusinessService.recordPayment(
                context.data(),
                businessActor(context),
                context.payload(BusinessKeys.KEY_BUSINESS),
                context.payload(BusinessKeys.KEY_AMOUNT),
                context.payload(BusinessKeys.KEY_NOTE),
                context.now());
        return toResult(context, outcome);
    }

    /** {@code PAYROLL_INFO}: the display-only payroll block of one business. */
    static ActionResult payrollInfo(final GovActionDispatcher.ActionContext context) {
        final String raw = context.payload(BusinessKeys.KEY_BUSINESS);
        final BusinessRecord business = BusinessService.find(context.data(), raw);
        if (business == null) {
            return ActionResult.failure(
                    context.request().reqId(), ErrorCode.BAD_BUSINESS,
                    BusinessService.noSuchBusinessMessage(raw));
        }
        return ActionResult.success(
                context.request().reqId(),
                GovFields.encode(BusinessService.payrollInfoFields(
                        context.data(), business, context.capabilities())));
    }


}
