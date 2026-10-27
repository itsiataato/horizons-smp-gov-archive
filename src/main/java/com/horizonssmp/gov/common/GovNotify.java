package com.horizonssmp.gov.common;

import java.util.List;

/**
 * A message the server sends to a client outside of a direct request/response pair.
 *
 * <p>Notifies exist because a government action is often about somebody else: a fine lands on a
 * player who did not click anything, a job is assigned, a warrant is served. The {@code ids} list
 * carries the record ids the client should refresh, which keeps the message a hint rather than a
 * patch: the client re-reads the record through the normal read path, so a dropped or duplicated
 * notification cannot corrupt what is on screen.
 *
 * @param kind how the message should be presented
 * @param text the message text, already sanitized by the sender
 * @param ids the record ids the client should refresh, possibly empty
 */
public record GovNotify(Kind kind, String text, List<String> ids) {

    /** Presentation intent. A client may style these differently but must not treat any as an error. */
    public enum Kind {
        /** Neutral information. */
        INFO,
        /** Something the recipient asked for succeeded. */
        SUCCESS,
        /** Something needs attention but nothing failed. */
        WARN,
        /** Something failed. */
        ERROR
    }

    public GovNotify {
        kind = kind == null ? Kind.INFO : kind;
        text = text == null ? "" : text;
        ids = ids == null ? List.of() : List.copyOf(ids);
    }

    /** An informational message with no refresh hint. */
    public static GovNotify info(final String text) {
        return new GovNotify(Kind.INFO, text, List.of());
    }
}
