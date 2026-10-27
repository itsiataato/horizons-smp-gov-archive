package com.horizonssmp.gov.common;

public final class BusinessKeysBlocks {


    /** The true number of businesses in the requested filter and scope. */
    public static final String TOTAL = "businesses";
    /** The zero-based page this answer carries. */
    public static final String PAGE = "page";
    /** The true number of pages at {@link #PAGE_SIZE}. */
    public static final String PAGES = "pages";
    /** The filter this answer was built for, echoed as the canonical filter id. */
    public static final String FILTER = "filter";
    /** Prefix of one business row: {@code business.<n>.id}. */
    public static final String ROW_PREFIX = "business.";
    /** Row field: the stable business id. */
    public static final String ROW_ID = ".id";
    /** Row field: the trading name. */
    public static final String ROW_NAME = ".name";
    /** Row field: the owner's display name. */
    public static final String ROW_OWNER = ".owner";
    /** Row field: the owner's UUID, as text. */
    public static final String ROW_OWNER_UUID = ".ownerUuid";
    /** Row field: the business type id. */
    public static final String ROW_TYPE = ".type";
    /** Row field: the status name: ACTIVE, SUSPENDED or CLOSED. */
    public static final String ROW_STATUS = ".status";
    /** Row field: the number of recorded employees. */
    public static final String ROW_EMPLOYEES = ".employees";
    /** Row field: the linked job id, or empty. */
    public static final String ROW_JOB = ".job";
    /** Row field: the linked job's display name, or empty. */
    public static final String ROW_JOB_NAME = ".jobName";
    /** The true number of online players the owner and employee pickers can offer. */
    public static final String ONLINE_COUNT = "online";
    /** Prefix of one online player name: {@code online.<n>}. */
    public static final String ONLINE_PREFIX = "online.";

    // --------------------------------------------------------- BIZ_INFO block

    /** Detail: the stable business id. */
    public static final String ID = "id";
    /** Detail: the trading name. */
    public static final String NAME = "name";
    /** Detail: the owner's display name. */
    public static final String OWNER = "owner";
    /** Detail: the owner's UUID, as text. */
    public static final String OWNER_UUID = "ownerUuid";
    /** Detail: the business type id. */
    public static final String TYPE = "bizType";
    /** Detail: the status name. */
    public static final String STATUS = "status";
    /** Detail: epoch milliseconds the business was registered. */
    public static final String CREATED_AT = "createdAt";
    /** Detail: the registering official's display name, or empty. */
    public static final String CREATED_BY = "createdBy";
    /** Detail: the linked job id, or empty. */
    public static final String JOB = "job";
    /** Detail: the linked job's display name, or empty. */
    public static final String JOB_NAME = "jobName";
    /** Detail: the suspend reason, or empty. */
    public static final String SUSPEND_REASON = "suspendReason";
    /** Detail: epoch milliseconds of the suspension, {@code 0} when never. */
    public static final String SUSPENDED_AT = "suspendedAt";
    /** Detail: the suspending official's display name, or empty. */
    public static final String SUSPENDED_BY = "suspendedBy";
    /** Detail: free notes on the business, in full. */
    public static final String NOTES = "notes";
    /** Detail flag: the viewer may edit this business (owner or {@code business.manage}). */
    public static final String CAN_EDIT_THIS = "canEditThis";
    /** Detail flag: the viewer holds {@code business.manage}. */
    public static final String CAN_MANAGE_THIS = "canManageThis";
    /** Detail flag: the viewer may record a physical payment ({@code business.manage}). */
    public static final String CAN_RECORD_PAYROLL_THIS = "canRecordPayroll";

    /** Detail: the true number of employees. */
    public static final String EMPLOYEE_COUNT = "employees";
    /** Prefix of one employee: {@code employee.<n>.name}. */
    public static final String EMPLOYEE_PREFIX = "employee.";
    /** Employee field: the last known name. */
    public static final String EMPLOYEE_NAME = ".name";
    /** Employee field: the UUID, as text. */
    public static final String EMPLOYEE_UUID = ".uuid";
    /** Employee field: the role id. */
    public static final String EMPLOYEE_ROLE = ".role";
    /** Employee field: epoch milliseconds the employee joined. */
    public static final String EMPLOYEE_SINCE = ".since";
    /** Employee field: {@code true} or {@code false}, right now. */
    public static final String EMPLOYEE_ONLINE = ".online";

    // ------------------------------------------------------ PAYROLL_INFO block

    /** Payroll: the recorded rate in gold ingots per in-game day. */
    public static final String PAYROLL_RATE = "payrollRatePerDay";
    /** Payroll: the pay schedule note. */
    public static final String PAYROLL_SCHEDULE = "payrollSchedule";
    /** Payroll: epoch milliseconds of the last recorded payment, {@code 0} when none. */
    public static final String PAYROLL_LAST_AT = "payrollLastPaidAt";
    /** Payroll: the official who recorded the last payment, or empty. */
    public static final String PAYROLL_LAST_BY = "payrollLastPaidBy";
    /** Payroll: the amount of the last recorded payment in gold ingots. */
    public static final String PAYROLL_LAST_AMOUNT = "payrollLastAmount";
    /** Payroll: the note recorded with the last payment. */
    public static final String PAYROLL_LAST_NOTE = "payrollLastNote";
    /** Payroll: the number of payments recorded so far. Never a balance. */
    public static final String PAYROLL_COUNT = "payrollPayments";

    // ------------------------------------------------------ POSITION_LIST block

    /** The true number of positions on the business, including the CEO root. */
    public static final String POSITION_COUNT = "positions";
    /** Prefix of one position row: {@code position.<n>.id}. */
    public static final String POSITION_PREFIX = "position.";
    /** Position row field: the stable position id. */
    public static final String POSITION_ROW_ID = ".id";
    /** Position row field: the displayed title. */
    public static final String POSITION_ROW_TITLE = ".title";
    /** Position row field: the parent position id, or empty for the root. */
    public static final String POSITION_ROW_PARENT = ".parent";
    /** Position row field: the wage in gold ingots per real 24 hours. */
    public static final String POSITION_ROW_WAGE = ".wage";
    /** Position row field: the display order among siblings. */
    public static final String POSITION_ROW_ORDER = ".order";
    /** Position row field: the CSV permission list. */
    public static final String POSITION_ROW_PERMISSIONS = ".permissions";
    /** Position row field: {@code true} only for the fixed CEO root. */
    public static final String POSITION_ROW_SYSTEM = ".system";
    /** Position row field: the depth from the root, for tree indentation. */
    public static final String POSITION_ROW_DEPTH = ".depth";
    /** Position row field: {@code true} when the viewer may edit, move or delete this position. */
    public static final String POSITION_ROW_CAN_EDIT = ".canEdit";

    // --------------------------------------------------------- OFFER_LIST block

    /** The true number of open offers on the business or for the viewer. */
    public static final String OFFER_COUNT = "offers";
    /** Prefix of one offer row: {@code offer.<n>.id}. */
    public static final String OFFER_PREFIX = "offer.";
    /** Offer row field: the stable contract id of the pending offer. */
    public static final String OFFER_ROW_ID = ".id";
    /** Offer row field: the position id. */
    public static final String OFFER_ROW_POSITION = ".position";
    /** Offer row field: the employee's display name. */
    public static final String OFFER_ROW_EMPLOYEE = ".employee";
    /** Offer row field: the employee's UUID, as text. */
    public static final String OFFER_ROW_EMPLOYEE_UUID = ".employeeUuid";
    /** Offer row field: the offered wage in gold ingots per real 24 hours. */
    public static final String OFFER_ROW_WAGE = ".wage";
    /** Offer row field: {@code true} for a one-time task offer. */
    public static final String OFFER_ROW_ONE_TIME = ".oneTime";
    /** Offer row field: the offer status, normally {@code OFFERED}. */
    public static final String OFFER_ROW_STATUS = ".status";
    /** Offer row field: epoch milliseconds the offer was sent. */
    public static final String OFFER_ROW_OFFERED_AT = ".offeredAt";

    // ------------------------------------------------------ CONTRACT_LIST block

    /** The true number of contracts on the business or for the viewer. */
    public static final String CONTRACT_COUNT = "contracts";
    /** Prefix of one contract row: {@code contract.<n>.id}. */
    public static final String CONTRACT_PREFIX = "contract.";
    /** Contract row field: the stable contract id. */
    public static final String CONTRACT_ROW_ID = ".id";
    /** Contract row field: the position id. */
    public static final String CONTRACT_ROW_POSITION = ".position";
    /** Contract row field: the employee's display name. */
    public static final String CONTRACT_ROW_EMPLOYEE = ".employee";
    /** Contract row field: the employee's UUID, as text. */
    public static final String CONTRACT_ROW_EMPLOYEE_UUID = ".employeeUuid";
    /** Contract row field: the wage in gold ingots per real 24 hours. */
    public static final String CONTRACT_ROW_WAGE = ".wage";
    /** Contract row field: {@code true} for a one-time task contract. */
    public static final String CONTRACT_ROW_ONE_TIME = ".oneTime";
    /** Contract row field: the lifecycle status. */
    public static final String CONTRACT_ROW_STATUS = ".status";
    /** Contract row field: epoch milliseconds the contract started. */
    public static final String CONTRACT_ROW_STARTED_AT = ".startedAt";
    /** Contract row field: total recorded wages in gold ingots. Never a balance. */
    public static final String CONTRACT_ROW_PAID_TOTAL = ".paidTotal";

    // ----------------------------------------------------- COMPLAINT_LIST block

    /** The true number of complaints recorded for the business or one employee. */
    public static final String COMPLAINT_COUNT = "complaints";
    /** Prefix of one complaint row: {@code complaint.<n>.id}. */
    public static final String COMPLAINT_PREFIX = "complaint.";
    /** Complaint row field: the stable complaint id. */
    public static final String COMPLAINT_ROW_ID = ".id";
    /** Complaint row field: the employee's display name. */
    public static final String COMPLAINT_ROW_EMPLOYEE = ".employee";
    /** Complaint row field: the employee's UUID, as text. */
    public static final String COMPLAINT_ROW_EMPLOYEE_UUID = ".employeeUuid";
    /** Complaint row field: the stated reason. */
    public static final String COMPLAINT_ROW_REASON = ".reason";
    /** Complaint row field: epoch milliseconds the complaint was filed. */
    public static final String COMPLAINT_ROW_AT = ".at";
    /** Complaint row field: {@code true} once upheld. */
    public static final String COMPLAINT_ROW_UPHELD = ".upheld";
    /** Complaint row field: the fine id created by an upheld complaint, or empty. */
    public static final String COMPLAINT_ROW_FINE = ".fine";

    // ---------------------------------------------------------- close request

    /** Detail: {@code true} when a soft-close request is waiting for government approval. */
    public static final String CLOSE_PENDING = "closePending";
    /** Detail: the requester's display name, or empty. */
    public static final String CLOSE_REQUESTED_BY = "closeRequestedBy";
    /** Detail: the requester's UUID, as text, or empty. */
    public static final String CLOSE_REQUESTED_BY_UUID = "closeRequestedByUuid";
    /** Detail: epoch milliseconds the soft-close request was filed, {@code 0} when none. */
    public static final String CLOSE_REQUESTED_AT = "closeRequestedAt";
    /** Detail: the reason stated on the request or decision, or empty. */
    public static final String CLOSE_REASON = "closeReason";
    /** Detail: the last decision id, either {@code approve} or {@code reject}, or empty. */
    public static final String CLOSE_DECISION = "closeDecision";

    // --------------------------------------------------------- HISTORY_LIST block

    /** The true number of history rows the player has, at most {@link #MAX_HISTORY_PER_PLAYER}. */
    public static final String HISTORY_COUNT = "historyCount";
    /** Prefix of one history row: {@code history.<n>.kind}. */
    public static final String HISTORY_PREFIX = "history.";
    /** History row field: the stable {@code HistoryKind} id. */
    public static final String HISTORY_ROW_KIND = ".kind";
    /** History row field: the one-line description. */
    public static final String HISTORY_ROW_TEXT = ".text";
    /** History row field: the actor's display name, or empty for a system event. */
    public static final String HISTORY_ROW_ACTOR = ".actor";
    /** History row field: the actor's UUID, as text, or empty. */
    public static final String HISTORY_ROW_ACTOR_UUID = ".actorUuid";
    /** History row field: epoch milliseconds of the event. */
    public static final String HISTORY_ROW_AT = ".at";
    /** History row field: the short reference id, or empty. */
    public static final String HISTORY_ROW_REF = ".ref";


    private BusinessKeysBlocks() {
    }
}
