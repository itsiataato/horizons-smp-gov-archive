package com.horizonssmp.gov.server.economy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.economy.EconomyMath;
import com.horizonssmp.gov.common.economy.EconomyResult;
import com.horizonssmp.gov.common.records.AccountRecord;
import com.horizonssmp.gov.common.records.EconomyMeta;
import com.horizonssmp.gov.common.records.LedgerEntry;
import com.horizonssmp.gov.server.GovSavedData;

final class TxnDraftValidator {

    private TxnDraftValidator() {
    }

    public static EconomyResult validateStructure(final TxnDraft draft) {
        final LedgerEntry entry = draft.ledger();
        final long externalDelta = draft.externalDelta();
        final List<TxnDraft.Line> debits = draft.debits();
        final List<TxnDraft.Line> credits = draft.credits();

        if (externalDelta > EconomyMath.MAX_BALANCE
                || externalDelta < -EconomyMath.MAX_BALANCE) {
            return unbalanced("The transaction moves more gold than one account may hold.");
        }
        final boolean fromPhysical = TxnDraft.PHYSICAL.equals(entry.from());
        final boolean toPhysical = TxnDraft.PHYSICAL.equals(entry.to());
        if (fromPhysical && toPhysical) {
            return unbalanced("The transaction cannot move gold from physical to physical.");
        }
        if (debits.isEmpty() && credits.isEmpty()) {
            return unbalanced("The transaction has no postings.");
        }

        final long debitTotal;
        final long creditTotal;
        try {
            debitTotal = sum(debits);
            creditTotal = sum(credits);
        } catch (final ArithmeticException overflow) {
            return EconomyResult.failure(
                    ErrorCode.LIMIT_EXCEEDED, "The transaction total is too large.");
        }
        if (EconomyMath.subtractExact(creditTotal, debitTotal) != externalDelta) {
            return unbalanced("The transaction postings do not match its external movement.");
        }

        final String government = GovSavedData.GOVERNMENT_ACCOUNT_ID;
        final String police = GovSavedData.POLICE_ACCOUNT_ID;

        if (fromPhysical) {
            if (!debits.isEmpty()) {
                return unbalanced("A physical source cannot be debited.");
            }
            if (creditTotal != entry.gross() || externalDelta != entry.gross()) {
                return unbalanced("A physical deposit must credit exactly its gross.");
            }
            if (entry.to().equals(government)) {
                if (creditsTo(credits, government) != entry.gross()) {
                    return unbalanced("A deposit to Government must credit its gross.");
                }
            } else {
                if (creditsTo(credits, entry.to()) != entry.net()) {
                    return unbalanced("The recipient credit must equal the net amount.");
                }
                if (creditsTo(credits, government) != entry.tax()) {
                    return unbalanced("The tax credit must equal the ledger tax.");
                }
            }
        } else if (toPhysical) {
            if (debitTotal != entry.gross()) {
                return unbalanced("A physical withdrawal must debit exactly its gross.");
            }
            if (creditTotal != entry.tax() || externalDelta != -entry.net()) {
                return unbalanced("A physical withdrawal must credit exactly its tax.");
            }
            for (final TxnDraft.Line line : debits) {
                if (!entry.from().equals(line.accountId())) {
                    return unbalanced("A withdrawal may debit only the named account.");
                }
            }
            for (final TxnDraft.Line line : credits) {
                if (!government.equals(line.accountId())) {
                    return unbalanced("A withdrawal may credit only Government.");
                }
            }
            if (creditsTo(credits, government) != entry.tax()) {
                return unbalanced("The tax credit must equal the ledger tax.");
            }
        } else {
            if (debitTotal != entry.gross() || creditTotal != entry.gross()
                    || externalDelta != 0L) {
                return unbalanced("A transfer must debit and credit the same gross.");
            }
            for (final TxnDraft.Line line : debits) {
                if (!entry.from().equals(line.accountId())) {
                    return unbalanced("A transfer may debit only the named source.");
                }
            }
            for (final TxnDraft.Line line : credits) {
                if (!entry.to().equals(line.accountId())
                        && !government.equals(line.accountId())) {
                    return unbalanced("A transfer may credit only the destination or Government.");
                }
            }
            if (entry.to().equals(government)) {
                if (creditsTo(credits, government) != entry.gross()) {
                    return unbalanced("A transfer to Government must credit its gross.");
                }
            } else {
                if (creditsTo(credits, entry.to()) != entry.net()) {
                    return unbalanced("The recipient credit must equal the net amount.");
                }
                if (creditsTo(credits, government) != entry.tax()) {
                    return unbalanced("The tax credit must equal the ledger tax.");
                }
            }
        }

        if (entry.tax() > 0L
                && (government.equals(entry.from()) || police.equals(entry.from()))) {
            return unbalanced("A Government or Police debit is never taxed.");
        }
        return EconomyResult.success();
    }

    public static EconomyResult validateAccounts(final TxnDraft draft, final AccountService service) {
        final List<TxnDraft.Line> debits = draft.debits();
        final List<TxnDraft.Line> credits = draft.credits();
        final Map<String, Long> debitTotals = totals(debits);
        final Map<String, Long> creditTotals = totals(credits);
        for (final String accountId : debitTotals.keySet()) {
            if (creditTotals.containsKey(accountId)) {
                return EconomyResult.failure(ErrorCode.SELF_TRANSFER, "That would move gold within one account.");
            }
        }
        for (final Map.Entry<String, Long> entry : debitTotals.entrySet()) {
            final AccountRecord account = service.lookup(entry.getKey());
            if (account == null) {
                return EconomyResult.failure(ErrorCode.BAD_TARGET);
            }
            if (!account.acceptsDebit()) {
                return EconomyResult.failure(ErrorCode.ACCOUNT_FROZEN);
            }
            if (account.balance() < entry.getValue()) {
                return EconomyResult.failure(ErrorCode.INSUFFICIENT_FUNDS);
            }
        }
        for (final Map.Entry<String, Long> entry : creditTotals.entrySet()) {
            final AccountRecord account = service.lookup(entry.getKey());
            if (account == null) {
                return EconomyResult.failure(ErrorCode.BAD_TARGET);
            }
            if (!account.acceptsCredit()) {
                return EconomyResult.failure(ErrorCode.ACCOUNT_FROZEN);
            }
            final long after;
            try {
                after = EconomyMath.addExact(account.balance(), entry.getValue());
            } catch (final ArithmeticException overflow) {
                return EconomyResult.failure(ErrorCode.LIMIT_EXCEEDED);
            }
            if (after > EconomyMath.MAX_BALANCE) {
                return EconomyResult.failure(ErrorCode.LIMIT_EXCEEDED);
            }
        }
        return EconomyResult.success();
    }

    public static EconomyResult validateCounters(final TxnDraft draft, final AccountService service) {
        final EconomyMeta meta = service.data().economyMeta();
        final long externalDelta = draft.externalDelta();
        try {
            final long postedNet = EconomyMath.subtractExact(sum(draft.credits()), sum(draft.debits()));
            final long projectedBalances =
                    EconomyMath.addExact(service.sumBalances(), postedNet);
            final long deposited;
            final long withdrawn;
            if (externalDelta > 0L) {
                deposited = EconomyMath.addExact(meta.totalDeposited(), externalDelta);
                withdrawn = meta.totalWithdrawn();
            } else {
                deposited = meta.totalDeposited();
                withdrawn = EconomyMath.addExact(meta.totalWithdrawn(), -externalDelta);
            }
            if (withdrawn > deposited) {
                return EconomyResult.failure(ErrorCode.INVALID_STATE);
            }
            if (!EconomyMath.conservation(
                    projectedBalances, meta.contractEscrow(), deposited, withdrawn)) {
                return EconomyResult.failure(ErrorCode.INVALID_STATE);
            }
        } catch (final ArithmeticException overflow) {
            return EconomyResult.failure(ErrorCode.LIMIT_EXCEEDED);
        }
        return EconomyResult.success();
    }

    public static long sum(final List<TxnDraft.Line> lines) {
        long total = 0L;
        for (final TxnDraft.Line line : lines) {
            total = EconomyMath.addExact(total, line.amount());
        }
        return total;
    }

    public static long creditsTo(final List<TxnDraft.Line> credits, final String accountId) {
        long total = 0L;
        for (final TxnDraft.Line line : credits) {
            if (accountId.equals(line.accountId())) {
                total = EconomyMath.addExact(total, line.amount());
            }
        }
        return total;
    }

    public static Map<String, Long> totals(final List<TxnDraft.Line> lines) {
        final Map<String, Long> totals = new LinkedHashMap<>();
        for (final TxnDraft.Line line : lines) {
            accumulate(totals, line.accountId(), line.amount());
        }
        return totals;
    }

    public static void accumulate(
            final Map<String, Long> totals, final String accountId, final long amount) {
        totals.merge(accountId, amount, EconomyMath::addExact);
    }

    private static EconomyResult unbalanced(final String message) {
        return EconomyResult.failure(ErrorCode.INVALID_STATE, message);
    }
}