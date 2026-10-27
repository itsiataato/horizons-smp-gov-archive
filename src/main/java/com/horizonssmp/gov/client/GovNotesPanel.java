package com.horizonssmp.gov.client;

import com.horizonssmp.gov.client.GovNotesTypes.*;
import static com.horizonssmp.gov.client.GovNotesTypes.*;

import java.util.Locale;

import com.horizonssmp.gov.client.GovDashboardScreen;
import com.horizonssmp.gov.common.NoteKeys;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

final class GovNotesPanel extends GovNotesLifecycle {
    GovNotesPanel(final GovDashboardScreen host) {
        super(host);
        this.allTitle = upper(tr("gui.gov.notes.title.all"));
        this.jobTitle = upper(tr("gui.gov.notes.title.job"));
        this.bizTitle = upper(tr("gui.gov.notes.title.business"));
        this.allTitleBold = GovText.boldCached(this.allTitle);
        this.jobTitleBold = GovText.boldCached(this.jobTitle);
        this.bizTitleBold = GovText.boldCached(this.bizTitle);
        this.notesSubtitle = upper(tr("gui.gov.notes.subtitle"));
        this.registerText = upper(tr("gui.gov.notes.register"));
        this.scopesText = upper(tr("gui.gov.notes.scopes"));
        this.boardText = upper(tr("gui.gov.notes.board"));
        this.personalText = upper(tr("gui.gov.notes.personal"));
        this.postText = upper(tr("gui.gov.notes.post"));
        this.saveText = upper(tr("gui.gov.people.note_save"));
        this.editText = upper(tr("gui.gov.people.note_edit_button"));
        this.deleteText = upper(tr("gui.gov.people.note_delete"));
        this.confirmDeleteText = upper(tr("gui.gov.notes.confirm_delete"));
        this.cancelText = upper(tr("gui.gov.people.note_cancel"));
        this.retryText = upper(tr("gui.gov.notes.retry"));
        this.refreshText = upper(tr("gui.gov.refresh"));
        this.loadText = upper(tr("gui.gov.notes.load"));
        this.previousText = upper(tr("gui.gov.previous"));
        this.nextText = upper(tr("gui.gov.next"));
        this.readOnlyText = upper(tr("gui.gov.notes.read_only"));
        this.teamText = upper(tr("gui.gov.notes.team"));
        this.noJobText = upper(tr("gui.gov.notes.no_job"));
        this.noBizText = upper(tr("gui.gov.notes.no_business"));
        this.noNotesText = upper(tr("gui.gov.notes.no_notes"));
        this.enterTargetText = upper(tr("gui.gov.notes.enter_target"));
        this.pressRefreshText = upper(tr("gui.gov.notes.press_refresh"));
        this.loadingText = upper(tr("gui.gov.notes.loading"));
        this.errorTitle = upper(tr("gui.gov.notes.error_title"));
        this.mutationErrorText = upper(tr("gui.gov.notes.error"));
        this.savedText = upper(tr("gui.gov.notes.saved"));
        this.unknownAuthorText = upper(tr("gui.gov.people.unknown_author"));
        this.newestFirstText = upper(tr("gui.gov.notes.newest_first"));
        this.noJobBold = GovText.boldCached(this.noJobText);
        this.noBizBold = GovText.boldCached(this.noBizText);
        this.noNotesBold = GovText.boldCached(this.noNotesText);
        this.enterTargetBold = GovText.boldCached(this.enterTargetText);
        this.pressRefreshBold = GovText.boldCached(this.pressRefreshText);
        this.errorTitleBold = GovText.boldCached(this.errorTitle);
        this.targetHint = Component.translatable("gui.gov.notes.target_hint");
        this.noteHint = Component.translatable("gui.gov.notes.text_hint");

        GovText.boldCached(this.boardText);
        GovText.boldCached(this.personalText);
        GovText.upperBoldCached(this.registerText);
        GovText.upperBoldCached(this.scopesText);
    }
}
