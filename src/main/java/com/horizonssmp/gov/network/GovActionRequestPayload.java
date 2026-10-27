package com.horizonssmp.gov.network;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.horizonssmp.gov.Gov;
import com.horizonssmp.gov.common.ActionRequest;
import com.horizonssmp.gov.common.OpCode;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * A dashboard action request on the wire: {@code gov:action_request}, client to server.
 *
 * <p>This is a faithful carrier of {@link ActionRequest}: the op as its stable enum name, the
 * plain string payload and the correlation id. It deliberately carries no target identity beyond
 * what the payload map already carried, no player UUID and no capability claim: the server
 * resolves the actor from the connection and the target from its own sources, exactly as it does
 * for a command.
 *
 * <p>The map codec is bounded on both entry count and string length. The dispatcher's own payload
 * guard is the policy (64 keys, 4096 characters); these limits are larger, so a request that is
 * merely too big for the policy still reaches the dispatcher and gets a real {@code BAD_PAYLOAD}
 * refusal, while a request that is absurd is stopped before the server allocates it.
 *
 * @param op the op name, verbatim from {@link OpCode#name()}
 * @param payload the request arguments
 * @param reqId the client's correlation id, echoed back in the result
 */
public record GovActionRequestPayload(String op, Map<String, String> payload, String reqId)
        implements CustomPacketPayload {

    /** The channel id. */
    public static final CustomPacketPayload.Type<GovActionRequestPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(Gov.MODID, "action_request"));

    /** The wire codec. */
    public static final StreamCodec<ByteBuf, GovActionRequestPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.stringUtf8(64), GovActionRequestPayload::op,
                    ByteBufCodecs.map(LinkedHashMap::new,
                            ByteBufCodecs.stringUtf8(128),
                            ByteBufCodecs.stringUtf8(4096),
                            128), GovActionRequestPayload::payload,
                    ByteBufCodecs.stringUtf8(64), GovActionRequestPayload::reqId,
                    GovActionRequestPayload::new);

    public GovActionRequestPayload {
        op = op == null ? "" : op;
        reqId = reqId == null ? "" : reqId;
        final Map<String, String> copy = new LinkedHashMap<>();
        if (payload != null) {
            for (final Map.Entry<String, String> entry : payload.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    copy.put(entry.getKey(), entry.getValue());
                }
            }
        }
        payload = Collections.unmodifiableMap(copy);
    }

    /** A payload for a request this build is sending. */
    public static GovActionRequestPayload of(final OpCode op, final Map<String, String> payload, final String reqId) {
        return new GovActionRequestPayload(op.name(), payload, reqId);
    }

    /** The op if this build knows the name, or empty for an op from a newer build. */
    public java.util.Optional<OpCode> opCode() {
        return OpCode.byName(op);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
