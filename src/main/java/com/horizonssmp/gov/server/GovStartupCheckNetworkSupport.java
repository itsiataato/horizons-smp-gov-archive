package com.horizonssmp.gov.server;

import java.util.List;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

final class GovStartupCheckNetworkSupport {

    private GovStartupCheckNetworkSupport() {
    }

    static int expectChannel(
            final ResourceLocation id, final PacketFlow flow, final List<String> failures) {
        final StreamCodec<? super net.minecraft.network.FriendlyByteBuf, ? extends CustomPacketPayload> codec =
                NetworkRegistry.getCodec(id, ConnectionProtocol.PLAY, flow);
        if (codec == null) {
            failures.add("network channel is not registered: " + id + " " + flow);
        }
        return 1;
    }

    /**
     * Encodes and decodes a payload through its own codec.
     *
     * <p>This is the only place the payload shapes are exercised without a connection, and it is
     * exactly the check that catches an off-by-one field order or a codec that cannot represent a
     * value it is expected to carry.
     */
    static <T extends CustomPacketPayload> boolean roundTrips(
            final StreamCodec<ByteBuf, T> codec, final T value) {
        final ByteBuf buffer = Unpooled.buffer();
        try {
            codec.encode(buffer, value);
            return value.equals(codec.decode(buffer));
        } catch (final RuntimeException e) {
            return false;
        } finally {
            buffer.release();
        }
    }

    /** The field block must survive its own encoding, including the escape characters. */

}
