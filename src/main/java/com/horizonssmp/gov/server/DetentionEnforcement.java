package com.horizonssmp.gov.server;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.horizonssmp.gov.Gov;
import com.horizonssmp.gov.config.GovConfig;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The server-side enforcement boundary for detention: cuffs, the expiry sweep and the cell leash.
 *
 * <p>This class is the thin NeoForge adapter over {@link DetentionService}. It only cancels
 * interactions and applies the state the service records; it never removes an item, opens an
 * inventory or searches a container. Article 9.04's search rule is not implemented as an inventory
 * operation anywhere in this mod.
 *
 * <p><b>The timer is coarse.</b> The sweep runs every twenty server ticks (once per second), not on
 * every tick. Detention is measured in real minutes, and a per-tick scan of the history would be
 * waste for no gain.
 *
 * <p><b>Combat damage is not touched.</b> Article 9.05's "killing a player who surrenders is murder"
 * is a roleplay rule for officers and the court. The mod makes the surrendered/cuffed state visible
 * in the Jail tab and does not add a damage immunity that would change server combat.
 *
 * <p>Every listener checks for a server player and a server level. Each sweep is wrapped per record
 * inside the service, so one damaged record cannot stop the sweep for everyone else.
 */
public final class DetentionEnforcement {

    /** How many server ticks between sweeps. Twenty ticks is one second. */
    public static final int SWEEP_TICKS = 20;

    /** Safety cap for the per-player message throttle map. */
    private static final int MAX_THROTTLE_ENTRIES = 1024;

    private static final Map<UUID, Long> LAST_MESSAGE = new ConcurrentHashMap<>();
    private static int tickCounter;

    private DetentionEnforcement() {
    }

    // --------------------------------------------------------------------- tick

    /** The coarse detention sweep. */
    public static void onServerTick(final ServerTickEvent.Post event) {
        tickCounter++;
        if (tickCounter < SWEEP_TICKS) {
            return;
        }
        tickCounter = 0;
        final MinecraftServer server = event.getServer();
        if (server == null) {
            return;
        }
        final GovSavedData data = GovSavedData.getIfPresent(server);
        if (data == null) {
            return;
        }
        try {
            DetentionService.tick(
                    data,
                    new JailGateway.Live(server),
                    DetentionService.Policy.fromConfig(),
                    System.currentTimeMillis());
        } catch (final RuntimeException e) {
            // A sweep failure is a bug, not a reason to take the server down. The next sweep runs
            // one second later; an active record that failed to expire remains active and is not
            // silently erased.
            Gov.LOGGER.warn("[gov] detention sweep failed: {}", e.toString());
        }
    }

    // --------------------------------------------------------------------- join

    /** Re-applies cuffs and native confinement after a reconnect, or clears stale state. */
    public static void onPlayerLoggedIn(final PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || player.level().isClientSide()) {
            return;
        }
        try {
            final GovSavedData data = GovSavedData.get(player.getServer());
            DetentionService.reapplyOnJoin(
                    player,
                    data,
                    new JailGateway.Live(player.getServer()),
                    DetentionService.Policy.fromConfig(),
                    System.currentTimeMillis());
        } catch (final RuntimeException e) {
            Gov.LOGGER.warn("[gov] could not restore detention state for {}: {}",
                    player.getName().getString(), e.toString());
        }
    }

    // ------------------------------------------------------------------- cuffs

    /** Right-clicking with an item is item use. Door and empty-hand interaction stays allowed. */
    public static void onRightClickItem(final PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().isClientSide()) {
            return;
        }
        if (isCuffed(player)) {
            event.setCanceled(true);
            deny(player, "You are cuffed and cannot use items.");
        }
    }

    /** Using a held item on a block is item use; empty-hand interaction (a door, a button) is not. */
    public static void onRightClickBlock(final PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().isClientSide()) {
            return;
        }
        if (event.getItemStack().isEmpty()) {
            return;
        }
        if (isCuffed(player)) {
            event.setCanceled(true);
            event.setCancellationResult(net.minecraft.world.InteractionResult.FAIL);
            deny(player, "You are cuffed and cannot use items.");
        }
    }

    /** Left-clicking a block is a block break attempt. */
    public static void onLeftClickBlock(final PlayerInteractEvent.LeftClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().isClientSide()) {
            return;
        }
        if (isCuffed(player)) {
            event.setCanceled(true);
            deny(player, "You are cuffed and cannot break blocks.");
        }
    }

    /** Attacking an entity is barred while cuffed. */
    public static void onAttackEntity(final AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().isClientSide()) {
            return;
        }
        if (isCuffed(player)) {
            event.setCanceled(true);
            deny(player, "You are cuffed and cannot attack.");
        }
    }

    /** The actual block break, for paths that do not start with a left-click event. */
    public static void onBlockBreak(final BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player) || player.level().isClientSide()) {
            return;
        }
        if (isCuffed(player)) {
            event.setCanceled(true);
            deny(player, "You are cuffed and cannot break blocks.");
        }
    }

    /** Right-clicking an entity with an item is item use. */
    public static void onEntityInteract(final PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().isClientSide()) {
            return;
        }
        if (event.getItemStack().isEmpty()) {
            return;
        }
        if (isCuffed(player)) {
            event.setCanceled(true);
            deny(player, "You are cuffed and cannot use items.");
        }
    }

    /** The precise entity interaction variant, for the same reason. */
    public static void onEntityInteractSpecific(
            final PlayerInteractEvent.EntityInteractSpecific event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().isClientSide()) {
            return;
        }
        if (event.getItemStack().isEmpty()) {
            return;
        }
        if (isCuffed(player)) {
            event.setCanceled(true);
            deny(player, "You are cuffed and cannot use items.");
        }
    }

    // ------------------------------------------------------------------ helpers

    private static boolean isCuffed(final ServerPlayer player) {
        if (player == null || !GovConfig.nativeCuffs()) {
            return false;
        }
        final GovSavedData data = GovSavedData.getIfPresent(player.getServer());
        return data != null && DetentionService.isCuffed(
                data, player.getUUID(), System.currentTimeMillis(), true);
    }

    /** Sends one denial line, throttled per player. */
    private static void deny(final ServerPlayer player, final String message) {
        final long now = System.currentTimeMillis();
        final int cooldown = Math.max(0, GovConfig.enforcementMessageCooldownMs());
        final Long last = LAST_MESSAGE.get(player.getUUID());
        if (last != null && now - last < cooldown) {
            return;
        }
        LAST_MESSAGE.put(player.getUUID(), now);
        prune(now, Math.max(60_000L, cooldown * 10L));
        player.sendSystemMessage(Component.literal("[Gov] " + message)
                .withStyle(ChatFormatting.RED));
    }

    private static void prune(final long now, final long maxAgeMillis) {
        if (LAST_MESSAGE.size() <= MAX_THROTTLE_ENTRIES) {
            return;
        }
        LAST_MESSAGE.entrySet().removeIf(entry -> now - entry.getValue() > maxAgeMillis);
        if (LAST_MESSAGE.size() > MAX_THROTTLE_ENTRIES) {
            LAST_MESSAGE.clear();
        }
    }
}
