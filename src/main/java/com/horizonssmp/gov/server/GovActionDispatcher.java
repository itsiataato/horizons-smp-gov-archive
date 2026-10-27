package com.horizonssmp.gov.server;

import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import com.horizonssmp.gov.common.ActionRequest;
import com.horizonssmp.gov.common.ActionResult;
import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.GovCapability;
import com.horizonssmp.gov.common.GovCapabilities;
import com.horizonssmp.gov.common.OpCode;
import com.horizonssmp.gov.common.records.PlayerRecord;

import net.minecraft.server.level.ServerPlayer;

/**
 * The single entry point for every action in the mod.
 *
 * <p>Implementation details (registry, rate limiter, pipeline) live in
 * {@link GovActionDispatcherSupport}; this class keeps the public API and the
 * nested types that callers and handlers depend on.
 */
public final class GovActionDispatcher {

    /** Maximum number of payload keys accepted. */
    public static final int MAX_PAYLOAD_KEYS = 64;

    /** Maximum total payload characters accepted, keys and values together. */
    public static final int MAX_PAYLOAD_CHARS = 4096;

    /**
     * A handler for one action.
     */
    @FunctionalInterface
    public interface ActionHandler {
        ActionResult handle(ActionContext context);
    }

    /**
     * Everything a handler is given.
     */
    public record ActionContext(
            ActionRequest request,
            ServerPlayer actor,
            GovSavedData data,
            PlayerRecord actorRecord,
            Set<GovCapability> capabilities,
            GovTargets.Resolved target,
            long now) {

        /** A payload value, or {@code fallback} when absent. */
        public String payload(final String key, final String fallback) {
            return request.get(key, fallback);
        }

        /** A payload value, or empty when absent. */
        public String payload(final String key) {
            return request.get(key, "");
        }

        /** True when the actor holds the capability. */
        public boolean can(final GovCapability capability) {
            return GovCapabilities.has(capabilities, capability);
        }
    }

    private GovActionDispatcher() {
    }

    // --------------------------------------------------------------- registry

    public static void register(final OpCode op, final ActionHandler handler) {
        GovActionDispatcherSupport.register(op, handler);
    }

    public static boolean isRegistered(final OpCode op) {
        return GovActionDispatcherSupport.isRegistered(op);
    }

    public static Set<OpCode> registeredOps() {
        return GovActionDispatcherSupport.registeredOps();
    }

    public static RateLimiter rateLimiter() {
        return GovActionDispatcherSupport.rateLimiter();
    }

    // --------------------------------------------------------------- dispatch

    public static ActionResult dispatch(
            final ServerPlayer actor, final OpCode op, final Map<String, String> payload) {
        return GovActionDispatcherSupport.dispatch(actor, op, payload);
    }

    public static void submit(
            final ServerPlayer actor,
            final OpCode op,
            final Map<String, String> payload,
            final Consumer<ActionResult> reply) {
        GovActionDispatcherSupport.submit(actor, op, payload, reply);
    }

    // ---------------------------------------------------------------- helpers

    static ErrorCode checkPayload(final Map<String, String> payload) {
        return GovActionDispatcherSupport.checkPayload(payload);
    }
}