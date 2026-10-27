package com.horizonssmp.gov.server;

import java.util.UUID;

import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.NoteScope;
import com.horizonssmp.gov.common.records.BusinessRecord;
import com.horizonssmp.gov.common.GovCapability;
import com.horizonssmp.gov.common.records.NoteRecord;
import com.horizonssmp.gov.server.Sanitizer;

/** Mutations (add/edit/delete) for scoped notes. Package-private. */
final class ScopedNoteMutations {

    private ScopedNoteMutations() {
    }

    static ScopedNoteService.Outcome add(
            final GovSavedData data,
            final ScopedNoteService.Actor actor,
            final String rawScope,
            final String rawScopeId,
            final UUID targetUuid,
            final String targetName,
            final String rawText,
            final long now) {
        final ScopedNoteScopeKey key = ScopedNoteScopeKey.parse(rawScope, rawScopeId);
        if (key == null) {
            return ScopedNoteService.Outcome.refused(
                    ErrorCode.BAD_PAYLOAD, ScopedNoteSupport.scopeMessage(rawScope));
        }
        if (!ScopedNoteSupport.usable(data, actor)) {
            return ScopedNoteService.Outcome.refused(ErrorCode.INVALID_STATE, "No scope to note.");
        }

        BusinessRecord business = null;
        if (key.type() == NoteScope.JOB) {
            if (data.job(key.id()) == null) {
                return ScopedNoteService.Outcome.refused(
                        ErrorCode.BAD_JOB, JobService.noSuchJobMessage(key.id()));
            }
            if (targetUuid == null) {
                return ScopedNoteService.Outcome.refused(
                        ErrorCode.BAD_PAYLOAD, "A job note needs a person to be about.");
            }
            if (!ScopedNoteAuthorization.jobCanPost(data, actor, key.id())) {
                return ScopedNoteService.Outcome.refused(
                        ErrorCode.FORBIDDEN, "You may not post a note in that job.");
            }
        } else {
            business = data.business(key.id());
            if (business == null) {
                return ScopedNoteService.Outcome.refused(
                        ErrorCode.BAD_BUSINESS,
                        BusinessService.noSuchBusinessMessage(key.id()));
            }
            if (targetUuid == null) {
                if (!ScopedNoteAuthorization.boardCanPost(business, actor)) {
                    return ScopedNoteService.Outcome.refused(
                            ErrorCode.FORBIDDEN,
                            ScopedNoteAuthorization.boardCanRead(business, actor)
                                    ? "That business is not ACTIVE; the team board is read-only."
                                    : "You may not post on that business's team board.");
                }
            } else if (!ScopedNoteAuthorization.perPlayerCanPost(business, actor, targetUuid)) {
                return ScopedNoteService.Outcome.refused(
                        ErrorCode.FORBIDDEN,
                        ScopedNoteAuthorization.perPlayerCanRead(business, actor)
                                ? "That note needs an ACTIVE business and a current owner or employee as its target."
                                : "You may not post notes on that business.");
            }
        }

        final String text = PersonService.noteText(rawText);
        if (text == null) {
            return ScopedNoteService.Outcome.refused(ErrorCode.BAD_PAYLOAD, PersonService.textLimitMessage());
        }

        if (data.storedScopedNoteCount() >= ScopedNoteService.MAX_SCOPED_NOTES) {
            return ScopedNoteService.Outcome.refused(
                    ErrorCode.INVALID_STATE,
                    "The server already holds the maximum of " + ScopedNoteService.MAX_SCOPED_NOTES
                            + " scoped notes; delete one before adding another.");
        }

        final String name = targetUuid == null
                ? ""
                : (Sanitizer.name(targetName).isBlank()
                        ? ScopedNoteSupport.targetName(data, targetUuid) : Sanitizer.name(targetName));
        if (targetUuid != null) {
            if (key.type() == NoteScope.JOB
                    && data.notesInScopeFor(NoteScope.JOB, key.id(), targetUuid).size()
                            >= ScopedNoteService.MAX_JOB_NOTES_PER_TARGET) {
                return ScopedNoteService.Outcome.refused(
                        ErrorCode.INVALID_STATE,
                        name + " already has " + ScopedNoteService.MAX_JOB_NOTES_PER_TARGET
                                + " notes in job '" + key.id() + "'; delete one before adding another.");
            }
            if (key.type() == NoteScope.BUSINESS
                    && data.notesInScopeFor(NoteScope.BUSINESS, key.id(), targetUuid).size()
                            >= ScopedNoteService.MAX_BUSINESS_NOTES_PER_TARGET) {
                return ScopedNoteService.Outcome.refused(
                        ErrorCode.INVALID_STATE,
                        name + " already has " + ScopedNoteService.MAX_BUSINESS_NOTES_PER_TARGET
                                + " notes in '" + business.name()
                                + "'; delete one before adding another.");
            }
        } else if (data.notesInScopeFor(NoteScope.BUSINESS, key.id(), null).size()
                >= ScopedNoteService.MAX_BUSINESS_BOARD_NOTES) {
            return ScopedNoteService.Outcome.refused(
                    ErrorCode.INVALID_STATE,
                    "The team board of '" + business.name() + "' already has "
                            + ScopedNoteService.MAX_BUSINESS_BOARD_NOTES + " notes; delete one before adding another.");
        }

        if (targetUuid != null) {
            data.playerOrCreate(targetUuid, name, now);
        }
        final String id = PersonService.newNoteId(data);
        data.putNote(new NoteRecord(
                id, targetUuid, actor.uuid(), Sanitizer.name(actor.name()), text, now, 0L,
                key.type().id(), key.id()));
        final String where = key.type() == NoteScope.JOB
                ? "job '" + key.id() + "'"
                : (targetUuid == null ? "the team board of '" + business.name() + "'"
                        : "'" + business.name() + "'");
        return ScopedNoteService.Outcome.ok("added note " + id + " in " + where
                + (targetUuid == null ? "." : " on " + name + "."));
    }

    static ScopedNoteService.Outcome edit(
            final GovSavedData data,
            final ScopedNoteService.Actor actor,
            final String rawScope,
            final String rawScopeId,
            final UUID targetFilter,
            final String rawNoteId,
            final String rawText,
            final long now) {
        final ScopedNoteLookup lookup =
                lockedNote(data, actor, rawScope, rawScopeId, targetFilter, rawNoteId);
        if (lookup.refusal() != null) {
            return lookup.refusal();
        }
        final NoteRecord note = lookup.note();
        if (!canEdit(data, lookup, actor)) {
            return ScopedNoteService.Outcome.refused(ErrorCode.FORBIDDEN, "You may not edit that note.");
        }
        final String text = PersonService.noteText(rawText);
        if (text == null) {
            return ScopedNoteService.Outcome.refused(ErrorCode.BAD_PAYLOAD, PersonService.textLimitMessage());
        }
        if (text.equals(note.text())) {
            return ScopedNoteService.Outcome.refused(
                    ErrorCode.INVALID_STATE, "Note " + note.id() + " already has that text.");
        }
        data.putNote(note.withText(text, now));
        return ScopedNoteService.Outcome.ok("edited note " + note.id() + ".");
    }

    static ScopedNoteService.Outcome delete(
            final GovSavedData data,
            final ScopedNoteService.Actor actor,
            final String rawScope,
            final String rawScopeId,
            final UUID targetFilter,
            final String rawNoteId) {
        final ScopedNoteLookup lookup =
                lockedNote(data, actor, rawScope, rawScopeId, targetFilter, rawNoteId);
        if (lookup.refusal() != null) {
            return lookup.refusal();
        }
        final NoteRecord note = lookup.note();
        if (!canDelete(data, lookup, actor)) {
            return ScopedNoteService.Outcome.refused(ErrorCode.FORBIDDEN, "You may not delete that note.");
        }
        if (!data.removeNote(note.id())) {
            return ScopedNoteService.Outcome.refused(
                    ErrorCode.INVALID_STATE, "Note " + note.id() + " could not be deleted.");
        }
        return ScopedNoteService.Outcome.ok("deleted note " + note.id() + ".");
    }

    private static ScopedNoteLookup lockedNote(
            final GovSavedData data,
            final ScopedNoteService.Actor actor,
            final String rawScope,
            final String rawScopeId,
            final UUID targetFilter,
            final String rawNoteId) {
        final ScopedNoteScopeKey key = ScopedNoteScopeKey.parse(rawScope, rawScopeId);
        if (key == null) {
            return ScopedNoteLookup.refused(
                    ScopedNoteService.Outcome.refused(ErrorCode.BAD_PAYLOAD, ScopedNoteSupport.scopeMessage(rawScope)));
        }
        if (!ScopedNoteSupport.usable(data, actor)) {
            return ScopedNoteLookup.refused(
                    ScopedNoteService.Outcome.refused(ErrorCode.INVALID_STATE, "No scope to note."));
        }
        if (key.type() == NoteScope.JOB && data.job(key.id()) == null) {
            return ScopedNoteLookup.refused(unknownNote(rawNoteId));
        }
        if (key.type() == NoteScope.JOB && !ScopedNoteAuthorization.jobCanRead(data, actor, key.id())) {
            return ScopedNoteLookup.refused(
                    ScopedNoteService.Outcome.refused(ErrorCode.FORBIDDEN, "You may not use that job's notes."));
        }
        final BusinessRecord business =
                key.type() == NoteScope.BUSINESS ? data.business(key.id()) : null;
        if (key.type() == NoteScope.BUSINESS && business == null) {
            return ScopedNoteLookup.refused(unknownNote(rawNoteId));
        }

        final String id = Sanitizer.id(rawNoteId);
        final NoteRecord note = id.isEmpty() ? null : data.note(id);
        if (note == null
                || !GovSavedData.isScope(note, key.type())
                || !key.id().equals(note.scopeId())
                || (targetFilter != null && !targetFilter.equals(note.targetUuid()))) {
            return ScopedNoteLookup.refused(unknownNote(rawNoteId));
        }
        if (business != null && !ScopedNoteAuthorization.isAuthor(note, actor)) {
            final boolean readable = note.targetUuid() == null
                    ? ScopedNoteAuthorization.boardCanRead(business, actor)
                    : ScopedNoteAuthorization.perPlayerCanRead(business, actor);
            if (!readable) {
                return ScopedNoteLookup.refused(unknownNote(rawNoteId));
            }
        }
        return new ScopedNoteLookup(key, business, note, null);
    }

    private static ScopedNoteService.Outcome unknownNote(final String rawNoteId) {
        return ScopedNoteService.Outcome.refused(
                ErrorCode.BAD_NOTE, PersonService.noSuchNoteMessage(rawNoteId));
    }

    private static boolean canEdit(
            final GovSavedData data, final ScopedNoteLookup lookup, final ScopedNoteService.Actor actor) {
        if (lookup.key().type() == NoteScope.JOB) {
            return ScopedNoteAuthorization.jobCanPost(data, actor, lookup.key().id());
        }
        if (lookup.note().targetUuid() == null) {
            return ScopedNoteAuthorization.isAuthor(lookup.note(), actor)
                    && ScopedNoteAuthorization.boardCanPost(lookup.business(), actor);
        }
        return ScopedNoteAuthorization.isAuthor(lookup.note(), actor)
                && ScopedNoteAuthorization.perPlayerCanPost(
                        lookup.business(), actor, lookup.note().targetUuid());
    }

    private static boolean canDelete(
            final GovSavedData data, final ScopedNoteLookup lookup, final ScopedNoteService.Actor actor) {
        if (lookup.key().type() == NoteScope.JOB) {
            return ScopedNoteAuthorization.jobCanPost(data, actor, lookup.key().id())
                    || (actor != null && actor.can(GovCapability.JOB_MANAGE));
        }
        if (lookup.note().targetUuid() == null) {
            return ScopedNoteAuthorization.isAuthor(lookup.note(), actor)
                    || ScopedNoteAuthorization.businessOwner(lookup.business(), actor)
                    || ScopedNoteAuthorization.businessManager(lookup.business(), actor)
                    || ScopedNoteAuthorization.businessManage(actor);
        }
        return ScopedNoteAuthorization.isAuthor(lookup.note(), actor)
                || ScopedNoteAuthorization.businessOwner(lookup.business(), actor)
                || ScopedNoteAuthorization.businessManage(actor);
    }
}