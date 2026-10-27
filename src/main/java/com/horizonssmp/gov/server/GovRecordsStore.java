package com.horizonssmp.gov.server;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.horizonssmp.gov.common.enums.LicenseClass;
import com.horizonssmp.gov.common.enums.PermitClass;
import com.horizonssmp.gov.common.records.AreaPermitRecord;
import com.horizonssmp.gov.common.records.BountyRecord;
import com.horizonssmp.gov.common.records.FineRecord;
import com.horizonssmp.gov.common.records.LicenseRecord;
import com.horizonssmp.gov.common.records.PermitRecord;
import com.horizonssmp.gov.common.records.PlateScanRecord;
import com.horizonssmp.gov.common.records.VehicleRecord;
import com.horizonssmp.gov.common.records.WarrantRecord;
import com.horizonssmp.gov.common.records.WeaponRecord;

public final class GovRecordsStore {
    private final GovSavedData owner;
    final Map<String, VehicleRecord> vehicles = new LinkedHashMap<>();
    final Map<String, WeaponRecord> weapons = new LinkedHashMap<>();
    final Map<String, FineRecord> fines = new LinkedHashMap<>();
    final Map<String, WarrantRecord> warrants = new LinkedHashMap<>();
    final Map<String, BountyRecord> bounties = new LinkedHashMap<>();
    final Map<String, PlateScanRecord> plateScans = new LinkedHashMap<>();
    final Map<String, LicenseRecord> licenses = new LinkedHashMap<>();
    final Map<String, PermitRecord> weaponPermits = new LinkedHashMap<>();
    final Map<String, AreaPermitRecord> areaPermits = new LinkedHashMap<>();

    public GovRecordsStore(final GovSavedData owner) {
        this.owner = owner;
    }

    public Map<String, VehicleRecord> vehicles() { return Collections.unmodifiableMap(vehicles); }
    public VehicleRecord vehicle(final String id) { return id == null ? null : vehicles.get(id); }
    public void putVehicle(final VehicleRecord vehicle) { vehicles.put(vehicle.id(), vehicle); owner.setDirty(); }

    public Map<String, WeaponRecord> weapons() { return Collections.unmodifiableMap(weapons); }
    public WeaponRecord weapon(final String id) { return id == null ? null : weapons.get(id); }
    public void putWeapon(final WeaponRecord weapon) { weapons.put(weapon.id(), weapon); owner.setDirty(); }

    public Map<String, FineRecord> fines() { return Collections.unmodifiableMap(fines); }
    public FineRecord fine(final String id) { return id == null ? null : fines.get(id); }
    public void putFine(final FineRecord fine) { fines.put(fine.id(), fine); owner.setDirty(); }

    public Map<String, WarrantRecord> warrants() { return Collections.unmodifiableMap(warrants); }
    public WarrantRecord warrant(final String id) { return id == null ? null : warrants.get(id); }
    public void putWarrant(final WarrantRecord warrant) { warrants.put(warrant.id(), warrant); owner.setDirty(); }

    public Map<String, BountyRecord> bounties() { return Collections.unmodifiableMap(bounties); }
    public BountyRecord bounty(final String id) { return id == null ? null : bounties.get(id); }
    public void putBounty(final BountyRecord bounty) { bounties.put(bounty.id(), bounty); owner.setDirty(); }

    public Map<String, PlateScanRecord> plateScans() { return Collections.unmodifiableMap(plateScans); }
    public void putPlateScan(final PlateScanRecord scan) { plateScans.put(scan.id(), scan); owner.setDirty(); }

    public Map<String, LicenseRecord> licenses() { return Collections.unmodifiableMap(licenses); }
    public LicenseRecord license(final UUID playerUuid, final LicenseClass licenseClass) {
        return playerUuid == null || licenseClass == null ? null : licenses.get(LicenseRecord.key(playerUuid, licenseClass));
    }
    public List<LicenseRecord> licensesFor(final UUID playerUuid) {
        final List<LicenseRecord> result = new ArrayList<>();
        if (playerUuid == null) return result;
        for (final LicenseRecord record : licenses.values()) {
            if (playerUuid.equals(record.playerUuid())) result.add(record);
        }
        return result;
    }
    public void putLicense(final LicenseRecord license) {
        licenses.put(LicenseRecord.key(license.playerUuid(), license.licenseClass()), license);
        owner.setDirty();
    }

    public Map<String, PermitRecord> weaponPermits() { return Collections.unmodifiableMap(weaponPermits); }
    public PermitRecord weaponPermit(final UUID holderUuid, final PermitClass permitClass) {
        return holderUuid == null || permitClass == null ? null : weaponPermits.get(PermitRecord.key(holderUuid, permitClass));
    }
    public List<PermitRecord> weaponPermitsFor(final UUID holderUuid) {
        final List<PermitRecord> result = new ArrayList<>();
        if (holderUuid == null) return result;
        for (final PermitRecord record : weaponPermits.values()) {
            if (holderUuid.equals(record.holderUuid())) result.add(record);
        }
        return result;
    }
    public void putWeaponPermit(final PermitRecord permit) {
        weaponPermits.put(PermitRecord.key(permit.holderUuid(), permit.permitClass()), permit);
        owner.setDirty();
    }

    public Map<String, AreaPermitRecord> areaPermits() { return Collections.unmodifiableMap(areaPermits); }
    public AreaPermitRecord areaPermit(final String id) { return id == null ? null : areaPermits.get(id); }
    public void putAreaPermit(final AreaPermitRecord permit) { areaPermits.put(permit.id(), permit); owner.setDirty(); }
}