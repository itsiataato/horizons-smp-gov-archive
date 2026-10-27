package com.horizonssmp.gov.client;

import java.util.List;

import net.minecraft.util.FormattedCharSequence;

final class GovPeopleNotesCache {
    static void rebuildNoteViews(final GovPeoplePanel p) {
        p.noteViews.clear();
        if (p.font == null || p.notesListRect.w() <= 0) {
            return;
        }
        final int textW = Math.max(1, p.notesListRect.w() - 12
                - (p.canManageNotes && p.canPostNotes
                        ? p.noteEditW + p.noteDeleteW + GovMetrics.GAP_XS * 2 : 0));
        p.noteTextW = textW;
        final int metaW = textW;
        for (final GovPeoplePanelModel.NoteRow note : p.notes) {
            final String author = note.author().isBlank()
                    ? p.unknownAuthorText : note.author();
            final String meta = p.noteScopeText + "  " + author + "  "
                    + GovPeopleUtil.shortTime(note.createdAt())
                    + (note.editedAt() > 0L
                            ? "  " + GovPeoplePanel.upper(GovPeoplePanel.tr("gui.gov.people.note_edited",
                                    GovPeopleUtil.shortTime(note.editedAt())))
                            : "");
            final List<FormattedCharSequence> lines = List.copyOf(
                    p.font.split(net.minecraft.network.chat.Component.literal(note.text()), textW));
            p.noteViews.add(new GovPeoplePanelModel.NoteView(note.id(),
                    GovText.clip(p.font, meta, metaW), lines));
        }
        final int visibleRows = Math.max(1,
                p.notesListRect.h() / Math.max(1, p.noteRowHeight));
        p.notesScrollMax = Math.max(0,
                (p.noteViews.size() - visibleRows) * p.noteRowHeight);
        p.notesScroll = GovMetrics.clamp(p.notesScroll, 0, p.notesScrollMax);
        p.notesScroll = (p.notesScroll / p.noteRowHeight) * p.noteRowHeight;
        rebuildNoteRects(p);
    }

    static void rebuildNoteRects(final GovPeoplePanel p) {
        p.noteEditRects.clear();
        p.noteDeleteRects.clear();
        final int deleteX = p.notesListRect.right() - 6 - p.noteDeleteW;
        final int editX = deleteX - GovMetrics.GAP_XS - p.noteEditW;
        final int buttonH = Math.min(12, p.noteRowHeight - 2);
        int y = p.notesListRect.y() + 1 - p.notesScroll;
        for (int index = 0; index < p.noteViews.size(); index++) {
            p.noteEditRects.add(GovPeopleGeometry.rect(p, editX, y + 1, p.noteEditW, buttonH));
            p.noteDeleteRects.add(GovPeopleGeometry.rect(p, deleteX, y + 1, p.noteDeleteW, buttonH));
            y += p.noteRowHeight;
        }
    }

    static void rebuildStatusTexts(final GovPeoplePanel p) {
        if (p.searchState == GovPeoplePanelModel.LoadState.READY) {
            p.searchCountText = p.peopleTotal > p.people.size()
                    ? GovPeoplePanel.upper(GovPeoplePanel.tr("gui.gov.people.showing", p.people.size(), p.peopleTotal))
                    : GovPeoplePanel.upper(GovPeoplePanel.tr("gui.gov.people.count", p.peopleTotal));
        } else {
            p.searchCountText = "";
        }
        p.cardStatusText = p.detailState == GovPeoplePanelModel.LoadState.READY && !p.selectedUuid.isEmpty()
                ? (p.cardOnline ? p.onlineText : p.offlineText) : "";
        p.cardUuidText = p.cardUuid == null || p.cardUuid.isBlank()
                ? p.selectedUuid : p.cardUuid;
        if (!p.cardUuidText.isEmpty()) {
            p.cardUuidText = p.uuidLabel + "  " + p.cardUuidText;
        }
        p.personTitleWidth = Math.max(0,
                p.headerRect.w() - p.font.width(p.cardStatusText) - 8);
        p.notesCountText = GovPeoplePanel.upper(GovPeoplePanel.tr("gui.gov.people.note_count", p.notesTotal));
        p.notesTitleWidth = Math.max(0,
                p.notesHeaderRect.w() - p.font.width(p.notesCountText) - 8);
        final String name = GovPeopleUtil.displayName(p);
        p.notesTitleText = GovPeoplePanel.upper(GovPeoplePanel.tr("gui.gov.people.notes_title",
                name == null || name.isBlank() ? "-" : name));
        p.notesTitleComponent = GovText.boldCached(p.notesTitleText);
        p.notesPageText = p.bounds.w() < 340
                ? (p.notesPage + 1) + "/" + p.notesPages
                : GovPeoplePanel.upper(GovPeoplePanel.tr("gui.gov.page", p.notesPage + 1, p.notesPages));
    }
}
