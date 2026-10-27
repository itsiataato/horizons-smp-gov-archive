package com.horizonssmp.gov.common;

import java.util.Locale;
import java.util.Optional;

/**
 * Every action the dashboard can ask the server to perform.
 *
 * <p>This is the single vocabulary of the mod. A future client screen, a future command bridge and
 * the audit trail all speak {@code OpCode}, so an action can never exist on one side only: adding a
 * constant here forces {@link GovCapabilities#requiredFor(OpCode)} to be updated as well, because
 * that method is an exhaustive switch with no default.
 *
 * <p>{@link #requiresPlayerTarget()} marks the actions whose target is a person. Those are the only
 * actions for which the dispatcher resolves a target before calling a handler; everything else
 * (a job id, a plate, a vehicle id, a fine id) is resolved inside the handler that owns that
 * record type. The flag exists so target resolution cannot be forgotten: a handler for an action
 * that needs a person is only reachable through the resolved target.
 *
 * <p>{@link #audited()} marks the actions that must leave an audit line. Reads are not: the audit
 * log records changes to government data, and an audit-page view that appended its own entry would
 * make the log describe itself. A read that is refused is logged at DEBUG like every other refusal,
 * so abuse is still visible without a line per tab open.
 *
 * <p>Most mutations are written by the dispatcher after a handler accepts them. A few write their
 * own line instead, because the evidence is richer than the generic payload summary: an export
 * line carries the path, size and sha256, a season line carries the counts, token window and
 * backup, and an economy line is written by the service that moved the gold.
 * {@link #dispatcherAudited()} is false for those, and the service appends the line;
 * {@link #audited()} stays true so a future reviewer still sees that the action is recorded.
 *
 * <p>Names are persisted verbatim in {@code gov.dat} audit entries. Do not rename constants without
 * a schema migration.
 */
public enum OpCode {

    OPEN_TAB(false, false),
    LOOKUP_PERSON(true, false),
    PERSON_SEARCH(false, false),
    NOTE_ADD(true),
    NOTE_EDIT(true),
    NOTE_DELETE(true),
    NOTE_LIST(false, false),
    NOTE_SCOPES(false, false),
    NOTE_SCOPED_LIST(false, false),
    NOTE_SCOPED_ADD(false),
    NOTE_SCOPED_EDIT(false),
    NOTE_SCOPED_DELETE(false),
    FINE_ISSUE(true),
    FINE_LIST(false, false),
    FINE_INFO(false, false),
    FINE_PAY(true),
    FINE_COLLECT(true),
    FINE_WAIVE(true),
    SUSPECT_SET(true),
    SUSPECT_CLEAR(true),
    WARRANT_ISSUE(true),
    JOB_LIST(false, false),
    JOB_INFO(false, false),
    JOB_CREATE(false),
    JOB_EDIT(false),
    JOB_DELETE(false),
    JOB_ASSIGN(true),
    BIZ_REGISTER(false),
    BIZ_LIST(false, false),
    BIZ_INFO(false, false),
    BIZ_TRANSFER(true),
    BIZ_SUSPEND(false),
    BIZ_REVOKE(false),
    BIZ_DELETE(true),
    BIZ_EMPLOYEE_ADD(true),
    BIZ_EMPLOYEE_REMOVE(true),
    BIZ_LINK(false),
    BIZ_PAYROLL_SET(false),
    BIZ_PAYROLL_RECORD(false),
    VEH_REGISTER(true),
    VEH_EDIT(true),
    VEH_IMPOUND(false),
    VEH_RELEASE(false),
    PLATE_SCAN(false),
    VEH_LIST(false, false),
    VEH_INFO(false, false),
    LICENSE_ISSUE(true),
    LICENSE_REVOKE(true),
    LICENSE_POINTS(true),
    WPN_REGISTER(true),
    WPN_TRANSFER(true),
    WPN_REVOKE(false),
    WPN_REPORT_LOSS(false),
    WPN_LIST(false, false),
    WPN_INFO(false, false),
    WPN_CLASSIFY(false, false),
    WPN_PERMIT_LIST(false, false),
    WPN_PERMIT_ISSUE(true),
    WPN_PERMIT_REVOKE(true),
    WPN_PERMIT_SUSPEND(true),
    AREA_PERMIT_LIST(false, false),
    AREA_PERMIT_CREATE(false),
    AREA_PERMIT_REVOKE(false),
    JAIL_COMMIT(true),
    JAIL_RELEASE(true),
    JAIL_LIST(false, false),
    JAIL_INFO(false, false),
    JAIL_EXTEND(true),
    JAIL_CELL(true),
    JAIL_CELL_CREATE(false),
    JAIL_CELL_DELETE(false),
    CUFF(true),
    UNCUFF(true),
    DUTY_TOGGLE(false),
    PAYROLL_INFO(false, false),
    AUDIT_PAGE(false, false),
    AUDIT_INFO(false, false),
    AUDIT_EXPORT(false),
    ADMIN_EXPORT(false),
    SEASON_STATUS(false, false),
    SEASON_BEGIN(false),
    SEASON_COMMIT(false),
    LAW_ARTICLE(false, false),
    BIZ_RENAME(false),
    BIZ_POSITION_LIST(false, false),
    BIZ_POSITION_CREATE(false),
    BIZ_POSITION_EDIT(false),
    BIZ_POSITION_DELETE(false),
    BIZ_POSITION_MOVE(false),
    BIZ_OFFER_LIST(false, false),
    BIZ_OFFER_SEND(true),
    BIZ_OFFER_RESPOND(false),
    BIZ_CONTRACT_LIST(false, false),
    BIZ_CONTRACT_END(false),
    BIZ_CONTRACT_PROMOTE(false),
    BIZ_PAYROLL_RUN(false),
    BIZ_COMPLAINT_LIST(false, false),
    BIZ_COMPLAINT_FILE(false),
    BIZ_CLOSE_REQUEST(false),
    HISTORY_LIST(false, false),
    BIZ_CLOSE_APPROVE(false),
    BIZ_CLOSE_REJECT(false),
    BANK_BALANCE(false, false),
    BANK_HISTORY(false, false),
    BANK_DEPOSIT(false),
    BANK_WITHDRAW(true),
    BANK_TRANSFER(false),
    BANK_CREDIT(false),
    PAY_PLAYER(true),
    PAYROLL_RUN(false),
    STORE_LIST(false, false),
    STORE_BUY(false),
    CONTRACT_LIST(false, false),
    CONTRACT_POST(true),
    CONTRACT_ACCEPT(false),
    CONTRACT_SUBMIT(false),
    CONTRACT_APPROVE(false),
    CONTRACT_VOID(false),
    CONTRACT_CREDIT(false),
    BOUNTY_PLACE(true),
    BOUNTY_LIST(false, false),
    BOUNTY_CANCEL(false);

    private final boolean requiresPlayerTarget;
    private final boolean audited;

    OpCode(final boolean requiresPlayerTarget) {
        this(requiresPlayerTarget, true);
    }

    OpCode(final boolean requiresPlayerTarget, final boolean audited) {
        this.requiresPlayerTarget = requiresPlayerTarget;
        this.audited = audited;
    }

    /** True when the dispatcher must resolve a person from the payload before calling a handler. */
    public boolean requiresPlayerTarget() {
        return requiresPlayerTarget;
    }

    /**
     * True when this action must leave an audit line at all.
     *
     * <p>False for reads. A refused ordinary action is not audited, read or mutation alike; a
     * safety-critical service may write its own refusal line, which is why
     * {@link #AUDIT_EXPORT}, {@link #ADMIN_EXPORT}, {@link #SEASON_BEGIN} and {@link #SEASON_COMMIT}
     * are audited by their service.
     */
    public boolean audited() {
        return audited;
    }

    /**
     * True when the dispatcher writes the audit line for an accepted action.
     *
     * <p>False for the service-audited actions: the two exports, the two season steps and the
     * economy mutations. The dispatcher must never write a second line for them: an export or
     * reset would then appear twice without its path or counts, and an economy movement would
     * appear twice without its account and tax fields.
     */
    public boolean dispatcherAudited() {
        return audited
                && this != AUDIT_EXPORT && this != ADMIN_EXPORT
                && this != SEASON_BEGIN && this != SEASON_COMMIT
                && this != BANK_DEPOSIT && this != BANK_WITHDRAW && this != BANK_TRANSFER
                && this != BANK_CREDIT
                && this != PAY_PLAYER && this != PAYROLL_RUN && this != STORE_BUY
                && this != CONTRACT_POST && this != CONTRACT_ACCEPT && this != CONTRACT_SUBMIT
                && this != CONTRACT_APPROVE && this != CONTRACT_VOID && this != CONTRACT_CREDIT;
    }

    /**
     * Looks up an op by its name, as a payload sends it.
     *
     * <p>The comparison is case-insensitive and trims surrounding whitespace, because the name
     * crosses a wire and may be edited by hand in a test. Unknown names return
     * {@link Optional#empty()} rather than throwing, so a request from a newer build is answered
     * with an {@code UNSUPPORTED} refusal instead of a decode failure. This is the tolerant-read
     * half of the forward-compatibility rule, and it is the only place an op name is parsed.
     */
    public static Optional<OpCode> byName(final String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        final String needle = name.trim().toUpperCase(Locale.ROOT);
        for (final OpCode op : values()) {
            if (op.name().equals(needle)) {
                return Optional.of(op);
            }
        }
        return Optional.empty();
    }
}
