package com.horizonssmp.gov.server;

import java.util.List;

import com.horizonssmp.gov.common.records.NbtSupport;
import com.horizonssmp.gov.common.records.PlayerRecord;
import com.horizonssmp.gov.common.records.JobRecord;
import com.horizonssmp.gov.common.records.BusinessRecord;
import com.horizonssmp.gov.common.records.LocationRecord;
import com.horizonssmp.gov.common.records.VehicleRecord;
import com.horizonssmp.gov.common.records.WeaponRecord;
import com.horizonssmp.gov.common.records.FineRecord;
import com.horizonssmp.gov.common.records.NoteRecord;
import com.horizonssmp.gov.common.records.WarrantRecord;
import com.horizonssmp.gov.common.records.BountyRecord;
import com.horizonssmp.gov.common.records.PlateScanRecord;
import com.horizonssmp.gov.common.records.LicenseRecord;
import com.horizonssmp.gov.common.records.PermitRecord;
import com.horizonssmp.gov.common.records.AreaPermitRecord;
import com.horizonssmp.gov.common.records.DetentionRecord;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

public final class GovSavedDataLoad {
    private GovSavedDataLoad() {}

    public static GovSavedData load(final CompoundTag tag, final HolderLookup.Provider registries) {
        final GovSavedData data = new GovSavedData();
        data.schemaVersion = NbtSupport.getInt(tag, "schemaVersion", GovSavedData.SCHEMA_VERSION);
        data.templatesSeeded = NbtSupport.getBoolean(tag, "templatesSeeded", false);
        data.auditHistory.auditSeq = NbtSupport.getLong(tag, "auditSeq", 0L);

        if (!tag.contains("licenses")) data.setDirty();
        if (!tag.contains("weaponPermits") || !tag.contains("areaPermits")) data.setDirty();
        if (!tag.contains("businesses")) data.setDirty();
        if (!tag.contains("detentions")) data.setDirty();
        if (!tag.contains("history")) data.setDirty();

        final GovPersonnelStore p = data.personnel;
        for (final CompoundTag element : NbtSupport.getCompoundList(tag, "players")) {
            readInto(element, "players", () -> {
                final PlayerRecord record = PlayerRecord.read(element, null);
                p.players.put(record.uuid(), record);
            });
        }
        for (final CompoundTag element : NbtSupport.getCompoundList(tag, "jobs")) {
            readInto(element, "jobs", () -> {
                final JobRecord record = JobRecord.read(element, null);
                p.jobs.put(record.id(), record);
            });
        }
        for (final CompoundTag element : NbtSupport.getCompoundList(tag, "businesses")) {
            readInto(element, "businesses", () -> {
                final BusinessRecord record = BusinessRecord.read(element, null);
                p.businesses.put(record.id(), record);
            });
        }
        if (tag.contains("jailCells")) {
            for (final CompoundTag cellTag : NbtSupport.getCompoundList(tag, "jailCells")) {
                readInto(cellTag, "jailCells", () -> {
                    final LocationRecord cell = LocationRecord.read(cellTag);
                    if (cell != null && cell.isSet()) {
                        final String name = cellTag.getString("name");
                        final String key = name.isBlank() ? "cell_" + p.runtimeCells.size() : name;
                        p.runtimeCells.put(key, cell);
                    }
                });
            }
        }

        final GovRecordsStore r = data.records;
        for (final CompoundTag element : NbtSupport.getCompoundList(tag, "vehicles")) {
            readInto(element, "vehicles", () -> {
                final VehicleRecord record = VehicleRecord.read(element, null);
                r.vehicles.put(record.id(), record);
            });
        }
        for (final CompoundTag element : NbtSupport.getCompoundList(tag, "weapons")) {
            readInto(element, "weapons", () -> {
                final WeaponRecord record = WeaponRecord.read(element, null);
                r.weapons.put(record.id(), record);
            });
        }
        for (final CompoundTag element : NbtSupport.getCompoundList(tag, "fines")) {
            readInto(element, "fines", () -> {
                final FineRecord record = FineRecord.read(element, null);
                r.fines.put(record.id(), record);
            });
        }
        for (final CompoundTag element : NbtSupport.getCompoundList(tag, "notes")) {
            readInto(element, "notes", () -> {
                final NoteRecord record = NoteRecord.read(element, null);
                data.noteStore.notes.put(record.id(), record);
            });
        }
        for (final CompoundTag element : NbtSupport.getCompoundList(tag, "warrants")) {
            readInto(element, "warrants", () -> {
                final WarrantRecord record = WarrantRecord.read(element, null);
                r.warrants.put(record.id(), record);
            });
        }
        for (final CompoundTag element : NbtSupport.getCompoundList(tag, "bounties")) {
            readInto(element, "bounties", () -> {
                final BountyRecord record = BountyRecord.read(element, null);
                r.bounties.put(record.id(), record);
            });
        }
        for (final CompoundTag element : NbtSupport.getCompoundList(tag, "plateScans")) {
            readInto(element, "plateScans", () -> {
                final PlateScanRecord record = PlateScanRecord.read(element, null);
                r.plateScans.put(record.id(), record);
            });
        }
        for (final CompoundTag element : NbtSupport.getCompoundList(tag, "licenses")) {
            readInto(element, "licenses", () -> {
                final LicenseRecord record = LicenseRecord.read(element, null);
                if (record == null) {
                    com.horizonssmp.gov.Gov.LOGGER.warn(
                            "[gov] skipping one licence entry in 'licenses': unknown or missing class/player");
                    return;
                }
                r.licenses.put(LicenseRecord.key(record.playerUuid(), record.licenseClass()), record);
            });
        }
        for (final CompoundTag element : NbtSupport.getCompoundList(tag, "weaponPermits")) {
            readInto(element, "weaponPermits", () -> {
                final PermitRecord record = PermitRecord.read(element, null);
                if (record == null) {
                    com.horizonssmp.gov.Gov.LOGGER.warn(
                            "[gov] skipping one permit entry in 'weaponPermits': unknown or missing class/holder");
                    return;
                }
                r.weaponPermits.put(PermitRecord.key(record.holderUuid(), record.permitClass()), record);
            });
        }
        for (final CompoundTag element : NbtSupport.getCompoundList(tag, "areaPermits")) {
            readInto(element, "areaPermits", () -> {
                final AreaPermitRecord record = AreaPermitRecord.read(element, null);
                if (record == null) {
                    com.horizonssmp.gov.Gov.LOGGER.warn(
                            "[gov] skipping one area permit in 'areaPermits': missing id or dimension");
                    return;
                }
                r.areaPermits.put(record.id(), record);
            });
        }
        for (final CompoundTag element : NbtSupport.getCompoundList(tag, "detentions")) {
            readInto(element, "detentions", () -> {
                final DetentionRecord record = DetentionRecord.read(element);
                if (record == null) {
                    com.horizonssmp.gov.Gov.LOGGER.warn(
                            "[gov] skipping one detention in 'detentions': missing id or target");
                    return;
                }
                data.detentionStore.detentions.put(record.id(), record);
            });
        }

        GovSavedDataLoadEconomy.load(data, tag);
        return data;
    }

    private static void readInto(final CompoundTag element, final String section, final Runnable reader) {
        try {
            reader.run();
        } catch (final RuntimeException e) {
            com.horizonssmp.gov.Gov.LOGGER.warn("[gov] skipping one unreadable entry in '{}': {}", section, e.toString());
        }
    }
}