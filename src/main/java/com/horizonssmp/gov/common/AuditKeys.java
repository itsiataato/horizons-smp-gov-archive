package com.horizonssmp.gov.common;

import java.util.List;

/**
 * The stable field keys, filters and bounds of the Audit tab, the export actions and the
 * season-reset flow.
 *
 * <p>These ids are the wire contract between the server and the client, and the command layer
 * speaks the same values. The page size lives here rather than in a panel because the server owns
 * pagination and a client must never ask for a page the server would silently shrink.
 *
 * <p>Every list answer carries the true counts and the page bounds, so a short page reads as a
 * page rather than as the whole log. Rows are compact; the full payload and reason travel only in
 * the {@code AUDIT_INFO} detail block, which is why a list of eight cannot exceed the 4000
 * character field block.
 */
public final class AuditKeys {

    // ------------------------------------------------------- request payloads

    /** Payload: actor name filter, case-insensitive substring. */
    public static final String KEY_ACTOR = "actor";
    /** Payload: action/op filter, case-insensitive exact name. */
    public static final String KEY_ACTION = "action";
    /** Payload: target name filter, case-insensitive substring. */
    public static final String KEY_TARGET = "target";
    /** Payload: outcome filter id. */
    public static final String KEY_OUTCOME = "outcome";
    /** Payload: bounded recent-window id. */
    public static final String KEY_WINDOW = "window";
    /** Payload: zero-based page. */
    public static final String KEY_PAGE = "page";
    /** Payload: the audit sequence number for a detail request. */
    public static final String KEY_SEQ = "seq";
    /** Payload: an export section id. */
    public static final String KEY_SECTION = "section";
    /** Payload: the season-reset confirmation token. */
    public static final String KEY_TOKEN = "token";

    // ---------------------------------------------------------------- filters

    /** Every outcome. */
    public static final String OUTCOME_ALL = "all";
    /** Accepted changes only. */
    public static final String OUTCOME_ACCEPTED = "accepted";
    /** Safety-critical refusals only. */
    public static final String OUTCOME_REFUSED = "refused";

    /** The last hour. */
    public static final String WINDOW_1H = "1h";
    /** The last 24 hours. */
    public static final String WINDOW_24H = "24h";
    /** The last 7 days; the default. */
    public static final String WINDOW_7D = "7d";
    /** The last 30 days, the widest allowed window. */
    public static final String WINDOW_30D = "30d";
    /** No time bound. */
    public static final String WINDOW_ALL = "all";

    /** The window ids in display order. */
    public static final List<String> WINDOWS =
            List.of(WINDOW_1H, WINDOW_24H, WINDOW_7D, WINDOW_30D, WINDOW_ALL);
    /** The outcome ids in display order. */
    public static final List<String> OUTCOMES =
            List.of(OUTCOME_ALL, OUTCOME_ACCEPTED, OUTCOME_REFUSED);

    /** The default recent window. */
    public static final String DEFAULT_WINDOW = WINDOW_7D;

    /** Eight rows: the field-block budget allows eight compact rows plus the header. */
    public static final int PAGE_SIZE = 8;

    /** Hard cap on the recent-window bound: thirty days. */
    public static final long MAX_WINDOW_MS = 30L * 24L * 60L * 60L * 1000L;

    // ----------------------------------------------------------- viewer flags

    /** Viewer flag: the actor holds {@code audit.view} and may export the log. */
    public static final String CAN_EXPORT = "canExport";
    /** Viewer flag: the actor holds {@code season.admin} and may use the Admin section. */
    public static final String CAN_ADMIN = "canAdmin";

    // ------------------------------------------------------------------ list

    /** The total number of lines currently retained. */
    public static final String COUNT = "count";
    /** The true number of lines matching the filters. */
    public static final String MATCHING = "matching";
    /** The zero-based page this answer carries. */
    public static final String PAGE = "page";
    /** The true number of pages for the filters at {@link #PAGE_SIZE}. */
    public static final String PAGES = "pages";
    /** Epoch milliseconds of the oldest retained line, or {@code 0}. */
    public static final String OLDEST_AT = "oldestAt";
    /** Epoch milliseconds of the newest retained line, or {@code 0}. */
    public static final String NEWEST_AT = "newestAt";
    /** The retention ceiling, so the header can say "n of 50000". */
    public static final String RETENTION = "retention";
    /** The canonical window this answer was built for. */
    public static final String WINDOW = "window";
    /** The canonical outcome filter. */
    public static final String OUTCOME = "outcome";
    /** The actor filter echoed back, sanitized. */
    public static final String FILTER_ACTOR = "filterActor";
    /** The action filter echoed back, sanitized. */
    public static final String FILTER_ACTION = "filterAction";
    /** The target filter echoed back, sanitized. */
    public static final String FILTER_TARGET = "filterTarget";

    /** Prefix of one list row: {@code entry.<n>.}. */
    public static final String ROW_PREFIX = "entry.";
    /** Row field: the stable sequence number. */
    public static final String ROW_SEQ = ".seq";
    /** Row field: epoch milliseconds. */
    public static final String ROW_AT = ".at";
    /** Row field: the actor's snapshot name. */
    public static final String ROW_ACTOR = ".actor";
    /** Row field: the action verb. */
    public static final String ROW_ACTION = ".action";
    /** Row field: the target snapshot name, or empty. */
    public static final String ROW_TARGET = ".target";
    /** Row field: {@code ACCEPTED} or {@code REFUSED}. */
    public static final String ROW_OUTCOME = ".outcome";

    // ---------------------------------------------------------------- detail

    /** Detail: the stable sequence number. */
    public static final String SEQ = "seq";
    /** Detail: epoch milliseconds. */
    public static final String AT = "at";
    /** Detail: the ISO-8601 form of {@link #AT}. */
    public static final String AT_ISO = "atIso";
    /** Detail: the actor's UUID text, or empty for a system line. */
    public static final String ACTOR_UUID = "actorUuid";
    /** Detail: the actor's snapshot name. */
    public static final String ACTOR = "actor";
    /** Detail: the actor's job id at the time. */
    public static final String ACTOR_JOB = "actorJob";
    /** Detail: the action verb. */
    public static final String ACTION = "action";
    /** Detail: the target's UUID text, or empty. */
    public static final String TARGET_UUID = "targetUuid";
    /** Detail: the target's snapshot name. */
    public static final String TARGET = "target";
    /** Detail: the sanitized payload summary. */
    public static final String PAYLOAD = "payload";
    /** Detail: the recorded reason. */
    public static final String REASON = "reason";
    /** Detail: the outcome name. */
    public static final String DETAIL_OUTCOME = "entryOutcome";

    // ---------------------------------------------------------------- export

    /** Export answer: the path relative to the server run directory, using {@code /}. */
    public static final String EXPORT_PATH = "exportPath";
    /** Export answer: the exact byte size. */
    public static final String EXPORT_BYTES = "exportBytes";
    /** Export answer: the lowercase hex sha256 of the file. */
    public static final String EXPORT_SHA256 = "exportSha256";
    /** Export answer: the section id that was written. */
    public static final String EXPORT_SECTION = "exportSection";
    /** Export answer: epoch milliseconds the export was written. */
    public static final String EXPORT_AT = "exportAt";
    /** Export answer: the format id in the JSON root. */
    public static final String EXPORT_FORMAT = "exportFormat";

    // ----------------------------------------------------------------- season

    /** Season answer: the section ids a reset would clear, comma-separated. */
    public static final String SEASON_SECTIONS = "seasonSections";
    /** Season answer: section counts as {@code id=count} pairs, comma-separated. */
    public static final String SEASON_COUNTS = "seasonCounts";
    /** Season answer: human-readable count text. */
    public static final String SEASON_COUNTS_TEXT = "seasonCountsText";
    /** Season answer: the sections a reset keeps, comma-separated. */
    public static final String SEASON_KEEP = "seasonKeep";
    /** Season answer: whether business records are in scope. */
    public static final String SEASON_CLEAR_BUSINESSES = "seasonClearBusinesses";
    /** Season answer: the token lifetime in seconds. */
    public static final String SEASON_TOKEN_SECONDS = "seasonTokenSeconds";
    /** Season answer: true when this actor has a live token. */
    public static final String SEASON_PENDING = "seasonPending";
    /** Season answer: epoch milliseconds the pending token expires, or {@code 0}. */
    public static final String SEASON_EXPIRES_AT = "seasonExpiresAt";
    /** Season answer: the single-use token, on a begin answer only. */
    public static final String SEASON_TOKEN = "seasonToken";
    /** Season answer: the full backup's relative path. */
    public static final String SEASON_BACKUP_PATH = "seasonBackupPath";
    /** Season answer: the full backup's sha256. */
    public static final String SEASON_BACKUP_SHA256 = "seasonBackupSha256";
    /** Season answer: the full backup's byte size. */
    public static final String SEASON_BACKUP_BYTES = "seasonBackupBytes";
    /** Season answer: the backup directory relative to the run directory. */
    public static final String SEASON_BACKUP_DIR = "seasonBackupDir";
    /** Season answer: the export directory relative to the run directory. */
    public static final String SEASON_EXPORT_DIR = "seasonExportDir";
    /** Season answer: the number of player files changed by the reset. */
    public static final String SEASON_PLAYERS_RESET = "seasonPlayersReset";

    private AuditKeys() {
    }
}
