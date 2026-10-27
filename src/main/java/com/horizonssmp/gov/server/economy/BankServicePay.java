package com.horizonssmp.gov.server.economy;

import com.horizonssmp.gov.common.EconomyKeys;
import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.OpCode;
import com.horizonssmp.gov.common.economy.EconomyResult;
import com.horizonssmp.gov.common.records.AccountRecord;

import net.minecraft.server.level.ServerPlayer;

final class BankServicePay {

    static EconomyResult pay(
            final BankService service,
            final ServerPlayer actor,
            final ServerPlayer target,
            final long gross,
            final String nonce) {
        final EconomyResult enabled = service.requireEnabled();
        if (enabled.failed()) {
            return enabled;
        }
        if (actor == null) {
            return EconomyResult.failure(ErrorCode.BAD_PAYLOAD, BankService.ACTOR_REQUIRED);
        }
        if (target == null) {
            return EconomyResult.failure(ErrorCode.BAD_TARGET);
        }
        final ServerPlayer recipient = service.vault.player(target.getUUID());
        if (recipient == null) {
            return EconomyResult.failure(ErrorCode.BAD_TARGET);
        }
        final java.util.UUID actorId = actor.getUUID();
        if (actorId.equals(recipient.getUUID())) {
            return EconomyResult.failure(ErrorCode.SELF_TRANSFER);
        }
        final EconomyResult shape = BankServiceHelpers.checkMovementAmount(gross, BankService.MIN_PAY, BankService.MAX_PAY);
        if (shape.failed()) {
            return shape;
        }
        if (!com.horizonssmp.gov.common.economy.EconomyMath.validNonce(nonce)) {
            return EconomyResult.failure(ErrorCode.BAD_PAYLOAD, BankService.NONCE_INVALID);
        }
        if (service.data.economyMeta().hasNonce(actorId, nonce)) {
            return EconomyResult.failure(ErrorCode.DUPLICATE_REQUEST);
        }

        final EconomyResult token = service.accounts.takeEconomyToken(actorId, OpCode.PAY_PLAYER);
        if (token.failed()) {
            return token;
        }
        return BankServiceHelpers.withBothLocks(service.accounts, actorId, recipient.getUUID(),
                () -> payLocked(service, actor, recipient, gross, nonce));
    }

    private static EconomyResult payLocked(
            final BankService service,
            final ServerPlayer actor,
            final ServerPlayer recipient,
            final long gross,
            final String nonce) {
        final java.util.UUID actorId = actor.getUUID();
        final String accountId = AccountService.playerAccountId(actorId);
        final String targetId = AccountService.playerAccountId(recipient.getUUID());
        final long tax = service.tax(gross);
        final long net = gross - tax;
        if (net < 1L) {
            return EconomyResult.failure(ErrorCode.AMOUNT_TOO_SMALL);
        }

        final AccountRecord source = service.accounts.player(actorId);
        final AccountRecord destination = service.accounts.player(recipient.getUUID());
        final EconomyResult preconditions =
                BankServiceHelpers.checkMovementAccounts(source, destination, gross, tax);
        if (preconditions.failed()) {
            return preconditions;
        }

        final EconomyResult accepted = service.accounts.acceptNonce(actorId, nonce);
        if (accepted.failed()) {
            return accepted;
        }

        final EconomyResult moved = service.accounts.transfer(
                accountId, targetId, gross, tax, BankService.TXN_PAY, com.horizonssmp.gov.common.enums.LedgerKind.PAY, actorId, "");
        if (moved.failed()) {
            return moved;
        }

        final long balance = BankServiceHelpers.balanceOf(service.accounts, accountId);
        BankServiceHelpers.audit(service, actor, OpCode.PAY_PLAYER, recipient,
                BankServiceHelpers.movementPayload(accountId, targetId, gross, tax), "");
        return BankServiceHelpers.movementSuccess(
                com.horizonssmp.gov.common.EconomyText.paid(gross, recipient.getName().getString(), tax, net),
                accountId, balance, accountId, targetId, gross, tax, "");
    }
}