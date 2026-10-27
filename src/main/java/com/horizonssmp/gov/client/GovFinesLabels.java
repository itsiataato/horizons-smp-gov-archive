package com.horizonssmp.gov.client;

import com.horizonssmp.gov.client.GovFinesTypes.*;
import static com.horizonssmp.gov.client.GovFinesTypes.*;

import java.util.Locale;

import com.horizonssmp.gov.common.FineKeys;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

final class GovFinesLabels {
    // ------------------------------------------------------------------- labels

    static void rebuildLabels(final GovFinesPanel p) {
        if (!p.labels.isEmpty()) {
            return;
        }
        for (final String key : LABEL_KEYS) {
            p.labels.put(key, up(tr(key)));
        }
        for (final String state : STATES) {
            final String key = "gui.gov.fines.state." + state.toLowerCase(Locale.ROOT);
            p.stateNames.put(state, up(tr(key)));
        }
        for (final String band : BANDS) {
            final String key = "gui.gov.fines.band." + band.toLowerCase(Locale.ROOT);
            p.bandNames.put(band, up(tr(key)));
            p.bandShortNames.put(band, up(tr(key + ".short")));
        }
        p.columnHeaders[0] = GovText.boldCached(label(p, "gui.gov.fines.field.citizen"));
        p.columnHeaders[1] = GovText.boldCached(label(p, "gui.gov.fines.field.article"));
        p.columnHeaders[2] = GovText.boldCached(label(p, "gui.gov.fines.column.gold"));
        p.columnHeaders[3] = GovText.boldCached(label(p, "gui.gov.fines.field.state"));
        p.columnHeaders[4] = GovText.boldCached(label(p, "gui.gov.fines.column.age"));
    }

    static String label(final GovFinesPanel p, final String key) {
        final String value = p.labels.get(key);
        return value == null ? "" : value;
    }

    static String stateText(final GovFinesPanel p, final String state) {
        final String key = up(state);
        final String value = p.stateNames.get(key);
        return value == null ? key : value;
    }

    static String bandText(final GovFinesPanel p, final String band) {
        final String key = up(band);
        final String value = p.bandNames.get(key);
        return value == null ? key : value;
    }

    static String bandButtonLabel(final GovFinesPanel p, final String band, final int width) {
        final String full = bandText(p, band);
        if (p.font.width(full) + GovMetrics.GAP_XS * 2 <= width) {
            return full;
        }
        final String shortName = p.bandShortNames.get(up(band));
        return shortName == null || shortName.startsWith("gui.gov.") ? full : shortName;
    }

    static String tr(final String key) {
        return Component.translatable(key).getString();
    }

    static String trArgs(final String key, final Object... args) {
        return Component.translatable(key, args).getString();
    }

    static String up(final String value) {
        return value == null ? "" : value.toUpperCase(Locale.ROOT);
    }


}
