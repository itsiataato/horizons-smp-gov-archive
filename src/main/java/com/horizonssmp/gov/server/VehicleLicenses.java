package com.horizonssmp.gov.server;

import java.util.Locale;
import java.util.UUID;

import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.GovCapability;
import com.horizonssmp.gov.common.LicenseKeys;
import com.horizonssmp.gov.common.enums.LicenseClass;
import com.horizonssmp.gov.common.enums.LicenseStatus;
import com.horizonssmp.gov.common.records.LicenseRecord;

/**
 * Licence issuance, revocation, points and prechecks.
 */
public final class VehicleLicenses {

    private VehicleLicenses() {
    }

    public static VehicleService.Outcome issueLicense(
            final GovSavedData data,
            final VehicleService.Actor actor,
            final UUID targetUuid,
            final String targetName,
            final String rawClass,
            final long now) {
        if (data == null || actor == null || targetUuid == null) {
            return VehicleService.Outcome.refused(ErrorCode.INVALID_STATE, "No person to license.");
        }
        if (!actor.capabilities().contains(GovCapability.VEHICLE_REGISTER)) {
            return VehicleService.Outcome.refused(
                    ErrorCode.FORBIDDEN,
                    "You need " + GovCapability.VEHICLE_REGISTER.id() + " to issue a licence.");
        }
        final LicenseClass licenseClass = LicenseClass.parse(rawClass);
        if (licenseClass == null) {
            return VehicleService.Outcome.refused(
                    ErrorCode.BAD_PAYLOAD,
                    "Unknown licence class. Use one of: " + LicenseClass.ids() + ".");
        }
        final LicenseRecord existing = data.license(targetUuid, licenseClass);
        if (existing != null) {
            final LicenseStatus state = existing.stateAt(now);
            if (state == LicenseStatus.VALID || state == LicenseStatus.SUSPENDED) {
                return VehicleService.Outcome.refused(
                        ErrorCode.INVALID_STATE,
                        VehicleLookups.ownerName(data, targetUuid) + " already holds a " + licenseClass.displayName()
                                + " licence (" + state.name().toLowerCase(Locale.ROOT) + ").");
            }
        }
        data.playerOrCreate(targetUuid, Sanitizer.name(targetName), now);
        final LicenseRecord issued = LicenseRecord.issued(targetUuid, licenseClass, actor.uuid(), now);
        data.putLicense(issued);
        return VehicleService.Outcome.ok(
                "issued " + licenseClass.displayName() + " licence (" + licenseClass.code() + ") to "
                        + VehicleLookups.ownerName(data, targetUuid) + ".", "");
    }

    public static VehicleService.Outcome revokeLicense(
            final GovSavedData data,
            final VehicleService.Actor actor,
            final UUID targetUuid,
            final String targetName,
            final String rawClass,
            final String rawReason,
            final long now) {
        if (data == null || actor == null || targetUuid == null) {
            return VehicleService.Outcome.refused(ErrorCode.INVALID_STATE, "No licence to revoke.");
        }
        if (!actor.capabilities().contains(GovCapability.VEHICLE_REGISTER)) {
            return VehicleService.Outcome.refused(
                    ErrorCode.FORBIDDEN,
                    "You need " + GovCapability.VEHICLE_REGISTER.id() + " to revoke a licence.");
        }
        final LicenseClass licenseClass = LicenseClass.parse(rawClass);
        if (licenseClass == null) {
            return VehicleService.Outcome.refused(
                    ErrorCode.BAD_PAYLOAD,
                    "Unknown licence class. Use one of: " + LicenseClass.ids() + ".");
        }
        final String reason = Sanitizer.text(rawReason);
        if (reason.isBlank()) {
            return VehicleService.Outcome.refused(
                    ErrorCode.BAD_PAYLOAD,
                    "A revocation needs a recorded reason of at most " + Sanitizer.MAX_TEXT
                            + " characters.");
        }
        final LicenseRecord license = data.license(targetUuid, licenseClass);
        if (license == null) {
            return VehicleService.Outcome.refused(
                    ErrorCode.BAD_LICENSE,
                    noSuchLicenseMessage(data, targetUuid, targetName, licenseClass));
        }
        if (license.stateAt(now) == LicenseStatus.REVOKED) {
            return VehicleService.Outcome.refused(
                    ErrorCode.INVALID_STATE,
                    "The " + licenseClass.displayName() + " licence of "
                            + VehicleLookups.ownerName(data, targetUuid) + " is already revoked.");
        }
        data.putLicense(license.revoked(actor.uuid(), reason, now));
        return VehicleService.Outcome.ok(
                "revoked " + licenseClass.displayName() + " licence of "
                        + VehicleLookups.ownerName(data, targetUuid) + ": " + reason, "");
    }

    public static VehicleService.Outcome assessPoints(
            final GovSavedData data,
            final VehicleService.Actor actor,
            final UUID targetUuid,
            final String targetName,
            final String rawClass,
            final String rawPoints,
            final String rawReason,
            final long now) {
        if (data == null || actor == null || targetUuid == null) {
            return VehicleService.Outcome.refused(ErrorCode.INVALID_STATE, "No licence to assess points on.");
        }
        if (!actor.capabilities().contains(GovCapability.VEHICLE_REGISTER)) {
            return VehicleService.Outcome.refused(
                    ErrorCode.FORBIDDEN,
                    "You need " + GovCapability.VEHICLE_REGISTER.id() + " to assess licence points.");
        }
        final LicenseClass licenseClass = LicenseClass.parse(rawClass);
        if (licenseClass == null) {
            return VehicleService.Outcome.refused(
                    ErrorCode.BAD_PAYLOAD,
                    "Unknown licence class. Use one of: " + LicenseClass.ids() + ".");
        }
        final int points = VehicleLookups.parsePoints(rawPoints);
        if (!LicenseKeys.allowedPoints(points)) {
            return VehicleService.Outcome.refused(
                    ErrorCode.BAD_PAYLOAD,
                    "Points must be 2 (minor), 4 (reckless) or 6 (impaired).");
        }
        final String reason = Sanitizer.text(rawReason);
        if (reason.isBlank()) {
            return VehicleService.Outcome.refused(
                    ErrorCode.BAD_PAYLOAD,
                    "Assessing points needs a recorded reason of at most " + Sanitizer.MAX_TEXT
                            + " characters.");
        }
        final LicenseRecord license = data.license(targetUuid, licenseClass);
        if (license == null) {
            return VehicleService.Outcome.refused(
                    ErrorCode.BAD_LICENSE,
                    noSuchLicenseMessage(data, targetUuid, targetName, licenseClass));
        }
        if (license.stateAt(now) == LicenseStatus.REVOKED) {
            return VehicleService.Outcome.refused(
                    ErrorCode.INVALID_STATE,
                    "The " + licenseClass.displayName() + " licence of "
                            + VehicleLookups.ownerName(data, targetUuid) + " is revoked; issue a new one first.");
        }

        LicenseRecord updated = license.withPoint(
                new LicenseRecord.PointEntry(points, now, actor.uuid(), reason));
        final int active = updated.activePoints(now);
        boolean suspendedNow = false;
        if (active >= LicenseKeys.SUSPENSION_THRESHOLD) {
            final long until = Math.max(updated.suspendedUntil(), now + VehicleService.SUSPENSION_MS);
            updated = updated.suspended(actor.uuid(), VehicleService.AUTO_SUSPENSION_REASON, now, until);
            suspendedNow = true;
        }
        data.putLicense(updated);
        final StringBuilder message = new StringBuilder("assessed ").append(points)
                .append(" points on the ").append(licenseClass.displayName())
                .append(" licence of ").append(VehicleLookups.ownerName(data, targetUuid))
                .append(" (").append(active).append("/")
                .append(LicenseKeys.SUSPENSION_THRESHOLD).append(" in 30 in-game days): ")
                .append(reason);
        if (suspendedNow) {
            message.append(" Suspended until ").append(java.time.Instant.ofEpochMilli(updated.suspendedUntil()))
                    .append(" (7 in-game days).");
        }
        return VehicleService.Outcome.ok(message.toString(), "");
    }

    public static VehicleService.Outcome licensePrecheck(
            final GovSavedData data, final UUID targetUuid, final String rawClass) {
        final LicenseClass licenseClass = LicenseClass.parse(rawClass);
        if (licenseClass == null) {
            return VehicleService.Outcome.refused(
                    ErrorCode.BAD_PAYLOAD,
                    "Unknown licence class. Use one of: " + LicenseClass.ids() + ".");
        }
        if (data != null && targetUuid != null && data.license(targetUuid, licenseClass) == null) {
            return VehicleService.Outcome.refused(
                    ErrorCode.BAD_LICENSE, noSuchLicenseMessage(data, targetUuid, "", licenseClass));
        }
        return null;
    }

    public static String noSuchLicenseMessage(
            final GovSavedData data, final UUID targetUuid, final String targetName,
            final LicenseClass licenseClass) {
        final String who = targetName == null || targetName.isBlank()
                ? VehicleLookups.ownerName(data, targetUuid) : Sanitizer.name(targetName);
        return "There is no " + licenseClass.displayName() + " licence for "
                + (who.isBlank() ? "that person" : who) + ".";
    }
}