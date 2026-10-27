package com.horizonssmp.gov.server;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.horizonssmp.gov.Gov;
import com.horizonssmp.gov.common.GovCapabilities;
import com.horizonssmp.gov.common.records.EconomyMeta;
import com.horizonssmp.gov.common.records.JobRecord;

import net.minecraft.nbt.CompoundTag;

public final class GovSavedDataMigrations {
    private GovSavedDataMigrations() {}

    public static boolean ensureSystemCapability(final GovSavedData data, final String jobId, final com.horizonssmp.gov.common.GovCapability capability) {
        if (jobId == null || capability == null) return false;
        final GovPersonnelStore p = data.personnel;
        final JobRecord job = p.jobs.get(jobId);
        if (job == null || !job.system() || job.capabilities().contains(capability)) return false;
        final java.util.EnumSet<com.horizonssmp.gov.common.GovCapability> next =
                java.util.EnumSet.copyOf(job.capabilities());
        next.add(capability);
        p.jobs.put(jobId, new JobRecord(
                job.id(), job.displayName(), job.color(), job.department(), job.rankLabel(),
                job.system(), next, job.createdAt(), job.createdBy()));
        data.setDirty();
        Gov.LOGGER.info("[gov] migrated system job '{}': added capability {}", jobId, capability.id());
        return true;
    }

    public static void migrateEconomyCapabilities(final GovSavedData data) {
        final GovAccountStore a = data.accountStore;
        if (a.economyMeta.hasMigration(GovCapabilities.ECONOMY_CAPABILITY_MIGRATION_KEY)) return;
        final GovPersonnelStore p = data.personnel;
        for (final Map.Entry<String, Set<com.horizonssmp.gov.common.GovCapability>> entry
                : GovCapabilities.economyCapabilityMigrationSeed().entrySet()) {
            final JobRecord job = p.jobs.get(entry.getKey());
            if (job == null) continue;
            final Set<com.horizonssmp.gov.common.GovCapability> additions =
                    GovCapabilities.economyCapabilityAdditions(job.id(), job.capabilities());
            if (additions.isEmpty()) continue;
            final Set<com.horizonssmp.gov.common.GovCapability> next = GovCapabilities.copyOf(job.capabilities());
            next.addAll(additions);
            p.jobs.put(job.id(), new JobRecord(
                    job.id(), job.displayName(), job.color(), job.department(), job.rankLabel(),
                    job.system(), next, job.createdAt(), job.createdBy()));
            Gov.LOGGER.info("[gov] migrated job '{}': added economy capabilities {}", job.id(), additions);
        }
        a.economyMeta = a.economyMeta.withMigration(GovCapabilities.ECONOMY_CAPABILITY_MIGRATION_KEY);
        data.setDirty();
    }

    public static void migrateOnLoad(final GovSavedData data, final CompoundTag tag, final long now) {
        if (data.schemaVersion > GovSavedData.SCHEMA_VERSION) {
            Gov.LOGGER.warn("[gov] gov.dat has schema version {} but this build knows {}; unknown fields will be dropped",
                    data.schemaVersion, GovSavedData.SCHEMA_VERSION);
        } else if (data.schemaVersion < GovSavedData.SCHEMA_VERSION) {
            Gov.LOGGER.info("[gov] migrating gov.dat from schema version {} to {}",
                    data.schemaVersion, GovSavedData.SCHEMA_VERSION);
            data.schemaVersion = GovSavedData.SCHEMA_VERSION;
            data.setDirty();
        }

        if (data.accountStore.economyMeta == null) {
            data.accountStore.economyMeta = EconomyMeta.empty();
            data.setDirty();
        }
        final boolean governmentMissing = !data.accountStore.accounts.containsKey(GovSavedData.GOVERNMENT_ACCOUNT_ID);
        final boolean policeMissing = !data.accountStore.accounts.containsKey(GovSavedData.POLICE_ACCOUNT_ID);
        final int seeded = data.accountStore.seedEconomyIfNeeded(now);
        if (seeded > 0) {
            final List<String> seededIds = new ArrayList<>(2);
            if (governmentMissing) seededIds.add(GovSavedData.GOVERNMENT_ACCOUNT_ID);
            if (policeMissing) seededIds.add(GovSavedData.POLICE_ACCOUNT_ID);
            Gov.LOGGER.info("[gov] seeded {} economy singleton account(s): {}",
                    seeded, seededIds);
        }
        if (!tag.contains(com.horizonssmp.gov.common.EconomyKeys.ROOT_ACCOUNTS)
                || !tag.contains(com.horizonssmp.gov.common.EconomyKeys.ROOT_LEDGER)
                || !tag.contains(com.horizonssmp.gov.common.EconomyKeys.ROOT_VAULT_TXNS)
                || !tag.contains(com.horizonssmp.gov.common.EconomyKeys.ROOT_CONTRACTS)
                || !tag.contains(com.horizonssmp.gov.common.EconomyKeys.ROOT_PAYROLL_RUNS)
                || !tag.contains(com.horizonssmp.gov.common.EconomyKeys.ROOT_ECONOMY_META)) {
            data.setDirty();
        }
    }
}