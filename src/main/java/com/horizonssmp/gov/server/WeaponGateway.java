package com.horizonssmp.gov.server;

import java.util.List;
import java.util.UUID;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * The live-world boundary of the weapon rules.
 *
 * <p>The service decides whether a pickup, use, placement or attack is lawful from a small set of
 * facts: the item's class, the actor's inventory (for the 16-TNT cap), and the actor's dimension
 * and position (for town cores and explosion areas). Those facts are behind this interface so the
 * whole decision set can be asserted on a headless server, exactly as the fine and vehicle
 * milestones put gold, reception and physical presence behind a gateway.
 *
 * <p>Nothing here removes, moves or searches an item. Enforcement cancels an interaction; the law's
 * search and seizure rules (Article 9.04) are never implemented as an inventory operation.
 */
public interface WeaponGateway {

    /** One item stack's registry id and count. */
    record HeldStack(String itemId, int count) {

        public HeldStack {
            itemId = itemId == null ? "" : itemId;
            count = Math.max(0, count);
        }
    }

    /** A position with its dimension. */
    record Position(String dimension, double x, double y, double z) {

        public Position {
            dimension = dimension == null ? "" : dimension;
        }

        public static Position none() {
            return new Position("", 0.0d, 0.0d, 0.0d);
        }
    }

    /** Every stack the player carries, including armor and offhand. */
    List<HeldStack> inventory(UUID player);

    /** The player's current dimension and position, or {@link Position#none()} when offline. */
    Position position(UUID player);

    /** True when the player is online. */
    boolean present(UUID player);

    /** The total count of one item id in the inventory, case-insensitively. */
    default int countItem(final UUID player, final String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return 0;
        }
        int total = 0;
        for (final HeldStack stack : inventory(player)) {
            if (stack != null && itemId.equalsIgnoreCase(stack.itemId())) {
                total += stack.count();
            }
        }
        return total;
    }

    /** An empty gateway, used when no player is present. */
    WeaponGateway NONE = new WeaponGateway() {
        @Override
        public List<HeldStack> inventory(final UUID player) {
            return List.of();
        }

        @Override
        public Position position(final UUID player) {
            return Position.none();
        }

        @Override
        public boolean present(final UUID player) {
            return false;
        }
    };

    /**
     * The live adapter. It reads the real inventory and position and does nothing else; a future
     * inventory mod or a test adapter can replace it without changing the service.
     */
    final class Live implements WeaponGateway {

        private final MinecraftServer server;

        public Live(final MinecraftServer server) {
            this.server = server;
        }

        @Override
        public List<HeldStack> inventory(final UUID player) {
            final ServerPlayer online = server == null || player == null
                    ? null : server.getPlayerList().getPlayer(player);
            if (online == null) {
                return List.of();
            }
            final java.util.List<HeldStack> result = new java.util.ArrayList<>();
            add(result, online.getInventory().items);
            add(result, online.getInventory().armor);
            add(result, online.getInventory().offhand);
            return List.copyOf(result);
        }

        private static void add(
                final java.util.List<HeldStack> target,
                final Iterable<ItemStack> stacks) {
            if (stacks == null) {
                return;
            }
            for (final ItemStack stack : stacks) {
                if (stack == null || stack.isEmpty()) {
                    continue;
                }
                final String id = WeaponClassifier.itemId(stack);
                if (!id.isBlank()) {
                    target.add(new HeldStack(id, stack.getCount()));
                }
            }
        }

        @Override
        public Position position(final UUID player) {
            final ServerPlayer online = server == null || player == null
                    ? null : server.getPlayerList().getPlayer(player);
            if (online == null) {
                return Position.none();
            }
            return new Position(
                    online.level().dimension().location().toString(),
                    online.getX(), online.getY(), online.getZ());
        }

        @Override
        public boolean present(final UUID player) {
            return server != null && player != null
                    && server.getPlayerList().getPlayer(player) != null;
        }
    }
}
