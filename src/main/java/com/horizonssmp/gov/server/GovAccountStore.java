package com.horizonssmp.gov.server;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.horizonssmp.gov.Gov;
import com.horizonssmp.gov.common.EconomyKeys;
import com.horizonssmp.gov.common.enums.AccountStatus;
import com.horizonssmp.gov.common.enums.AccountType;
import com.horizonssmp.gov.common.records.AccountRecord;
import com.horizonssmp.gov.common.records.EconomyMeta;
import com.horizonssmp.gov.common.records.LedgerEntry;
import com.horizonssmp.gov.common.records.PayrollRunRecord;

import net.minecraft.nbt.CompoundTag;

public final class GovAccountStore {
    private final GovSavedData owner;
    final Map<String, AccountRecord> accounts = new LinkedHashMap<>();
    final List<LedgerEntry> ledger = new ArrayList<>();
    long maxLedgerSeq;
    final Map<String, PayrollRunRecord> payrollRuns = new LinkedHashMap<>();
    EconomyMeta economyMeta = EconomyMeta.empty();
    final List<CompoundTag> vaultTxns = new ArrayList<>();

    public GovAccountStore(final GovSavedData owner) {
        this.owner = owner;
    }

    public AccountRecord account(final String id) {
        return id == null || id.isBlank() ? null : accounts.get(id);
    }

    public Map<String, AccountRecord> accounts() {
        return Collections.unmodifiableMap(accounts);
    }

    public void putAccount(final AccountRecord account) {
        if (account == null) throw new IllegalArgumentException("putAccount requires a non-null record");
        accounts.put(account.id(), account);
        owner.setDirty();
    }

    boolean removeAccount(final String id) {
        if (id == null || accounts.remove(id) == null) return false;
        owner.setDirty();
        return true;
    }

    public List<LedgerEntry> ledger() {
        return Collections.unmodifiableList(ledger);
    }

    public void appendLedger(final LedgerEntry entry) {
        if (entry == null) throw new IllegalArgumentException("appendLedger requires a non-null entry");
        if (entry.seq() < 1L) throw new IllegalStateException("ledger sequence must be positive, got " + entry.seq());
        if (entry.seq() <= maxLedgerSeq) {
            throw new IllegalStateException("ledger sequence " + entry.seq()
                    + " is not after the newest stored sequence " + maxLedgerSeq + " (duplicate or out of order)");
        }
        ledger.add(entry);
        maxLedgerSeq = entry.seq();
        owner.setDirty();
    }

    public long nextLedgerSeq() {
        if (maxLedgerSeq == Long.MAX_VALUE) {
            throw new IllegalStateException("ledger sequence space exhausted at Long.MAX_VALUE");
        }
        return Math.max(economyMeta.nextLedgerSeq(), maxLedgerSeq + 1L);
    }

    public Map<String, PayrollRunRecord> payrollRuns() {
        return Collections.unmodifiableMap(payrollRuns);
    }

    public PayrollRunRecord payrollRun(final String scope, final long dayBucket) {
        if (scope == null || scope.isBlank() || dayBucket < 0L) return null;
        return payrollRuns.get(scope + "|" + dayBucket);
    }

    public void putPayrollRun(final PayrollRunRecord run) {
        if (run == null) throw new IllegalArgumentException("putPayrollRun requires a non-null record");
        payrollRuns.put(run.scope() + "|" + run.dayBucket(), run);
        owner.setDirty();
    }

    public EconomyMeta economyMeta() { return economyMeta; }

    public void setEconomyMeta(final EconomyMeta meta) {
        if (meta == null) throw new IllegalArgumentException("setEconomyMeta requires a non-null meta block");
        economyMeta = meta;
        owner.setDirty();
    }

    public List<CompoundTag> vaultTxns() {
        return vaultTxns.stream().map(CompoundTag::copy).toList();
    }

    public void addVaultTxn(final CompoundTag txn) {
        if (txn == null) throw new IllegalArgumentException("addVaultTxn requires a non-null tag");
        vaultTxns.add(txn.copy());
        trimVaultTxns();
        owner.setDirty();
    }

    private void trimVaultTxns() {
        final int excess = vaultTxns.size() - GovSavedData.MAX_VAULT_TXNS;
        if (excess <= 0) return;
        vaultTxns.subList(0, excess).clear();
        Gov.LOGGER.warn("[gov] vault transaction audit exceeded {} rows; dropped the {} oldest",
                GovSavedData.MAX_VAULT_TXNS, excess);
    }

    public int seedEconomyIfNeeded(final long now) {
        int written = 0;
        written += seedAccount(GovSavedData.GOVERNMENT_ACCOUNT_ID, AccountType.GOVERNMENT, now);
        written += seedAccount(GovSavedData.POLICE_ACCOUNT_ID, AccountType.POLICE, now);
        if (written > 0) owner.setDirty();
        return written;
    }

    int seedAccount(final String id, final AccountType type, final long now) {
        if (accounts.containsKey(id)) return 0;
        accounts.put(id, new AccountRecord(id, type, null, null, 0L, AccountStatus.OPEN, now, 0L));
        return 1;
    }

    void migrateEconomyCapabilities(final GovSavedData data) {
        // placeholder, actual in GovEconomyCapabilities? handle in migrations helper
    }
}