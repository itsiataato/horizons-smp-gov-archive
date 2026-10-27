package com.horizonssmp.gov.server.economy;

import com.horizonssmp.gov.common.OpCode;
import com.horizonssmp.gov.server.GovActionDispatcher;

/**
 * The network handlers of the economy group.
 *
 * <p>Every handler is a thin adapter: it parses the payload, resolves an account reference where
 * the operation names one, and calls the service that owns the rule. The services audit their own
 * mutations, so {@link OpCode#dispatcherAudited()} excludes this group and no action is recorded
 * twice. The four reads return their service field block as the result delta; a mutation returns
 * the service's neutral one-line message because a screen re-reads the tab after a change.
 *
 * <p>Account references are resolved server-side. {@code me} becomes the actor's player account,
 * {@code gov}/{@code government} and {@code police} become the two singleton accounts, and every
 * other spelling is a player name or {@code player:<name>} resolved through
 * {@link com.horizonssmp.gov.server.GovTargets}; {@code business:<id>} is passed through for the
 * service to validate. An unresolved reference is a
 * {@link com.horizonssmp.gov.common.ErrorCode#BAD_TARGET} refusal.
 *
 * <p>The store catalogue is a startup snapshot that scans the recipe manager, so it is loaded once
 * per server and cached.
 *
 * <p>The handler implementations live in {@link EconomyBankHandlers}, {@link EconomyPayrollHandlers},
 * {@link EconomyStoreHandlers} and {@link EconomyContractHandlers}; shared state and helpers live in
 * {@link EconomyHandlerSupport}.
 */
public final class EconomyHandlers {

    private EconomyHandlers() {
    }

    /**
     * Registers the seventeen economy handlers.
     *
     * <p>Called once from {@code GovHandlers.registerAll()}; the dispatcher's registry replaces an
     * existing entry, so a second call is harmless.
     */
    public static synchronized void register() {
        GovActionDispatcher.register(OpCode.BANK_BALANCE, EconomyBankHandlers::bankBalance);
        GovActionDispatcher.register(OpCode.BANK_HISTORY, EconomyBankHandlers::bankHistory);
        GovActionDispatcher.register(OpCode.BANK_DEPOSIT, EconomyBankHandlers::bankDeposit);
        GovActionDispatcher.register(OpCode.BANK_WITHDRAW, EconomyBankHandlers::bankWithdraw);
        GovActionDispatcher.register(OpCode.BANK_TRANSFER, EconomyBankHandlers::bankTransfer);
        GovActionDispatcher.register(OpCode.PAY_PLAYER, EconomyBankHandlers::payPlayer);
        GovActionDispatcher.register(OpCode.PAYROLL_RUN, EconomyPayrollHandlers::payrollRun);
        GovActionDispatcher.register(OpCode.STORE_LIST, EconomyStoreHandlers::storeList);
        GovActionDispatcher.register(OpCode.STORE_BUY, EconomyStoreHandlers::storeBuy);
        GovActionDispatcher.register(OpCode.CONTRACT_LIST, EconomyContractHandlers::contractList);
        GovActionDispatcher.register(OpCode.CONTRACT_POST, EconomyContractHandlers::contractPost);
        GovActionDispatcher.register(OpCode.CONTRACT_ACCEPT, EconomyContractHandlers::contractAccept);
        GovActionDispatcher.register(OpCode.CONTRACT_SUBMIT, EconomyContractHandlers::contractSubmit);
        GovActionDispatcher.register(OpCode.CONTRACT_APPROVE, EconomyContractHandlers::contractApprove);
        GovActionDispatcher.register(OpCode.CONTRACT_VOID, EconomyContractHandlers::contractVoid);
        GovActionDispatcher.register(OpCode.CONTRACT_CREDIT, EconomyBankHandlers::contractCredit);
        GovActionDispatcher.register(OpCode.BANK_CREDIT, EconomyBankHandlers::bankCredit);
    }
}