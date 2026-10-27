package com.horizonssmp.gov.server;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import com.horizonssmp.gov.Gov;
import com.horizonssmp.gov.common.ActionResult;
import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.OpCode;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * The global null-handler and error boundary for every remotely-triggerable path.
 *
 * <p>Dispatch funnels through {@link #guardPreHandle}, {@link #guardHandler} and
 * {@link #guardAudit} so no handler, lookup or audit step can throw onto the server thread:
 * anything unexpected becomes an {@code INVALID_STATE} refusal instead of a crash. Startup checks
 * run inside {@link #guardStartupCheck} so one throwing check is recorded and the rest still run.
 * Saved-data access goes through {@link #guardSavedData} so a missing overworld fails with an
 * actionable message instead of a null dereference. Dashboard fan-out uses {@link #safeClient} so
 * one failing panel cannot starve the rest.
 */
public final class GovGlobalGuard {

    private GovGlobalGuard() {
    }

    /**
     * Runs the pre-handler phase (thread, payload, rate-limit, target lookup, capability, handler
     * lookup). Any throw becomes an {@code INVALID_STATE} refusal; a {@code null} return means the
     * phase passed and dispatch may continue to the handler.
     */
    public static ActionResult guardPreHandle(
            final OpCode op, final String reqId, final Supplier<ActionResult> pre) {
        try {
            return pre.get();
        } catch (final Throwable t) {
            Gov.LOGGER.error("[gov] pre-handle for {} failed [{}]", op, reqId, t);
            return ActionResult.failure(
                    reqId, ErrorCode.INVALID_STATE, "That action failed. See the server log.");
        }
    }

    /**
     * Runs one action handler. Any throw — including {@code Error} — and a {@code null} result
     * both become an {@code INVALID_STATE} refusal instead of escaping onto the server thread.
     */
    public static ActionResult guardHandler(
            final OpCode op,
            final String actorName,
            final String reqId,
            final Supplier<ActionResult> call) {
        try {
            final ActionResult result = call.get();
            if (result == null) {
                Gov.LOGGER.error("[gov] handler for {} returned no result", op);
                return ActionResult.failure(
                        reqId, ErrorCode.INVALID_STATE, "That action failed. See the server log.");
            }
            return result;
        } catch (final Throwable t) {
            Gov.LOGGER.error("[gov] handler for {} failed for {}", op, actorName, t);
            return ActionResult.failure(
                    reqId, ErrorCode.INVALID_STATE, "That action failed. See the server log.");
        }
    }

    /** Runs the post-handler audit append. A throw is logged and skipped, never propagated. */
    public static void guardAudit(final Runnable audit) {
        try {
            audit.run();
        } catch (final Throwable t) {
            Gov.LOGGER.warn("[gov] audit append failed and was skipped: {}", t.toString());
        }
    }

    /**
     * Runs one startup check. A throw is recorded as a named failure and the remaining checks
     * still run, so the boot report names every broken contract instead of dying on the first.
     */
    public static int guardStartupCheck(
            final List<String> failures, final String name, final IntSupplier check) {
        try {
            return check.getAsInt();
        } catch (final Throwable t) {
            Gov.LOGGER.error("[gov] startup check {} threw and was recorded as failed", name, t);
            failures.add(name + " failed: " + t);
            return 0;
        }
    }

    /**
     * Returns the overworld for saved-data access, or throws an actionable
     * {@code IllegalStateException} when the world is not ready instead of null-dereferencing.
     */
    public static ServerLevel guardSavedData(final MinecraftServer server) {
        if (server == null) {
            throw new IllegalStateException(
                    "[gov] saved data unavailable: the server is null, the world is not ready yet.");
        }
        final ServerLevel overworld = server.overworld();
        if (overworld == null) {
            throw new IllegalStateException(
                    "[gov] saved data unavailable: the overworld is null, the world is not ready yet.");
        }
        return overworld;
    }

    /** An actor name for log lines that can never throw, even for a half-torn-down player. */
    public static String safeName(final ServerPlayer actor) {
        if (actor == null) {
            return "?";
        }
        try {
            return actor.getName().getString();
        } catch (final Throwable ignored) {
            return "?";
        }
    }

    /** Runs one dashboard panel's result handling; a throwing panel is logged, the rest still run. */
    public static void safeClient(final String panel, final Runnable panelCall) {
        if (panelCall == null) {
            return;
        }
        try {
            panelCall.run();
        } catch (final Throwable t) {
            Gov.LOGGER.warn("[gov] dashboard panel {} dropped a result: {}", panel, t.toString());
        }
    }

    /** Boolean variant of {@link #safeClient} for panels that report whether they handled a result. */
    public static boolean safeClientResult(final String panel, final BooleanSupplier panelCall) {
        if (panelCall == null) {
            return false;
        }
        try {
            return panelCall.getAsBoolean();
        } catch (final Throwable t) {
            Gov.LOGGER.warn("[gov] dashboard panel {} dropped a result: {}", panel, t.toString());
            return false;
        }
    }
}
