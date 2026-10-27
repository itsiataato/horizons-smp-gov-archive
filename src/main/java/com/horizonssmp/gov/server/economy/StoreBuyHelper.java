package com.horizonssmp.gov.server.economy;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.horizonssmp.gov.common.EconomyKeys;
import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.OpCode;
import com.horizonssmp.gov.common.enums.LedgerKind;
import com.horizonssmp.gov.common.economy.EconomyMath;
import com.horizonssmp.gov.common.economy.EconomyResult;
import com.horizonssmp.gov.common.records.AccountRecord;
import com.horizonssmp.gov.common.records.LedgerEntry;
import com.horizonssmp.gov.server.AuditService;
import com.horizonssmp.gov.server.GovSavedData;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Buy-side implementation for {@link StoreService}. Holds the validation,
 * delivery and commit pipeline so {@code StoreService} stays a thin public
 * facade. Behavior is identical to the previous in-class implementation.
 */
final class StoreBuyHelper {

    /** Success field: the total price paid, in gold ingots. */
    private static final String KEY_TOTAL = "total";

    /** Ledger label of a store purchase. */
    private static final String TXN_STORE = "store.buy";

    /** Ledger and audit reason of a store purchase. */
    private static final String STORE_REASON = "store purchase";

    /** Neutral success line; no exclamation mark and no colour code, like every economy text. */
    private static final String PURCHASE_FORMAT =
            "Bought %,d %s for %,d gold ingots. Balance %,d.";

    private static final String ACTOR_REQUIRED = "An economy action requires an actor.";
    private static final String ITEM_REQUIRED = "A store purchase requires an item id.";
    private static final String NONCE_INVALID = "The request nonce is not valid.";

    private StoreBuyHelper() {
    }

    static EconomyResult buy(
            final GovSavedData data,
            final AccountService accounts,
            final StoreCatalog catalog,
            final ItemDelivery delivery,
            final ServerPlayer actor,
            final String itemId,
            final int stacks,
            final String nonce) {
        if (actor == null) {
            return EconomyResult.failure(ErrorCode.BAD_PAYLOAD, ACTOR_REQUIRED);
        }
        if (itemId == null || itemId.isBlank()) {
            return EconomyResult.failure(ErrorCode.BAD_PAYLOAD, ITEM_REQUIRED);
        }
        final String id = itemId.strip();
        if (catalog.entry(id) == null) {
            return EconomyResult.failure(ErrorCode.STORE_UNAVAILABLE);
        }
        if (stacks < StoreCatalog.MIN_STACKS || stacks > StoreCatalog.MAX_STACKS) {
            return EconomyResult.failure(ErrorCode.BAD_PAYLOAD);
        }
        if (!EconomyMath.validNonce(nonce)) {
            return EconomyResult.failure(ErrorCode.BAD_PAYLOAD, NONCE_INVALID);
        }

        final UUID actorId = actor.getUUID();
        if (data.economyMeta().hasNonce(actorId, nonce)) {
            return EconomyResult.failure(ErrorCode.DUPLICATE_REQUEST);
        }

        final EconomyResult token = accounts.takeEconomyToken(actorId, OpCode.STORE_BUY);
        if (token.failed()) {
            return token;
        }
        return accounts.withActorLock(actorId, () -> buyLocked(
                data, accounts, catalog, delivery, actor, id, stacks, nonce));
    }

    private static EconomyResult buyLocked(
            final GovSavedData data,
            final AccountService accounts,
            final StoreCatalog catalog,
            final ItemDelivery delivery,
            final ServerPlayer actor,
            final String itemId,
            final int stacks,
            final String nonce) {
        final UUID actorId = actor.getUUID();

        // Server-side revalidation. The entry resolved here, not the one the caller saw, fixes the
        // price; the catalogue is immutable, so this only proves the row still exists.
        final StoreCatalog.Entry entry = catalog.entry(itemId);
        if (entry == null) {
            return EconomyResult.failure(ErrorCode.STORE_UNAVAILABLE);
        }
        if (stacks < StoreCatalog.MIN_STACKS || stacks > StoreCatalog.MAX_STACKS) {
            return EconomyResult.failure(ErrorCode.BAD_PAYLOAD);
        }
        final int itemCount;
        final long total;
        try {
            itemCount = Math.multiplyExact(entry.stackSize(), stacks);
            total = Math.multiplyExact(entry.price(), (long) stacks);
        } catch (final ArithmeticException overflow) {
            return EconomyResult.failure(ErrorCode.LIMIT_EXCEEDED);
        }
        if (total < 1L || total > EconomyMath.MAX_BALANCE) {
            return EconomyResult.failure(ErrorCode.LIMIT_EXCEEDED);
        }

        final String accountId = AccountService.playerAccountId(actorId);
        final AccountRecord account = accounts.player(actorId);
        if (!account.acceptsDebit()) {
            return EconomyResult.failure(ErrorCode.ACCOUNT_FROZEN);
        }
        if (account.balance() < total) {
            return EconomyResult.failure(ErrorCode.INSUFFICIENT_FUNDS);
        }

        final ItemStack template = catalog.template(entry);
        if (template.isEmpty()) {
            return EconomyResult.failure(ErrorCode.STORE_UNAVAILABLE);
        }
        if (!delivery.canAccept(actor, itemCount, entry.stackSize())) {
            return EconomyResult.failure(ErrorCode.INVENTORY_FULL);
        }

        // Seed the revenue account before validation so a missing singleton is created here, not
        // discovered halfway through a commit.
        accounts.government();
        final TxnDraft draft = new TxnDraft(new LedgerEntry(
                0L,
                accounts.now(),
                TXN_STORE,
                LedgerKind.STORE,
                accountId,
                GovSavedData.GOVERNMENT_ACCOUNT_ID,
                total,
                0L,
                actorId,
                STORE_REASON))
                .debit(accountId, total, STORE_REASON)
                .credit(GovSavedData.GOVERNMENT_ACCOUNT_ID, total, STORE_REASON);

        final EconomyResult validated = draft.validate(accounts);
        if (validated.failed()) {
            return validated;
        }

        // The nonce is written only after every stored precondition is proved and immediately
        // before the physical delivery, as the AccountService pipeline requires.
        final EconomyResult accepted = accounts.acceptNonce(actorId, nonce);
        if (accepted.failed()) {
            return accepted;
        }

        if (!delivery.add(actor, template, stacks)) {
            // canAccept was an exact simulation of this placement; a false return means the
            // inventory changed out from under the lock. Nothing was booked, so refuse.
            return EconomyResult.failure(ErrorCode.INVENTORY_FULL);
        }

        try {
            draft.commit(accounts);
        } catch (final RuntimeException commitFailure) {
            if (!delivery.removeAdded(actor, template, stacks)) {
                throw new IllegalStateException(
                        "store purchase commit failed after delivering " + stacks + " stacks of "
                                + entry.itemId() + " to " + actorId
                                + "; the delivered items could not be removed and the server has"
                                + " to be reconciled",
                        commitFailure);
            }
            throw commitFailure;
        }

        // The commit is durable, so the rollback journal must be cleared; retaining it would be a
        // per-player memory leak and leave a stale journal that a later rollback could consume.
        delivery.complete(actor);

        final long balance = balanceOf(accounts, accountId);
        audit(data, actor, entry, stacks, total);
        final String message = String.format(
                Locale.ROOT, PURCHASE_FORMAT, itemCount, entry.displayName(), total, balance);
        return EconomyResult.success(message)
                .with(EconomyKeys.KEY_ACCOUNT, accountId)
                .with(EconomyKeys.KEY_BALANCE, Long.toString(balance))
                .with(EconomyKeys.KEY_ITEM, entry.itemId())
                .with(EconomyKeys.KEY_STACKS, Integer.toString(stacks))
                .with(KEY_TOTAL, Long.toString(total));
    }

    private static long balanceOf(final AccountService accounts, final String accountId) {
        final AccountService.Balance balance = accounts.balance(accountId);
        return balance == null ? 0L : balance.balance();
    }

    /**
     * Writes exactly one audit line for a completed purchase.
     */
    private static void audit(
            final GovSavedData data, final ServerPlayer actor, final StoreCatalog.Entry entry,
            final int stacks, final long total) {
        final Map<String, String> payload = Map.of(
                EconomyKeys.KEY_ACCOUNT, AccountService.playerAccountId(actor.getUUID()),
                EconomyKeys.KEY_ITEM, entry.itemId(),
                EconomyKeys.KEY_STACKS, Integer.toString(stacks),
                EconomyKeys.KEY_AMOUNT, Long.toString(total));
        AuditService.append(data, actor, OpCode.STORE_BUY.name(), null, payload, "");
    }
}