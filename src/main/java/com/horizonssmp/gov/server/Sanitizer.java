package com.horizonssmp.gov.server;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Text hygiene for everything that arrives from a client or a command.
 *
 * <p>Two rules, applied in one place so no handler has to remember them:
 *
 * <ul>
 *   <li>{@code §} (section sign) is removed, so a player cannot smuggle a colour code into a note,
 *       a reason or a job name and have it re-render as formatting in someone else's dashboard.</li>
 *   <li>Control characters are removed, because a note text or a name is rendered in a GUI and
 *       written to a log line, and neither should have to cope with a raw newline, a tab or a
 *       terminal escape sequence.</li>
 * </ul>
 *
 * <p>Length caps are applied after filtering, with a visible {@link #TRUNCATION_MARKER} so a
 * truncated note reads as truncated rather than as if someone typed {@code (...)}. The marker is
 * allowed to push the result past the cap by its own length; the cap is a budget for the payload,
 * not a hard storage limit, and an obviously-truncated note is worth five extra characters.
 */
public final class Sanitizer {

    /** The Minecraft formatting character. */
    public static final char SECTION_SIGN = '\u00a7';

    /** Appended when text had to be cut. */
    public static final String TRUNCATION_MARKER = "(...)";

    /** Cap for a player or job name. */
    public static final int MAX_NAME = 64;
    /** Cap for a free-text field such as a note or a reason. */
    public static final int MAX_TEXT = 512;
    /** Cap for an identifier such as a job id. */
    public static final int MAX_ID = 64;
    /** Cap for the audit log's summary of a request payload. */
    public static final int MAX_PAYLOAD_SUMMARY = 1024;

    private Sanitizer() {
    }

    /** A name: one line, no formatting, capped at {@link #MAX_NAME}. */
    public static String name(final String raw) {
        return singleLine(raw, MAX_NAME);
    }

    /** Free text: one line, no formatting, capped at {@link #MAX_TEXT}. */
    public static String text(final String raw) {
        return singleLine(raw, MAX_TEXT);
    }

    /**
     * Free text that may keep its line breaks: paragraph breaks are removed to a single newline,
     * every other control character becomes a space. Capped at {@code max}.
     *
     * <p>For note bodies, which are the one text field where a line break is meaningful. The note
     * service in M3 is the caller; nothing in M0 writes multi-line text yet.
     */
    public static String multiLine(final String raw, final int max) {
        return cap(filter(raw, true).strip(), max);
    }

    /**
     * Multi-line text with no length cap, for a caller that must <i>reject</i> an over-length value
     * instead of silently truncating it.
     *
     * <p>The note service is the caller: a note is a record of what an official wrote, so a note
     * that would exceed the limit is refused with a message rather than stored with its tail cut
     * off. The same character rules apply as {@link #multiLine}: formatting codes and control
     * characters are removed, and line breaks survive.
     */
    public static String multiLineUncapped(final String raw) {
        return filter(raw, true).strip();
    }

    /**
     * An identifier: lower case, spaces become {@code _}, and only {@code a-z}, {@code 0-9},
     * {@code .}, {@code _} and {@code -} survive. Capped at {@link #MAX_ID}.
     *
     * <p>This is the form used as a map key for jobs, notes, fines and the rest, so a hand-typed id
     * cannot contain a character that would make it unusable as a key or unreadable in a command.
     */
    public static String id(final String raw) {
        final String filtered = singleLine(raw, MAX_ID).toLowerCase(java.util.Locale.ROOT);
        final StringBuilder out = new StringBuilder(filtered.length());
        for (int i = 0; i < filtered.length(); i++) {
            final char c = filtered.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '.' || c == '-' || c == '_') {
                out.append(c);
            } else if (c == ' ' && out.length() > 0 && out.charAt(out.length() - 1) != '_') {
                // A space becomes one underscore, so "gov worker" and "govworker" stay distinct.
                out.append('_');
            }
        }
        while (out.length() > 0 && out.charAt(out.length() - 1) == '_') {
            out.setLength(out.length() - 1);
        }
        return cap(out.toString(), MAX_ID);
    }

    /** One line: controls become spaces, runs of spaces collapse, then the cap applies. */
    public static String singleLine(final String raw, final int max) {
        final String filtered = filter(raw, false).strip();
        final StringBuilder out = new StringBuilder(filtered.length());
        boolean lastWasSpace = false;
        for (int i = 0; i < filtered.length(); i++) {
            final char c = filtered.charAt(i);
            if (c == ' ') {
                if (lastWasSpace) {
                    continue;
                }
                lastWasSpace = true;
            } else {
                lastWasSpace = false;
            }
            out.append(c);
        }
        return cap(out.toString(), max);
    }

    /**
     * A one-line summary of a request payload for the audit log.
     *
     * <p>Keys are sorted so the same request always produces the same line, which is what makes two
     * audit lines comparable by eye. The {@code reason} key is left out: it has its own column in
     * the audit entry, and printing it twice makes the summary harder to read.
     */
    public static String payloadSummary(final Map<String, String> payload) {
        if (payload == null || payload.isEmpty()) {
            return "";
        }
        final List<String> parts = new ArrayList<>(payload.size());
        final List<String> keys = new ArrayList<>(payload.keySet());
        keys.sort(String::compareTo);
        for (final String key : keys) {
            if ("reason".equals(key)) {
                continue;
            }
            parts.add(singleLine(key, MAX_NAME) + "=" + singleLine(payload.get(key), MAX_TEXT));
        }
        return cap(String.join(", ", parts), MAX_PAYLOAD_SUMMARY);
    }

    /**
     * Applies the character rules.
     *
     * <p>A {@code §} and the formatting code that follows it are removed together: stripping only
     * the section sign would leave the code letter behind as ordinary text, so {@code §calert}
     * would become {@code calert}. Only a real formatting code is consumed, so a stray section sign
     * in the middle of a sentence does not eat the next letter.
     *
     * @param keepNewlines whether a {@code \n} survives as a newline instead of becoming a space
     */
    private static String filter(final String raw, final boolean keepNewlines) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        final StringBuilder out = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            final char c = raw.charAt(i);
            if (c == SECTION_SIGN) {
                if (i + 1 < raw.length() && isFormatCode(raw.charAt(i + 1))) {
                    i++;
                }
                continue;
            }
            if (Character.isHighSurrogate(c)) {
                // Keep a well-formed pair together; replace a lone half. A string cut in the
                // middle of a pair is invalid UTF-16 and can make a later UTF-8 write lossy.
                if (i + 1 < raw.length() && Character.isLowSurrogate(raw.charAt(i + 1))) {
                    out.append(c).append(raw.charAt(i + 1));
                    i++;
                } else {
                    out.append('\uFFFD');
                }
                continue;
            }
            if (Character.isLowSurrogate(c)) {
                out.append('\uFFFD');
                continue;
            }
            if (c == '\n') {
                out.append(keepNewlines ? '\n' : ' ');
                continue;
            }
            if (c == '\r') {
                // \r\n collapses to a single newline; a lone \r becomes a space.
                if (i + 1 < raw.length() && raw.charAt(i + 1) == '\n') {
                    continue;
                }
                out.append(' ');
                continue;
            }
            if (c == '\t') {
                out.append(' ');
                continue;
            }
            if (Character.isISOControl(c)) {
                continue;
            }
            out.append(c);
        }
        return out.toString();
    }

    /** True for the characters that may follow a section sign to form a vanilla formatting code. */
    private static boolean isFormatCode(final char c) {
        return (c >= '0' && c <= '9')
                || (c >= 'a' && c <= 'f')
                || (c >= 'k' && c <= 'o')
                || (c >= 'r' && c <= 'r')
                || (c >= 'A' && c <= 'F')
                || (c >= 'K' && c <= 'O')
                || c == 'R';
    }

    private static String cap(final String text, final int max) {
        if (text.length() <= max) {
            return text;
        }
        int keep = Math.max(0, max - TRUNCATION_MARKER.length());
        if (keep > 0 && Character.isHighSurrogate(text.charAt(keep - 1))) {
            // Do not leave the high half of a surrogate pair without its low half.
            keep--;
        }
        return text.substring(0, keep) + TRUNCATION_MARKER;
    }
}
