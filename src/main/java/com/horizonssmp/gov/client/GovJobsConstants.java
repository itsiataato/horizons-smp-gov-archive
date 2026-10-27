package com.horizonssmp.gov.client;

import java.util.Locale;
import java.util.Map;

import com.horizonssmp.gov.network.GovActionResultPayload;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import com.horizonssmp.gov.client.GovRect;

final class GovJobsConstants {

    private GovJobsConstants() {
    }

    static final GovRect EMPTY = new GovRect(0, 0, 0, 0);
    static final String CLOSE_LETTER = "X";
    /** Matches GovNotesPanel: a retry is a small secondary button at the foot of its viewport. */
    static final int RETRY_W = 52;
    static final int RETRY_H = 14;
    static final int ROW_BUTTON_W = 52;
    static final int ROW_BUTTON_H = 14;
    static final int CLOSE_W = 11;
    static final int CLOSE_H = 9;

    static final int MODE_ROSTER = 0;
    static final int MODE_MEMBERS = 1;
    static final int MODE_CAPABILITIES = 2;

    static final long ARM_FLOOR_MS = 600L;
    /** Assigning, like removal, follows the single 5 s armed window from the GUI contract. */
    static final long ARM_MS = 5000L;
    static final int MODAL_GUARD_MS = 250;
    static final int JOB_PAGE_SIZE = 8;

    static String tr(final String key, final Object... args) {
        return Component.translatable(key, args).getString();
    }

    static String upper(final String text) {
        return text == null ? "" : text.toUpperCase(Locale.ROOT);
    }

    static String fallbackError(final GovActionResultPayload payload, final String key) {
        return payload.errorText().isBlank()
                ? Component.translatable(key).getString()
                : payload.errorText();
    }

    static int intField(final Map<String, String> fields, final String key) {
        return Math.max(0, com.horizonssmp.gov.common.GovFields.intField(fields, key));
    }

    static GovRect retryRect(final GovRect area) {
        if (area.w() <= 0 || area.h() <= 0) {
            return EMPTY;
        }
        final int width = Math.min(RETRY_W, area.w());
        final int height = Math.min(RETRY_H, area.h());
        return new GovRect(area.x() + (area.w() - width) / 2, area.bottom() - height,
                width, height);
    }

    static GovRect rowButtonRect(final GovRect container, final int x, final int top) {
        if (container.w() <= 0 || container.h() <= 0
                || x < container.x() || x + ROW_BUTTON_W > container.right()
                || top < container.y() || top + ROW_BUTTON_H > container.bottom()) {
            return EMPTY;
        }
        return new GovRect(x, top, ROW_BUTTON_W, ROW_BUTTON_H);
    }

    static boolean clipRows(final GuiGraphics graphics, final GovRect rows) {
        if (rows.w() <= 2 || rows.h() <= 2) {
            return false;
        }
        graphics.enableScissor(rows.x() + 1, rows.y() + 1, rows.right() - 1,
                rows.bottom() - 1);
        return true;
    }

    static String dash(final String value) {
        return com.horizonssmp.gov.common.GovFields.dash(value);
    }
}
