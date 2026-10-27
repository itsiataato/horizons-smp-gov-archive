package com.horizonssmp.gov.server;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.horizonssmp.gov.Gov;
import com.horizonssmp.gov.common.GovExportSection;
import com.horizonssmp.gov.common.records.AuditEntry;
import com.horizonssmp.gov.common.records.AreaPermitRecord;
import com.horizonssmp.gov.common.records.BusinessRecord;
import com.horizonssmp.gov.common.records.DetentionRecord;
import com.horizonssmp.gov.common.records.FineRecord;
import com.horizonssmp.gov.common.records.JobRecord;
import com.horizonssmp.gov.common.records.LicenseRecord;
import com.horizonssmp.gov.common.records.NoteRecord;
import com.horizonssmp.gov.common.records.PermitRecord;
import com.horizonssmp.gov.common.records.PlayerRecord;
import com.horizonssmp.gov.common.records.PlateScanRecord;
import com.horizonssmp.gov.common.records.VehicleRecord;
import com.horizonssmp.gov.common.records.WarrantRecord;
import com.horizonssmp.gov.common.records.WeaponRecord;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

final class GovExportJson {

    private GovExportJson() {
    }

    static JsonObject metadata(final String format, final long now) {
        final JsonObject root = new JsonObject();
        root.addProperty("format", format);
        root.addProperty("createdAt", now);
        root.addProperty("createdAtIso", java.time.Instant.ofEpochMilli(now).toString());
        root.addProperty("mod", "gov");
        root.addProperty("modVersion", Gov.version());
        return root;
    }

    static JsonObject fullData(final GovSavedData data) {
        final JsonObject object = new JsonObject();
        final List<PlayerRecord> players = new ArrayList<>(data.players().values());
        players.sort(Comparator.comparing(record -> record.uuid().toString()));
        object.add("players", array(players, PlayerRecord::write));
        object.add("jobs", array(sortedValues(data.jobs()), JobRecord::write));
        object.add("businesses", array(sortedValues(data.businesses()), BusinessRecord::write));
        object.add("vehicles", array(sortedValues(data.vehicles()), VehicleRecord::write));
        object.add("weapons", array(sortedValues(data.weapons()), WeaponRecord::write));
        object.add("fines", array(sortedValues(data.fines()), FineRecord::write));
        object.add("notes", array(sortedValues(data.notes()), NoteRecord::write));
        object.add("warrants", array(sortedValues(data.warrants()), WarrantRecord::write));
        object.add("plateScans", array(sortedValues(data.plateScans()), PlateScanRecord::write));
        object.add("licenses", array(sortedValues(data.licenses()), LicenseRecord::write));
        object.add("weaponPermits", array(sortedValues(data.weaponPermits()), PermitRecord::write));
        object.add("areaPermits", array(sortedValues(data.areaPermits()), AreaPermitRecord::write));
        object.add("detentions", array(sortedValues(data.detentions()), DetentionRecord::write));
        object.add("audit", array(data.audit(), AuditEntry::write));
        return object;
    }

    static JsonArray sectionArray(final GovSavedData data, final GovExportSection section) {
        return switch (section) {
            case AUDIT -> array(data.audit(), AuditEntry::write);
            case PLAYERS -> {
                final List<PlayerRecord> players = new ArrayList<>(data.players().values());
                players.sort(Comparator.comparing(record -> record.uuid().toString()));
                yield array(players, PlayerRecord::write);
            }
            case JOBS -> array(sortedValues(data.jobs()), JobRecord::write);
            case FINES -> array(sortedValues(data.fines()), FineRecord::write);
            case NOTES -> array(sortedValues(data.notes()), NoteRecord::write);
            case BUSINESSES -> array(sortedValues(data.businesses()), BusinessRecord::write);
            case VEHICLES -> array(sortedValues(data.vehicles()), VehicleRecord::write);
            case WEAPONS -> array(sortedValues(data.weapons()), WeaponRecord::write);
            case PERMITS -> array(sortedValues(data.weaponPermits()), PermitRecord::write);
            case AREA_PERMITS -> array(sortedValues(data.areaPermits()), AreaPermitRecord::write);
            case DETENTIONS -> array(sortedValues(data.detentions()), DetentionRecord::write);
        };
    }

    private static <T> List<T> sortedValues(final Map<String, T> map) {
        final List<String> keys = new ArrayList<>(map.keySet());
        keys.sort(String::compareTo);
        final List<T> values = new ArrayList<>(keys.size());
        for (final String key : keys) {
            values.add(map.get(key));
        }
        return values;
    }

    @FunctionalInterface
    interface TagWriter<T> {
        CompoundTag write(T value);
    }

    private static <T> JsonArray array(final List<T> values, final TagWriter<T> writer) {
        final JsonArray array = new JsonArray();
        for (final T value : values) {
            array.add(tagToJson(writer.write(value)));
        }
        return array;
    }

    static JsonElement tagToJson(final Tag tag) {
        if (tag == null) {
            return com.google.gson.JsonNull.INSTANCE;
        }
        return switch (tag.getId()) {
            case Tag.TAG_BYTE -> new com.google.gson.JsonPrimitive(
                    ((net.minecraft.nbt.ByteTag) tag).getAsByte());
            case Tag.TAG_SHORT -> new com.google.gson.JsonPrimitive(
                    ((net.minecraft.nbt.ShortTag) tag).getAsShort());
            case Tag.TAG_INT -> new com.google.gson.JsonPrimitive(
                    ((net.minecraft.nbt.IntTag) tag).getAsInt());
            case Tag.TAG_LONG -> new com.google.gson.JsonPrimitive(
                    ((net.minecraft.nbt.LongTag) tag).getAsLong());
            case Tag.TAG_FLOAT -> new com.google.gson.JsonPrimitive(
                    ((net.minecraft.nbt.FloatTag) tag).getAsFloat());
            case Tag.TAG_DOUBLE -> new com.google.gson.JsonPrimitive(
                    ((net.minecraft.nbt.DoubleTag) tag).getAsDouble());
            case Tag.TAG_STRING -> new com.google.gson.JsonPrimitive(tag.getAsString());
            case Tag.TAG_BYTE_ARRAY -> intArray(
                    ((net.minecraft.nbt.ByteArrayTag) tag).getAsByteArray());
            case Tag.TAG_INT_ARRAY -> intArray(
                    ((net.minecraft.nbt.IntArrayTag) tag).getAsIntArray());
            case Tag.TAG_LONG_ARRAY -> {
                final long[] values = ((net.minecraft.nbt.LongArrayTag) tag).getAsLongArray();
                final JsonArray array = new JsonArray(values.length);
                for (final long value : values) {
                    array.add(value);
                }
                yield array;
            }
            case Tag.TAG_LIST -> {
                final ListTag list = (ListTag) tag;
                final JsonArray array = new JsonArray(list.size());
                for (int i = 0; i < list.size(); i++) {
                    array.add(tagToJson(list.get(i)));
                }
                yield array;
            }
            case Tag.TAG_COMPOUND -> {
                final CompoundTag compound = (CompoundTag) tag;
                final JsonObject object = new JsonObject();
                final List<String> keys = new ArrayList<>(compound.getAllKeys());
                keys.sort(String::compareTo);
                for (final String key : keys) {
                    object.add(key, tagToJson(compound.get(key)));
                }
                yield object;
            }
            default -> new com.google.gson.JsonPrimitive(tag.getAsString());
        };
    }

    private static JsonArray intArray(final byte[] values) {
        final JsonArray array = new JsonArray(values.length);
        for (final byte value : values) {
            array.add(value);
        }
        return array;
    }

    private static JsonArray intArray(final int[] values) {
        final JsonArray array = new JsonArray(values.length);
        for (final int value : values) {
            array.add(value);
        }
        return array;
    }
}