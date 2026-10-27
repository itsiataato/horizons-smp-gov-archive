package com.horizonssmp.gov.server.economy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * The live {@link ItemDelivery}: a real player's main inventory and offhand.
 *
 * <p>This class is the only store class that touches a {@code ServerPlayer}, an {@code ItemStack}
 * or an inventory. {@link StoreService} stays testable without a world, and the rules about what
 * may be sold stay in {@link StoreCatalog}.
 *
 * <h2>Placement</h2>
 *
 * <p>Only empty slots are used: the 36 main slots {@code 0..35} and the offhand slot
 * {@code 40}, in that order. Each delivered stack has {@code min(stackSize, remaining)} items, so a
 * partial final stack is possible only when the caller asked for a count that is not a whole
 * number of stacks; the store never does. Existing stacks are not merged into, which makes the
 * placement journal exact and makes {@link #canAccept} an exact simulation rather than an
 * optimistic count of general free space.
 *
 * <h2>Rollback journal</h2>
 *
 * <p>A successful {@link #add} records the slot index and count of every placed stack keyed by the
 * player's UUID. {@link #removeAdded} verifies the whole journal first and only then shrinks those
 * exact slots, so either the inventory is restored exactly or it is left untouched. A
 * pre-existing stack of the same item is never removed: rollback is by journal, not by item-type
 * search. The journal holds one delivery per player; the service's actor lock makes two
 * outstanding deliveries for one player impossible.
 *
 * <h2>Never drops</h2>
 *
 * <p>There is no call to {@code Inventory#add}, no {@code drop}, no container insertion and no
 * overflow path. An inventory without enough empty slots is refused before anything is placed.
 *
 * <h2>Threading</h2>
 *
 * <p>Server thread only. The journal map is concurrent so a future caller cannot corrupt it, but
 * the live {@code Inventory} itself still demands the server thread and the actor lock.
 */
public final class LiveItemDelivery implements ItemDelivery {

    /** Main slots {@code 0..35} and the offhand slot 40, the only slots a delivery may use. */
    private static final int[] DELIVERY_SLOTS = deliverySlots();

    /** One stack this delivery placed: the slot and the number of items put there. */
    private record Placed(int slot, int count) {
    }

    /** What one successful delivery added, for an exact rollback. */
    private record Receipt(ItemStack template, int total, List<Placed> placed) {

        private Receipt {
            placed = List.copyOf(placed);
        }
    }

    /** The most recent successful delivery per player, waiting for a commit or a rollback. */
    private final Map<UUID, Receipt> pending = new ConcurrentHashMap<>();

    private static int[] deliverySlots() {
        final int[] slots = new int[Inventory.INVENTORY_SIZE + 1];
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            slots[slot] = slot;
        }
        slots[Inventory.INVENTORY_SIZE] = Inventory.SLOT_OFFHAND;
        return slots;
    }

    @Override
    public boolean canAccept(
            final ServerPlayer player, final int itemCount, final int stackSize) {
        checkCount(itemCount);
        checkStackSize(stackSize);
        if (player == null) {
            return false;
        }
        if (itemCount == 0) {
            return true;
        }
        final Inventory inventory = player.getInventory();
        long capacity = 0L;
        for (final int slot : DELIVERY_SLOTS) {
            if (inventory.getItem(slot).isEmpty()) {
                capacity += stackSize;
                if (capacity >= itemCount) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean add(final ServerPlayer player, final ItemStack template, final int stacks) {
        checkTemplate(template);
        checkStacks(stacks);
        if (player == null) {
            return false;
        }
        final int stackSize = template.getMaxStackSize();
        final int total = Math.multiplyExact(stackSize, stacks);
        if (!canAccept(player, total, stackSize)) {
            return false;
        }

        final Inventory inventory = player.getInventory();
        final List<Placed> placed = new ArrayList<>(stacks);
        int remaining = total;
        for (final int slot : DELIVERY_SLOTS) {
            if (remaining == 0) {
                break;
            }
            if (!inventory.getItem(slot).isEmpty()) {
                continue;
            }
            final int count = Math.min(stackSize, remaining);
            inventory.setItem(slot, template.copyWithCount(count));
            placed.add(new Placed(slot, count));
            remaining -= count;
        }
        if (remaining != 0) {
            // Unreachable after the canAccept proof on the same thread, but a partial placement
            // must never survive: undo exactly what this call placed and report failure.
            removePlaced(inventory, template, placed);
            inventory.setChanged();
            return false;
        }
        inventory.setChanged();
        pending.put(player.getUUID(), new Receipt(template.copyWithCount(1), total, placed));
        return true;
    }

    @Override
    public boolean removeAdded(
            final ServerPlayer player, final ItemStack template, final int stacks) {
        checkTemplate(template);
        checkStacks(stacks);
        if (player == null) {
            return false;
        }
        final Receipt receipt = pending.get(player.getUUID());
        if (receipt == null) {
            return false;
        }
        final int total = Math.multiplyExact(template.getMaxStackSize(), stacks);
        if (receipt.total() != total
                || !ItemStack.isSameItemSameComponents(receipt.template(), template)) {
            return false;
        }

        final Inventory inventory = player.getInventory();
        for (final Placed placed : receipt.placed()) {
            final ItemStack current = inventory.getItem(placed.slot());
            if (current.isEmpty()
                    || !ItemStack.isSameItemSameComponents(current, template)
                    || current.getCount() < placed.count()) {
                // The exact placement is no longer there. Remove nothing rather than guess.
                return false;
            }
        }
        removePlaced(inventory, template, receipt.placed());
        inventory.setChanged();
        pending.remove(player.getUUID(), receipt);
        return true;
    }

    @Override
    public void complete(final ServerPlayer player) {
        if (player == null) {
            return;
        }
        // The receipt exists only for rollback between add and commit. Retaining it after a
        // successful commit is a per-player memory leak and leaves a stale journal that a later
        // removeAdded call could match against a much older delivery.
        pending.remove(player.getUUID());
    }

    /** Undoes one placement list. Callers prove every slot first. */
    private static void removePlaced(
            final Inventory inventory, final ItemStack template, final List<Placed> placed) {
        for (final Placed entry : placed) {
            final ItemStack current = inventory.getItem(entry.slot());
            if (current.isEmpty()
                    || !ItemStack.isSameItemSameComponents(current, template)) {
                continue;
            }
            current.shrink(entry.count());
            if (current.isEmpty()) {
                inventory.setItem(entry.slot(), ItemStack.EMPTY);
            }
        }
    }

    private static void checkTemplate(final ItemStack template) {
        if (template == null || template.isEmpty()) {
            throw new IllegalArgumentException(
                    "a store delivery requires a non-empty item template");
        }
    }

    private static void checkStacks(final int stacks) {
        if (stacks < 1) {
            throw new IllegalArgumentException(
                    "a store delivery requires at least one stack, got " + stacks);
        }
    }

    private static void checkCount(final int itemCount) {
        if (itemCount < 0) {
            throw new IllegalArgumentException(
                    "itemCount must be at least zero, got " + itemCount);
        }
    }

    private static void checkStackSize(final int stackSize) {
        if (stackSize < 1) {
            throw new IllegalArgumentException(
                    "stackSize must be positive, got " + stackSize);
        }
    }
}
