package com.horizonssmp.gov.client;

import java.util.Map;
import java.util.UUID;

import com.horizonssmp.gov.common.GovFields;
import com.horizonssmp.gov.common.NoteKeys;
import com.horizonssmp.gov.common.OpCode;
import com.horizonssmp.gov.common.PersonKeys;
import com.horizonssmp.gov.network.GovActionResultPayload;
import com.horizonssmp.gov.network.GovNetwork;

final class GovPeopleRequests {
    static void doSearch(final GovPeoplePanel p) {
        if (p.searchBox == null || GovPeopleUtil.mutationPending(p)) {
            return;
        }
        p.status = "";
        p.statusIsError = false;
        final String query = p.searchBox.getValue().strip();
        if (query.isEmpty()) {
            p.people.clear();
            p.personViews.clear();
            p.peopleTotal = 0;
            p.searchState = GovPeoplePanelModel.LoadState.IDLE;
            p.searchError = "";
            p.searchScroll = 0;
            GovPeopleCaches.rebuildAllCaches(p);
            return;
        }
        final String reqId = UUID.randomUUID().toString();
        p.pending.put(reqId, GovPeoplePanelModel.RequestKind.SEARCH);
        p.latestSearchReqId = reqId;
        p.searchState = GovPeoplePanelModel.LoadState.LOADING;
        p.searchError = "";
        p.people.clear();
        p.personViews.clear();
        p.searchScroll = 0;
        GovNetwork.sendRequest(OpCode.PERSON_SEARCH,
                Map.of(PersonKeys.KEY_QUERY, query), reqId);
        GovPeopleCaches.rebuildAllCaches(p);
    }

    static void requestDetail(final GovPeoplePanel p, final String uuid) {
        if (uuid == null || uuid.isEmpty()) {
            return;
        }
        final String reqId = UUID.randomUUID().toString();
        p.pending.put(reqId, GovPeoplePanelModel.RequestKind.DETAIL);
        p.latestDetailReqId = reqId;
        p.detailState = GovPeoplePanelModel.LoadState.LOADING;
        p.detailError = "";
        GovNetwork.sendRequest(OpCode.LOOKUP_PERSON, Map.of("target", uuid), reqId);
        GovPeopleWidgets.syncWidgets(p);
        GovPeopleHits.rebuildHits(p);
    }

    static void requestNotes(final GovPeoplePanel p, final String uuid, final int page) {
        if (uuid == null || uuid.isEmpty()) {
            return;
        }
        final String reqId = UUID.randomUUID().toString();
        p.pending.put(reqId, GovPeoplePanelModel.RequestKind.NOTES);
        p.latestNotesReqId = reqId;
        p.notesState = GovPeoplePanelModel.LoadState.LOADING;
        p.canPostNotes = false;
        p.deleteArmedNoteId = "";
        p.notesScroll = 0;
        GovNetwork.sendRequest(OpCode.NOTE_LIST, Map.of("target", uuid,
                NoteKeys.KEY_PAGE, Integer.toString(Math.max(0, page))), reqId);
        GovPeopleHits.rebuildHits(p);
        GovPeopleWidgets.syncWidgets(p);
    }

    static void mutate(final GovPeoplePanel p, final OpCode op, final Map<String, String> payload) {
        if (GovPeopleUtil.mutationPending(p)) {
            return;
        }
        final String reqId = UUID.randomUUID().toString();
        p.pending.put(reqId, GovPeoplePanelModel.RequestKind.MUTATE);
        GovNetwork.sendRequest(op, payload, reqId);
    }

    static void onActionResult(final GovPeoplePanel p, final GovActionResultPayload payload) {
        final GovPeoplePanelModel.RequestKind kind = p.pending.remove(payload.reqId());
        if (kind == null) {
            return;
        }
        switch (kind) {
            case SEARCH -> {
                if (!payload.reqId().equals(p.latestSearchReqId)) {
                    return;
                }
                if (payload.ok()) {
                    applySearch(p, payload.delta());
                } else {
                    p.searchState = GovPeoplePanelModel.LoadState.ERROR;
                    p.searchError = GovPeopleUtil.fallbackError(payload, "gui.gov.people.error");
                }
            }
            case DETAIL -> {
                if (!payload.reqId().equals(p.latestDetailReqId)) {
                    return;
                }
                if (payload.ok()) {
                    applyDetail(p, payload.delta());
                } else {
                    p.detailState = GovPeoplePanelModel.LoadState.ERROR;
                    p.detailError = GovPeopleUtil.fallbackError(payload, "gui.gov.people.detail_error");
                }
            }
            case NOTES -> {
                if (!payload.reqId().equals(p.latestNotesReqId)) {
                    return;
                }
                if (payload.ok()) {
                    applyNotes(p, payload.delta());
                } else {
                    p.notesState = GovPeoplePanelModel.LoadState.ERROR;
                }
            }
            case MUTATE -> {
                if (payload.ok()) {
                    p.status = payload.delta().isBlank() ? p.doneText : payload.delta();
                    p.statusIsError = false;
                    GovPeopleInput.cancelEdit(p);
                    GovPeopleRequests.requestDetail(p, p.selectedUuid);
                    GovPeopleRequests.requestNotes(p, p.selectedUuid, p.notesPage);
                } else {
                    p.status = GovPeopleUtil.fallbackError(payload, "gui.gov.people.error");
                    p.statusIsError = true;
                }
            }
        }
        GovPeopleCaches.rebuildAllCaches(p);
        GovPeopleWidgets.syncWidgets(p);
    }

    static void applySearch(final GovPeoplePanel p, final String delta) {
        final Map<String, String> fields = GovFields.decode(delta);
        p.peopleTotal = GovPeopleUtil.intField(fields, PersonKeys.PEOPLE_COUNT);
        p.people.clear();
        for (int i = 0; i < Math.min(p.peopleTotal, PersonKeys.MAX_SEARCH_RESULTS); i++) {
            final String prefix = PersonKeys.PERSON_PREFIX + i + ".";
            final String uuid = fields.get(prefix + PersonKeys.ROW_UUID);
            if (uuid == null || uuid.isBlank()) {
                continue;
            }
            final boolean hasNotes = fields.containsKey(prefix + PersonKeys.ROW_NOTES);
            final int noteCount = GovPeopleUtil.intField(fields, prefix + PersonKeys.ROW_NOTES);
            p.people.add(new GovPeoplePanelModel.PersonRow(
                    uuid,
                    fields.getOrDefault(prefix + PersonKeys.ROW_NAME, uuid),
                    "true".equals(fields.get(prefix + PersonKeys.ROW_ONLINE)),
                    fields.getOrDefault(prefix + PersonKeys.ROW_JOB, ""),
                    hasNotes ? GovPeoplePanel.upper(GovPeoplePanel.tr("gui.gov.people.note_count", noteCount)) : "",
                    "",
                    ""));
        }
        p.searchState = GovPeoplePanelModel.LoadState.READY;
        p.searchError = "";
        p.searchScroll = 0;
    }

    static void applyDetail(final GovPeoplePanel p, final String delta) {
        final Map<String, String> fields = GovFields.decode(delta);
        final String uuid = fields.get(PersonKeys.UUID);
        if (uuid == null || uuid.isBlank()) {
            p.detailState = GovPeoplePanelModel.LoadState.ERROR;
            p.detailError = GovPeoplePanel.tr("gui.gov.people.detail_error");
            return;
        }
        p.cardUuid = uuid;
        p.cardName = fields.getOrDefault(PersonKeys.NAME, uuid);
        p.cardOnline = "true".equals(fields.get(PersonKeys.ONLINE));
        p.cardJob = fields.getOrDefault(PersonKeys.JOB, "");
        p.cardJobId = fields.getOrDefault(PersonKeys.JOB_ID, "");
        p.cardRank = fields.getOrDefault(PersonKeys.RANK, "");
        p.cardFines = GovPeopleUtil.intField(fields, PersonKeys.FINES);
        p.cardLicense = fields.getOrDefault(PersonKeys.LICENSE, "");
        p.cardWeapons = fields.getOrDefault(PersonKeys.WEAPONS, "");
        p.canViewNotes = "true".equals(fields.get(PersonKeys.CAN_VIEW_NOTES));
        p.canManageNotes = "true".equals(fields.get(PersonKeys.CAN_MANAGE_NOTES));
        ((GovDashboardScreen) p.host).setSubtabVisibility(GovDashboardScreen.Tab.PEOPLE,
                new boolean[] {true, true, p.canViewNotes});
        if (p.selectedUuid.isEmpty()) {
            p.selectedUuid = uuid;
        }
        p.personScroll = 0;
        p.detailState = GovPeoplePanelModel.LoadState.READY;
        p.detailError = "";
        if (p.canViewNotes) {
            if (p.notesState == GovPeoplePanelModel.LoadState.NONE) {
                GovPeopleRequests.requestNotes(p, p.cardUuid, 0);
            }
        } else {
            p.notes.clear();
            p.noteViews.clear();
            p.notesTotal = 0;
            p.notesState = GovPeoplePanelModel.LoadState.NONE;
            p.canPostNotes = false;
        }
    }

    static void applyNotes(final GovPeoplePanel p, final String delta) {
        final Map<String, String> fields = GovFields.decode(delta);
        p.notesTotal = GovPeopleUtil.intField(fields, NoteKeys.TOTAL);
        p.notesPage = GovPeopleUtil.intField(fields, NoteKeys.PAGE);
        p.notesPages = Math.max(1, GovPeopleUtil.intField(fields, NoteKeys.PAGES));
        p.canPostNotes = "true".equals(fields.get(NoteKeys.CAN_MANAGE));
        p.notes.clear();
        for (int i = 0; i < NoteKeys.PAGE_SIZE; i++) {
            final String prefix = NoteKeys.ROW_PREFIX + i;
            final String id = fields.get(prefix + NoteKeys.ROW_ID);
            if (id == null || id.isBlank()) {
                continue;
            }
            p.notes.add(new GovPeoplePanelModel.NoteRow(
                    id,
                    fields.getOrDefault(prefix + NoteKeys.ROW_AUTHOR, ""),
                    fields.getOrDefault(prefix + NoteKeys.ROW_TEXT, ""),
                    GovPeopleUtil.longField(fields, prefix + NoteKeys.ROW_CREATED),
                    GovPeopleUtil.longField(fields, prefix + NoteKeys.ROW_EDITED)));
        }
        p.notesState = GovPeoplePanelModel.LoadState.READY;
        p.notesScroll = 0;
        if (!p.editingNoteId.isEmpty() && GovPeopleUtil.findNote(p, p.editingNoteId) == null) {
            GovPeopleInput.cancelEdit(p);
        }
    }
}
