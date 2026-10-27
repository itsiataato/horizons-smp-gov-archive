package com.horizonssmp.gov.server.economy;

import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.economy.EconomyResult;
import com.horizonssmp.gov.server.GovSavedData;

import net.minecraft.server.level.ServerPlayer;

/**
 * The Government Store: a read-only catalogue page and the buy transaction.
 *
 * <p>Section 7 of {@code docs/ECONOMY-SPEC.md} defines a buy-only store. This service implements
 * exactly the two operations {@code STORE_LIST} and {@code STORE_BUY}; there is no sell tab, no
 * buyback, no search, no dynamic price and no stock limit. The catalogue and its price floors are
 * parsed once by {@link StoreCatalog}; a buy never reads the item registry, the creative menu or
 * the caller's claimed price. The only price is the catalogue's tier, and the caller sends only an
 * item id, a stack count and a nonce.
 *
 * <p>The implementation details live in {@link StoreListHelper} and {@link StoreBuyHelper}; this
 * class is the public facade and keeps the stable API. Behavior is unchanged.
 *
 * <h2>Threading</h2>
 *
 * <p>Server thread only. The catalogue is immutable and safely readable, but a buy reads a live
 * inventory; the actor lock serialises purchases for one player and is not a substitute for the
 * dispatcher's thread check.
 */
public final class StoreService {

    private final GovSavedData data;
    private final AccountService accounts;
    private final StoreCatalog catalog;
    private final ItemDelivery delivery;

    /**
     * A store service over one world.
     *
     * @param data the government data set, never {@code null}
     * @param accounts the account ledger, never {@code null}
     * @param catalog the parsed catalogue, never {@code null}
     * @param delivery the physical delivery gateway, never {@code null}
     */
    public StoreService(
            final GovSavedData data,
            final AccountService accounts,
            final StoreCatalog catalog,
            final ItemDelivery delivery) {
        if (data == null) {
            throw new IllegalArgumentException("StoreService requires a GovSavedData");
        }
        if (accounts == null) {
            throw new IllegalArgumentException("StoreService requires an AccountService");
        }
        if (catalog == null) {
            throw new IllegalArgumentException("StoreService requires a StoreCatalog");
        }
        if (delivery == null) {
            throw new IllegalArgumentException("StoreService requires an ItemDelivery");
        }
        this.data = data;
        this.accounts = accounts;
        this.catalog = catalog;
        this.delivery = delivery;
    }

    /**
     * The enabled flag, as a reusable refusal.
     */
    public EconomyResult requireEnabled() {
        return catalog.enabled()
                ? EconomyResult.success()
                : EconomyResult.failure(ErrorCode.ECONOMY_DISABLED);
    }

    /**
     * Reads one catalogue page and the actor's balance.
     */
    public EconomyResult list(final ServerPlayer actor, final int requestedPage) {
        return StoreListHelper.list(data, accounts, catalog, actor, requestedPage);
    }

    /**
     * Buys whole stacks of one listed item for the actor.
     */
    public EconomyResult buy(
            final ServerPlayer actor,
            final String itemId,
            final int stacks,
            final String nonce) {
        final EconomyResult enabled = requireEnabled();
        if (enabled.failed()) {
            return enabled;
        }
        return StoreBuyHelper.buy(data, accounts, catalog, delivery, actor, itemId, stacks, nonce);
    }
}