package com.horizonssmp.gov.server;


import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.horizonssmp.gov.Gov;
import com.horizonssmp.gov.common.ActionResult;
import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.GovCapability;
import com.horizonssmp.gov.common.GovFields;
import com.horizonssmp.gov.common.NoteKeys;
import com.horizonssmp.gov.common.PersonKeys;
import com.horizonssmp.gov.common.records.PermitRecord;

import net.minecraft.server.level.ServerPlayer;

import static com.horizonssmp.gov.server.GovHandlerSupport.*;

public final class GovPeopleHandlers {

    private GovPeopleHandlers() {
    }

    // --------------------------------------------------------------- people: reads

    /**
     * {@code PERSON_SEARCH}: known people whose name starts with the query.
     *
     * <p>The online list is built here, from the live server, and handed to the service; the
     * stored files come from the data. The answer is capped and carries the true match count, so
     * the client can say "showing 20 of 40" instead of silently showing a short list.
     */
    static ActionResult personSearch(final GovActionDispatcher.ActionContext context) {
        final String query = context.payload(PersonKeys.KEY_QUERY).strip();
        if (query.isEmpty()) {
            return ActionResult.failure(
                    context.request().reqId(),
                    ErrorCode.BAD_PAYLOAD,
                    "Type a name to search for.");
        }
        final List<ServerPlayer> online = new ArrayList<>(context.actor().getServer().getPlayerList().getPlayers());
        final List<PersonService.PersonRef> refs = new ArrayList<>(online.size());
        for (final ServerPlayer player : online) {
            refs.add(new PersonService.PersonRef(player.getUUID(), player.getName().getString()));
        }
        final PersonService.Search result = PersonService.search(context.data(), refs, query);

        final Map<String, String> fields = new LinkedHashMap<>();
        fields.put(PersonKeys.CAN_VIEW_NOTES, Boolean.toString(context.can(GovCapability.NOTE_VIEW_ALL)));
        fields.put(PersonKeys.CAN_MANAGE_NOTES, Boolean.toString(context.can(GovCapability.NOTE_WRITE)));
        fields.put(PersonKeys.PEOPLE_COUNT, Integer.toString(result.total()));
        int row = 0;
        for (final PersonService.Hit hit : result.hits()) {
            final String prefix = PersonKeys.PERSON_PREFIX + row + ".";
            fields.put(prefix + PersonKeys.ROW_NAME, hit.name());
            fields.put(prefix + PersonKeys.ROW_UUID, hit.uuid().toString());
            fields.put(prefix + PersonKeys.ROW_ONLINE, Boolean.toString(hit.online()));
            fields.put(prefix + PersonKeys.ROW_JOB, hit.jobName());
            if (context.can(GovCapability.NOTE_VIEW_ALL)) {
                fields.put(prefix + PersonKeys.ROW_NOTES, Integer.toString(hit.noteCount()));
            }
            row++;
        }
        return ActionResult.success(context.request().reqId(), GovFields.encode(fields));
    }

    /**
     * {@code LOOKUP_PERSON}: one person's card, plus their note count for a viewer who may read
     * notes.
     *
     * <p>The target was resolved by the dispatcher before this handler ran, so a card can never be
     * built for a person the server cannot confirm.
     */
    static ActionResult lookupPerson(final GovActionDispatcher.ActionContext context) {
        final GovTargets.Resolved target = context.target();
        if (target == null) {
            return ActionResult.failure(context.request().reqId(), ErrorCode.BAD_TARGET);
        }
        final Map<String, String> fields = PersonService.detail(
                context.data(), target.uuid(), target.name(), target.online(), context.capabilities());
        return ActionResult.success(context.request().reqId(), GovFields.encode(fields));
    }

    // --------------------------------------------------------------- notes: reads

    /**
     * {@code NOTE_LIST}: one page of notes, for one person when the payload names a target and for
     * everyone otherwise.
     *
     * <p>The target is optional, so it is resolved here against the same server-side sources
     * {@link GovTargets} uses everywhere else; a payload can never assert who someone is. The
     * page is clamped server-side, and the answer carries the true total and page count.
     */
    static ActionResult noteList(final GovActionDispatcher.ActionContext context) {
        final String rawTarget = context.payload(GovTargets.KEY_TARGET).strip();
        UUID targetUuid = null;
        String targetName = "";
        if (!rawTarget.isEmpty()) {
            final GovTargets.Resolved target =
                    GovTargets.resolve(context.actor().getServer(), context.data(), rawTarget);
            if (target == null) {
                return ActionResult.failure(context.request().reqId(), ErrorCode.BAD_TARGET);
            }
            targetUuid = target.uuid();
            targetName = target.name();
        }
        final Map<String, String> fields = PersonService.notesPageFields(
                context.data(), targetUuid, targetName, context.capabilities(),
                parsePage(context.payload(NoteKeys.KEY_PAGE)));
        return ActionResult.success(context.request().reqId(), GovFields.encode(fields));
    }

    // ----------------------------------------------------------- notes: mutations

    /** {@code NOTE_ADD}: adds a note to the resolved person's file. */
    static ActionResult noteAdd(final GovActionDispatcher.ActionContext context) {
        final GovTargets.Resolved target = context.target();
        if (target == null) {
            return ActionResult.failure(context.request().reqId(), ErrorCode.BAD_TARGET);
        }
        final PersonService.Outcome outcome = PersonService.addNote(
                context.data(), personActor(context), target.uuid(), target.name(),
                context.payload(NoteKeys.KEY_TEXT), context.now());
        return toResult(context, outcome);
    }

    /** {@code NOTE_EDIT}: replaces a note's text, keeping the original author. */
    static ActionResult noteEdit(final GovActionDispatcher.ActionContext context) {
        final GovTargets.Resolved target = context.target();
        if (target == null) {
            return ActionResult.failure(context.request().reqId(), ErrorCode.BAD_TARGET);
        }
        final PersonService.Outcome outcome = PersonService.editNote(
                context.data(), personActor(context), target.uuid(),
                context.payload(NoteKeys.KEY_NOTE_ID), context.payload(NoteKeys.KEY_TEXT),
                context.now());
        return toResult(context, outcome);
    }

    /** {@code NOTE_DELETE}: removes a note from the resolved person's file. */
    static ActionResult noteDelete(final GovActionDispatcher.ActionContext context) {
        final GovTargets.Resolved target = context.target();
        if (target == null) {
            return ActionResult.failure(context.request().reqId(), ErrorCode.BAD_TARGET);
        }
        final PersonService.Outcome outcome = PersonService.deleteNote(
                context.data(), personActor(context), target.uuid(),
                context.payload(NoteKeys.KEY_NOTE_ID));
        return toResult(context, outcome);
    }

    // -------------------------------------------------------- scoped notes (M11)

    /** {@code NOTE_SCOPES}: the job and business scopes this actor may open. */
    static ActionResult noteScopes(final GovActionDispatcher.ActionContext context) {
        final List<ScopedNoteService.ScopeView> scopes =
                ScopedNoteService.scopes(context.data(), scopedActor(context));
        return ActionResult.success(
                context.request().reqId(),
                GovFields.encode(ScopedNoteService.scopesFields(scopes)));
    }

    /**
     * {@code NOTE_SCOPED_LIST}: one page of a job's or a business's notes.
     *
     * <p>The optional target is resolved server-side here, exactly as {@code NOTE_LIST} resolves
     * its optional target. For a job it is a filter; for a business it selects per-player notes,
     * and its absence selects the team board. The service authorizes the scope before any note is
     * read.
     */
    static ActionResult noteScopedList(final GovActionDispatcher.ActionContext context) {
        final OptionalTarget target = optionalTarget(context);
        if (target.requested() && target.resolved() == null) {
            return ActionResult.failure(context.request().reqId(), ErrorCode.BAD_TARGET);
        }
        final ScopedNoteService.ListResult result = ScopedNoteService.list(
                context.data(),
                scopedActor(context),
                context.payload(NoteKeys.KEY_SCOPE),
                context.payload(NoteKeys.KEY_SCOPE_ID),
                target.resolved() == null ? null : target.resolved().uuid(),
                target.resolved() == null ? "" : target.resolved().name(),
                parsePage(context.payload(NoteKeys.KEY_PAGE)));
        if (!result.ok()) {
            return ActionResult.failure(
                    context.request().reqId(), result.code(), result.message());
        }
        return ActionResult.success(
                context.request().reqId(),
                GovFields.encode(ScopedNoteService.listFields(result)));
    }

    /** {@code NOTE_SCOPED_ADD}: a job note, a business per-player note, or a board note. */
    static ActionResult noteScopedAdd(final GovActionDispatcher.ActionContext context) {
        final OptionalTarget target = optionalTarget(context);
        if (target.requested() && target.resolved() == null) {
            return ActionResult.failure(context.request().reqId(), ErrorCode.BAD_TARGET);
        }
        final ScopedNoteService.Outcome outcome = ScopedNoteService.add(
                context.data(),
                scopedActor(context),
                context.payload(NoteKeys.KEY_SCOPE),
                context.payload(NoteKeys.KEY_SCOPE_ID),
                target.resolved() == null ? null : target.resolved().uuid(),
                target.resolved() == null ? "" : target.resolved().name(),
                context.payload(NoteKeys.KEY_TEXT),
                context.now());
        return toResult(context, outcome);
    }

    /** {@code NOTE_SCOPED_EDIT}: replaces a scoped note's text, keeping the original author. */
    static ActionResult noteScopedEdit(final GovActionDispatcher.ActionContext context) {
        final OptionalTarget target = optionalTarget(context);
        if (target.requested() && target.resolved() == null) {
            return ActionResult.failure(context.request().reqId(), ErrorCode.BAD_TARGET);
        }
        final ScopedNoteService.Outcome outcome = ScopedNoteService.edit(
                context.data(),
                scopedActor(context),
                context.payload(NoteKeys.KEY_SCOPE),
                context.payload(NoteKeys.KEY_SCOPE_ID),
                target.resolved() == null ? null : target.resolved().uuid(),
                context.payload(NoteKeys.KEY_NOTE_ID),
                context.payload(NoteKeys.KEY_TEXT),
                context.now());
        return toResult(context, outcome);
    }

    /** {@code NOTE_SCOPED_DELETE}: removes a scoped note. */
    static ActionResult noteScopedDelete(final GovActionDispatcher.ActionContext context) {
        final OptionalTarget target = optionalTarget(context);
        if (target.requested() && target.resolved() == null) {
            return ActionResult.failure(context.request().reqId(), ErrorCode.BAD_TARGET);
        }
        final ScopedNoteService.Outcome outcome = ScopedNoteService.delete(
                context.data(),
                scopedActor(context),
                context.payload(NoteKeys.KEY_SCOPE),
                context.payload(NoteKeys.KEY_SCOPE_ID),
                target.resolved() == null ? null : target.resolved().uuid(),
                context.payload(NoteKeys.KEY_NOTE_ID));
        return toResult(context, outcome);
    }


}
