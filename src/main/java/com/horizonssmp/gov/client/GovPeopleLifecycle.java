package com.horizonssmp.gov.client;

final class GovPeopleLifecycle {
    static void setActive(final GovPeoplePanel p, final boolean active) {
        p.active = active;
        if (!active) {
            p.enterRepeat.reset();
            p.deleteArmedNoteId = "";
            GovPeopleWidgets.clearWidgetFocus(p);
        } else if (p.mode == GovPeoplePanel.SUBTAB_PERSON && !p.selectedUuid.isEmpty()
                && p.detailState == GovPeoplePanelModel.LoadState.NONE) {
            GovPeopleRequests.requestDetail(p, p.selectedUuid);
        } else if (p.mode == GovPeoplePanel.SUBTAB_NOTES && !p.selectedUuid.isEmpty()) {
            if (p.detailState == GovPeoplePanelModel.LoadState.NONE) {
                GovPeopleRequests.requestDetail(p, p.selectedUuid);
            } else if (p.canViewNotes && p.notesState == GovPeoplePanelModel.LoadState.NONE) {
                GovPeopleRequests.requestNotes(p, p.selectedUuid, 0);
            }
        }
        GovPeopleWidgets.syncWidgets(p);
        GovPeopleHits.rebuildHits(p);
    }

    static void reset(final GovPeoplePanel p) {
        p.pending.clear();
        p.latestSearchReqId = "";
        p.latestDetailReqId = "";
        p.latestNotesReqId = "";
        p.status = "";
        p.statusIsError = false;
        p.deleteArmedNoteId = "";
        p.selectedNoteIndex = -1;
        p.enterRepeat.reset();
        p.searchScroll = 0;
        p.personScroll = 0;
        p.notesScroll = 0;
        GovPeopleInput.cancelEdit(p);
        GovPeopleHits.rebuildHits(p);
    }

    static void setSubtab(final GovPeoplePanel p, final int index) {
        setMode(p, index, false);
    }

    static void setMode(final GovPeoplePanel p, final int index, final boolean notifyHost) {
        final int next = index == GovPeoplePanel.SUBTAB_PERSON ? GovPeoplePanel.SUBTAB_PERSON
                : index == GovPeoplePanel.SUBTAB_NOTES ? GovPeoplePanel.SUBTAB_NOTES
                        : GovPeoplePanel.SUBTAB_SEARCH;
        if (next == p.mode) {
            return;
        }
        p.mode = next;
        p.status = "";
        p.statusIsError = false;
        p.deleteArmedNoteId = "";
        p.enterRepeat.reset();
        if (next == GovPeoplePanel.SUBTAB_PERSON && !p.selectedUuid.isEmpty()
                && p.detailState == GovPeoplePanelModel.LoadState.NONE) {
            GovPeopleRequests.requestDetail(p, p.selectedUuid);
        } else if (next == GovPeoplePanel.SUBTAB_NOTES && !p.selectedUuid.isEmpty()) {
            if (p.detailState == GovPeoplePanelModel.LoadState.NONE) {
                GovPeopleRequests.requestDetail(p, p.selectedUuid);
            } else if (p.canViewNotes && p.notesState == GovPeoplePanelModel.LoadState.NONE) {
                GovPeopleRequests.requestNotes(p, p.selectedUuid, 0);
            }
        }
        GovPeopleCaches.rebuildAllCaches(p);
        GovPeopleWidgets.syncWidgets(p);
        if (notifyHost && p.host instanceof GovDashboardScreen screen) {
            screen.onPeopleSubtabChanged(next);
        }
    }
}
