package com.horizonssmp.gov.server;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.horizonssmp.gov.common.GovCapabilities;
import com.horizonssmp.gov.common.enums.DutyState;
import com.horizonssmp.gov.common.records.JailRecord;
import com.horizonssmp.gov.common.records.JobRecord;
import com.horizonssmp.gov.common.records.PlayerRecord;

public final class GovClearSeason {
    private GovClearSeason() {}

    public static Map<String, Integer> clearSeason(final GovSavedData data, final boolean clearBusinesses, final long now) {
        final GovPersonnelStore p = data.personnel;
        final JobRecord citizen = p.jobs.get(GovCapabilities.CITIZEN_JOB_ID);
        final String citizenRank = citizen == null ? "Citizen" : citizen.rankLabel();
        final List<Map.Entry<UUID, PlayerRecord>> updates = new ArrayList<>();
        for (final Map.Entry<UUID, PlayerRecord> entry : new ArrayList<>(p.players.entrySet())) {
            final PlayerRecord record = entry.getValue();
            PlayerRecord after = record;
            if (!GovCapabilities.CITIZEN_JOB_ID.equals(record.primaryJobId())
                    || !GovCapabilities.CITIZEN_JOB_ID.equals(record.employment().jobId())) {
                after = after.withJob(GovCapabilities.CITIZEN_JOB_ID, citizenRank, now, null, true);
            }
            if (after.duty() != DutyState.N_A) {
                after = after.withDuty(DutyState.N_A);
            }
            if (after.jail().active()) {
                after = after.withJail(JailRecord.NONE);
            }
            if (after != record) {
                updates.add(Map.entry(entry.getKey(), after));
            }
        }

        final Map<String, Integer> counts = new LinkedHashMap<>();
        final GovRecordsStore r = data.records;
        counts.put("licenses", r.licenses.size());
        r.licenses.clear();
        counts.put("vehicles", r.vehicles.size());
        r.vehicles.clear();
        counts.put("weapons", r.weapons.size());
        r.weapons.clear();
        counts.put("permits", r.weaponPermits.size());
        r.weaponPermits.clear();
        counts.put("area-permits", r.areaPermits.size());
        r.areaPermits.clear();
        final GovDetentionStore d = data.detentionStore;
        counts.put("detentions", d.detentions.size());
        d.clearDetentions();
        counts.put("fines", r.fines.size());
        r.fines.clear();
        counts.put("notes", data.noteStore.notes.size());
        data.noteStore.notes.clear();
        final GovAuditHistoryStore ah = data.auditHistory;
        counts.put("history", ah.historyEventCount);
        ah.clearHistory();
        if (clearBusinesses) {
            counts.put("businesses", p.businesses.size());
            p.businesses.clear();
        }

        for (final Map.Entry<UUID, PlayerRecord> update : updates) {
            p.players.put(update.getKey(), update.getValue());
        }
        counts.put("player-jobs", updates.size());
        data.setDirty();
        return counts;
    }
}