package com.horizonssmp.gov.event;

import com.horizonssmp.gov.Gov;
import com.horizonssmp.gov.network.GovHandshakePayload;
import com.horizonssmp.gov.network.GovNetwork;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Sends the both-sides handshake to a player who just logged in.
 *
 * <p>The handshake is not the gate - the non-optional channel is, and it has already done its
 * work during the configuration phase by the time this event fires. This is the conversation that
 * proves the gate ran: the server says its protocol number and version, the client validates them
 * and answers, and the server logs the answer. If a version somehow slipped through negotiation,
 * the handler refuses the connection with a message naming both numbers instead of failing later
 * at a random payload decode.
 *
 * <p>Only a {@link ServerPlayer} is touched, so this listener is safe on the integrated server
 * too and never references a client class.
 */
public final class GovHandshakeHandler {

    private GovHandshakeHandler() {
    }

    /**
     * Sends the handshake once the player is in the world.
     *
     * @param event the game-bus login event
     */
    public static void onPlayerLoggedIn(final PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        try {
            PacketDistributor.sendToPlayer(player, GovHandshakePayload.current());
            Gov.LOGGER.info(
                    "[gov] handshake sent to {}: protocol={} channelVersion={}",
                    player.getName().getString(),
                    GovNetwork.PROTOCOL_VERSION,
                    GovNetwork.channelVersion());
        } catch (final RuntimeException e) {
            // A missing channel means the connection should not have been allowed; log it, do not
            // crash the login. The channel negotiation is the gate, this is the report.
            Gov.LOGGER.warn(
                    "[gov] could not send the handshake to {}: {}",
                    player.getName().getString(), e.toString());
        }
    }
}
