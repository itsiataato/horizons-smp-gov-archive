package com.horizonssmp.gov.server.economy;

import com.horizonssmp.gov.common.EconomyKeys;
import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.OpCode;
import com.horizonssmp.gov.common.economy.EconomyResult;
import com.horizonssmp.gov.common.records.AccountRecord;
import com.horizonssmp.gov.server.Sanitizer;

import net.minecraft.server.level.ServerPlayer;

final class BankServiceDeposit {

    static EconomyResult deposit(
            final BankService service, final ServerPlayer actor, final long gross, final String nonce) {
        final EconomyResult enabled = service.requireEnabled();
        if (enabled.failed()) {
            return enabled;
        }
        if (actor == null) {
            return EconomyResult.failure(ErrorCode.BAD_PAYLOAD, BankService.ACTOR_REQUIRED);
        }
        if (!service.regions.isBankOrTownhall(actor)) {
            return EconomyResult.failure(ErrorCode.REGION_REQUIRED);
        }
        final EconomyResult shape = checkDepositAmount(gross);
        if (shape.failed()) {
            return shape;
        }
        if (!com.horizonssmp.gov.common.economy.EconomyMath.validNonce(nonce)) {
            return EconomyResult.failure(ErrorCode.BAD_PAYLOAD, BankService.NONCE_INVALID);
        }

        final java.util.UUID actorId = actor.getUUID();
        if (service.data.economyMeta().hasNonce(actorId, nonce)) {
            return EconomyResult.failure(ErrorCode.DUPLICATE_REQUEST);
        }

        final EconomyResult token = service.accounts.takeEconomyToken(actorId, OpCode.BANK_DEPOSIT);
        if (token.failed()) {
            return token;
        }
        return service.accounts.withActorLock(actorId, () -> depositLocked(service, actor, gross, nonce));
    }

    private static EconomyResult depositLocked(
            final BankService service, final ServerPlayer actor, final long gross, final String nonce) {
        final java.util.UUID actorId = actor.getUUID();
        final String accountId = AccountService.playerAccountId(actorId);
        final long tax = service.tax(gross);
        final long net = gross - tax;
        if (net < 1L) {
            return EconomyResult.failure(ErrorCode.AMOUNT_TOO_SMALL);
        }

        final AccountRecord account = service.accounts.player(actorId);
        if (!account.acceptsCredit()) {
            return EconomyResult.failure(ErrorCode.ACCOUNT_FROZEN);
        }
        if (BankServiceHelpers.overCap(account.balance(), net)) {
            return EconomyResult.failure(ErrorCode.LIMIT_EXCEEDED);
        }
        if (tax > 0L) {
            final AccountRecord government = service.accounts.government();
            if (!government.acceptsCredit()) {
                return EconomyResult.failure(ErrorCode.ACCOUNT_FROZEN);
            }
            if (BankServiceHelpers.overCap(government.balance(), tax)) {
                return EconomyResult.failure(ErrorCode.LIMIT_EXCEEDED);
            }
        }

        if (service.vault.countGold(actor) < gross) {
            return EconomyResult.failure(ErrorCode.INSUFFICIENT_ITEMS);
        }

        final EconomyResult accepted = service.accounts.acceptNonce(actorId, nonce);
        if (accepted.failed()) {
            return accepted;
        }

        if (!service.vault.removeGold(actor, (int) gross)) {
            return EconomyResult.failure(ErrorCode.INSUFFICIENT_ITEMS);
        }

        final EconomyResult booked = service.accounts.depositCredit(
                accountId, gross, tax, BankService.TXN_DEPOSIT, actorId, BankService.DEPOSIT_REASON);
        if (booked.failed()) {
            restoreGold(service, actor, gross);
            return booked;
        }

        final long balance = BankServiceHelpers.balanceOf(service.accounts, accountId);
        BankServiceHelpers.audit(service, actor, OpCode.BANK_DEPOSIT, null,
                BankServiceHelpers.auditPayload(accountId, gross, tax), "");
        return BankServiceHelpers.success(
                com.horizonssmp.gov.common.EconomyText.deposited(gross, tax, balance),
                accountId, balance, gross, tax, true);
    }

    private static EconomyResult checkDepositAmount(final long gross) {
        if (gross <= 0L || gross > BankService.MAX_DEPOSIT) {
            return EconomyResult.failure(ErrorCode.BAD_PAYLOAD);
        }
        if (gross < BankService.MIN_DEPOSIT) {
            return EconomyResult.failure(ErrorCode.AMOUNT_TOO_SMALL);
        }
        return EconomyResult.success();
    }

    private static void restoreGold(final BankService service, final ServerPlayer actor, final long gross) {
        if (!service.vault.addGold(actor, (int) gross)) {
            throw new IllegalStateException(
                    "bank deposit booking failed after removing " + gross
                            + " gold ingots from " + actor.getUUID()
                            + "; the inventory refused the restore and the server has to be"
                            + " reconciled");
        }
    }
}