package com.horizonssmp.gov.client;

/**
 * Debounces Enter so a held key cannot submit a form more than once per quarter second.
 *
 * <p>{@code GUI-SPEC.md} requires "Enter submits once with a 250 ms repeat guard". GLFW reports a
 * held key as a stream of key-down events, so every panel owns one guard and calls
 * {@link #accepts(long)} from {@code keyPressed}. The guard is deliberately time based and has no
 * clock of its own: the caller supplies {@code System.currentTimeMillis()}, which keeps the class
 * free of hidden state that a test or a replay cannot control.
 *
 * <p>The guard only debounces. It never decides whether a submission is legal: the panel still
 * checks its own pending request, selection and eligibility before it sends anything.
 */
final class GovKeyRepeat {

    /** The minimum gap between two accepted Enter presses. */
    static final long INTERVAL_MS = 250L;

    private long lastAcceptedAt = Long.MIN_VALUE;

    /** True when this Enter press is at least {@link #INTERVAL_MS} after the last accepted one. */
    boolean accepts(final long nowMs) {
        if (this.lastAcceptedAt != Long.MIN_VALUE
                && nowMs - this.lastAcceptedAt < INTERVAL_MS) {
            return false;
        }
        this.lastAcceptedAt = nowMs;
        return true;
    }

    /** Forgets the last press, so the next Enter is accepted immediately. */
    void reset() {
        this.lastAcceptedAt = Long.MIN_VALUE;
    }

    /** True for either Enter key. */
    static boolean isEnter(final int keyCode) {
        return keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER
                || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER;
    }
}
