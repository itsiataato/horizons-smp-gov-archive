package com.horizonssmp.gov.server;

import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;

import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.GovCapability;
import com.horizonssmp.gov.common.enums.ImpoundReason;
import com.horizonssmp.gov.common.enums.VehicleStatus;
import com.horizonssmp.gov.common.records.ImpoundRecord;
import com.horizonssmp.gov.common.records.LocationRecord;
import com.horizonssmp.gov.common.records.VehicleRecord;
import com.horizonssmp.gov.config.GovConfig;

/**
 * Impound, release and disposal marking.
 */
public final class VehicleImpound {

    private VehicleImpound() {
    }

    public static VehicleService.Outcome impound(
            final GovSavedData data,
            final VehicleService.Actor actor,
            final String rawPlate,
            final String rawReason,
            final LocationRecord location,
            final long now) {
        if (data == null || actor == null) {
            return VehicleService.Outcome.refused(ErrorCode.INVALID_STATE, "No vehicle to impound.");
        }
        if (!actor.capabilities().contains(GovCapability.VEHICLE_IMPOUND)) {
            return VehicleService.Outcome.refused(
                    ErrorCode.FORBIDDEN,
                    "You need " + GovCapability.VEHICLE_IMPOUND.id() + " to impound a vehicle.");
        }
        final VehicleRecord vehicle = VehiclePlates.byPlate(data, rawPlate);
        if (vehicle == null) {
            return VehicleService.Outcome.refused(ErrorCode.BAD_VEHICLE, VehiclePlates.noSuchVehicleMessage(rawPlate));
        }
        final String reason = Sanitizer.text(rawReason);
        if (reason.isBlank()) {
            return VehicleService.Outcome.refused(
                    ErrorCode.BAD_PAYLOAD,
                    "An impound needs a recorded reason of at most " + Sanitizer.MAX_TEXT
                            + " characters.");
        }
        if (vehicle.impound().active()) {
            return VehicleService.Outcome.refused(
                    ErrorCode.INVALID_STATE, "Plate " + vehicle.plate() + " is already impounded.");
        }
        final long fee = Math.max(0L, GovConfig.impoundReleaseFeeGold());
        final ImpoundRecord impound = new ImpoundRecord(
                true, ImpoundReason.classify(reason), reason, now, actor.uuid(),
                location == null ? LocationRecord.NONE : location, fee, 0L, null, 0L);
        data.putVehicle(vehicle.withState(VehicleStatus.IMPOUNDED, impound));
        return VehicleService.Outcome.ok(
                "impounded plate " + vehicle.plate() + ": " + reason + ". Release fee "
                        + fee + " gold ingots at the " + GovConfig.paymentPoint() + ".",
                vehicle.id());
    }

    public static VehicleService.Outcome release(
            final GovSavedData data,
            final VehicleService.Actor actor,
            final String rawPlate,
            final FineService.Gateway gateway,
            final Optional<FineService.ReceptionPoint> reception,
            final long now) {
        if (data == null || actor == null || gateway == null) {
            return VehicleService.Outcome.refused(ErrorCode.INVALID_STATE, "No vehicle to release.");
        }
        if (!actor.capabilities().contains(GovCapability.VEHICLE_RELEASE)) {
            return VehicleService.Outcome.refused(
                    ErrorCode.FORBIDDEN,
                    "You need " + GovCapability.VEHICLE_RELEASE.id() + " to release a vehicle.");
        }
        syncDisposalFlags(data, now);
        final VehicleRecord vehicle = VehiclePlates.byPlate(data, rawPlate);
        if (vehicle == null) {
            return VehicleService.Outcome.refused(ErrorCode.BAD_VEHICLE, VehiclePlates.noSuchVehicleMessage(rawPlate));
        }
        if (!vehicle.impound().active()) {
            return VehicleService.Outcome.refused(
                    ErrorCode.INVALID_STATE, "Plate " + vehicle.plate() + " is not impounded.");
        }
        if (vehicle.ownerUuid() == null) {
            return VehicleService.Outcome.refused(
                    ErrorCode.INVALID_STATE,
                    "Plate " + vehicle.plate() + " has no recorded owner to release it to.");
        }
        if (reception == null || reception.isEmpty()) {
            return VehicleService.Outcome.refused(ErrorCode.INVALID_STATE, FineService.receptionMissingMessage());
        }
        if (!gateway.atReception(actor.uuid())) {
            return VehicleService.Outcome.refused(ErrorCode.INVALID_STATE, FineService.notAtReceptionMessage());
        }
        if (!gateway.present(vehicle.ownerUuid())) {
            return VehicleService.Outcome.refused(
                    ErrorCode.INVALID_STATE,
                    VehicleLookups.ownerName(data, vehicle.ownerUuid()) + " is not online to recover the vehicle.");
        }
        if (!gateway.atReception(vehicle.ownerUuid())) {
            return VehicleService.Outcome.refused(
                    ErrorCode.INVALID_STATE,
                    VehicleLookups.ownerName(data, vehicle.ownerUuid()) + " must be at the "
                            + GovConfig.paymentPoint() + " to recover the vehicle.");
        }
        final long fee = Math.max(0L, vehicle.impound().releaseFeeGold());
        final long carried = gateway.goldIngots(vehicle.ownerUuid());
        if (carried < fee) {
            return VehicleService.Outcome.refused(
                    ErrorCode.INVALID_STATE,
                    VehicleLookups.ownerName(data, vehicle.ownerUuid()) + " needs " + fee
                            + " gold ingots but carries " + carried + ".");
        }
        if (!gateway.removeGoldIngots(vehicle.ownerUuid(), fee)) {
            return VehicleService.Outcome.refused(
                    ErrorCode.INVALID_STATE,
                    "The gold could not be taken; the vehicle was not released.");
        }
        data.putVehicle(vehicle.withState(
                VehicleStatus.VALID, vehicle.impound().released(actor.uuid(), now)));
        return VehicleService.Outcome.ok(
                "released plate " + vehicle.plate() + " to "
                        + VehicleLookups.ownerName(data, vehicle.ownerUuid()) + " for " + fee + " gold ingots.",
                vehicle.id());
    }

    public static int syncDisposalFlags(final GovSavedData data, final long now) {
        if (data == null) {
            return 0;
        }
        int marked = 0;
        for (final VehicleRecord vehicle : new ArrayList<>(data.vehicles().values())) {
            final ImpoundRecord impound = vehicle.impound();
            if (!impound.active() || impound.flaggedForDisposal()
                    || impound.impoundedAt() <= 0L
                    || now - impound.impoundedAt() < VehicleService.DISPOSAL_AFTER_MS) {
                continue;
            }
            data.putVehicle(vehicle.withState(
                    VehicleStatus.FLAGGED_FOR_DISPOSAL, impound.flaggedForDisposal(now)));
            AuditService.append(
                    data, null, "VEH_DISPOSAL_FLAGGED", null, Map.of("plate", vehicle.plate()),
                    "unclaimed for 30 in-game days; staff disposal requires notice; nothing deleted");
            marked++;
        }
        return marked;
    }

    public static VehicleService.Outcome impoundPrecheck(
            final GovSavedData data, final String rawPlate, final String rawReason) {
        final VehicleRecord vehicle = VehiclePlates.byPlate(data, rawPlate);
        if (vehicle == null) {
            return VehicleService.Outcome.refused(ErrorCode.BAD_VEHICLE, VehiclePlates.noSuchVehicleMessage(rawPlate));
        }
        if (vehicle.impound().active()) {
            return VehicleService.Outcome.refused(
                    ErrorCode.INVALID_STATE, "Plate " + vehicle.plate() + " is already impounded.");
        }
        if (Sanitizer.text(rawReason).isBlank()) {
            return VehicleService.Outcome.refused(ErrorCode.BAD_PAYLOAD, "An impound needs a recorded reason.");
        }
        return null;
    }

    public static VehicleService.Outcome releasePrecheck(final GovSavedData data, final String rawPlate) {
        final VehicleRecord vehicle = VehiclePlates.byPlate(data, rawPlate);
        if (vehicle == null) {
            return VehicleService.Outcome.refused(ErrorCode.BAD_VEHICLE, VehiclePlates.noSuchVehicleMessage(rawPlate));
        }
        if (!vehicle.impound().active()) {
            return VehicleService.Outcome.refused(
                    ErrorCode.INVALID_STATE, "Plate " + vehicle.plate() + " is not impounded.");
        }
        if (vehicle.ownerUuid() == null) {
            return VehicleService.Outcome.refused(
                    ErrorCode.INVALID_STATE,
                    "Plate " + vehicle.plate() + " has no recorded owner to release it to.");
        }
        return null;
    }
}