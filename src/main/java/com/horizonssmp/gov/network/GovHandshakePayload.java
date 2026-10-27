package com.horizonssmp.gov.network;

import com.horizonssmp.gov.Gov;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * The both-sides handshake, carried on the non-optional {@code gov:handshake} channel.
 *
 * <p>The channel itself is the install gate: NeoForge's negotiator refuses a connection when a
 * non-optional channel is missing on either side or when the two sides registered different
 * channel versions, and it does that during the configuration phase, before a player exists. This
 * payload is what the two sides say to each other once they are through: the wire protocol number
 * and the mod version, so the server can log who connected with what and so a protocol mismatch
 * that somehow survived negotiation still ends in a readable refusal instead of a decode failure.
 *
 * <p>The protocol number is bumped whenever the meaning or the shape of any {@code gov:} payload
 * changes. The mod version is the value NeoForge already compares on the channel; sending it too
 * costs a few bytes and makes the log line self-contained.
 *
 * @param protocol the wire protocol number, see {@link GovNetwork#PROTOCOL_VERSION}
 * @param modVersion the sending side's mod version, as NeoForge sees it
 */
public record GovHandshakePayload(int protocol, String modVersion) implements CustomPacketPayload {

    /** The channel id. Never rename: it is negotiated between two independently built jars. */
    public static final CustomPacketPayload.Type<GovHandshakePayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(Gov.MODID, "handshake"));

    /** The wire codec. {@code STRING_UTF8} is bounded so a hostile peer cannot send an unbounded version. */
    public static final StreamCodec<ByteBuf, GovHandshakePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, GovHandshakePayload::protocol,
                    ByteBufCodecs.stringUtf8(64), GovHandshakePayload::modVersion,
                    GovHandshakePayload::new);

    public GovHandshakePayload {
        modVersion = modVersion == null ? "" : modVersion;
    }

    /** The payload this build sends. */
    public static GovHandshakePayload current() {
        return new GovHandshakePayload(GovNetwork.PROTOCOL_VERSION, Gov.version());
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
