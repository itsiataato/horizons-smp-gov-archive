package com.horizonssmp.gov.common.records;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import com.horizonssmp.gov.common.BusinessKeys;

import net.minecraft.nbt.CompoundTag;

/**
 * NBT (de)serialization helpers for {@link BusinessRecord}. Behavior-preserving delegates.
 */
public final class BusinessRecordNbt {

    private BusinessRecordNbt() {
    }

    public static CompoundTag write(final BusinessRecord r) {
        final CompoundTag tag = new CompoundTag();
        tag.putString("id", r.id());
        tag.putString("name", r.name());
        NbtSupport.putUuid(tag, "ownerUuid", r.ownerUuid());
        tag.putString("type", r.type());
        tag.putString("status", r.status());
        tag.putLong("createdAt", r.createdAt());
        NbtSupport.putUuid(tag, "createdBy", r.createdBy());
        tag.putString("linkedJobId", r.linkedJobId());
        NbtSupport.putCompoundList(tag, "employees",
                r.employees().stream().map(BusinessEmployee::write).toList());
        tag.put("payroll", r.payroll().write());
        tag.putString("notes", r.notes());
        tag.putString("suspendReason", r.suspendReason());
        tag.putLong("suspendedAt", r.suspendedAt());
        NbtSupport.putUuid(tag, "suspendedBy", r.suspendedBy());
        NbtSupport.putCompoundList(tag, "positions",
                r.positions().stream().map(BusinessPositionRecord::write).toList());
        NbtSupport.putCompoundList(tag, "contracts",
                r.contracts().stream().map(EmploymentContractRecord::write).toList());
        NbtSupport.putCompoundList(tag, "complaints",
                r.complaints().stream().map(BusinessComplaintRecord::write).toList());
        NbtSupport.putUuid(tag, "closeRequestedBy", r.closeRequestedBy());
        tag.putLong("closeRequestedAt", r.closeRequestedAt());
        return tag;
    }

    public static BusinessRecord read(final CompoundTag tag, final String fallbackId) {
        final String stored = NbtSupport.getString(tag, "id", "");
        final String id = stored.isBlank() ? fallbackId : stored;
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("BusinessRecord tag has no id and no fallback");
        }
        return new BusinessRecord(
                id,
                NbtSupport.getString(tag, "name", ""),
                NbtSupport.getUuid(tag, "ownerUuid"),
                NbtSupport.getString(tag, "type", ""),
                NbtSupport.getString(tag, "status", BusinessRecord.STATUS_ACTIVE),
                NbtSupport.getLong(tag, "createdAt", 0L),
                NbtSupport.getUuid(tag, "createdBy"),
                NbtSupport.getString(tag, "linkedJobId", ""),
                readEmployees(tag),
                PayrollInfo.read(NbtSupport.getCompoundOr(tag, "payroll")),
                NbtSupport.getString(tag, "notes", ""),
                NbtSupport.getString(tag, "suspendReason", ""),
                NbtSupport.getLong(tag, "suspendedAt", 0L),
                NbtSupport.getUuid(tag, "suspendedBy"),
                readCapped(tag, "positions", BusinessRecord.MAX_POSITIONS, BusinessPositionRecord::read),
                readCapped(tag, "contracts", BusinessRecord.MAX_CONTRACTS, EmploymentContractRecord::read),
                readCapped(tag, "complaints", BusinessRecord.MAX_COMPLAINTS, BusinessComplaintRecord::read),
                NbtSupport.getUuid(tag, "closeRequestedBy"),
                NbtSupport.getLong(tag, "closeRequestedAt", 0L));
    }

    private static List<BusinessEmployee> readEmployees(final CompoundTag tag) {
        final List<BusinessEmployee> result = new ArrayList<>();
        for (final CompoundTag element : NbtSupport.getCompoundList(tag, "employees")) {
            final BusinessEmployee entry = BusinessEmployee.read(element);
            if (entry != null) {
                result.add(entry);
            }
        }
        return List.copyOf(result);
    }

    private static <T> List<T> readCapped(
            final CompoundTag tag, final String key, final int maximum,
            final Function<CompoundTag, T> reader) {
        final List<T> result = new ArrayList<>();
        for (final CompoundTag element : NbtSupport.getCompoundList(tag, key)) {
            if (result.size() >= maximum) {
                break;
            }
            try {
                final T value = reader.apply(element);
                if (value != null) {
                    result.add(value);
                }
            } catch (final RuntimeException e) {
                // Drop the malformed row only.
            }
        }
        return List.copyOf(result);
    }
}