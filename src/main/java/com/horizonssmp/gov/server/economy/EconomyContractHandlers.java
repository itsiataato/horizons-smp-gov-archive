package com.horizonssmp.gov.server.economy;

import java.util.OptionalLong;

import com.horizonssmp.gov.common.ActionResult;
import com.horizonssmp.gov.common.EconomyKeys;
import com.horizonssmp.gov.server.GovActionDispatcher;
import com.horizonssmp.gov.server.GovTargets;

/**
 * Economy handlers for government contracts.
 */
public final class EconomyContractHandlers {

    private EconomyContractHandlers() {
    }

    static ActionResult contractList(final GovActionDispatcher.ActionContext context) {
        return EconomyHandlerSupport.read(context, EconomyHandlerSupport.contracts(context).list(context.actor(), EconomyHandlerSupport.page(context)));
    }

    static ActionResult contractPost(final GovActionDispatcher.ActionContext context) {
        final ActionResult refused = EconomyHandlerSupport.consumeNonce(context);
        if (refused != null) {
            return refused;
        }
        final GovTargets.Resolved target = context.target();
        if (target == null) {
            return EconomyHandlerSupport.badTarget(context);
        }
        final OptionalLong materials = EconomyHandlerSupport.amount(context, EconomyHandlerSupport.KEY_MATERIALS);
        final OptionalLong labor = EconomyHandlerSupport.amount(context, EconomyHandlerSupport.KEY_LABOR);
        if (materials.isEmpty() || labor.isEmpty()) {
            return EconomyHandlerSupport.badAmount(context);
        }
        return EconomyHandlerSupport.write(context, EconomyHandlerSupport.contracts(context).post(
                context.actor(), target.uuid(), materials.getAsLong(), labor.getAsLong(),
                context.payload(EconomyHandlerSupport.KEY_DESCRIPTION), context.now()));
    }

    static ActionResult contractAccept(final GovActionDispatcher.ActionContext context) {
        final ActionResult refused = EconomyHandlerSupport.consumeNonce(context);
        if (refused != null) {
            return refused;
        }
        return EconomyHandlerSupport.write(context, EconomyHandlerSupport.contracts(context).accept(
                context.actor(), context.payload(EconomyKeys.KEY_CONTRACT), context.now()));
    }

    static ActionResult contractSubmit(final GovActionDispatcher.ActionContext context) {
        final ActionResult refused = EconomyHandlerSupport.consumeNonce(context);
        if (refused != null) {
            return refused;
        }
        return EconomyHandlerSupport.write(context, EconomyHandlerSupport.contracts(context).submit(
                context.actor(), context.payload(EconomyKeys.KEY_CONTRACT), context.now()));
    }

    static ActionResult contractApprove(final GovActionDispatcher.ActionContext context) {
        final ActionResult refused = EconomyHandlerSupport.consumeNonce(context);
        if (refused != null) {
            return refused;
        }
        return EconomyHandlerSupport.write(context, EconomyHandlerSupport.contracts(context).approve(
                context.actor(), context.payload(EconomyKeys.KEY_CONTRACT), context.now()));
    }

    static ActionResult contractVoid(final GovActionDispatcher.ActionContext context) {
        final ActionResult refused = EconomyHandlerSupport.consumeNonce(context);
        if (refused != null) {
            return refused;
        }
        return EconomyHandlerSupport.write(context, EconomyHandlerSupport.contracts(context).voidContract(
                context.actor(),
                context.payload(EconomyKeys.KEY_CONTRACT),
                context.payload(EconomyKeys.KEY_REASON),
                context.now()));
    }
}