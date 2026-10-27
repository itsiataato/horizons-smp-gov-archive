package com.horizonssmp.gov.server;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.GovCapability;
import com.horizonssmp.gov.common.NoteKeys;
import com.horizonssmp.gov.common.NoteScope;
import com.horizonssmp.gov.common.records.BusinessRecord;
import com.horizonssmp.gov.common.records.NoteRecord;

/**
 * The scoped-note rules: a job's notes and a business's notes, and who may read, post, edit and
 * delete each kind.
 *
 * <p>This is the layer between the scoped-note handlers and {@link GovSavedData}. It decides
 * <i>whether</i> a scoped operation is legal and performs it. The dispatcher has already checked
 * the one baseline capability the scoped actions require ({@code player.lookup.self}), precisely
 * because job members and business staff do not hold {@code note.view.all}; every real authority
 * check is here.
 *
 * <p>Implementation details live in package-private helpers ({@code ScopedNoteScopes},
 * {@code ScopedNoteListing}, {@code ScopedNoteMutations}, {@code ScopedNoteAuthorization},
 * {@code ScopedNoteSupport}); this class keeps the public API and the nested records.
 */
public final class ScopedNoteService {

    /** Most job notes about one person. */
    public static final int MAX_JOB_NOTES_PER_TARGET = NoteKeys.MAX_JOB_NOTES_PER_TARGET;
    /** Most business notes about one person. */
    public static final int MAX_BUSINESS_NOTES_PER_TARGET = NoteKeys.MAX_BUSINESS_NOTES_PER_TARGET;
    /** Most rows on one business's team board. */
    public static final int MAX_BUSINESS_BOARD_NOTES = NoteKeys.MAX_BUSINESS_BOARD_NOTES;
    /** Most scoped notes in one world, across every scope. */
    public static final int MAX_SCOPED_NOTES = NoteKeys.MAX_SCOPED_NOTES;

    /**
     * Most job scopes {@code NOTE_SCOPES} lists. A job manager may hold authority over every job;
     * the cap keeps the answer inside the field block's 4000-character budget. The cap is a listing
     * bound, not an authority rule: a job named directly by a scoped action is still checked
     * normally.
     */
    public static final int MAX_LISTED_JOB_SCOPES = 6;

    /** Most business scopes {@code NOTE_SCOPES} lists, for the same field-block reason. */
    public static final int MAX_LISTED_BUSINESS_SCOPES = 6;

    /**
     * Longest scope label carried in a {@code NOTE_SCOPES} row.
     */
    public static final int MAX_SCOPE_LABEL = 48;

    /** Who is acting, and what they can do. */
    public record Actor(UUID uuid, String name, Set<GovCapability> capabilities) {

        public Actor {
            name = name == null ? "" : name;
            capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
        }

        /** True when the actor's effective set holds the capability. */
        public boolean can(final GovCapability capability) {
            return capability != null && capabilities.contains(capability);
        }
    }

    /**
     * One scope the actor may open.
     */
    public record ScopeView(
            NoteScope type,
            String id,
            String label,
            String role,
            boolean canPost,
            boolean canModerate) {
    }

    /** One note row: the note and its target's display name, empty for a board row. */
    public record Row(NoteRecord note, String targetName) {
    }

    /** One page of scoped notes. */
    public record Page(List<Row> rows, int total, int page, int pages) {

        public Page {
            rows = rows == null ? List.of() : List.copyOf(rows);
        }
    }

    /**
     * The outcome of a scoped list: accepted with a page, or refused with a code and a message.
     */
    public record ListResult(
            boolean ok, ErrorCode code, String message, ScopeView scope, Page page) {

        public ListResult {
            message = message == null ? "" : message;
        }

        static ListResult ok(final ScopeView scope, final Page page) {
            return new ListResult(true, null, "", scope, page);
        }

        static ListResult refused(final ErrorCode code, final String message) {
            return new ListResult(false, code, message, null, null);
        }
    }

    /** The outcome of a scoped mutation. */
    public record Outcome(boolean ok, ErrorCode code, String message) {

        public Outcome {
            message = message == null ? "" : message;
        }

        public static Outcome ok(final String message) {
            return new Outcome(true, null, message);
        }

        public static Outcome refused(final ErrorCode code, final String message) {
            return new Outcome(false, code, message);
        }
    }

    private ScopedNoteService() {
    }

    // ------------------------------------------------------------- scope lookups

    public static List<ScopeView> scopes(final GovSavedData data, final Actor actor) {
        return ScopedNoteScopes.scopes(data, actor);
    }

    // ------------------------------------------------------------------- list

    public static ListResult list(
            final GovSavedData data,
            final Actor actor,
            final String rawScope,
            final String rawScopeId,
            final UUID targetUuid,
            final String targetName,
            final int rawPage) {
        return ScopedNoteListing.list(data, actor, rawScope, rawScopeId, targetUuid, targetName, rawPage);
    }

    /** The {@code NOTE_SCOPES} answer as a field map. */
    public static Map<String, String> scopesFields(final List<ScopeView> scopes) {
        return ScopedNoteListing.scopesFields(scopes);
    }

    /** The {@code NOTE_SCOPED_LIST} answer as a field map. */
    public static Map<String, String> listFields(final ListResult result) {
        return ScopedNoteListing.listFields(result);
    }

    // -------------------------------------------------------------------- add

    public static Outcome add(
            final GovSavedData data,
            final Actor actor,
            final String rawScope,
            final String rawScopeId,
            final UUID targetUuid,
            final String targetName,
            final String rawText,
            final long now) {
        return ScopedNoteMutations.add(data, actor, rawScope, rawScopeId, targetUuid, targetName, rawText, now);
    }

    // ------------------------------------------------------------------- edit

    public static Outcome edit(
            final GovSavedData data,
            final Actor actor,
            final String rawScope,
            final String rawScopeId,
            final UUID targetFilter,
            final String rawNoteId,
            final String rawText,
            final long now) {
        return ScopedNoteMutations.edit(data, actor, rawScope, rawScopeId, targetFilter, rawNoteId, rawText, now);
    }

    // ----------------------------------------------------------------- delete

    public static Outcome delete(
            final GovSavedData data,
            final Actor actor,
            final String rawScope,
            final String rawScopeId,
            final UUID targetFilter,
            final String rawNoteId) {
        return ScopedNoteMutations.delete(data, actor, rawScope, rawScopeId, targetFilter, rawNoteId);
    }

    // ----------------------------------------------------------------- authorization (delegated)

    public static boolean jobMember(final GovSavedData data, final Actor actor, final String jobId) {
        return ScopedNoteAuthorization.jobMember(data, actor, jobId);
    }

    public static boolean jobCanRead(final GovSavedData data, final Actor actor, final String jobId) {
        return ScopedNoteAuthorization.jobCanRead(data, actor, jobId);
    }

    public static boolean jobCanPost(final GovSavedData data, final Actor actor, final String jobId) {
        return ScopedNoteAuthorization.jobCanPost(data, actor, jobId);
    }

    public static boolean jobCanModerate(final GovSavedData data, final Actor actor, final String jobId) {
        return ScopedNoteAuthorization.jobCanModerate(data, actor, jobId);
    }

    public static boolean businessOwner(final BusinessRecord business, final Actor actor) {
        return ScopedNoteAuthorization.businessOwner(business, actor);
    }

    public static boolean businessEmployee(final BusinessRecord business, final Actor actor) {
        return ScopedNoteAuthorization.businessEmployee(business, actor);
    }

    public static boolean businessManager(final BusinessRecord business, final Actor actor) {
        return ScopedNoteAuthorization.businessManager(business, actor);
    }

    public static boolean businessManage(final Actor actor) {
        return ScopedNoteAuthorization.businessManage(actor);
    }

    public static String businessRole(final BusinessRecord business, final Actor actor) {
        return ScopedNoteAuthorization.businessRole(business, actor);
    }

    public static boolean supervisesJobs(final Actor actor) {
        return ScopedNoteAuthorization.supervisesJobs(actor);
    }

    public static boolean boardCanRead(final BusinessRecord business, final Actor actor) {
        return ScopedNoteAuthorization.boardCanRead(business, actor);
    }

    public static boolean boardCanPost(final BusinessRecord business, final Actor actor) {
        return ScopedNoteAuthorization.boardCanPost(business, actor);
    }

    public static boolean boardCanModerate(final BusinessRecord business, final Actor actor) {
        return ScopedNoteAuthorization.boardCanModerate(business, actor);
    }

    public static boolean perPlayerCanRead(final BusinessRecord business, final Actor actor) {
        return ScopedNoteAuthorization.perPlayerCanRead(business, actor);
    }

    public static boolean perPlayerCanPost(
            final BusinessRecord business, final Actor actor, final UUID targetUuid) {
        return ScopedNoteAuthorization.perPlayerCanPost(business, actor, targetUuid);
    }

    public static boolean perPlayerCanModerate(final BusinessRecord business, final Actor actor) {
        return ScopedNoteAuthorization.perPlayerCanModerate(business, actor);
    }
}