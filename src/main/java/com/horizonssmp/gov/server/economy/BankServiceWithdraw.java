package com.horizonssmp.gov.server.economy;

import com.horizonssmp.gov.common.EconomyKeys;
import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.OpCode;
import com.horizonssmp.gov.common.enums.LedgerKind;
import com.horizonssmp.gov.common.economy.EconomyResult;
import com.horizonssmp.gov.common.records.LedgerEntry;
import com.horizonssmp.gov.server.GovSavedData;

import net.minecraft.server.level.ServerPlayer;

final class BankServiceWithdraw {

    static EconomyResult withdraw(
            final BankService service,
            final ServerPlayer teller,
            final ServerPlayer target,
            final long gross,
            final String nonce) {
        final EconomyResult enabled = service.requireEnabled();
        if (enabled.failed()) {
            return enabled;
        }
        if (teller == null) {
            return EconomyResult.failure(ErrorCode.BAD_PAYLOAD, BankService.ACTOR_REQUIRED);
        }
        if (target == null) {
            return EconomyResult.failure(ErrorCode.BAD_TARGET);
        }
        if (!service.checkManage(teller)) {
            return EconomyResult.failure(ErrorCode.FORBIDDEN);
        }
        if (!service.regions.isBank(teller)) {
            return EconomyResult.failure(ErrorCode.REGION_REQUIRED);
        }
        final ServerPlayer recipient = service.vault.player(target.getUUID());
        if (recipient == null) {
            return EconomyResult.failure(ErrorCode.BAD_TARGET);
        }
        final EconomyResult shape = checkWithdrawAmount(gross);
        if (shape.failed()) {
            return shape;
        }
        if (!com.horizonssmp.gov.common.economy.EconomyMath.validNonce(nonce)) {
            return EconomyResult.failure(ErrorCode.BAD_PAYLOAD, BankService.NONCE_INVALID);
        }

        final java.util.UUID tellerId = teller.getUUID();
        if (service.data.economyMeta().hasNonce(tellerId, nonce)) {
            return EconomyResult.failure(ErrorCode.DUPLICATE_REQUEST);
        }

        final EconomyResult token = service.accounts.takeEconomyToken(tellerId, OpCode.BANK_WITHDRAW);
        if (token.failed()) {
            return token;
        }
        return BankServiceHelpers.withBothLocks(service.accounts, tellerId, recipient.getUUID(),
                () -> withdrawLocked(service, teller, recipient, gross, nonce));
    }

    private static EconomyResult withdrawLocked(
            final BankService service,
            final ServerPlayer teller,
            final ServerPlayer recipient,
            final long gross,
            final String nonce) {
        final java.util.UUID tellerId = teller.getUUID();
        final String accountId = AccountService.playerAccountId(recipient.getUUID());
        final long tax = service.tax(gross);
        final long net = gross - tax;
        if (net < 1L) {
            return EconomyResult.failure(ErrorCode.AMOUNT_TOO_SMALL);
        }

        service.accounts.player(recipient.getUUID());
        if (tax > 0L) {
            service.accounts.government();
        }

        final TxnDraft draft = new TxnDraft(new LedgerEntry(
                0L,
                service.accounts.now(),
                BankService.TXN_WITHDRAW,
                LedgerKind.WITHDRAW,
                accountId,
                TxnDraft.PHYSICAL,
                gross,
                tax,
                tellerId,
                BankService.WITHDRAW_REASON))
                .debit(accountId, gross, BankService.WITHDRAW_REASON)
                .externalDelta(-net);
        if (tax > 0L) {
            draft.credit(GovSavedData.GOVERNMENT_ACCOUNT_ID, tax, BankService.WITHDRAW_REASON);
        }

        final EconomyResult validated = draft.validate(service.accounts);
        if (validated.failed()) {
            return validated;
        }
        if (!service.vault.canAccept(recipient, (int) net)) {
            return EconomyResult.failure(ErrorCode.INVENTORY_FULL);
        }

        final EconomyResult accepted = service.accounts.acceptNonce(tellerId, nonce);
        if (accepted.failed()) {
            return accepted;
        }

        if (!service.vault.addGold(recipient, (int) net)) {
            return EconomyResult.failure(ErrorCode.INVENTORY_FULL);
        }

        try {
            draft.commit(service.accounts);
        } catch (final RuntimeException commitFailure) {
            removeDelivered(service, recipient, net, commitFailure);
            throw commitFailure;
        }

        final long balance = BankServiceHelpers.balanceOf(service.accounts, accountId);
        BankServiceHelpers.audit(service, teller, OpCode.BANK_WITHDRAW, recipient,
                BankServiceHelpers.auditPayload(accountId, gross, tax), "");
        return BankServiceHelpers.success(
                com.horizonssmp.gov.common.EconomyText.paid(gross, recipient.getName().getString(), tax, net),
                accountId, balance, gross, tax, false);
    }

    private static EconomyResult checkWithdrawAmount(final long gross) {
        if (gross <= 0L || gross > BankService.MAX_WITHDRAW) {
            return EconomyResult.failure(ErrorCode.BAD_PAYLOAD);
        }
        if (gross < BankService.MIN_WITHDRAW) {
            return EconomyResult.failure(ErrorCode.AMOUNT_TOO_SMALL);
        }
        return EconomyResult.success();
    }

    private static void removeDelivered(
            final BankService service, final ServerPlayer recipient, final long net, final RuntimeException cause) {
        if (!service.vault.removeGold(recipient, (int) net)) {
            throw new IllegalStateException(
                    "bank withdrawal commit failed after delivering " + net
                            + " gold ingots to " + recipient.getUUID()
                            + "; the delivered items could not be removed and the server has to be"
                            + " reconciled", cause);
        }
    }
}