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


final class WeaponServiceAreas {

    // --------------------------------------------------------------- area permits

    /** The canonical dimension id, or {@code null}. */
    public static String canonicalDimension(final String raw) {
        if (raw == null) {
            return null;
        }
        final String value = raw.trim().toLowerCase(Locale.ROOT);
        return WeaponService.DIMENSION.matcher(value).matches() ? value : null;
    }

    /**
     * Issues an explosion-area permit. Overlap is allowed: the detonation check asks whether any
     * active permit covers the point, not whether one is exclusive.
     */
    public static WeaponService.Outcome createArea(
            final GovSavedData data, final WeaponService.Actor actor, final String rawDimension,
            final String rawX, final String rawY, final String rawZ, final String rawRadius,
            final String rawMinutes, final String rawReason, final long now) {
        if (data == null || actor == null) {
            return WeaponService.Outcome.refused(ErrorCode.INVALID_STATE, "No area permit to create.");
        }
        if (!actor.can(GovCapability.WEAPON_REGISTER)) {
            return WeaponService.Outcome.refused(
                    ErrorCode.FORBIDDEN,
                    "You need " + GovCapability.WEAPON_REGISTER.id()
                            + " to issue an explosion-area permit.");
        }
        final String dimension = canonicalDimension(rawDimension);
        if (dimension == null) {
            return WeaponService.Outcome.refused(
                    ErrorCode.BAD_PAYLOAD,
                    "Dimension must be a resource id such as minecraft:overworld.");
        }
        final int x = WeaponServiceHelpers.parseInt(rawX, Integer.MIN_VALUE);
        final int y = WeaponServiceHelpers.parseInt(rawY, Integer.MIN_VALUE);
        final int z = WeaponServiceHelpers.parseInt(rawZ, Integer.MIN_VALUE);
        if (x == Integer.MIN_VALUE || y == Integer.MIN_VALUE || z == Integer.MIN_VALUE
                || Math.abs((long) x) > 30_000_000L || Math.abs((long) z) > 30_000_000L
                || y < -2048 || y > 2048) {
            return WeaponService.Outcome.refused(
                    ErrorCode.BAD_PAYLOAD, "The area center is outside the world bounds.");
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
        final String reason = Sanitizer.text(rawReason);
        if (reason.isBlank()) {
            return WeaponService.Outcome.refused(
                    ErrorCode.BAD_PAYLOAD, "An explosion-area permit needs a recorded reason.");
        }
        final String id = WeaponServiceHelpers.newAreaId(data);
        data.putAreaPermit(AreaPermitRecord.create(
                id, dimension, x, y, z, radius, now, now + minutes * 60_000L,
                actor.uuid(), now, reason));
        return WeaponService.Outcome.ok(
                "issued explosion-area permit " + id + " at " + dimension + " " + x + " " + y + " "
                        + z + " radius " + radius + " for " + minutes + " minutes.",
                id);
    }

    /** Revokes an explosion-area permit with a recorded reason. */
    public static WeaponService.Outcome revokeArea(
            final GovSavedData data, final WeaponService.Actor actor, final String rawId,
            final String rawReason, final long now) {
        if (data == null || actor == null) {
            return WeaponService.Outcome.refused(ErrorCode.INVALID_STATE, "No area permit to revoke.");
        }
        if (!actor.can(GovCapability.WEAPON_REGISTER)) {
            return WeaponService.Outcome.refused(
                    ErrorCode.FORBIDDEN,
                    "You need " + GovCapability.WEAPON_REGISTER.id()
                            + " to revoke an explosion-area permit.");
        }
        final AreaPermitRecord permit = data.areaPermit(Sanitizer.id(rawId));
        if (permit == null) {
            return WeaponService.Outcome.refused(
                    ErrorCode.BAD_AREA_PERMIT, noSuchAreaMessage(rawId));
        }
        final String reason = Sanitizer.text(rawReason);
        if (reason.isBlank()) {
            return WeaponService.Outcome.refused(ErrorCode.BAD_PAYLOAD, "A revocation needs a recorded reason.");
        }
        if (AreaPermitRecord.STATUS_REVOKED.equals(permit.status())) {
            return WeaponService.Outcome.refused(
                    ErrorCode.INVALID_STATE, "Permit " + permit.id() + " is already revoked.");
        }
        data.putAreaPermit(permit.revoked(actor.uuid(), reason, now));
        return WeaponService.Outcome.ok("revoked explosion-area permit " + permit.id() + ": " + reason,
                permit.id());
    }

    /** The one wording for an area permit that does not exist. */
    public static String noSuchAreaMessage(final String raw) {
        return "There is no explosion-area permit with id '"
                + Sanitizer.singleLine(raw, 32) + "'.";
    }

    /** The first active area permit covering a point, or {@code null}. */
    public static AreaPermitRecord activeAreaAt(
            final GovSavedData data, final String dimension,
            final double x, final double y, final double z, final long now) {
        if (data == null) {
            return null;
        }
        for (final AreaPermitRecord permit : data.areaPermits().values()) {
            if (permit.covers(dimension, x, y, z, now)) {
                return permit;
            }
        }
        return null;
    }
}
