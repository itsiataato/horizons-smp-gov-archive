package com.horizonssmp.gov.server.economy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.horizonssmp.gov.common.EconomyKeys;
import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.OpCode;
import com.horizonssmp.gov.common.enums.AccountStatus;
import com.horizonssmp.gov.common.enums.LedgerKind;
import com.horizonssmp.gov.common.economy.EconomyMath;
import com.horizonssmp.gov.common.economy.EconomyResult;
import com.horizonssmp.gov.common.records.AccountRecord;
import com.horizonssmp.gov.common.records.BusinessRecord;
import com.horizonssmp.gov.common.records.EmploymentContractRecord;
import com.horizonssmp.gov.common.records.LedgerEntry;
import com.horizonssmp.gov.common.records.PayrollRunRecord;
import com.horizonssmp.gov.config.GovConfig;
import com.horizonssmp.gov.server.AuditService;
import com.horizonssmp.gov.server.GovSavedData;

import net.minecraft.server.level.ServerPlayer;

/**
 * Commit pipeline for a payroll run. Behavior is identical to the previous
 * in-class implementation.
 */
final class PayrollCommitHelper {

    private static final String GOVERNMENT = GovSavedData.GOVERNMENT_ACCOUNT_ID;
    private static final String TXN_PREFIX = "payroll.";

    private static final String KEY_SCOPE = "payroll.scope";
    private static final String KEY_COUNT = "payroll.count";
    private static final String KEY_GROSS = "payroll.gross";
    private static final String KEY_TAX = "payroll.tax";
    private static final String KEY_NET = "payroll.net";
    private static final String KEY_RUN = "payroll.run";
    private static final String KEY_SKIPPED_SELF = "skipped.self";
    private static final String KEY_SKIPPED_TAX = "skipped.tax";

    private static final String DESTINATION_MISSING = "An employee account does not exist.";
    private static final String TAX_ACCOUNT_MISSING = "The Government account does not exist.";
    private static final String RUN_CAP_EXCEEDED = "The payroll run exceeds the configured cap.";
    private static final String COMMIT_FAILED = "The payroll run could not be recorded.";

    private PayrollCommitHelper() {
    }

    static EconomyResult commit(
            final GovSavedData data,
            final AccountService accounts,
            final ServerPlayer actor,
            final String scope,
            final String ledgerReason,
            final String auditReason,
            final long now,
            final long bucket,
            final AccountRecord source,
            final PayrollDraft draft,
            final BusinessRecord business) {
        long grossTotal = 0L;
        long taxTotal = 0L;
        final Map<String, Long> credits = new LinkedHashMap<>();
        final Map<String, AccountRecord> destinations = new LinkedHashMap<>();
        for (final PayrollPayment payment : draft.payments()) {
            try {
                grossTotal = Math.addExact(grossTotal, payment.gross());
                taxTotal = Math.addExact(taxTotal, payment.tax());
                credits.merge(
                        payment.accountId(),
                        payment.net(),
                        (left, right) -> Math.addExact(left, right));
            } catch (final ArithmeticException overflow) {
                return EconomyResult.failure(ErrorCode.LIMIT_EXCEEDED);
            }
            final AccountRecord destination = accounts.lookup(payment.accountId());
            if (destination == null) {
                return EconomyResult.failure(ErrorCode.BAD_TARGET, DESTINATION_MISSING);
            }
            if (!destination.acceptsCredit()) {
                return EconomyResult.failure(ErrorCode.ACCOUNT_FROZEN);
            }
            destinations.put(destination.id(), destination);
        }

        if (grossTotal > GovConfig.payrollRunCap()) {
            return EconomyResult.failure(ErrorCode.LIMIT_EXCEEDED, RUN_CAP_EXCEEDED);
        }
        if (source.balance() < grossTotal) {
            return EconomyResult.failure(ErrorCode.INSUFFICIENT_FUNDS);
        }

        final Map<String, Long> afterBalances = new LinkedHashMap<>();
        afterBalances.put(source.id(), source.balance() - grossTotal);
        for (final Map.Entry<String, Long> entry : credits.entrySet()) {
            final AccountRecord destination = destinations.get(entry.getKey());
            if (destination == null) {
                return EconomyResult.failure(ErrorCode.BAD_TARGET, DESTINATION_MISSING);
            }
            final long after;
            try {
                after = Math.addExact(destination.balance(), entry.getValue());
            } catch (final ArithmeticException overflow) {
                return EconomyResult.failure(ErrorCode.LIMIT_EXCEEDED);
            }
            if (after > AccountRecord.MAX_BALANCE) {
                return EconomyResult.failure(ErrorCode.LIMIT_EXCEEDED);
            }
            afterBalances.put(entry.getKey(), after);
        }

        AccountRecord government = null;
        if (taxTotal > 0L) {
            government = accounts.lookup(GOVERNMENT);
            if (government == null) {
                return EconomyResult.failure(ErrorCode.BAD_TARGET, TAX_ACCOUNT_MISSING);
            }
            if (!government.acceptsCredit()) {
                return EconomyResult.failure(ErrorCode.ACCOUNT_FROZEN);
            }
            final long after;
            try {
                after = Math.addExact(government.balance(), taxTotal);
            } catch (final ArithmeticException overflow) {
                return EconomyResult.failure(ErrorCode.LIMIT_EXCEEDED);
            }
            if (after > AccountRecord.MAX_BALANCE) {
                return EconomyResult.failure(ErrorCode.LIMIT_EXCEEDED);
            }
            afterBalances.put(government.id(), after);
        }

        final String txn = TXN_PREFIX + scope + "." + bucket;
        final List<LedgerEntry> entries = new ArrayList<>(draft.payments().size());
        final Map<String, Long> lastSeq = new LinkedHashMap<>();
        final long firstSeq;
        try {
            firstSeq = accounts.nextSeq();
        } catch (final IllegalStateException exhausted) {
            return EconomyResult.failure(ErrorCode.INVALID_STATE, COMMIT_FAILED);
        }
        long seq = firstSeq;
        for (final PayrollPayment payment : draft.payments()) {
            entries.add(new LedgerEntry(
                    seq,
                    now,
                    txn,
                    LedgerKind.PAYROLL,
                    source.id(),
                    payment.accountId(),
                    payment.gross(),
                    payment.tax(),
                    actor.getUUID(),
                    ledgerReason));
            lastSeq.merge(payment.accountId(), seq, Long::max);
            if (payment.tax() > 0L) {
                lastSeq.merge(GOVERNMENT, seq, Long::max);
            }
            seq++;
        }
        lastSeq.put(source.id(), seq - 1L);

        final Map<String, AccountRecord> beforeAccounts = new LinkedHashMap<>();
        beforeAccounts.put(source.id(), source);
        for (final AccountRecord destination : destinations.values()) {
            beforeAccounts.put(destination.id(), destination);
        }
        if (government != null) {
            beforeAccounts.put(government.id(), government);
        }

        final Map<String, AccountRecord> afterAccounts = new LinkedHashMap<>();
        for (final Map.Entry<String, AccountRecord> entry : beforeAccounts.entrySet()) {
            final String id = entry.getKey();
            afterAccounts.put(id, withBalance(
                    entry.getValue(),
                    afterBalances.get(id),
                    lastSeq.getOrDefault(id, entry.getValue().lastSeq())));
        }

        BusinessRecord afterBusiness = null;
        if (business != null && draft.payments().stream().anyMatch(p -> p.contract() != null)) {
            final Map<String, EmploymentContractRecord> stamped = new LinkedHashMap<>();
            for (final PayrollPayment payment : draft.payments()) {
                if (payment.contract() != null) {
                    stamped.put(payment.contract().id(), payment.contract());
                }
            }
            final List<EmploymentContractRecord> next = new ArrayList<>(business.contracts().size());
            for (final EmploymentContractRecord original : business.contracts()) {
                next.add(stamped.getOrDefault(original.id(), original));
            }
            afterBusiness = business.withContracts(next);
        }

        try {
            for (final AccountRecord account : afterAccounts.values()) {
                data.putAccount(account);
            }
            if (afterBusiness != null) {
                data.putBusiness(afterBusiness);
            }
            for (final LedgerEntry entry : entries) {
                data.appendLedger(entry);
            }
        } catch (final RuntimeException failure) {
            for (final AccountRecord account : beforeAccounts.values()) {
                data.putAccount(account);
            }
            if (business != null) {
                data.putBusiness(business);
            }
            data.markDirty();
            return EconomyResult.failure(ErrorCode.INVALID_STATE, COMMIT_FAILED);
        }

        final PayrollRunRecord run = new PayrollRunRecord(
                scope,
                bucket,
                now,
                actor.getUUID(),
                draft.payments().size(),
                grossTotal,
                taxTotal);
        data.putPayrollRun(run);
        data.markDirty();

        final Map<String, String> payload = new LinkedHashMap<>();
        payload.put(KEY_SCOPE, scope);
        payload.put(KEY_COUNT, Integer.toString(run.count()));
        payload.put(KEY_GROSS, Long.toString(run.gross()));
        payload.put(KEY_TAX, Long.toString(run.tax()));
        payload.put(KEY_NET, Long.toString(run.net()));
        payload.put(KEY_RUN, txn);
        AuditService.append(
                data, actor, OpCode.PAYROLL_RUN.name(), null, payload, auditReason);

        return EconomyResult.success("Payroll completed.")
                .with(KEY_SCOPE, scope)
                .with(KEY_COUNT, Integer.toString(run.count()))
                .with(KEY_GROSS, Long.toString(run.gross()))
                .with(KEY_TAX, Long.toString(run.tax()))
                .with(KEY_NET, Long.toString(run.net()))
                .with(KEY_RUN, txn)
                .with(KEY_SKIPPED_SELF, Integer.toString(draft.skippedSelf()))
                .with(KEY_SKIPPED_TAX, Integer.toString(draft.skippedTax()));
    }

    private static AccountRecord withBalance(
            final AccountRecord base, final long balance, final long lastSeq) {
        return new AccountRecord(
                base.id(),
                base.type(),
                base.owner(),
                base.business(),
                balance,
                base.status(),
                base.createdAt(),
                lastSeq);
    }
}