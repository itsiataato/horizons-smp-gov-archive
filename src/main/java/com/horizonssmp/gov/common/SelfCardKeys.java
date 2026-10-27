package com.horizonssmp.gov.common;

/**
 * The stable field keys of the self card, shared by the server that builds it and the client that
 * renders it.
 *
 * <p>The keys are the wire contract: {@link GovFields} carries them, the server writes them and
 * the client reads them. They live in the common package rather than next to either renderer
 * because a client that imported a server class just to learn a key would couple the two sides of
 * the wire to each other's code, which is exactly what the tolerant-read rule is meant to avoid.
 *
 * <p>Adding a key is compatible: an older client ignores it. Renaming or removing one is not,
 * without a protocol bump. Values are the record's own vocabulary - {@link
 * com.horizonssmp.gov.common.enums.DutyState} names, {@code true}/{@code false}, {@link #JAIL_HELD}
 * or {@link #JAIL_NONE} - so a value never depends on display language.
 */
public final class SelfCardKeys {

    /** The player's display name. */
    public static final String NAME = "name";
    /** The current job's display name, empty when unemployed. */
    public static final String JOB = "job";
    /** The current job's id, empty when unemployed. */
    public static final String JOB_ID = "jobId";
    /** The rank label, empty when none is recorded. */
    public static final String RANK = "rank";
    /** The duty state: {@code ON_DUTY}, {@code OFF_DUTY} or {@code N_A}. */
    public static final String DUTY = "duty";
    /** The suspect flag: {@code true} or {@code false}. */
    public static final String SUSPECT = "suspect";
    /** The jail state: {@link #JAIL_HELD} or {@link #JAIL_NONE}. */
    public static final String JAIL = "jail";
    /** The detention end as epoch milliseconds, {@code 0} for open-ended or not held. */
    public static final String JAIL_UNTIL = "jailUntil";
    /** The cell region tag, empty when not held. */
    public static final String JAIL_CELL = "jailCell";
    /** The number of fines still expecting gold. */
    public static final String FINES = "fines";

    /** Tab visibility: true when the player may open the People tab. */
    public static final String CAP_PEOPLE = "cap.people";
    /** Tab visibility: true when the player may open the Notes tab. */
    public static final String CAP_NOTES = "cap.notes";
    /** Tab visibility: true when the player may open the Audit tab. */
    public static final String CAP_AUDIT = "cap.audit";
    /** Tab visibility: true when the player may open the Jobs management tab. */
    public static final String CAP_JOBS_MANAGE = "cap.jobsManage";

    /** The jail value for a currently held player. */
    public static final String JAIL_HELD = "held";
    /** The jail value for a player who is not held. */
    public static final String JAIL_NONE = "none";

    private SelfCardKeys() {
    }
}
