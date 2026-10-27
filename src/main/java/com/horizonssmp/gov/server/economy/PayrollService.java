package com.horizonssmp.gov.server.economy;

import java.util.UUID;

import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.GovCapabilities;
import com.horizonssmp.gov.common.GovCapability;
import com.horizonssmp.gov.common.economy.EconomyResult;
import com.horizonssmp.gov.common.enums.AccountStatus;
import com.horizonssmp.gov.common.enums.BusinessPermission;
import com.horizonssmp.gov.common.records.AccountRecord;
import com.horizonssmp.gov.common.records.BusinessPositionRecord;
import com.horizonssmp.gov.common.records.BusinessRecord;
import com.horizonssmp.gov.common.records.EmploymentContractRecord;
import com.horizonssmp.gov.config.GovConfig;
import com.horizonssmp.gov.server.GovSavedData;
import com.horizonssmp.gov.server.Sanitizer;

import net.minecraft.server.level.ServerPlayer;

/**
 * Runs one payroll scope as a single all-or-nothing transaction.
 *
 * <p>A run has three possible scopes. {@code gov} pays every player whose primary job is
 * {@code govworker} or {@code govexecutive} from the Government account and needs
 * {@code gov.payroll}. {@code police} pays every player whose primary job is {@code police} from
 * the Police account and needs {@code police.payroll}. {@code business:<id>} pays the active
 * employment contracts of that business from its own account and needs the owner's seat or an
 * active position holding {@link BusinessPermission#PAYROLL}.
 *
 * <p>The day guard is the stored {@link PayrollRunRecord}, keyed by the scope and
 * {@code now / 86_400_000}. Government and Police employees have no per-position stamp in this
 * build, so the run record is the only guard for those scopes; a business contract's
 * {@code lastPaidAt} advances by one day per run and {@code paidTotal} accumulates, which is
 * the same stamp {@code BusinessEmploymentService} uses. The payout is the unpaid balance (wage owed
 * since {@code startedAt} minus {@code paidTotal}), capped by the arrears and per-payout ceilings,
 * so a missed run is carried forward instead of lost.
 *
 * <p>All destinations must already exist and accept a credit. The service deliberately does not
 * create an employee account: a creation made during prevalidation could not be undone if a later
 * destination refused the run, which would break the "no mutation on any failure" rule. The
 * Government, Police and business sources must likewise exist and be open for a debit.
 *
 * <p>Every mutator follows one order: validate the actor, the scope, the reason, the day bucket,
 * every destination and the total; build every replacement record and ledger line; store the
 * accounts, the business contract stamps and the ledger lines; restore the snapshots if a store
 * fails; then persist the run, mark the data dirty and append one audit line. A refusal before
 * the first store writes nothing. Server thread only.
 */
public final class PayrollService {

    /** The Government payroll scope. */
    public static final String SCOPE_GOV = "gov";

    /** The Police payroll scope. */
    public static final String SCOPE_POLICE = "police";

    /** The prefix of a business payroll scope. */
    public static final String SCOPE_BUSINESS_PREFIX = "business:";

    private static final long DAY_MILLIS = 86_400_000L;
    private static final int MIN_REASON = AccountService.MIN_REASON_LENGTH;
    private static final int MAX_REASON = AccountService.MAX_REASON_LENGTH;

    private static final String GOVERNMENT = GovSavedData.GOVERNMENT_ACCOUNT_ID;


    private static final String ACTOR_REQUIRED = "An economy action requires an actor.";
    private static final String SCOPE_INVALID = "That payroll scope is not valid.";
    private static final String REASON_INVALID = "The reason must be 3 to 128 characters.";
    private static final String DUPLICATE_RUN =
            "Payroll has already run for that scope today.";
    private static final String SOURCE_MISSING = "The payroll source account does not exist.";
    private static final String NO_GOV_DUE = "No Government employee is due payroll.";
    private static final String NO_POLICE_DUE = "No Police employee is due payroll.";
    private static final String NO_BUSINESS_DUE =
            "No active contract has a completed real day to pay.";
    private static final String BUSINESS_INACTIVE = "That business is not active.";

    private final GovSavedData data;
    private final AccountService accounts;

    /**
     * Creates the service around one data set and its account view.
     *
     * @param data the Government saved data, required
     * @param accounts the account service over the same data, required
     */
    public PayrollService(final GovSavedData data, final AccountService accounts) {
        if (data == null) {
            throw new IllegalArgumentException("PayrollService requires saved data");
        }
        if (accounts == null) {
            throw new IllegalArgumentException("PayrollService requires an AccountService");
        }
        this.data = data;
        this.accounts = accounts;
    }

    // -------------------------------------------------------------------- run

    /**
     * Runs one payroll scope for one day bucket.
     *
     * @param actor the player starting the run, required
     * @param scope {@code gov}, {@code police} or {@code business:<businessId>}
     * @param reason the stated reason; required and 3..128 characters for Government and Police,
     *     optional for a business
     * @param now the run time in epoch milliseconds
     * @return a neutral success with the run fields, or a typed refusal that changed nothing
     */
    public EconomyResult run(
            final ServerPlayer actor, final String scope, final String reason, final long now) {
        if (!GovConfig.bankEnabled()) {
            return EconomyResult.failure(ErrorCode.ECONOMY_DISABLED);
        }
        if (actor == null) {
            return EconomyResult.failure(ErrorCode.BAD_PAYLOAD, ACTOR_REQUIRED);
        }
        if (now < 0L) {
            return EconomyResult.failure(ErrorCode.BAD_PAYLOAD);
        }

        final String cleanScope = scope == null ? "" : scope.strip();
        if (!isKnownScope(cleanScope)) {
            return EconomyResult.failure(ErrorCode.BAD_PAYLOAD, SCOPE_INVALID);
        }

        final String submittedReason = Sanitizer.singleLine(reason, Sanitizer.MAX_TEXT);
        final boolean reasonRequired =
                SCOPE_GOV.equals(cleanScope) || SCOPE_POLICE.equals(cleanScope);
        if (reasonRequired
                && (submittedReason.length() < MIN_REASON
                        || submittedReason.length() > MAX_REASON)) {
            return EconomyResult.failure(ErrorCode.BAD_PAYLOAD, REASON_INVALID);
        }
        final String cleanReason = Sanitizer.singleLine(submittedReason, MAX_REASON);

        final long bucket = now / DAY_MILLIS;
        if (SCOPE_GOV.equals(cleanScope)) {
            if (!holds(actor, GovCapability.GOV_PAYROLL)) {
                return EconomyResult.failure(ErrorCode.FORBIDDEN);
            }
            if (data.payrollRun(cleanScope, bucket) != null) {
                return duplicateRun();
            }
            return runDepartment(actor, cleanScope, cleanReason, now, bucket);
        }
        if (SCOPE_POLICE.equals(cleanScope)) {
            if (!holds(actor, GovCapability.POLICE_PAYROLL)) {
                return EconomyResult.failure(ErrorCode.FORBIDDEN);
            }
            if (data.payrollRun(cleanScope, bucket) != null) {
                return duplicateRun();
            }
            return runDepartment(actor, cleanScope, cleanReason, now, bucket);
        }

        final String businessId = cleanScope.substring(SCOPE_BUSINESS_PREFIX.length()).strip();
        if (businessId.isEmpty()) {
            return EconomyResult.failure(ErrorCode.BAD_PAYLOAD, SCOPE_INVALID);
        }
        // Canonical scope for the duplicate-run key: "business:" plus the stripped id, never the
        // operator's spelling with internal whitespace.
        final String runScope = SCOPE_BUSINESS_PREFIX + businessId;
        final BusinessRecord business = data.business(businessId);
        if (business == null) {
            return EconomyResult.failure(ErrorCode.BAD_BUSINESS);
        }
        if (!canRunBusinessPayroll(business, actor.getUUID())) {
            return EconomyResult.failure(ErrorCode.FORBIDDEN);
        }
        if (!business.active()) {
            return EconomyResult.failure(ErrorCode.INVALID_STATE, BUSINESS_INACTIVE);
        }
        if (data.payrollRun(runScope, bucket) != null) {
            return duplicateRun();
        }
        final AccountRecord source = accounts.lookup(AccountService.businessAccountId(businessId));
        if (source == null) {
            return EconomyResult.failure(ErrorCode.BAD_TARGET, SOURCE_MISSING);
        }
        if (source.status() != AccountStatus.OPEN) {
            return EconomyResult.failure(ErrorCode.ACCOUNT_FROZEN);
        }

        final PayrollDraft draft = PayrollDraftBuilder.buildBusinessDraft(business, actor.getUUID(), now);
        if (draft.payments().isEmpty()) {
            return EconomyResult.failure(ErrorCode.INVALID_STATE, NO_BUSINESS_DUE);
        }
        final String ledgerReason = cleanReason.isEmpty()
                ? "business payroll " + businessId : cleanReason;
        final String auditReason = cleanReason.isEmpty()
                ? "Business payroll for " + businessId + "." : cleanReason;

        return PayrollCommitHelper.commit(
                data, accounts, actor, runScope, ledgerReason, auditReason, now, bucket,
                source, draft, business);
    }

    // --------------------------------------------------------------- builders

    private EconomyResult runDepartment(
            final ServerPlayer actor,
            final String scope,
            final String cleanReason,
            final long now,
            final long bucket) {
        final String sourceId = SCOPE_GOV.equals(scope) ? GOVERNMENT : GovSavedData.POLICE_ACCOUNT_ID;
        final AccountRecord source = accounts.lookup(sourceId);
        if (source == null) {
            return EconomyResult.failure(ErrorCode.BAD_TARGET, SOURCE_MISSING);
        }
        if (!source.acceptsDebit()) {
            return EconomyResult.failure(ErrorCode.ACCOUNT_FROZEN);
        }

        final PayrollDraft draft = PayrollDraftBuilder.buildDepartmentDraft(data, scope, actor.getUUID(), now);
        if (draft.payments().isEmpty()) {
            return EconomyResult.failure(
                    ErrorCode.INVALID_STATE,
                    SCOPE_GOV.equals(scope) ? NO_GOV_DUE : NO_POLICE_DUE);
        }
        return PayrollCommitHelper.commit(
                data, accounts, actor, scope, cleanReason, cleanReason, now, bucket, source, draft, null);
    }

    // ---------------------------------------------------------------- helpers

    /** True when the player's effective capabilities contain the required payroll grant. */
    private boolean holds(final ServerPlayer actor, final GovCapability required) {
        return GovCapabilities.has(
                GovCapabilities.effective(data.player(actor.getUUID()), data::job), required);
    }

    /**
     * True when the actor owns the business or holds an active position with PAYROLL. The owner's
     * fixed CEO seat and an active non-one-time contract are the only position authorities.
     */
    private static boolean canRunBusinessPayroll(
            final BusinessRecord business, final UUID actorId) {
        if (actorId == null) {
            return false;
        }
        if (actorId.equals(business.ownerUuid())) {
            return true;
        }
        final EmploymentContractRecord contract =
                business.standingContractFor(actorId);
        if (contract == null) {
            return false;
        }
        final BusinessPositionRecord position =
                business.position(contract.positionId());
        return position != null
                && (position.isCeo() || position.has(BusinessPermission.PAYROLL));
    }

    private static boolean isKnownScope(final String scope) {
        if (SCOPE_GOV.equals(scope) || SCOPE_POLICE.equals(scope)) {
            return true;
        }
        return scope.startsWith(SCOPE_BUSINESS_PREFIX)
                && scope.length() > SCOPE_BUSINESS_PREFIX.length();
    }

    private static EconomyResult duplicateRun() {
        return EconomyResult.failure(ErrorCode.INVALID_STATE, DUPLICATE_RUN);
    }
}