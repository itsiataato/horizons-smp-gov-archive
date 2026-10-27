package com.horizonssmp.gov.client;

import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.util.FormattedCharSequence;

final class GovPeopleCaches {
    static void rebuildAllCaches(final GovPeoplePanel p) {
        if (p.font == null || p.bounds.w() <= 0 || p.bounds.h() <= 0) {
            return;
        }
        rebuildSearchViews(p);
        rebuildPersonDetail(p);
        GovPeopleNotesCache.rebuildNoteViews(p);
        GovPeopleNotesCache.rebuildStatusTexts(p);
        GovPeopleHits.rebuildHits(p);
    }

    static void rebuildSearchViews(final GovPeoplePanel p) {
        p.personViews.clear();
        if (p.font == null || p.listRect.w() <= 0) {
            return;
        }
        final int textW = Math.max(1, p.listRect.w() - 12);
        for (final GovPeoplePanelModel.PersonRow person : p.people) {
            final String rank = person.rank().isBlank() ? "-" : person.rank();
            final String standing = person.standing().isBlank()
                    ? p.notAvailableText : person.standing();
            final String metaA = (person.online() ? p.onlineText : p.offlineText)
                    + "  " + (person.job().isBlank() ? "-" : person.job())
                    + (person.notesText().isBlank() ? "" : "  " + person.notesText());
            final String metaB = p.rankLabel + " " + rank
                    + "  " + p.standingLabel + " " + standing;
            final String line2;
            final String line3;
            if (p.searchNarrow) {
                line2 = GovText.clip(p.font, metaA, textW);
                line3 = GovText.clip(p.font, metaB, textW);
            } else {
                line2 = GovText.clip(p.font, metaA + "  " + metaB, textW);
                line3 = "";
            }
            p.personViews.add(new GovPeoplePanelModel.PersonView(
                    GovText.clip(p.font, person.name(), textW),
                    line2, line3, person.uuid(), person.name()));
        }
        final int visibleRows = Math.max(1, p.listRect.h() / p.searchRowH);
        p.searchScrollMax = Math.max(0,
                (p.personViews.size() - visibleRows) * p.searchRowH);
        p.searchScroll = GovMetrics.clamp(p.searchScroll, 0, p.searchScrollMax);
        p.searchScroll = (p.searchScroll / p.searchRowH) * p.searchRowH;
    }

    static void rebuildPersonDetail(final GovPeoplePanel p) {
        p.detailLines.clear();
        p.personScrollMax = 0;
        if (p.font == null || p.selectedUuid.isEmpty()
                || p.personFormInnerRect.w() <= 0) {
            return;
        }
        computePersonColumnMetrics(p);
        addDetail(p, 0, p.cardName, GovTheme.TEXT);
        addDetail(p, 1, p.cardUuid, GovTheme.FAINT);
        addDetail(p, 2, GovPeopleUtil.jobValue(p), GovTheme.BODY);
        addDetail(p, 3, p.cardRank, GovTheme.BODY);
        addDetail(p, 4, p.notAvailableText, GovTheme.FAINT);
        addDetail(p, 5, p.notAvailableText, GovTheme.FAINT);
        addDetail(p, 6, p.cardFines > 0
                ? p.cardFines + " " + p.dueText : "0 " + p.clearText,
                p.cardFines > 0 ? GovTheme.WARN : GovTheme.POSITIVE);
        addDetail(p, 7, p.notAvailableText, GovTheme.FAINT);
        addDetail(p, 8, p.notAvailableText, GovTheme.FAINT);
        addDetail(p, 9, p.cardWeapons.isBlank() ? "none" : p.cardWeapons, GovTheme.BODY);
        addDetail(p, 10, p.cardLicense, GovTheme.BODY);

        p.personRowsPerColumn = p.personTwoColumn
                ? Math.max(1, p.detailLines.size() / 2) : p.detailLines.size();
        final int maxPitch = p.personTwoColumn ? 12 : 18;
        final int pitchRows = p.personTwoColumn
                ? p.personRowsPerColumn + 1 : p.personRowsPerColumn;
        p.personPitch = GovMetrics.clamp(
                p.personFormInnerRect.h() / Math.max(1, pitchRows),
                GovPeoplePanelModel.MIN_LINE_PITCH, maxPitch);
        int contentH = 0;
        if (p.personTwoColumn) {
            contentH = pitchRows * p.personPitch;
        } else {
            for (final GovPeoplePanelModel.DetailLine line : p.detailLines) {
                contentH += line.wrapped().isEmpty() ? p.personPitch
                        : Math.max(p.personPitch,
                                line.wrapped().size() * p.font.lineHeight);
            }
        }
        p.personScrollMax = Math.max(0, contentH - p.personFormInnerRect.h());
        p.personScroll = GovMetrics.clamp(p.personScroll, 0, p.personScrollMax);
    }

    static void computePersonColumnMetrics(final GovPeoplePanel p) {
        if (p.personTwoColumn) {
            final int columnW = Math.max(1,
                    (p.personFormInnerRect.w() - GovMetrics.GAP_S) / 2);
            p.personLabelW = Math.min(58, Math.max(0, p.personFormInnerRect.w() / 4));
            p.personValueW = Math.max(1,
                    columnW - p.personLabelW - GovMetrics.GAP_XS);
        } else {
            p.personLabelW = Math.min(90, Math.max(0, p.personFormInnerRect.w() / 8));
            p.personValueW = Math.max(1, p.personFormInnerRect.w()
                    - p.personLabelW - GovMetrics.GAP_XS);
        }
        p.personFullValueW = Math.max(1, p.personFormInnerRect.w()
                - p.personLabelW - GovMetrics.GAP_XS);
    }

    static void addDetail(final GovPeoplePanel p, final int index, final String raw,
            final int color) {
        final String value = raw == null || raw.isBlank() ? "-" : raw;
        if (index == 10 && !p.personTwoColumn && p.personValueW > 0) {
            final List<FormattedCharSequence> lines = List.copyOf(
                    p.font.split(net.minecraft.network.chat.Component.literal(value),
                            p.personValueW));
            p.detailLines.add(new GovPeoplePanelModel.DetailLine(p.detailLabels[index], "", lines, color));
        } else {
            final int width = index == 10 ? p.personFullValueW : p.personValueW;
            p.detailLines.add(new GovPeoplePanelModel.DetailLine(p.detailLabels[index],
                    GovText.clip(p.font, value, Math.max(1, width)),
                    List.of(), color));
        }
    }
}
