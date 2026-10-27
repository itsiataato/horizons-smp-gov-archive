package com.horizonssmp.gov.server;

import java.util.UUID;

import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.GovCapability;
import com.horizonssmp.gov.common.enums.HistoryKind;
import com.horizonssmp.gov.common.enums.LicenseClass;
import com.horizonssmp.gov.common.records.ImpoundRecord;
import com.horizonssmp.gov.common.records.LicenseRecord;
import com.horizonssmp.gov.common.records.VehicleRecord;
import com.horizonssmp.gov.common.enums.VehicleStatus;
import com.horizonssmp.gov.server.HistoryService;

/**
 * Registration and transfer of vehicles.
 */
public final class VehicleRegistration {

    private VehicleRegistration() {
    }

    public static VehicleService.Outcome register(
            final GovSavedData data,
            final VehicleService.Actor actor,
            final UUID ownerUuid,
            final String enteredOwnerName,
            final String rawPlate,
            final String rawClass,
            final boolean requireLicense,
            final long now) {
        if (data == null || actor == null || ownerUuid == null) {
            return VehicleService.Outcome.refused(ErrorCode.INVALID_STATE, "No owner to register the vehicle to.");
        }
        if (!actor.capabilities().contains(GovCapability.VEHICLE_REGISTER)) {
            return VehicleService.Outcome.refused(
                    ErrorCode.FORBIDDEN,
                    "You need " + GovCapability.VEHICLE_REGISTER.id() + " to register a vehicle.");
        }
        final LicenseClass vehicleClass = LicenseClass.parse(rawClass);
        if (vehicleClass == null) {
            return VehicleService.Outcome.refused(
                    ErrorCode.BAD_PAYLOAD,
                    "Unknown vehicle class. Use one of: " + LicenseClass.ids() + ".");
        }

        final String id = VehicleIds.newVehicleId(data);
        final String plate;
        if (vehicleClass == LicenseClass.QUAD) {
            if (rawPlate != null && !rawPlate.trim().isEmpty()) {
                return VehicleService.Outcome.refused(ErrorCode.BAD_PAYLOAD,
                        "Quad registrations use an auto-generated id; leave the plate blank.");
            }
            plate = id;
        } else {
            plate = VehiclePlates.canonicalPlate(rawPlate);
            if (plate == null) {
                return VehicleService.Outcome.refused(ErrorCode.BAD_PAYLOAD, VehiclePlates.invalidPlateMessage(rawPlate));
            }
            final int max = VehiclePlates.maxPlateLength(vehicleClass);
            if (plate.length() > max) {
                return VehicleService.Outcome.refused(ErrorCode.BAD_PAYLOAD,
                        "Plate '" + Sanitizer.singleLine(rawPlate, 16) + "' is too long: "
                                + vehicleClass.displayName() + " plates allow at most " + max
                                + " characters.");
            }
        }
        if (VehiclePlates.byPlate(data, plate) != null) {
            return VehicleService.Outcome.refused(ErrorCode.PLATE_TAKEN, "Plate " + plate + " is already registered.");
        }
        if (requireLicense) {
            final LicenseRecord license = VehicleLookups.licenseFor(data, ownerUuid, vehicleClass, now);
            if (license == null || !license.authorizesAt(now, vehicleClass)) {
                return VehicleService.Outcome.refused(
                        ErrorCode.INVALID_STATE,
                        VehicleLookups.ownerName(data, ownerUuid) + " holds no valid " + vehicleClass.displayName()
                                + " licence for plate " + plate + ".");
            }
        }

        data.playerOrCreate(ownerUuid, Sanitizer.name(enteredOwnerName), now);
        data.putVehicle(new VehicleRecord(
                id, plate, ownerUuid, "", "", "", "", vehicleClass.id(),
                now, actor.uuid(), 0L, VehicleStatus.VALID, ImpoundRecord.NONE));
        HistoryService.append(data, ownerUuid, HistoryKind.VEHICLE_REGISTERED,
                "Vehicle plate " + plate + " (" + vehicleClass.displayName()
                        + ") was registered to you.",
                actor.uuid(), actor.name(), now, id);
        return VehicleService.Outcome.ok(
                "registered plate " + plate + " (" + vehicleClass.displayName() + ") to "
                        + VehicleLookups.ownerName(data, ownerUuid) + ".", id);
    }

    public static VehicleService.Outcome transfer(
            final GovSavedData data,
            final VehicleService.Actor actor,
            final String rawPlate,
            final UUID newOwnerUuid,
            final String newOwnerName,
            final boolean requireLicense,
            final long now) {
        if (data == null || actor == null || newOwnerUuid == null) {
            return VehicleService.Outcome.refused(ErrorCode.INVALID_STATE, "No owner to transfer the vehicle to.");
        }
        if (!actor.capabilities().contains(GovCapability.VEHICLE_REGISTER)) {
            return VehicleService.Outcome.refused(
                    ErrorCode.FORBIDDEN,
                    "You need " + GovCapability.VEHICLE_REGISTER.id() + " to transfer a vehicle.");
        }
        final VehicleRecord vehicle = VehiclePlates.byPlate(data, rawPlate);
        if (vehicle == null) {
            return VehicleService.Outcome.refused(ErrorCode.BAD_VEHICLE, VehiclePlates.noSuchVehicleMessage(rawPlate));
        }
        final LicenseClass vehicleClass = LicenseClass.parse(vehicle.vehClass());
        if (vehicleClass == null) {
            return VehicleService.Outcome.refused(
                    ErrorCode.INVALID_STATE, "Vehicle " + vehicle.plate() + " has no known class.");
        }
        if (requireLicense) {
            final LicenseRecord license = VehicleLookups.licenseFor(data, newOwnerUuid, vehicleClass, now);
            if (license == null || !license.authorizesAt(now, vehicleClass)) {
                return VehicleService.Outcome.refused(
                        ErrorCode.INVALID_STATE,
                        VehicleLookups.ownerName(data, newOwnerUuid) + " holds no valid "
                                + vehicleClass.displayName() + " licence.");
            }
        }
        data.playerOrCreate(newOwnerUuid, Sanitizer.name(newOwnerName), now);
        data.putVehicle(vehicle.withOwner(newOwnerUuid, actor.uuid(), now));
        return VehicleService.Outcome.ok(
                "transferred plate " + vehicle.plate() + " to " + VehicleLookups.ownerName(data, newOwnerUuid) + ".",
                vehicle.id());
    }

    public static VehicleService.Outcome registerPrecheck(
            final GovSavedData data, final String rawPlate, final String rawClass) {
        final String plate = VehiclePlates.canonicalPlate(rawPlate);
        if (plate == null) {
            return VehicleService.Outcome.refused(ErrorCode.BAD_PAYLOAD, VehiclePlates.invalidPlateMessage(rawPlate));
        }
        if (LicenseClass.parse(rawClass) == null) {
            return VehicleService.Outcome.refused(
                    ErrorCode.BAD_PAYLOAD,
                    "Unknown vehicle class. Use one of: " + LicenseClass.ids() + ".");
        }
        if (VehiclePlates.byPlate(data, plate) != null) {
            return VehicleService.Outcome.refused(ErrorCode.PLATE_TAKEN, "Plate " + plate + " is already registered.");
        }
        return null;
    }
}