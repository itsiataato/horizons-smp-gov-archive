package com.horizonssmp.gov.client;

final class GovPeopleLabels {
    static void rebuildLabels(final GovPeoplePanel p) {
        p.searchButtonText = upper(tr("gui.gov.people.search_button"));
        p.refreshText = upper(tr("gui.gov.refresh"));
        p.openSearchText = upper(tr("gui.gov.people.open_search"));
        p.prevText = upper(tr("gui.gov.previous"));
        p.nextText = upper(tr("gui.gov.next"));
        p.idleText = upper(tr("gui.gov.people.idle"));
        p.loadingText = upper(tr("gui.gov.people.loading"));
        p.searchErrorText = upper(tr("gui.gov.people.error"));
        p.detailErrorText = upper(tr("gui.gov.people.detail_error"));
        p.notesErrorText = upper(tr("gui.gov.notes.error"));
        p.noResultsText = upper(tr("gui.gov.people.no_results"));
        p.noCitizenText = upper(tr("gui.gov.people.no_citizen"));
        p.noSelectionText = upper(tr("gui.gov.people.no_selection"));
        p.noNotesText = upper(tr("gui.gov.people.no_notes"));
        p.notesHiddenText = upper(tr("gui.gov.people.notes_hidden"));
        p.onlineText = upper(tr("gui.gov.people.online"));
        p.offlineText = upper(tr("gui.gov.people.offline"));
        p.unknownAuthorText = upper(tr("gui.gov.people.unknown_author"));
        p.noteAddText = upper(tr("gui.gov.people.note_add"));
        p.noteSaveText = upper(tr("gui.gov.people.note_save"));
        p.noteCancelText = upper(tr("gui.gov.people.note_cancel"));
        p.noteEditText = upper(tr("gui.gov.people.note_edit_button"));
        p.noteDeleteText = upper(tr("gui.gov.people.note_delete"));
        p.noteConfirmDeleteText = upper(tr("gui.gov.people.note_confirm_delete_action"));
        p.noteScopeText = upper(tr("gui.gov.people.note_scope_global"));
        p.personTitleText = upper(tr("gui.gov.people.person_title"));
        p.personTitleComponent = GovText.boldCached(p.personTitleText);
        p.uuidLabel = upper(tr("gui.gov.people.uuid"));
        p.notAvailableText = upper(tr("gui.gov.people.not_available"));
        p.rankLabel = upper(tr("gui.gov.people.rank"));
        p.standingLabel = upper(tr("gui.gov.people.standing"));
        p.dueText = upper(tr("gui.gov.people.due"));
        p.clearText = upper(tr("gui.gov.people.clear"));
        p.unemployedText = upper(tr("gui.gov.card.unemployed"));
        p.doneText = upper(tr("gui.gov.people.done"));
        p.detailLabels = new String[] {
                upper(tr("gui.gov.people.name")),
                upper(tr("gui.gov.people.uuid")),
                upper(tr("gui.gov.people.job")),
                upper(tr("gui.gov.people.rank")),
                upper(tr("gui.gov.people.duty")),
                upper(tr("gui.gov.people.standing")),
                upper(tr("gui.gov.people.fines_short")),
                upper(tr("gui.gov.people.detention")),
                upper(tr("gui.gov.people.vehicles")),
                upper(tr("gui.gov.people.weapons")),
                upper(tr("gui.gov.people.license"))
        };
    }

    private static String tr(final String key, final Object... args) {
        return GovPeoplePanel.tr(key, args);
    }

    private static String upper(final String text) {
        return GovPeoplePanel.upper(text);
    }
}
