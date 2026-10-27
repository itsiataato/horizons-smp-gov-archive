package com.horizonssmp.gov.common;

/**
 * The stable field keys, filters and bounds of the Jail tab, the jail actions and the chat mirrors.
 *
 * <p>Keys are the wire contract between the server and the client. They live in the common package
 * so a panel never imports a server class to learn one. Row keys are indexed by position; the
 * identity is the stable detention id.
 *
 * <p>Every list answer carries the true total, the active total and the page bounds, so a short
 * answer reads as a page rather than as the whole history. Reason and release text in list rows is
 * clipped; the detail block carries the full value.
 */
public final class JailKeys {

    // ------------------------------------------------------- request payloads

    /** Payload: the person target, exactly as the dispatcher's target key. */
    public static final String KEY_TARGET = "target";
    /** Payload: the basis id: {@code consent}, {@code violent} or {@code warrant}. */
    public static final String KEY_BASIS = "basis";
    /** Payload: a recorded reason, required by detain and extend. */
    public static final String KEY_REASON = "reason";
    /** Payload: the warrant reference, required when the basis is warrant. */
    public static final String KEY_WARRANT_REF = "warrantRef";
    /** Payload: the requested length in real minutes. */
    public static final String KEY_MINUTES = "minutes";
    /** Payload: the explicit consent confirmation, required when the basis is consent. */
    public static final String KEY_CONSENT_CONFIRMED = "consentConfirmed";
    /** Payload: the list filter id. */
    public static final String KEY_FILTER = "filter";
    /** Payload: the zero-based page a list request asks for. */
    public static final String KEY_PAGE = "page";
    /** Payload: the detention id, for a detail request that does not name a person. */
    public static final String KEY_DETENTION_ID = "detentionId";

    // ---------------------------------------------------------------- filters

    /** Every detention in scope. */
    public static final String FILTER_ALL = "all";
    /** Active detentions only: the default view. */
    public static final String FILTER_ACTIVE = "active";
    /** Detentions ended by an official. */
    public static final String FILTER_RELEASED = "released";
    /** Detentions ended by the timer or the warrant term. */
    public static final String FILTER_EXPIRED = "expired";

    // ----------------------------------------------------------- viewer flags

    /** Viewer flag: the actor holds {@code jail.commit}. */
    public static final String CAN_DETAIN = "canDetain";
    /** Viewer flag: the actor holds {@code jail.release}. */
    public static final String CAN_RELEASE = "canRelease";
    /** Viewer flag: the actor may see every detention, not only their own. */
    public static final String CAN_VIEW_ALL = "canViewAll";
    /** Whether native cuffs are enabled in the server config. */
    public static final String NATIVE_CUFFS = "nativeCuffs";
    /** The configured no-warrant ceiling in real minutes. */
    public static final String MAX_NO_WARRANT_MINUTES = "maxNoWarrantMinutes";
    /** The configured warrant ceiling in real minutes. */
    public static final String MAX_WARRANT_MINUTES = "maxWarrantMinutes";
    /** Whether a native jail cell is configured. */
    public static final String CELL_CONFIGURED = "cellConfigured";
    /** The selected confinement adapter: {@code plugin}, {@code native} or {@code none}. */
    public static final String ADAPTER = "adapter";
    /** The configured cell as display text, or empty. */
    public static final String CELL = "cell";
    /** The viewer's own UUID. */
    public static final String VIEWER_UUID = "viewerUuid";
    /** The viewer's own display name. */
    public static final String VIEWER_NAME = "viewerName";

    /** Payload/detail: the selected cell number, 1-based. */
    public static final String KEY_CELL = "cell";

    // --------------------------------------------- cell create/delete payloads

    /** Payload: the runtime cell name for {@code JAIL_CELL_CREATE} / {@code JAIL_CELL_DELETE}. */
    public static final String KEY_CELL_NAME = "cellName";
    /** Payload: the block x for {@code JAIL_CELL_CREATE}, as a decimal integer. */
    public static final String KEY_CELL_X = "cellX";
    /** Payload: the block y for {@code JAIL_CELL_CREATE}, as a decimal integer. */
    public static final String KEY_CELL_Y = "cellY";
    /** Payload: the block z for {@code JAIL_CELL_CREATE}, as a decimal integer. */
    public static final String KEY_CELL_Z = "cellZ";
    /** Payload: the dimension id for {@code JAIL_CELL_CREATE}, e.g. {@code minecraft:overworld}. */
    public static final String KEY_CELL_DIMENSION = "cellDimension";

    // ------------------------------------------------------------------ list

    /** The true number of records in the requested filter and scope. */
    public static final String TOTAL = "detentions";
    /** The true number of active records in the viewer's scope. */
    public static final String ACTIVE_TOTAL = "activeDetentions";
    /** The zero-based page this answer carries. */
    public static final String PAGE = "page";
    /** The true number of pages at {@link #PAGE_SIZE}. */
    public static final String PAGES = "pages";
    /** The filter this answer was built for. */
    public static final String FILTER = "filter";
    /** Prefix of one detention row: {@code detention.<n>.id}. */
    public static final String ROW_PREFIX = "detention.";
    /** Row field: the stable detention id. */
    public static final String ROW_ID = ".id";
    /** Row field: the target's display name. */
    public static final String ROW_TARGET = ".target";
    /** Row field: the target's UUID text. */
    public static final String ROW_TARGET_UUID = ".targetUuid";
    /** Row field: the basis id. */
    public static final String ROW_BASIS = ".basis";
    /** Row field: the detaining official's name. */
    public static final String ROW_OFFICER = ".officer";
    /** Row field: epoch milliseconds the detention started. */
    public static final String ROW_STARTED = ".started";
    /** Row field: remaining milliseconds, {@code 0} when not active. */
    public static final String ROW_REMAINING_MS = ".remainingMs";
    /** Row field: true when cuffed. */
    public static final String ROW_CUFFED = ".cuffed";
    /** Row field: the effective status name. */
    public static final String ROW_STATUS = ".status";
    /** Row field: the warrant reference, or empty. */
    public static final String ROW_WARRANT = ".warrant";

    // ---------------------------------------------------------------- detail

    /** Detail: the stable detention id. */
    public static final String ID = "id";
    /** Detail: the target's display name. */
    public static final String TARGET = "target";
    /** Detail: the target's UUID text. */
    public static final String TARGET_UUID = "targetUuid";
    /** Detail: the detaining official's name. */
    public static final String OFFICER = "officer";
    /** Detail: the detaining official's UUID text. */
    public static final String OFFICER_UUID = "officerUuid";
    /** Detail: the basis id. */
    public static final String BASIS = "basis";
    /** Detail: the recorded reason. */
    public static final String REASON = "reason";
    /** Detail: the warrant reference, or empty. */
    public static final String WARRANT_REF = "warrantRef";
    /** Detail: epoch milliseconds the detention started. */
    public static final String STARTED_AT = "startedAt";
    /** Detail: the recorded maximum length in real minutes. */
    public static final String MAX_MINUTES = "maxMinutes";
    /** Detail: epoch milliseconds the term ends. */
    public static final String EXPIRES_AT = "expiresAt";
    /** Detail: remaining milliseconds, {@code 0} when not active. */
    public static final String REMAINING_MS = "remainingMs";
    /** Detail: the status name. */
    public static final String STATUS = "status";
    /** Detail: true while the record is active. */
    public static final String ACTIVE = "active";
    /** Detail: true when cuffed. */
    public static final String CUFFED = "cuffed";
    /** Detail: the native cell as display text, or empty. */
    public static final String CELL_LOCATION = "cellLocation";
    /** Detail: epoch milliseconds released, {@code 0} when not released. */
    public static final String RELEASED_AT = "releasedAt";
    /** Detail: the releasing official's name, or empty. */
    public static final String RELEASED_BY = "releasedBy";
    /** Detail: the release reason, or empty. */
    public static final String RELEASE_REASON = "releaseReason";
    /** Detail: the number of recorded extensions. */
    public static final String EXTENSION_COUNT = "extensionCount";
    /** Detail: epoch milliseconds of the last extension, {@code 0} when none. */
    public static final String EXTENDED_AT = "extendedAt";
    /** Detail: the last extending official's name, or empty. */
    public static final String EXTENDED_BY = "extendedBy";
    /** Detail: the last extension reason, or empty. */
    public static final String EXTENSION_REASON = "extensionReason";
    /** Detail: the viewer may release this record. */
    public static final String CAN_RELEASE_THIS = "canReleaseThis";
    /** Detail: the viewer may extend this record. */
    public static final String CAN_EXTEND_THIS = "canExtendThis";
    /** Detail: the viewer may cuff this record. */
    public static final String CAN_CUFF_THIS = "canCuffThis";
    /** Detail: the viewer may uncuff this record. */
    public static final String CAN_UNCUFF_THIS = "canUncuffThis";
    /** Detail: the confinement adapter that handled this record. */
    public static final String RECORD_ADAPTER = "recordAdapter";

    // ------------------------------------------------------------- online picker

    /** The true number of online players the detain picker can offer. */
    public static final String ONLINE_COUNT = "online";
    /** Prefix of one online player name: {@code online.<n>}. */
    public static final String ONLINE_PREFIX = "online.";

    // ------------------------------------------------------------ runtime cells

    /** The true number of persisted runtime jail cells. */
    public static final String CELL_LIST_COUNT = "cellCount";
    /** Prefix of one runtime cell row: {@code cell.<n>.name}. */
    public static final String CELL_ROW_PREFIX = "cell.";
    /** Cell row field: the runtime cell name. */
    public static final String CELL_ROW_NAME = ".name";
    /** Cell row field: the dimension id, e.g. {@code minecraft:overworld}. */
    public static final String CELL_ROW_DIMENSION = ".dimension";
    /** Cell row field: block x, as a decimal integer. */
    public static final String CELL_ROW_X = ".x";
    /** Cell row field: block y, as a decimal integer. */
    public static final String CELL_ROW_Y = ".y";
    /** Cell row field: block z, as a decimal integer. */
    public static final String CELL_ROW_Z = ".z";

    // ------------------------------------------------------------------ bounds

    /** The law's hard no-warrant ceiling, in real minutes. Article 9.12: "Without a warrant, jail may
     * not exceed ten minutes." */
    public static final int HARD_MAX_NO_WARRANT_MINUTES = 10;

    /**
     * The default warrant ceiling, in real minutes.
     *
     * <p><b>Value and source.</b> 140 minutes: Article 9.03 says a warrant lasts seven in-game days,
     * and Article 1.11 defines an in-game day as 20 minutes of server time. 7 x 20 = 140.
     *
     * <p><b>Derivation.</b> The warrant's own lifetime is the natural maximum for an imprisonment
     * ordered under it. The ceiling is configurable so a server can shorten it, but the default is
     * the law's number rather than an invented "long enough" value.
     *
     * <p><b>Failure both ways.</b> A ceiling above the warrant lifetime authorizes a detention the
     * paper no longer covers; a ceiling of a few minutes makes a warrant useless for anything more
     * serious than the no-warrant cap and pushes officers to misuse consent.
     *
     * <p><b>Tolerance.</b> A longer warrant detachment is refused, not clamped, so the officer sees
     * that the request was wrong. The value is copied onto each record, so changing the config never
     * silently shortens or lengthens a detention already in force.
     */
    public static final int DEFAULT_MAX_WARRANT_MINUTES = 7 * 20;

    /** The absolute ceiling the config may not exceed, seven real days. */
    public static final int HARD_MAX_WARRANT_MINUTES = 7 * 24 * 60;

    /** Detention rows per page. */
    public static final int PAGE_SIZE = 8;
    /** Most online names one answer carries for the detain picker. */
    public static final int MAX_LISTED_ONLINE = 64;
    /** Longest reason carried in a list row. */
    public static final int LIST_REASON_CHARS = 96;
    /** Longest warrant reference accepted. */
    public static final int MAX_WARRANT_REF_CHARS = 64;
    /** Longest release or extension reason accepted. */
    public static final int MAX_REASON_CHARS = 512;
    /** Most runtime cells one list answer carries. */
    public static final int MAX_LISTED_CELLS = 64;

    private JailKeys() {
    }
}
