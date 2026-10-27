package com.horizonssmp.gov.server.economy;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import com.horizonssmp.gov.common.OpCode;
import com.horizonssmp.gov.common.enums.AccountStatus;
import com.horizonssmp.gov.common.enums.LedgerKind;
import com.horizonssmp.gov.common.economy.EconomyResult;
import com.horizonssmp.gov.common.records.AccountRecord;
import com.horizonssmp.gov.common.records.LedgerEntry;
import com.horizonssmp.gov.server.GovSavedData;

/**
 * The account ledger: typed reads, the balance primitives and the transaction-envelope helpers.
 *
 * <p>This service owns the mechanics of moving gold between accounts. It deliberately does not own
 * authorization, region checks, inventory, command parsing, GUI wiring, the store, contracts or
 * payroll; a handler resolves an actor and an account, checks the capability cell, then calls one
 * method here. Every method is headless: there is no {@code ServerPlayer}, no level and no item
 * stack in this file, which is what lets the same rules run in a startup check and in a future
 * asynchronous caller.
 *
 * <p>Implementation is split across package-private helpers ({@link AccountReads},
 * {@link AccountMutations}, {@link AccountConservation}, {@link AccountEnvelope},
 * {@link AccountUtil}); this class keeps the public API and delegates.
 */
public final class AccountService {

    /** Prefix of a player account id: {@code player:<uuid>}. */
    public static final String PLAYER_PREFIX = "player:";

    /** Prefix of a business account id: {@code business:<businessId>}. */
    public static final String BUSINESS_PREFIX = "business:";

    /** Number of ledger rows in one history page, fixed by the specification. */
    public static final int HISTORY_PAGE_SIZE = 20;

    /** Shortest reason accepted for a Government or Police debit. */
    public static final int MIN_REASON_LENGTH = 3;

    /** Longest reason stored on a ledger line; section 6.5 of the specification. */
    public static final int MAX_REASON_LENGTH = 128;

    final GovSavedData data;
    final LongSupplier clock;
    final Map<UUID, ReentrantLock> actorLocks = new ConcurrentHashMap<>();
    final Map<String, ReentrantLock> businessLocks = new ConcurrentHashMap<>();
    final Map<AccountEnvelope.RateKey, AccountEnvelope.Bucket> economyBuckets = new ConcurrentHashMap<>();
    final ReentrantLock systemLock = new ReentrantLock();
    long rateCalls;

    public AccountService(final GovSavedData data) {
        this(data, System::currentTimeMillis);
    }

    public AccountService(final GovSavedData data, final LongSupplier clock) {
        if (data == null) {
            throw new IllegalArgumentException("AccountService requires a GovSavedData");
        }
        if (clock == null) {
            throw new IllegalArgumentException("AccountService requires a clock");
        }
        this.data = data;
        this.clock = clock;
    }

    public record Balance(String accountId, long balance, AccountStatus status) {
        public Balance {
            if (accountId == null || accountId.isBlank()) {
                throw new IllegalArgumentException("Balance requires an account id");
            }
            status = status == null ? AccountStatus.CLOSED : status;
        }
    }

    public record HistoryPage(List<LedgerEntry> rows, int total, int page, int pages) {
        public HistoryPage {
            rows = rows == null ? List.of() : List.copyOf(rows);
            total = Math.max(0, total);
            pages = Math.max(1, pages);
            page = Math.max(0, Math.min(page, pages - 1));
        }
    }

    public GovSavedData data() {
        return data;
    }

    public long now() {
        return clock.getAsLong();
    }

    public long nextSeq() {
        return data.nextLedgerSeq();
    }

    public static String playerAccountId(final UUID owner) {
        if (owner == null) {
            throw new IllegalArgumentException("playerAccountId requires a UUID");
        }
        return PLAYER_PREFIX + owner;
    }

    public static String businessAccountId(final String businessId) {
        if (businessId == null || businessId.isBlank()) {
            throw new IllegalArgumentException("businessAccountId requires a business id");
        }
        return BUSINESS_PREFIX + businessId.strip();
    }

    public AccountRecord lookup(final String accountId) { return AccountReads.lookup(this, accountId); }
    public AccountRecord constant(final String accountId) { return AccountReads.constant(this, accountId); }
    public AccountRecord player(final UUID owner) { return AccountReads.player(this, owner); }
    public AccountRecord business(final String businessId) { return AccountReads.business(this, businessId); }
    public AccountRecord openBusiness(final String businessId) { return AccountReads.openBusiness(this, businessId); }
    public AccountRecord government() { return AccountReads.government(this); }
    public AccountRecord police() { return AccountReads.police(this); }
    public Balance balance(final String accountId) { return AccountReads.balance(this, accountId); }
    public HistoryPage history(final String accountId, final int requestedPage) { return AccountReads.history(this, accountId, requestedPage); }

    public long sumBalances() { return AccountConservation.sumBalances(this); }
    public boolean conservationHolds() { return AccountConservation.conservationHolds(this); }
    public void assertConservation() { AccountConservation.assertConservation(this); }

    public EconomyResult credit(final String accountId, final long amount, final String reason) { return AccountMutations.credit(this, accountId, amount, reason); }
    public EconomyResult debit(final String accountId, final long amount, final String reason) { return AccountMutations.debit(this, accountId, amount, reason); }
    public EconomyResult depositCredit(final String accountId, final long gross, final long tax, final String txn, final UUID actor, final String reason) { return AccountMutations.depositCredit(this, accountId, gross, tax, txn, actor, reason); }
    public EconomyResult withdrawalDebit(final String accountId, final long gross, final long tax, final String txn, final UUID actor, final String reason) { return AccountMutations.withdrawalDebit(this, accountId, gross, tax, txn, actor, reason); }
    public EconomyResult transfer(final String fromId, final String toId, final long gross, final long tax, final String txn, final LedgerKind kind, final UUID actor, final String reason) { return AccountMutations.transfer(this, fromId, toId, gross, tax, txn, kind, actor, reason); }

    public EconomyResult takeEconomyToken(final UUID actor, final OpCode op) { return AccountEnvelope.takeEconomyToken(this, actor, op); }
    public EconomyResult acceptNonce(final UUID actor, final String nonce) { return AccountEnvelope.acceptNonce(this, actor, nonce); }
    public EconomyResult withActorLock(final UUID actor, final Supplier<EconomyResult> work) { return AccountEnvelope.withActorLock(this, actor, work); }
    public EconomyResult withBusinessLock(final String businessId, final Supplier<EconomyResult> work) { return AccountEnvelope.withBusinessLock(this, businessId, work); }
    public EconomyResult withLocks(final UUID actor, final String businessId, final Supplier<EconomyResult> work) { return AccountEnvelope.withLocks(this, actor, businessId, work); }
}