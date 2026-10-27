package com.horizonssmp.gov.server;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.horizonssmp.gov.common.PersonKeys;
import com.horizonssmp.gov.common.GovCapabilities;
import com.horizonssmp.gov.common.GovCapability;
import com.horizonssmp.gov.common.records.JobRecord;
import com.horizonssmp.gov.common.records.PlayerRecord;

/**
 * Helper for {@link PersonService} person-card logic.
 */
public final class PersonDetail {

    private PersonDetail() {
    }

    public static Map<String, String> detail(
            final GovSavedData data, final UUID uuid, final String name, final boolean online,
            final Set<GovCapability> viewerCapabilities, final long now) {
        final PlayerRecord record = data == null ? null : data.player(uuid);
        final String displayName = record == null || record.lastName().isBlank()
                ? Sanitizer.name(name)
                : record.lastName();
        final String jobId = record == null ? "" : GovCapabilities.jobIdOf(record);
        final JobRecord job = jobId.isEmpty() ? null : data.job(jobId);
        final boolean canViewNotes = GovCapabilities.has(viewerCapabilities, GovCapability.NOTE_VIEW_ALL);
        final boolean canManageNotes = GovCapabilities.has(viewerCapabilities, GovCapability.NOTE_WRITE);

        final Map<String, String> fields = new LinkedHashMap<>();
        fields.put(PersonKeys.NAME, displayName);
        fields.put(PersonKeys.UUID, uuid == null ? "" : uuid.toString());
        fields.put(PersonKeys.ONLINE, Boolean.toString(online));
        fields.put(PersonKeys.JOB, job == null ? "" : job.displayName());
        fields.put(PersonKeys.JOB_ID, job == null ? "" : job.id());
        fields.put(PersonKeys.RANK, record == null ? "" : record.rank());
        fields.put(PersonKeys.NOTES,
                Integer.toString(canViewNotes ? PersonService.notesOf(data, uuid).size() : 0));
        fields.put(PersonKeys.FINES, Integer.toString(SelfCardService.outstandingFines(data, uuid)));
        fields.put(PersonKeys.LICENSE, VehicleService.summary(data, uuid, now));
        fields.put(PersonKeys.WEAPONS, WeaponService.permitSummary(data, uuid, now));
        fields.put(PersonKeys.CAN_VIEW_NOTES, Boolean.toString(canViewNotes));
        fields.put(PersonKeys.CAN_MANAGE_NOTES, Boolean.toString(canManageNotes));
        return fields;
    }
}