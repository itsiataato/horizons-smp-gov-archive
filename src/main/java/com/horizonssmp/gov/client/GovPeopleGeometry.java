package com.horizonssmp.gov.client;

import net.minecraft.client.gui.Font;

final class GovPeopleGeometry {
    static void layout(final GovPeoplePanel p, final int left, final int top, final int right,
            final int bottom, final Font font) {
        p.font = font;
        final int width = Math.max(0, right - left);
        final int height = Math.max(0, bottom - top);
        p.bounds = new GovRect(left, top, width, height);
        computeGeometry(p);
        GovPeopleCaches.rebuildAllCaches(p);
        GovPeopleWidgets.syncWidgets(p);
    }

    static void computeGeometry(final GovPeoplePanel p) {
        if (p.bounds.w() <= 0 || p.bounds.h() <= 0) {
            p.barRect = GovPeoplePanelModel.EMPTY;
            p.refreshRect = GovPeoplePanelModel.EMPTY;
            p.openSearchRect = GovPeoplePanelModel.EMPTY;
            p.searchFieldRect = GovPeoplePanelModel.EMPTY;
            p.searchButtonRect = GovPeoplePanelModel.EMPTY;
            p.countRect = GovPeoplePanelModel.EMPTY;
            p.listRect = GovPeoplePanelModel.EMPTY;
            p.listInnerRect = GovPeoplePanelModel.EMPTY;
            p.headerRect = GovPeoplePanelModel.EMPTY;
            p.personFormRect = GovPeoplePanelModel.EMPTY;
            p.personFormInnerRect = GovPeoplePanelModel.EMPTY;
            p.notesHeaderRect = GovPeoplePanelModel.EMPTY;
            p.notesListRect = GovPeoplePanelModel.EMPTY;
            p.notesListInnerRect = GovPeoplePanelModel.EMPTY;
            p.composerRect = GovPeoplePanelModel.EMPTY;
            p.noteFieldRect = GovPeoplePanelModel.EMPTY;
            p.noteActionRect = GovPeoplePanelModel.EMPTY;
            p.noteCancelRect = GovPeoplePanelModel.EMPTY;
            p.notesStatusRect = GovPeoplePanelModel.EMPTY;
            p.notePrevRect = GovPeoplePanelModel.EMPTY;
            p.noteNextRect = GovPeoplePanelModel.EMPTY;
            p.notesPageRect = GovPeoplePanelModel.EMPTY;
            p.notesRefreshRect = GovPeoplePanelModel.EMPTY;
            return;
        }

        final int pad = p.bounds.w() < 340 || p.bounds.h() < 120
                ? GovMetrics.GAP_XS : GovMetrics.GAP_S;
        final int barH = GovMetrics.BUTTON_H;
        p.barRect = rect(p, p.bounds.x() + pad, p.bounds.bottom() - pad - barH,
                p.bounds.w() - pad * 2, barH);
        p.refreshRect = rect(p, p.barRect.right() - 56, p.barRect.y(), 56, barH);
        p.openSearchRect = rect(p, p.barRect.x(), p.barRect.y(), 72, barH);

        p.searchNarrow = p.bounds.w() < 340;
        p.searchRowH = p.searchNarrow ? 27 : GovMetrics.ROW_H;
        final int searchButtonW = p.bounds.w() < 340 ? 46 : 56;
        final int searchFieldW = Math.max(0,
                p.barRect.w() - searchButtonW - GovMetrics.GAP_XS);
        p.searchFieldRect = rect(p, p.bounds.x() + pad, p.bounds.y() + pad,
                searchFieldW, GovMetrics.FIELD_H);
        p.searchButtonRect = rect(p, p.searchFieldRect.right() + GovMetrics.GAP_XS,
                p.searchFieldRect.y(), searchButtonW, GovMetrics.FIELD_H);
        if (p.searchNarrow) {
            p.countRect = GovPeoplePanelModel.EMPTY;
            p.listRect = rect(p, p.bounds.x() + pad, p.searchFieldRect.bottom() + 4,
                    p.bounds.w() - pad * 2,
                    Math.max(0, p.barRect.y() - 4 - (p.searchFieldRect.bottom() + 4)));
        } else {
            p.countRect = rect(p, p.bounds.x() + pad, p.searchFieldRect.bottom()
                    + GovMetrics.GAP_XS, p.bounds.w() - pad * 2, GovMetrics.LINE_BODY);
            p.listRect = rect(p, p.bounds.x() + pad, p.countRect.bottom() + 2,
                    p.bounds.w() - pad * 2,
                    Math.max(0, p.barRect.y() - 4 - (p.countRect.bottom() + 2)));
        }
        p.listInnerRect = rect(p, p.listRect.x() + 1, p.listRect.y() + 1,
                Math.max(0, p.listRect.w() - 2), Math.max(0, p.listRect.h() - 2));

        p.headerRect = rect(p, p.bounds.x() + pad, p.bounds.y() + pad,
                p.bounds.w() - pad * 2, 19);
        p.personFormRect = rect(p, p.bounds.x() + pad, p.headerRect.bottom() + 1,
                p.bounds.w() - pad * 2,
                Math.max(0, p.barRect.y() - 4 - (p.headerRect.bottom() + 1)));
        p.personFormInnerRect = rect(p, p.personFormRect.x() + 4,
                p.personFormRect.y() + 2, Math.max(0, p.personFormRect.w() - 8),
                Math.max(0, p.personFormRect.h() - 4));
        p.personTwoColumn = p.bounds.w() < 480 || p.bounds.h() < 190;

        p.notesHeaderRect = rect(p, p.bounds.x() + pad, p.bounds.y() + pad,
                p.bounds.w() - pad * 2, GovMetrics.LINE_BODY);
        p.composerRect = rect(p, p.bounds.x() + pad,
                p.barRect.y() - GovMetrics.GAP_XS - GovMetrics.FIELD_H,
                p.bounds.w() - pad * 2, GovMetrics.FIELD_H);
        p.notesListRect = rect(p, p.bounds.x() + pad, p.notesHeaderRect.bottom() + 2,
                p.bounds.w() - pad * 2,
                Math.max(0, p.composerRect.y() - 4 - (p.notesHeaderRect.bottom() + 2)));
        p.notesListInnerRect = rect(p, p.notesListRect.x() + 1, p.notesListRect.y() + 1,
                Math.max(0, p.notesListRect.w() - 2),
                Math.max(0, p.notesListRect.h() - 2));
        p.noteRowHeight = GovMetrics.clamp(p.notesListRect.h() / 3, GovPeoplePanel.NOTE_ROW_MIN,
                GovPeoplePanel.NOTE_ROW_MAX);
        p.noteMaxLines = p.noteRowHeight >= 30 ? 2 : 1;
        p.noteEditW = p.bounds.w() < 340 ? 26 : 34;
        p.noteDeleteW = p.bounds.w() < 340 ? 44 : 54;
        computeComposerGeometry(p);
        computeNotesBarGeometry(p);
        GovPeopleWidgets.positionWidgets(p);
    }

    static void computeComposerGeometry(final GovPeoplePanel p) {
        final boolean editing = !p.editingNoteId.isEmpty();
        final int actionW = p.bounds.w() < 340 ? 52 : 58;
        final int cancelW = editing ? (p.bounds.w() < 340 ? 50 : 56) : 0;
        final int gap = GovMetrics.GAP_XS;
        final int fieldW = Math.max(0, p.composerRect.w() - actionW - gap
                - (editing ? cancelW + gap : 0));
        p.noteFieldRect = rect(p, p.composerRect.x(), p.composerRect.y(), fieldW,
                GovMetrics.FIELD_H);
        p.noteActionRect = rect(p, p.noteFieldRect.right() + gap, p.composerRect.y(),
                actionW, GovMetrics.FIELD_H);
        p.noteCancelRect = editing
                ? rect(p, p.noteActionRect.right() + gap, p.composerRect.y(), cancelW,
                        GovMetrics.FIELD_H)
                : GovPeoplePanelModel.EMPTY;
        GovPeopleWidgets.positionWidgets(p);
    }

    static void computeNotesBarGeometry(final GovPeoplePanel p) {
        final int barH = p.barRect.h();
        final int prevW = p.bounds.w() < 340 ? 36 : 38;
        final int nextW = prevW;
        final int pageW = p.bounds.w() < 340 ? 48 : 64;
        final int refreshW = p.bounds.w() < 340 ? 44 : 56;
        int x = p.barRect.right() - refreshW;
        p.notesRefreshRect = rect(p, x, p.barRect.y(), refreshW, barH);
        x -= pageW + GovMetrics.GAP_XS;
        p.notesPageRect = rect(p, x, p.barRect.y(), pageW, barH);
        x -= nextW + GovMetrics.GAP_XS;
        p.noteNextRect = rect(p, x, p.barRect.y(), nextW, barH);
        x -= prevW + GovMetrics.GAP_XS;
        p.notePrevRect = rect(p, x, p.barRect.y(), prevW, barH);
        p.notesStatusRect = rect(p, p.barRect.x(), p.barRect.y(),
                Math.max(0, p.notePrevRect.x() - p.barRect.x() - GovMetrics.GAP_XS), barH);
    }

    static GovRect rect(final GovPeoplePanel p, final int x, final int y, final int width,
            final int height) {
        final int w = GovMetrics.clamp(width, 0, p.bounds.w());
        final int h = GovMetrics.clamp(height, 0, p.bounds.h());
        final int rx = GovMetrics.clamp(x, p.bounds.x(), p.bounds.right() - w);
        final int ry = GovMetrics.clamp(y, p.bounds.y(), p.bounds.bottom() - h);
        return new GovRect(rx, ry, w, h);
    }
}
