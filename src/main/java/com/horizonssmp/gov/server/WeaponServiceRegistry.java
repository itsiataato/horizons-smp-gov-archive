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


final class WeaponServiceRegistry {

    // ------------------------------------------------------------------- registry

    /**
     * Registers a firearm to an owner.
     *
     * <p>The owner must already hold the matching carry permit: Article 5.14 makes unlicensed carry
     * an offence, and Article 5.09 makes a sale to a person without the matching class aggravated.
     * A Class H registration additionally requires a valid Class G permit, which is the military-only
     * gate. A state-held weapon (no owner) is a future staff path and is not exposed by this build;
     * every command registration names an owner.
     */
    public static WeaponService.Outcome register(
            final GovSavedData data,
            final WeaponService.Actor actor,
            final UUID ownerUuid,
            final String ownerName,
            final String rawClass,
            final String rawSerial,
            final String rawType,
            final long now) {
        return register(data, actor, ownerUuid, ownerName, rawClass, rawSerial, rawType,
                now, 0L, false);
    }

    /**
     * Registers a dealer sale, enforcing Article 5.09's one-in-game-day registration window.
     *
     * @param soldAt epoch milliseconds the dealer recorded the sale; {@code 0} means now
     * @param now the registration time
     */
    public static WeaponService.Outcome registerDealerSale(
            final GovSavedData data,
            final WeaponService.Actor actor,
            final UUID ownerUuid,
            final String ownerName,
            final String rawClass,
            final String rawSerial,
            final String rawType,
            final long soldAt,
            final long now) {
        return register(data, actor, ownerUuid, ownerName, rawClass, rawSerial, rawType,
                now, soldAt <= 0L ? now : soldAt, true);
    }

    public static WeaponService.Outcome register(
            final GovSavedData data,
            final WeaponService.Actor actor,
            final UUID ownerUuid,
            final String ownerName,
            final String rawClass,
            final String rawSerial,
            final String rawType,
            final long now,
            final long soldAt,
            final boolean dealerSale) {
        if (data == null || actor == null || ownerUuid == null) {
            return WeaponService.Outcome.refused(ErrorCode.INVALID_STATE, "No owner to register the weapon to.");
        }
        if (!actor.can(GovCapability.WEAPON_REGISTER)) {
            return WeaponService.Outcome.refused(
                    ErrorCode.FORBIDDEN,
                    "You need " + GovCapability.WEAPON_REGISTER.id() + " to register a weapon.");
        }
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
        final String type = WeaponServiceHelpers.weaponType(rawType, category);
        if (type.isBlank()) {
            return WeaponService.Outcome.refused(
                    ErrorCode.BAD_PAYLOAD, "A registration needs a type or item id.");
        }
        if (dealerSale) {
            if (!actor.can(GovCapability.WEAPON_REGISTER)) {
                return WeaponService.Outcome.refused(
                        ErrorCode.FORBIDDEN, "A dealer sale needs weapon.register.");
            }
            final PermitRecord dealer = WeaponServicePermits.activePermit(data, actor.uuid(), PermitClass.D, now);
            if (dealer == null) {
                return WeaponService.Outcome.refused(
                        ErrorCode.FORBIDDEN,
                        "A dealer sale needs a valid Class D dealer permit.");
            }
            if (soldAt > now) {
                return WeaponService.Outcome.refused(
                        ErrorCode.BAD_PAYLOAD, "The sale time cannot be in the future.");
            }
            if (now - soldAt > WeaponService.REGISTRATION_WINDOW_MS) {
                return WeaponService.Outcome.refused(
                        ErrorCode.INVALID_STATE,
                        "The sale must be registered within one in-game day (Article 5.09).");
            }
        }

        final PermitRecord permit = permitForRegistration(data, ownerUuid, category, now);
        if (permit == null) {
            return WeaponService.Outcome.refused(
                    ErrorCode.INVALID_STATE,
                    WeaponServiceSerials.ownerName(data, ownerUuid) + " holds no valid "
                            + category.permitClass().label() + " permit for a Class "
                            + category.name() + " weapon.");
        }
        if (category == WeaponCategory.H
                && WeaponServicePermits.activePermit(data, ownerUuid, PermitClass.G, now) == null) {
            return WeaponService.Outcome.refused(
                    ErrorCode.INVALID_STATE,
                    "Class H is military-only: " + WeaponServiceSerials.ownerName(data, ownerUuid)
                            + " needs a valid Class G government duty permit (Article 5.08).");
        }

        data.playerOrCreate(ownerUuid, Sanitizer.name(ownerName), now);
        final String id = WeaponServiceHelpers.newWeaponId(data);
        data.putWeapon(new WeaponRecord(
                id, serial, type, "", category, category.militaryGrade(),
                actor.uuid(), now, ownerUuid, dealerSale ? actor.uuid() : null,
                dealerSale ? "dealer-sale" : "registry",
                now, actor.uuid(), 0L, WeaponRecord.STATUS_ACTIVE,
                type, permit.id(), dealerSale ? soldAt : 0L, List.of()));
        return WeaponService.Outcome.ok(
                "registered " + serial + " (Class " + category.name() + ") to "
                        + WeaponServiceSerials.ownerName(data, ownerUuid) + ".", id);
    }

    /** The permit that authorizes a category for a holder, or {@code null}. */
    public static PermitRecord permitForRegistration(
            final GovSavedData data, final UUID ownerUuid, final WeaponCategory category,
            final long now) {
        final PermitClass required = category.permitClass();
        if (required == null) {
            return null;
        }
        return WeaponServicePermits.activePermit(data, ownerUuid, required, now);
    }
}
