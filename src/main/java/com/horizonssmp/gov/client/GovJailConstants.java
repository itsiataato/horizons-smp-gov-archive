package com.horizonssmp.gov.client;

import java.util.Locale;
import java.util.Map;

import com.horizonssmp.gov.network.GovActionResultPayload;
import com.horizonssmp.gov.common.JailKeys;

import net.minecraft.network.chat.Component;

final class GovJailConstants {

    private GovJailConstants() {
    }

    static final GovRect EMPTY = new GovRect(0, 0, 0, 0);
    static final int SUBTABS = 5;
    static final int ROW_PITCH = 20;
    static final int ROW_PITCH_COMPACT = 14;
    static final int DETAIL_PITCH = 11;
    static final int DETAIL_PITCH_COMPACT = 10;
    static final int FORM_ROW = 16;
    static final int FORM_GAP = 2;
    static final long ARM_MS = 5_000L;
    static final long ARM_FLOOR_MS = 600L;

    static String tr(final String key, final Object... args) {
        return Component.translatable(key, args).getString();
    }

    static String caps(final String text) {
        return text == null ? "" : text.toUpperCase(Locale.ROOT);
    }

    static String fallbackError(final GovActionResultPayload payload, final String key) {
        return payload.errorText().isBlank() ? tr(key) : payload.errorText();
    }

    static boolean strongStatus(final String text) {
        return text != null && (text.equals(caps(tr("gui.gov.jail.status.held")))
                || text.equals(caps(tr("gui.gov.jail.status.expired"))));
    }

    static long longField(final Map<String, String> fields, final String key) {
        return com.horizonssmp.gov.common.GovFields.longField(fields, key);
    }

    static boolean isPositive(final String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        try {
            return Long.parseLong(raw) > 0L;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    static int intField(final Map<String, String> fields, final String key,
            final int fallback) {
        return com.horizonssmp.gov.common.GovFields.intField(fields, key, fallback);
    }

    static String dash(final String value) {
        return com.horizonssmp.gov.common.GovFields.dash(value);
    }

    static String when(final long epochMillis) {
        return com.horizonssmp.gov.common.GovFields.when(epochMillis);
    }

    static String remaining(final long millis) {
        if (millis <= 0L) {
            return tr("gui.gov.jail.release_due");
        }
        final long minutes = Math.max(1L, millis / 60_000L);
        return tr("gui.gov.jail.remaining", minutes);
    }

    static String basisLabel(final String basis) {
        return switch (basis == null ? "" : basis.toLowerCase(Locale.ROOT)) {
            case "consent" -> tr("gui.gov.jail.basis.consent").toUpperCase(Locale.ROOT);
            case "violent" -> tr("gui.gov.jail.basis.violent_offense");
            case "warrant" -> tr("gui.gov.jail.basis.warrant").toUpperCase(Locale.ROOT);
            default -> basis == null ? "" : basis.toUpperCase(Locale.ROOT);
        };
    }

    static String statusText(final String raw) {
        return switch (raw == null ? "" : raw.toUpperCase(Locale.ROOT)) {
            case "ACTIVE", "HELD" -> caps(tr("gui.gov.jail.status.held"));
            case "RELEASED" -> caps(tr("gui.gov.jail.status.released"));
            case "EXPIRED" -> caps(tr("gui.gov.jail.status.expired"));
            default -> raw == null ? "" : raw.toUpperCase(Locale.ROOT);
        };
    }
}
