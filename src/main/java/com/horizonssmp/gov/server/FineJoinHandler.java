package com.horizonssmp.gov.server;

import com.horizonssmp.gov.Gov;
import com.horizonssmp.gov.common.records.FineRecord;
import com.horizonssmp.gov.config.GovConfig;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Reminds a joining player about unpaid fines.
 *
 * <p>One short chat line on join, controlled by {@code gov.fine.joinReminder}. A fine the player
 * never hears about is a surprise at the reception desk, not a deterrent. This is deliberately not
 * the full {@code GovNotify} push channel: that is its own milestone, and a line the player reads
 * now is worth more than a wire format nobody drains yet.
 *
 * <p>The reminder counts only {@link FineStatus#UNPAID} records and adds their restitution, because
 * the amount due at the desk includes it. A player with no unpaid fines gets nothing at all - no
 * "you are clear" line, which would train them to ignore the message.
 */
public final class FineJoinHandler {

    private FineJoinHandler() {
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
        final MinecraftServer server = player.getServer();
        if (server == null || !GovConfig.fineJoinReminder()) {
            return;
        }
        try {
            remind(player, GovSavedData.get(server));
        } catch (final RuntimeException e) {
            // A failed reminder must never block a login. The fine is still on the record and the
            // Fines tab still answers; the next join retries.
            Gov.LOGGER.error(
                    "[gov] failed to send the fine reminder to {}",
                    player.getName().getString(), e);
        }
    }

    /** Sends the reminder when the player has at least one unpaid fine. */
    static void remind(final ServerPlayer player, final GovSavedData data) {
        int count = 0;
        long due = 0L;
        for (final FineRecord fine : data.fines().values()) {
            if (fine.outstanding() && player.getUUID().equals(fine.targetUuid())) {
                count++;
                due += fine.totalDue().count();
            }
        }
        if (count == 0) {
            return;
        }
        final String message = "You have " + count + (count == 1 ? " unpaid fine" : " unpaid fines")
                + " totaling " + due + " gold ingots. Pay at the "
                + GovConfig.paymentPoint() + "; use /gov fine list to see them.";
        player.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.GOLD));
    }
}
