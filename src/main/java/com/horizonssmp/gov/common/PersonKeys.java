package com.horizonssmp.gov.common;

/**
 * The stable field keys of the People tab and the person lookup, shared by the server that builds
 * them and the client that renders them.
 *
 * <p>The keys are the wire contract: the server writes them into a {@link GovFields} block, the
 * client reads them. They live in the common package for the same reason {@link SelfCardKeys}
 * does - a client that imported a server class to learn a key would couple the two sides of the
 * wire to each other's code.
 *
 * <p>Search results are indexed rather than packed: a row is {@code person.<n>.name},
 * {@code person.<n>.uuid}, {@code person.<n>.online}, {@code person.<n>.job} and, only for a
 * viewer who may read notes, {@code person.<n>.notes}. The index is a position, not an identity;
 * the uuid field is the identity. The {@code people} count always carries the true total, so a
 * capped block reads as capped rather than as the whole answer.
 *
 * <p>The person detail block carries the viewer flags {@code canViewNotes} and
 * {@code canManageNotes} computed from the actor's effective set on the server. The client uses
 * them to decide which controls to draw, never to decide what is allowed: every mutation is
 * checked again on the server.
 */
public final class PersonKeys {

    // ------------------------------------------------------------- viewer flags

    /** Viewer flag: the actor's effective set holds {@code note.view.all}. */
    public static final String CAN_VIEW_NOTES = "canViewNotes";
    /** Viewer flag: the actor's effective set holds {@code note.write}. */
    public static final String CAN_MANAGE_NOTES = "canManageNotes";

    // -------------------------------------------------- PERSON_SEARCH request

    /** Payload: the name prefix to search for. */
    public static final String KEY_QUERY = "query";

    // ------------------------------------------------- PERSON_SEARCH response

    /** The true number of matching people, before the result cap. */
    public static final String PEOPLE_COUNT = "people";
    /** Prefix of one search result row: {@code person.<n>.name}. */
    public static final String PERSON_PREFIX = "person.";
    /** Row field: the last known display name. */
    public static final String ROW_NAME = ".name";
    /** Row field: the UUID, as text. */
    public static final String ROW_UUID = ".uuid";
    /** Row field: {@code true} or {@code false}, right now. */
    public static final String ROW_ONLINE = ".online";
    /** Row field: the primary job's display name, empty when unemployed. */
    public static final String ROW_JOB = ".job";
    /** Row field: the note count, sent only when the viewer holds {@code note.view.all}. */
    public static final String ROW_NOTES = ".notes";

    // ------------------------------------------------------- person detail block

    /** Detail: the person's display name. */
    public static final String NAME = "name";
    /** Detail: the person's UUID, as text. */
    public static final String UUID = "uuid";
    /** Detail: {@code true} or {@code false}, right now. */
    public static final String ONLINE = "online";
    /** Detail: the primary job's display name, empty when unemployed. */
    public static final String JOB = "job";
    /** Detail: the primary job's id, empty when unemployed. */
    public static final String JOB_ID = "jobId";
    /** Detail: the recorded rank label, empty when none. */
    public static final String RANK = "rank";
    /** Detail: the true number of notes on the person, {@code 0} when not viewable. */
    public static final String NOTES = "notes";
    /** Detail: the number of fines still expecting gold. */
    public static final String FINES = "fines";
    /**
     * Detail: the licence summary for the person.
     *
     * <p>M5 sends the registry's own words: one class per licence with its effective state and
     * active point total, for example {@code Car (C): VALID 6 pts; Truck (T): SUSPENDED until ...}.
     * {@code none} means the person holds no licence for any class. The client renders the value as
     * the server's own words and does not derive a state from a count.
     */
    public static final String LICENSE = "license";
    /** Detail: the weapon-permit summary for the person. */
    public static final String WEAPONS = "weapons";

    // ---------------------------------------------------------------------- limits

    /** Most search result rows one {@code PERSON_SEARCH} answer carries. */
    public static final int MAX_SEARCH_RESULTS = 20;

    private PersonKeys() {
    }
}
