package com.horizonssmp.gov.server;

import com.horizonssmp.gov.common.ErrorCode;

/**
 * Data-only prechecks for vehicle operations.
 */
public final class VehiclePrechecks {

    private VehiclePrechecks() {
    }

    public static VehicleService.Outcome platePrecheck(final String rawPlate) {
        return VehiclePlates.canonicalPlate(rawPlate) == null
                ? VehicleService.Outcome.refused(ErrorCode.BAD_PAYLOAD, VehiclePlates.invalidPlateMessage(rawPlate))
                : null;
    }

    public static VehicleService.Outcome vehiclePrecheck(final GovSavedData data, final String rawPlate) {
        return VehiclePlates.find(data, rawPlate) == null
                ? VehicleService.Outcome.refused(ErrorCode.BAD_VEHICLE, VehiclePlates.noSuchVehicleMessage(rawPlate))
                : null;
    }
}