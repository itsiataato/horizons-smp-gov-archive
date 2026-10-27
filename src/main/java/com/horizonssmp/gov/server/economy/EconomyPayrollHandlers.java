package com.horizonssmp.gov.server.economy;

import com.horizonssmp.gov.common.ActionResult;
import com.horizonssmp.gov.common.EconomyKeys;
import com.horizonssmp.gov.server.GovActionDispatcher;

/**
 * Economy handlers for payroll runs.
 */
public final class EconomyPayrollHandlers {

    private EconomyPayrollHandlers() {
    }

    static ActionResult payrollRun(final GovActionDispatcher.ActionContext context) {
        final ActionResult refused = EconomyHandlerSupport.consumeNonce(context);
        if (refused != null) {
            return refused;
        }
        return EconomyHandlerSupport.write(context, EconomyHandlerSupport.payroll(context).run(
                context.actor(),
                context.payload(EconomyKeys.KEY_SCOPE),
                context.payload(EconomyKeys.KEY_REASON),
                context.now()));
    }
}