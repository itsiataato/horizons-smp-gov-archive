package com.horizonssmp.gov.server.economy;

import java.util.UUID;

import com.horizonssmp.gov.common.EconomyKeys;
import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.enums.LedgerKind;
import com.horizonssmp.gov.common.economy.EconomyResult;
import com.horizonssmp.gov.common.records.AccountRecord;
import com.horizonssmp.gov.common.records.LedgerEntry;
import com.horizonssmp.gov.server.GovSavedData;

final class AccountMutations {

    private static final String TXN_CREDIT = "credit";
    private static final String TXN_DEBIT = "debit";

    private AccountMutations() {
    }

    private static EconomyResult validateAndCommit(final AccountService service, final TxnDraft draft) {
        final EconomyResult validated = draft.validate(service);
        if (validated.failed()) {
            return validated;
        }
        draft.commit(service);
        return EconomyResult.success();
    }

    static EconomyResult credit(final AccountService service, final String accountId, final long amount, final String reason) {
        final String id = AccountUtil.canonical(accountId);
        if (id == null) {
            return EconomyResult.failure(ErrorCode.BAD_PAYLOAD);
        }
        final EconomyResult shape = AccountUtil.checkAmount(amount);
        if (shape.failed()) {
            return shape;
        }
        if (AccountReads.resolve(service, id) == null) {
            return EconomyResult.failure(ErrorCode.BAD_TARGET);
        }
        final String cleanReason = AccountUtil.cleanReason(reason);
        final TxnDraft draft = new TxnDraft(new LedgerEntry(
                0L, service.now(), TXN_CREDIT, LedgerKind.DEPOSIT, TxnDraft.PHYSICAL, id,
                amount, 0L, null, cleanReason))
                .credit(id, amount, cleanReason)
                .externalDelta(amount);
        final EconomyResult result = validateAndCommit(service, draft);
        if (result.failed()) {
            return result;
        }
        return AccountUtil.withAmountFields(
                result.with(EconomyKeys.KEY_ACCOUNT, id)
                        .with(EconomyKeys.KEY_BALANCE, Long.toString(AccountReads.balanceOf(service, id))),
                TxnDraft.PHYSICAL, id, amount, 0L, cleanReason);
    }

    static EconomyResult debit(final AccountService service, final String accountId, final long amount, final String reason) {
        final String id = AccountUtil.canonical(accountId);
        if (id == null) {
            return EconomyResult.failure(ErrorCode.BAD_PAYLOAD);
        }
        final EconomyResult shape = AccountUtil.checkAmount(amount);
        if (shape.failed()) {
            return shape;
        }
        final AccountRecord account = AccountReads.resolve(service, id);
        if (account == null) {
            return EconomyResult.failure(ErrorCode.BAD_TARGET);
        }
        final String cleanReason = AccountUtil.cleanReason(reason);
        final EconomyResult reasonCheck = AccountUtil.checkDebitReason(account, cleanReason);
        if (reasonCheck.failed()) {
            return reasonCheck;
        }
        final TxnDraft draft = new TxnDraft(new LedgerEntry(
                0L, service.now(), TXN_DEBIT, LedgerKind.WITHDRAW, id, TxnDraft.PHYSICAL,
                amount, 0L, null, cleanReason))
                .debit(id, amount, cleanReason)
                .externalDelta(-amount);
        final EconomyResult result = validateAndCommit(service, draft);
        if (result.failed()) {
            return result;
        }
        return AccountUtil.withAmountFields(
                result.with(EconomyKeys.KEY_ACCOUNT, id)
                        .with(EconomyKeys.KEY_BALANCE, Long.toString(AccountReads.balanceOf(service, id))),
                id, TxnDraft.PHYSICAL, amount, 0L, cleanReason);
    }

    static EconomyResult depositCredit(
            final AccountService service,
            final String accountId,
            final long gross,
            final long tax,
            final String txn,
            final UUID actor,
            final String reason) {
        final String id = AccountUtil.canonical(accountId);
        if (id == null) {
            return EconomyResult.failure(ErrorCode.BAD_PAYLOAD);
        }
        final AccountRecord account = AccountReads.resolve(service, id);
        if (account == null) {
            return EconomyResult.failure(ErrorCode.BAD_TARGET);
        }
        final EconomyResult taxCheck = AccountUtil.checkExternalTax(account, gross, tax);
        if (taxCheck.failed()) {
            return taxCheck;
        }
        final String label = AccountUtil.cleanTxn(txn);
        if (label == null) {
            return EconomyResult.failure(ErrorCode.BAD_PAYLOAD);
        }
        final String cleanReason = AccountUtil.cleanReason(reason);
        final long net = gross - tax;
        if (tax > 0L) {
            service.government();
        }
        final TxnDraft draft = new TxnDraft(new LedgerEntry(
                0L, service.now(), label, LedgerKind.DEPOSIT, TxnDraft.PHYSICAL, id,
                gross, tax, actor, cleanReason))
                .credit(id, net, cleanReason)
                .externalDelta(gross);
        if (tax > 0L) {
            draft.credit(GovSavedData.GOVERNMENT_ACCOUNT_ID, tax, cleanReason);
        }
        final EconomyResult result = validateAndCommit(service, draft);
        if (result.failed()) {
            return result;
        }
        return AccountUtil.withAmountFields(
                result.with(EconomyKeys.KEY_ACCOUNT, id)
                        .with(EconomyKeys.KEY_BALANCE, Long.toString(AccountReads.balanceOf(service, id))),
                TxnDraft.PHYSICAL, id, gross, tax, cleanReason);
    }

    static EconomyResult withdrawalDebit(
            final AccountService service,
            final String accountId,
            final long gross,
            final long tax,
            final String txn,
            final UUID actor,
            final String reason) {
        final String id = AccountUtil.canonical(accountId);
        if (id == null) {
            return EconomyResult.failure(ErrorCode.BAD_PAYLOAD);
        }
        final AccountRecord account = AccountReads.resolve(service, id);
        if (account == null) {
            return EconomyResult.failure(ErrorCode.BAD_TARGET);
        }
        final EconomyResult taxCheck = AccountUtil.checkExternalTax(account, gross, tax);
        if (taxCheck.failed()) {
            return taxCheck;
        }
        final String label = AccountUtil.cleanTxn(txn);
        if (label == null) {
            return EconomyResult.failure(ErrorCode.BAD_PAYLOAD);
        }
        final String cleanReason = AccountUtil.cleanReason(reason);
        final EconomyResult reasonCheck = AccountUtil.checkDebitReason(account, cleanReason);
        if (reasonCheck.failed()) {
            return reasonCheck;
        }
        final long net = gross - tax;
        if (tax > 0L) {
            service.government();
        }
        final TxnDraft draft = new TxnDraft(new LedgerEntry(
                0L, service.now(), label, LedgerKind.WITHDRAW, id, TxnDraft.PHYSICAL,
                gross, tax, actor, cleanReason))
                .debit(id, gross, cleanReason)
                .externalDelta(-net);
        if (tax > 0L) {
            draft.credit(GovSavedData.GOVERNMENT_ACCOUNT_ID, tax, cleanReason);
        }
        final EconomyResult result = validateAndCommit(service, draft);
        if (result.failed()) {
            return result;
        }
        return AccountUtil.withAmountFields(
                result.with(EconomyKeys.KEY_ACCOUNT, id)
                        .with(EconomyKeys.KEY_BALANCE, Long.toString(AccountReads.balanceOf(service, id))),
                id, TxnDraft.PHYSICAL, gross, tax, cleanReason);
    }

    static EconomyResult transfer(
            final AccountService service,
            final String fromId,
            final String toId,
            final long gross,
            final long tax,
            final String txn,
            final LedgerKind kind,
            final UUID actor,
            final String reason) {
        final String from = AccountUtil.canonical(fromId);
        final String to = AccountUtil.canonical(toId);
        if (from == null || to == null) {
            return EconomyResult.failure(ErrorCode.BAD_PAYLOAD);
        }
        if (from.equals(to)) {
            return EconomyResult.failure(ErrorCode.SELF_TRANSFER);
        }
        final EconomyResult shape = AccountUtil.checkAmount(gross);
        if (shape.failed()) {
            return shape;
        }
        if (tax < 0L || tax > gross) {
            return EconomyResult.failure(ErrorCode.BAD_PAYLOAD);
        }
        final AccountRecord source = AccountReads.resolve(service, from);
        if (source == null) {
            return EconomyResult.failure(ErrorCode.BAD_TARGET);
        }
        final AccountRecord destination = AccountReads.resolve(service, to);
        if (destination == null) {
            return EconomyResult.failure(ErrorCode.BAD_TARGET);
        }
        final LedgerKind ledgerKind = kind == null ? LedgerKind.TRANSFER : kind;
        final EconomyResult taxCheck = AccountUtil.checkTransferTax(source, destination, ledgerKind, gross, tax);
        if (taxCheck.failed()) {
            return taxCheck;
        }
        final String label = AccountUtil.cleanTxn(txn);
        if (label == null) {
            return EconomyResult.failure(ErrorCode.BAD_PAYLOAD);
        }
        final String cleanReason = AccountUtil.cleanReason(reason);
        final EconomyResult reasonCheck = AccountUtil.checkDebitReason(source, cleanReason);
        if (reasonCheck.failed()) {
            return reasonCheck;
        }
        final long net = gross - tax;
        if (tax > 0L) {
            service.government();
        }
        final TxnDraft draft = new TxnDraft(new LedgerEntry(
                0L, service.now(), label, ledgerKind, from, to, gross, tax, actor, cleanReason))
                .debit(from, gross, cleanReason)
                .credit(to, net, cleanReason);
        if (tax > 0L) {
            draft.credit(GovSavedData.GOVERNMENT_ACCOUNT_ID, tax, cleanReason);
        }
        final EconomyResult result = validateAndCommit(service, draft);
        if (result.failed()) {
            return result;
        }
        return AccountUtil.withAmountFields(result, from, to, gross, tax, cleanReason);
    }
}