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


final class WeaponServiceSerials {

    // -------------------------------------------------------------------- serials

    /**
     * The one canonical serial form: uppercase, trimmed. Returns {@code null} for an invalid serial.
     *
     * <p>The comparison and the stored key are case-insensitive: {@code ab-1234} and {@code AB-1234}
     * are one weapon, and the stored form is the uppercase one. A serial that is not 3-32 letters,
     * digits, hyphens or underscores is refused before a record is touched.
     */
    public static String canonicalSerial(final String raw) {
        if (raw == null) {
            return null;
        }
        final String upper = raw.trim().toUpperCase(Locale.ROOT);
        return WeaponService.SERIAL.matcher(upper).matches() ? upper : null;
    }

    /** The one wording for an invalid serial. */
    public static String invalidSerialMessage(final String raw) {
        return "Serial '" + Sanitizer.singleLine(raw, 32)
                + "' is not valid: 3-32 letters, digits, hyphen or underscore.";
    }

    /** The weapon a serial names, case-insensitively, or {@code null}. */
    public static WeaponRecord bySerial(final GovSavedData data, final String raw) {
        final String serial = canonicalSerial(raw);
        if (data == null || serial == null) {
            return null;
        }
        for (final WeaponRecord weapon : data.weapons().values()) {
            if (serial.equalsIgnoreCase(weapon.serial())) {
                return weapon;
            }
        }
        return null;
    }

    /** The weapon a serial or stable id names, or {@code null}. */
    public static WeaponRecord find(final GovSavedData data, final String raw) {
        if (data == null || raw == null || raw.isBlank()) {
            return null;
        }
        final String trimmed = raw.trim();
        if (trimmed.regionMatches(true, 0, WeaponService.WEAPON_ID_PREFIX, 0, WeaponService.WEAPON_ID_PREFIX.length())) {
            final WeaponRecord byId = data.weapon(trimmed.toLowerCase(Locale.ROOT));
            if (byId != null) {
                return byId;
            }
        }
        return bySerial(data, trimmed);
    }

    /** The one wording for a serial that is not in the registry. */
    public static String noSuchWeaponMessage(final String raw) {
        final String serial = canonicalSerial(raw);
        return "There is no registered weapon with serial '"
                + Sanitizer.singleLine(serial == null ? raw : serial, 32) + "'.";
    }

    /** The owner's display name, falling back to the UUID text. */
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

}
