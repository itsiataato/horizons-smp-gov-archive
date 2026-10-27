package com.horizonssmp.gov.server;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.horizonssmp.gov.common.GovCapability;
import com.horizonssmp.gov.common.LicenseKeys;
import com.horizonssmp.gov.common.VehicleKeys;
import com.horizonssmp.gov.common.enums.LicenseClass;
import com.horizonssmp.gov.common.enums.LicenseStatus;
import com.horizonssmp.gov.common.enums.VehicleStatus;
import com.horizonssmp.gov.common.records.ImpoundRecord;
import com.horizonssmp.gov.common.records.LicenseRecord;
import com.horizonssmp.gov.common.records.VehicleRecord;
import com.horizonssmp.gov.config.GovConfig;

/**
 * Vehicle reads, paging and field maps.
 */
public final class VehicleReads {

    private VehicleReads() {
    }

    public static boolean canViewAll(final Set<GovCapability> capabilities) {
        if (capabilities == null) {
            return false;
        }
        return capabilities.contains(GovCapability.PLATE_SCAN)
                || capabilities.contains(GovCapability.VEHICLE_REGISTER)
                || capabilities.contains(GovCapability.VEHICLE_IMPOUND)
                || capabilities.contains(GovCapability.VEHICLE_RELEASE);
    }

    public static VehicleService.Page page(
            final GovSavedData data,
            final UUID viewerUuid,
            final Set<GovCapability> capabilities,
            final String rawFilter,
            final UUID ownerFilter,
            final int rawPage,
            final long now) {
        VehicleImpound.syncDisposalFlags(data, now);
        final String filter = VehicleService.canonicalFilter(rawFilter);
        final UUID scope = canViewAll(capabilities) ? ownerFilter : viewerUuid;
        final List<VehicleService.VehicleRow> all = new ArrayList<>();
        if (data != null) {
            for (final VehicleRecord vehicle : data.vehicles().values()) {
                if (scope != null && !scope.equals(vehicle.ownerUuid())) {
                    continue;
                }
                if (!matchesFilter(vehicle, filter, now)) {
                    continue;
                }
                all.add(row(data, vehicle, now));
            }
        }
        all.sort(Comparator.comparing(row -> row.vehicle().plate()));
        final int total = all.size();
        final int pages = Math.max(1, (total + VehicleKeys.PAGE_SIZE - 1) / VehicleKeys.PAGE_SIZE);
        final int page = Math.max(0, Math.min(rawPage, pages - 1));
        final List<VehicleService.VehicleRow> rows = new ArrayList<>(VehicleKeys.PAGE_SIZE);
        final int start = page * VehicleKeys.PAGE_SIZE;
        for (int i = start; i < Math.min(total, start + VehicleKeys.PAGE_SIZE); i++) {
            rows.add(all.get(i));
        }
        return new VehicleService.Page(rows, total, page, pages);
    }

    public static String canonicalFilter(final String raw) {
        if (raw == null) {
            return VehicleKeys.FILTER_ALL;
        }
        return switch (raw.trim().toLowerCase(java.util.Locale.ROOT)) {
            case VehicleKeys.FILTER_VALID -> VehicleKeys.FILTER_VALID;
            case VehicleKeys.FILTER_IMPOUNDED -> VehicleKeys.FILTER_IMPOUNDED;
            case VehicleKeys.FILTER_FLAGGED -> VehicleKeys.FILTER_FLAGGED;
            default -> VehicleKeys.FILTER_ALL;
        };
    }

    static boolean matchesFilter(
            final VehicleRecord vehicle, final String filter, final long now) {
        return switch (filter) {
            case VehicleKeys.FILTER_VALID -> vehicle.status() == VehicleStatus.VALID
                    && !vehicle.impound().active();
            case VehicleKeys.FILTER_IMPOUNDED -> vehicle.impound().active();
            case VehicleKeys.FILTER_FLAGGED -> vehicle.status() == VehicleStatus.FLAGGED_FOR_DISPOSAL
                    || vehicle.impound().flaggedForDisposal();
            default -> true;
        };
    }

    static VehicleService.VehicleRow row(
            final GovSavedData data, final VehicleRecord vehicle, final long now) {
        final LicenseClass vehicleClass = LicenseClass.parse(vehicle.vehClass());
        final LicenseRecord license = vehicleClass == null
                ? null : VehicleLookups.licenseFor(data, vehicle.ownerUuid(), vehicleClass, now);
        return new VehicleService.VehicleRow(
                vehicle,
                VehicleLookups.ownerName(data, vehicle.ownerUuid()),
                license,
                license == null ? LicenseStatus.REVOKED : license.stateAt(now),
                license == null ? 0 : license.activePoints(now));
    }

    public static Map<String, String> listFields(
            final GovSavedData data,
            final UUID viewerUuid,
            final String viewerName,
            final Set<GovCapability> capabilities,
            final String rawFilter,
            final UUID ownerFilter,
            final int page,
            final List<String> onlineNames,
            final long now) {
        final VehicleService.Page result = page(data, viewerUuid, capabilities, rawFilter, ownerFilter, page, now);
        final Map<String, String> fields = new LinkedHashMap<>();
        fields.put(VehicleKeys.CAN_REGISTER,
                Boolean.toString(capabilities != null
                        && capabilities.contains(GovCapability.VEHICLE_REGISTER)));
        fields.put(VehicleKeys.CAN_IMPOUND,
                Boolean.toString(capabilities != null
                        && capabilities.contains(GovCapability.VEHICLE_IMPOUND)));
        fields.put(VehicleKeys.CAN_RELEASE,
                Boolean.toString(capabilities != null
                        && capabilities.contains(GovCapability.VEHICLE_RELEASE)));
        fields.put(VehicleKeys.CAN_SCAN,
                Boolean.toString(capabilities != null
                        && capabilities.contains(GovCapability.PLATE_SCAN)));
        fields.put(VehicleKeys.CAN_MANAGE_LICENSES,
                Boolean.toString(capabilities != null
                        && capabilities.contains(GovCapability.VEHICLE_REGISTER)));
        fields.put(VehicleKeys.VIEWER_UUID, viewerUuid == null ? "" : viewerUuid.toString());
        fields.put(VehicleKeys.VIEWER_NAME, Sanitizer.name(viewerName));
        fields.put(VehicleKeys.RECEPTION_CONFIGURED,
                Boolean.toString(GovConfig.receptionConfigured()));
        fields.put(VehicleKeys.PAYMENT_POINT, GovConfig.paymentPoint());
        fields.put(VehicleKeys.RELEASE_FEE, Long.toString(GovConfig.impoundReleaseFeeGold()));
        fields.put(VehicleKeys.TOTAL, Integer.toString(result.total()));
        fields.put(VehicleKeys.PAGE, Integer.toString(result.page()));
        fields.put(VehicleKeys.PAGES, Integer.toString(result.pages()));
        fields.put(VehicleKeys.FILTER, VehicleService.canonicalFilter(rawFilter));

        for (int i = 0; i < result.rows().size(); i++) {
            final VehicleService.VehicleRow row = result.rows().get(i);
            final VehicleRecord vehicle = row.vehicle();
            final String prefix = VehicleKeys.ROW_PREFIX + i;
            fields.put(prefix + VehicleKeys.ROW_ID, vehicle.id());
            fields.put(prefix + VehicleKeys.ROW_PLATE, vehicle.plate());
            fields.put(prefix + VehicleKeys.ROW_CLASS, vehicle.vehClass());
            fields.put(prefix + VehicleKeys.ROW_OWNER, row.ownerName());
            fields.put(prefix + VehicleKeys.ROW_OWNER_UUID,
                    vehicle.ownerUuid() == null ? "" : vehicle.ownerUuid().toString());
            fields.put(prefix + VehicleKeys.ROW_STATUS, vehicle.status().name());
            fields.put(prefix + VehicleKeys.ROW_IMPOUND,
                    Boolean.toString(vehicle.impound().active()));
            fields.put(prefix + VehicleKeys.ROW_IMPOUND_REASON,
                    VehicleSupport.clip(vehicle.impound().reasonText(), VehicleKeys.LIST_REASON_CHARS));
            fields.put(prefix + VehicleKeys.ROW_LICENSE,
                    row.license() == null ? LicenseKeys.STATE_NONE : row.licenseState().name());
            fields.put(prefix + VehicleKeys.ROW_POINTS, Integer.toString(row.points()));
            fields.put(prefix + VehicleKeys.ROW_DISPOSAL,
                    Boolean.toString(vehicle.impound().flaggedForDisposal()
                            || vehicle.status() == VehicleStatus.FLAGGED_FOR_DISPOSAL));
        }

        final List<String> names = onlineNames == null ? List.of() : onlineNames;
        fields.put(VehicleKeys.ONLINE_COUNT, Integer.toString(names.size()));
        for (int i = 0; i < names.size() && i < VehicleKeys.MAX_LISTED_ONLINE; i++) {
            fields.put(VehicleKeys.ONLINE_PREFIX + i, Sanitizer.name(names.get(i)));
        }
        return fields;
    }

    public static Map<String, String> detailFields(
            final GovSavedData data,
            final VehicleRecord vehicle,
            final Set<GovCapability> capabilities,
            final long now) {
        VehicleImpound.syncDisposalFlags(data, now);
        final VehicleRecord current = data == null || vehicle == null
                ? vehicle : data.vehicle(vehicle.id());
        final VehicleRecord shown = current == null ? vehicle : current;
        if (shown == null) {
            return Map.of();
        }
        final Map<String, String> fields = new LinkedHashMap<>();
        fields.put(VehicleKeys.ID, shown.id());
        fields.put(VehicleKeys.PLATE, shown.plate());
        fields.put(VehicleKeys.CLASS, shown.vehClass());
        fields.put(VehicleKeys.OWNER, VehicleLookups.ownerName(data, shown.ownerUuid()));
        fields.put(VehicleKeys.OWNER_UUID,
                shown.ownerUuid() == null ? "" : shown.ownerUuid().toString());
        fields.put(VehicleKeys.STATUS, shown.status().name());
        fields.put(VehicleKeys.REGISTERED_AT, Long.toString(shown.registeredAt()));
        fields.put(VehicleKeys.REGISTERED_BY, VehicleLookups.ownerName(data, shown.registeredBy()));
        fields.put(VehicleKeys.EXPIRES_AT, Long.toString(shown.expiresAt()));

        final ImpoundRecord impound = shown.impound();
        fields.put(VehicleKeys.IMPOUND_ACTIVE, Boolean.toString(impound.active()));
        fields.put(VehicleKeys.IMPOUND_GROUND, impound.reason().name());
        fields.put(VehicleKeys.IMPOUND_REASON, impound.reasonText());
        fields.put(VehicleKeys.IMPOUNDED_AT, Long.toString(impound.impoundedAt()));
        fields.put(VehicleKeys.IMPOUNDED_BY, VehicleLookups.ownerName(data, impound.impoundedBy()));
        fields.put(VehicleKeys.IMPOUND_LOCATION, VehicleSupport.locationText(impound.location()));
        fields.put(VehicleKeys.FEE, Long.toString(impound.releaseFeeGold()));
        fields.put(VehicleKeys.RELEASED_AT, Long.toString(impound.releasedAt()));
        fields.put(VehicleKeys.RELEASED_BY, VehicleLookups.ownerName(data, impound.releasedBy()));
        fields.put(VehicleKeys.DISPOSAL,
                Boolean.toString(impound.flaggedForDisposal()
                        || shown.status() == VehicleStatus.FLAGGED_FOR_DISPOSAL));
        fields.put(VehicleKeys.DISPOSAL_AT, Long.toString(impound.disposalFlaggedAt()));

        final LicenseClass vehicleClass = LicenseClass.parse(shown.vehClass());
        final LicenseRecord license = vehicleClass == null
                ? null : VehicleLookups.licenseFor(data, shown.ownerUuid(), vehicleClass, now);
        fields.put(VehicleKeys.LICENSE_STATE,
                license == null ? LicenseKeys.STATE_NONE : license.stateAt(now).name());
        fields.put(VehicleKeys.LICENSE_POINTS,
                Integer.toString(license == null ? 0 : license.activePoints(now)));
        fields.put(VehicleKeys.LICENSE_SUSPENDED_UNTIL,
                Long.toString(license == null ? 0L : license.suspendedUntil()));

        final boolean canImpound = capabilities != null
                && capabilities.contains(GovCapability.VEHICLE_IMPOUND);
        final boolean canRelease = capabilities != null
                && capabilities.contains(GovCapability.VEHICLE_RELEASE);
        fields.put(VehicleKeys.CAN_IMPOUND_THIS,
                Boolean.toString(canImpound && !impound.active()));
        fields.put(VehicleKeys.CAN_RELEASE_THIS,
                Boolean.toString(canRelease && impound.active()));
        fields.put(VehicleKeys.CAN_MANAGE_LICENSES,
                Boolean.toString(capabilities != null
                        && capabilities.contains(GovCapability.VEHICLE_REGISTER)));
        return fields;
    }
}