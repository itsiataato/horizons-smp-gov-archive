package com.horizonssmp.gov.common;

/**
 * The stable field keys of the bounty actions and their chat mirrors, shared by the server that
 * builds them and the client that renders them.
 *
 * <p>The keys are the wire contract: the server writes them into a {@link GovFields} block and the
 * client reads them. They live in the common package for the same reason {@link FineKeys} and
 * {@link JailKeys} do - a client that imported a server class to learn a key would couple the two
 * sides of the wire to each other's code.
 *
 * <p>List rows are indexed rather than packed: {@code bounty.<n>.id}, {@code bounty.<n>.target},
 * {@code bounty.<n>.amount}, and so on. The index is a position, not an identity; the id field is
 * the identity. {@code bounties} always carries the true total of active bounties for the
 * requested filter, so a short answer reads as a page rather than as a truncated list.
 */
public final class BountyKeys {

    // ------------------------------------------------------- request payloads

    /** Payload: the bounty amount in gold ingots for {@code BOUNTY_PLACE}, as text. */
    public static final String KEY_AMOUNT = "amount";
    /** Payload: the stable bounty id for {@code BOUNTY_CANCEL}. */
    public static final String KEY_BOUNTY_ID = "bountyId";
    /** Payload: the optional person filter for {@code BOUNTY_LIST}. */
    public static final String KEY_TARGET = "target";

    // --------------------------------------------------------- BOUNTY_LIST block

    /** The true number of active bounties in the requested filter. */
    public static final String TOTAL = "bounties";
    /** Prefix of one bounty row: {@code bounty.<n>.id}. */
    public static final String ROW_PREFIX = "bounty.";
    /** Row field: the stable bounty id. */
    public static final String ROW_ID = "id";
    /** Row field: the target's display name. */
    public static final String ROW_TARGET = "target";
    /** Row field: the target's UUID, as text. */
    public static final String ROW_TARGET_UUID = "targetUuid";
    /** Row field: the poster's display name. */
    public static final String ROW_POSTER = "poster";
    /** Row field: the poster's UUID, as text. */
    public static final String ROW_POSTER_UUID = "posterUuid";
    /** Row field: the bounty amount in gold ingots. */
    public static final String ROW_AMOUNT = "amount";
    /** Row field: epoch milliseconds the bounty was placed. */
    public static final String ROW_CREATED = "createdAt";
    /** Row field: epoch milliseconds the bounty expires. */
    public static final String ROW_EXPIRES = "expiresAt";
    /** Row flag: true when the viewer posted this bounty. */
    public static final String ROW_MINE = "mine";

    // ------------------------------------------------------------ place bounds

    /** The configured minimum bounty in gold ingots, for the place form. */
    public static final String MIN_AMOUNT = "minAmount";
    /** The configured maximum bounty in gold ingots, for the place form. */
    public static final String MAX_AMOUNT = "maxAmount";
    /** The configured maximum active bounties on one target, for the place form. */
    public static final String PER_TARGET_CAP = "perTargetCap";
    /** The configured maximum active bounties one poster may hold, for the place form. */
    public static final String PER_POSTER_CAP = "perPosterCap";
    /** The viewer's own UUID, so the client can mark which rows are theirs. */
    public static final String VIEWER_UUID = "viewerUuid";

    // ------------------------------------------------------------- online picker

    /** The true number of online players the place picker can offer. */
    public static final String ONLINE_COUNT = "online";
    /** Prefix of one online player name: {@code online.<n>}. */
    public static final String ONLINE_PREFIX = "online.";

    // ------------------------------------------------------------------ limits

    /** Most bounty rows one {@code BOUNTY_LIST} answer carries; {@link #TOTAL} stays exact. */
    public static final int MAX_LISTED_BOUNTIES = 64;
    /** Most online names one answer carries for the place picker. */
    public static final int MAX_LISTED_ONLINE = 64;

    private BountyKeys() {
    }
}
