package com.horizonssmp.gov.client;

final class GovPeopleHits {
    static void rebuildHits(final GovPeoplePanel p) {
        p.hits.clear();
        if (!p.active || p.bounds.w() <= 0 || p.bounds.h() <= 0) {
            return;
        }
        switch (p.mode) {
            case GovPeoplePanel.SUBTAB_SEARCH -> {
                if (searchEnabled(p)) {
                    addHit(p, p.searchButtonRect, GovPeoplePanelModel.Action.SEARCH, -1, "");
                }
                if (p.searchState == GovPeoplePanelModel.LoadState.READY) {
                    final int first = p.searchScroll / p.searchRowH;
                    final int last = Math.min(p.personViews.size(),
                            first + p.listRect.h() / p.searchRowH);
                    int y = p.listRect.y() + 1 - p.searchScroll;
                    for (int index = 0; index < p.personViews.size(); index++) {
                        if (index >= first && index < last
                                && y >= p.listInnerRect.y()
                                && y + p.searchRowH <= p.listInnerRect.bottom()) {
                            addHit(p, GovPeopleGeometry.rect(p, p.listRect.x() + 2, y,
                                    Math.max(0, p.listRect.w() - 6),
                                    p.searchRowH - 1).clamp(p.listInnerRect),
                                    GovPeoplePanelModel.Action.ROW, index, "");
                        }
                        y += p.searchRowH;
                    }
                }
                addHit(p, p.refreshRect, GovPeoplePanelModel.Action.REFRESH, -1, "");
            }
            case GovPeoplePanel.SUBTAB_PERSON -> {
                if (p.selectedUuid.isEmpty()) {
                    addHit(p, p.openSearchRect, GovPeoplePanelModel.Action.OPEN_SEARCH, -1, "");
                }
                addHit(p, p.refreshRect, GovPeoplePanelModel.Action.REFRESH, -1, "");
            }
            case GovPeoplePanel.SUBTAB_NOTES -> {
                if (p.canManageNotes && p.canPostNotes
                        && p.detailState == GovPeoplePanelModel.LoadState.READY
                        && !p.selectedUuid.isEmpty()) {
                    addHit(p, p.noteActionRect, GovPeoplePanelModel.Action.NOTE_ADD_SAVE, -1, "");
                    if (!p.editingNoteId.isEmpty()) {
                        addHit(p, p.noteCancelRect, GovPeoplePanelModel.Action.NOTE_CANCEL, -1, "");
                    }
                }
                if (p.canViewNotes && !p.selectedUuid.isEmpty()) {
                    if (p.notesPage > 0 && !GovPeopleUtil.mutationPending(p)) {
                        addHit(p, p.notePrevRect, GovPeoplePanelModel.Action.NOTE_PREV, -1, "");
                    }
                    if (p.notesPage + 1 < p.notesPages && !GovPeopleUtil.mutationPending(p)) {
                        addHit(p, p.noteNextRect, GovPeoplePanelModel.Action.NOTE_NEXT, -1, "");
                    }
                    if (!GovPeopleUtil.mutationPending(p)) {
                        addHit(p, p.notesRefreshRect, GovPeoplePanelModel.Action.REFRESH, -1, "");
                    }
                }
                if (p.notesState == GovPeoplePanelModel.LoadState.READY) {
                    final int first = p.notesScroll / Math.max(1, p.noteRowHeight);
                    final int last = Math.min(p.noteViews.size(),
                            first + p.notesListRect.h() / Math.max(1, p.noteRowHeight));
                    int y = p.notesListRect.y() + 1 - p.notesScroll;
                    for (int index = 0; index < p.noteViews.size(); index++) {
                        if (index >= first && index < last
                                && y >= p.notesListInnerRect.y()
                                && y + p.noteRowHeight
                                        <= p.notesListInnerRect.bottom()) {
                            addNoteHits(p, p.noteViews.get(index), index, y);
                        }
                        y += p.noteRowHeight;
                    }
                }
            }
            default -> {
            }
        }
    }

    static void addNoteHits(final GovPeoplePanel p, final GovPeoplePanelModel.NoteView note,
            final int index, final int y) {
        if (index >= 0 && index < p.noteEditRects.size()
                && index < p.noteDeleteRects.size()) {
            if (p.canManageNotes && p.canPostNotes && !GovPeopleUtil.mutationPending(p)
                    && noteButtonsVisible(p, index)) {
                addHit(p, p.noteEditRects.get(index), GovPeoplePanelModel.Action.NOTE_EDIT, index, note.id());
                addHit(p, p.noteDeleteRects.get(index), GovPeoplePanelModel.Action.NOTE_DELETE, index, note.id());
            }
        }
        addHit(p, GovPeopleGeometry.rect(p, p.notesListRect.x() + 2, y,
                Math.max(0, p.notesListRect.w() - 6), p.noteRowHeight - 1)
                .clamp(p.notesListInnerRect), GovPeoplePanelModel.Action.NOTE_ROW, index, "");
    }

    static boolean noteButtonsVisible(final GovPeoplePanel p, final int index) {
        if (index < 0 || index >= p.noteEditRects.size()
                || index >= p.noteDeleteRects.size()) {
            return false;
        }
        final GovRect edit = p.noteEditRects.get(index);
        final GovRect delete = p.noteDeleteRects.get(index);
        return edit.y() >= p.notesListInnerRect.y()
                && edit.bottom() <= p.notesListInnerRect.bottom()
                && delete.y() >= p.notesListInnerRect.y()
                && delete.bottom() <= p.notesListInnerRect.bottom();
    }

    static void addHit(final GovPeoplePanel p, final GovRect rect, final GovPeoplePanelModel.Action action,
            final int index, final String value) {
        if (rect == null || rect.w() <= 0 || rect.h() <= 0) {
            return;
        }
        p.hits.add(new GovPeoplePanelModel.Hit(rect, action, index, value));
    }

    static boolean searchEnabled(final GovPeoplePanel p) {
        return !GovPeopleUtil.mutationPending(p) && p.searchBox != null
                && !p.searchBox.getValue().isBlank();
    }
}
