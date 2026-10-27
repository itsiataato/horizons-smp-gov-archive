package com.horizonssmp.gov.server.economy;

import java.util.OptionalLong;
import java.util.UUID;

import com.horizonssmp.gov.common.ActionResult;
import com.horizonssmp.gov.common.EconomyKeys;
import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.GovCapability;
import com.horizonssmp.gov.common.economy.EconomyResult;
import com.horizonssmp.gov.server.GovActionDispatcher;
import com.horizonssmp.gov.server.GovTargets;

import net.minecraft.server.level.ServerPlayer;

/**
 * Economy handlers for bank accounts, payments and the console credit remedy.
 */
public final class EconomyBankHandlers {

    private EconomyBankHandlers() {
    }

    static ActionResult bankBalance(final GovActionDispatcher.ActionContext context) {
        final String account = EconomyHandlerSupport.accountRef(context, EconomyKeys.KEY_ACCOUNT);
        if (account == null) {
            return EconomyHandlerSupport.badTarget(context);
        }
        final EconomyResult result = EconomyHandlerSupport.bank(context).balance(
                context.actor().getUUID(), account,
                context.can(GovCapability.BANK_MANAGE));
        return EconomyHandlerSupport.read(context, result.with(
                EconomyKeys.KEY_CAN_CREDIT, Boolean.toString(canCredit(context))));
    }

    /**
     * Whether this actor could succeed at {@code BANK_CREDIT} or {@code CONTRACT_CREDIT}
     * right now.
     *
     * <p>True exactly when the actor holds {@code season.admin} and passes the live permission
     * level 4 check — the same two gates {@link #bankCredit} and {@link #contractCredit}
     * enforce after the dispatcher's own capability gate. This is an affordance hint for the
     * client only: a {@code "false"} here changes nothing about what the server accepts, and
     * a forged {@code "true"} still meets both server gates. Read failures stay refusals;
     * this never throws.
     */
    private static boolean canCredit(final GovActionDispatcher.ActionContext context) {
        return context.can(GovCapability.SEASON_ADMIN)
                && EconomyHandlerSupport.hasConsolePermission(context.actor());
    }

    static ActionResult bankHistory(final GovActionDispatcher.ActionContext context) {
        final String account = EconomyHandlerSupport.accountRef(context, EconomyKeys.KEY_ACCOUNT);
        if (account == null) {
            return EconomyHandlerSupport.badTarget(context);
        }
        return EconomyHandlerSupport.read(context, EconomyHandlerSupport.bank(context).history(
                context.actor().getUUID(), account, EconomyHandlerSupport.page(context),
                context.can(GovCapability.BANK_MANAGE)));
    }

    static ActionResult bankDeposit(final GovActionDispatcher.ActionContext context) {
        final OptionalLong amount = EconomyHandlerSupport.amount(context, EconomyKeys.KEY_AMOUNT);
        if (amount.isEmpty()) {
            return EconomyHandlerSupport.badAmount(context);
        }
        return EconomyHandlerSupport.write(context, EconomyHandlerSupport.bank(context).deposit(
                context.actor(), amount.getAsLong(), EconomyHandlerSupport.nonce(context)));
    }

    static ActionResult bankWithdraw(final GovActionDispatcher.ActionContext context) {
        final OptionalLong amount = EconomyHandlerSupport.amount(context, EconomyKeys.KEY_AMOUNT);
        if (amount.isEmpty()) {
            return EconomyHandlerSupport.badAmount(context);
        }
        final GovTargets.Resolved target = context.target();
        if (target == null) {
            return EconomyHandlerSupport.badTarget(context);
        }
        return EconomyHandlerSupport.write(context, EconomyHandlerSupport.bank(context).withdraw(
                context.actor(), target.player(), amount.getAsLong(), EconomyHandlerSupport.nonce(context)));
    }

    static ActionResult bankTransfer(final GovActionDispatcher.ActionContext context) {
        final String from = EconomyHandlerSupport.accountRef(context, EconomyKeys.KEY_FROM);
        final String to = EconomyHandlerSupport.accountRef(context, EconomyKeys.KEY_TO);
        if (from == null || to == null) {
            return EconomyHandlerSupport.badTarget(context);
        }
        final OptionalLong amount = EconomyHandlerSupport.amount(context, EconomyKeys.KEY_AMOUNT);
        if (amount.isEmpty()) {
            return EconomyHandlerSupport.badAmount(context);
        }
        return EconomyHandlerSupport.write(context, EconomyHandlerSupport.bank(context).transfer(
                context.actor(), from, to, amount.getAsLong(),
                context.payload(EconomyKeys.KEY_REASON), EconomyHandlerSupport.nonce(context)));
    }

    static ActionResult payPlayer(final GovActionDispatcher.ActionContext context) {
        final OptionalLong amount = EconomyHandlerSupport.amount(context, EconomyKeys.KEY_AMOUNT);
        if (amount.isEmpty()) {
            return EconomyHandlerSupport.badAmount(context);
        }
        final GovTargets.Resolved target = context.target();
        if (target == null) {
            return EconomyHandlerSupport.badTarget(context);
        }
        return EconomyHandlerSupport.write(context, EconomyHandlerSupport.bank(context).pay(
                context.actor(), target.player(), amount.getAsLong(), EconomyHandlerSupport.nonce(context)));
    }

    /**
     * {@code CONTRACT_CREDIT}: the Treasury Contracts-tab parity path for the same audited
     * crash remedy {@link #bankCredit} serves.
     *
     * <p>TASK-032 choice, kept as a gated alias rather than deleted: the dashboard already
     * ships the Contracts-tab button and the mint itself ({@code consoleCredit}) is identical
     * to the command-side remedy, so the bug was the weaker gate, not the alias. The op maps
     * to {@code season.admin} at the dispatcher and {@link #consoleCreditToOwner} re-checks
     * both the capability and live permission level 4, exactly like {@link #bankCredit}; a
     * teller holding only {@code bank.manage} is refused before any gold moves. The only
     * per-caller difference from {@link #bankCredit} is the extra {@code from}/{@code to}
     * fallbacks accepted as the person reference. Every failure is a refusal.
     */
    static ActionResult contractCredit(final GovActionDispatcher.ActionContext context) {
        return consoleCreditToOwner(context, GovTargets.KEY_TARGET,
                EconomyKeys.KEY_ACCOUNT, EconomyKeys.KEY_FROM, EconomyKeys.KEY_TO);
    }

    /**
     * {@code BANK_CREDIT}: the dashboard parity path for {@code /gov bank credit}.
     *
     * <p>Mirrors {@code EconomyBankCommands.bankCredit} exactly: permission level 4, an in-game
     * actor (structurally guaranteed — the dispatcher only runs with a player actor), a
     * server-resolved player target, a positive whole-ingot amount, and the call to
     * {@link com.horizonssmp.gov.server.economy.BankService#consoleCredit}, which writes the
     * identical system audit line. The payload carries the target as a name under
     * {@code target} (with {@code account} accepted as an alias), so this op does not require a
     * player target and resolves the person itself, exactly as {@link #contractCredit} does.
     * Every failure is a refusal; nothing here throws.
     */
    static ActionResult bankCredit(final GovActionDispatcher.ActionContext context) {
        return consoleCreditToOwner(context, GovTargets.KEY_TARGET, EconomyKeys.KEY_ACCOUNT);
    }

    /**
     * Shared console-credit body behind {@link #bankCredit} and {@link #contractCredit}.
     *
     * <p>Dual-gated exactly like the command: {@code season.admin} first, then live permission
     * level 4. The capability re-check here is defense in depth — the dispatcher already gates
     * both ops on it, but a forged client hint must never open a mint, so the handler refuses
     * {@code FORBIDDEN} before touching the actor or any service. It runs before the
     * permission read on purpose: the permission half dereferences the live player, so the
     * capability half alone decides the refusal for an ungated actor. Then a positive
     * whole-ingot amount, {@code me}-or-blank self routing, server resolution of any other
     * name, and the identical system audit line from
     * {@link com.horizonssmp.gov.server.economy.BankService#consoleCredit}. The only
     * per-caller difference is which payload keys are accepted as the person reference, in
     * priority order: {@code bankCredit} reads {@code target} then {@code account}, while
     * {@code contractCredit} additionally falls back to {@code from} then {@code to}.
     * Every failure is a refusal; nothing here throws.
     */
    private static ActionResult consoleCreditToOwner(
            final GovActionDispatcher.ActionContext context, final String... aliasKeys) {
        if (!context.can(GovCapability.SEASON_ADMIN)) {
            return ActionResult.failure(
                    context.request().reqId(), ErrorCode.FORBIDDEN,
                    "You need " + GovCapability.SEASON_ADMIN.id() + " for that action.");
        }
        if (!EconomyHandlerSupport.hasConsolePermission(context.actor())) {
            return ActionResult.failure(
                    context.request().reqId(), ErrorCode.FORBIDDEN,
                    "A console credit requires permission level " + EconomyHandlerSupport.CONSOLE_PERMISSION + ".");
        }
        final OptionalLong amount = EconomyHandlerSupport.amount(context, EconomyKeys.KEY_AMOUNT);
        if (amount.isEmpty()) {
            return EconomyHandlerSupport.badAmount(context);
        }

        final String[] candidates = new String[aliasKeys.length];
        for (int index = 0; index < aliasKeys.length; index++) {
            candidates[index] = context.payload(aliasKeys[index]);
        }
        final String raw = EconomyHandlerSupport.firstNonBlank(candidates);
        final UUID owner;
        if (raw.isEmpty() || "me".equalsIgnoreCase(raw)) {
            owner = context.actor().getUUID();
        } else {
            final GovTargets.Resolved target = GovTargets.resolve(
                    context.actor().getServer(), context.data(), raw);
            if (target == null) {
                return EconomyHandlerSupport.badTarget(context);
            }
            owner = target.uuid();
        }

        return EconomyHandlerSupport.write(context, EconomyHandlerSupport.bank(context).consoleCredit(
                context.actor(), owner, amount.getAsLong(),
                context.payload(EconomyKeys.KEY_REASON)));
    }
}