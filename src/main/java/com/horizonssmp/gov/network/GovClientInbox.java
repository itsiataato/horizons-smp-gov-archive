package com.horizonssmp.gov.network;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * The hand-off between the network handlers and the client UI.
 *
 * <p>NeoForge's canonical shape for a client-bound payload is to register the handler in a common
 * class and let it call a client-only method. That shape cannot be used here: this project's
 * bytecode gate requires that <b>no class outside {@code com.horizonssmp.gov.client} references a
 * client class at all</b> (a dedicated server must not even carry the reference, and the gate is
 * checked by grepping the compiled classes). A lambda in a common class that calls
 * {@code GovClientNetwork.handle(...)} puts {@code com/horizonssmp/gov/client/GovClientNetwork}
 * into that class's constant pool, so it fails the gate even though it would never run on a
 * dedicated server.
 *
 * <p>This inbox inverts the direction instead. The common handlers only <i>enqueue</i> what
 * arrived; the client package drains the queues from its own tick handler. The dependency arrow
 * points client to common, never the other way, and the dedicated server carries no client
 * reference in any common class.
 *
 * <p>Threading: every enqueue happens from a payload handler on the game thread (the registrar
 * wraps handlers in {@code MainThreadPayloadHandler}), and every drain happens on the client
 * thread, so the locks are belt-and-braces against a future handler registered on the network
 * thread. The queues are bounded: a client that never opens a screen cannot be made to grow a
 * queue without bound by a server that keeps sending, and a stale result can never be replayed
 * because the screen matches on its own request id.
 */
public final class GovClientInbox {

    /** Most result payloads kept before the oldest are dropped. */
    public static final int MAX_RESULTS = 32;

    private static final Deque<GovActionResultPayload> RESULTS = new ArrayDeque<>();
    private static final Deque<GovOpenScreenPayload> OPENS = new ArrayDeque<>();

    private GovClientInbox() {
    }

    /** Enqueues one action result. Called by the common network handler. */
    public static void pushResult(final GovActionResultPayload payload) {
        if (payload == null) {
            return;
        }
        synchronized (RESULTS) {
            while (RESULTS.size() >= MAX_RESULTS) {
                RESULTS.pollFirst();
            }
            RESULTS.addLast(payload);
        }
    }

    /**
     * Enqueues one open-screen request.
     *
     * <p>Only the newest request is kept: two {@code /gov} commands in flight describe the same
     * intent, and honouring the older one after the newer one would be visibly wrong.
     */
    public static void pushOpen(final GovOpenScreenPayload payload) {
        if (payload == null) {
            return;
        }
        synchronized (OPENS) {
            OPENS.clear();
            OPENS.addLast(payload);
        }
    }

    /** Removes and returns the oldest result, or {@code null}. Called by the client tick handler. */
    public static GovActionResultPayload pollResult() {
        synchronized (RESULTS) {
            return RESULTS.pollFirst();
        }
    }

    /** Removes and returns the newest open-screen request, or {@code null}. Called by the client. */
    public static GovOpenScreenPayload pollOpen() {
        synchronized (OPENS) {
            return OPENS.pollFirst();
        }
    }

    /** Drops everything. Called when the client screen stack is reset. */
    public static void clear() {
        synchronized (RESULTS) {
            RESULTS.clear();
        }
        synchronized (OPENS) {
            OPENS.clear();
        }
    }
}
