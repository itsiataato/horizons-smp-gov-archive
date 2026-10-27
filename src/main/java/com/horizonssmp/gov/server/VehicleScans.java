package com.horizonssmp.gov.server;

import java.util.LinkedHashMap;
import java.util.Map;

import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.LicenseKeys;
import com.horizonssmp.gov.common.VehicleKeys;
import com.horizonssmp.gov.common.enums.LicenseClass;
import com.horizonssmp.gov.common.enums.ScanResult;
import com.horizonssmp.gov.common.enums.VehicleStatus;
import com.horizonssmp.gov.common.records.LicenseRecord;
import com.horizonssmp.gov.common.records.PlateScanRecord;
import com.horizonssmp.gov.common.records.VehicleRecord;

/**
 * Plate scans.
 */
public final class VehicleScans {

    private VehicleScans() {
    }

    public static VehicleService.Scan scan(
            final GovSavedData data,
            final VehicleService.Actor actor,
            final String rawPlate,
            final VehicleService.VehicleAdapter adapter,
            final long now,
            final boolean record) {
        final VehicleService.VehicleAdapter source = adapter == null ? VehicleService.REGISTRY_ONLY : adapter;
        if (data == null || actor == null) {
            return VehicleService.Scan.refused(ErrorCode.INVALID_STATE, "No plate to scan.");
        }
        if (!actor.capabilities().contains(com.horizonssmp.gov.common.GovCapability.PLATE_SCAN)) {
            return VehicleService.Scan.refused(
                    ErrorCode.FORBIDDEN,
                    "You need " + com.horizonssmp.gov.common.GovCapability.PLATE_SCAN.id() + " to scan a plate.");
        }
        final String plate = VehiclePlates.canonicalPlate(rawPlate);
        if (plate == null) {
            return VehicleService.Scan.refused(ErrorCode.BAD_PAYLOAD, VehiclePlates.invalidPlateMessage(rawPlate));
        }
        if (record) {
            VehicleImpound.syncDisposalFlags(data, now);
        }
        final String sourceLabel = VehicleSupport.sourceLabel(source, plate);
        final VehicleRecord vehicle = VehiclePlates.byPlate(data, plate);
        final VehicleService.ScanView view;
        if (vehicle == null) {
            view = new VehicleService.ScanView("", plate, ScanResult.UNREGISTERED, "", "", "",
                    false, "", LicenseKeys.STATE_NONE, 0, now, sourceLabel);
        } else {
            final LicenseClass vehicleClass = LicenseClass.parse(vehicle.vehClass());
            final LicenseRecord license = vehicleClass == null
                    ? null : VehicleLookups.licenseFor(data, vehicle.ownerUuid(), vehicleClass, now);
            final String licenseState = license == null
                    ? LicenseKeys.STATE_NONE : license.stateAt(now).name();
            final ScanResult result = resultOf(vehicle, license, vehicleClass, now);
            view = new VehicleService.ScanView("", plate, result, VehicleLookups.ownerName(data, vehicle.ownerUuid()),
                    vehicle.vehClass(), vehicle.status().name(), vehicle.impound().active(),
                    VehicleSupport.clip(vehicle.impound().reasonText(), VehicleKeys.LIST_REASON_CHARS),
                    licenseState, license == null ? 0 : license.activePoints(now),
                    now, sourceLabel);
        }
        if (!record) {
            return VehicleService.Scan.ok(view);
        }
        final String scanId = VehicleIds.newScanId(data);
        final PlateScanRecord stored = new PlateScanRecord(
                scanId, plate, actor.uuid(), now, view.result(), sourceLabel);
        data.putPlateScan(stored);
        return VehicleService.Scan.ok(new VehicleService.ScanView(
                scanId, view.plate(), view.result(), view.ownerName(), view.vehClass(),
                view.status(), view.impoundActive(), view.impoundReason(), view.licenseState(),
                view.licensePoints(), view.at(), view.source()));
    }

    private static ScanResult resultOf(
            final VehicleRecord vehicle,
            final LicenseRecord license,
            final LicenseClass vehicleClass,
            final long now) {
        if (vehicle.status() == VehicleStatus.FLAGGED_FOR_DISPOSAL
                || vehicle.impound().flaggedForDisposal()) {
            return ScanResult.DISPOSAL_FLAGGED;
        }
        if (vehicle.impound().active() || vehicle.status() == VehicleStatus.IMPOUNDED) {
            return ScanResult.IMPOUNDED;
        }
        if (vehicle.status() == VehicleStatus.STOLEN) {
            return ScanResult.STOLEN;
        }
        if (vehicle.status() == VehicleStatus.SUSPENDED) {
            return ScanResult.SUSPENDED;
        }
        if (vehicle.expiredAt(now) || vehicle.status() == VehicleStatus.EXPIRED) {
            return ScanResult.EXPIRED;
        }
        if (vehicleClass == null || license == null || !license.authorizesAt(now, vehicleClass)) {
            return ScanResult.LICENSE_INVALID;
        }
        return ScanResult.VALID;
    }

    public static Map<String, String> scanFields(final VehicleService.ScanView view) {
        final Map<String, String> fields = new LinkedHashMap<>();
        fields.put(VehicleKeys.SCAN_ID, view.scanId());
        fields.put(VehicleKeys.SCAN_PLATE, view.plate());
        fields.put(VehicleKeys.SCAN_RESULT, view.result().name());
        fields.put(VehicleKeys.SCAN_OWNER, view.ownerName());
        fields.put(VehicleKeys.SCAN_CLASS, view.vehClass());
        fields.put(VehicleKeys.SCAN_STATUS, view.status());
        fields.put(VehicleKeys.SCAN_IMPOUND, Boolean.toString(view.impoundActive()));
        fields.put(VehicleKeys.SCAN_IMPOUND_REASON, view.impoundReason());
        fields.put(VehicleKeys.SCAN_LICENSE_STATE, view.licenseState());
        fields.put(VehicleKeys.SCAN_LICENSE_POINTS, Integer.toString(view.licensePoints()));
        fields.put(VehicleKeys.SCAN_AT, Long.toString(view.at()));
        fields.put(VehicleKeys.SCAN_SOURCE, view.source());
        return fields;
    }
}