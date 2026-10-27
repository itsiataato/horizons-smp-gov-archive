package com.horizonssmp.gov.server.economy;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import com.horizonssmp.gov.common.EconomyKeys;
import com.horizonssmp.gov.common.EconomyText;
import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.GovCapability;
import com.horizonssmp.gov.common.GovCapabilities;
import com.horizonssmp.gov.common.OpCode;
import com.horizonssmp.gov.common.enums.AccountType;
import com.horizonssmp.gov.common.enums.BusinessPermission;
import com.horizonssmp.gov.common.enums.LedgerKind;
import com.horizonssmp.gov.common.economy.EconomyMath;
import com.horizonssmp.gov.common.economy.EconomyResult;
import com.horizonssmp.gov.common.records.AccountRecord;
import com.horizonssmp.gov.common.records.BusinessRecord;
import com.horizonssmp.gov.common.records.LedgerEntry;
import com.horizonssmp.gov.common.records.PlayerRecord;
import com.horizonssmp.gov.config.GovConfig;
import com.horizonssmp.gov.server.AuditService;
import com.horizonssmp.gov.server.GovSavedData;
import com.horizonssmp.gov.server.Sanitizer;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

/**
 * The bank service: the two operations that cross the physical vault boundary, the two
 * account-to-account movements, the read-only queries and the console-only crash remedy.
 *
 * <p>{@link #deposit(ServerPlayer, long, String)} and
 * {@link #withdraw(ServerPlayer, ServerPlayer, long, String)} move gold between an inventory and
 * an account. {@link #pay(ServerPlayer, ServerPlayer, long, String)} and
 * {@link #transfer(ServerPlayer, String, String, long, String, String)} are ledger-only: no item
 * moves, and the pair matrix fixes which accounts may pay which. {@link #balance(UUID, String,
 * boolean)} and {@link #history(UUID, String, int, boolean)} are pure reads. The store, payroll
 * and contracts are other services and are not reachable from here.
 *
 * <h2>Order of operations</h2>
 *
 * <p>Section 3.5 of {@code docs/ECONOMY-SPEC.md} fixes the order, and the account primitives in
 * {@link AccountService} are shaped around it:
 *
 * <ul>
 *   <li><b>Deposit</b> — prove the enabled flag, the bank-or-town-hall region, the amount and the
 *       nonce; take the economy token and the actor lock; precheck the tax, the net and both
 *       account caps; count the gold; record the nonce; remove exactly {@code gross} ingots
 *       all-or-nothing; book the account side with
 *       {@link AccountService#depositCredit(String, long, long, String, UUID, String)}; audit once.
 *       A booking failure restores the removed ingots and refuses the action, so a failed deposit
 *       never mints a balance.</li>
 *   <li><b>Withdrawal</b> — prove the enabled flag, the teller's effective
 *       {@code bank.manage}, the bank region, the target's presence, the amount and the nonce; take
 *       the teller and target locks in UUID order; build and validate a {@link TxnDraft}; prove the
 *       delivery fits; record the nonce; deliver exactly {@code net} ingots all-or-nothing; commit
 *       the draft; audit once. If the commit throws after delivery, the delivered ingots are
 *       removed again before the exception propagates.</li>
 *   <li><b>Pay</b> — prove the enabled flag, the online payee, the non-self rule, the amount and
 *       the nonce; take the economy token and both player locks; precheck both accounts and the
 *       tax recipient; record the nonce; book one taxed player-to-player line; audit once.</li>
 *   <li><b>Transfer</b> — prove the enabled flag, {@code bank.manage}, the bank region, the
 *       amount, the parsed account references, the pair matrix, the singleton reason rule and the
 *       nonce; take the economy token, the actor lock and the business lock; re-resolve the
 *       accounts, precheck status, funds and caps; record the nonce; book one line; audit once.</li>
 *   <li><b>Reads</b> — authorize the viewer, then read the stored record or page. A read never
 *       mutates the ledger; the only write is the documented lazy creation of the actor's own
 *       player account by a balance read.</li>
 *   <li><b>Console credit</b> — only a command source with permission level 4; create the
 *       target's account when needed, credit it without tax or a rate token, and write one system
 *       audit line. The ordinary account cap and status rules still apply.</li>
 * </ul>
 *
 * <p>The nonce is recorded after every stored precondition has been proved and immediately before
 * the mutation, which is the pipeline documented on {@link AccountService}. A duplicate nonce is
 * probed read-only at the start of each call so it cannot create an account, consume a rate token
 * or move an item, and {@link AccountService#acceptNonce(UUID, String)} still performs the
 * authoritative write under the lock.
 *
 * <h2>Failure is fail-closed</h2>
 *
 * <p>Gold may be destroyed by a crash between the item removal and the {@code gov.dat} write; it
 * must never be minted. That is why the deposit removes items before booking accounts and why a
 * failed booking restores them, and why a failed withdrawal commit removes the delivered net
 * before rethrowing rather than returning a neutral refusal over a real balance change.
 *
 * <h2>Audit</h2>
 *
 * <p>Every successful mutation writes exactly one audit line through the existing static
 * {@link AuditService#append}. Reads do not audit. This service owns no audit storage of its own.
 * A mutation returns a neutral, colourless {@link EconomyResult} whose fields carry the evidence
 * the caller needs: {@code account}, {@code balance}, {@code amount} (the gross), {@code tax},
 * {@code net}, {@code from} and {@code to}; a transfer also returns its sanitized {@code reason}.
 * A read returns the balance or the page fields instead.
 *
 * <p>Threading: every method is server-thread only, because the vault operations read a live
 * inventory and a live player position. The account locks defend against reentrancy and a future
 * asynchronous caller; they are not a substitute for the dispatcher's thread check.
 */
public final class BankService {

    public static final long MIN_DEPOSIT = 10L;

    public static final long MAX_DEPOSIT = 64L;

    public static final long MIN_WITHDRAW = 2L;

    public static final long MAX_WITHDRAW = 64L;

    public static final long MIN_PAY = 2L;

    public static final long MAX_PAY = 64L;

    public static final long MIN_TRANSFER = 2L;

    public static final long MAX_TRANSFER = 64L;

    static final String TXN_DEPOSIT = "bank.deposit";

    static final String TXN_WITHDRAW = "bank.withdraw";

    static final String TXN_PAY = "pay";

    static final String TXN_TRANSFER = "transfer";

    static final String DEPOSIT_REASON = "bank deposit";

    static final String WITHDRAW_REASON = "teller withdrawal";

    static final String KEY_PAGES = "pages";

    static final String KEY_TOTAL = "total";

    static final String ROW_PREFIX = "row.";

    static final String ROW_SEQ = "seq";
    static final String ROW_AT = "at";
    static final String ROW_TXN = "txn";
    static final String ROW_KIND = "kind";
    static final String ROW_GROSS = "gross";

    static final String ROW_PRIVATE = "private";

    static final String KEY_ACTOR = "actor";

    static final String KEY_ACTOR_NAME = "actorName";

    static final String KEY_ORIGIN = "origin";

    static final String ORIGIN_SYSTEM = "system";

    static final String ACTOR_REQUIRED = "An economy action requires an actor.";
    static final String NONCE_INVALID = "The request nonce is not valid.";
    static final String SINGLETON_REASON_REQUIRED =
            "A reason of at least " + AccountService.MIN_REASON_LENGTH
                    + " characters is required for a Government or Police debit.";
    static final String CONSOLE_REASON_REQUIRED =
            "A reason of at least " + AccountService.MIN_REASON_LENGTH
                    + " characters is required.";

    final GovSavedData data;
    final AccountService accounts;
    final VaultGateway vault;
    final BankRegions regions;

    public BankService(
            final GovSavedData data,
            final AccountService accounts,
            final VaultGateway vault,
            final BankRegions regions) {
        if (data == null) {
            throw new IllegalArgumentException("BankService requires a GovSavedData");
        }
        if (accounts == null) {
            throw new IllegalArgumentException("BankService requires an AccountService");
        }
        if (vault == null) {
            throw new IllegalArgumentException("BankService requires a VaultGateway");
        }
        if (regions == null) {
            throw new IllegalArgumentException("BankService requires BankRegions");
        }
        this.data = data;
        this.accounts = accounts;
        this.vault = vault;
        this.regions = regions;
    }

    // ------------------------------------------------------------------- gates

    public EconomyResult requireEnabled() {
        return GovConfig.bankEnabled()
                ? EconomyResult.success()
                : EconomyResult.failure(ErrorCode.ECONOMY_DISABLED);
    }

    public boolean checkManage(final ServerPlayer teller) {
        if (teller == null) {
            return false;
        }
        return GovCapabilities.has(
                GovCapabilities.effective(data.player(teller.getUUID()), data::job),
                GovCapability.BANK_MANAGE);
    }

    public long tax(final long gross) {
        return EconomyMath.tax(gross);
    }

    // ----------------------------------------------------------------- deposit

    public EconomyResult deposit(final ServerPlayer actor, final long gross, final String nonce) {
        return BankServiceDeposit.deposit(this, actor, gross, nonce);
    }

    // ---------------------------------------------------------------- withdraw

    public EconomyResult withdraw(
            final ServerPlayer teller,
            final ServerPlayer target,
            final long gross,
            final String nonce) {
        return BankServiceWithdraw.withdraw(this, teller, target, gross, nonce);
    }

    // -------------------------------------------------------------------- pay

    public EconomyResult pay(
            final ServerPlayer actor,
            final ServerPlayer target,
            final long gross,
            final String nonce) {
        return BankServicePay.pay(this, actor, target, gross, nonce);
    }

    // --------------------------------------------------------------- transfer

    public EconomyResult transfer(
            final ServerPlayer actor,
            final String fromId,
            final String toId,
            final long gross,
            final String reason,
            final String nonce) {
        return BankServiceTransfer.transfer(this, actor, fromId, toId, gross, reason, nonce);
    }

    // ------------------------------------------------------------------ reads

    public EconomyResult balance(
            final UUID actor, final String accountId, final boolean manage) {
        return BankServiceReads.balance(this, actor, accountId, manage);
    }

    public EconomyResult history(
            final UUID actor,
            final String accountId,
            final int page,
            final boolean manage) {
        return BankServiceReads.history(this, actor, accountId, page, manage);
    }

    public boolean canRead(
            final UUID actor, final String accountId, final boolean manage) {
        return BankServiceReads.canRead(this, actor, accountId, manage);
    }

    public boolean businessControlledBy(final String businessId, final UUID actor) {
        return BankServiceReads.businessControlledBy(this, businessId, actor);
    }

    // ---------------------------------------------------------- console credit

    public EconomyResult consoleCredit(
            final ServerPlayer source,
            final UUID target,
            final long amount,
            final String reason) {
        return BankServiceConsole.consoleCredit(this, source, target, amount, reason);
    }
}
