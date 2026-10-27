package com.horizonssmp.gov.common.records;

import java.util.UUID;

import com.horizonssmp.gov.common.enums.VehicleStatus;

import net.minecraft.nbt.CompoundTag;

/**
 * A registered vehicle.
 *
 * <p>The plate is the identity the world uses; {@code id} is the record identity the data file
 * uses. Both are kept: a plate can be changed on an edit, and the history must still resolve to the
 * same vehicle. The plate is normalized (uppercase, no spaces) by the registration service before
 * it reaches this record, so the stored form is the form that was validated against the configured
 * pattern.
 *
 * <p>{@code status} is a single value, and {@code impound} carries the detail of the current or
 * last impound. An impounded vehicle is therefore exactly one record with {@code status = IMPOUNDED}
 * and {@code impound.active = true}; a released one has {@code status = VALID} and an inactive
 * impound block that still shows the history.
 *
 * @param id the stable record id
 * @param plate the normalized plate
 * @param ownerUuid the registered owner, or {@code null} for a vehicle registered without an owner
 * @param make the make, or empty
 * @param model the model, or empty
 * @param color the colour, or empty
 * @param vin the vehicle identification number, or empty
 * @param vehClass the class id from the law's vehicle schedule, for example {@code car}
 * @param registeredAt epoch milliseconds the vehicle was registered
 * @param registeredBy the official who registered it, or {@code null} if unknown
 * @param expiresAt epoch milliseconds the registration expires, {@code 0} for no expiry
 * @param status the registration status
 * @param impound the impound block, never {@code null}
 */
public record VehicleRecord(
        String id,
        String plate,
        UUID ownerUuid,
        String make,
        String model,
        String color,
        String vin,
        String vehClass,
        long registeredAt,
        UUID registeredBy,
        long expiresAt,
        VehicleStatus status,
        ImpoundRecord impound) {

    public VehicleRecord {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("VehicleRecord requires an id");
        }
        plate = plate == null ? "" : plate;
        make = make == null ? "" : make;
        model = model == null ? "" : model;
        color = color == null ? "" : color;
        vin = vin == null ? "" : vin;
        vehClass = vehClass == null ? "" : vehClass;
        status = status == null ? VehicleStatus.VALID : status;
        impound = impound == null ? ImpoundRecord.NONE : impound;
    }

    /** True when the registration has an expiry that has already passed. */
    public boolean expiredAt(final long now) {
        return expiresAt > 0L && now >= expiresAt;
    }

    /** Returns a copy with a new status and impound block. */
    public VehicleRecord withState(final VehicleStatus status, final ImpoundRecord impound) {
        return new VehicleRecord(
                id, plate, ownerUuid, make, model, color, vin, vehClass,
                registeredAt, registeredBy, expiresAt,
                status == null ? VehicleStatus.VALID : status,
                impound == null ? ImpoundRecord.NONE : impound);
    }

    /**
     * Returns a copy registered to a new owner at {@code now}.
     *
     * <p>The new stamp is the current registration's date and official; the audit log keeps the
     * earlier registration event, so updating the current entry does not lose the history.
     */
    public VehicleRecord withOwner(final UUID newOwner, final UUID by, final long now) {
        return new VehicleRecord(
                id, plate, newOwner, make, model, color, vin, vehClass,
                now, by, expiresAt, status, impound);
    }

    /** Serializes this vehicle into a fresh tag. */
    public CompoundTag write() {
        final CompoundTag tag = new CompoundTag();
        tag.putString("id", id);
        tag.putString("plate", plate);
        NbtSupport.putUuid(tag, "ownerUuid", ownerUuid);
        tag.putString("make", make);
        tag.putString("model", model);
        tag.putString("color", color);
        tag.putString("vin", vin);
        tag.putString("vehClass", vehClass);
        tag.putLong("registeredAt", registeredAt);
        NbtSupport.putUuid(tag, "registeredBy", registeredBy);
        tag.putLong("expiresAt", expiresAt);
        tag.putString("status", status.name());
        tag.put("impound", impound.write());
        return tag;
    }

    /** Reads a vehicle. A tag without an id cannot be keyed, so the caller supplies the key. */
    public static VehicleRecord read(final CompoundTag tag, final String fallbackId) {
        final String stored = NbtSupport.getString(tag, "id", "");
        final String id = stored.isBlank() ? fallbackId : stored;
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("VehicleRecord tag has no id and no fallback");
        }
        return new VehicleRecord(
                id,
                NbtSupport.getString(tag, "plate", ""),
                NbtSupport.getUuid(tag, "ownerUuid"),
                NbtSupport.getString(tag, "make", ""),
                NbtSupport.getString(tag, "model", ""),
                NbtSupport.getString(tag, "color", ""),
                NbtSupport.getString(tag, "vin", ""),
                NbtSupport.getString(tag, "vehClass", ""),
                NbtSupport.getLong(tag, "registeredAt", 0L),
                NbtSupport.getUuid(tag, "registeredBy"),
                NbtSupport.getLong(tag, "expiresAt", 0L),
                VehicleStatus.parse(NbtSupport.getString(tag, "status", "")),
                ImpoundRecord.read(NbtSupport.getCompoundOr(tag, "impound")));
    }
}
