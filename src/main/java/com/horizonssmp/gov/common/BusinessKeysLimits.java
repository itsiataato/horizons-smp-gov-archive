package com.horizonssmp.gov.common;

public final class BusinessKeysLimits {


    /**
     * Most positions one business may hold, including the CEO root.
     *
     * <p>A tree of 64 is far more than any player-run shop needs, and it bounds the per-business
     * NBT row count the same way {@code MAX_EMPLOYEES} bounds the old staff list. A refused 65th
     * creation is a service refusal, never a silent truncation: silently dropping a position would
     * orphan the contracts that name it.
     */
    public static final int MAX_POSITIONS = 64;
    /** Most contracts one business may hold, including terminal history. */
    public static final int MAX_CONTRACTS = 256;
    /** Most complaints one business may hold. */
    public static final int MAX_COMPLAINTS = 256;
    /** Most history rows retained per player, newest first. */
    public static final int MAX_HISTORY_PER_PLAYER = 10;
    /**
     * Employment and history rows per dashboard page.
     *
     * <p>Six, not the registry's eight: a position row carries a title, a permission CSV and a
     * wage, and a history row carries a sentence, so the field block budget allows two fewer rows
     * than a plain business row.
     */
    public static final int EMPLOYMENT_PAGE_SIZE = 6;
    /**
     * Highest wage in gold ingots per real 24 hours that a <b>new</b> position or contract may
     * state: {@code 704}, the 64-gold per-run payout plus the 640-gold arrears ceiling.
     *
     * <p><b>WHY:</b> one payroll run pays at most {@code GovConfig.payrollPerPayout()} (default
     * 64) gross, and unpaid days accumulate up to {@code GovConfig.payrollArrearsCap()} (default
     * 640). A wage of 704 can therefore always be settled in full by one run plus its arrears
     * backlog, while a larger stated wage could never be paid. Legacy stored wages up to
     * {@link #MAX_WAGE_PER_DAY} still load; only new input is bounded here.
     */
    public static final long MAX_PAYABLE_WAGE = 704L;
    /**
     * Highest wage in gold ingots per real 24 hours that a position or contract may state.
     *
     * <p>Policy, not a statute: one million is a sanity ceiling for a player-entered long, not an
     * economic claim. A real wage is expected to be a handful of ingots; the bound exists so a
     * mistyped value cannot overflow the payroll arithmetic or make the NBT unreadable.
     */
    public static final long MAX_WAGE_PER_DAY = 1_000_000L;
    /**
     * Minimum time between two complaints by the same CEO against the same employee: 24 real hours.
     *
     * <p>Three upheld complaints end a contract and issue a 64-gold fine. Without a cooldown a
     * single angry click could reach that threshold in seconds, so the policy requires each
     * complaint to represent a separate day's conduct. The service enforces this; the constant is
     * here so the wire and the GUI can explain the wait.
     */
    public static final long COMPLAINT_COOLDOWN_MS = 24L * 60L * 60L * 1000L;

    /**
     * Business rows per {@code BIZ_LIST} page.
     *
     * <p>Set by the {@link GovFields} block budget, not by taste: a row carries a name, an owner
     * and a linked job name, and the answer also carries the online picker list. Eight rows fit
     * with room to spare; the ninth would push the block past {@link GovFields#MAX_ENCODED}, where
     * {@code encode} drops whole pairs (tip t46/t47).
     */
    public static final int PAGE_SIZE = 8;
    /** Most online names one {@code BIZ_LIST} answer carries for the owner and employee pickers. */
    public static final int MAX_LISTED_ONLINE = 32;
    /**
     * <b>Value and source.</b> 64 gold ingots per in-game day is the registry's upper bound on a
     * stated wage. Article 7.10 requires an employment agreement to state task, wage and payment
     * time but sets no figure, so the number is policy and is reported as policy, not as a
     * statutory quote.
     *
     * <p><b>Derivation.</b> 64 is the AGGRAVATED fine of Article 9.11, the highest single sum the
     * Code names. One in-game day's recorded wage can therefore never exceed the highest penalty
     * the same Code can impose, so the payroll note cannot become a wealth-transfer ledger under a
     * government name. It is also four times the 16-gold threshold above which Article 7.02
     * requires a written and witnessed contract, so every wage large enough to need a contract is
     * still representable.
     *
     * <p><b>Failure both ways.</b> Too high (4096, say) turns the note into an IOU ledger and
     * invites the mod to be used as a bank, which the locked-currency rule forbids. Too low (8,
     * the PETTY fine) refuses a full day's wage and pushes real payments off the record, which is
     * worse than a high number because it hides what actually happened.
     *
     * <p><b>Tolerance.</b> Exact and write-time only: no gold moves through this mod, and a value
     * over the bound is refused with a message asking for a corrected amount rather than silently
     * accepted or truncated. A payment over the bound is recorded as several payments.
     */
    public static final long MAX_PAYROLL_GOLD = 64L;
    /**
     * Most employees one {@code BIZ_INFO} answer carries. The true count is always sent, so a
     * longer roster reads as "showing 12 of 30" rather than a silently short list.
     */
    public static final int MAX_LISTED_EMPLOYEES = 12;

    private BusinessKeysLimits() {
    }
}
