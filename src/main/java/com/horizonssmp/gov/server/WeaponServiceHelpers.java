package com.horizonssmp.gov.server;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.GovCapability;
import com.horizonssmp.gov.common.GovTime;
import com.horizonssmp.gov.common.TownCore;
import com.horizonssmp.gov.common.WeaponKeys;
import com.horizonssmp.gov.common.enums.DutyState;
import com.horizonssmp.gov.common.enums.HistoryKind;
import com.horizonssmp.gov.common.enums.PermitClass;
import com.horizonssmp.gov.common.enums.PermitStatus;
import com.horizonssmp.gov.common.enums.WeaponCategory;
import com.horizonssmp.gov.common.enums.WeaponClass;
import com.horizonssmp.gov.common.records.AreaPermitRecord;
import com.horizonssmp.gov.common.records.PermitRecord;
import com.horizonssmp.gov.common.records.PlayerRecord;
import com.horizonssmp.gov.common.records.WeaponRecord;
import com.horizonssmp.gov.config.GovConfig;


final class WeaponServiceHelpers {

    // ----------------------------------------------------------------- helpers

    public static String weaponType(final String rawType, final WeaponCategory category) {
        return Sanitizer.singleLine(rawType, 128);
    }

    public static String newWeaponId(final GovSavedData data) {
        for (int attempt = 0; attempt < 8; attempt++) {
            final String id = WeaponService.WEAPON_ID_PREFIX
                    + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            if (data.weapon(id) == null) {
                return id;
            }
        }
        String fallback = WeaponService.WEAPON_ID_PREFIX + Long.toHexString(System.nanoTime());
        while (data.weapon(fallback) != null) {
            fallback += "x";
        }
        return fallback;
    }

    public static String newAreaId(final GovSavedData data) {
        for (int attempt = 0; attempt < 8; attempt++) {
            final String id = AreaPermitRecord.ID_PREFIX
                    + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            if (data.areaPermit(id) == null) {
                return id;
            }
        }
        String fallback = AreaPermitRecord.ID_PREFIX + Long.toHexString(System.nanoTime());
        while (data.areaPermit(fallback) != null) {
            fallback += "x";
        }
        return fallback;
    }

    public static int parseInt(final String raw, final int fallback) {
        return com.horizonssmp.gov.common.GovFields.parseInt(raw, fallback);
    }

    public static long parseLong(final String raw, final long fallback) {
        return com.horizonssmp.gov.common.GovFields.parseLong(raw, fallback);
    }

    public static boolean isInt(final String raw) {
        try {
            Integer.parseInt(raw == null ? "" : raw.trim());
            return true;
        } catch (final NumberFormatException e) {
            return false;
        }
    }

    public static String clip(final String text, final int max) {
        final String value = text == null ? "" : text;
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, Math.max(0, max - 3)) + "...";
    }

    /** The permit record a holder and class resolve to, invalid or not, or {@code null}. */
    public static PermitRecord permitRecord(
            final GovSavedData data, final UUID holderUuid, final PermitClass permitClass) {
        return data == null ? null : data.weaponPermit(holderUuid, permitClass);
    }

    /** A class's display label, for command help. */
    public static String permitLabel(final String rawClass) {
        final PermitClass permitClass = PermitClass.parse(rawClass);
        return permitClass == null ? "" : permitClass.label();
    }

    /** The optional note held by a permit, or empty. */
    /** A person-card summary of every permit the holder holds, mirroring { VehicleService.summary}. */
    public static String permitSummary(final GovSavedData data, final UUID holderUuid, final long now) {
        if (data == null || holderUuid == null) {
            return "none";
        }
        final java.util.List<PermitRecord> permits = new java.util.ArrayList<>(data.weaponPermitsFor(holderUuid));
        permits.sort(java.util.Comparator.comparingInt(record -> record.permitClass().ordinal()));
        if (permits.isEmpty()) {
            return "none";
        }
        final StringBuilder out = new StringBuilder();
        for (final PermitRecord permit : permits) {
            if (out.length() > 0) {
                out.append("; ");
            }
            out.append(permit.permitClass().label())
                    .append(" (").append(permit.permitClass().code()).append("): ")
                    .append(permit.stateAt(now).name());
            if (permit.stateAt(now) == PermitStatus.SUSPENDED) {
                out.append(" until ").append(java.time.Instant.ofEpochMilli(permit.suspendedUntil()));
            }
        }
        return out.toString();
    }

    public static String permitNote(final PermitRecord permit) {
        return permit == null ? "" : permit.note();
    }

    /** True when a category is one a registry may use. */
    public static boolean registrable(final WeaponCategory category) {
        return category != null && category != WeaponCategory.UNCLASSIFIED;
    }

    /** The classified category a raw class names, or {@code null}. */
    public static WeaponCategory registrableCategory(final String rawClass) {
        final WeaponCategory category = WeaponCategory.byId(rawClass).orElse(null);
        return registrable(category) ? category : null;
    }

    /** The optional class of a permit, or empty. */
    public static Optional<PermitClass> permitClass(final String rawClass) {
        return PermitClass.byId(rawClass);
    }
}
