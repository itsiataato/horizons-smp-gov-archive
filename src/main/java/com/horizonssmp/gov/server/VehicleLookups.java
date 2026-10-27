package com.horizonssmp.gov.server;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.horizonssmp.gov.common.LicenseKeys;
import com.horizonssmp.gov.common.records.LicenseRecord;
import com.horizonssmp.gov.common.records.PlayerRecord;
import com.horizonssmp.gov.common.enums.LicenseClass;
import com.horizonssmp.gov.common.enums.LicenseStatus;

/**
 * Licence and owner lookups for vehicles.
 */
public final class VehicleLookups {

    private VehicleLookups() {
    }

    public static String ownerName(final GovSavedData data, final UUID ownerUuid) {
        if (ownerUuid == null) {
            return "";
        }
        final PlayerRecord player = data == null ? null : data.player(ownerUuid);
        if (player != null && !player.lastName().isBlank()) {
            return player.lastName();
        }
        return ownerUuid.toString();
    }

    public static LicenseRecord licenseFor(
            final GovSavedData data, final UUID ownerUuid, final LicenseClass requested, final long now) {
        if (data == null || ownerUuid == null || requested == null) {
            return null;
        }
        final LicenseRecord direct = data.license(ownerUuid, requested);
        if (direct != null && direct.authorizesAt(now, requested)) {
            return direct;
        }
        for (final LicenseRecord candidate : data.licensesFor(ownerUuid)) {
            if (candidate.authorizesAt(now, requested)) {
                return candidate;
            }
        }
        return direct;
    }

    public static String licenseState(
            final GovSavedData data, final UUID ownerUuid, final LicenseClass licenseClass, final long now) {
        final LicenseRecord license = licenseFor(data, ownerUuid, licenseClass, now);
        return license == null ? LicenseKeys.STATE_NONE : license.stateAt(now).name();
    }

    public static int licensePoints(
            final GovSavedData data, final UUID ownerUuid, final LicenseClass licenseClass, final long now) {
        final LicenseRecord license = licenseFor(data, ownerUuid, licenseClass, now);
        return license == null ? 0 : license.activePoints(now);
    }

    public static String summary(final GovSavedData data, final UUID playerUuid, final long now) {
        if (data == null || playerUuid == null) {
            return "none";
        }
        final List<LicenseRecord> licenses = new ArrayList<>(data.licensesFor(playerUuid));
        licenses.sort(Comparator.comparingInt(record -> record.licenseClass().ordinal()));
        if (licenses.isEmpty()) {
            return "none";
        }
        final StringBuilder out = new StringBuilder();
        for (final LicenseRecord license : licenses) {
            if (out.length() > 0) {
                out.append("; ");
            }
            final LicenseStatus state = license.stateAt(now);
            out.append(license.licenseClass().displayName())
                    .append(" (").append(license.licenseClass().code()).append("): ")
                    .append(state.name()).append(' ').append(license.activePoints(now)).append(" pts");
            if (state == LicenseStatus.SUSPENDED) {
                out.append(" until ").append(java.time.Instant.ofEpochMilli(license.suspendedUntil()));
            }
            if (state == LicenseStatus.REVOKED && !license.revokeReason().isBlank()) {
                out.append(" (").append(Sanitizer.singleLine(license.revokeReason(), 48)).append(')');
            }
        }
        return out.toString();
    }

    public static int parsePoints(final String raw) {
        try {
            return Integer.parseInt(raw == null ? "" : raw.trim());
        } catch (final NumberFormatException e) {
            return -1;
        }
    }

    // PlayerRecord import
}