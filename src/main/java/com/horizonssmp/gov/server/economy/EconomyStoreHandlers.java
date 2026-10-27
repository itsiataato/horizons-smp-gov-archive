package com.horizonssmp.gov.server.economy;

import java.util.OptionalInt;

import com.horizonssmp.gov.common.ActionResult;
import com.horizonssmp.gov.common.EconomyKeys;
import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.server.GovActionDispatcher;

/**
 * Economy handlers for the government store.
 */
public final class EconomyStoreHandlers {

    private EconomyStoreHandlers() {
    }

    static ActionResult storeList(final GovActionDispatcher.ActionContext context) {
        return EconomyHandlerSupport.read(context, EconomyHandlerSupport.store(context).list(context.actor(), EconomyHandlerSupport.page(context)));
    }

    static ActionResult storeBuy(final GovActionDispatcher.ActionContext context) {
        final OptionalInt stacks = EconomyHandlerSupport.whole(context.payload(EconomyKeys.KEY_STACKS));
        if (stacks.isEmpty()) {
            return ActionResult.failure(
                    context.request().reqId(), ErrorCode.BAD_PAYLOAD, EconomyHandlerSupport.BAD_STACKS);
        }
        return EconomyHandlerSupport.write(context, EconomyHandlerSupport.store(context).buy(
                context.actor(),
                context.payload(EconomyKeys.KEY_ITEM),
                stacks.getAsInt(),
                EconomyHandlerSupport.nonce(context)));
    }
}