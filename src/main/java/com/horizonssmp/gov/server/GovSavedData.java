package com.horizonssmp.gov.server;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.horizonssmp.gov.Gov;
import com.horizonssmp.gov.common.BusinessKeys;
import com.horizonssmp.gov.common.EconomyKeys;
import com.horizonssmp.gov.common.GovCapabilities;
import com.horizonssmp.gov.common.GovCapability;
import com.horizonssmp.gov.common.NoteScope;
import com.horizonssmp.gov.common.enums.AccountStatus;
import com.horizonssmp.gov.common.enums.AccountType;
import com.horizonssmp.gov.common.enums.DutyState;
import com.horizonssmp.gov.common.enums.LicenseClass;
import com.horizonssmp.gov.common.enums.PermitClass;
import com.horizonssmp.gov.common.records.AccountRecord;
import com.horizonssmp.gov.common.records.AreaPermitRecord;
import com.horizonssmp.gov.common.records.BountyRecord;
import com.horizonssmp.gov.common.records.AuditEntry;
import com.horizonssmp.gov.common.records.BusinessRecord;
import com.horizonssmp.gov.common.records.ContractRecord;
import com.horizonssmp.gov.common.records.DetentionRecord;
import com.horizonssmp.gov.common.records.EconomyMeta;
import com.horizonssmp.gov.common.records.FineRecord;
import com.horizonssmp.gov.common.records.HistoryEvent;
import com.horizonssmp.gov.common.records.JobRecord;
import com.horizonssmp.gov.common.records.JailRecord;
import com.horizonssmp.gov.common.records.LedgerEntry;
import com.horizonssmp.gov.common.records.LicenseRecord;
import com.horizonssmp.gov.common.records.LocationRecord;
import com.horizonssmp.gov.common.records.NoteRecord;
import com.horizonssmp.gov.common.records.PayrollRunRecord;
import com.horizonssmp.gov.common.records.PermitRecord;
import com.horizonssmp.gov.common.records.PlateScanRecord;
import com.horizonssmp.gov.common.records.PlayerRecord;
import com.horizonssmp.gov.common.records.VehicleRecord;
import com.horizonssmp.gov.common.records.WarrantRecord;
import com.horizonssmp.gov.common.records.WeaponRecord;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public final class GovSavedData extends SavedData {

    public static final String DATA_NAME = "gov";
    public static final int SCHEMA_VERSION = 2;
    public static final String GOVERNMENT_ACCOUNT_ID = "government";
    public static final String POLICE_ACCOUNT_ID = "police";
    public static final int MAX_VAULT_TXNS = 256;
    public static final int MAX_AUDIT_ENTRIES = 50_000;
    public static final int MAX_HISTORY_ENTRIES = 10_000;

    public static final SavedData.Factory<GovSavedData> FACTORY =
            new SavedData.Factory<>(GovSavedData::new, GovSavedData::load);

    static final Set<String> KNOWN_ROOT_KEYS = Set.of(
            "schemaVersion", "templatesSeeded", "auditSeq", "players", "jobs", "businesses",
            "vehicles", "weapons", "fines", "notes", "warrants", "plateScans", "licenses",
            "weaponPermits", "areaPermits", "detentions", "audit", "history", "bounties", "jailCells",
            EconomyKeys.ROOT_ACCOUNTS, EconomyKeys.ROOT_LEDGER, EconomyKeys.ROOT_VAULT_TXNS,
            EconomyKeys.ROOT_CONTRACTS, EconomyKeys.ROOT_PAYROLL_RUNS,
            EconomyKeys.ROOT_ECONOMY_META);

    final GovPersonnelStore personnel = new GovPersonnelStore(this);
    final GovRecordsStore records = new GovRecordsStore(this);
    final GovNoteStore noteStore = new GovNoteStore(this);
    final GovDetentionStore detentionStore = new GovDetentionStore(this);
    final GovAccountStore accountStore = new GovAccountStore(this);
    final GovContractStore contractStore = new GovContractStore(this);
    final GovAuditHistoryStore auditHistory = new GovAuditHistoryStore(this);

    final CompoundTag extraRoot = new CompoundTag();
    final UUID instanceId = UUID.randomUUID();
    int schemaVersion = SCHEMA_VERSION;
    boolean templatesSeeded;
    // auditSeq lives in auditHistory

    public GovSavedData() {
    }

    // access
    public static GovSavedData get(final MinecraftServer server) {
        return GovGlobalGuard.guardSavedData(server).getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }
    public static GovSavedData get(final ServerLevel level) {
        return level == null ? null : level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }
    public static GovSavedData getIfPresent(final MinecraftServer server) {
        // Read-only by design: no guard and no create, so tick-time callers never throw. The
        // guard throws on a missing overworld by design, which is exactly what this accessor
        // must not do — a missing server or overworld is a null.
        final ServerLevel overworld = server == null ? null : server.overworld();
        return overworld == null ? null : overworld.getDataStorage().get(FACTORY, DATA_NAME);
    }

    public int schemaVersion() { return schemaVersion; }
    UUID instanceId() { return instanceId; }
    public boolean templatesSeeded() { return templatesSeeded; }
    public long auditSeq() { return auditHistory.auditSeq; }

    // players
    public Map<UUID, PlayerRecord> players() { return personnel.players(); }
    public PlayerRecord player(final UUID uuid) { return personnel.player(uuid); }
    public PlayerRecord playerOrCreate(final UUID uuid, final String name, final long now) {
        return personnel.playerOrCreate(uuid, name, now);
    }
    public void putPlayer(final PlayerRecord record) { personnel.putPlayer(record); }
    public Optional<PlayerRecord> playerByName(final String name) { return personnel.playerByName(name); }

    // jobs
    public Map<String, JobRecord> jobs() { return personnel.jobs(); }
    public JobRecord job(final String id) { return personnel.job(id); }
    public void putJob(final JobRecord job) { personnel.putJob(job); }
    public boolean removeJob(final String id) { return personnel.removeJob(id); }
    public long templateCount() { return personnel.templateCount(); }

    // businesses
    public Map<String, BusinessRecord> businesses() { return personnel.businesses(); }
    public BusinessRecord business(final String id) { return personnel.business(id); }
    public void putBusiness(final BusinessRecord business) { personnel.putBusiness(business); }
    public void removeBusiness(final String id) { personnel.removeBusiness(id); }

    // runtime cells
    public Map<String, LocationRecord> runtimeCells() { return personnel.runtimeCells(); }
    public boolean addRuntimeCell(final String name, final LocationRecord cell) { return personnel.addRuntimeCell(name, cell); }
    public boolean removeRuntimeCell(final String name) { return personnel.removeRuntimeCell(name); }

    // records
    public Map<String, VehicleRecord> vehicles() { return records.vehicles(); }
    public VehicleRecord vehicle(final String id) { return records.vehicle(id); }
    public void putVehicle(final VehicleRecord vehicle) { records.putVehicle(vehicle); }

    public Map<String, WeaponRecord> weapons() { return records.weapons(); }
    public WeaponRecord weapon(final String id) { return records.weapon(id); }
    public void putWeapon(final WeaponRecord weapon) { records.putWeapon(weapon); }

    public Map<String, FineRecord> fines() { return records.fines(); }
    public FineRecord fine(final String id) { return records.fine(id); }
    public void putFine(final FineRecord fine) { records.putFine(fine); }

    public Map<String, NoteRecord> notes() { return noteStore.notes(); }
    public NoteRecord note(final String id) { return noteStore.note(id); }
    public void putNote(final NoteRecord note) { noteStore.putNote(note); }
    public boolean removeNote(final String id) { return noteStore.removeNote(id); }
    public List<NoteRecord> notesFor(final UUID targetUuid) { return noteStore.notesFor(targetUuid); }
    public List<NoteRecord> globalNotes() { return noteStore.globalNotes(); }
    public List<NoteRecord> notesInScope(final NoteScope scope, final String scopeId) {
        return noteStore.notesInScope(scope, scopeId);
    }
    public List<NoteRecord> notesInScopeFor(final NoteScope scope, final String scopeId, final UUID targetUuid) {
        return noteStore.notesInScopeFor(scope, scopeId, targetUuid);
    }
    public int scopedNoteCount() { return noteStore.scopedNoteCount(); }
    public int storedScopedNoteCount() { return noteStore.storedScopedNoteCount(); }
    public static boolean isScope(final NoteRecord note, final NoteScope scope) {
        return GovNoteStore.isScope(note, scope);
    }

    public Map<String, WarrantRecord> warrants() { return records.warrants(); }
    public WarrantRecord warrant(final String id) { return records.warrant(id); }
    public void putWarrant(final WarrantRecord warrant) { records.putWarrant(warrant); }

    public Map<String, BountyRecord> bounties() { return records.bounties(); }
    public BountyRecord bounty(final String id) { return records.bounty(id); }
    public void putBounty(final BountyRecord bounty) { records.putBounty(bounty); }

    public Map<String, PlateScanRecord> plateScans() { return records.plateScans(); }
    public void putPlateScan(final PlateScanRecord scan) { records.putPlateScan(scan); }

    public Map<String, LicenseRecord> licenses() { return records.licenses(); }
    public LicenseRecord license(final UUID playerUuid, final LicenseClass licenseClass) {
        return records.license(playerUuid, licenseClass);
    }
    public List<LicenseRecord> licensesFor(final UUID playerUuid) { return records.licensesFor(playerUuid); }
    public void putLicense(final LicenseRecord license) { records.putLicense(license); }

    public Map<String, PermitRecord> weaponPermits() { return records.weaponPermits(); }
    public PermitRecord weaponPermit(final UUID holderUuid, final PermitClass permitClass) {
        return records.weaponPermit(holderUuid, permitClass);
    }
    public List<PermitRecord> weaponPermitsFor(final UUID holderUuid) { return records.weaponPermitsFor(holderUuid); }
    public void putWeaponPermit(final PermitRecord permit) { records.putWeaponPermit(permit); }

    public Map<String, AreaPermitRecord> areaPermits() { return records.areaPermits(); }
    public AreaPermitRecord areaPermit(final String id) { return records.areaPermit(id); }
    public void putAreaPermit(final AreaPermitRecord permit) { records.putAreaPermit(permit); }

    public Map<String, DetentionRecord> detentions() { return detentionStore.detentions(); }
    public DetentionRecord detention(final String id) { return detentionStore.detention(id); }
    public void putDetention(final DetentionRecord detention) { detentionStore.putDetention(detention); }
    public List<DetentionRecord> detentionsForTarget(final UUID targetUuid) {
        return detentionStore.detentionsForTarget(targetUuid);
    }
    void ensureDetentionIndex() { detentionStore.ensureDetentionIndex(); }

    // economy
    public AccountRecord account(final String id) { return accountStore.account(id); }
    public Map<String, AccountRecord> accounts() { return accountStore.accounts(); }
    public void putAccount(final AccountRecord account) { accountStore.putAccount(account); }
    boolean removeAccount(final String id) { return accountStore.removeAccount(id); }

    public List<LedgerEntry> ledger() { return accountStore.ledger(); }
    public void appendLedger(final LedgerEntry entry) { accountStore.appendLedger(entry); }
    public long nextLedgerSeq() { return accountStore.nextLedgerSeq(); }

    public Map<String, ContractRecord> contracts() { return contractStore.contracts(); }
    public ContractRecord contract(final String id) { return contractStore.contract(id); }
    public void putContract(final ContractRecord contract) { contractStore.putContract(contract); }

    public Map<String, PayrollRunRecord> payrollRuns() { return accountStore.payrollRuns(); }
    public PayrollRunRecord payrollRun(final String scope, final long dayBucket) {
        return accountStore.payrollRun(scope, dayBucket);
    }
    public void putPayrollRun(final PayrollRunRecord run) { accountStore.putPayrollRun(run); }

    public EconomyMeta economyMeta() { return accountStore.economyMeta(); }
    public void setEconomyMeta(final EconomyMeta meta) { accountStore.setEconomyMeta(meta); }

    public List<CompoundTag> vaultTxns() { return accountStore.vaultTxns(); }
    public void addVaultTxn(final CompoundTag txn) { accountStore.addVaultTxn(txn); }

    public List<String> contractIdOrder() { return contractStore.contractIdOrder(); }
    public List<String> contractIdsForParty(final UUID party) { return contractStore.contractIdsForParty(party); }
    public List<String> openContractIds() { return contractStore.openContractIds(); }
    public boolean hasOpenContract(final UUID builder) { return contractStore.hasOpenContract(builder); }
    public int openContractCount() { return contractStore.openContractCount(); }
    void ensureContractIndex() { contractStore.ensureContractIndex(); }
    void ensureOpenContractIndex() { contractStore.ensureOpenContractIndex(); }

    // audit/history
    public List<AuditEntry> audit() { return auditHistory.audit(); }
    public long nextAuditSeq() { return auditHistory.nextAuditSeq(); }
    public void addAudit(final AuditEntry entry) { auditHistory.addAudit(entry); }
    int trimAuditTo(final int maximum) { return auditHistory.trimAuditTo(maximum); }
    public static long nextAuditSequence(final long current) {
        return GovAuditHistoryStore.nextAuditSequence(current);
    }

    public List<HistoryEvent> historyFor(final UUID playerUuid) { return auditHistory.historyFor(playerUuid); }
    public void appendHistory(final UUID playerUuid, final HistoryEvent event) {
        auditHistory.appendHistory(playerUuid, event);
    }

    // seeds
    public int seedIfNeeded(final long now) { return seedIfNeeded(now, true); }
    int seedIfNeeded(final long now, final boolean announce) {
        return GovSavedDataSeeds.seedIfNeeded(this, now, announce);
    }
    public int seedEconomyIfNeeded(final long now) { return accountStore.seedEconomyIfNeeded(now); }

    // capability migration
    public boolean ensureSystemCapability(final String jobId, final GovCapability capability) {
        return GovSavedDataMigrations.ensureSystemCapability(this, jobId, capability);
    }
    void migrateEconomyCapabilities() {
        GovSavedDataMigrations.migrateEconomyCapabilities(this);
    }

    // clear season
    public Map<String, Integer> clearSeason(final boolean clearBusinesses, final long now) {
        return GovClearSeason.clearSeason(this, clearBusinesses, now);
    }

    // mark dirty
    public void markDirty() { setDirty(); }

    // nextAuditSeq already

    // serialization
    @Override
    public CompoundTag save(final CompoundTag tag, final HolderLookup.Provider registries) {
        return GovSavedDataSave.save(this, tag, registries);
    }

    public static GovSavedData load(final CompoundTag tag, final HolderLookup.Provider registries) {
        return GovSavedDataLoad.load(tag, registries);
    }

    void migrateOnLoad(final CompoundTag tag, final long now) {
        GovSavedDataMigrations.migrateOnLoad(this, tag, now);
    }

    // expose for load helper? migrateOnLoad called by load helper via data.migrateOnLoad
    // Need public/package access for load helper
    // Make package-private method accessible: already package-private

    // JobRecord import for clear season? not needed

}