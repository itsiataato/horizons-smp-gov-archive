package com.horizonssmp.gov.server.economy;

import java.util.ArrayList;
import java.util.List;

import com.horizonssmp.gov.common.enums.LedgerKind;
import com.horizonssmp.gov.common.economy.EconomyMath;
import com.horizonssmp.gov.common.economy.EconomyResult;
import com.horizonssmp.gov.common.records.LedgerEntry;
import com.horizonssmp.gov.server.GovSavedData;
import com.horizonssmp.gov.server.Sanitizer;

/**
 * One economy transaction, built and proved before any stored balance is touched.
 *
 * <p>A draft is an ordered list of debit and credit postings plus exactly one
 * {@link LedgerEntry} that describes the transaction as a whole. Postings are positive amounts:
 * a debit takes that many ingots from an account, a credit gives that many to an account. The
 * ledger entry records the gross, the tax and the two account sides; the tax share is always
 * credited to {@code government} by a separate posting, so the entry stays the single net line
 * the specification requires while the postings remain explicit.
 *
 * <p>The draft also carries an {@code externalDelta}: the change this transaction makes to
 * {@code totalDeposited - totalWithdrawn}. It is positive for a deposit or a system credit,
 * negative for a withdrawal or a system debit, and zero for every account-to-account transfer.
 * That is the only field that distinguishes a physical boundary movement from an internal one,
 * and it is what lets {@link #validate(AccountService)} prove conservation without knowing which
 * caller built the draft.
 *
 * <p><b>Lifecycle.</b> {@link #validate(AccountService)} reads the current accounts and returns a
 * typed refusal before anything is written. {@link #commit(AccountService)} requires a successful
 * validation, applies every account replacement, appends the one ledger line and advances the
 * economy counters. If the append throws, the pre-draft account records are put back before the
 * exception propagates. There is no I/O in either method and no second audit store: the caller's
 * handler writes the audit line from the result fields, as documented on {@link AccountService}.
 *
 * <p><b>Threading.</b> Validation and commit are separate calls by design, so the caller must
 * run both inside the actor and business locks supplied by {@link AccountService}. The server
 * thread is the only writer; the locks make a future asynchronous caller safe rather than turning
 * this class into a transaction manager.
 *
 * <p>All arithmetic is exact. An overflow while summing postings is a refusal, never a clamp.
 */
public final class TxnDraft {

    /** The marker that represents gold entering or leaving the world. Never an account id. */
    public static final String PHYSICAL = "physical";

    /**
     * One posting.
     *
     * @param accountId the stable account id, never {@link #PHYSICAL}
     * @param amount the positive number of gold ingots
     * @param reason the sanitized reason copied into the ledger line, bounded to
     *     {@link AccountService#MAX_REASON_LENGTH}
     */
    public record Line(String accountId, long amount, String reason) {

        public Line {
            if (accountId == null || accountId.isBlank()) {
                throw new IllegalArgumentException("TxnDraft.Line requires an account id");
            }
            if (amount <= 0L) {
                throw new IllegalArgumentException(
                        "TxnDraft.Line amount must be positive, got " + amount);
            }
            if (amount > EconomyMath.MAX_BALANCE) {
                throw new IllegalArgumentException(
                        "TxnDraft.Line amount exceeds " + EconomyMath.MAX_BALANCE + ", got " + amount);
            }
            accountId = accountId.strip();
            if (PHYSICAL.equals(accountId)) {
                throw new IllegalArgumentException(
                        "TxnDraft.Line cannot post to the physical marker");
            }
            reason = reason == null
                    ? ""
                    : Sanitizer.singleLine(reason, AccountService.MAX_REASON_LENGTH);
        }
    }

    private final LedgerEntry template;
    private final List<Line> debits = new ArrayList<>();
    private final List<Line> credits = new ArrayList<>();
    private long externalDelta;
    private boolean validated;

    /**
     * Builds an empty draft around one ledger line.
     *
     * <p>The line's sequence is a placeholder and is replaced at commit. Its {@code at} is kept
     * when positive; a non-positive value is replaced with the service clock at commit. The
     * transaction label and the reason are sanitized here, so a ledger line written by this class
     * can never contain a section sign or a control character.
     *
     * @param template the ledger line, with the sequence ignored
     * @throws IllegalArgumentException when the line is null, has no transaction label, has a
     *     non-positive gross, names the same side twice, or names {@code physical} twice
     */
    public TxnDraft(final LedgerEntry template) {
        if (template == null) {
            throw new IllegalArgumentException("TxnDraft requires a ledger template");
        }
        if (template.gross() <= 0L) {
            throw new IllegalArgumentException(
                    "TxnDraft gross must be positive, got " + template.gross());
        }
        if (template.gross() > EconomyMath.MAX_BALANCE) {
            throw new IllegalArgumentException(
                    "TxnDraft gross exceeds " + EconomyMath.MAX_BALANCE
                            + ", got " + template.gross());
        }
        final String from = template.from() == null ? "" : template.from().strip();
        final String to = template.to() == null ? "" : template.to().strip();
        if (from.isEmpty() || to.isEmpty()) {
            throw new IllegalArgumentException("TxnDraft requires two ledger sides");
        }
        if (from.equals(to)) {
            throw new IllegalArgumentException("TxnDraft from and to must differ");
        }
        if (PHYSICAL.equals(from) && PHYSICAL.equals(to)) {
            throw new IllegalArgumentException(
                    "TxnDraft cannot move gold from physical to physical");
        }
        final String txn = Sanitizer.singleLine(template.txn(), Sanitizer.MAX_ID);
        if (txn.isBlank()) {
            throw new IllegalArgumentException("TxnDraft requires a non-blank transaction label");
        }
        final String reason = Sanitizer.singleLine(template.reason(), AccountService.MAX_REASON_LENGTH);
        this.template = new LedgerEntry(
                0L,
                template.at(),
                txn,
                template.kind() == null ? LedgerKind.TRANSFER : template.kind(),
                from,
                to,
                template.gross(),
                template.tax(),
                template.actor(),
                reason);
    }

    /** The ledger template, with its sequence still a placeholder. */
    public LedgerEntry ledger() {
        return template;
    }

    /** The debit postings, in insertion order. */
    public List<Line> debits() {
        return List.copyOf(debits);
    }

    /** The credit postings, in insertion order. */
    public List<Line> credits() {
        return List.copyOf(credits);
    }

    /** The change this draft makes to {@code totalDeposited - totalWithdrawn}. */
    public long externalDelta() {
        return externalDelta;
    }

    /** Adds a debit posting. */
    public TxnDraft debit(final String accountId, final long amount, final String reason) {
        debits.add(new Line(accountId, amount, reason));
        validated = false;
        return this;
    }

    /** Adds a credit posting. */
    public TxnDraft credit(final String accountId, final long amount, final String reason) {
        credits.add(new Line(accountId, amount, reason));
        validated = false;
        return this;
    }

    /** Sets the change this draft makes to the physical conservation counters. */
    public TxnDraft externalDelta(final long delta) {
        externalDelta = delta;
        validated = false;
        return this;
    }

    /**
     * Proves every precondition without touching stored data.
     *
     * <p>The checks are, in order: the postings balance the ledger line, the two account sides
     * agree with the postings, every posting is on the correct side, every account exists and is
     * open for the direction of its posting, no balance goes below zero or above
     * {@link EconomyMath#MAX_BALANCE}, and the projected conservation counters remain valid.
     *
     * <p>A successful validation is remembered for {@link #commit(AccountService)}. Any later
     * {@link #debit}, {@link #credit} or {@link #externalDelta} invalidates it again.
     *
     * @return a success with no fields, or the first typed refusal
     */
    public EconomyResult validate(final AccountService service) {
        if (service == null) {
            throw new IllegalArgumentException("TxnDraft.validate requires an AccountService");
        }
        validated = false;
        final EconomyResult structure = TxnDraftValidator.validateStructure(this);
        if (!structure.ok()) {
            return structure;
        }
        final EconomyResult accounts = TxnDraftValidator.validateAccounts(this, service);
        if (!accounts.ok()) {
            return accounts;
        }
        final EconomyResult counters = TxnDraftValidator.validateCounters(this, service);
        if (!counters.ok()) {
            return counters;
        }
        validated = true;
        return EconomyResult.success();
    }

    /**
     * Applies a validated draft.
     *
     * <p>Every touched account is replaced once, with its new balance and the committed ledger
     * sequence as {@code lastSeq}. Then the one ledger line is appended and the economy counters
     * and sequence are advanced. If {@link GovSavedData#appendLedger(LedgerEntry)} refuses the
     * line, the pre-draft records are restored and the refusal is rethrown; the ledger itself
     * cannot have been partially written, because that method appends only after its own checks.
     *
     * <p>A failure after the append (for example a conservation assertion) is a programming error
     * or a damaged file and is not rolled back: the ledger is append-only and the correct remedy is
     * an operator repair, not silently rewriting history.
     *
     * @throws IllegalStateException when the draft was not validated, the ledger sequence is
     *     exhausted, or a validated account disappeared
     */
    public void commit(final AccountService service) {
        if (service == null) {
            throw new IllegalArgumentException("TxnDraft.commit requires an AccountService");
        }
        if (!validated) {
            throw new IllegalStateException(
                    "TxnDraft.commit requires a successful validate(AccountService) first");
        }
        TxnDraftCommitter.commit(this, service);
    }
}