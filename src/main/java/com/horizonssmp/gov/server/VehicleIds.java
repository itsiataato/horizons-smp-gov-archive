package com.horizonssmp.gov.server;

import java.util.UUID;

/**
 * Id generation for vehicles and plate scans.
 */
public final class VehicleIds {

    private VehicleIds() {
    }

    public static String newVehicleId(final GovSavedData data) {
        for (int attempt = 0; attempt < 8; attempt++) {
            final String id = VehicleService.VEHICLE_ID_PREFIX
                    + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            if (data.vehicle(id) == null) {
                return id;
            }
        }
        String fallback = VehicleService.VEHICLE_ID_PREFIX + Long.toHexString(System.nanoTime());
        while (data.vehicle(fallback) != null) {
            fallback += "x";
        }
        return fallback;
    }

    public static String newScanId(final GovSavedData data) {
        for (int attempt = 0; attempt < 8; attempt++) {
            final String id = VehicleService.SCAN_ID_PREFIX
                    + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            if (data.plateScans().get(id) == null) {
                return id;
            }
        }
        String fallback = VehicleService.SCAN_ID_PREFIX + Long.toHexString(System.nanoTime());
        while (data.plateScans().containsKey(fallback)) {
            fallback += "x";
        }
        return fallback;
    }
}