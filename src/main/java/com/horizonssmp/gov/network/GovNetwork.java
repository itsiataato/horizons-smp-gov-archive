package com.horizonssmp.gov.network;

import java.util.Map;
import java.util.Optional;

import com.horizonssmp.gov.Gov;
import com.horizonssmp.gov.common.ActionResult;
import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.OpCode;
import com.horizonssmp.gov.server.GovActionDispatcher;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * The {@code gov:} network channel: registration, handlers and the two send helpers.
 *
 * <h2>Why a channel at all</h2>
 *
 * <p>{@code displayTest = "MATCH_VERSION"} in {@code neoforge.mods.toml} is inert on NeoForge
 * 21.1: FML 4.0.45 does not parse the key, so it cannot make "this mod is required on both sides"
 * true. The only cross-side gate in 21.1.x is network negotiation, and this class is that gate: a
 * non-optional, versioned channel registered through {@link RegisterPayloadHandlersEvent}.
 * NeoForge's {@code NetworkComponentNegotiator} refuses a connection when the channel is missing
 * on either side or when the two sides registered different versions, during the configuration
 * phase, before a player exists. A vanilla client on a NeoForge server and a modded client on a
 * vanilla server are refused the same way.
 *
 * <h2>Versioning</h2>
 *
 * <p>The channel version is the mod version, read from the mod container at construction. Two
 * jars with different versions cannot connect, which is exactly what the toml and the README
 * promise. The handshake payload additionally carries {@link #PROTOCOL_VERSION}, a number that is
 * bumped when the wire shape changes; the handler checks it and refuses with a readable message
 * if it ever disagrees, so a protocol change can never be decoded as garbage.
 *
 * <h2>Client classes</h2>
 *
 * <p>No handler in this class names a client class. Client-bound payloads are enqueued into
 * {@link GovClientInbox} and drained by the client package. That keeps every reference to
 * {@code net.minecraft.client.*} and to {@code com.horizonssmp.gov.client.*} out of the common
 * classes, which is both the dedicated-server rule and this project's bytecode gate.
 */
@EventBusSubscriber(modid = Gov.MODID)
public final class GovNetwork {

    /**
     * The wire protocol number of every {@code gov:} payload.
     *
     * <p>Bump when the shape or the meaning of a payload changes. The channel version (the mod
     * version) already blocks mismatched jars; this number is the narrower, wire-level statement
     * and exists so the handshake can explain a mismatch in words.
     *
     * <p>Version 4 is the economy change: the action vocabulary gained the sixteen bank, payroll,
     * store and contract ops and the envelope can carry their payload keys. The envelope shape is
     * unchanged (op name, string map, request id), but a version-3 server would answer the new op
     * names with {@code UNSUPPORTED} and a version-3 client has no screen that sends them. Both
     * sides ship the same jar, so the bump is the statement that they must.
     *
     * <p>Version 3 was the business employment change: the position, offer, contract, complaint,
     * close and history ops, with the employment block and history keys in a payload.
     */
    public static final int PROTOCOL_VERSION = 4;

    /** Fallback channel version when the mod version cannot be read (never expected outside a dev run). */
    public static final String FALLBACK_VERSION = "dev";

    private GovNetwork() {
    }

    /** The channel version this build registers: the mod version, or {@link #FALLBACK_VERSION}. */
    public static String channelVersion() {
        final String version = Gov.version();
        return version == null || version.isBlank() ? FALLBACK_VERSION : version;
    }

    /**
     * Registers the channel. Fired on the mod bus on both sides, once, before any world exists.
     *
     * <p>No registrar here is marked {@code optional()}: the handshake is the install gate and the
     * other three payloads are the dashboard itself, so a client missing any of them must not
     * connect. See the class documentation for why that is the point.
     *
     * @param event the payload-registration event
     */
    @SubscribeEvent
    public static void onRegisterPayloadHandlers(final RegisterPayloadHandlersEvent event) {
        final String version = channelVersion();
        final PayloadRegistrar registrar = event.registrar(version);
        registrar.playBidirectional(
                GovHandshakePayload.TYPE, GovHandshakePayload.STREAM_CODEC, GovNetwork::onHandshake);
        registrar.playToServer(
                GovActionRequestPayload.TYPE, GovActionRequestPayload.STREAM_CODEC, GovNetwork::onActionRequest);
        registrar.playToClient(
                GovActionResultPayload.TYPE, GovActionResultPayload.STREAM_CODEC, GovNetwork::onActionResult);
        registrar.playToClient(
                GovOpenScreenPayload.TYPE, GovOpenScreenPayload.STREAM_CODEC, GovNetwork::onOpenScreen);

        Gov.LOGGER.info(
                "[gov] network channel registered on side={} protocol={} channelVersion={}"
                        + " payloads=[handshake, action_request, action_result, open_screen] (non-optional)",
                FMLEnvironment.dist,
                PROTOCOL_VERSION,
                version);
    }

    // ------------------------------------------------------------------ sending

    /**
     * Test-only sink for headless button-matrix suites (TASK-037). When non-null,
     * {@link #sendRequest} records the op/payload/reqId here and returns without
     * touching {@code PacketDistributor} (which NPEs headless with no channel).
     * Always {@code null} in production; set/cleared only by test mains.
     */
    public interface RequestSink {
        void accept(OpCode op, Map<String, String> payload, String reqId);
    }

    public static volatile RequestSink testSink = null;

    /**
     * Sends one dashboard action to the server. Called from the client package.
     *
     * <p>The request id is generated by the caller so the reply can be matched without a second
     * round trip; the server echoes it in the result.
     */
    public static void sendRequest(final OpCode op, final Map<String, String> payload, final String reqId) {
        final RequestSink sink = testSink;
        if (sink != null) {
            sink.accept(op, payload, reqId);
            return;
        }
        PacketDistributor.sendToServer(GovActionRequestPayload.of(op, payload, reqId));
    }

    /** Tells one player's client to open the dashboard on a tab. Called from the command tree. */
    public static void openScreen(final ServerPlayer player, final GovOpenScreenPayload payload) {
        if (player != null && payload != null) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    // ----------------------------------------------------------------- handlers

    /**
     * The handshake handler, on both sides.
     *
     * <p>The receiving side compares the sender's protocol number and mod version with its own. A
     * mismatch disconnects with a message naming both numbers, because a connection that got this
     * far with a mismatch would otherwise fail later at a random payload decode. On the client
     * side the handler answers with this side's handshake, which is what makes it a handshake
     * rather than an announcement.
     */
    private static void onHandshake(final GovHandshakePayload payload, final IPayloadContext context) {
        final boolean serverSide = context.flow() == PacketFlow.SERVERBOUND;
        final String ourVersion = channelVersion();
        final String sender = serverSide
                ? (context.player() instanceof ServerPlayer player ? player.getName().getString() : "?")
                : "server";

        if (payload.protocol() != PROTOCOL_VERSION) {
            Gov.LOGGER.warn(
                    "[gov] handshake protocol mismatch from {}: peer protocol={} this build protocol={}",
                    sender, payload.protocol(), PROTOCOL_VERSION);
            context.disconnect(Component.literal(
                    "Gov protocol mismatch: the other side speaks " + payload.protocol()
                            + ", this build speaks " + PROTOCOL_VERSION + ". Update both sides."));
            return;
        }
        if (!ourVersion.equals(payload.modVersion())) {
            Gov.LOGGER.warn(
                    "[gov] handshake version mismatch from {}: peer version={} this build version={}",
                    sender, payload.modVersion(), ourVersion);
            context.disconnect(Component.literal(
                    "Gov version mismatch: the other side runs " + payload.modVersion()
                            + ", this build runs " + ourVersion + ". Update both sides."));
            return;
        }

        if (serverSide) {
            Gov.LOGGER.info(
                    "[gov] handshake acknowledged by {}: protocol={} modVersion={}",
                    sender, payload.protocol(), payload.modVersion());
        } else {
            Gov.LOGGER.info(
                    "[gov] handshake from server: protocol={} modVersion={}",
                    payload.protocol(), payload.modVersion());
            // The ack. Sent from the client to the server, on the same bidirectional channel.
            PacketDistributor.sendToServer(GovHandshakePayload.current());
        }
    }

    /**
     * Routes one dashboard action through the dispatcher.
     *
     * <p>The op name is parsed tolerantly: an op this build does not know is answered with an
     * {@code UNSUPPORTED} refusal instead of being dropped, so a newer client sees an explanation
     * rather than a screen that never loads. The refusal goes through the same rate limiter as a
     * real action, so the unknown-op path cannot be used to make the server send unbounded
     * replies.
     */
    private static void onActionRequest(final GovActionRequestPayload payload, final IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        final Optional<OpCode> op = payload.opCode();
        if (op.isEmpty()) {
            if (!GovActionDispatcher.rateLimiter().allow(player.getUUID())
                    || !GovActionDispatcher.rateLimiter().allowGlobal()) {
                final ActionResult limited = ActionResult.failure(
                        payload.reqId(),
                        ErrorCode.RATE_LIMITED,
                        ErrorCode.RATE_LIMITED.defaultText());
                try {
                    PacketDistributor.sendToPlayer(player, GovActionResultPayload.of(limited));
                } catch (final RuntimeException e) {
                    Gov.LOGGER.debug("[gov] dropped a reply to a disconnected player: {}", e.toString());
                }
                return;
            }
            final ActionResult unknown = ActionResult.failure(
                    payload.reqId(),
                    ErrorCode.UNSUPPORTED,
                    "This server does not know the action '" + payload.op() + "'.");
            try {
                PacketDistributor.sendToPlayer(player, GovActionResultPayload.of(unknown));
            } catch (final RuntimeException e) {
                Gov.LOGGER.debug("[gov] dropped a reply to a disconnected player: {}", e.toString());
            }
            return;
        }

        GovActionDispatcher.submit(player, op.get(), payload.payload(), result -> {
            // The dispatcher stamps its own correlation id; the client's id is the one that must
            // come back, so the reply is rebuilt around it.
            final ActionResult correlated = new ActionResult(
                    payload.reqId(), result.ok(), result.errorCode(), result.errorText(), result.delta());
            try {
                PacketDistributor.sendToPlayer(player, GovActionResultPayload.of(correlated));
            } catch (final RuntimeException e) {
                Gov.LOGGER.debug("[gov] dropped a reply to a disconnected player: {}", e.toString());
            }
        });
    }

    /** Enqueues an action result for the client UI. Runs on the client only. */
    private static void onActionResult(final GovActionResultPayload payload, final IPayloadContext context) {
        GovClientInbox.pushResult(payload);
    }

    /** Enqueues an open-screen instruction for the client UI. Runs on the client only. */
    private static void onOpenScreen(final GovOpenScreenPayload payload, final IPayloadContext context) {
        GovClientInbox.pushOpen(payload);
    }
}
