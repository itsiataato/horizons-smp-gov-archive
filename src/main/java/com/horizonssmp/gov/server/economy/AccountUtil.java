package com.horizonssmp.gov.server.economy;

import java.util.UUID;

import com.horizonssmp.gov.common.EconomyKeys;
import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.OpCode;
import com.horizonssmp.gov.common.economy.EconomyMath;
import com.horizonssmp.gov.common.economy.EconomyResult;
import com.horizonssmp.gov.common.records.AccountRecord;
import com.horizonssmp.gov.common.records.LedgerEntry;
import com.horizonssmp.gov.common.enums.LedgerKind;
import com.horizonssmp.gov.server.GovSavedData;
import com.horizonssmp.gov.server.Sanitizer;

final class AccountUtil {

    private static final int MAX_ACCOUNT_ID_LENGTH = 128;

    private AccountUtil() {
    }

    static String canonical(final String accountId) {
        if (accountId == null) {
            return null;
        }
        final String id = accountId.strip();
        if (id.isEmpty() || id.length() > MAX_ACCOUNT_ID_LENGTH) {
            return null;
        }
        return id;
    }

    static String cleanTxn(final String txn) {
        final String label = Sanitizer.id(txn == null ? "" : txn);
        return label.isBlank() ? null : label;
    }

    static String cleanReason(final String reason) {
        return Sanitizer.singleLine(reason, AccountService.MAX_REASON_LENGTH);
    }

    static String blankToNull(final String value) {
        return value == null || value.isBlank() ? null : value;
    }

    static EconomyResult checkAmount(final long amount) {
        if (amount <= 0L || amount > EconomyMath.MAX_BALANCE) {
            return EconomyResult.failure(
                    ErrorCode.BAD_PAYLOAD,
                    "The amount must be a whole number between 1 and "
                            + EconomyMath.MAX_BALANCE + " gold ingots.");
        }
        return EconomyResult.success();
    }

    static boolean isSingleton(final AccountRecord account) {
        return account != null
                && (GovSavedData.GOVERNMENT_ACCOUNT_ID.equals(account.id())
                        || GovSavedData.POLICE_ACCOUNT_ID.equals(account.id()));
    }

    static EconomyResult checkDebitReason(final AccountRecord account, final String reason) {
        if (isSingleton(account) && reason.length() < AccountService.MIN_REASON_LENGTH) {
            return EconomyResult.failure(
                    ErrorCode.BAD_PAYLOAD,
                    "A reason of at least " + AccountService.MIN_REASON_LENGTH
                            + " characters is required for a Government or Police debit.");
        }
        return EconomyResult.success();
    }

    static EconomyResult checkTransferTax(
            final AccountRecord source,
            final AccountRecord destination,
            final LedgerKind kind,
            final long gross,
            final long tax) {
        final EconomyResult shape = checkAmount(gross);
        if (shape.failed()) {
            return shape;
        }
        if (tax < 0L || tax > gross) {
            return EconomyResult.failure(ErrorCode.BAD_PAYLOAD);
        }
        if (isSingleton(source)) {
            if (tax != 0L) {
                return EconomyResult.failure(
                        ErrorCode.INVALID_STATE,
                        "Government and Police payments are not taxed.");
            }
        } else if (kind == LedgerKind.FINE || kind == LedgerKind.STORE) {
            if (tax != 0L) {
                return EconomyResult.failure(
                        ErrorCode.INVALID_STATE, "Fines and store purchases are not taxed.");
            }
            if (!GovSavedData.GOVERNMENT_ACCOUNT_ID.equals(destination.id())) {
                return EconomyResult.failure(
                        ErrorCode.INVALID_STATE,
                        "Fine and store revenue must be credited to Government.");
            }
        } else if (tax != EconomyMath.tax(gross)) {
            return EconomyResult.failure(
                    ErrorCode.BAD_PAYLOAD, "The tax on that transaction is not correct.");
        }
        if (gross - tax < 1L) {
            return EconomyResult.failure(ErrorCode.AMOUNT_TOO_SMALL);
        }
        return EconomyResult.success();
    }

    static EconomyResult checkExternalTax(
            final AccountRecord account, final long gross, final long tax) {
        final EconomyResult shape = checkAmount(gross);
        if (shape.failed()) {
            return shape;
        }
        if (tax < 0L || tax > gross) {
            return EconomyResult.failure(ErrorCode.BAD_PAYLOAD);
        }
        if (isSingleton(account)) {
            if (tax != 0L) {
                return EconomyResult.failure(
                        ErrorCode.INVALID_STATE,
                        "Government and Police payments are not taxed.");
            }
        } else if (tax != EconomyMath.tax(gross)) {
            return EconomyResult.failure(
                    ErrorCode.BAD_PAYLOAD, "The tax on that transaction is not correct.");
        }
        if (gross - tax < 1L) {
            return EconomyResult.failure(ErrorCode.AMOUNT_TOO_SMALL);
        }
        return EconomyResult.success();
    }

    static EconomyResult withAmountFields(
            final EconomyResult result,
            final String from,
            final String to,
            final long gross,
            final long tax,
            final String reason) {
        return result
                .with(EconomyKeys.KEY_FROM, from)
                .with(EconomyKeys.KEY_TO, to)
                .with(EconomyKeys.KEY_AMOUNT, Long.toString(gross))
                .with(EconomyKeys.KEY_TAX, Long.toString(tax))
                .with(EconomyKeys.KEY_NET, Long.toString(gross - tax))
                .with(EconomyKeys.KEY_REASON, blankToNull(reason));
    }

    static EconomyResult requireResult(final EconomyResult result) {
        if (result == null) {
            throw new IllegalStateException("an economy action returned no result");
        }
        return result;
    }
}