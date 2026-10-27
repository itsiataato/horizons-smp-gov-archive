package com.horizonssmp.gov.client;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import com.horizonssmp.gov.common.AuditKeys;

import net.minecraft.client.gui.components.EditBox;

final class GovAuditUtil {
    static String text(final EditBox box) {
        return box == null ? "" : box.getValue().strip();
    }

    static String value(final Map<String, String> fields, final String key) {
        return com.horizonssmp.gov.common.GovFields.value(fields, key);
    }

    static String dash(final String value) {
        return com.horizonssmp.gov.common.GovFields.dash(value);
    }

    static boolean isOk(final String outcome) {
        return "ACCEPTED".equalsIgnoreCase(outcome) || "OK".equalsIgnoreCase(outcome);
    }

    static boolean isDenied(final String outcome) {
        return "REFUSED".equalsIgnoreCase(outcome) || "DENIED".equalsIgnoreCase(outcome);
    }

    static int resultColor(final String outcome) {
        return isDenied(outcome) ? GovTheme.DANGER : GovTheme.POSITIVE;
    }

    static String resultText(final GovAuditPanel p, final String outcome) {
        return isOk(outcome) ? p.resultOkText
                : isDenied(outcome) ? p.resultDeniedText : "-";
    }

    static String shortTime(final long epochMillis) {
        if (epochMillis <= 0L) {
            return "-";
        }
        return GovAuditPanel.SHORT_TIME.format(Instant.ofEpochMilli(epochMillis));
    }

    static int intField(final Map<String, String> fields, final String key, final int fallback) {
        return com.horizonssmp.gov.common.GovFields.intField(fields, key, fallback);
    }

    static long longField(final Map<String, String> fields, final String key) {
        return com.horizonssmp.gov.common.GovFields.longField(fields, key);
    }
}