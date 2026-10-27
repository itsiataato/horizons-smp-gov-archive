package com.horizonssmp.gov.common.economy;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.horizonssmp.gov.common.EconomyKeys;
import com.horizonssmp.gov.common.ErrorCode;

/**
 * The immutable result of one economy service call.
 *
 * <p>The shape mirrors {@code FineService.Outcome}: an accepted call carries a neutral one-line
 * message, and a refused call carries a stable {@link ErrorCode} plus the same neutral message.
 * What it adds is a string field block keyed by {@link EconomyKeys}, because an economy read such
 * as a balance or a history page has structured data to return and an {@code ActionResult} has only
 * one channel for it. The keys are the wire contract, so they are never spelled at a call site.
 *
 * <p>Failure messages are user-facing. They come from {@link ErrorCode#defaultText()} or from a
 * canonical sentence in {@code EconomyText}; they never contain a raw exception, a stack trace, a
 * database or file detail, an account id the actor may not see, or a colour code.
 *
 * <p>The record is immutable. The constructor copies {@code fields} into an unmodifiable
 * {@link LinkedHashMap}, so neither the caller's map nor the map returned by {@link #fields()} can
 * change a result after it was built. {@link #with(String, String)} returns a new result and never
 * mutates this one, so a result can be shared safely.
 *
 * @param ok true when the call completed and changed or read what it promised
 * @param error the refusal code, {@code null} exactly when {@code ok} is true
 * @param message the neutral one-line feedback, never {@code null}
 * @param fields the stable-keyed response fields, never {@code null}
 */
public record EconomyResult(
        boolean ok,
        ErrorCode error,
        String message,
        Map<String, String> fields) {

    public EconomyResult {
        message = message == null ? "" : message;
        if (ok) {
            // A success with an error code is contradictory. Clearing it is safer than carrying
            // two answers, and no caller can branch on a code that is not there.
            error = null;
        } else if (error == null) {
            throw new IllegalArgumentException(
                    "A refused EconomyResult requires an ErrorCode");
        }
        fields = copyFields(fields);
    }

    /** An accepted call with a neutral message and no fields yet. */
    public static EconomyResult success(final String message) {
        return new EconomyResult(true, null, message, Map.of());
    }

    /** An accepted call with a neutral message and no fields yet. */
    public static EconomyResult success() {
        return success("");
    }

    /** A refused call with the code's neutral default text. */
    public static EconomyResult failure(final ErrorCode code) {
        if (code == null) {
            throw new IllegalArgumentException("EconomyResult.failure requires an ErrorCode");
        }
        return failure(code, code.defaultText());
    }

    /**
     * A refused call.
     *
     * @throws IllegalArgumentException when {@code code} is null, because a refusal without a code
     *     is not a typed failure
     */
    public static EconomyResult failure(final ErrorCode code, final String message) {
        if (code == null) {
            throw new IllegalArgumentException("EconomyResult.failure requires an ErrorCode");
        }
        return new EconomyResult(false, code, message, Map.of());
    }

    /**
     * Returns a copy of this result with one response field set.
     *
     * <p>A blank key or a {@code null} value is ignored and this result is returned unchanged. That
     * keeps a field-building chain total: an absent optional value simply does not add a key,
     * rather than adding an empty one a renderer would have to special-case.
     */
    public EconomyResult with(final String key, final String value) {
        if (key == null || key.isBlank() || value == null) {
            return this;
        }
        final Map<String, String> updated = new LinkedHashMap<>(fields);
        updated.put(key, value);
        return new EconomyResult(ok, error, message, updated);
    }

    /** One response field, or the empty string when it is absent. */
    public String field(final String key) {
        if (key == null) {
            return "";
        }
        final String value = fields.get(key);
        return value == null ? "" : value;
    }

    /** True when the call was refused. The inverse of {@link #ok()}. */
    public boolean failed() {
        return !ok;
    }

    private static Map<String, String> copyFields(final Map<String, String> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        final Map<String, String> copy = new LinkedHashMap<>(source.size());
        for (final Map.Entry<String, String> entry : source.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null) {
                continue;
            }
            copy.put(entry.getKey(), entry.getValue());
        }
        return copy.isEmpty() ? Map.of() : Collections.unmodifiableMap(copy);
    }
}
