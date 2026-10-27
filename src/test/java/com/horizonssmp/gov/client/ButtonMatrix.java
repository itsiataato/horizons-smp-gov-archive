package com.horizonssmp.gov.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.horizonssmp.gov.common.OpCode;
import com.horizonssmp.gov.network.GovNetwork;

/**
 * Shared sink + shot assertions for the TASK-037 headless button-matrix suites.
 *
 * <p>Each per-tab suite installs the {@link GovNetwork#testSink} seam, drives every
 * Click/Action enum value through the real {@code handleClick}/{@code handleAction}/
 * {@code handleHit} dispatch, and asserts the documented opcode fires with its payload
 * keys. Suites run via {@code main()} (no JUnit discovery: {@code failOnNoDiscoveredTests}
 * is false by design).
 */
final class ButtonMatrix {
    /** One captured client-to-server request. */
    record Shot(OpCode op, Map<String, String> payload, String reqId) {
    }

    private static final List<Shot> SHOTS = new ArrayList<>();
    private static int passed = 0;
    private static int failed = 0;
    private static int blocked = 0;
    private static int expectedBlocked = 0;

    private ButtonMatrix() {
    }

    /** Installs the sink and resets per-run counters. */
    static void install() {
        synchronized (SHOTS) {
            SHOTS.clear();
        }
        passed = 0;
        failed = 0;
        blocked = 0;
        expectedBlocked = 0;
        GovNetwork.testSink = (op, payload, reqId) ->
                SHOTS.add(new Shot(op, new LinkedHashMap<>(payload), reqId));
    }

    /** Removes the sink; production path untouched afterwards. */
    static void uninstall() {
        GovNetwork.testSink = null;
    }

    /** Drops captured shots (simulates the server round-trip between staged clicks). */
    static void drain() {
        synchronized (SHOTS) {
            SHOTS.clear();
        }
    }

    private static List<Shot> copy() {
        synchronized (SHOTS) {
            return new ArrayList<>(SHOTS);
        }
    }

    /**
     * Fires {@code fire}, expecting exactly the documented opcode with at least the given
     * payload keys. Prints {@code PASS <TAB>.<Click>=<OpCode>}.
     */
    static void fire(final String tab, final String click, final Runnable fire,
            final OpCode expect, final String... keys) {
        drain();
        try {
            fire.run();
        } catch (final Throwable t) {
            blocked++;
            System.out.println("BLOCK " + tab + "." + click + ": "
                    + t.getClass().getSimpleName() + ": " + t.getMessage());
            t.printStackTrace(System.out);
            return;
        }
        final List<Shot> shots = copy();
        final List<Shot> hits = new ArrayList<>();
        for (final Shot s : shots) {
            if (s.op() == expect) {
                hits.add(s);
            }
        }
        if (hits.size() != 1) {
            failed++;
            System.out.println("FAIL " + tab + "." + click + ": expected exactly one "
                    + expect + ", got " + hits.size() + " (all=" + describe(shots) + ")");
            return;
        }
        for (final String key : keys) {
            if (!hits.get(0).payload().containsKey(key)) {
                failed++;
                System.out.println("FAIL " + tab + "." + click + ": " + expect
                        + " missing payload key '" + key + "' keys=" + hits.get(0).payload().keySet());
                return;
            }
        }
        passed++;
        System.out.println("PASS " + tab + "." + click + "=" + expect);
    }

    /**
     * Fires {@code fire}, expecting no request (local-only click). {@code stateCheck}
     * proves the click still did something observable.
     */
    static void fireNone(final String tab, final String click, final Runnable fire,
            final Runnable stateCheck) {
        drain();
        try {
            fire.run();
        } catch (final Throwable t) {
            blocked++;
            System.out.println("BLOCK " + tab + "." + click + ": "
                    + t.getClass().getSimpleName() + ": " + t.getMessage());
            t.printStackTrace(System.out);
            return;
        }
        final List<Shot> shots = copy();
        if (!shots.isEmpty()) {
            failed++;
            System.out.println("FAIL " + tab + "." + click + ": expected no opcode, got "
                    + describe(shots));
            return;
        }
        try {
            stateCheck.run();
        } catch (final Throwable t) {
            failed++;
            System.out.println("FAIL " + tab + "." + click + ": local state unchanged: "
                    + t.getMessage());
            return;
        }
        passed++;
        System.out.println("PASS " + tab + "." + click + "=LOCAL");
    }

    private static String describe(final List<Shot> shots) {
        final StringBuilder sb = new StringBuilder("[");
        for (final Shot s : shots) {
            sb.append(s.op()).append(s.payload().keySet()).append(' ');
        }
        return sb.append(']').toString();
    }

    /** Prints the suite totals; throws when anything failed or blocked. */
    static void summary(final String suite) {
        System.out.println(suite + " matrix: passed=" + passed
                + " failed=" + failed + " blocked=" + blocked
                + " expectedBlocked=" + expectedBlocked);
        if (failed > 0 || blocked > 0) {
            throw new AssertionError(suite + ": " + failed + " failed, " + blocked
                    + " blocked, " + expectedBlocked + " expected-blocked");
        }
    }

    /**
     * Fires {@code fire}, expecting the documented headless block: the click must throw
     * because it needs a live client (null host focus, null font, or
     * {@code Minecraft.getInstance()}), so the dispatch path is exercised up to the
     * headed-only seam. Prints {@code BLOCK <TAB>.<Click> (expected: <reason>)}.
     * A click that stops throwing is a FAIL: the documented reason went stale.
     */
    static void fireBlock(final String tab, final String click, final Runnable fire,
            final String reason) {
        drain();
        try {
            fire.run();
        } catch (final Throwable t) {
            expectedBlocked++;
            System.out.println("BLOCK " + tab + "." + click + " (expected: " + reason + ": "
                    + t.getClass().getSimpleName() + ")");
            return;
        }
        failed++;
        System.out.println("FAIL " + tab + "." + click + ": expected headless block ("
                + reason + "), ran clean shots=" + describe(copy()));
    }

    /**
     * Fires {@code fire}, expecting the opcode to hit the sink BEFORE the headed-only
     * tail throws (e.g. list/scopes paths send then rebuild geometry via
     * {@code Minecraft.getInstance()}, mutate paths send then sync host focus).
     * Proves dispatch + payload keys AND documents the seam in one shot.
     * Prints {@code PASS <TAB>.<Click>=<OpCode> (seam: <throw>)}.
     */
    static void fireSeam(final String tab, final String click, final Runnable fire,
            final OpCode expect, final String... keys) {
        drain();
        Throwable thrown = null;
        try {
            fire.run();
        } catch (final Throwable t) {
            thrown = t;
        }
        final List<Shot> shots = copy();
        final List<Shot> hits = new ArrayList<>();
        for (final Shot s : shots) {
            if (s.op() == expect) {
                hits.add(s);
            }
        }
        if (hits.size() != 1) {
            failed++;
            System.out.println("FAIL " + tab + "." + click + ": expected exactly one "
                    + expect + " before seam, got " + hits.size() + " (all=" + describe(shots)
                    + " thrown=" + (thrown == null ? "none" : thrown.toString()) + ")");
            if (thrown != null) {
                thrown.printStackTrace(System.out);
            }
            return;
        }
        for (final String key : keys) {
            if (!hits.get(0).payload().containsKey(key)) {
                failed++;
                System.out.println("FAIL " + tab + "." + click + ": " + expect
                        + " missing payload key '" + key + "' keys=" + hits.get(0).payload().keySet());
                return;
            }
        }
        if (thrown == null) {
            failed++;
            System.out.println("FAIL " + tab + "." + click + ": expected headed-only seam throw, "
                    + "ran clean shots=" + describe(shots));
            return;
        }
        passed++;
        System.out.println("PASS " + tab + "." + click + "=" + expect
                + " (seam: " + thrown.getClass().getSimpleName() + ")");
    }

    /**
     * Local-only click that stages state BEFORE the headed-only tail throws
     * (e.g. edit composer stages then syncs host focus). Proves the staging via
     * {@code stateCheck} despite the throw.
     */
    static void fireNoneSeam(final String tab, final String click, final Runnable fire,
            final Runnable stateCheck, final String reason) {
        drain();
        Throwable thrown = null;
        try {
            fire.run();
        } catch (final Throwable t) {
            thrown = t;
        }
        final List<Shot> shots = copy();
        if (!shots.isEmpty()) {
            failed++;
            System.out.println("FAIL " + tab + "." + click + ": expected no opcode, got "
                    + describe(shots));
            return;
        }
        if (thrown == null) {
            failed++;
            System.out.println("FAIL " + tab + "." + click + ": expected headed-only seam ("
                    + reason + "), ran clean");
            return;
        }
        try {
            stateCheck.run();
        } catch (final Throwable t) {
            failed++;
            System.out.println("FAIL " + tab + "." + click + ": staged state lost at seam: "
                    + t.getMessage() + " (seam: " + thrown.getClass().getSimpleName() + ")");
            return;
        }
        passed++;
        System.out.println("PASS " + tab + "." + click + "=LOCAL"
                + " (seam: " + thrown.getClass().getSimpleName() + ": " + reason + ")");
    }

    static void check(final boolean condition, final String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
