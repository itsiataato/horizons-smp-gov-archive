package com.horizonssmp.gov.server;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.horizonssmp.gov.common.BusinessKeys;
import com.horizonssmp.gov.common.GovCapability;
import com.horizonssmp.gov.common.PersonKeys;
import com.horizonssmp.gov.common.enums.HistoryKind;
import com.horizonssmp.gov.common.records.HistoryEvent;
import com.horizonssmp.gov.common.records.PlayerRecord;

/**
 * The one writer and reader of the bounded per-player history strip.
 *
 * <p>History is an accountability aid, not a second audit log. A service that changes a record
 * that belongs to a player calls {@link #append} with one short, human sentence; the audit line is
 * still written by the dispatcher and remains the authoritative record. The ten-row window and the
 * whole-section ceiling live in {@link GovSavedData#appendHistory}; this class only supplies the
 * event, de-duplicates a multi-recipient append and builds the read answer.
 *
 * <p><b>Who may read.</b> A player reads their own strip without any capability. Another player's
 * strip needs {@code player.lookup}, the same capability {@code LOOKUP_PERSON} already uses. There
 * is no separate history capability and no job-name check.
 *
 * <p>Every append is best-effort in the sense that a missing data set or a null player is ignored;
 * it is never best-effort about the data it names. Callers pass server-confirmed UUIDs and a
 * sentence built from records they just wrote, so a row cannot claim an event that did not happen.
 */
public final class HistoryService {

    /** History rows per page. Defined with the employment wire keys. */
    public static final int PAGE_SIZE = BusinessKeys.EMPLOYMENT_PAGE_SIZE;

    /** One page of history, newest first, with the true total. */
    public record Page(List<HistoryEvent> rows, int total, int page, int pages) {

        public Page {
            rows = rows == null ? List.of() : List.copyOf(rows);
        }
    }

    private HistoryService() {
    }

    /**
     * Appends one event for one player.
     *
     * <p>The text is sanitized to one line here, then {@link HistoryEvent} applies its own hard
     * caps. A null player, a null kind or null data is ignored: a caller with no recipient has
     * nothing to record, and inventing one would put an event on the wrong person's strip.
     */
    public static void append(
            final GovSavedData data,
            final UUID playerUuid,
            final HistoryKind kind,
            final String text,
            final UUID actorUuid,
            final String actorName,
            final long at,
            final String ref) {
        if (data == null || playerUuid == null || kind == null) {
            return;
        }
        data.appendHistory(playerUuid, new HistoryEvent(
                kind,
                Sanitizer.singleLine(text, HistoryEvent.MAX_TEXT),
                actorUuid,
                Sanitizer.singleLine(actorName, HistoryEvent.MAX_ACTOR_NAME),
                at,
                Sanitizer.id(ref)));
    }

    /**
     * Appends the same event to several players, once each.
     *
     * <p>The de-duplication matters because the owner and the actor are often the same person: a
     * single action must not consume two of that player's ten rows. The order is the caller's; an
     * event is appended for every distinct non-null UUID.
     */
    public static void appendAll(
            final GovSavedData data,
            final Collection<UUID> players,
            final HistoryKind kind,
            final String text,
            final UUID actorUuid,
            final String actorName,
            final long at,
            final String ref) {
        if (players == null || players.isEmpty()) {
            return;
        }
        final Set<UUID> distinct = new LinkedHashSet<>();
        for (final UUID player : players) {
            if (player != null) {
                distinct.add(player);
            }
        }
        for (final UUID player : distinct) {
            append(data, player, kind, text, actorUuid, actorName, at, ref);
        }
    }

    /**
     * True when the viewer may read the target's history.
     *
     * <p>Self is always allowed; anyone else needs {@code player.lookup}, the exact existing
     * capability that already governs reading another person's card. A null viewer or target is
     * refused rather than treated as a wildcard.
     */
    public static boolean canRead(
            final Set<GovCapability> capabilities,
            final UUID viewerUuid,
            final UUID targetUuid) {
        if (viewerUuid == null || targetUuid == null) {
            return false;
        }
        if (viewerUuid.equals(targetUuid)) {
            return true;
        }
        return capabilities != null && capabilities.contains(GovCapability.PLAYER_LOOKUP);
    }

    /** The recorded name of a player, or the UUID text when no file exists. */
    public static String playerName(final GovSavedData data, final UUID uuid) {
        if (uuid == null) {
            return "";
        }
        final PlayerRecord record = data == null ? null : data.player(uuid);
        return record != null && !record.lastName().isBlank()
                ? record.lastName() : uuid.toString();
    }

    /**
     * One page of a player's history.
     *
     * <p>The stored list is already newest first and capped at ten. The page number is clamped to
     * the real bounds, and the answer carries the true total so a short page can never be mistaken
     * for a complete one. No row is truncated here beyond the record's own documented caps.
     */
    public static Page page(final GovSavedData data, final UUID targetUuid, final int rawPage) {
        final List<HistoryEvent> all = data == null ? List.of() : data.historyFor(targetUuid);
        final int total = all.size();
        final int pages = Math.max(1, (total + PAGE_SIZE - 1) / PAGE_SIZE);
        final int page = Math.max(0, Math.min(rawPage, pages - 1));
        final List<HistoryEvent> rows = new ArrayList<>(PAGE_SIZE);
        final int start = page * PAGE_SIZE;
        for (int i = start; i < Math.min(total, start + PAGE_SIZE); i++) {
            rows.add(all.get(i));
        }
        return new Page(rows, total, page, pages);
    }

    /**
     * The {@code HISTORY_LIST} answer as a field map.
     *
     * <p>Every row carries the stable kind id, the sentence, the actor and the short reference. The
     * answer also names the player the strip belongs to, so a lookup of another person is never
     * ambiguous about whose events are shown.
     */
    public static Map<String, String> listFields(
            final GovSavedData data, final UUID targetUuid, final int rawPage) {
        final Page result = page(data, targetUuid, rawPage);
        final Map<String, String> fields = new LinkedHashMap<>();
        fields.put(PersonKeys.NAME, playerName(data, targetUuid));
        fields.put(PersonKeys.UUID, targetUuid == null ? "" : targetUuid.toString());
        fields.put(BusinessKeys.HISTORY_COUNT, Integer.toString(result.total()));
        fields.put(BusinessKeys.PAGE, Integer.toString(result.page()));
        fields.put(BusinessKeys.PAGES, Integer.toString(result.pages()));
        for (int i = 0; i < result.rows().size(); i++) {
            final HistoryEvent event = result.rows().get(i);
            final String prefix = BusinessKeys.HISTORY_PREFIX + i;
            fields.put(prefix + BusinessKeys.HISTORY_ROW_KIND, event.kind().id());
            fields.put(prefix + BusinessKeys.HISTORY_ROW_TEXT, event.text());
            fields.put(prefix + BusinessKeys.HISTORY_ROW_ACTOR, event.actorName());
            fields.put(prefix + BusinessKeys.HISTORY_ROW_ACTOR_UUID,
                    event.actorUuid() == null ? "" : event.actorUuid().toString());
            fields.put(prefix + BusinessKeys.HISTORY_ROW_AT, Long.toString(event.at()));
            fields.put(prefix + BusinessKeys.HISTORY_ROW_REF, event.ref());
        }
        return fields;
    }
}
