package com.horizonssmp.gov.server;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.horizonssmp.gov.common.enums.DutyState;
import com.horizonssmp.gov.common.records.PlayerRecord;
import com.horizonssmp.gov.config.GovConfig;
import com.horizonssmp.gov.Gov;
import com.horizonssmp.gov.common.GovCapabilities;

/**
 * Plan computation helper for {@link SeasonResetService}.
 */
public final class SeasonResetPlanning {

    private SeasonResetPlanning() {
    }

    public static SeasonResetService.Plan plan(
            final GovSavedData data, final boolean clearBusinesses) {
        final Map<String, Integer> counts = new LinkedHashMap<>();
        if (data != null) {
            counts.put("licenses", data.licenses().size());
            counts.put("vehicles", data.vehicles().size());
            counts.put("weapons", data.weapons().size());
            counts.put("permits", data.weaponPermits().size());
            counts.put("area-permits", data.areaPermits().size());
            counts.put("detentions", data.detentions().size());
            counts.put("fines", data.fines().size());
            counts.put("notes", data.notes().size());
            if (clearBusinesses) {
                counts.put("businesses", data.businesses().size());
            }
            counts.put("player-jobs", playersToReset(data));
        } else {
            counts.put("player-jobs", 0);
        }
        return new SeasonResetService.Plan(clearBusinesses, counts, planSections(clearBusinesses));
    }

    static List<String> planSections(final boolean clearBusinesses) {
        final List<String> sections = new ArrayList<>(List.of(
                "licenses", "vehicles", "weapons", "permits", "area-permits",
                "detentions", "fines", "notes"));
        if (clearBusinesses) {
            sections.add("businesses");
        }
        sections.add("player-jobs");
        return sections;
    }

    static int playersToReset(final GovSavedData data) {
        int count = 0;
        for (final PlayerRecord record : data.players().values()) {
            if (!GovCapabilities.CITIZEN_JOB_ID.equals(record.primaryJobId())
                    || !GovCapabilities.CITIZEN_JOB_ID.equals(record.employment().jobId())
                    || record.duty() != DutyState.N_A
                    || record.jail().active()) {
                count++;
            }
        }
        return count;
    }

    static String currentCounts(final GovSavedData data) {
        return SeasonResetService.countsText(
                plan(data, SeasonResetService.Policy.fromConfig().clearBusinesses()).counts());
    }
}