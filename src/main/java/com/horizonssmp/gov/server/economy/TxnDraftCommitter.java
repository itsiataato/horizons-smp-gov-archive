package com.horizonssmp.gov.server.economy;

import java.util.LinkedHashMap;
import java.util.Map;

import com.horizonssmp.gov.common.economy.EconomyMath;
import com.horizonssmp.gov.common.records.AccountRecord;
import com.horizonssmp.gov.common.records.EconomyMeta;
import com.horizonssmp.gov.common.records.LedgerEntry;
import com.horizonssmp.gov.server.GovSavedData;

final class TxnDraftCommitter {

    private TxnDraftCommitter() {
    }

    public static void commit(final TxnDraft draft, final AccountService service) {
        final GovSavedData data = service.data();
        final long seq = service.nextSeq();
        if (seq == Long.MAX_VALUE) {
            throw new IllegalStateException("ledger sequence space exhausted at Long.MAX_VALUE");
        }
        final long at = draft.ledger().at() > 0L ? draft.ledger().at() : service.now();

        final Map<String, Long> deltas = new LinkedHashMap<>();
        for (final TxnDraft.Line line : draft.debits()) {
            TxnDraftValidator.accumulate(deltas, line.accountId(), -line.amount());
        }
        for (final TxnDraft.Line line : draft.credits()) {
            TxnDraftValidator.accumulate(deltas, line.accountId(), line.amount());
        }

        final Map<String, AccountRecord> previous = new LinkedHashMap<>(deltas.size());
        final Map<String, AccountRecord> replacements = new LinkedHashMap<>(deltas.size());
        for (final Map.Entry<String, Long> entry : deltas.entrySet()) {
            final AccountRecord current = data.account(entry.getKey());
            if (current == null) {
                throw new IllegalStateException(
                        "validated account '" + entry.getKey() + "' disappeared before commit");
            }
            previous.put(entry.getKey(), current);
            final long balance = EconomyMath.addExact(current.balance(), entry.getValue());
            replacements.put(entry.getKey(), new AccountRecord(
                    current.id(),
                    current.type(),
                    current.owner(),
                    current.business(),
                    balance,
                    current.status(),
                    current.createdAt(),
                    seq));
        }
        for (final AccountRecord replacement : replacements.values()) {
            data.putAccount(replacement);
        }

        final LedgerEntry entry = new LedgerEntry(
                seq,
                at,
                draft.ledger().txn(),
                draft.ledger().kind(),
                draft.ledger().from(),
                draft.ledger().to(),
                draft.ledger().gross(),
                draft.ledger().tax(),
                draft.ledger().actor(),
                draft.ledger().reason());
        try {
            data.appendLedger(entry);
        } catch (final RuntimeException failure) {
            for (final Map.Entry<String, AccountRecord> restore : previous.entrySet()) {
                data.putAccount(restore.getValue());
            }
            throw failure;
        }

        final EconomyMeta meta = data.economyMeta();
        final long deposited;
        final long withdrawn;
        final long externalDelta = draft.externalDelta();
        try {
            if (externalDelta > 0L) {
                deposited = EconomyMath.addExact(meta.totalDeposited(), externalDelta);
                withdrawn = meta.totalWithdrawn();
            } else {
                deposited = meta.totalDeposited();
                withdrawn = EconomyMath.addExact(meta.totalWithdrawn(), -externalDelta);
            }
        } catch (final ArithmeticException overflow) {
            throw new IllegalStateException("economy counters overflowed on commit", overflow);
        }
        data.setEconomyMeta(new EconomyMeta(
                deposited,
                withdrawn,
                meta.contractEscrow(),
                seq + 1L,
                meta.nextContractSeq(),
                meta.nonces(),
                meta.migrations()));
        data.markDirty();
        service.assertConservation();
    }
}