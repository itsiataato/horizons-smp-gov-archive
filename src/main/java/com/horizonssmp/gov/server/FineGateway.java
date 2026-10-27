package com.horizonssmp.gov.server;

import java.util.UUID;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The live implementation of {@link FineService.Gateway}: a player's real inventory and their real
 * position against the configured reception.
 *
 * <p>This is the only class in the fine lifecycle that touches a {@code ServerPlayer}, an
 * {@code ItemStack} or a dimension. That boundary exists so the service rules - band amounts,
 * restitution bounds, ownership, double-payment, waivers, receipts - can be asserted on a headless
 * server by the startup check, where there is no player inventory to inspect. What cannot be proved
 * headlessly is exactly what this class does: that the items removed are gold ingots, that they
 * leave the player's inventory, and that the player is really standing at the reception.
 *
 * <p>Gold is counted across the whole inventory, including the offhand and armour slots (armour
 * cannot hold an ingot, so counting it is harmless). Removal is all-or-nothing: the count is
 * re-checked first, and if any slot cannot supply its share the method returns false before the
 * fine is written. A partial removal would take a player's gold without settling the debt, which is
 * the one outcome this class must never produce.
 */
public final class FineGateway implements FineService.Gateway {

    private final MinecraftServer server;

    private FineGateway(final MinecraftServer server) {
        this.server = server;
    }

    /** The gateway for a running server. */
    public static FineService.Gateway live(final MinecraftServer server) {
        return new FineGateway(server);
    }

    private ServerPlayer player(final UUID uuid) {
        return uuid == null || server == null ? null : server.getPlayerList().getPlayer(uuid);
    }

    @Override
    public long goldIngots(final UUID player) {
        final ServerPlayer online = player(player);
        if (online == null) {
            return 0L;
        }
        long total = 0L;
        for (int slot = 0; slot < online.getInventory().getContainerSize(); slot++) {
            final ItemStack stack = online.getInventory().getItem(slot);
            if (!stack.isEmpty() && stack.is(Items.GOLD_INGOT)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    @Override
    public boolean removeGoldIngots(final UUID player, final long count) {
        if (count <= 0L) {
            return true;
        }
        final ServerPlayer online = player(player);
        if (online == null || goldIngots(player) < count) {
            return false;
        }
        long remaining = count;
        for (int slot = 0; slot < online.getInventory().getContainerSize() && remaining > 0L; slot++) {
            final ItemStack stack = online.getInventory().getItem(slot);
            if (stack.isEmpty() || !stack.is(Items.GOLD_INGOT)) {
                continue;
            }
            final int take = (int) Math.min(remaining, stack.getCount());
            stack.shrink(take);
            remaining -= take;
            if (stack.isEmpty()) {
                online.getInventory().setItem(slot, ItemStack.EMPTY);
            }
        }
        online.getInventory().setChanged();
        return remaining == 0L;
    }

    @Override
    public boolean addGoldIngots(final UUID player, final long count) {
        if (count <= 0L) {
            return true;
        }
        final ServerPlayer online = player(player);
        if (online == null) {
            return false;
        }
        final long before = goldIngots(player);
        long remaining = count;
        while (remaining > 0L) {
            final int chunk = (int) Math.min(64L, remaining);
            if (!online.getInventory().add(new ItemStack(Items.GOLD_INGOT, chunk))) {
                // All-or-nothing: put the player's gold count back where it was. The removal may
                // take a different physical stack than the one just added, but the total is what
                // the fine record promises, and no net gold is created or destroyed.
                final long added = goldIngots(player) - before;
                if (added > 0L) {
                    removeGoldIngots(player, added);
                }
                return false;
            }
            remaining -= chunk;
        }
        online.getInventory().setChanged();
        return true;
    }

    @Override
    public boolean present(final UUID player) {
        return player(player) != null;
    }

    @Override
    public boolean atReception(final UUID player) {
        final ServerPlayer online = player(player);
        if (online == null) {
            return false;
        }
        return FineService.configuredReception()
                .map(point -> point.contains(
                        online.level().dimension().location().toString(),
                        online.getX(), online.getY(), online.getZ()))
                .orElse(false);
    }
}
