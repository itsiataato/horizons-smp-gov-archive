package com.horizonssmp.gov.server;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import com.horizonssmp.gov.Gov;
import com.horizonssmp.gov.common.ActionRequest;
import com.horizonssmp.gov.common.ActionResult;
import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.GovCapability;
import com.horizonssmp.gov.common.GovCapabilities;
import com.horizonssmp.gov.common.OpCode;
import com.horizonssmp.gov.common.records.PlayerRecord;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Implementation body for {@link GovActionDispatcher}. Holds the handler registry, the rate
 * limiter, and the full dispatch pipeline so the public facade stays small. Package-private:
 * callers go through {@link GovActionDispatcher}.
 */
final class GovActionDispatcherSupport {

    private static final Map<OpCode, GovActionDispatcher.ActionHandler> HANDLERS =
            new EnumMap<>(OpCode.class);
    private static final RateLimiter RATE_LIMITER = new RateLimiter();

    private GovActionDispatcherSupport() {
    }

    // --------------------------------------------------------------- registry

    public static void register(final OpCode op, final GovActionDispatcher.ActionHandler handler) {
        if (op == null || handler == null) {
            throw new IllegalArgumentException("register requires an op and a handler");
        }
        HANDLERS.put(op, handler);
    }

    public static boolean isRegistered(final OpCode op) {
        return HANDLERS.containsKey(op);
    }

    public static Set<OpCode> registeredOps() {
        return Collections.unmodifiableSet(HANDLERS.keySet());
    }

    public static RateLimiter rateLimiter() {
        return RATE_LIMITER;
    }

    // --------------------------------------------------------------- dispatch

    /** Pre-handler outcome: a refusal short-circuits dispatch, otherwise the handler phase runs. */
    private record Pre(
            ActionResult refusal,
            GovActionDispatcher.ActionHandler handler,
            GovActionDispatcher.ActionContext context,
            ActionRequest request,
            GovTargets.Resolved target,
            GovSavedData data) {
    }

    private static final class PreBox {
        Pre value;
    }

    public static ActionResult dispatch(
            final ServerPlayer actor, final OpCode op, final Map<String, String> payload) {
        if (actor == null || op == null) {
            throw new IllegalArgumentException("dispatch requires an actor and an op");
        }
        final String reqId = UUID.randomUUID().toString();
        final PreBox preBox = new PreBox();
        final ActionResult refusal = GovGlobalGuard.guardPreHandle(op, reqId, () -> {
            preBox.value = preHandle(actor, op, payload, reqId);
            return preBox.value.refusal();
        });
        if (refusal != null) {
            return refusal;
        }
        final Pre pre = preBox.value;
        final String actorName = GovGlobalGuard.safeName(actor);

        final ActionResult result = GovGlobalGuard.guardHandler(
                op, actorName, reqId, () -> pre.handler().handle(pre.context()));

        // The reply always carries this request's id, whatever the handler put in it, so a client
        // with several requests in flight can always match an answer to a question.
        final ActionResult correlated = new ActionResult(
                reqId, result.ok(), result.errorCode(), result.errorText(), result.delta());

        if (!correlated.ok()) {
            logRejected(op, actor, reqId, correlated.errorCode());
        } else if (op.dispatcherAudited()) {
            // Reads are not audited: see OpCode.audited(). A read that was refused is logged above
            // at DEBUG like every other refusal. The four service-audited actions (exports and the
            // season reset) write their own richer line, so this branch deliberately skips them.
            GovGlobalGuard.guardAudit(() -> AuditService.append(
                    pre.data(), actor, op.name(), pre.target(),
                    pre.request().payload(), pre.request().get("reason", "")));
        }
        return correlated;
    }

    private static Pre preHandle(
            final ServerPlayer actor,
            final OpCode op,
            final Map<String, String> payload,
            final String reqId) {
        final MinecraftServer server = actor.getServer();

        // 1. Server thread.
        if (server == null || !server.isSameThread()) {
            Gov.LOGGER.warn("[gov] {} refused: not on the server thread", op);
            return new Pre(ActionResult.failure(
                    reqId, ErrorCode.INVALID_STATE, "That action must run on the server thread."),
                    null, null, null, null, null);
        }

        // 2. Payload size.
        final ErrorCode payloadError = checkPayload(payload);
        if (payloadError != null) {
            logRejected(op, actor, reqId, payloadError);
            return new Pre(ActionResult.failure(
                    reqId,
                    payloadError,
                    "That request was too large or malformed."),
                    null, null, null, null, null);
        }
        final ActionRequest request = new ActionRequest(op, payload, reqId);

        // 3. Rate limit. The per-actor bucket takes the op's policy cost; the per-tick global cap
        //    is a second, independent backstop for many actors asking at once.
        if (!RATE_LIMITER.allow(actor.getUUID(), op) || !RATE_LIMITER.allowGlobal()) {
            logRejected(op, actor, reqId, ErrorCode.RATE_LIMITED);
            return new Pre(ActionResult.failure(
                    reqId,
                    ErrorCode.RATE_LIMITED,
                    "Too many government requests right now. Wait a moment and try again."),
                    null, null, null, null, null);
        }

        final GovSavedData data = GovSavedData.get(server);
        final long now = System.currentTimeMillis();

        // 4. Target resolution, server-side only.
        GovTargets.Resolved target = null;
        if (op.requiresPlayerTarget()) {
            target = GovTargets.resolve(server, data, request.get(GovTargets.KEY_TARGET, ""));
            if (target == null) {
                logRejected(op, actor, reqId, ErrorCode.BAD_TARGET);
                return new Pre(ActionResult.failure(reqId, ErrorCode.BAD_TARGET),
                        null, null, null, null, null);
            }
        }

        // 5. Capability check.
        final PlayerRecord actorRecord =
                data.playerOrCreate(actor.getUUID(), actor.getName().getString(), now);
        Set<GovCapability> capabilities =
                GovCapabilities.effective(actorRecord, data::job);
        if (GovCapabilities.isOperator(actor)) {
            capabilities = EnumSet.allOf(GovCapability.class);
        }
        final GovCapability required = GovCapabilities.requiredFor(op);
        if (!capabilities.contains(required)) {
            logRejected(op, actor, reqId, ErrorCode.FORBIDDEN);
            return new Pre(ActionResult.failure(
                    reqId,
                    ErrorCode.FORBIDDEN,
                    "You need " + required.id() + " for that action."),
                    null, null, null, null, null);
        }

        // 6. Handler lookup.
        final GovActionDispatcher.ActionHandler handler = HANDLERS.get(op);
        if (handler == null) {
            logRejected(op, actor, reqId, ErrorCode.UNSUPPORTED);
            return new Pre(ActionResult.unsupported(reqId, op), null, null, null, null, null);
        }

        final GovActionDispatcher.ActionContext context =
                new GovActionDispatcher.ActionContext(request, actor, data, actorRecord, capabilities, target, now);
        return new Pre(null, handler, context, request, target, data);
    }

    public static void submit(
            final ServerPlayer actor,
            final OpCode op,
            final Map<String, String> payload,
            final Consumer<ActionResult> reply) {
        if (actor == null || op == null || reply == null) {
            throw new IllegalArgumentException("submit requires an actor, an op and a reply");
        }
        final MinecraftServer server = actor.getServer();
        if (server == null) {
            reply.accept(ActionResult.failure(
                    UUID.randomUUID().toString(),
                    ErrorCode.INVALID_STATE,
                    "That action must run on a server."));
            return;
        }
        if (server.isSameThread()) {
            reply.accept(dispatch(actor, op, payload));
            return;
        }
        try {
            server.execute(() -> reply.accept(dispatch(actor, op, payload)));
        } catch (final RuntimeException e) {
            Gov.LOGGER.warn("[gov] could not schedule {}: {}", op, e.toString());
            reply.accept(ActionResult.failure(
                    UUID.randomUUID().toString(),
                    ErrorCode.INVALID_STATE,
                    "The server is not accepting actions right now."));
        }
    }

    // ---------------------------------------------------------------- helpers

    static ErrorCode checkPayload(final Map<String, String> payload) {
        if (payload == null || payload.isEmpty()) {
            return null;
        }
        if (payload.size() > GovActionDispatcher.MAX_PAYLOAD_KEYS) {
            return ErrorCode.BAD_PAYLOAD;
        }
        int chars = 0;
        for (final Map.Entry<String, String> entry : payload.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                return ErrorCode.BAD_PAYLOAD;
            }
            chars += entry.getKey().length() + entry.getValue().length();
            if (chars > GovActionDispatcher.MAX_PAYLOAD_CHARS) {
                return ErrorCode.BAD_PAYLOAD;
            }
        }
        return null;
    }

    private static void logRejected(
            final OpCode op, final ServerPlayer actor, final String reqId, final ErrorCode code) {
        if (Gov.LOGGER.isDebugEnabled()) {
            Gov.LOGGER.debug(
                    "[gov] rejected {} for {}: {} [{}]",
                    op,
                    GovGlobalGuard.safeName(actor),
                    code,
                    reqId);
        }
    }
}