package com.horizonssmp.gov.server;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.horizonssmp.gov.common.AuditKeys;
import com.horizonssmp.gov.common.GovCapability;
import com.horizonssmp.gov.common.GovFields;
import com.horizonssmp.gov.common.records.AuditEntry;

import java.time.Instant;

/**
 * Field-map builders for {@link AuditService}. Holds the {@code AUDIT_PAGE} and
 * {@code AUDIT_INFO} answer construction so {@code AuditService} stays small.
 * Behavior is identical to the previous in-class implementation.
 */
public final class AuditFields {

    private AuditFields() {
    }

    public static Map<String, String> listFields(
            final GovSavedData data, final AuditService.Filter filter, final int page,
            final Set<GovCapability> capabilities) {
        final AuditService.Page result = AuditService.page(data, filter, page);
        final Map<String, String> fields = new LinkedHashMap<>();
        final boolean canExport = capabilities != null
                && capabilities.contains(GovCapability.AUDIT_VIEW);
        final boolean canAdmin = capabilities != null
                && capabilities.contains(GovCapability.SEASON_ADMIN);
        fields.put(AuditKeys.CAN_EXPORT, Boolean.toString(canExport));
        fields.put(AuditKeys.CAN_ADMIN, Boolean.toString(canAdmin));
        fields.put(AuditKeys.COUNT, Integer.toString(data == null ? 0 : data.audit().size()));
        fields.put(AuditKeys.MATCHING, Integer.toString(result.matching()));
        fields.put(AuditKeys.PAGE, Integer.toString(result.page()));
        fields.put(AuditKeys.PAGES, Integer.toString(result.pages()));
        fields.put(AuditKeys.RETENTION, Integer.toString(GovSavedData.MAX_AUDIT_ENTRIES));
        final long oldest = data == null || data.audit().isEmpty() ? 0L : data.audit().get(0).at();
        final long newest = data == null || data.audit().isEmpty()
                ? 0L : data.audit().get(data.audit().size() - 1).at();
        fields.put(AuditKeys.OLDEST_AT, Long.toString(oldest));
        fields.put(AuditKeys.NEWEST_AT, Long.toString(newest));
        fields.put(AuditKeys.WINDOW, filter == null ? AuditKeys.DEFAULT_WINDOW : filter.window());
        fields.put(AuditKeys.OUTCOME,
                filter == null || filter.outcome() == null
                        ? AuditKeys.OUTCOME_ALL : filter.outcome().name().toLowerCase(Locale.ROOT));
        fields.put(AuditKeys.FILTER_ACTOR, filter == null ? "" : filter.actor());
        fields.put(AuditKeys.FILTER_ACTION, filter == null ? "" : filter.action());
        fields.put(AuditKeys.FILTER_TARGET, filter == null ? "" : filter.target());

        for (int i = 0; i < result.rows().size(); i++) {
            final AuditEntry entry = result.rows().get(i);
            final String prefix = AuditKeys.ROW_PREFIX + i;
            fields.put(prefix + AuditKeys.ROW_SEQ, Long.toString(entry.seq()));
            fields.put(prefix + AuditKeys.ROW_AT, Long.toString(entry.at()));
            fields.put(prefix + AuditKeys.ROW_ACTOR, entry.actorName());
            fields.put(prefix + AuditKeys.ROW_ACTION, entry.action());
            fields.put(prefix + AuditKeys.ROW_TARGET, entry.targetName());
            fields.put(prefix + AuditKeys.ROW_OUTCOME, entry.outcome().name());
        }
        return fields;
    }

    public static Map<String, String> detailFields(final AuditEntry entry) {
        if (entry == null) {
            return Map.of();
        }
        final Map<String, String> fields = new LinkedHashMap<>();
        fields.put(AuditKeys.SEQ, Long.toString(entry.seq()));
        fields.put(AuditKeys.AT, Long.toString(entry.at()));
        fields.put(AuditKeys.AT_ISO,
                entry.at() <= 0L ? "" : Instant.ofEpochMilli(entry.at()).toString());
        fields.put(AuditKeys.ACTOR_UUID,
                entry.actorUuid() == null ? "" : entry.actorUuid().toString());
        fields.put(AuditKeys.ACTOR, entry.actorName());
        fields.put(AuditKeys.ACTOR_JOB, entry.actorJobId());
        fields.put(AuditKeys.ACTION, entry.action());
        fields.put(AuditKeys.TARGET_UUID,
                entry.targetUuid() == null ? "" : entry.targetUuid().toString());
        fields.put(AuditKeys.TARGET, entry.targetName());
        fields.put(AuditKeys.PAYLOAD, entry.payload());
        fields.put(AuditKeys.REASON, entry.reason());
        fields.put(AuditKeys.DETAIL_OUTCOME, entry.outcome().name());
        return fields;
    }
}