package com.horizonssmp.gov.common;

/**
 * The stable field keys of the notes views and the note actions, shared by the server that builds
 * them and the client that renders them.
 *
 * <p>The keys are the wire contract: the server writes them into a {@link GovFields} block, the
 * client reads them. They live in the common package for the same reason {@link JobKeys} does.
 *
 * <p>Note rows are indexed rather than packed: {@code note.<n>.id}, {@code note.<n>.target},
 * {@code note.<n>.author}, {@code note.<n>.text}, {@code note.<n>.createdAt},
 * {@code note.<n>.editedAt}. The index is a position, not an identity; the id field is the
 * identity. The {@code notes} count always carries the true total for the requested scope, and the
 * {@code page}/{@code pages} pair describes the page bounds, so a short page reads as a page
 * rather than as a truncated answer.
 *
 * <p>The note text travels in full: the edit control pre-fills from it, so a clipped copy would
 * silently destroy text on save. The page size is deliberately small for that reason - the whole
 * block has to fit the network codec's 4096-character delta, and a page of full-length notes is
 * the worst case. A block that would still exceed the limit is cut by {@link GovFields#encode}
 * after whole pairs, and the true {@code notes} count makes the cut visible.
 *
 * <h2>Scoped notes (M11)</h2>
 *
 * <p>{@code NOTE_SCOPES} answers with {@link #SCOPES} and one row per scope:
 * {@code scope.<n>.type}, {@code .id}, {@code .label}, {@code .role}, {@code .canPost},
 * {@code .canModerate}. {@code NOTE_SCOPED_LIST} answers with {@link #SCOPE_TYPE},
 * {@link #SCOPE_ID}, {@link #SCOPE_LABEL}, {@link #CAN_POST_NOTES},
 * {@link #CAN_MODERATE_NOTES} and the same {@code notes}/{@code page}/{@code pages} bounds and
 * {@code note.<n>.*} rows as the ordinary list. {@link #CAN_MANAGE} mirrors
 * {@link #CAN_POST_NOTES} on a scoped list for a client that predates the split. A business team
 * board row has an empty {@code target} and {@code targetUuid}; that is valid and means the note
 * is about the team, not one person.
 *
 * <p>The payload scope is the lower-case {@code job} or {@code business} id and the payload
 * {@code scopeId} is the job or business id. The server resolves both and authorizes every read
 * and write itself; none of these fields is ever trusted as proof of identity.
 */
public final class NoteKeys {

    // ------------------------------------------------------- request payloads

    /** Payload: the note id a {@code NOTE_EDIT} or {@code NOTE_DELETE} names. */
    public static final String KEY_NOTE_ID = "noteId";
    /** Payload: the note text for {@code NOTE_ADD} or {@code NOTE_EDIT}. */
    public static final String KEY_TEXT = "text";
    /** Payload: the zero-based page a {@code NOTE_LIST} asks for. */
    public static final String KEY_PAGE = "page";
    /** Payload: the scoped-note kind: {@code job} or {@code business}. */
    public static final String KEY_SCOPE = "scope";
    /** Payload: the job id or business id the scoped action names. */
    public static final String KEY_SCOPE_ID = "scopeId";

    // ------------------------------------------------------ NOTE_LIST response

    /** Viewer flag: the actor may post in this scope (mirrored for old clients). */
    public static final String CAN_MANAGE = "canManageNotes";
    /** The true number of notes in the requested scope. */
    public static final String TOTAL = "notes";
    /** The zero-based page this answer carries. */
    public static final String PAGE = "page";
    /** The true number of pages at {@link #PAGE_SIZE}. */
    public static final String PAGES = "pages";
    /** Prefix of one note row: {@code note.<n>.id}. */
    public static final String ROW_PREFIX = "note.";
    /** Row field: the stable note id. */
    public static final String ROW_ID = ".id";
    /** Row field: the target person's display name, empty for a team board row. */
    public static final String ROW_TARGET = ".target";
    /** Row field: the target person's UUID, as text, empty for a team board row. */
    public static final String ROW_TARGET_UUID = ".targetUuid";
    /** Row field: the author's display name at write time. */
    public static final String ROW_AUTHOR = ".author";
    /** Row field: the author's UUID, as text, empty when unknown. */
    public static final String ROW_AUTHOR_UUID = ".authorUuid";
    /** Row field: the note text, in full. */
    public static final String ROW_TEXT = ".text";
    /** Row field: epoch milliseconds the note was written. */
    public static final String ROW_CREATED = ".createdAt";
    /** Row field: epoch milliseconds of the last edit, {@code 0} when never edited. */
    public static final String ROW_EDITED = ".editedAt";

    // ---------------------------------------------------- scoped-note responses

    /** Response: the scope kind this answer is for: {@code job} or {@code business}. */
    public static final String SCOPE_TYPE = "scopeType";
    /** Response: the job id or business id this answer is for. */
    public static final String SCOPE_ID = "scopeId";
    /** Response: the scope's stored display name, never a bare id. */
    public static final String SCOPE_LABEL = "scopeLabel";
    /** Response: the actor may add a note in this scope right now. */
    public static final String CAN_POST_NOTES = "canPostNotes";
    /** Response: the actor may moderate (delete others') notes in this scope. */
    public static final String CAN_MODERATE_NOTES = "canModerateNotes";
    /** The number of scopes this actor may open, as listed by {@code NOTE_SCOPES}. */
    public static final String SCOPES = "scopes";
    /** Prefix of one scope row: {@code scope.<n>.type}. */
    public static final String SCOPE_PREFIX = "scope.";
    /** Scope row field: the scope kind, {@code job} or {@code business}. */
    public static final String SCOPE_ROW_TYPE = ".type";
    /** Scope row field: the job or business id. */
    public static final String SCOPE_ROW_ID = ".id";
    /** Scope row field: the display label. */
    public static final String SCOPE_ROW_LABEL = ".label";
    /** Scope row field: the actor's role in the scope, e.g. {@code member} or {@code owner}. */
    public static final String SCOPE_ROW_ROLE = ".role";
    /** Scope row field: the actor may post here right now. */
    public static final String SCOPE_ROW_CAN_POST = ".canPost";
    /** Scope row field: the actor may moderate here. */
    public static final String SCOPE_ROW_CAN_MODERATE = ".canModerate";

    // ------------------------------------------------------------------ limits

    /** Longest note text accepted, after sanitization. */
    public static final int MAX_NOTE_CHARS = 200;
    /** Most {@code GLOBAL} notes one person's file may hold. Scoped notes never count here. */
    public static final int MAX_NOTES_PER_PERSON = 50;
    /** Most notes one job may hold about one person. */
    public static final int MAX_JOB_NOTES_PER_TARGET = 20;
    /** Most notes one business may hold about one person. */
    public static final int MAX_BUSINESS_NOTES_PER_TARGET = 20;
    /** Most rows on one business's team board. */
    public static final int MAX_BUSINESS_BOARD_NOTES = 200;
    /**
     * Most scoped notes any one world may hold, across every job and business.
     *
     * <p>A bound on storage, not an authority rule. GLOBAL notes keep their own per-person cap; a
     * server that reaches this ceiling refuses a new scoped note with a clear message rather than
     * letting the single notes map grow without bound.
     */
    public static final int MAX_SCOPED_NOTES = 10_000;
    /** Notes per {@code NOTE_LIST} page. Small on purpose: see the class documentation. */
    public static final int PAGE_SIZE = 6;

    private NoteKeys() {
    }
}
