package com.horizonssmp.gov.server.economy;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * The physical side of a store purchase: the only place a bought stack enters a player's
 * inventory.
 *
 * <p>Section 7.3 of {@code docs/ECONOMY-SPEC.md} requires a purchase to deliver every bought stack
 * all-or-nothing, never to drop an item in the world, and to remove the delivered items again when
 * the account commit fails. This interface states those three obligations without exposing the
 * live {@code Inventory}; {@link LiveItemDelivery} is the server implementation and a test can
 * substitute a recorder.
 *
 * <h2>What a delivery may use</h2>
 *
 * <p>The 36 main inventory slots and the offhand only. Armour, the ender chest and the cursor are
 * never touched. A delivery never calls {@code Inventory#add}, {@code drop} or a container
 * insertion helper that could spill overflow into the world.
 *
 * <h2>Units</h2>
 *
 * <p>A store unit is one whole stack: {@code stackSize} items, the item's own
 * {@link ItemStack#getMaxStackSize()}. A caller buys {@code stacks} units, so the total item count
 * is {@code stackSize * stacks}. There are no partial stacks in this version of the store.
 *
 * <h2>Rollback</h2>
 *
 * <p>{@link #add} is all-or-nothing; if it returns {@code false} the inventory is unchanged. When
 * it returns {@code true} the exact placement is journalled by slot and count, and
 * {@link #removeAdded} undoes that placement. Rollback is by the journalled counts, never by an
 * item-type search, so a pre-existing stack of the same item is never consumed by a failed
 * purchase. At most one delivery per player is outstanding at a time; the service holds the actor
 * lock for the whole add/commit/rollback window.
 *
 * <h2>Threading</h2>
 *
 * <p>Server thread only. Every method reads or writes a live inventory and is not safe to call
 * from a network or configuration thread.
 */
public interface ItemDelivery {

    /**
     * Proves that a delivery of {@code itemCount} items would fit, without mutating anything.
     *
     * <p>The simulation is exact: only empty main and offhand slots are counted, each contributing
     * {@code stackSize} items. An existing stack of the same item is not topped up, because this
     * signature does not name the item and a top-up that matters would require one; the resulting
     * refusal is conservative, never optimistic.
     *
     * @param player the recipient, may be {@code null}
     * @param itemCount the total number of items to deliver, at least zero
     * @param stackSize the number of items in one delivered stack, at least one
     * @return true when {@link #add} with the same arguments would fit
     * @throws IllegalArgumentException when {@code itemCount} is negative or {@code stackSize} is
     *     not positive
     */
    boolean canAccept(ServerPlayer player, int itemCount, int stackSize);

    /**
     * Delivers {@code stacks} whole stacks into empty main or offhand slots.
     *
     * <p>All-or-nothing. The caller must run {@link #canAccept} first; {@code add} repeats the
     * proof so a direct call cannot overfill an inventory. A {@code false} return means no item was
     * placed. Nothing is ever dropped.
     *
     * @param player the recipient, may be {@code null}
     * @param template a plain item stack naming the item, never {@code null} or empty
     * @param stacks the number of whole stacks to deliver, at least one
     * @return true when every stack was placed
     * @throws IllegalArgumentException when the template is missing, the template is empty, or
     *     {@code stacks} is not positive
     */
    boolean add(ServerPlayer player, ItemStack template, int stacks);

    /**
     * Removes the items most recently delivered to this player, exactly as they were placed.
     *
     * <p>Used only when the account commit fails after a successful {@link #add}. The journal is
     * matched by item identity and total count, then every recorded slot is verified before a
     * single item is removed. If the inventory no longer holds the recorded placement, nothing is
     * removed and {@code false} is returned; the caller must treat that as a reconciliation error,
     * never as a neutral refusal.
     *
     * <p>No journal means no delivery to undo, so a defensive call on a fresh player returns
     * {@code false} without touching the inventory.
     *
     * @param player the recipient, may be {@code null}
     * @param template the same plain item stack passed to {@link #add}
     * @param stacks the same stack count passed to {@link #add}
     * @return true when the exact placement was removed, false when no matching journal exists or
     *     the inventory no longer matches it
     * @throws IllegalArgumentException when the template is missing, the template is empty, or
     *     {@code stacks} is not positive
     */
    boolean removeAdded(ServerPlayer player, ItemStack template, int stacks);

    /**
     * Marks the most recent delivery to this player as permanent after a successful commit.
     *
     * <p>Called exactly once after {@link #add} and a successful account commit. The delivery is
     * now permanent and any rollback journal must be discarded. A default no-op keeps test
     * recorders source-compatible.
     *
     * @param player the recipient, may be {@code null}
     */
    default void complete(final ServerPlayer player) {
    }
}
