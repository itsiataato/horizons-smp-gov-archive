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



final class WeaponServiceTransfer {

    public static WeaponService.Outcome transfer(
            final GovSavedData data,
            final WeaponService.Actor actor,
            final String rawSerial,
            final UUID newOwnerUuid,
            final String newOwnerName,
            final long now) {
        return transfer(data, actor, rawSerial, newOwnerUuid, newOwnerName, now, now);
    }

    /** The transfer with an explicit requested stamp, for the one-day window assertion. */
    public static WeaponService.Outcome transfer(
            final GovSavedData data,
            final WeaponService.Actor actor,
            final String rawSerial,
            final UUID newOwnerUuid,
            final String newOwnerName,
            final long requestedAt,
            final long now) {
        if (data == null || actor == null || newOwnerUuid == null) {
            return WeaponService.Outcome.refused(ErrorCode.INVALID_STATE, "No owner to transfer the weapon to.");
        }
        if (!actor.can(GovCapability.WEAPON_REGISTER)) {
            return WeaponService.Outcome.refused(
                    ErrorCode.FORBIDDEN,
                    "You need " + GovCapability.WEAPON_REGISTER.id() + " to transfer a weapon.");
        }
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
                    ErrorCode.INVALID_STATE,
                    "Serial " + weapon.serial() + " is reported lost; recover it before transfer.");
        }
        if (requestedAt <= 0L || requestedAt > now) {
            return WeaponService.Outcome.refused(ErrorCode.BAD_PAYLOAD, "The transfer request time is not valid.");
        }
        if (now - requestedAt > WeaponService.TRANSFER_WINDOW_MS) {
            return WeaponService.Outcome.refused(
                    ErrorCode.INVALID_STATE,
                    "A transfer must be recorded within one in-game day (Article 5.09).");
        }
        final PermitRecord permit = WeaponServiceRegistry.permitForRegistration(data, newOwnerUuid, weapon.category(), now);
        if (permit == null) {
            return WeaponService.Outcome.refused(
                    ErrorCode.INVALID_STATE,
                    WeaponServiceSerials.ownerName(data, newOwnerUuid) + " holds no valid "
                            + weapon.category().permitClass().label() + " permit for Class "
                            + weapon.category().name() + ".");
        }
        if (weapon.category() == WeaponCategory.H
                && WeaponServicePermits.activePermit(data, newOwnerUuid, PermitClass.G, now) == null) {
            return WeaponService.Outcome.refused(
                    ErrorCode.INVALID_STATE,
                    "Class H is military-only: " + WeaponServiceSerials.ownerName(data, newOwnerUuid)
                            + " needs a valid Class G permit (Article 5.08).");
        }
        data.playerOrCreate(newOwnerUuid, Sanitizer.name(newOwnerName), now);
        data.putWeapon(weapon.withOwner(newOwnerUuid, actor.uuid(), now, "transfer")
                .withPermit(permit.id()));
        return WeaponService.Outcome.ok(
                "transferred " + weapon.serial() + " to " + WeaponServiceSerials.ownerName(data, newOwnerUuid) + ".",
                weapon.id());
    }

    /** Revokes a registration with a recorded reason. The record stays. */
    public static WeaponService.Outcome revoke(
            final GovSavedData data, final WeaponService.Actor actor, final String rawSerial,
            final String rawReason, final long now) {
        if (data == null || actor == null) {
            return WeaponService.Outcome.refused(ErrorCode.INVALID_STATE, "No weapon to revoke.");
        }
        if (!actor.can(GovCapability.WEAPON_REGISTER)) {
            return WeaponService.Outcome.refused(
                    ErrorCode.FORBIDDEN,
                    "You need " + GovCapability.WEAPON_REGISTER.id() + " to revoke a registration.");
        }
        final WeaponRecord weapon = WeaponServiceSerials.bySerial(data, rawSerial);
        if (weapon == null) {
            return WeaponService.Outcome.refused(ErrorCode.BAD_WEAPON, WeaponServiceSerials.noSuchWeaponMessage(rawSerial));
        }
        final String reason = Sanitizer.text(rawReason);
        if (reason.isBlank()) {
            return WeaponService.Outcome.refused(
                    ErrorCode.BAD_PAYLOAD, "A revocation needs a recorded reason.");
        }
        if (WeaponRecord.STATUS_REVOKED.equals(weapon.status())) {
            return WeaponService.Outcome.refused(
                    ErrorCode.INVALID_STATE, "Serial " + weapon.serial() + " is already revoked.");
        }
        data.putWeapon(weapon.revoked(actor.uuid(), now));
        return WeaponService.Outcome.ok("revoked " + weapon.serial() + ": " + reason, weapon.id());
    }

    /** Records a weapon lost or stolen. The owner may report it, as may a registrar. */
    public static WeaponService.Outcome reportLoss(
            final GovSavedData data, final WeaponService.Actor actor, final String rawSerial,
            final String rawReason) {
        if (data == null || actor == null) {
            return WeaponService.Outcome.refused(ErrorCode.INVALID_STATE, "No weapon to report.");
        }
        final WeaponRecord weapon = WeaponServiceSerials.bySerial(data, rawSerial);
        if (weapon == null) {
            return WeaponService.Outcome.refused(ErrorCode.BAD_WEAPON, WeaponServiceSerials.noSuchWeaponMessage(rawSerial));
        }
        final boolean owner = actor.uuid() != null && actor.uuid().equals(weapon.ownerUuid());
        if (!owner && !actor.can(GovCapability.WEAPON_REGISTER)) {
            return WeaponService.Outcome.refused(
                    ErrorCode.FORBIDDEN,
                    "Only the registered owner or a registrar may report this weapon lost.");
        }
        if (WeaponRecord.STATUS_REVOKED.equals(weapon.status())) {
            return WeaponService.Outcome.refused(
                    ErrorCode.INVALID_STATE, "Serial " + weapon.serial() + " is revoked.");
        }
        if (WeaponRecord.STATUS_LOST.equals(weapon.status())) {
            return WeaponService.Outcome.refused(
                    ErrorCode.INVALID_STATE, "Serial " + weapon.serial() + " is already lost.");
        }
        if (Sanitizer.text(rawReason).isBlank()) {
            return WeaponService.Outcome.refused(ErrorCode.BAD_PAYLOAD, "A loss report needs a recorded reason.");
        }
        data.putWeapon(weapon.lost());
        return WeaponService.Outcome.ok("reported " + weapon.serial() + " lost or stolen.", weapon.id());
    }

}
