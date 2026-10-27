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


final class WeaponServicePrechecks {

    // ----------------------------------------------------------------- prechecks

    /** The data-only refusal a registration would get, or {@code null}. */
    public static WeaponService.Outcome registerPrecheck(
            final GovSavedData data, final String rawClass, final String rawSerial,
            final String rawType) {
        final WeaponCategory category = WeaponCategory.byId(rawClass).orElse(null);
        if (category == null || category == WeaponCategory.UNCLASSIFIED) {
            return WeaponService.Outcome.refused(
                    ErrorCode.BAD_PAYLOAD,
                    "Unknown weapon class. Use one of: " + WeaponCategory.ids() + ".");
        }
        final String serial = WeaponServiceSerials.canonicalSerial(rawSerial);
        if (serial == null) {
            return WeaponService.Outcome.refused(ErrorCode.BAD_PAYLOAD, WeaponServiceSerials.invalidSerialMessage(rawSerial));
        }
        if (WeaponServiceSerials.bySerial(data, serial) != null) {
            return WeaponService.Outcome.refused(
                    ErrorCode.SERIAL_TAKEN, "Serial " + serial + " is already registered.");
        }
        if (WeaponServiceHelpers.weaponType(rawType, category).isBlank()) {
            return WeaponService.Outcome.refused(
                    ErrorCode.BAD_PAYLOAD, "A registration needs a type or item id.");
        }
        return null;
    }

    /** The data-only refusal a transfer would get, or {@code null}. */
    public static WeaponService.Outcome transferPrecheck(final GovSavedData data, final String rawSerial) {
        final WeaponRecord weapon = WeaponServiceSerials.bySerial(data, rawSerial);
        if (weapon == null) {
            return WeaponService.Outcome.refused(ErrorCode.BAD_WEAPON, WeaponServiceSerials.noSuchWeaponMessage(rawSerial));
        }
        if (WeaponRecord.STATUS_REVOKED.equals(weapon.status())) {
            return WeaponService.Outcome.refused(
                    ErrorCode.INVALID_STATE, "Serial " + weapon.serial() + " is revoked.");
        }
        if (WeaponRecord.STATUS_LOST.equals(weapon.status())) {
            return WeaponService.Outcome.refused(
                    ErrorCode.INVALID_STATE, "Serial " + weapon.serial() + " is reported lost.");
        }
        return null;
    }

    /** The data-only refusal a permit issue would get, or {@code null}. */
    public static WeaponService.Outcome permitIssuePrecheck(final String rawClass) {
        final PermitClass permitClass = PermitClass.parse(rawClass);
        if (permitClass == null) {
            return WeaponService.Outcome.refused(
                    ErrorCode.BAD_PAYLOAD,
                    "Unknown permit class. Use one of: " + PermitClass.ids() + ".");
        }
        return null;
    }

    /** The data-only refusal a permit revoke or suspend would get, or {@code null}. */
    public static WeaponService.Outcome permitPrecheck(
            final GovSavedData data, final UUID holderUuid, final String rawClass) {
        final PermitClass permitClass = PermitClass.parse(rawClass);
        if (permitClass == null) {
            return WeaponService.Outcome.refused(
                    ErrorCode.BAD_PAYLOAD,
                    "Unknown permit class. Use one of: " + PermitClass.ids() + ".");
        }
        if (data != null && holderUuid != null && data.weaponPermit(holderUuid, permitClass) == null) {
            return WeaponService.Outcome.refused(
                    ErrorCode.BAD_PERMIT,
                    WeaponServicePermits.noSuchPermitMessage(data, holderUuid, "", permitClass));
        }
        return null;
    }

    /** The data-only refusal an explosion-area create would get, or {@code null}. */
    public static WeaponService.Outcome areaCreatePrecheck(
            final String rawDimension, final String rawX, final String rawY, final String rawZ,
            final String rawRadius, final String rawMinutes, final String rawReason) {
        if (WeaponServiceAreas.canonicalDimension(rawDimension) == null) {
            return WeaponService.Outcome.refused(
                    ErrorCode.BAD_PAYLOAD,
                    "Dimension must be a resource id such as minecraft:overworld.");
        }
        if (!WeaponServiceHelpers.isInt(rawX) || !WeaponServiceHelpers.isInt(rawY) || !WeaponServiceHelpers.isInt(rawZ)) {
            return WeaponService.Outcome.refused(ErrorCode.BAD_PAYLOAD, "The area center must be three integers.");
        }
        final int radius = WeaponServiceHelpers.parseInt(rawRadius, -1);
        if (radius < WeaponService.MIN_AREA_RADIUS || radius > WeaponService.MAX_AREA_RADIUS) {
            return WeaponService.Outcome.refused(
                    ErrorCode.BAD_PAYLOAD,
                    "The radius must be " + WeaponService.MIN_AREA_RADIUS + "-" + WeaponService.MAX_AREA_RADIUS + " blocks.");
        }
        final long minutes = WeaponServiceHelpers.parseLong(rawMinutes, -1L);
        if (minutes <= 0L || minutes > WeaponService.MAX_AREA_MINUTES) {
            return WeaponService.Outcome.refused(
                    ErrorCode.BAD_PAYLOAD,
                    "The permit must run 1-" + WeaponService.MAX_AREA_MINUTES + " minutes.");
        }
        if (Sanitizer.text(rawReason).isBlank()) {
            return WeaponService.Outcome.refused(
                    ErrorCode.BAD_PAYLOAD, "An explosion-area permit needs a recorded reason.");
        }
        return null;
    }

    /** The classification answer for an item id or a live held stack. */
    public static WeaponClassifier.Classification classify(
            final String rawItemId, final WeaponClassifier classifier) {
        final WeaponClassifier source = classifier == null ? WeaponClassifier.get() : classifier;
        if (rawItemId == null || rawItemId.isBlank()) {
            return new WeaponClassifier.Classification("", WeaponClass.NONE, "none", false);
        }
        final String id = rawItemId.trim().toLowerCase(Locale.ROOT);
        if (!WeaponService.ITEM_ID.matcher(id).matches()) {
            return new WeaponClassifier.Classification(id, WeaponClass.NONE, "none", false);
        }
        return source.classifyId(id);
    }

}
