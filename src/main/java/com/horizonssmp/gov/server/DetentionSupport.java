package com.horizonssmp.gov.server;

import java.util.UUID;

import com.horizonssmp.gov.Gov;
import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.enums.HistoryKind;
import com.horizonssmp.gov.common.enums.LegalBasis;
import com.horizonssmp.gov.common.records.DetentionRecord;
import com.horizonssmp.gov.common.records.JailRecord;
import com.horizonssmp.gov.common.records.LocationRecord;
import com.horizonssmp.gov.common.records.PlayerRecord;

/**
 * Shared helpers for detention requests, timer and views.
 */
public final class DetentionSupport {

    private DetentionSupport() {
    }

    public static LegalBasis parseBasis(final String raw) {
        if (raw == null) {
            return null;
        }
        return switch (raw.trim().toLowerCase(java.util.Locale.ROOT).replace('-', '_')) {
            case "consent" -> LegalBasis.CONSENT;
            case "violent", "violent_offense" -> LegalBasis.VIOLENT_OFFENSE;
            case "warrant" -> LegalBasis.WARRANT;
            default -> null;
        };
    }

    public static String basisId(final LegalBasis basis) {
        return switch (basis == null ? LegalBasis.CONSENT : basis) {
            case CONSENT -> "consent";
            case VIOLENT_OFFENSE -> "violent";
            case WARRANT -> "warrant";
        };
    }

    public static String basisLabel(final LegalBasis basis) {
        return switch (basis == null ? LegalBasis.CONSENT : basis) {
            case CONSENT -> "consent";
            case VIOLENT_OFFENSE -> "a violent offence";
            case WARRANT -> "a warrant";
        };
    }

    public static boolean canExtend(final DetentionRecord record, final DetentionService.Policy policy) {
        return record != null && record.active()
                && record.legalBasis() == LegalBasis.WARRANT
                && !record.warrantRef().isBlank()
                && policy != null
                && (long) record.maxDurationMinutes() < policy.warrantMinutes();
    }

    public static String remainingText(final long remainingMs) {
        if (remainingMs <= 0L) {
            return "release due";
        }
        final long minutes = Math.max(1L, remainingMs / 60_000L);
        if (minutes < 60L) {
            return minutes + " min left";
        }
        final long hours = minutes / 60L;
        final long rest = minutes % 60L;
        return hours + " h" + (rest == 0L ? "" : " " + rest + " min") + " left";
    }

    public static String formatCell(final LocationRecord cell) {
        if (cell == null || !cell.isSet()) {
            return "";
        }
        return cell.dimension() + " " + cell.x() + " " + cell.y() + " " + cell.z();
    }

    public static LocationRecord locationOf(final JailGateway gateway, final UUID player) {
        if (gateway == null) {
            return LocationRecord.NONE;
        }
        final JailGateway.Position position = gateway.position(player);
        if (!position.set()) {
            return LocationRecord.NONE;
        }
        return new LocationRecord(
                position.dimension(),
                (int) Math.floor(position.x()),
                (int) Math.floor(position.y()),
                (int) Math.floor(position.z()));
    }

    public static int boundedMinutes(final int requested, final DetentionService.Policy policy) {
        if (requested == Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        if (requested < 0) {
            return policy.noWarrantMinutes();
        }
        if (requested == 0) {
            return -1;
        }
        return requested <= policy.noWarrantMinutes() ? requested : -1;
    }

    public static DetentionService.Outcome tooLong(final DetentionService.Policy policy) {
        return DetentionService.Outcome.refused(
                ErrorCode.BAD_PAYLOAD,
                "Without a warrant a detention may not exceed " + policy.noWarrantMinutes()
                        + " real minutes (Article " + DetentionService.ARTICLE_DETENTION + ").");
    }

    public static int parseMinutes(final String raw) {
        if (raw == null || raw.isBlank()) {
            return -1;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (final NumberFormatException e) {
            return Integer.MIN_VALUE;
        }
    }

    public static boolean confirmed(final String raw) {
        final String value = raw == null ? "" : raw.trim();
        return "true".equalsIgnoreCase(value)
                || "yes".equalsIgnoreCase(value)
                || "1".equals(value);
    }

    public static String newDetentionId(final GovSavedData data) {
        for (int attempt = 0; attempt < 8; attempt++) {
            final String id = DetentionRecord.ID_PREFIX
                    + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            if (data.detention(id) == null) {
                return id;
            }
        }
        String fallback = DetentionRecord.ID_PREFIX + Long.toHexString(System.nanoTime());
        for (int attempt = 0; attempt < 16; attempt++) {
            if (data.detention(fallback) == null) {
                return fallback;
            }
            fallback += "x";
        }
        throw new IllegalStateException("detention id space exhausted");
    }

    public static JailAdapter adapterForRecord(
            final DetentionService.Policy policy, final DetentionRecord record) {
        if (policy == null || record == null) {
            return JailAdapter.NONE;
        }
        if (DetentionRecord.ADAPTER_PLUGIN.equals(record.jailAdapter())
                && !policy.pluginCommand().isBlank()) {
            return new JailAdapter.PluginAdapter(policy.pluginCommand());
        }
        if (DetentionRecord.ADAPTER_NATIVE.equals(record.jailAdapter())
                && record.cell().isSet() && policy.nativeFallback()) {
            return new JailAdapter.NativeAdapter(policy.leashRadius());
        }
        return JailAdapter.NONE;
    }

    public static void cleanLiveState(
            final JailGateway gateway, final DetentionRecord record) {
        if (gateway == null || record == null) {
            return;
        }
        gateway.applyCuffs(record.targetUuid(), false);
        gateway.applyConfinement(record.targetUuid(), false);
    }

    public static void notifyOfficer(
            final JailGateway gateway, final DetentionRecord record, final String message) {
        if (gateway != null && record.officerUuid() != null) {
            gateway.message(record.officerUuid(), message);
        }
    }

    public static void syncPlayerSummary(
            final GovSavedData data, final DetentionRecord record, final long now) {
        if (data == null || record == null || record.targetUuid() == null) {
            return;
        }
        final PlayerRecord player = data.playerOrCreate(
                record.targetUuid(), record.targetName(), now);
        final String cell = record.cell().isSet() ? formatCell(record.cell()) : "";
        final JailRecord jail = new JailRecord(
                record.active(),
                cell,
                record.startedAt(),
                record.expiresAt(),
                record.legalBasis(),
                record.reason(),
                record.officerUuid(),
                record.releasedAt(),
                record.releasedBy());
        data.putPlayer(player.withJail(jail));
    }
}
