package com.horizonssmp.gov.server.economy;

import java.util.List;

import com.horizonssmp.gov.common.EconomyKeys;
import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.economy.EconomyResult;
import com.horizonssmp.gov.common.records.AccountRecord;
import com.horizonssmp.gov.server.GovSavedData;

import net.minecraft.server.level.ServerPlayer;

/**
 * List-side implementation for {@link StoreService}. Holds the catalogue paging
 * and balance read so {@code StoreService} stays a thin public facade.
 * Behavior is identical to the previous in-class implementation.
 */
final class StoreListHelper {

    /** Store response key for the actor's balance; named literally by section 7.3. */
    private static final String KEY_STORE_BALANCE = "store.balance";

    /** Store response key for the served page. */
    private static final String KEY_STORE_PAGE = "store.page";

    /** Store response key for the true page count. */
    private static final String KEY_STORE_PAGES = "store.pages";

    /** Store response key for the true row count. */
    private static final String KEY_STORE_TOTAL = "store.total";

    /** Prefix of one catalogue row's field block. */
    private static final String KEY_STORE_ITEM_PREFIX = "store.item.";

    /** Row field: the canonical item id. Section 7.3 names it {@code id}, not {@code item}. */
    private static final String ROW_ID = "id";

    /** Row field: the sanitized display name. */
    private static final String ROW_NAME = "name";

    private StoreListHelper() {
    }

    static EconomyResult list(
            final GovSavedData data,
            final AccountService accounts,
            final StoreCatalog catalog,
            final ServerPlayer actor,
            final int requestedPage) {
        if (actor == null) {
            return EconomyResult.failure(ErrorCode.BAD_PAYLOAD, "An economy action requires an actor.");
        }
        final AccountRecord account = accounts.player(actor.getUUID());
        final long balance = account.balance();
        final List<StoreCatalog.Entry> entries = catalog.entries();
        final int total = entries.size();
        final int pages = Math.max(
                1, (total + StoreCatalog.PAGE_SIZE - 1) / StoreCatalog.PAGE_SIZE);
        final int page = Math.max(0, Math.min(requestedPage, pages - 1));

        EconomyResult result = EconomyResult.success()
                .with(KEY_STORE_BALANCE, Long.toString(balance))
                .with(KEY_STORE_PAGE, Integer.toString(page))
                .with(KEY_STORE_PAGES, Integer.toString(pages))
                .with(KEY_STORE_TOTAL, Integer.toString(total));

        final int start = page * StoreCatalog.PAGE_SIZE;
        final int end = Math.min(total, start + StoreCatalog.PAGE_SIZE);
        for (int index = start; index < end; index++) {
            final StoreCatalog.Entry entry = entries.get(index);
            final String prefix = KEY_STORE_ITEM_PREFIX + (index - start) + ".";
            result = result
                    .with(prefix + ROW_ID, entry.itemId())
                    .with(prefix + ROW_NAME, entry.displayName())
                    .with(prefix + EconomyKeys.KEY_TIER, Integer.toString(entry.tier()))
                    .with(prefix + EconomyKeys.KEY_PRICE, Integer.toString(entry.price()))
                    .with(prefix + EconomyKeys.KEY_STACK, Integer.toString(entry.stackSize()))
                    .with(prefix + EconomyKeys.KEY_CAN_BUY,
                            Boolean.toString(balance >= entry.price()));
        }
        return result;
    }
}