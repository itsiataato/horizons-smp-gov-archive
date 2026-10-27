package com.horizonssmp.gov.common.records;

import java.util.UUID;

import com.horizonssmp.gov.common.enums.HistoryKind;

import net.minecraft.nbt.CompoundTag;

/**
 * One row of a player's recent-history strip.
 *
 * <p>History is a ten-row, newest-first window over business, government-job, fine, vehicle, weapon
 * and detention events. It exists so a player can see what happened to them without the audit
 * authority, and it is deliberately <b>not</b> a copy of the audit log: no payload, no outcome and
 * no permission data. The audit entry remains the authoritative record.
 *
 * <p>The text is a human sentence, not a wire format. It is capped because this record is written
 * for every event and the cap is the file-growth bound; the actor name and the reference are capped
 * for the same reason. Truncation is applied on construction and again on read, so a hand-edited
 * oversized value cannot grow a loaded file.
 *
 * <p>An unknown {@link HistoryKind} is dropped on read by returning {@code null}; one log line names
 * the count. See {@link HistoryKind} for why history is the one record allowed to discard an
 * unknown vocabulary value.
 *
 * @param kind the event kind, never {@code null}
 * @param text the one-line description, capped at {@link #MAX_TEXT}
 * @param actorUuid the player or official who acted, or {@code null} for a system event
 * @param actorName the actor's display name at the time, capped at {@link #MAX_ACTOR_NAME}
 * @param at epoch milliseconds of the event
 * @param ref a business id, contract id, fine id or other short reference, capped at {@link #MAX_REF}
 */
public record HistoryEvent(
        HistoryKind kind,
        String text,
        UUID actorUuid,
        String actorName,
        long at,
        String ref) {

    /** Longest description retained. One dashboard row plus a detail line. */
    public static final int MAX_TEXT = 160;

    /** Longest actor display name retained. A Minecraft name is at most 16 characters. */
    public static final int MAX_ACTOR_NAME = 32;

    /** Longest reference id retained. Every registry id in this mod is far shorter. */
    public static final int MAX_REF = 48;

    public HistoryEvent {
        if (kind == null) {
            throw new IllegalArgumentException("HistoryEvent requires a kind");
        }
        text = cap(text, MAX_TEXT);
        actorName = cap(actorName, MAX_ACTOR_NAME);
        at = Math.max(0L, at);
        ref = cap(ref, MAX_REF);
    }

    /**
     * Trims a value and cuts it to the cap without splitting a surrogate pair.
     *
     * <p>A raw {@code substring(0, max)} can leave a lone high surrogate, which is invalid text and
     * can make a later UTF write lossy. Stepping back one char in that case keeps the string valid
     * at a cost of at most one code unit.
     */
    private static String cap(final String raw, final int maximum) {
        if (raw == null) {
            return "";
        }
        final String value = raw.trim();
        if (value.length() <= maximum) {
            return value;
        }
        int end = maximum;
        if (end > 0 && Character.isHighSurrogate(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(0, end);
    }

    /** Serializes this event into a fresh tag. The owning player key is added by the data set. */
    public CompoundTag write() {
        final CompoundTag tag = new CompoundTag();
        tag.putString("kind", kind.id());
        tag.putString("text", text);
        NbtSupport.putUuid(tag, "actorUuid", actorUuid);
        tag.putString("actorName", actorName);
        tag.putLong("at", at);
        tag.putString("ref", ref);
        return tag;
    }

    /** Reads one event, returning {@code null} for an unknown kind or a missing kind. */
    public static HistoryEvent read(final CompoundTag tag) {
        if (tag == null || tag.isEmpty()) {
            return null;
        }
        final HistoryKind kind = HistoryKind.byId(NbtSupport.getString(tag, "kind", "")).orElse(null);
        if (kind == null) {
            return null;
        }
        try {
            return new HistoryEvent(
                    kind,
                    NbtSupport.getString(tag, "text", ""),
                    NbtSupport.getUuid(tag, "actorUuid"),
                    NbtSupport.getString(tag, "actorName", ""),
                    NbtSupport.getLong(tag, "at", 0L),
                    NbtSupport.getString(tag, "ref", ""));
        } catch (final IllegalArgumentException e) {
            return null;
        }
    }
}
