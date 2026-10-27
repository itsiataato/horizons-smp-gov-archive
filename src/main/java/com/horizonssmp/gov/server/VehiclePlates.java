package com.horizonssmp.gov.server;

import java.util.Locale;

import com.horizonssmp.gov.common.records.VehicleRecord;

/**
 * Plate canonicalisation and plate-based lookups.
 */
public final class VehiclePlates {

    private VehiclePlates() {
    }

    public static String canonicalPlate(final String raw) {
        if (raw == null) {
            return null;
        }
        final String upper = raw.trim().toUpperCase(Locale.ROOT);
        final String canonical = upper.replace("-", "");
        if (canonical.isEmpty() || canonical.length() > 12) {
            return null;
        }
        return canonical;
    }

    public static String invalidPlateMessage(final String raw) {
        return "Plate '" + Sanitizer.singleLine(raw, 16)
                + "' is not valid: use 1 to 12 characters.";
    }

    public static VehicleRecord byPlate(final GovSavedData data, final String rawPlate) {
        final String plate = canonicalPlate(rawPlate);
        if (data == null || plate == null) {
            return null;
        }
        for (final VehicleRecord vehicle : data.vehicles().values()) {
            if (plate.equals(vehicle.plate())) {
                return vehicle;
            }
        }
        return null;
    }

    public static VehicleRecord find(final GovSavedData data, final String raw) {
        if (data == null || raw == null || raw.isBlank()) {
            return null;
        }
        final String trimmed = raw.trim();
        if (trimmed.regionMatches(true, 0, VehicleService.VEHICLE_ID_PREFIX, 0,
                VehicleService.VEHICLE_ID_PREFIX.length())) {
            final VehicleRecord byId = data.vehicle(trimmed.toLowerCase(Locale.ROOT));
            if (byId != null) {
                return byId;
            }
        }
        return byPlate(data, trimmed);
    }

    public static String noSuchVehicleMessage(final String raw) {
        final String plate = canonicalPlate(raw);
        return "There is no vehicle with plate '"
                + Sanitizer.singleLine(plate == null ? raw : plate, 16) + "'.";
    }

    static int maxPlateLength(final com.horizonssmp.gov.common.enums.LicenseClass vehicleClass) {
        return switch (vehicleClass) {
            case CAR, TRUCK -> 6;
            case HELICOPTER -> 8;
            case PLANE -> 12;
            case QUAD -> 0;
        };
    }
}