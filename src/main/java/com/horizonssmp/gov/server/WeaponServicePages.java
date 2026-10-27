package com.horizonssmp.gov.server;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.GovCapability;
import com.horizonssmp.gov.common.GovTime;
import com.horizonssmp.gov.common.TownCore;
import com.horizonssmp.gov.common.WeaponKeys;
import com.horizonssmp.gov.common.enums.DutyState;
import com.horizonssmp.gov.common.enums.HistoryKind;
import com.horizonssmp.gov.common.enums.PermitClass;
import com.horizonssmp.gov.common.enums.PermitStatus;
import com.horizonssmp.gov.common.enums.WeaponCategory;
import com.horizonssmp.gov.common.enums.WeaponClass;
import com.horizonssmp.gov.common.records.AreaPermitRecord;
import com.horizonssmp.gov.common.records.PermitRecord;
import com.horizonssmp.gov.common.records.PlayerRecord;
import com.horizonssmp.gov.common.records.WeaponRecord;
import com.horizonssmp.gov.config.GovConfig;


final class WeaponServicePages {

    public static boolean canViewAll(final Set<GovCapability> capabilities) {
        return capabilities != null
                && (capabilities.contains(GovCapability.WEAPON_VIEW)
                        || capabilities.contains(GovCapability.WEAPON_REGISTER));
    }

    /** One page of the registry, scoped to the viewer's own registrations when they may not see all. */

    public static WeaponService.WeaponPage page(
            final GovSavedData data, final UUID viewerUuid, final Set<GovCapability> capabilities,
            final String rawFilter, final UUID ownerFilter, final int rawPage) {
        final String filter = canonicalFilter(rawFilter);
        final UUID scope = canViewAll(capabilities) ? ownerFilter : viewerUuid;
        final List<WeaponService.WeaponRow> all = new ArrayList<>();
        if (data != null) {
            for (final WeaponRecord weapon : data.weapons().values()) {
                if (scope != null && !scope.equals(weapon.ownerUuid())) {
                    continue;
                }
                if (!matchesFilter(weapon, filter)) {
                    continue;
                }
                all.add(new WeaponService.WeaponRow(weapon, WeaponServiceSerials.ownerName(data, weapon.ownerUuid())));
            }
        }
        all.sort(Comparator.comparing(row -> row.weapon().serial()));
        final int total = all.size();
        final int pages = Math.max(1, (total + WeaponKeys.PAGE_SIZE - 1) / WeaponKeys.PAGE_SIZE);
        final int page = Math.max(0, Math.min(rawPage, pages - 1));
        final List<WeaponService.WeaponRow> rows = new ArrayList<>(WeaponKeys.PAGE_SIZE);
        final int start = page * WeaponKeys.PAGE_SIZE;
        for (int i = start; i < Math.min(total, start + WeaponKeys.PAGE_SIZE); i++) {
            rows.add(all.get(i));
        }
        return new WeaponService.WeaponPage(rows, total, page, pages);
    }

    /** The canonical filter id, defaulting to all. */

    public static String canonicalFilter(final String raw) {
        if (raw == null) {
            return WeaponKeys.FILTER_ALL;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case WeaponKeys.FILTER_ACTIVE -> WeaponKeys.FILTER_ACTIVE;
            case WeaponKeys.FILTER_REVOKED -> WeaponKeys.FILTER_REVOKED;
            case WeaponKeys.FILTER_LOST -> WeaponKeys.FILTER_LOST;
            default -> WeaponKeys.FILTER_ALL;
        };
    }


    public static boolean matchesFilter(final WeaponRecord weapon, final String filter) {
        return switch (filter) {
            case WeaponKeys.FILTER_ACTIVE -> WeaponRecord.STATUS_ACTIVE.equals(weapon.status());
            case WeaponKeys.FILTER_REVOKED -> WeaponRecord.STATUS_REVOKED.equals(weapon.status());
            case WeaponKeys.FILTER_LOST -> WeaponRecord.STATUS_LOST.equals(weapon.status());
            default -> true;
        };
    }

    /** The {@code WPN_LIST} answer as a field map. */

    public static WeaponService.PermitPage permitsPage(
            final GovSavedData data, final UUID holderUuid, final int rawPage, final long now) {
        final List<PermitRecord> all = new ArrayList<>();
        if (data != null && holderUuid != null) {
            all.addAll(data.weaponPermitsFor(holderUuid));
        }
        all.sort(Comparator.comparingInt(record -> record.permitClass().ordinal()));
        final int total = all.size();
        final int pages = Math.max(1,
                (total + WeaponKeys.PERMIT_PAGE_SIZE - 1) / WeaponKeys.PERMIT_PAGE_SIZE);
        final int page = Math.max(0, Math.min(rawPage, pages - 1));
        final List<PermitRecord> rows = new ArrayList<>(WeaponKeys.PERMIT_PAGE_SIZE);
        final int start = page * WeaponKeys.PERMIT_PAGE_SIZE;
        for (int i = start; i < Math.min(total, start + WeaponKeys.PERMIT_PAGE_SIZE); i++) {
            rows.add(all.get(i));
        }
        return new WeaponService.PermitPage(rows, total, page, pages);
    }

    /** The {@code WPN_PERMIT_LIST} answer as a field map. */

    public static WeaponService.AreaPage areaPage(final GovSavedData data, final int rawPage, final long now) {
        final List<AreaPermitRecord> all = new ArrayList<>();
        if (data != null) {
            all.addAll(data.areaPermits().values());
        }
        all.sort(Comparator.comparingLong(AreaPermitRecord::issuedAt).reversed()
                .thenComparing(AreaPermitRecord::id));
        final int total = all.size();
        final int pages = Math.max(1,
                (total + WeaponKeys.AREA_PAGE_SIZE - 1) / WeaponKeys.AREA_PAGE_SIZE);
        final int page = Math.max(0, Math.min(rawPage, pages - 1));
        final List<AreaPermitRecord> rows = new ArrayList<>(WeaponKeys.AREA_PAGE_SIZE);
        final int start = page * WeaponKeys.AREA_PAGE_SIZE;
        for (int i = start; i < Math.min(total, start + WeaponKeys.AREA_PAGE_SIZE); i++) {
            rows.add(all.get(i));
        }
        return new WeaponService.AreaPage(rows, total, page, pages);
    }

    /** The {@code AREA_PERMIT_LIST} answer as a field map. */
}
