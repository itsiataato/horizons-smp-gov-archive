package com.horizonssmp.gov.event;

import com.horizonssmp.gov.Gov;
import com.horizonssmp.gov.LawBook;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Gives every player one law book on their first join, then never again.
 *
 * <p>The marker lives in the player's persistent data, which is saved with the player and travels
 * with them across dimensions and respawns. It is deliberately not a global "seen players" set:
 * a player who joins for the first time on a later world still gets the book.
 *
 * <p>The listener is registered on the game bus from {@link Gov}'s constructor. Only a
 * {@link ServerPlayer} is touched, so this code is safe in singleplayer too and never needs a
 * client-side class.
 */
public final class LawJoinHandler {

    /** Persistent player-data key recording that the first-join book was delivered. */
    public static final String RECEIVED_TAG = "gov:received";

    private LawJoinHandler() {
    }

    /**
     * Handles a player finishing login.
     *
     * @param event the game-bus login event
     */
    public static void onPlayerLoggedIn(final PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        final CompoundTag persistentData = player.getPersistentData();
        if (persistentData.getBoolean(RECEIVED_TAG)) {
            return;
        }

        try {
            giveBook(player);
            persistentData.putBoolean(RECEIVED_TAG, true);
            player.sendSystemMessage(Component.literal(
                            "Season 10 Law received. Use /gov law for another copy. "
                                    + "Press K or use /gov dashboard to open the Gov reader if the client mod is installed.")
                    .withStyle(ChatFormatting.GRAY));
        } catch (final RuntimeException e) {
            // Do not set the marker: the next login retries instead of silently losing the book.
            Gov.LOGGER.error(
                    "[gov] failed to give the first-join law book to {}",
                    player.getName().getString(), e);
        }
    }

    /**
     * Puts one law book into a player's inventory, dropping it at their feet if the inventory is
     * full. The caller decides whether this delivery should be recorded as the first join.
     *
     * @param player the receiving player
     */
    public static void giveBook(final ServerPlayer player) {
        final ItemStack book = LawBook.create();
        if (!player.getInventory().add(book) && !book.isEmpty()) {
            player.drop(book, false);
        }
    }
}
