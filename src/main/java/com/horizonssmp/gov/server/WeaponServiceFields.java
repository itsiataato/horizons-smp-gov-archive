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


final class WeaponServiceFields {

    public static Map<String, String> listFields(
            final GovSavedData data, final UUID viewerUuid, final String viewerName,
            final Set<GovCapability> capabilities, final String rawFilter,
            final UUID ownerFilter, final int page, final List<String> onlineNames) {
        final WeaponService.WeaponPage result = WeaponServicePages.page(data, viewerUuid, capabilities, rawFilter, ownerFilter, page);
        final Map<String, String> fields = new LinkedHashMap<>();
        fields.put(WeaponKeys.CAN_REGISTER,
                Boolean.toString(capabilities != null
                        && capabilities.contains(GovCapability.WEAPON_REGISTER)));
        fields.put(WeaponKeys.CAN_VIEW_ALL, Boolean.toString(WeaponServicePages.canViewAll(capabilities)));
        fields.put(WeaponKeys.CAN_MILITARY,
                Boolean.toString(capabilities != null
                        && capabilities.contains(GovCapability.MILITARY_AUTHORITY)));
        fields.put(WeaponKeys.VIEWER_UUID, viewerUuid == null ? "" : viewerUuid.toString());
        fields.put(WeaponKeys.VIEWER_NAME, Sanitizer.name(viewerName));
        fields.put(WeaponKeys.SEASON_END, Long.toString(GovConfig.seasonEndMillis()));
        fields.put(WeaponKeys.TOWN_CORES, Boolean.toString(GovConfig.townCoresConfigured()));
        fields.put(WeaponKeys.TOWN_CORE_COUNT,
                Integer.toString(GovConfig.townCoreRegions().size()));
        fields.put(WeaponKeys.TOTAL, Integer.toString(result.total()));
        fields.put(WeaponKeys.PAGE, Integer.toString(result.page()));
        fields.put(WeaponKeys.PAGES, Integer.toString(result.pages()));
        fields.put(WeaponKeys.FILTER, WeaponServicePages.canonicalFilter(rawFilter));

        for (int i = 0; i < result.rows().size(); i++) {
            final WeaponService.WeaponRow row = result.rows().get(i);
            final WeaponRecord weapon = row.weapon();
            final String prefix = WeaponKeys.ROW_PREFIX + i;
            fields.put(prefix + WeaponKeys.ROW_ID, weapon.id());
            fields.put(prefix + WeaponKeys.ROW_SERIAL, weapon.serial());
            fields.put(prefix + WeaponKeys.ROW_CLASS, weapon.category().name());
            fields.put(prefix + WeaponKeys.ROW_TYPE, weapon.type());
            fields.put(prefix + WeaponKeys.ROW_OWNER, row.ownerName());
            fields.put(prefix + WeaponKeys.ROW_OWNER_UUID,
                    weapon.ownerUuid() == null ? "" : weapon.ownerUuid().toString());
            fields.put(prefix + WeaponKeys.ROW_STATUS, weapon.status());
            fields.put(prefix + WeaponKeys.ROW_PERMIT, weapon.permitId());
            fields.put(prefix + WeaponKeys.ROW_MILITARY, Boolean.toString(weapon.militaryGrade()));
            fields.put(prefix + WeaponKeys.ROW_REGISTERED_AT,
                    Long.toString(weapon.registeredAt()));
        }

        final List<String> names = onlineNames == null ? List.of() : onlineNames;
        fields.put(WeaponKeys.ONLINE_COUNT, Integer.toString(names.size()));
        for (int i = 0; i < names.size() && i < WeaponKeys.MAX_LISTED_ONLINE; i++) {
            fields.put(WeaponKeys.ONLINE_PREFIX + i, Sanitizer.name(names.get(i)));
        }
        return fields;
    }

    /** The {@code WPN_INFO} answer: the registry entry, permit reference and transfer history. */

    public static Map<String, String> detailFields(
            final GovSavedData data, final WeaponRecord weapon) {
        if (weapon == null) {
            return Map.of();
        }
        final Map<String, String> fields = new LinkedHashMap<>();
        fields.put(WeaponKeys.ID, weapon.id());
        fields.put(WeaponKeys.SERIAL, weapon.serial());
        fields.put(WeaponKeys.CLASS, weapon.category().name());
        fields.put(WeaponKeys.TYPE, weapon.type());
        fields.put(WeaponKeys.NAME, weapon.exactName());
        fields.put(WeaponKeys.MOD_ID, weapon.taczId());
        fields.put(WeaponKeys.OWNER, WeaponServiceSerials.ownerName(data, weapon.ownerUuid()));
        fields.put(WeaponKeys.OWNER_UUID,
                weapon.ownerUuid() == null ? "" : weapon.ownerUuid().toString());
        fields.put(WeaponKeys.STATUS, weapon.status());
        fields.put(WeaponKeys.PERMIT, weapon.permitId());
        fields.put(WeaponKeys.MILITARY, Boolean.toString(weapon.militaryGrade()));
        fields.put(WeaponKeys.REGISTERED_AT, Long.toString(weapon.registeredAt()));
        fields.put(WeaponKeys.REGISTERED_BY, WeaponServiceSerials.ownerName(data, weapon.registeredBy()));
        fields.put(WeaponKeys.DEALER, WeaponServiceSerials.ownerName(data, weapon.dealerUuid()));
        fields.put(WeaponKeys.SOURCE, weapon.source());
        fields.put(WeaponKeys.SOLD_AT, Long.toString(weapon.soldAt()));
        fields.put(WeaponKeys.TRANSFER_COUNT, Integer.toString(weapon.transfers().size()));
        final int first = Math.max(0, weapon.transfers().size() - WeaponKeys.MAX_LISTED_TRANSFERS);
        int row = 0;
        for (int i = first; i < weapon.transfers().size(); i++) {
            final WeaponRecord.TransferEntry transfer = weapon.transfers().get(i);
            final String prefix = WeaponKeys.TRANSFER_PREFIX + row;
            fields.put(prefix + WeaponKeys.TRANSFER_FROM,
                    WeaponServiceSerials.ownerName(data, transfer.fromUuid()));
            fields.put(prefix + WeaponKeys.TRANSFER_TO, WeaponServiceSerials.ownerName(data, transfer.toUuid()));
            fields.put(prefix + WeaponKeys.TRANSFER_REQUESTED_AT,
                    Long.toString(transfer.requestedAt()));
            fields.put(prefix + WeaponKeys.TRANSFER_COMPLETED_AT,
                    Long.toString(transfer.completedAt()));
            fields.put(prefix + WeaponKeys.TRANSFER_BY, WeaponServiceSerials.ownerName(data, transfer.by()));
            fields.put(prefix + WeaponKeys.TRANSFER_IN_WINDOW,
                    Boolean.toString(transfer.recordedWithinWindow()));
            row++;
        }
        return fields;
    }

    /** The classification answer as a field map. */

    public static Map<String, String> classificationFields(
            final WeaponClassifier.Classification classification) {
        final Map<String, String> fields = new LinkedHashMap<>();
        final WeaponClass weaponClass = classification.enforcedClass();
        fields.put(WeaponKeys.ITEM_ID, classification.itemId());
        fields.put(WeaponKeys.ITEM_CLASS, weaponClass.id());
        fields.put(WeaponKeys.ITEM_CLASS_LABEL, weaponClass.label());
        fields.put(WeaponKeys.ITEM_SOURCE, classification.source());
        fields.put(WeaponKeys.ITEM_WEAPON_LIKE, Boolean.toString(classification.weaponLike()));
        fields.put(WeaponKeys.ITEM_PERMIT_CLASS,
                weaponClass.permitClass() == null ? "" : weaponClass.permitClass().id());
        fields.put(WeaponKeys.ITEM_MILITARY, Boolean.toString(weaponClass.militaryGrade()));
        fields.put(WeaponKeys.ITEM_EXPLOSIVE, Boolean.toString(weaponClass.explosive()));
        fields.put(WeaponKeys.ITEM_NUCLEAR, Boolean.toString(weaponClass.nuclear()));
        fields.put(WeaponKeys.ITEM_EXPLANATION, classification.explanation());
        return fields;
    }

    /** One page of a holder's permits, sorted by class. */

    public static Map<String, String> permitListFields(
            final GovSavedData data, final UUID holderUuid, final String holderName,
            final Set<GovCapability> capabilities, final int page, final long now) {
        final WeaponService.PermitPage result = WeaponServicePages.permitsPage(data, holderUuid, page, now);
        final Map<String, String> fields = new LinkedHashMap<>();
        fields.put(WeaponKeys.PERMIT_TOTAL, Integer.toString(result.total()));
        fields.put(WeaponKeys.PERMIT_PAGE, Integer.toString(result.page()));
        fields.put(WeaponKeys.PERMIT_PAGES, Integer.toString(result.pages()));
        fields.put(WeaponKeys.VIEWER_NAME, Sanitizer.name(holderName));
        fields.put(WeaponKeys.CAN_REGISTER,
                Boolean.toString(capabilities != null
                        && capabilities.contains(GovCapability.WEAPON_REGISTER)));
        fields.put(WeaponKeys.CAN_MILITARY,
                Boolean.toString(capabilities != null
                        && capabilities.contains(GovCapability.MILITARY_AUTHORITY)));
        for (int i = 0; i < result.rows().size(); i++) {
            final PermitRecord permit = result.rows().get(i);
            final String prefix = WeaponKeys.PERMIT_PREFIX + i;
            final PermitStatus state = permit.stateAt(now);
            fields.put(prefix + WeaponKeys.PERMIT_ID, permit.id());
            fields.put(prefix + WeaponKeys.PERMIT_HOLDER, WeaponServiceSerials.ownerName(data, permit.holderUuid()));
            fields.put(prefix + WeaponKeys.PERMIT_HOLDER_UUID, permit.holderUuid().toString());
            fields.put(prefix + WeaponKeys.PERMIT_CLASS, permit.permitClass().id());
            fields.put(prefix + WeaponKeys.PERMIT_STATUS, state.name());
            fields.put(prefix + WeaponKeys.PERMIT_ISSUED_BY, WeaponServiceSerials.ownerName(data, permit.issuedBy()));
            fields.put(prefix + WeaponKeys.PERMIT_ISSUED_AT, Long.toString(permit.issuedAt()));
            fields.put(prefix + WeaponKeys.PERMIT_EXPIRES_AT, Long.toString(permit.expiresAt()));
            fields.put(prefix + WeaponKeys.PERMIT_REASON, WeaponServiceHelpers.clip(permitReason(permit), 
                    WeaponKeys.LIST_REASON_CHARS));
            fields.put(prefix + WeaponKeys.PERMIT_MILITARY,
                    Boolean.toString(permit.permitClass().militaryOnly()));
            fields.put(prefix + WeaponKeys.PERMIT_CAN_MANAGE,
                    Boolean.toString(capabilities != null
                            && capabilities.contains(GovCapability.WEAPON_REGISTER)
                            && state != PermitStatus.REVOKED));
        }
        return fields;
    }


    public static String permitReason(final PermitRecord permit) {
        if (!permit.revokeReason().isBlank()) {
            return permit.revokeReason();
        }
        if (!permit.suspendReason().isBlank()) {
            return permit.suspendReason();
        }
        return permit.note();
    }

    // ------------------------------------------------------------------- areas

    /** One page of the explosion-area registry, newest first. */

    public static Map<String, String> areaListFields(
            final GovSavedData data, final Set<GovCapability> capabilities,
            final int page, final long now) {
        final WeaponService.AreaPage result = WeaponServicePages.areaPage(data, page, now);
        final Map<String, String> fields = new LinkedHashMap<>();
        fields.put(WeaponKeys.AREA_TOTAL, Integer.toString(result.total()));
        fields.put(WeaponKeys.AREA_PAGE, Integer.toString(result.page()));
        fields.put(WeaponKeys.AREA_PAGES, Integer.toString(result.pages()));
        fields.put(WeaponKeys.CAN_REGISTER,
                Boolean.toString(capabilities != null
                        && capabilities.contains(GovCapability.WEAPON_REGISTER)));
        for (int i = 0; i < result.rows().size(); i++) {
            final AreaPermitRecord permit = result.rows().get(i);
            final String prefix = WeaponKeys.AREA_PREFIX + i;
            fields.put(prefix + WeaponKeys.AREA_ID, permit.id());
            fields.put(prefix + WeaponKeys.AREA_DIMENSION, permit.dimension());
            fields.put(prefix + WeaponKeys.AREA_X, Integer.toString(permit.centerX()));
            fields.put(prefix + WeaponKeys.AREA_Y, Integer.toString(permit.centerY()));
            fields.put(prefix + WeaponKeys.AREA_Z, Integer.toString(permit.centerZ()));
            fields.put(prefix + WeaponKeys.AREA_RADIUS, Integer.toString(permit.radius()));
            fields.put(prefix + WeaponKeys.AREA_STATUS, permit.stateAt(now));
            fields.put(prefix + WeaponKeys.AREA_VALID_FROM, Long.toString(permit.validFrom()));
            fields.put(prefix + WeaponKeys.AREA_VALID_UNTIL, Long.toString(permit.validUntil()));
            fields.put(prefix + WeaponKeys.AREA_REASON,
                    WeaponServiceHelpers.clip(permit.reason(), WeaponKeys.LIST_REASON_CHARS));
            fields.put(prefix + WeaponKeys.AREA_ISSUED_BY, WeaponServiceSerials.ownerName(data, permit.issuedBy()));
            fields.put(prefix + WeaponKeys.AREA_BOX, permit.boxText());
        }
        return fields;
    }

}
