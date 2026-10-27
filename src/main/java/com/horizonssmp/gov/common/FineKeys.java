package com.horizonssmp.gov.common;

/**
 * The stable field keys of the Fines tab, the fine actions and the fine chat mirrors, shared by the
 * server that builds them and the client that renders them.
 *
 * <p>The keys are the wire contract: the server writes them into a {@link GovFields} block and the
 * client reads them. They live in the common package for the same reason {@link JobKeys} and {@link
 * NoteKeys} do - a client that imported a server class to learn a key would couple the two sides of
 * the wire to each other's code.
 *
 * <p>List rows are indexed rather than packed: {@code fine.<n>.id}, {@code fine.<n>.target},
 * {@code fine.<n>.band}, {@code fine.<n>.amount}, {@code fine.<n>.state}, and so on. The index is a
 * position, not an identity; the id field is the identity. {@code fines} always carries the true
 * total for the requested filter and scope, and {@code page}/{@code pages} describe the page
 * bounds, so a short answer reads as a page rather than as a truncated list.
 *
 * <p>The list carries a clipped reason ({@link #LIST_REASON_CHARS}); the detail block carries the
 * full one. A page of eight full-length reasons would not fit the network codec's 4000-character
 * delta, and a clipped row with the true total is honest where a silently shortened block is not.
 */
public final class FineKeys {

    // ------------------------------------------------------- request payloads

    /** Payload: the stable fine id for info, pay, collect and waive. */
    public static final String KEY_FINE_ID = "fineId";
    /** Payload: the band id for {@code FINE_ISSUE}: PETTY, MISDEMEANOR, FELONY or AGGRAVATED. */
    public static final String KEY_BAND = "band";
    /** Payload: the law article id a fine rests on. Empty means the default article 9.11. */
    public static final String KEY_ARTICLE = "article";
    /** Payload: the restitution amount in gold ingots, as text. Empty means zero. */
    public static final String KEY_RESTITUTION = "restitution";
    /** Payload: the victim restitution is owed to. Required when restitution is above zero. */
    public static final String KEY_VICTIM = "victim";
    /** Payload: the exile term in in-game days for an AGGRAVATED fine. Empty means the minimum. */
    public static final String KEY_EXILE_DAYS = "exileDays";
    /** Payload: the stated reason. Required and sanitized by the service. */
    public static final String KEY_REASON = "reason";
    /** Payload: the list filter, one of {@link #FILTER_ALL}, {@link #FILTER_UNPAID}, ... */
    public static final String KEY_FILTER = "filter";
    /** Payload: the zero-based page a {@code FINE_LIST} asks for. */
    public static final String KEY_PAGE = "page";

    // ---------------------------------------------------------------- filters

    /** Every fine in scope. */
    public static final String FILTER_ALL = "all";
    /** Fines still expecting gold. */
    public static final String FILTER_UNPAID = "unpaid";
    /** Fines settled by physical payment. */
    public static final String FILTER_PAID = "paid";
    /** Fines cleared by an official, with a recorded reason. */
    public static final String FILTER_WAIVED = "waived";

    // ----------------------------------------------------------- viewer flags

    /** Viewer flag: the actor's effective set holds {@code fine.issue}. */
    public static final String CAN_ISSUE = "canIssue";
    /**
     * Viewer flag: the actor may read every player's fines.
     *
     * <p>True for {@code fine.view.all} and for {@code fine.waive}: a receptionist who can clear a
     * fine must be able to find it, and the seeded govworker job carries waive without view-all.
     */
    public static final String CAN_VIEW_ALL = "canViewAll";
    /** Viewer flag: the actor's effective set holds {@code fine.waive}. */
    public static final String CAN_WAIVE = "canWaive";
    /**
     * Viewer flag: the actor may accept a payment at the reception.
     *
     * <p>This is the same capability as {@code fine.waive}. No {@code fine.collect} id exists in
     * this build, and the reception-desk authority that clears a fine is the authority that accepts
     * gold for it. See {@code FineService} for the full reasoning.
     */
    public static final String CAN_COLLECT = "canCollect";
    /** The viewer's own UUID, so the client can mark which rows are theirs. */
    public static final String VIEWER_UUID = "viewerUuid";

    /** Whether a reception location is configured. Self-payment is refused when it is not. */
    public static final String RECEPTION_CONFIGURED = "receptionConfigured";
    /** The configured payment point's display name, for the "pay there" message. */
    public static final String PAYMENT_POINT = "paymentPoint";

    // --------------------------------------------------------- FINE_LIST block

    /** The true number of fines in the requested filter and scope. */
    public static final String TOTAL = "fines";
    /** The zero-based page this answer carries. */
    public static final String PAGE = "page";
    /** The true number of pages at {@link #PAGE_SIZE}. */
    public static final String PAGES = "pages";
    /** The filter this answer was built for, echoed as the canonical filter id. */
    public static final String FILTER = "filter";
    /** Prefix of one fine row: {@code fine.<n>.id}. */
    public static final String ROW_PREFIX = "fine.";
    /** Row field: the stable fine id. */
    public static final String ROW_ID = "id";
    /** Row field: the fined person's display name. */
    public static final String ROW_TARGET = "target";
    /** Row field: the fined person's UUID, as text. */
    public static final String ROW_TARGET_UUID = "targetUuid";
    /** Row field: the band id. */
    public static final String ROW_BAND = "band";
    /** Row field: the fine amount in gold ingots. */
    public static final String ROW_AMOUNT = "amount";
    /** Row field: the restitution amount in gold ingots. */
    public static final String ROW_RESTITUTION = "restitution";
    /** Row field: the total due at reception, fine plus restitution. */
    public static final String ROW_DUE = "due";
    /** Row field: the law article id. */
    public static final String ROW_ARTICLE = "article";
    /** Row field: the lifecycle state: UNPAID, PAID, WAIVED or VOID. */
    public static final String ROW_STATE = "state";
    /** Row field: the reason, clipped to {@link #LIST_REASON_CHARS}. */
    public static final String ROW_REASON = "reason";
    /** Row field: epoch milliseconds the fine was issued. */
    public static final String ROW_CREATED = "createdAt";
    /** Row field: the exile term in days, {@code 0} when none. */
    public static final String ROW_EXILE_DAYS = "exileDays";
    /** Row field: the restitution victim's name, empty when none. */
    public static final String ROW_VICTIM = "victim";
    /** Row field: the reception receipt reference, empty while unpaid. */
    public static final String ROW_RECEIPT = "receipt";
    /** The true number of online players the issue picker can offer. */
    public static final String ONLINE_COUNT = "online";
    /** Prefix of one online player name: {@code online.<n>}. */
    public static final String ONLINE_PREFIX = "online.";

    // --------------------------------------------------------- FINE_INFO block

    /** Detail: the stable fine id. */
    public static final String ID = "id";
    /** Detail: the fined person's display name. */
    public static final String TARGET = "target";
    /** Detail: the fined person's UUID, as text. */
    public static final String TARGET_UUID = "targetUuid";
    /** Detail: the band id. */
    public static final String BAND = "band";
    /** Detail: the fine amount in gold ingots. */
    public static final String AMOUNT = "amount";
    /** Detail: the restitution amount in gold ingots. */
    public static final String RESTITUTION = "restitution";
    /** Detail: the total due at reception, fine plus restitution. */
    public static final String DUE = "due";
    /** Detail: the law article id. */
    public static final String ARTICLE = "article";
    /** Detail: the article's title from the bundled law. */
    public static final String ARTICLE_TITLE = "articleTitle";
    /** Detail: the stated reason, in full. */
    public static final String REASON = "reason";
    /** Detail: the lifecycle state. */
    public static final String STATE = "state";
    /** Detail: epoch milliseconds the fine was issued. */
    public static final String CREATED = "createdAt";
    /** Detail: epoch milliseconds the fine was paid, {@code 0} while unpaid. */
    public static final String PAID_AT = "paidAt";
    /** Detail: the reception receipt reference, empty while unpaid. */
    public static final String RECEIPT = "receipt";
    /** Detail: the name of the official who accepted payment, or the payer for a self-payment. */
    public static final String COLLECTOR = "collector";
    /** Detail: {@code true} when the payer paid their own fine at reception. */
    public static final String SELF_PAID = "selfPaid";
    /** Detail: the official who waived the fine, or empty. */
    public static final String WAIVED_BY = "waivedBy";
    /** Detail: the recorded waiver reason, or empty. */
    public static final String WAIVED_REASON = "waivedReason";
    /** Detail: the exile term in days, {@code 0} when none. */
    public static final String EXILE_DAYS = "exileDays";
    /** Detail: the restitution victim's name, empty when none. */
    public static final String VICTIM = "victim";
    /** Detail: the restitution victim's server-confirmed UUID, empty when none was recorded. */
    public static final String VICTIM_UUID = "victimUuid";
    /** Detail flag: restitution is recorded as owed but has not been handed over yet. */
    public static final String COMPENSATION_PENDING = "compensationPending";
    /** Detail: epoch milliseconds the restitution was physically paid, {@code 0} when not. */
    public static final String COMPENSATION_PAID_AT = "compensationPaidAt";
    /** Detail flag: the viewer may pay this fine now (own, unpaid). */
    public static final String CAN_PAY = "canPay";
    /** Detail flag: the viewer may waive this fine now (capability, unpaid). */
    public static final String CAN_WAIVE_THIS = "canWaiveThis";
    /** Detail flag: the viewer may collect this fine now (capability, unpaid, not their own). */
    public static final String CAN_COLLECT_THIS = "canCollectThis";

    // ------------------------------------------------------------------ limits

    /** Fine rows per {@code FINE_LIST} page. */
    public static final int PAGE_SIZE = 8;
    /** Most online names one {@code FINE_LIST} answer carries for the issue picker. */
    public static final int MAX_LISTED_ONLINE = 64;
    /** Longest reason a list row carries; the detail block carries the full reason. */
    public static final int LIST_REASON_CHARS = 96;

    private FineKeys() {
    }
}
