package com.horizonssmp.gov.common;

/**
 * The stable field keys of the Jobs tab, shared by the server that builds them and the client that
 * renders them.
 *
 * <p>The keys are the wire contract: the server writes them into a {@link GovFields} block, the
 * client reads them. They live in the common package for the same reason {@link SelfCardKeys}
 * does - a client that imported a server class to learn a key would couple the two sides of the
 * wire to each other's code.
 *
 * <p>Lists are indexed rather than packed: a roster row is {@code job.<n>.id}, {@code
 * job.<n>.name}, {@code job.<n>.type}, {@code job.<n>.members}, {@code job.<n>.caps}. The index
 * is a position, not an identity; the id field is the identity. Adding a field is compatible, and
 * a client that does not know a key ignores it. The {@code <n>} counters stop at the documented
 * limits, and the count keys ({@code jobs}, {@code members}, {@code online}) always carry the
 * true total so a truncated block is visibly truncated rather than silently short.
 */
public final class JobKeys {

    /** Viewer flag: the actor's effective set holds {@code job.manage}. */
    public static final String CAN_MANAGE = "canManage";
    /** Viewer flag: the actor's effective set holds {@code job.assign}. */
    public static final String CAN_ASSIGN = "canAssign";

    // ------------------------------------------------------------- job type values

    /** The type value of a protected built-in job. */
    public static final String TYPE_BUILTIN = "built-in";
    /** The type value of a seeded, deletable template job. */
    public static final String TYPE_TEMPLATE = "template";
    /** The type value of a job created at runtime. */
    public static final String TYPE_CUSTOM = "custom";

    // --------------------------------------------------------------- JOB_LIST block

    /** The true number of defined jobs. */
    public static final String JOB_COUNT = "jobs";
    /** Prefix of one roster row: {@code job.<n>.id}. */
    public static final String JOB_ROW_PREFIX = "job.";
    /** Row field: the stable job id. */
    public static final String ROW_ID = "id";
    /** Row field: the display name. */
    public static final String ROW_NAME = "name";
    /** Row field: {@code built-in}, {@code template} or {@code custom}. */
    public static final String ROW_TYPE = "type";
    /** Row field: the number of players holding the job. */
    public static final String ROW_MEMBERS = "members";
    /** Row field: the number of capabilities the job grants. */
    public static final String ROW_CAPS = "caps";
    /** The true number of online players. */
    public static final String ONLINE_COUNT = "online";
    /** Prefix of one online player name: {@code online.<n>}. */
    public static final String ONLINE_PREFIX = "online.";

    // --------------------------------------------------------------- JOB_INFO block

    /** Detail: the stable job id. */
    public static final String ID = "id";
    /** Detail: the display name. */
    public static final String NAME = "name";
    /** Detail: {@code built-in}, {@code template} or {@code custom}. */
    public static final String TYPE = "type";
    /** Detail: the base rank label, empty when none. */
    public static final String RANK = "rank";
    /** Detail: the department label, empty when none. */
    public static final String DEPARTMENT = "department";
    /** Detail: the true number of members. */
    public static final String MEMBER_COUNT = "members";
    /** Detail: the true number of capabilities. */
    public static final String CAP_COUNT = "caps";
    /** Prefix of one capability id: {@code cap.<n>}. */
    public static final String CAP_PREFIX = "cap.";
    /** Prefix of one member: {@code member.<n>.name}. */
    public static final String MEMBER_PREFIX = "member.";
    /** Member field: the last known name. */
    public static final String MEMBER_NAME = ".name";
    /** Member field: the UUID, as text. */
    public static final String MEMBER_UUID = ".uuid";
    /** Member field: {@code true} or {@code false}, right now. */
    public static final String MEMBER_ONLINE = ".online";
    /** Detail: the true number of capability ids in this build's registry. */
    public static final String REGISTRY_COUNT = "registry";
    /** Prefix of one registry capability id: {@code registry.<n>}. */
    public static final String REGISTRY_PREFIX = "registry.";

    // ------------------------------------------------------------- request payloads

    /** Payload: the job id an action names. */
    public static final String KEY_JOB = "job";
    /** Payload: the display name for {@code JOB_CREATE}. */
    public static final String KEY_NAME = "name";
    /** Payload: the new display name for a {@code JOB_EDIT} rename. */
    public static final String KEY_DISPLAY_NAME = "displayName";
    /** Payload: the capability id a {@code JOB_EDIT} adds or removes. */
    public static final String KEY_CAPABILITY = "capability";
    /** Payload: {@code add} or {@code remove}. */
    public static final String KEY_MODE = "mode";
    /** The add mode of a capability edit. */
    public static final String MODE_ADD = "add";
    /** The remove mode of a capability edit. */
    public static final String MODE_REMOVE = "remove";

    // ---------------------------------------------------------------------- limits

    /** Most roster rows one {@code JOB_LIST} answer carries. */
    public static final int MAX_LISTED_JOBS = 32;
    /** Most online names one {@code JOB_LIST} answer carries. */
    public static final int MAX_LISTED_ONLINE = 64;
    /** Most members one {@code JOB_INFO} answer carries. */
    public static final int MAX_LISTED_MEMBERS = 64;
    /** Most capabilities one {@code JOB_INFO} answer carries, job or registry. */
    public static final int MAX_LISTED_CAPS = 64;

    private JobKeys() {
    }
}
