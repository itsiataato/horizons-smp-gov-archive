package com.horizonssmp.gov.server;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.horizonssmp.gov.common.GovCapability;
import com.horizonssmp.gov.common.JailKeys;
import com.horizonssmp.gov.common.enums.DetentionStatus;
import com.horizonssmp.gov.common.records.DetentionRecord;
import com.horizonssmp.gov.common.records.LocationRecord;

/**
 * Views and field maps for detentions.
 */
public final class DetentionViews {

    private DetentionViews() {
    }

    public static String canonicalFilter(final String raw) {
        if (raw == null) {
            return JailKeys.FILTER_ACTIVE;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case JailKeys.FILTER_ALL -> JailKeys.FILTER_ALL;
            case JailKeys.FILTER_RELEASED -> JailKeys.FILTER_RELEASED;
            case JailKeys.FILTER_EXPIRED -> JailKeys.FILTER_EXPIRED;
            default -> JailKeys.FILTER_ACTIVE;
        };
    }

    public static DetentionService.Page page(
            final GovSavedData data,
            final UUID viewerUuid,
            final Set<GovCapability> capabilities,
            final String rawFilter,
            final UUID targetFilter,
            final int rawPage,
            final long now) {
        final String filter = canonicalFilter(rawFilter);
        final boolean viewAll = DetentionQueries.canViewAll(capabilities);
        final List<DetentionService.Row> scoped = new ArrayList<>();
        if (data != null) {
            for (final DetentionRecord record : data.detentions().values()) {
                if (viewAll) {
                    if (targetFilter != null && !targetFilter.equals(record.targetUuid())) {
                        continue;
                    }
                } else if (viewerUuid == null || !viewerUuid.equals(record.targetUuid())) {
                    continue;
                }
                scoped.add(new DetentionService.Row(record));
            }
        }
        scoped.sort(Comparator
                .comparingInt((DetentionService.Row row) ->
                        row.record().effectiveStatus(now) == DetentionStatus.ACTIVE ? 0 : 1)
                .thenComparing(Comparator.comparingLong(
                        (DetentionService.Row row) -> row.record().startedAt()).reversed()));
        final int activeTotal = (int) scoped.stream()
                .filter(row -> row.record().effectiveStatus(now) == DetentionStatus.ACTIVE)
                .count();
        final List<DetentionService.Row> matching = new ArrayList<>();
        for (final DetentionService.Row row : scoped) {
            if (matchesFilter(row.record(), filter, now)) {
                matching.add(row);
            }
        }
        final int total = matching.size();
        final int pages = Math.max(1, (total + JailKeys.PAGE_SIZE - 1) / JailKeys.PAGE_SIZE);
        final int page = Math.max(0, Math.min(rawPage, pages - 1));
        final List<DetentionService.Row> rows = new ArrayList<>(JailKeys.PAGE_SIZE);
        final int start = page * JailKeys.PAGE_SIZE;
        for (int i = start; i < Math.min(total, start + JailKeys.PAGE_SIZE); i++) {
            rows.add(matching.get(i));
        }
        return new DetentionService.Page(rows, total, activeTotal, page, pages);
    }

    private static boolean matchesFilter(
            final DetentionRecord record, final String filter, final long now) {
        final DetentionStatus status = record.effectiveStatus(now);
        return switch (filter) {
            case JailKeys.FILTER_RELEASED -> status == DetentionStatus.RELEASED;
            case JailKeys.FILTER_EXPIRED -> status == DetentionStatus.EXPIRED;
            case JailKeys.FILTER_ALL -> true;
            default -> status == DetentionStatus.ACTIVE;
        };
    }

    public static Map<String, String> listFields(
            final GovSavedData data,
            final UUID viewerUuid,
            final String viewerName,
            final Set<GovCapability> capabilities,
            final String rawFilter,
            final UUID targetFilter,
            final int page,
            final List<String> onlineNames,
            final DetentionService.Policy policy,
            final long now) {
        final DetentionService.Policy safe = policy == null ? DetentionService.Policy.fromConfig() : policy;
        final DetentionService.Page result = page(data, viewerUuid, capabilities, rawFilter, targetFilter, page, now);
        final Map<String, String> fields = new LinkedHashMap<>();
        fields.put(JailKeys.CAN_DETAIN, Boolean.toString(capabilities != null
                && capabilities.contains(GovCapability.JAIL_COMMIT)));
        fields.put(JailKeys.CAN_RELEASE, Boolean.toString(capabilities != null
                && capabilities.contains(GovCapability.JAIL_RELEASE)));
        fields.put(JailKeys.CAN_VIEW_ALL, Boolean.toString(DetentionQueries.canViewAll(capabilities)));
        fields.put(JailKeys.NATIVE_CUFFS, Boolean.toString(safe.nativeCuffs()));
        fields.put(JailKeys.MAX_NO_WARRANT_MINUTES, Integer.toString(safe.noWarrantMinutes()));
        fields.put(JailKeys.MAX_WARRANT_MINUTES, Integer.toString(safe.warrantMinutes()));
        final boolean cellsConfigured = !DetentionQueries.allCells(data).isEmpty();
        fields.put(JailKeys.CELL_CONFIGURED, Boolean.toString(cellsConfigured));
        fields.put(JailKeys.ADAPTER, JailAdapter.select(safe.pluginCommand(), safe.nativeFallback(), cellsConfigured).id());
        fields.put(JailKeys.CELL, cellsConfigured
                ? DetentionSupport.formatCell(DetentionQueries.allCells(data).get(0))
                : "");
        fields.put(JailKeys.VIEWER_UUID, viewerUuid == null ? "" : viewerUuid.toString());
        fields.put(JailKeys.VIEWER_NAME, Sanitizer.name(viewerName));
        final Map<String, LocationRecord> runtimeCells =
                data == null ? Map.of() : data.runtimeCells();
        fields.put(JailKeys.CELL_LIST_COUNT, Integer.toString(runtimeCells.size()));
        int cellIndex = 0;
        for (final Map.Entry<String, LocationRecord> entry : runtimeCells.entrySet()) {
            if (cellIndex >= JailKeys.MAX_LISTED_CELLS) {
                break;
            }
            final LocationRecord cell = entry.getValue();
            if (cell == null) {
                continue;
            }
            final String cellPrefix = JailKeys.CELL_ROW_PREFIX + cellIndex;
            fields.put(cellPrefix + JailKeys.CELL_ROW_NAME, Sanitizer.name(entry.getKey()));
            fields.put(cellPrefix + JailKeys.CELL_ROW_DIMENSION,
                    cell.dimension() == null ? "" : cell.dimension());
            fields.put(cellPrefix + JailKeys.CELL_ROW_X, Integer.toString(cell.x()));
            fields.put(cellPrefix + JailKeys.CELL_ROW_Y, Integer.toString(cell.y()));
            fields.put(cellPrefix + JailKeys.CELL_ROW_Z, Integer.toString(cell.z()));
            cellIndex++;
        }
        fields.put(JailKeys.TOTAL, Integer.toString(result.total()));
        fields.put(JailKeys.ACTIVE_TOTAL, Integer.toString(result.activeTotal()));
        fields.put(JailKeys.PAGE, Integer.toString(result.page()));
        fields.put(JailKeys.PAGES, Integer.toString(result.pages()));
        fields.put(JailKeys.FILTER, canonicalFilter(rawFilter));

        for (int i = 0; i < result.rows().size(); i++) {
            final DetentionRecord record = result.rows().get(i).record();
            final String prefix = JailKeys.ROW_PREFIX + i;
            fields.put(prefix + JailKeys.ROW_ID, record.id());
            fields.put(prefix + JailKeys.ROW_TARGET, record.targetName());
            fields.put(prefix + JailKeys.ROW_TARGET_UUID, record.targetUuid().toString());
            fields.put(prefix + JailKeys.ROW_BASIS, DetentionSupport.basisId(record.legalBasis()));
            fields.put(prefix + JailKeys.ROW_OFFICER, record.officerName());
            fields.put(prefix + JailKeys.ROW_STARTED, Long.toString(record.startedAt()));
            fields.put(prefix + JailKeys.ROW_REMAINING_MS,
                    Long.toString(record.remainingMs(now)));
            fields.put(prefix + JailKeys.ROW_CUFFED, Boolean.toString(record.cuffed()));
            fields.put(prefix + JailKeys.ROW_STATUS,
                    record.effectiveStatus(now).name());
            fields.put(prefix + JailKeys.ROW_WARRANT, record.warrantRef());
        }

        final List<String> names = onlineNames == null ? List.of() : onlineNames;
        fields.put(JailKeys.ONLINE_COUNT, Integer.toString(names.size()));
        for (int i = 0; i < names.size() && i < JailKeys.MAX_LISTED_ONLINE; i++) {
            fields.put(JailKeys.ONLINE_PREFIX + i, Sanitizer.name(names.get(i)));
        }
        return fields;
    }

    public static Map<String, String> detailFields(
            final GovSavedData data,
            final DetentionRecord record,
            final Set<GovCapability> capabilities,
            final UUID viewerUuid,
            final DetentionService.Policy policy,
            final long now) {
        if (record == null) {
            return Map.of();
        }
        final DetentionRecord shown = data == null || data.detention(record.id()) == null
                ? record : data.detention(record.id());
        final DetentionService.Policy safe = policy == null ? DetentionService.Policy.fromConfig() : policy;
        final boolean active = shown.active();
        final boolean canRelease = capabilities != null
                && capabilities.contains(GovCapability.JAIL_RELEASE);
        final boolean canDetain = capabilities != null
                && capabilities.contains(GovCapability.JAIL_COMMIT);
        final Map<String, String> fields = new LinkedHashMap<>();
        fields.put(JailKeys.ID, shown.id());
        fields.put(JailKeys.TARGET, shown.targetName());
        fields.put(JailKeys.TARGET_UUID, shown.targetUuid().toString());
        fields.put(JailKeys.OFFICER, shown.officerName());
        fields.put(JailKeys.OFFICER_UUID,
                shown.officerUuid() == null ? "" : shown.officerUuid().toString());
        fields.put(JailKeys.BASIS, DetentionSupport.basisId(shown.legalBasis()));
        fields.put(JailKeys.REASON, shown.reason());
        fields.put(JailKeys.WARRANT_REF, shown.warrantRef());
        fields.put(JailKeys.STARTED_AT, Long.toString(shown.startedAt()));
        fields.put(JailKeys.MAX_MINUTES, Integer.toString(shown.maxDurationMinutes()));
        fields.put(JailKeys.EXPIRES_AT, Long.toString(shown.expiresAt()));
        fields.put(JailKeys.REMAINING_MS, Long.toString(shown.remainingMs(now)));
        fields.put(JailKeys.STATUS, shown.effectiveStatus(now).name());
        fields.put(JailKeys.ACTIVE, Boolean.toString(active));
        fields.put(JailKeys.CUFFED, Boolean.toString(shown.cuffed()));
        fields.put(JailKeys.CELL_LOCATION,
                shown.cell().isSet() ? DetentionSupport.formatCell(shown.cell()) : "");
        fields.put(JailKeys.RELEASED_AT, Long.toString(shown.releasedAt()));
        fields.put(JailKeys.RELEASED_BY, shown.releasedByName());
        fields.put(JailKeys.RELEASE_REASON, shown.releaseReason());
        fields.put(JailKeys.EXTENSION_COUNT, Integer.toString(shown.extensionCount()));
        fields.put(JailKeys.EXTENDED_AT, Long.toString(shown.extendedAt()));
        fields.put(JailKeys.EXTENDED_BY, shown.extendedByName());
        fields.put(JailKeys.EXTENSION_REASON, shown.extensionReason());
        fields.put(JailKeys.RECORD_ADAPTER, shown.jailAdapter());
        fields.put(JailKeys.ADAPTER, safe.adapterKind().id());
        fields.put(JailKeys.CAN_RELEASE_THIS, Boolean.toString(active && canRelease));
        fields.put(JailKeys.CAN_EXTEND_THIS, Boolean.toString(active && canDetain
                && shown.legalBasis() == com.horizonssmp.gov.common.enums.LegalBasis.WARRANT && !shown.warrantRef().isBlank()));
        fields.put(JailKeys.CAN_CUFF_THIS, Boolean.toString(active && canDetain
                && safe.nativeCuffs() && !shown.cuffed()));
        fields.put(JailKeys.CAN_UNCUFF_THIS, Boolean.toString(active && canRelease
                && shown.cuffed()));
        fields.put(JailKeys.NATIVE_CUFFS, Boolean.toString(safe.nativeCuffs()));
        fields.put(JailKeys.MAX_WARRANT_MINUTES, Integer.toString(safe.warrantMinutes()));
        fields.put(JailKeys.VIEWER_UUID, viewerUuid == null ? "" : viewerUuid.toString());
        return fields;
    }

    public static List<String> filters() {
        return List.of(
                JailKeys.FILTER_ACTIVE, JailKeys.FILTER_RELEASED,
                JailKeys.FILTER_EXPIRED, JailKeys.FILTER_ALL);
    }
}
