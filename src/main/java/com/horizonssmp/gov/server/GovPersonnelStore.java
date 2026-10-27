package com.horizonssmp.gov.server;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.horizonssmp.gov.Gov;
import com.horizonssmp.gov.common.GovCapabilities;
import com.horizonssmp.gov.common.records.BusinessRecord;
import com.horizonssmp.gov.common.records.JobRecord;
import com.horizonssmp.gov.common.records.LocationRecord;
import com.horizonssmp.gov.common.records.PlayerRecord;

public final class GovPersonnelStore {
    private final GovSavedData owner;
    final Map<UUID, PlayerRecord> players = new LinkedHashMap<>();
    final Map<String, JobRecord> jobs = new LinkedHashMap<>();
    final Map<String, BusinessRecord> businesses = new LinkedHashMap<>();
    final Map<String, LocationRecord> runtimeCells = new LinkedHashMap<>();

    public GovPersonnelStore(final GovSavedData owner) {
        this.owner = owner;
    }

    Map<UUID, PlayerRecord> playersMap() { return players; }
    Map<String, JobRecord> jobsMap() { return jobs; }
    Map<String, BusinessRecord> businessesMap() { return businesses; }
    Map<String, LocationRecord> runtimeCellsMap() { return runtimeCells; }

    public Map<UUID, PlayerRecord> players() {
        return Collections.unmodifiableMap(players);
    }

    public PlayerRecord player(final UUID uuid) {
        return uuid == null ? null : players.get(uuid);
    }

    public PlayerRecord playerOrCreate(final UUID uuid, final String name, final long now) {
        final PlayerRecord existing = players.get(uuid);
        if (existing != null) {
            return existing;
        }
        final PlayerRecord fresh = PlayerRecord.blank(uuid, name, now);
        players.put(uuid, fresh);
        owner.setDirty();
        return fresh;
    }

    public void putPlayer(final PlayerRecord record) {
        players.put(record.uuid(), record);
        owner.setDirty();
    }

    public Optional<PlayerRecord> playerByName(final String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        for (final PlayerRecord record : players.values()) {
            if (record.lastName().equalsIgnoreCase(name)) {
                return Optional.of(record);
            }
        }
        return Optional.empty();
    }

    public Map<String, JobRecord> jobs() {
        return Collections.unmodifiableMap(jobs);
    }

    public JobRecord job(final String id) {
        return id == null ? null : jobs.get(id);
    }

    public void putJob(final JobRecord job) {
        jobs.put(job.id(), job);
        owner.setDirty();
    }

    public boolean removeJob(final String id) {
        final JobRecord existing = jobs.get(id);
        if (existing == null || existing.system()) {
            return false;
        }
        for (final PlayerRecord player : players.values()) {
            if (GovCapabilities.holds(player, id)) {
                return false;
            }
        }
        jobs.remove(id);
        owner.setDirty();
        return true;
    }

    public long templateCount() {
        return jobs.values().stream().filter(job -> !job.system()).count();
    }

    public Map<String, BusinessRecord> businesses() {
        return Collections.unmodifiableMap(businesses);
    }

    public BusinessRecord business(final String id) {
        return id == null ? null : businesses.get(id);
    }

    public void putBusiness(final BusinessRecord business) {
        businesses.put(business.id(), business);
        owner.setDirty();
    }

    public void removeBusiness(final String id) {
        if (id != null && !id.isBlank() && businesses.remove(id) != null) {
            owner.setDirty();
        }
    }

    public Map<String, LocationRecord> runtimeCells() {
        return Collections.unmodifiableMap(runtimeCells);
    }

    public boolean addRuntimeCell(final String name, final LocationRecord cell) {
        if (name == null || name.isBlank() || cell == null || !cell.isSet()) {
            return false;
        }
        if (runtimeCells.containsKey(name)) {
            return false;
        }
        runtimeCells.put(name, cell);
        owner.setDirty();
        return true;
    }

    public boolean removeRuntimeCell(final String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        if (runtimeCells.remove(name) != null) {
            owner.setDirty();
            return true;
        }
        return false;
    }

    int seedJob(final String id, final String displayName, final String color,
            final String department, final String rankLabel, final boolean system, final long now) {
        if (jobs.containsKey(id)) {
            return 0;
        }
        jobs.put(id, new JobRecord(
                id, displayName, color, department, rankLabel, system,
                GovCapabilities.builtinJobCapabilities(id), now, null));
        return 1;
    }
}