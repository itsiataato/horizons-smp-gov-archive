package com.horizonssmp.gov.common;

/**
 * The outcome of one dispatched action.
 *
 * <p>Exactly one of {@code ok} and {@code errorCode} is set. That invariant is enforced in the
 * constructor rather than left to callers, because a half-built result is the kind of thing a
 * future screen would render as "succeeded, no error" and a future log would record as a success.
 *
 * <p>{@code errorText} is for humans only. A client must branch on {@code errorCode}, which is
 * stable, and never on the message, which may be reworded between builds.
 *
 * <p>{@code delta} is a short, already-sanitized summary of what changed, for example
 * {@code fine f-3f2a issued}. It is empty for a refusal. For a read action it carries the read
 * result itself as a {@link GovFields} block ({@code name=Steve\nduty=ON_DUTY}), which is the one
 * channel a result has for structured data and keeps a read action inside the same
 * request/result pair as everything else. It is a display hint, not a patch: a screen that needs
 * the new state asks for the tab again rather than applying a diff, so a missed message can never
 * leave stale data on screen.
 *
 * @param reqId the correlation id from the request
 * @param ok true when the action was accepted and performed
 * @param errorCode why it was refused, or {@code null} when {@code ok}
 * @param errorText the human-readable refusal, or empty when {@code ok}
 * @param delta a short summary of the accepted change, or empty
 */
public record ActionResult(String reqId, boolean ok, ErrorCode errorCode, String errorText, String delta) {

    public ActionResult {
        if (reqId == null) {
            throw new IllegalArgumentException("ActionResult requires a reqId");
        }
        delta = delta == null ? "" : delta;
        if (ok) {
            if (errorCode != null) {
                throw new IllegalArgumentException("An accepted result cannot carry an error code");
            }
            errorText = "";
        } else {
            if (errorCode == null) {
                throw new IllegalArgumentException("A refused result must carry an error code");
            }
            errorText = errorText == null || errorText.isBlank() ? errorCode.defaultText() : errorText;
        }
    }

    /** An accepted action. */
    public static ActionResult success(final String reqId, final String delta) {
        return new ActionResult(reqId, true, null, "", delta);
    }

    /** A refused action with the error code's default message. */
    public static ActionResult failure(final String reqId, final ErrorCode code) {
        return new ActionResult(reqId, false, code, code.defaultText(), "");
    }

    /** A refused action with a specific message. */
    public static ActionResult failure(
            final String reqId, final ErrorCode code, final String text) {
        return new ActionResult(reqId, false, code, text, "");
    }

    /** A refusal for an action this build has no handler for. */
    public static ActionResult unsupported(final String reqId, final OpCode op) {
        return failure(
                reqId,
                ErrorCode.UNSUPPORTED,
                "No handler is registered for " + op + " in this build.");
    }
}
