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


final class WeaponServicePermits {

    // ------------------------------------------------------------------- permits

    /**
     * Issues one permit.
     *
     * <p>Class G requires the issuer to hold {@code military.authority}: the military gate is a
     * capability, never a job name. Class H requires the holder to already hold a valid Class G
     * permit, which makes H military-only without naming a job. A user with the registrar capability
     * can issue the civilian classes and the Blasting and mining permits; only the military
     * authority can open the Class G gate.
     *
     * @param seasonEndMillis the configured season end, {@code 0} for no expiry
     */
    public static WeaponService.Outcome issuePermit(
            final GovSavedData data,
            final WeaponService.Actor actor,
            final UUID holderUuid,
            final String holderName,
            final String rawClass,
            final String rawNote,
            final long seasonEndMillis,
            final long now) {
        if (data == null || actor == null || holderUuid == null) {
            return WeaponService.Outcome.refused(ErrorCode.INVALID_STATE, "No person to issue a permit to.");
        }
        if (!actor.can(GovCapability.WEAPON_REGISTER)) {
            return WeaponService.Outcome.refused(
                    ErrorCode.FORBIDDEN,
                    "You need " + GovCapability.WEAPON_REGISTER.id() + " to issue a permit.");
        }
        final PermitClass permitClass = PermitClass.parse(rawClass);
        if (permitClass == null) {
            return WeaponService.Outcome.refused(
                    ErrorCode.BAD_PAYLOAD,
                    "Unknown permit class. Use one of: " + PermitClass.ids() + ".");
        }
        if (permitClass == PermitClass.G && !actor.can(GovCapability.MILITARY_AUTHORITY)) {
            return WeaponService.Outcome.refused(
                    ErrorCode.FORBIDDEN,
                    "Class G is military-only: you need " + GovCapability.MILITARY_AUTHORITY.id()
                            + " to issue it (Article 5.08).");
        }
        if (permitClass == PermitClass.H
                && activePermit(data, holderUuid, PermitClass.G, now) == null) {
            return WeaponService.Outcome.refused(
                    ErrorCode.INVALID_STATE,
                    "Class H is military-only: " + WeaponServiceSerials.ownerName(data, holderUuid)
                            + " needs a valid Class G permit first (Article 5.08).");
        }
        final PermitRecord existing = data.weaponPermit(holderUuid, permitClass);
        if (existing != null) {
            final PermitStatus state = existing.stateAt(now);
            if (state == PermitStatus.VALID || state == PermitStatus.SUSPENDED) {
                return WeaponService.Outcome.refused(
                        ErrorCode.INVALID_STATE,
                        WeaponServiceSerials.ownerName(data, holderUuid) + " already holds a " + permitClass.label()
                                + " (" + state.name().toLowerCase(Locale.ROOT) + ").");
            }
        }
        final long expiresAt = seasonEndMillis > now ? seasonEndMillis : 0L;
        data.playerOrCreate(holderUuid, Sanitizer.name(holderName), now);
        data.putWeaponPermit(PermitRecord.issued(
                holderUuid, Sanitizer.name(holderName), permitClass,
                actor.uuid(), now, expiresAt, Sanitizer.text(rawNote)));
        HistoryService.append(data, holderUuid, HistoryKind.WEAPON_PERMIT_ISSUED,
                permitClass.label() + " permit was issued to you"
                        + (expiresAt == 0L ? "." : " until the season end."),
                actor.uuid(), actor.name(), now, PermitRecord.key(holderUuid, permitClass));
        final String expiry = expiresAt == 0L
                ? "no expiry" : "expires " + java.time.Instant.ofEpochMilli(expiresAt);
        return WeaponService.Outcome.ok(
                "issued " + permitClass.label() + " to " + WeaponServiceSerials.ownerName(data, holderUuid)
                        + " (" + expiry + ").",
                PermitRecord.key(holderUuid, permitClass));
    }

    /** Revokes a permit with a recorded reason. */
    public static WeaponService.Outcome revokePermit(
            final GovSavedData data, final WeaponService.Actor actor, final UUID holderUuid,
            final String holderName, final String rawClass, final String rawReason, final long now) {
        if (data == null || actor == null || holderUuid == null) {
            return WeaponService.Outcome.refused(ErrorCode.INVALID_STATE, "No permit to revoke.");
        }
        if (!actor.can(GovCapability.WEAPON_REGISTER)) {
            return WeaponService.Outcome.refused(
                    ErrorCode.FORBIDDEN,
                    "You need " + GovCapability.WEAPON_REGISTER.id() + " to revoke a permit.");
        }
        final PermitClass permitClass = PermitClass.parse(rawClass);
        if (permitClass == null) {
            return WeaponService.Outcome.refused(
                    ErrorCode.BAD_PAYLOAD,
                    "Unknown permit class. Use one of: " + PermitClass.ids() + ".");
        }
        final String reason = Sanitizer.text(rawReason);
        if (reason.isBlank()) {
            return WeaponService.Outcome.refused(ErrorCode.BAD_PAYLOAD, "A revocation needs a recorded reason.");
        }
        final PermitRecord permit = data.weaponPermit(holderUuid, permitClass);
        if (permit == null) {
            return WeaponService.Outcome.refused(
                    ErrorCode.BAD_PERMIT,
                    noSuchPermitMessage(data, holderUuid, holderName, permitClass));
        }
        if (permit.stateAt(now) == PermitStatus.REVOKED) {
            return WeaponService.Outcome.refused(
                    ErrorCode.INVALID_STATE,
                    "The " + permitClass.label() + " of " + WeaponServiceSerials.ownerName(data, holderUuid)
                            + " is already revoked.");
        }
        data.putWeaponPermit(permit.revoked(actor.uuid(), reason, now));
        return WeaponService.Outcome.ok(
                "revoked " + permitClass.label() + " of " + WeaponServiceSerials.ownerName(data, holderUuid)
                        + ": " + reason, permit.id());
    }

    /** Suspends a permit for a stated number of minutes with a recorded reason. */
    public static WeaponService.Outcome suspendPermit(
            final GovSavedData data, final WeaponService.Actor actor, final UUID holderUuid,
            final String holderName, final String rawClass, final String rawMinutes,
            final String rawReason, final long now) {
        if (data == null || actor == null || holderUuid == null) {
            return WeaponService.Outcome.refused(ErrorCode.INVALID_STATE, "No permit to suspend.");
        }
        if (!actor.can(GovCapability.WEAPON_REGISTER)) {
            return WeaponService.Outcome.refused(
                    ErrorCode.FORBIDDEN,
                    "You need " + GovCapability.WEAPON_REGISTER.id() + " to suspend a permit.");
        }
        final PermitClass permitClass = PermitClass.parse(rawClass);
        if (permitClass == null) {
            return WeaponService.Outcome.refused(
                    ErrorCode.BAD_PAYLOAD,
                    "Unknown permit class. Use one of: " + PermitClass.ids() + ".");
        }
        final long minutes = WeaponServiceHelpers.parseLong(rawMinutes, -1L);
        if (minutes <= 0L || minutes > WeaponService.MAX_SUSPENSION_MINUTES) {
            return WeaponService.Outcome.refused(
                    ErrorCode.BAD_PAYLOAD,
                    "The suspension must be 1-" + WeaponService.MAX_SUSPENSION_MINUTES + " minutes.");
        }
        final String reason = Sanitizer.text(rawReason);
        if (reason.isBlank()) {
            return WeaponService.Outcome.refused(ErrorCode.BAD_PAYLOAD, "A suspension needs a recorded reason.");
        }
        final PermitRecord permit = data.weaponPermit(holderUuid, permitClass);
        if (permit == null) {
            return WeaponService.Outcome.refused(
                    ErrorCode.BAD_PERMIT,
                    noSuchPermitMessage(data, holderUuid, holderName, permitClass));
        }
        if (permit.stateAt(now) == PermitStatus.REVOKED) {
            return WeaponService.Outcome.refused(
                    ErrorCode.INVALID_STATE, "A revoked permit cannot be suspended.");
        }
        data.putWeaponPermit(permit.suspended(
                actor.uuid(), reason, now, now + minutes * 60_000L));
        return WeaponService.Outcome.ok(
                "suspended " + permitClass.label() + " of " + WeaponServiceSerials.ownerName(data, holderUuid)
                        + " for " + minutes + " minutes: " + reason, permit.id());
    }

    /** The one wording for a permit nothing resolves to. */
    public static String noSuchPermitMessage(
            final GovSavedData data, final UUID holderUuid, final String holderName,
            final PermitClass permitClass) {
        final String who = holderName == null || holderName.isBlank()
                ? WeaponServiceSerials.ownerName(data, holderUuid) : Sanitizer.name(holderName);
        return "There is no " + permitClass.label() + " for "
                + (who.isBlank() ? "that person" : who) + ".";
    }

    /** The valid permit for a holder and class, or {@code null}. */
    public static PermitRecord activePermit(
            final GovSavedData data, final UUID holderUuid, final PermitClass permitClass,
            final long now) {
        if (data == null || holderUuid == null || permitClass == null) {
            return null;
        }
        final PermitRecord permit = data.weaponPermit(holderUuid, permitClass);
        return permit != null && permit.validAt(now) ? permit : null;
    }

    /** The valid Blasting permit for a holder, or {@code null}. */
    public static PermitRecord blastingPermit(
            final GovSavedData data, final UUID holderUuid, final long now) {
        return activePermit(data, holderUuid, PermitClass.BLASTING, now);
    }

    /** True when the subject holds the Class G military-duty authority: capability, permit, duty. */
    public static boolean militaryAuthority(
            final GovSavedData data, final WeaponService.Subject subject, final long now) {
        return subject != null
                && subject.can(GovCapability.MILITARY_AUTHORITY)
                && subject.onDuty()
                && activePermit(data, subject.uuid(), PermitClass.G, now) != null;
    }

    /** True when the subject is an on-duty Class G permit holder. */
    public static boolean onDutyClassG(
            final GovSavedData data, final WeaponService.Subject subject, final long now) {
        return subject != null
                && subject.onDuty()
                && activePermit(data, subject.uuid(), PermitClass.G, now) != null;
    }

}
