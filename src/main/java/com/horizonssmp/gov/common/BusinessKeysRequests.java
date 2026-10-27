package com.horizonssmp.gov.common;

public final class BusinessKeysRequests {

    // ------------------------------------------------------- request payloads

    /** Payload: the trading name, for register. */
    public static final String KEY_NAME = "name";
    /** Payload: the business type id, for register. */
    public static final String KEY_TYPE = "bizType";
    /** Payload: the business id or name an action names. */
    public static final String KEY_BUSINESS = "business";
    /** Payload: the list filter, one of {@link #FILTER_ALL}, {@link #FILTER_ACTIVE}, ... */
    public static final String KEY_FILTER = "filter";
    /** Payload: the zero-based page a {@code BIZ_LIST} asks for. */
    public static final String KEY_PAGE = "page";
    /** Payload: the stated suspend reason. Required for a suspension. */
    public static final String KEY_REASON = "reason";
    /** Payload: the employee role id, one of the ids of the role vocabulary. */
    public static final String KEY_ROLE = "role";
    /** Payload: the linked job id; an empty value unlinks. */
    public static final String KEY_JOB = "job";
    /** Payload: the payroll rate in gold ingots per in-game day. */
    public static final String KEY_RATE = "ratePerDay";
    /** Payload: the pay schedule note. */
    public static final String KEY_SCHEDULE = "schedule";
    /** Payload: a recorded physical payment amount in gold ingots. */
    public static final String KEY_AMOUNT = "amount";
    /** Payload: the note recorded with a payment. */
    public static final String KEY_NOTE = "note";
    /** Payload: free notes on the business card. */
    public static final String KEY_NOTES = "notes";

    // ------------------------------------------- employment request payloads

    /** Payload: a position id, or the business a new position belongs to. */
    public static final String KEY_POSITION = "position";
    /** Payload: a position title. */
    public static final String KEY_TITLE = "title";
    /** Payload: the parent position id; an empty value means the root. */
    public static final String KEY_PARENT = "parent";
    /** Payload: a stated wage in gold ingots per real 24 hours. */
    public static final String KEY_WAGE = "wagePerDay";
    /** Payload: the CSV permission list of a position, in the {@code BusinessPermission} ids. */
    public static final String KEY_PERMISSIONS = "permissions";
    /** Payload: the display order among sibling positions. */
    public static final String KEY_ORDER = "order";
    /** Payload: the employee's name or UUID, as a picker supplies it. */
    public static final String KEY_EMPLOYEE = "employee";
    /** Payload: true for a one-time task contract, which does not occupy the standing slot. */
    public static final String KEY_ONE_TIME = "oneTime";
    /** Payload: a contract id, or the contract an action names. */
    public static final String KEY_CONTRACT = "contract";
    /** Payload: an offer id. */
    public static final String KEY_OFFER = "offer";
    /** Payload: a complaint id. */
    public static final String KEY_COMPLAINT = "complaint";
    /** Payload: a history row reference filter. */
    public static final String KEY_HISTORY = "history";
    /** Payload: the reason stated on a soft-close request or decision. */
    public static final String KEY_CLOSE_REASON = "closeReason";
    /** Payload: a decision id, either {@code approve} or {@code reject}. */
    public static final String KEY_DECISION = "decision";

    // ---------------------------------------------------------------- filters

    /** Every business in scope. */
    public static final String FILTER_ALL = "all";
    /** Businesses that may currently trade. */
    public static final String FILTER_ACTIVE = "active";
    /** Businesses temporarily stopped. */
    public static final String FILTER_SUSPENDED = "suspended";
    /** Businesses whose registration was withdrawn. */
    public static final String FILTER_CLOSED = "closed";

    // ----------------------------------------------------------- viewer flags

    /** Viewer flag: the actor's effective set holds {@code business.register}. */
    public static final String CAN_REGISTER = "canRegister";
    /** Viewer flag: the actor's effective set holds {@code business.manage}. */
    public static final String CAN_MANAGE = "canManage";
    /** Viewer flag: the actor's effective set holds {@code payroll.run}. */
    public static final String CAN_PAYROLL = "canPayroll";
    /** The viewer's own UUID, so the client can mark which rows are theirs. */
    public static final String VIEWER_UUID = "viewerUuid";
    /** The viewer's own display name, so a picker can default to the actor. */
    public static final String VIEWER_NAME = "viewerName";
    /** The maximum payroll rate in gold ingots per in-game day, so an editor can show the bound. */
    public static final String PAYROLL_CAP_GOLD = "payrollCapGold";


    private BusinessKeysRequests() {
    }
}
