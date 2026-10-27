package com.horizonssmp.gov.common;

/**
 * The per-action rate-limit policy.
 *
 * <p>Every {@link OpCode} has a policy. The mapping is an exhaustive switch with no {@code default},
 * so adding an action fails compilation until someone decides how expensive it is. That is the same
 * device {@link GovCapabilities#requiredFor(OpCode)} uses for authority: a new action must not
 * inherit "free and unlimited" by omission.
 *
 * <p>The limits are per actor, never global: the limiter keys its bucket by player UUID, so one
 * player exhausting their allowance cannot refuse another player's action. The global per-tick cap
 * is a separate final backstop, not a shared quota.
 *
 * <p>A cost is expressed in ordinary tokens. Reads and ordinary mutations cost one; an export
 * writes a file and hashes it, so it costs three; beginning or committing a season reset writes or
 * verifies a full backup, so it costs five. The configured allowance
 * ({@code gov.security.rateLimitActionsPer10s}) is the bucket size, so a cost of three means three
 * ordinary actions' worth of budget.
 */
public final class RateLimitPolicy {

    /** What kind of work an action does; used for reporting and for the cost. */
    public enum Kind {
        /** A read; never audited and normally cheap. */
        READ,
        /** An ordinary mutation. */
        MUTATION,
        /** A file export or backup. */
        EXPORT,
        /** A season reset step. */
        SEASON
    }

    /**
     * One action's policy.
     *
     * @param kind the class of work
     * @param cost tokens taken from the actor's bucket, at least one
     */
    public record Policy(Kind kind, int cost) {

        public Policy {
            if (kind == null) {
                throw new IllegalArgumentException("RateLimitPolicy.Policy requires a kind");
            }
            if (cost < 1) {
                throw new IllegalArgumentException("RateLimitPolicy.Policy cost must be at least one");
            }
        }
    }

    private static final Policy READ = new Policy(Kind.READ, 1);
    private static final Policy MUTATION = new Policy(Kind.MUTATION, 1);
    private static final Policy EXPORT = new Policy(Kind.EXPORT, 3);
    private static final Policy SEASON = new Policy(Kind.SEASON, 5);

    /**
     * The economy's own window, in milliseconds.
     *
     * <p>Value and source: economy specification section 3.6 says one deposit, withdrawal,
     * {@code /pay}, transfer, store purchase and payroll run per actor per minute. The value is the
     * full 60 seconds, kept in memory only.
     *
     * <p>Failure both ways: a longer window would refuse a legitimate second transaction of a
     * different kind; a shorter one would let a script replay a mutation as often as the
     * dispatcher's ten-second bucket allows. The two buckets apply together, so this one is
     * intentionally the stricter limit for any single economy mutation.
     *
     * <p>Tolerance: the bucket lives in memory and a restart clears it, which section 3.6 accepts
     * as an abuse guard rather than an accounting record. It is keyed by actor and op, so one
     * exhausted action does not consume another's allowance.
     */
    public static final long ECONOMY_WINDOW_MILLIS = 60_000L;

    /**
     * Tokens in the economy bucket per actor per op.
     *
     * <p>Value and source: economy specification section 3.6 says exactly one token. The second
     * attempt inside the window is refused with {@code RATE_LIMITED} and the bucket is not
     * decremented, so a refused attempt cannot extend the lockout.
     */
    public static final int ECONOMY_BUCKET_TOKENS = 1;

    private RateLimitPolicy() {
    }

    /**
     * The policy for an action.
     *
     * <p>Exhaustive by design. The reads are listed individually so a reader can see that a
     * search is a read, not an unlisted default.
     */
    public static Policy forOp(final OpCode op) {
        if (op == null) {
            return MUTATION;
        }
        return switch (op) {
            case OPEN_TAB, LOOKUP_PERSON, PERSON_SEARCH, NOTE_LIST, NOTE_SCOPES,
                    NOTE_SCOPED_LIST, FINE_LIST, FINE_INFO,
                    JOB_LIST, JOB_INFO, BIZ_LIST, BIZ_INFO, VEH_LIST, VEH_INFO, WPN_LIST,
                    WPN_INFO, WPN_CLASSIFY, WPN_PERMIT_LIST, AREA_PERMIT_LIST, JAIL_LIST,
                    JAIL_INFO, PAYROLL_INFO, AUDIT_PAGE, AUDIT_INFO, SEASON_STATUS,
                    LAW_ARTICLE, BIZ_POSITION_LIST, BIZ_OFFER_LIST, BIZ_CONTRACT_LIST,
                    BIZ_COMPLAINT_LIST, HISTORY_LIST, BANK_BALANCE, BANK_HISTORY, STORE_LIST,
                    CONTRACT_LIST, BOUNTY_LIST -> READ;
            case AUDIT_EXPORT, ADMIN_EXPORT -> EXPORT;
            case SEASON_BEGIN, SEASON_COMMIT -> SEASON;
            case NOTE_ADD, NOTE_EDIT, NOTE_DELETE, NOTE_SCOPED_ADD, NOTE_SCOPED_EDIT,
                    NOTE_SCOPED_DELETE, FINE_ISSUE, FINE_PAY, FINE_COLLECT,
                    FINE_WAIVE, SUSPECT_SET, SUSPECT_CLEAR, WARRANT_ISSUE, JOB_CREATE,
                    JOB_EDIT, JOB_DELETE, JOB_ASSIGN, BIZ_REGISTER, BIZ_TRANSFER, BIZ_SUSPEND,
                    BIZ_REVOKE, BIZ_DELETE, BIZ_EMPLOYEE_ADD, BIZ_EMPLOYEE_REMOVE, BIZ_LINK,
                    BIZ_PAYROLL_SET, BIZ_PAYROLL_RECORD, VEH_REGISTER, VEH_EDIT, VEH_IMPOUND,
                    VEH_RELEASE, PLATE_SCAN, LICENSE_ISSUE, LICENSE_REVOKE, LICENSE_POINTS,
                    WPN_REGISTER, WPN_TRANSFER, WPN_REVOKE, WPN_REPORT_LOSS, WPN_PERMIT_ISSUE,
                    WPN_PERMIT_REVOKE, WPN_PERMIT_SUSPEND, AREA_PERMIT_CREATE,
                    AREA_PERMIT_REVOKE, JAIL_COMMIT, JAIL_RELEASE, JAIL_EXTEND, JAIL_CELL,
                    JAIL_CELL_CREATE, JAIL_CELL_DELETE, CUFF, UNCUFF,
                    DUTY_TOGGLE, BIZ_RENAME, BIZ_POSITION_CREATE, BIZ_POSITION_EDIT,
                    BIZ_POSITION_DELETE, BIZ_POSITION_MOVE, BIZ_OFFER_SEND, BIZ_OFFER_RESPOND,
                    BIZ_CONTRACT_END, BIZ_CONTRACT_PROMOTE, BIZ_PAYROLL_RUN, BIZ_COMPLAINT_FILE,
                    BIZ_CLOSE_REQUEST, BIZ_CLOSE_APPROVE, BIZ_CLOSE_REJECT,
                    BANK_DEPOSIT, BANK_WITHDRAW, BANK_TRANSFER, PAY_PLAYER, PAYROLL_RUN,
                    STORE_BUY, CONTRACT_POST, CONTRACT_ACCEPT, CONTRACT_SUBMIT,
                    CONTRACT_APPROVE, CONTRACT_VOID, CONTRACT_CREDIT, BANK_CREDIT,
                    BOUNTY_PLACE, BOUNTY_CANCEL -> MUTATION;
        };
    }

    /**
     * True when the action also takes the economy's 60-second bucket.
     *
     * <p>Value and source: economy specification section 3.6. The section names six mutations and
     * this method covers every economy mutation, because the dispatcher cannot tell which of the
     * twelve can mint or move gold: {@code CONTRACT_ACCEPT} and {@code CONTRACT_SUBMIT} move the
     * escrow indirectly and {@code PAYROLL_RUN} pays many people at once. Applying the bucket to
     * all twelve is the conservative reading of "economy mutations"; applying it to only six would
     * leave the widest payout path unguarded.
     *
     * <p>Reads are deliberately false: section 3.6 says reads are not economy-rate-limited, and
     * the ten-second dispatcher bucket still applies to them.
     *
     * <p>Tolerance: a {@code null} op is not an economy action. {@code CONTRACT_CREDIT} is listed
     * for completeness because it is an economy mutation in the vocabulary, but its console path
     * never consults the network limiter. {@code BANK_CREDIT} is listed for the same reason: it
     * reaches the same console path through the dashboard, which likewise mints without a token.
     */
    public static boolean usesEconomyBucket(final OpCode op) {
        if (op == null) {
            return false;
        }
        return switch (op) {
            case BANK_DEPOSIT, BANK_WITHDRAW, BANK_TRANSFER, PAY_PLAYER, PAYROLL_RUN, STORE_BUY,
                    CONTRACT_POST, CONTRACT_ACCEPT, CONTRACT_SUBMIT, CONTRACT_APPROVE,
                    CONTRACT_VOID, CONTRACT_CREDIT, BANK_CREDIT -> true;
            default -> false;
        };
    }
}
