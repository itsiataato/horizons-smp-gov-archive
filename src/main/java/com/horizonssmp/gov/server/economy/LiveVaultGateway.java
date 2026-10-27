package com.horizonssmp.gov.server.economy;

import java.util.UUID;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The live {@link VaultGateway}: a real player's main inventory and offhand.
 *
 * <p>This is the only class in the vault path that touches a {@code ServerPlayer}, an
 * {@code ItemStack} or an inventory. The account and transaction services stay headless so their
 * rules can be proved without a client; this class is where the physical claim is made.
 *
 * <h2>What counts as a vault slot</h2>
 *
 * <p>The 36 main inventory slots plus the offhand: {@link Inventory#INVENTORY_SIZE} slots at
 * indexes {@code 0..35} and {@link Inventory#SLOT_OFFHAND}. Armour is excluded even though
 * {@code Inventory} exposes it in the same container, and the ender chest is never consulted. Only
 * {@code minecraft:gold_ingot} is gold; a nugget, a block or any other item is not counted, is not
 * removed and is never created. A gold ingot keeps its identity when it carries components (a
 * custom name, for example), so such a stack is still counted and removed like any other; the walk
 * is by slot index, and components never change what a stack is worth.
 *
 * <h2>Removal</h2>
 *
 * <p>Removal proves {@code countGold >= amount} before it touches a single slot, then walks the
 * slots in index order and shrinks each gold stack, clearing a stack that reaches zero. There is
 * no step after the proof that can fail, so a false return always means the inventory is unchanged.
 * Nothing is ever dropped into the world.
 *
 * <h2>Delivery</h2>
 *
 * <p>Delivery first tops up existing gold stacks, then fills empty vault slots with stacks of the
 * item's maximum size. {@link #canAccept} simulates exactly that placement without mutating
 * anything. If placement nevertheless comes up short, the ingots placed by this call are removed
 * again before {@code false} is returned; the physical world is left as it was.
 *
 * <h2>Threading</h2>
 *
 * <p>Server thread only. Every method reads or writes a live inventory; it is not safe to call
 * from a config thread, a network thread or a future asynchronous caller.
 */
public final class LiveVaultGateway implements VaultGateway {

    /** Main slots {@code 0..35} and the offhand slot 40, the only slots the vault may use. */
    private static final int[] VAULT_SLOTS = vaultSlots();

    private final MinecraftServer server;

    private LiveVaultGateway(final MinecraftServer server) {
        this.server = server;
    }

    /** The gateway for a running server. */
    public static VaultGateway live(final MinecraftServer server) {
        return new LiveVaultGateway(server);
    }

    private static int[] vaultSlots() {
        final int[] slots = new int[Inventory.INVENTORY_SIZE + 1];
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            slots[slot] = slot;
        }
        slots[Inventory.INVENTORY_SIZE] = Inventory.SLOT_OFFHAND;
        return slots;
    }

    @Override
    public ServerPlayer player(final UUID id) {
        return id == null || server == null ? null : server.getPlayerList().getPlayer(id);
    }

    @Override
    public boolean online(final UUID id) {
        return player(id) != null;
    }

    @Override
    public long countGold(final ServerPlayer player) {
        if (player == null) {
            return 0L;
        }
        final Inventory inventory = player.getInventory();
        long total = 0L;
        for (final int slot : VAULT_SLOTS) {
            final ItemStack stack = inventory.getItem(slot);
            if (isGold(stack)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    @Override
    public boolean removeGold(final ServerPlayer player, final int amount) {
        checkAmount(amount);
        if (player == null) {
            return false;
        }
        if (amount == 0) {
            return true;
        }
        if (countGold(player) < amount) {
            return false;
        }
        final Inventory inventory = player.getInventory();
        long remaining = amount;
        for (final int slot : VAULT_SLOTS) {
            if (remaining == 0L) {
                break;
            }
            final ItemStack stack = inventory.getItem(slot);
            if (!isGold(stack)) {
                continue;
            }
            final int take = (int) Math.min(remaining, stack.getCount());
            stack.shrink(take);
            remaining -= take;
            if (stack.isEmpty()) {
                inventory.setItem(slot, ItemStack.EMPTY);
            }
        }
        if (remaining != 0L) {
            // Unreachable after the count proof above, because nothing mutates the inventory in
            // between on the server thread. Put back what was taken rather than return failure
            // with a partial removal already on the player: a failed vault action must be a no-op.
            placeGold(inventory, amount - (int) remaining, goldMaxStack());
            inventory.setChanged();
            return false;
        }
        inventory.setChanged();
        return true;
    }

    @Override
    public boolean addGold(final ServerPlayer player, final int amount) {
        checkAmount(amount);
        if (player == null) {
            return false;
        }
        if (amount == 0) {
            return true;
        }
        if (!canAccept(player, amount)) {
            return false;
        }
        final Inventory inventory = player.getInventory();
        final int placed = placeGold(inventory, amount, goldMaxStack());
        if (placed != amount) {
            if (placed > 0) {
                // Those ingots are physically present, so this removal cannot fail; it restores
                // the exact pre-call count instead of leaving a partial delivery.
                removeGold(player, placed);
            }
            return false;
        }
        inventory.setChanged();
        return true;
    }

    @Override
    public boolean canAccept(final ServerPlayer player, final int amount) {
        checkAmount(amount);
        if (player == null) {
            return false;
        }
        if (amount == 0) {
            return true;
        }
        final Inventory inventory = player.getInventory();
        final int maxStack = goldMaxStack();
        long capacity = 0L;
        for (final int slot : VAULT_SLOTS) {
            final ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty()) {
                capacity += maxStack;
            } else if (isGold(stack)) {
                capacity += Math.max(0, stack.getMaxStackSize() - stack.getCount());
            }
            if (capacity >= amount) {
                return true;
            }
        }
        return false;
    }

    /**
     * Places up to {@code amount} ingots without ever dropping one.
     *
     * <p>Existing stacks are topped up first, then empty vault slots receive max-size stacks. The
     * order is deterministic, which is what lets {@link #canAccept} be an exact simulation rather
     * than an optimistic estimate.
     */
    private static int placeGold(final Inventory inventory, final int amount, final int maxStack) {
        int remaining = amount;
        for (final int slot : VAULT_SLOTS) {
            if (remaining == 0) {
                break;
            }
            final ItemStack stack = inventory.getItem(slot);
            if (!isGold(stack)) {
                continue;
            }
            final int space = stack.getMaxStackSize() - stack.getCount();
            if (space <= 0) {
                continue;
            }
            final int put = Math.min(space, remaining);
            stack.grow(put);
            remaining -= put;
        }
        for (final int slot : VAULT_SLOTS) {
            if (remaining == 0) {
                break;
            }
            if (!inventory.getItem(slot).isEmpty()) {
                continue;
            }
            final int put = Math.min(maxStack, remaining);
            inventory.setItem(slot, new ItemStack(Items.GOLD_INGOT, put));
            remaining -= put;
        }
        return amount - remaining;
    }

    private static boolean isGold(final ItemStack stack) {
        return !stack.isEmpty() && stack.is(Items.GOLD_INGOT);
    }

    private static int goldMaxStack() {
        return new ItemStack(Items.GOLD_INGOT).getMaxStackSize();
    }

    private static void checkAmount(final int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("amount must be at least zero.");
        }
    }
}
