package com.horizonssmp.gov.network;

import java.util.Optional;

import com.horizonssmp.gov.Gov;
import com.horizonssmp.gov.common.ActionResult;
import com.horizonssmp.gov.common.ErrorCode;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * The outcome of a dashboard action on the wire: {@code gov:action_result}, server to client.
 *
 * <p>Carries {@link ActionResult} field for field. The error code travels as its stable enum name
 * and is decoded tolerantly, so a client that does not know a code from a newer server still
 * receives the message and can show it; the client branches on the name, never on the text.
 *
 * <p>For a read action the {@code delta} is a {@link com.horizonssmp.gov.common.GovFields} block,
 * not a human sentence. That is the one place where the M0 rule "delta is empty for a read action"
 * is extended, and it is extended in the direction the M0 documentation allows: the result of a
 * read action is its summary. A mutation's delta stays a short sentence.
 *
 * @param reqId the correlation id from the request
 * @param ok true when the action was accepted
 * @param errorCode the stable error code name, empty when accepted
 * @param errorText the human-readable refusal, empty when accepted
 * @param delta the read result block, or the short change summary for a mutation
 */
public record GovActionResultPayload(
        String reqId, boolean ok, String errorCode, String errorText, String delta)
        implements CustomPacketPayload {

    /** The channel id. */
    public static final CustomPacketPayload.Type<GovActionResultPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(Gov.MODID, "action_result"));

    /** The wire codec. */
    public static final StreamCodec<ByteBuf, GovActionResultPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.stringUtf8(64), GovActionResultPayload::reqId,
                    ByteBufCodecs.BOOL, GovActionResultPayload::ok,
                    ByteBufCodecs.stringUtf8(64), GovActionResultPayload::errorCode,
                    ByteBufCodecs.stringUtf8(512), GovActionResultPayload::errorText,
                    ByteBufCodecs.stringUtf8(4096), GovActionResultPayload::delta,
                    GovActionResultPayload::new);

    public GovActionResultPayload {
        reqId = reqId == null ? "" : reqId;
        errorCode = errorCode == null ? "" : errorCode;
        errorText = errorText == null ? "" : errorText;
        delta = delta == null ? "" : delta;
    }

    /** Wraps a result produced by the dispatcher. */
    public static GovActionResultPayload of(final ActionResult result) {
        return new GovActionResultPayload(
                result.reqId(),
                result.ok(),
                result.errorCode() == null ? "" : result.errorCode().name(),
                result.errorText(),
                result.delta());
    }

    /** The error code if this build knows it, or empty when accepted or unknown. */
    public Optional<ErrorCode> errorCodeValue() {
        if (errorCode.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(ErrorCode.valueOf(errorCode.trim()));
        } catch (final IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
