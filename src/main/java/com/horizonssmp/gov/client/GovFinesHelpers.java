package com.horizonssmp.gov.client;

import com.horizonssmp.gov.client.GovFinesTypes.*;
import static com.horizonssmp.gov.client.GovFinesTypes.*;

import java.time.Instant;

import com.horizonssmp.gov.common.FineKeys;

final class GovFinesHelpers {

    // ------------------------------------------------------------------ helpers

    /** A whole-number field failed to parse; the request is not sent. */
    static void numberError(final GovFinesPanel p) {
        final String text = GovFinesLabels.tr("gui.gov.fines.number_invalid");
        GovFinesSupport.setStatus(p, text.startsWith("gui.gov.") ? "Enter a whole number." : text, true);
        GovFinesViews.rebuildStatus(p);
    }

    /** Parses an unsigned whole number; returns null when the text is unusable. */
    static Long parseWhole(final String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    static String articleText(final GovFinesPanel p) {
        final String id = p.detail.getOrDefault(FineKeys.ARTICLE, "");
        final String title = p.detail.getOrDefault(FineKeys.ARTICLE_TITLE, "");
        return title.isBlank() ? dash(id) : id + " - " + title;
    }

    static String restitutionText(final GovFinesPanel p) {
        final long restitution = GovFinesResponses.longField(p.detail, FineKeys.RESTITUTION);
        if (restitution <= 0L) {
            return GovFinesLabels.label(p, "gui.gov.fines.none");
        }
        final String victim = p.detail.getOrDefault(FineKeys.VICTIM, "");
        return GovFinesLabels.up(GovFinesLabels.trArgs("gui.gov.fines.restitution_line", restitution,
                victim.isBlank() ? GovFinesLabels.label(p, "gui.gov.fines.unrecorded") : victim));
    }

    static String collectorText(final GovFinesPanel p) {
        final String collector = p.detail.getOrDefault(FineKeys.COLLECTOR, "");
        final boolean self = "true".equals(p.detail.getOrDefault(FineKeys.SELF_PAID, "false"));
        if (collector.isBlank()) {
            return "-";
        }
        return self ? collector + " (" + GovFinesLabels.up(GovFinesLabels.label(p, "gui.gov.fines.self")) + ")" : collector;
    }

    static String gold(final String raw) {
        return GovFinesLabels.up(GovFinesLabels.trArgs("gui.gov.fines.gold_ingots",
                raw == null || raw.isBlank() ? "0" : raw));
    }

    static long bandGold(final String band) {
        // Article 9.11 statutory amounts. Display only; the server derives the stored amount.
        if ("MISDEMEANOR".equals(band)) {
            return 16L;
        }
        if ("FELONY".equals(band)) {
            return 32L;
        }
        if ("AGGRAVATED".equals(band)) {
            return 64L;
        }
        return 8L;
    }

    static String date(final long epochMillis) {
        return epochMillis <= 0L ? "-" : DATE.format(Instant.ofEpochMilli(epochMillis));
    }

    static String age(final long epochMillis, final long now) {
        if (epochMillis <= 0L) {
            return "-";
        }
        final long days = Math.max(0L, (now - epochMillis) / 86_400_000L);
        return days == 0L ? GovFinesLabels.up(GovFinesLabels.tr("gui.gov.fines.age.today"))
                : GovFinesLabels.up(GovFinesLabels.trArgs("gui.gov.fines.age.days", days));
    }

    static String dash(final String value) {
        return com.horizonssmp.gov.common.GovFields.dash(value);
    }
}
