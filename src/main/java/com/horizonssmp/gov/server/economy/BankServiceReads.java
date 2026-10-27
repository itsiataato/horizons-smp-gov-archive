package com.horizonssmp.gov.server.economy;

import com.horizonssmp.gov.common.EconomyKeys;
import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.economy.EconomyResult;
import com.horizonssmp.gov.common.records.AccountRecord;
import com.horizonssmp.gov.common.records.BusinessRecord;
import com.horizonssmp.gov.common.records.LedgerEntry;

import java.util.UUID;

import net.minecraft.server.level.ServerPlayer;

final class BankServiceReads {

    static EconomyResult balance(
            final BankService service,
            final UUID actor, final String accountId, final boolean manage) {
        if (accountId == null || accountId.isBlank()) {
            return EconomyResult.failure(ErrorCode.BAD_PAYLOAD);
        }
        if (!service.canRead(actor, accountId, manage)) {
            return EconomyResult.failure(ErrorCode.FORBIDDEN);
        }
        final String id = accountId.strip();
        final AccountRecord account = id.equals(AccountService.playerAccountId(actor))
                ? service.accounts.player(actor)
                : service.accounts.lookup(id);
        if (account == null) {
            return EconomyResult.failure(ErrorCode.BAD_TARGET);
        }
        return EconomyResult.success()
                .with(EconomyKeys.KEY_ACCOUNT, account.id())
                .with(EconomyKeys.KEY_BALANCE, Long.toString(account.balance()))
                .with(EconomyKeys.KEY_STATUS, account.status().name());
    }

    static EconomyResult history(
            final BankService service,
            final UUID actor,
            final String accountId,
            final int page,
            final boolean manage) {
        if (accountId == null || accountId.isBlank()) {
            return EconomyResult.failure(ErrorCode.BAD_PAYLOAD);
        }
        if (!service.canRead(actor, accountId, manage)) {
            return EconomyResult.failure(ErrorCode.FORBIDDEN);
        }

        final AccountService.HistoryPage slice = service.accounts.history(accountId, page);
        EconomyResult result = EconomyResult.success()
                .with(EconomyKeys.KEY_PAGE, Integer.toString(slice.page()))
                .with(BankService.KEY_PAGES, Integer.toString(slice.pages()))
                .with(BankService.KEY_TOTAL, Integer.toString(slice.total()));
        int index = 0;
        for (final LedgerEntry entry : slice.rows()) {
            final String prefix = BankService.ROW_PREFIX + index + ".";
            final boolean redact = !manage && privateTransfer(entry);
            result = result
                    .with(prefix + BankService.ROW_SEQ, Long.toString(entry.seq()))
                    .with(prefix + BankService.ROW_AT, Long.toString(entry.at()))
                    .with(prefix + BankService.ROW_TXN, entry.txn())
                    .with(prefix + BankService.ROW_KIND, entry.kind().name())
                    .with(prefix + EconomyKeys.KEY_FROM,
                            redact ? BankService.ROW_PRIVATE : entry.from())
                    .with(prefix + EconomyKeys.KEY_TO,
                            redact ? BankService.ROW_PRIVATE : entry.to())
                    .with(prefix + BankService.ROW_GROSS, Long.toString(entry.gross()))
                    .with(prefix + EconomyKeys.KEY_TAX, Long.toString(entry.tax()))
                    .with(prefix + EconomyKeys.KEY_REASON, entry.reason());
            index++;
        }
        return result;
    }

    static boolean canRead(
            final BankService service,
            final UUID actor, final String accountId, final boolean manage) {
        if (actor == null || accountId == null) {
            return false;
        }
        final String id = accountId.strip();
        if (id.isEmpty()) {
            return false;
        }
        if (AccountService.playerAccountId(actor).equals(id)) {
            return true;
        }
        if (manage) {
            return true;
        }
        if (id.startsWith(AccountService.BUSINESS_PREFIX)) {
            return businessControlledBy(service, id.substring(AccountService.BUSINESS_PREFIX.length()), actor);
        }
        return false;
    }

    static boolean businessControlledBy(
            final BankService service, final String businessId, final UUID actor) {
        if (businessId == null || businessId.isBlank() || actor == null) {
            return false;
        }
        final BusinessRecord business = service.data.business(businessId.strip());
        if (business == null) {
            return false;
        }
        return actor.equals(business.ownerUuid())
                || business.hasActiveBusinessRole(actor)
                || business.activeContractFor(actor) != null
                || business.employs(actor);
    }

    private static boolean privateTransfer(final LedgerEntry entry) {
        if (entry == null) {
            return false;
        }
        if (entry.kind() != com.horizonssmp.gov.common.enums.LedgerKind.PAY && entry.kind() != com.horizonssmp.gov.common.enums.LedgerKind.TRANSFER) {
            return false;
        }
        return entry.from().startsWith(AccountService.PLAYER_PREFIX)
                && entry.to().startsWith(AccountService.PLAYER_PREFIX);
    }
}