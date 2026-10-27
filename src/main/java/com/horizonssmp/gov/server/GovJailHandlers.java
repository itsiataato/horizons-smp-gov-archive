package com.horizonssmp.gov.server;


import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.horizonssmp.gov.Gov;
import com.horizonssmp.gov.common.ActionResult;
import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.GovFields;
import com.horizonssmp.gov.common.JailKeys;
import com.horizonssmp.gov.common.records.DetentionRecord;
import com.horizonssmp.gov.common.records.PermitRecord;

import net.minecraft.network.chat.Component;

import static com.horizonssmp.gov.server.GovHandlerSupport.*;

public final class GovJailHandlers {

    private GovJailHandlers() {
    }

    // ------------------------------------------------------------------ jail reads

    /**
     * {@code JAIL_LIST}: one page of detentions.
     *
     * <p>The service scopes the answer: a viewer without {@code jail.commit}, {@code jail.release}
     * or {@code audit.view} sees only their own record, whatever target the payload names. The
     * online list is included for the detain picker, capped and with its true count.
     */
    static ActionResult jailList(final GovActionDispatcher.ActionContext context) {
        final boolean viewAll = DetentionService.canViewAll(context.capabilities());
        UUID targetFilter = null;
        final String rawTarget = context.payload(GovTargets.KEY_TARGET).strip();
        if (!rawTarget.isEmpty() && !JailKeys.FILTER_ALL.equalsIgnoreCase(rawTarget)) {
            if (!viewAll) {
                return ActionResult.failure(
                        context.request().reqId(), ErrorCode.FORBIDDEN,
                        "You can only read your own detention record.");
            }
            final GovTargets.Resolved target =
                    GovTargets.resolve(context.actor().getServer(), context.data(), rawTarget);
            if (target == null) {
                return ActionResult.failure(context.request().reqId(), ErrorCode.BAD_TARGET);
            }
            targetFilter = target.uuid();
        }

        final List<String> names = onlineNames(context.actor().getServer());

        final Map<String, String> fields = DetentionService.listFields(
                context.data(),
                context.actor().getUUID(),
                context.actor().getName().getString(),
                context.capabilities(),
                context.payload(JailKeys.KEY_FILTER),
                targetFilter,
                parsePage(context.payload(JailKeys.KEY_PAGE)),
                names,
                DetentionService.Policy.fromConfig(),
                context.now());
        return ActionResult.success(context.request().reqId(), GovFields.encode(fields));
    }

    /**
     * {@code JAIL_INFO}: one detention, by id or by person.
     *
     * <p>The person is resolved server-side here rather than by the dispatcher, because a history
     * row must remain readable even when its subject has since been removed from the profile cache;
     * the detention id is the stable fallback.
     */
    static ActionResult jailInfo(final GovActionDispatcher.ActionContext context) {
        final String rawId = context.payload(JailKeys.KEY_DETENTION_ID).strip();
        DetentionRecord record = null;
        String name = "";
        if (!rawId.isEmpty()) {
            record = DetentionService.visibleRecord(
                    context.data(), context.actor().getUUID(), context.capabilities(), rawId);
            if (record == null) {
                return ActionResult.failure(
                        context.request().reqId(), ErrorCode.BAD_TARGET,
                        "There is no visible detention with id '"
                                + Sanitizer.singleLine(rawId, 32) + "'.");
            }
        } else {
            final String rawTarget = context.payload(GovTargets.KEY_TARGET).strip();
            if (rawTarget.isEmpty()) {
                return ActionResult.failure(
                        context.request().reqId(), ErrorCode.BAD_PAYLOAD,
                        "Name a person or a detention id.");
            }
            // Resolve from local data only. A read must not fall through to the Mojang profile
            // cache: a modded client could otherwise block the server thread with an arbitrary
            // name, and every detention has already created a player file (tip t44).
            final GovTargets.Resolved resolved = GovTargets.resolveLocal(
                    context.actor().getServer(), context.data(), rawTarget);
            if (resolved == null) {
                return ActionResult.failure(
                        context.request().reqId(), ErrorCode.BAD_TARGET,
                        "There is no detention on record for "
                                + Sanitizer.singleLine(rawTarget, 32) + ".");
            }
            final UUID targetUuid = resolved.uuid();
            name = resolved.name();
            record = DetentionService.visibleRecord(
                    context.data(), context.actor().getUUID(), context.capabilities(),
                    targetUuid);
            if (record == null) {
                return ActionResult.failure(
                        context.request().reqId(), ErrorCode.BAD_TARGET,
                        "There is no detention on record for " + Sanitizer.name(name) + ".");
            }
        }
        return ActionResult.success(
                context.request().reqId(),
                GovFields.encode(DetentionService.detailFields(
                        context.data(), record, context.capabilities(),
                        context.actor().getUUID(), DetentionService.Policy.fromConfig(),
                        context.now())));
    }

    // ------------------------------------------------------------- jail mutations

    /** {@code JAIL_COMMIT}: records a detention under the legal-basis gate. */
    static ActionResult jailDetain(final GovActionDispatcher.ActionContext context) {
        final GovTargets.Resolved target = context.target();
        if (target == null) {
            return ActionResult.failure(context.request().reqId(), ErrorCode.BAD_TARGET);
        }
        final DetentionService.Outcome outcome = DetentionService.detain(
                context.data(),
                jailActor(context),
                target.uuid(),
                target.name(),
                context.payload(JailKeys.KEY_BASIS),
                context.payload(JailKeys.KEY_WARRANT_REF),
                context.payload(JailKeys.KEY_MINUTES),
                context.payload(JailKeys.KEY_REASON),
                context.payload(JailKeys.KEY_CONSENT_CONFIRMED),
                new JailGateway.Live(context.actor().getServer()),
                DetentionService.Policy.fromConfig(),
                context.now());
        return toResult(context, outcome);
    }

    /** {@code JAIL_RELEASE}: releases an active detention and clears cuffs. */
    static ActionResult jailRelease(final GovActionDispatcher.ActionContext context) {
        final GovTargets.Resolved target = context.target();
        if (target == null) {
            return ActionResult.failure(context.request().reqId(), ErrorCode.BAD_TARGET);
        }
        final DetentionService.Outcome outcome = DetentionService.release(
                context.data(),
                jailActor(context),
                target.uuid(),
                target.name(),
                context.payload(JailKeys.KEY_REASON),
                new JailGateway.Live(context.actor().getServer()),
                DetentionService.Policy.fromConfig(),
                context.now());
        return toResult(context, outcome);
    }

    /** {@code JAIL_EXTEND}: extends an active warrant-backed detention. */
    static ActionResult jailExtend(final GovActionDispatcher.ActionContext context) {
        final GovTargets.Resolved target = context.target();
        if (target == null) {
            return ActionResult.failure(context.request().reqId(), ErrorCode.BAD_TARGET);
        }
        final DetentionService.Outcome outcome = DetentionService.extend(
                context.data(),
                jailActor(context),
                target.uuid(),
                target.name(),
                context.payload(JailKeys.KEY_MINUTES),
                context.payload(JailKeys.KEY_REASON),
                new JailGateway.Live(context.actor().getServer()),
                DetentionService.Policy.fromConfig(),
                context.now());
        return toResult(context, outcome);
    }

    /** {@code JAIL_CELL}: places an actively detained person into a selected native cell. */
    static ActionResult jailCell(final GovActionDispatcher.ActionContext context) {
        final GovTargets.Resolved target = context.target();
        if (target == null) {
            return ActionResult.failure(context.request().reqId(), ErrorCode.BAD_TARGET);
        }
        final int cell;
        try {
            cell = Integer.parseInt(context.payload(JailKeys.KEY_CELL).trim());
        } catch (final NumberFormatException e) {
            return ActionResult.failure(
                    context.request().reqId(), ErrorCode.BAD_PAYLOAD, "cell must be a whole number");
        }
        final DetentionService.Outcome outcome = DetentionService.putInCell(
                context.data(),
                jailActor(context),
                target.uuid(),
                target.name(),
                cell,
                new JailGateway.Live(context.actor().getServer()),
                DetentionService.Policy.fromConfig(),
                context.now());
        return toResult(context, outcome);
    }

    /** {@code JAIL_CELL_CREATE}: registers a runtime jail cell from explicit coordinates. */
    static ActionResult jailCellCreate(final GovActionDispatcher.ActionContext context) {
        return GovJailCellHandlers.jailCellCreate(context);
    }

    /** {@code JAIL_CELL_DELETE}: removes a runtime jail cell by name. */
    static ActionResult jailCellDelete(final GovActionDispatcher.ActionContext context) {
        return GovJailCellHandlers.jailCellDelete(context);
    }

    /** {@code CUFF}: applies native cuffs to an actively detained player. */
    static ActionResult cuff(final GovActionDispatcher.ActionContext context) {
        final GovTargets.Resolved target = context.target();
        if (target == null) {
            return ActionResult.failure(context.request().reqId(), ErrorCode.BAD_TARGET);
        }
        final DetentionService.Outcome outcome = DetentionService.cuff(
                context.data(),
                jailActor(context),
                target.uuid(),
                target.name(),
                new JailGateway.Live(context.actor().getServer()),
                DetentionService.Policy.fromConfig(),
                context.now());
        return toResult(context, outcome);
    }

    /** {@code UNCUFF}: removes native cuffs. */
    static ActionResult uncuff(final GovActionDispatcher.ActionContext context) {
        final GovTargets.Resolved target = context.target();
        if (target == null) {
            return ActionResult.failure(context.request().reqId(), ErrorCode.BAD_TARGET);
        }
        final DetentionService.Outcome outcome = DetentionService.uncuff(
                context.data(),
                jailActor(context),
                target.uuid(),
                target.name(),
                new JailGateway.Live(context.actor().getServer()),
                DetentionService.Policy.fromConfig(),
                context.now());
        return toResult(context, outcome);
    }


}
