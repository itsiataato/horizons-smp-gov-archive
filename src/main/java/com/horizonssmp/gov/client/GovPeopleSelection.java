package com.horizonssmp.gov.client;

final class GovPeopleSelection {
    static void openPerson(final GovPeoplePanel p, final String uuid, final String name) {
        if (uuid == null || uuid.isEmpty()) {
            return;
        }
        p.selectedUuid = uuid;
        p.selectedName = name == null ? "" : name;
        p.cardName = p.selectedName;
        p.cardUuid = uuid;
        p.cardOnline = false;
        p.canViewNotes = false;
        p.canManageNotes = false;
        p.canPostNotes = false;
        p.status = "";
        p.statusIsError = false;
        p.deleteArmedNoteId = "";
        p.notes.clear();
        p.noteViews.clear();
        p.notesTotal = 0;
        p.notesPage = 0;
        p.notesPages = 1;
        p.notesState = GovPeoplePanelModel.LoadState.NONE;
        p.personScroll = 0;
        p.notesScroll = 0;
        p.selectedNoteIndex = -1;
        p.detailState = GovPeoplePanelModel.LoadState.NONE;
        GovPeopleInput.cancelEdit(p);
        GovPeopleLifecycle.setMode(p, GovPeoplePanel.SUBTAB_PERSON, true);
        if (p.detailState == GovPeoplePanelModel.LoadState.NONE) {
            GovPeopleRequests.requestDetail(p, uuid);
        }
        GovPeopleCaches.rebuildAllCaches(p);
        GovPeopleWidgets.syncWidgets(p);
    }

    static void selectPerson(final GovPeoplePanel p, final String uuid, final String name) {
        if (uuid == null || uuid.isEmpty()) {
            return;
        }
        p.selectedUuid = uuid;
        p.selectedName = name == null ? "" : name;
        p.cardName = p.selectedName;
        p.cardUuid = uuid;
        p.cardOnline = false;
        p.canViewNotes = false;
        p.canManageNotes = false;
        p.canPostNotes = false;
        p.status = "";
        p.statusIsError = false;
        p.deleteArmedNoteId = "";
        p.personScroll = 0;
        p.notesScroll = 0;
        p.notes.clear();
        p.noteViews.clear();
        p.notesTotal = 0;
        p.notesPage = 0;
        p.notesPages = 1;
        p.notesState = GovPeoplePanelModel.LoadState.NONE;
        p.selectedNoteIndex = -1;
        p.detailState = GovPeoplePanelModel.LoadState.NONE;
        GovPeopleInput.cancelEdit(p);
        GovPeopleLifecycle.setMode(p, GovPeoplePanel.SUBTAB_PERSON, true);
        if (p.detailState == GovPeoplePanelModel.LoadState.NONE) {
            GovPeopleRequests.requestDetail(p, uuid);
        }
        GovPeopleCaches.rebuildAllCaches(p);
        GovPeopleWidgets.syncWidgets(p);
    }
}
