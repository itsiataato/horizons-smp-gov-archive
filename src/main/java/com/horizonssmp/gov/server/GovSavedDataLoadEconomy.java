package com.horizonssmp.gov.server;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import com.horizonssmp.gov.Gov;
import com.horizonssmp.gov.common.BusinessKeys;
import com.horizonssmp.gov.common.EconomyKeys;
import com.horizonssmp.gov.common.GovCapabilities;
import com.horizonssmp.gov.common.records.AccountRecord;
import com.horizonssmp.gov.common.records.AuditEntry;
import com.horizonssmp.gov.common.records.ContractRecord;
import com.horizonssmp.gov.common.records.EconomyMeta;
import com.horizonssmp.gov.common.records.HistoryEvent;
import com.horizonssmp.gov.common.records.LedgerEntry;
import com.horizonssmp.gov.common.records.NbtSupport;
import com.horizonssmp.gov.common.records.PayrollRunRecord;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

public final class GovSavedDataLoadEconomy {
    private GovSavedDataLoadEconomy() {}

    public static void load(final GovSavedData data, final CompoundTag tag) {
        final GovAccountStore a = data.accountStore;
        for (final CompoundTag element : NbtSupport.getCompoundList(tag, EconomyKeys.ROOT_ACCOUNTS)) {
            readInto(element, EconomyKeys.ROOT_ACCOUNTS, () -> {
                final AccountRecord record = AccountRecord.fromTag(element);
                a.accounts.put(record.id(), record);
            });
        }

        final List<LedgerEntry> loadedLedger = new ArrayList<>();
        for (final CompoundTag element : NbtSupport.getCompoundList(tag, EconomyKeys.ROOT_LEDGER)) {
            readInto(element, EconomyKeys.ROOT_LEDGER, () -> {
                final LedgerEntry entry = LedgerEntry.fromTag(element);
                if (entry.seq() < 1L) {
                    Gov.LOGGER.warn("[gov] skipping one entry in '{}': non-positive sequence {}",
                            EconomyKeys.ROOT_LEDGER, entry.seq());
                    return;
                }
                loadedLedger.add(entry);
            });
        }
        loadedLedger.sort(Comparator.comparingLong(LedgerEntry::seq));
        int droppedLedger = 0;
        long previousLedgerSeq = 0L;
        for (final LedgerEntry entry : loadedLedger) {
            if (entry.seq() <= previousLedgerSeq) {
                droppedLedger++;
                continue;
            }
            a.ledger.add(entry);
            previousLedgerSeq = entry.seq();
        }
        a.maxLedgerSeq = previousLedgerSeq;
        if (droppedLedger > 0) {
            Gov.LOGGER.warn("[gov] dropped {} duplicate ledger row(s) in '{}' on load",
                    droppedLedger, EconomyKeys.ROOT_LEDGER);
            data.setDirty();
        }

        int droppedVault = 0;
        for (final CompoundTag element : NbtSupport.getCompoundList(tag, EconomyKeys.ROOT_VAULT_TXNS)) {
            if (element.getAllKeys().isEmpty()) {
                droppedVault++;
                continue;
            }
            a.vaultTxns.add(element.copy());
        }
        final int overVaultBound = a.vaultTxns.size() - GovSavedData.MAX_VAULT_TXNS;
        if (overVaultBound > 0) {
            a.vaultTxns.subList(0, overVaultBound).clear();
            droppedVault += overVaultBound;
        }
        if (droppedVault > 0) {
            Gov.LOGGER.warn("[gov] dropped {} vault audit row(s) in '{}' on load",
                    droppedVault, EconomyKeys.ROOT_VAULT_TXNS);
            data.setDirty();
        }

        final GovContractStore c = data.contractStore;
        for (final CompoundTag element : NbtSupport.getCompoundList(tag, EconomyKeys.ROOT_CONTRACTS)) {
            readInto(element, EconomyKeys.ROOT_CONTRACTS, () -> {
                final ContractRecord record = ContractRecord.fromTag(element);
                c.contracts.put(record.id(), record);
            });
        }

        for (final CompoundTag element : NbtSupport.getCompoundList(tag, EconomyKeys.ROOT_PAYROLL_RUNS)) {
            readInto(element, EconomyKeys.ROOT_PAYROLL_RUNS, () -> {
                final PayrollRunRecord run = PayrollRunRecord.fromTag(element);
                a.payrollRuns.put(run.scope() + "|" + run.dayBucket(), run);
            });
        }

        if (tag.contains(EconomyKeys.ROOT_ECONOMY_META, Tag.TAG_COMPOUND)) {
            try {
                a.economyMeta = EconomyMeta.fromTag(tag.getCompound(EconomyKeys.ROOT_ECONOMY_META));
            } catch (final RuntimeException e) {
                Gov.LOGGER.warn("[gov] unreadable '{}' block: {}; using empty economy meta",
                        EconomyKeys.ROOT_ECONOMY_META, e.toString());
                a.economyMeta = EconomyMeta.empty();
                data.setDirty();
            }
        } else if (tag.contains(EconomyKeys.ROOT_ECONOMY_META)) {
            Gov.LOGGER.warn("[gov] '{}' is not a compound; using empty economy meta",
                    EconomyKeys.ROOT_ECONOMY_META);
            data.setDirty();
        }

        data.ensureSystemCapability(GovCapabilities.GOVEXECUTIVE_JOB_ID, com.horizonssmp.gov.common.GovCapability.MILITARY_AUTHORITY);
        data.ensureSystemCapability(GovCapabilities.GOVEXECUTIVE_JOB_ID, com.horizonssmp.gov.common.GovCapability.SEASON_ADMIN);
        data.ensureSystemCapability(GovCapabilities.GOVEXECUTIVE_JOB_ID, com.horizonssmp.gov.common.GovCapability.BUSINESS_CLOSE_APPROVE);
        data.migrateEconomyCapabilities();

        for (final CompoundTag element : NbtSupport.getCompoundList(tag, "audit")) {
            if (!element.contains("outcome")) {
                data.setDirty();
                break;
            }
        }

        final GovAuditHistoryStore ah = data.auditHistory;
        for (final CompoundTag element : NbtSupport.getCompoundList(tag, "audit")) {
            readInto(element, "audit", () -> {
                final AuditEntry entry = AuditEntry.read(element, GovAuditHistoryStore.nextAuditSequence(ah.auditSeq));
                ah.audit.add(entry);
                ah.auditSeq = Math.max(ah.auditSeq, entry.seq());
            });
        }
        final int dropped = ah.trimAuditTo(GovSavedData.MAX_AUDIT_ENTRIES);
        if (dropped > 0) {
            Gov.LOGGER.warn("[gov] gov.dat held more than {} audit entries; dropped the {} oldest on load",
                    GovSavedData.MAX_AUDIT_ENTRIES, dropped);
        }

        int droppedHistory = 0;
        for (final CompoundTag element : NbtSupport.getCompoundList(tag, "history")) {
            try {
                final UUID playerUuid = NbtSupport.getUuid(element, "player");
                final HistoryEvent event = HistoryEvent.read(element);
                if (playerUuid == null || event == null) {
                    droppedHistory++;
                    continue;
                }
                final List<HistoryEvent> events = ah.history.computeIfAbsent(playerUuid, key -> new ArrayList<>());
                if (events.size() >= BusinessKeys.MAX_HISTORY_PER_PLAYER) {
                    droppedHistory++;
                    continue;
                }
                events.add(event);
                ah.historyEventCount++;
            } catch (final RuntimeException e) {
                droppedHistory++;
            }
        }
        ah.trimHistory();
        if (droppedHistory > 0) {
            Gov.LOGGER.warn("[gov] skipped {} unreadable or over-cap history row(s) in 'history'", droppedHistory);
        }

        data.migrateOnLoad(tag, System.currentTimeMillis());

        for (final String key : tag.getAllKeys()) {
            if (!GovSavedData.KNOWN_ROOT_KEYS.contains(key)) {
                data.extraRoot.put(key, tag.get(key).copy());
            }
        }
        if (!data.extraRoot.getAllKeys().isEmpty()) {
            Gov.LOGGER.info("[gov] preserving {} unknown gov.dat root section(s): {}",
                    data.extraRoot.getAllKeys().size(), data.extraRoot.getAllKeys());
        }
    }

    private static void readInto(final CompoundTag element, final String section, final Runnable reader) {
        try {
            reader.run();
        } catch (final RuntimeException e) {
            Gov.LOGGER.warn("[gov] skipping one unreadable entry in '{}': {}", section, e.toString());
        }
    }
}