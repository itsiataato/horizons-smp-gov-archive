package com.horizonssmp.gov.client;

import java.util.Map;

import com.horizonssmp.gov.network.GovActionResultPayload;

final class GovPeopleUtil {
    static int intField(final Map<String, String> fields, final String key) {
        return com.horizonssmp.gov.common.GovFields.intField(fields, key);
    }

    static long longField(final Map<String, String> fields, final String key) {
        return com.horizonssmp.gov.common.GovFields.longField(fields, key);
    }

    static String shortTime(final long epochMillis) {
        if (epochMillis <= 0L) {
            return "-";
        }
        return GovPeoplePanelModel.SHORT_TIME.format(java.time.Instant.ofEpochMilli(epochMillis));
    }

    static String fallbackError(final GovActionResultPayload payload, final String key) {
        return payload.errorText().isBlank()
                ? GovPeoplePanel.tr(key) : payload.errorText();
    }

    static GovPeoplePanelModel.NoteRow findNote(final GovPeoplePanel p, final String id) {
        for (final GovPeoplePanelModel.NoteRow note : p.notes) {
            if (note.id().equals(id)) {
                return note;
            }
        }
        return null;
    }

    static String jobValue(final GovPeoplePanel p) {
        if (p.cardJob.isBlank()) {
            return p.unemployedText;
        }
        return p.cardJobId.isBlank()
                ? p.cardJob : p.cardJob + " (" + p.cardJobId + ")";
    }

    static String displayName(final GovPeoplePanel p) {
        if (p.detailState != GovPeoplePanelModel.LoadState.READY && p.selectedName != null
                && !p.selectedName.isBlank()) {
            return p.selectedName;
        }
        if (p.cardName != null && !p.cardName.isBlank()) {
            return p.cardName;
        }
        return p.selectedName;
    }

    static boolean mutationPending(final GovPeoplePanel p) {
        return p.pending.containsValue(GovPeoplePanelModel.RequestKind.MUTATE);
    }
}
