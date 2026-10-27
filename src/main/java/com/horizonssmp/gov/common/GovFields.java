package com.horizonssmp.gov.common;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The stable-keyed field block a read action sends back to the client.
 *
 * <p>A read action such as {@code OPEN_TAB} returns structured data, and the only channel an
 * {@link ActionResult} has for it is {@code delta}. Rather than invent a second response payload
 * for every tab, a read result is encoded as a block of {@code key=value} lines with string keys
 * that never change: {@code name}, {@code job}, {@code rank}, {@code duty}, {@code suspect},
 * {@code jail}, {@code fines}. A newer server may add keys; a newer client may ask for keys an
 * older server does not send. Neither is an error, because {@link #decode} keeps unknown keys and
 * the renderer draws only the keys it knows, with a dash for a missing one.
 *
 * <p>The encoding is line-based with backslash escapes rather than JSON. The delta travels in a
 * log-friendly and audit-friendly place, and a block of {@code key=value} lines is readable in a
 * log line where a JSON blob is not. The escape set is small and closed: {@code \} {@code \n}
 * {@code \r} and {@code =}. A malformed line is dropped, never fatal, and a repeated key keeps
 * its first value, so a hostile or damaged block cannot smuggle a second answer past the first.
 *
 * <p>{@link #encode} sorts keys, so the same map always produces the same text: two servers with
 * the same data produce byte-identical deltas, which is what makes the startup round-trip check
 * meaningful.
 */
public final class GovFields {

    /** Maximum length of an encoded block, matching the network codec's limit. */
    public static final int MAX_ENCODED = 4000;

    private GovFields() {
    }

    /**
     * Encodes a field map as newline-separated {@code key=value} lines.
     *
     * <p>Keys are sorted and blank keys are skipped. Once the output reaches {@link #MAX_ENCODED}
     * characters the remaining pairs are dropped rather than truncated mid-value, so a block is
     * always parseable.
     *
     * @param fields the fields, may be {@code null}
     * @return the encoded block, empty when there is nothing to encode
     */
    public static String encode(final Map<String, String> fields) {
        if (fields == null || fields.isEmpty()) {
            return "";
        }
        final List<String> keys = new ArrayList<>(fields.keySet());
        keys.removeIf(key -> key == null || key.isBlank());
        keys.sort(String::compareTo);

        final StringBuilder out = new StringBuilder();
        for (final String key : keys) {
            final String pair = escape(key) + "=" + escape(fields.get(key) == null ? "" : fields.get(key));
            if (out.length() == 0) {
                // The first pair is never dropped: a block with no fields at all is worse than a
                // truncated value. An oversized value is cut at the limit instead.
                out.append(pair.length() > MAX_ENCODED ? truncateEscaped(pair, MAX_ENCODED) : pair);
                continue;
            }
            if (out.length() + 1 + pair.length() > MAX_ENCODED) {
                break;
            }
            out.append('\n').append(pair);
        }
        return out.toString();
    }

    /**
     * Cuts an escaped pair at the limit without leaving a dangling backslash.
     *
     * <p>A trailing odd number of backslashes would consume the next character when the block is
     * decoded, so one is dropped. The cut value is still a valid escape sequence.
     */
    private static String truncateEscaped(final String pair, final int limit) {
        int end = Math.min(limit, pair.length());
        if (end > 0 && Character.isHighSurrogate(pair.charAt(end - 1))) {
            // Never leave a lone high surrogate: the block is decoded and re-encoded as UTF-8.
            end--;
        }
        String cut = pair.substring(0, end);
        int trailing = 0;
        while (trailing < cut.length() && cut.charAt(cut.length() - 1 - trailing) == '\\') {
            trailing++;
        }
        if (trailing % 2 == 1) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut;
    }

    /**
     * Decodes a block produced by {@link #encode}.
     *
     * <p>Tolerant by construction: a line without an unescaped {@code =} is skipped, a blank key is
     * skipped, and the first occurrence of a repeated key wins. The returned map preserves wire
     * order and is mutable so a renderer can layer defaults on top.
     *
     * @param text the encoded block, may be {@code null}
     * @return the decoded fields, never {@code null}
     */
    public static Map<String, String> decode(final String text) {
        final Map<String, String> fields = new LinkedHashMap<>();
        if (text == null || text.isEmpty()) {
            return fields;
        }
        for (final String line : splitEscaped(text, '\n')) {
            final List<String> pair = splitEscaped(line, '=');
            if (pair.size() != 2) {
                continue;
            }
            final String key = unescape(pair.get(0)).strip();
            if (key.isEmpty()) {
                continue;
            }
            fields.putIfAbsent(key, unescape(pair.get(1)));
        }
        return fields;
    }

    /**
     * Splits on an unescaped separator, leaving escape sequences intact in the segments.
     *
     * <p>The escapes must survive this pass: a value may contain an escaped {@code =} that is
     * split on later, and resolving it here would turn one value into three segments. Unescaping
     * is a separate final pass over the two halves of a pair.
     */
    private static List<String> splitEscaped(final String text, final char separator) {
        final List<String> parts = new ArrayList<>();
        final StringBuilder current = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            final char c = text.charAt(i);
            if (c == '\\' && i + 1 < text.length()) {
                current.append(c).append(text.charAt(++i));
            } else if (c == separator) {
                parts.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        parts.add(current.toString());
        return parts;
    }

    /** Resolves the escape sequences of one segment. An unknown escape yields its character. */
    private static String unescape(final String text) {
        final StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            final char c = text.charAt(i);
            if (c == '\\' && i + 1 < text.length()) {
                out.append(unescapeChar(text.charAt(++i)));
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static String escape(final String value) {
        final StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            final char c = value.charAt(i);
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '=' -> out.append("\\=");
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    private static char unescapeChar(final char c) {
        return switch (c) {
            case 'n' -> '\n';
            case 'r' -> '\r';
            // An unknown escape yields the escaped character itself. Tolerating it is what keeps a
            // block written by a newer build readable by an older one.
            default -> c;
        };
    }

    /**
     * Canonical dash: blank or missing text renders as {@code "-"}.
     *
     * <p>Consolidates ~20 identical copies across command/client helpers. Behavior is exactly the
     * old bodies: {@code null} or blank yields {@code "-"}.
     */
    public static String dash(final String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    /**
     * Canonical field value: missing or blank renders as {@code "-"}.
     */
    public static String value(final Map<String, String> fields, final String key) {
        final String value = fields.get(key);
        return value == null || value.isBlank() ? "-" : value;
    }

    /**
     * Canonical instant rendering: non-positive epoch renders as {@code "-"}.
     */
    public static String when(final long epochMillis) {
        return epochMillis <= 0L ? "-" : Instant.ofEpochMilli(epochMillis).toString();
    }

    /**
     * Canonical int parse with fallback.
     */
    public static int parseInt(final String raw, final int fallback) {
        try {
            return Integer.parseInt(raw == null ? "" : raw.trim());
        } catch (final NumberFormatException e) {
            return fallback;
        }
    }

    /**
     * Canonical long parse with fallback.
     */
    public static long parseLong(final String raw, final long fallback) {
        try {
            return Long.parseLong(raw == null ? "" : raw.trim());
        } catch (final NumberFormatException e) {
            return fallback;
        }
    }

    /**
     * Canonical int field with implicit {@code 0} fallback.
     */
    public static int intField(final Map<String, String> fields, final String key) {
        return intField(fields, key, 0);
    }

    /**
     * Canonical int field with explicit fallback.
     */
    public static int intField(final Map<String, String> fields, final String key,
            final int fallback) {
        try {
            return Integer.parseInt(fields.getOrDefault(key, "").trim());
        } catch (final NumberFormatException e) {
            return fallback;
        }
    }

    /**
     * Canonical long field with implicit {@code 0L} fallback.
     */
    public static long longField(final Map<String, String> fields, final String key) {
        return longField(fields, key, 0L);
    }

    /**
     * Canonical long field with explicit fallback.
     */
    public static long longField(final Map<String, String> fields, final String key,
            final long fallback) {
        try {
            return Long.parseLong(fields.getOrDefault(key, "").trim());
        } catch (final NumberFormatException e) {
            return fallback;
        }
    }
}
