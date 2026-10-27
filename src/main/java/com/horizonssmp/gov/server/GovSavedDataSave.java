package com.horizonssmp.gov.server;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.horizonssmp.gov.common.BusinessKeys;
import com.horizonssmp.gov.common.EconomyKeys;
import com.horizonssmp.gov.common.records.AccountRecord;
import com.horizonssmp.gov.common.records.AuditEntry;
import com.horizonssmp.gov.common.records.BusinessRecord;
import com.horizonssmp.gov.common.records.ContractRecord;
import com.horizonssmp.gov.common.records.DetentionRecord;
import com.horizonssmp.gov.common.records.HistoryEvent;
import com.horizonssmp.gov.common.records.JobRecord;
import com.horizonssmp.gov.common.records.LedgerEntry;
import com.horizonssmp.gov.common.records.LicenseRecord;
import com.horizonssmp.gov.common.records.LocationRecord;
import com.horizonssmp.gov.common.records.NoteRecord;
import com.horizonssmp.gov.common.records.PayrollRunRecord;
import com.horizonssmp.gov.common.records.PermitRecord;
import com.horizonssmp.gov.common.records.PlayerRecord;
import com.horizonssmp.gov.common.records.VehicleRecord;
import com.horizonssmp.gov.common.records.WeaponRecord;
import com.horizonssmp.gov.common.records.WarrantRecord;
import com.horizonssmp.gov.common.records.BountyRecord;
import com.horizonssmp.gov.common.records.FineRecord;
import com.horizonssmp.gov.common.records.PlateScanRecord;
import com.horizonssmp.gov.common.records.AreaPermitRecord;
import com.horizonssmp.gov.common.records.NbtSupport;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

public final class GovSavedDataSave {
    private GovSavedDataSave() {}

    public static CompoundTag save(final GovSavedData data, final CompoundTag tag, final HolderLookup.Provider registries) {
        tag.putInt("schemaVersion", data.schemaVersion);
        tag.putBoolean("templatesSeeded", data.templatesSeeded);
        tag.putLong("auditSeq", data.auditHistory.auditSeq());

        final GovPersonnelStore p = data.personnel;
        NbtSupport.putCompoundList(tag, "players",
                p.players.values().stream().map(PlayerRecord::write).toList());
        NbtSupport.putCompoundList(tag, "jobs",
                p.jobs.values().stream().map(JobRecord::write).toList());
        NbtSupport.putCompoundList(tag, "businesses",
                p.businesses.values().stream().map(BusinessRecord::write).toList());
        final List<CompoundTag> cellTags = new ArrayList<>();
        for (final Map.Entry<String, LocationRecord> entry : p.runtimeCells.entrySet()) {
            final CompoundTag cellTag = entry.getValue().write();
            cellTag.putString("name", entry.getKey());
            cellTags.add(cellTag);
        }
        NbtSupport.putCompoundList(tag, "jailCells", cellTags);

        final GovRecordsStore r = data.records;
        NbtSupport.putCompoundList(tag, "vehicles",
                r.vehicles.values().stream().map(VehicleRecord::write).toList());
        NbtSupport.putCompoundList(tag, "weapons",
                r.weapons.values().stream().map(WeaponRecord::write).toList());
        NbtSupport.putCompoundList(tag, "fines",
                r.fines.values().stream().map(FineRecord::write).toList());
        NbtSupport.putCompoundList(tag, "notes",
                data.noteStore.notes.values().stream().map(NoteRecord::write).toList());
        NbtSupport.putCompoundList(tag, "warrants",
                r.warrants.values().stream().map(WarrantRecord::write).toList());
        NbtSupport.putCompoundList(tag, "bounties",
                r.bounties.values().stream().map(BountyRecord::write).toList());
        NbtSupport.putCompoundList(tag, "plateScans",
                r.plateScans.values().stream().map(PlateScanRecord::write).toList());
        NbtSupport.putCompoundList(tag, "licenses",
                r.licenses.values().stream().map(LicenseRecord::write).toList());
        NbtSupport.putCompoundList(tag, "weaponPermits",
                r.weaponPermits.values().stream().map(PermitRecord::write).toList());
        NbtSupport.putCompoundList(tag, "areaPermits",
                r.areaPermits.values().stream().map(AreaPermitRecord::write).toList());
        NbtSupport.putCompoundList(tag, "detentions",
                data.detentionStore.detentions.values().stream().map(DetentionRecord::write).toList());

        final GovAccountStore a = data.accountStore;
        NbtSupport.putCompoundList(tag, EconomyKeys.ROOT_ACCOUNTS,
                a.accounts.values().stream().map(AccountRecord::toTag).toList());
        NbtSupport.putCompoundList(tag, EconomyKeys.ROOT_LEDGER,
                a.ledger.stream().map(LedgerEntry::toTag).toList());
        NbtSupport.putCompoundList(tag, EconomyKeys.ROOT_VAULT_TXNS,
                a.vaultTxns.stream().map(CompoundTag::copy).toList());
        final GovContractStore c = data.contractStore;
        NbtSupport.putCompoundList(tag, EconomyKeys.ROOT_CONTRACTS,
                c.contracts.values().stream().map(ContractRecord::toTag).toList());
        NbtSupport.putCompoundList(tag, EconomyKeys.ROOT_PAYROLL_RUNS,
                a.payrollRuns.values().stream().map(PayrollRunRecord::toTag).toList());
        tag.put(EconomyKeys.ROOT_ECONOMY_META, a.economyMeta.toTag());

        NbtSupport.putCompoundList(tag, "audit",
                data.auditHistory.audit.stream().map(AuditEntry::write).toList());

        final List<CompoundTag> historyRows = new ArrayList<>();
        for (final Map.Entry<UUID, List<HistoryEvent>> entry : data.auditHistory.history.entrySet()) {
            for (final HistoryEvent event : entry.getValue()) {
                final CompoundTag row = event.write();
                NbtSupport.putUuid(row, "player", entry.getKey());
                historyRows.add(row);
            }
        }
        NbtSupport.putCompoundList(tag, "history", historyRows);

        for (final String key : data.extraRoot.getAllKeys()) {
            if (!GovSavedData.KNOWN_ROOT_KEYS.contains(key)) {
                tag.put(key, data.extraRoot.get(key).copy());
            }
        }
        return tag;
    }
}