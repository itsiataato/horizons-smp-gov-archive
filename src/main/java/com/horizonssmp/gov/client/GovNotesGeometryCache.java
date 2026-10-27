package com.horizonssmp.gov.client;

import com.horizonssmp.gov.client.GovNotesTypes.*;
import static com.horizonssmp.gov.client.GovNotesTypes.*;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.util.FormattedCharSequence;

class GovNotesGeometryCache extends GovNotesGeometry {
    GovNotesGeometryCache(final GovDashboardScreen host) {
        super(host);
    }

    protected void updateViewText(final View view) {
        if (view.state == Load.READY) {
            view.pageText = upper(tr("gui.gov.notes.page", view.page + 1, view.pages));
            view.countText = view.total == view.notes.size()
                    ? upper(tr("gui.gov.notes.count", view.total))
                    : upper(tr("gui.gov.people.showing", view.notes.size(), view.total));
        } else if (view.state == Load.LOADING) {
            view.countText = this.loadingText;
            view.pageText = "";
        } else if (view.state == Load.ERROR) {
            view.countText = this.errorTitle;
            view.pageText = "";
        } else {
            view.countText = "";
            view.pageText = "";
        }
    }

    protected void rebuildViewGeometry(final View view) {
        if (view == null) {
            return;
        }
        final int available = Math.max(0, this.listRect.h() - 4);
        final int count = view.notes.size();
        final int wanted = Math.min(Math.max(1, count), Math.max(1, available / 18));
        int pitch = 0;
        if (count > 0 && available > 0) {
            pitch = Math.min(available, GovMetrics.clamp(available / wanted, 18, 44));
        }
        view.rowPitch = pitch;
        final int textStart = pitch >= 20 ? 11 : 9;
        view.textLines = pitch <= 0 ? 0
                : Math.max(1, Math.min(MAX_TEXT_LINES, (pitch - textStart) / 9));
        view.scrollMax = Math.max(0, count * pitch - available);
        view.scroll = GovMetrics.clamp(view.scroll, 0, view.scrollMax);
        view.barW = view.scrollMax > 0 ? 4 : 0;
        view.textX = this.listRect.x() + 4;
        view.textW = Math.max(0, this.listRect.w() - 8 - view.barW);
        view.editW = this.font == null ? 0 : Math.min(34,
                Math.max(0, this.font.width(this.editText) + 8));
        view.deleteW = this.font == null ? 0 : Math.min(86,
                Math.max(0, this.font.width(this.confirmDeleteText) + 8));

        final List<DisplayRow> display = new ArrayList<>(count);
        final String localName = localPlayerName();
        for (final NoteRow note : view.notes) {
            final boolean canEdit = view.canPost;
            final boolean canDelete = view.mode == 0
                    ? view.canPost
                    : view.canModerate || (!localName.isEmpty() && !note.author().isBlank()
                            && note.author().equalsIgnoreCase(localName));
            final String targetText = !note.targetName().isBlank() ? note.targetName()
                    : !note.targetUuid().isBlank() ? note.targetUuid() : this.teamText;
            final String author = note.author().isBlank()
                    ? this.unknownAuthorText : note.author();
            final StringBuilder meta = new StringBuilder(64);
            meta.append(targetText).append("  /  ").append(author)
                    .append("  /  ").append(ageText(note.createdAt()));
            if (note.editedAt() > 0L) {
                meta.append("  /  ").append(tr("gui.gov.people.note_edited",
                        ageText(note.editedAt())));
            }
            List<FormattedCharSequence> lines = NO_LINES;
            if (this.font != null && view.textW > 0 && !note.text().isBlank()
                    && view.textLines > 0) {
                final List<FormattedCharSequence> wrapped =
                        GovText.wrapCached(this.font, note.text(), view.textW);
                final int lineCount = Math.min(view.textLines, wrapped.size());
                if (lineCount > 0) {
                    lines = List.copyOf(wrapped.subList(0, lineCount));
                }
            }
            display.add(new DisplayRow(note, meta.toString(), lines, canEdit, canDelete));
        }
        view.display = List.copyOf(display);

        if (view.state == Load.ERROR && this.font != null && this.listRect.w() > 8) {
            final List<FormattedCharSequence> wrapped =
                    GovText.wrapCached(this.font, view.error, this.listRect.w() - 8);
            final int max = Math.max(1, Math.min(4, (this.listRect.h() - 24) / 9));
            view.errorLines = wrapped.isEmpty() ? NO_LINES
                    : List.copyOf(wrapped.subList(0, Math.min(max, wrapped.size())));
        } else {
            view.errorLines = NO_LINES;
        }

        view.hits = NO_HITS;
        rebuildHits(view);
    }

    protected void rebuildHits(final View view) {
        if (view == null) {
            return;
        }
        final int pitch = view.rowPitch;
        final List<NoteHit> hits = new ArrayList<>();
        if (view.state == Load.READY && pitch > 0) {
            final int top = this.listRect.y() + 2 - view.scroll;
            for (int index = 0; index < view.display.size(); index++) {
                final int y = top + index * pitch;
                if (y + pitch <= this.listRect.y() + 2
                        || y >= this.listRect.bottom() - 2) {
                    continue;
                }
                final int rowTop = Math.max(y, this.listRect.y() + 2);
                final int rowBottom = Math.min(y + pitch, this.listRect.bottom() - 2);
                if (rowTop >= rowBottom) {
                    continue;
                }
                final NoteRow note = view.notes.get(index);
                final DisplayRow row = view.display.get(index);
                final int btnY = y + (pitch >= 20 ? 1 : 0);
                final int textStart = y + (pitch >= 20 ? 11 : 9);
                final int btnH = Math.min(ROW_BUTTON_H,
                        Math.max(0, Math.min(rowBottom, textStart) - btnY));
                if (btnH > 0) {
                    int bx = this.listRect.right() - 4 - view.barW;
                    if (row.canDelete() && view.deleteW > 0) {
                        final int x = bx - view.deleteW;
                        if (x >= this.listRect.x()) {
                            hits.add(new NoteHit(new GovRect(x, btnY, view.deleteW, btnH)
                                    .clamp(this.listRect), note, HitKind.DELETE));
                            bx = x - 3;
                        }
                    }
                    if (row.canEdit() && view.editW > 0) {
                        final int x = bx - view.editW;
                        if (x >= this.listRect.x()) {
                            hits.add(new NoteHit(new GovRect(x, btnY, view.editW, btnH)
                                    .clamp(this.listRect), note, HitKind.EDIT));
                        }
                    }
                }
                hits.add(new NoteHit(new GovRect(this.listRect.x() + 2, rowTop,
                        Math.max(0, this.listRect.w() - 4), rowBottom - rowTop)
                        .clamp(this.listRect), note, HitKind.ROW));
            }
        }
        view.hits = List.copyOf(hits);
    }

    protected void rebuildScopeGeometry() {
        this.scopeHits = NO_SCOPE_HITS;
        this.pickerHits = NO_SCOPE_HITS;
        this.scopesErrorLines = NO_LINES;
        this.scopeRetryRect = EMPTY;
        if (this.scopesState == Load.ERROR && this.font != null) {
            final int wrapArea = this.wide
                    ? Math.min(this.scopeRect.w(), this.listRect.w()) : this.mainRect.w();
            if (wrapArea > 8) {
                final List<FormattedCharSequence> wrapped = GovText.wrapCached(this.font,
                        this.scopesError, wrapArea - 8);
                final int max = Math.max(1, Math.min(4, (this.listRect.h() - 24) / 9));
                this.scopesErrorLines = wrapped.isEmpty() ? NO_LINES
                        : List.copyOf(wrapped.subList(0, Math.min(max, wrapped.size())));
            }
            final GovRect retryArea = this.wide ? this.scopeRect : this.mainRect;
            if (retryArea.w() > 8 && retryArea.h() > 0) {
                final int retryH = Math.min(14, retryArea.h());
                final int retryW = Math.min(52, retryArea.w());
                this.scopeRetryRect = new GovRect(
                        retryArea.x() + Math.max(0, (retryArea.w() - retryW) / 2),
                        retryArea.bottom() - retryH, retryW, retryH).clamp(retryArea);
            }
        }
        if (this.wide) {
            this.scopeHeaderRect = new GovRect(this.scopeRect.x(), this.scopeRect.y(),
                    this.scopeRect.w(), Math.min(14, this.scopeRect.h()))
                    .clamp(this.scopeRect);
            final List<ScopeRow> scopes = currentScopes();
            final int top = this.scopeHeaderRect.bottom() + 2;
            final int available = Math.max(0, this.scopeRect.bottom() - top);
            if (this.scopesState == Load.READY && !scopes.isEmpty() && available > 0) {
                final int pitch = Math.min(available,
                        GovMetrics.clamp(available / scopes.size(), 12, 22));
                final List<ScopeHit> hits = new ArrayList<>(scopes.size());
                for (int index = 0; index < scopes.size(); index++) {
                    final int y = top + index * pitch;
                    final int rowH = Math.min(pitch,
                            Math.max(0, this.scopeRect.bottom() - y));
                    if (rowH > 0) {
                        hits.add(new ScopeHit(new GovRect(this.scopeRect.x(), y,
                                this.scopeRect.w(), rowH).clamp(this.scopeRect),
                                scopes.get(index)));
                    }
                }
                this.scopeHits = List.copyOf(hits);
            }
        } else {
            this.scopeHeaderRect = EMPTY;
            if (this.pickerOpen) {
                final List<ScopeRow> scopes = currentScopes();
                final int available = this.mainRect.h();
                if (this.scopesState == Load.READY && !scopes.isEmpty() && available > 0) {
                    final int pitch = Math.min(available,
                            GovMetrics.clamp(available / scopes.size(), 10, 18));
                    final List<ScopeHit> hits = new ArrayList<>(scopes.size());
                    for (int index = 0; index < scopes.size(); index++) {
                        final int y = this.mainRect.y() + index * pitch;
                        final int rowH = Math.min(pitch,
                                Math.max(0, this.mainRect.bottom() - y));
                        if (rowH > 0) {
                            hits.add(new ScopeHit(new GovRect(this.mainRect.x(), y,
                                    this.mainRect.w(), rowH).clamp(this.mainRect),
                                    scopes.get(index)));
                        }
                    }
                    this.pickerHits = List.copyOf(hits);
                }
            }
        }
    }
}
