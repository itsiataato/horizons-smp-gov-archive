package com.horizonssmp.gov.server;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.horizonssmp.gov.LawData;
import com.horizonssmp.gov.common.FineKeys;
import com.horizonssmp.gov.common.GovCapability;
import com.horizonssmp.gov.common.enums.FineStatus;
import com.horizonssmp.gov.common.records.FineRecord;
import com.horizonssmp.gov.law.LawIndex;

public final class FineReads {

    private FineReads() {
    }

    public static String canonicalFilter(final String raw) {
        if (raw == null) {
            return FineKeys.FILTER_ALL;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case FineKeys.FILTER_UNPAID -> FineKeys.FILTER_UNPAID;
            case FineKeys.FILTER_PAID -> FineKeys.FILTER_PAID;
            case FineKeys.FILTER_WAIVED -> FineKeys.FILTER_WAIVED;
            default -> FineKeys.FILTER_ALL;
        };
    }

    public static FineService.Page page(
            final GovSavedData data, final UUID viewerUuid, final Set<GovCapability> capabilities,
            final String rawFilter, final UUID targetFilter, final int rawPage) {
        final String filter = canonicalFilter(rawFilter);
        final UUID scope;
        if (FineLookups.canViewAll(capabilities)) {
            scope = targetFilter;
        } else if (viewerUuid == null) {
            return new FineService.Page(List.of(), 0, 0, 1, filter);
        } else {
            scope = viewerUuid;
        }
        final List<FineRecord> matching = new ArrayList<>();
        if (data != null) {
            for (final FineRecord fine : data.fines().values()) {
                if (scope != null && !scope.equals(fine.targetUuid())) {
                    continue;
                }
                if (!matchesFilter(fine, filter)) {
                    continue;
                }
                matching.add(fine);
            }
        }
        matching.sort(Comparator
                .comparingLong(FineRecord::createdAt).reversed()
                .thenComparing(FineRecord::id));

        final int total = matching.size();
        final int pages = Math.max(1, (total + FineKeys.PAGE_SIZE - 1) / FineKeys.PAGE_SIZE);
        final int page = Math.max(0, Math.min(rawPage, pages - 1));
        final List<FineService.FineRow> rows = new ArrayList<>(FineKeys.PAGE_SIZE);
        final int start = page * FineKeys.PAGE_SIZE;
        for (int i = start; i < Math.min(total, start + FineKeys.PAGE_SIZE); i++) {
            final FineRecord fine = matching.get(i);
            rows.add(new FineService.FineRow(fine, FineLookups.targetName(data, fine.targetUuid())));
        }
        return new FineService.Page(rows, total, page, pages, filter);
    }

    private static boolean matchesFilter(final FineRecord fine, final String filter) {
        return switch (filter) {
            case FineKeys.FILTER_UNPAID -> fine.status() == FineStatus.UNPAID;
            case FineKeys.FILTER_PAID -> fine.status() == FineStatus.PAID;
            case FineKeys.FILTER_WAIVED -> fine.status() == FineStatus.WAIVED;
            default -> true;
        };
    }

    public static Map<String, String> listFields(
            final GovSavedData data, final FineService.Actor viewer, final Set<GovCapability> capabilities,
            final String rawFilter, final UUID targetFilter, final int rawPage,
            final List<String> onlineNames) {
        final FineService.Page result = page(data, viewer == null ? null : viewer.uuid(), capabilities,
                rawFilter, targetFilter, rawPage);

        final Map<String, String> fields = new LinkedHashMap<>();
        fields.put(FineKeys.CAN_ISSUE,
                Boolean.toString(capabilities != null
                        && capabilities.contains(GovCapability.FINE_ISSUE)));
        fields.put(FineKeys.CAN_VIEW_ALL, Boolean.toString(FineLookups.canViewAll(capabilities)));
        fields.put(FineKeys.CAN_WAIVE,
                Boolean.toString(capabilities != null
                        && capabilities.contains(GovCapability.FINE_WAIVE)));
        fields.put(FineKeys.CAN_COLLECT,
                Boolean.toString(capabilities != null
                        && capabilities.contains(GovCapability.FINE_WAIVE)));
        fields.put(FineKeys.VIEWER_UUID,
                viewer == null || viewer.uuid() == null ? "" : viewer.uuid().toString());
        fields.put(FineKeys.RECEPTION_CONFIGURED,
                Boolean.toString(FineReception.configuredReception().isPresent()));
        fields.put(FineKeys.PAYMENT_POINT, com.horizonssmp.gov.config.GovConfig.paymentPoint());
        fields.put(FineKeys.TOTAL, Integer.toString(result.total()));
        fields.put(FineKeys.PAGE, Integer.toString(result.page()));
        fields.put(FineKeys.PAGES, Integer.toString(result.pages()));
        fields.put(FineKeys.FILTER, result.filter());

        for (int i = 0; i < result.rows().size(); i++) {
            writeRow(fields, i, result.rows().get(i));
        }

        final List<String> names = onlineNames == null ? List.of() : onlineNames;
        fields.put(FineKeys.ONLINE_COUNT, Integer.toString(names.size()));
        for (int i = 0; i < names.size() && i < FineKeys.MAX_LISTED_ONLINE; i++) {
            fields.put(FineKeys.ONLINE_PREFIX + i, Sanitizer.name(names.get(i)));
        }
        return fields;
    }

    private static void writeRow(
            final Map<String, String> fields, final int index, final FineService.FineRow row) {
        final FineRecord fine = row.fine();
        final String prefix = FineKeys.ROW_PREFIX + index + ".";
        fields.put(prefix + FineKeys.ROW_ID, fine.id());
        fields.put(prefix + FineKeys.ROW_TARGET, row.targetName());
        fields.put(prefix + FineKeys.ROW_TARGET_UUID,
                fine.targetUuid() == null ? "" : fine.targetUuid().toString());
        fields.put(prefix + FineKeys.ROW_BAND, fine.band().name());
        fields.put(prefix + FineKeys.ROW_AMOUNT, Long.toString(fine.amount().count()));
        fields.put(prefix + FineKeys.ROW_RESTITUTION, Long.toString(fine.restitution().count()));
        fields.put(prefix + FineKeys.ROW_DUE, Long.toString(fine.totalDue().count()));
        fields.put(prefix + FineKeys.ROW_ARTICLE, fine.lawArticle());
        fields.put(prefix + FineKeys.ROW_STATE, fine.status().name());
        fields.put(prefix + FineKeys.ROW_REASON,
                Sanitizer.singleLine(fine.reason(), FineKeys.LIST_REASON_CHARS));
        fields.put(prefix + FineKeys.ROW_CREATED, Long.toString(fine.createdAt()));
        fields.put(prefix + FineKeys.ROW_EXILE_DAYS, Integer.toString(fine.exileDays()));
        fields.put(prefix + FineKeys.ROW_VICTIM, fine.victim());
        fields.put(prefix + FineKeys.ROW_RECEIPT, fine.paidAtReceptionRef());
    }

    public static Map<String, String> detailFields(
            final GovSavedData data, final FineRecord fine,
            final Set<GovCapability> capabilities, final UUID viewerUuid) {
        final Map<String, String> fields = new LinkedHashMap<>();
        fields.put(FineKeys.ID, fine.id());
        fields.put(FineKeys.TARGET, FineLookups.targetName(data, fine.targetUuid()));
        fields.put(FineKeys.TARGET_UUID,
                fine.targetUuid() == null ? "" : fine.targetUuid().toString());
        fields.put(FineKeys.BAND, fine.band().name());
        fields.put(FineKeys.AMOUNT, Long.toString(fine.amount().count()));
        fields.put(FineKeys.RESTITUTION, Long.toString(fine.restitution().count()));
        fields.put(FineKeys.DUE, Long.toString(fine.totalDue().count()));
        fields.put(FineKeys.ARTICLE, fine.lawArticle());
        fields.put(FineKeys.ARTICLE_TITLE,
                LawIndex.get().article(fine.lawArticle()).map(LawData.Article::title).orElse(""));
        fields.put(FineKeys.REASON, fine.reason());
        fields.put(FineKeys.STATE, fine.status().name());
        fields.put(FineKeys.CREATED, Long.toString(fine.createdAt()));
        fields.put(FineKeys.PAID_AT, Long.toString(fine.paidAt()));
        fields.put(FineKeys.RECEIPT, fine.paidAtReceptionRef());
        fields.put(FineKeys.COLLECTOR, fine.collectedBy());
        fields.put(FineKeys.SELF_PAID, Boolean.toString(fine.selfPaid()));
        fields.put(FineKeys.WAIVED_BY, FineLookups.waivedByName(data, fine.waivedBy()));
        fields.put(FineKeys.WAIVED_REASON, fine.waivedReason());
        fields.put(FineKeys.EXILE_DAYS, Integer.toString(fine.exileDays()));
        fields.put(FineKeys.VICTIM, fine.victim());
        fields.put(FineKeys.VICTIM_UUID,
                fine.victimUuid() == null ? "" : fine.victimUuid().toString());
        fields.put(FineKeys.COMPENSATION_PENDING, Boolean.toString(fine.compensationPending()));
        fields.put(FineKeys.COMPENSATION_PAID_AT,
                Long.toString(fine.compensationPaidAt()));

        final boolean mine = viewerUuid != null && viewerUuid.equals(fine.targetUuid());
        final boolean unpaid = fine.status() == FineStatus.UNPAID;
        final boolean canWaive = capabilities != null
                && capabilities.contains(GovCapability.FINE_WAIVE);
        fields.put(FineKeys.CAN_PAY, Boolean.toString(mine && unpaid));
        fields.put(FineKeys.CAN_WAIVE_THIS, Boolean.toString(canWaive && unpaid));
        fields.put(FineKeys.CAN_COLLECT_THIS, Boolean.toString(canWaive && unpaid && !mine));
        return fields;
    }
}
