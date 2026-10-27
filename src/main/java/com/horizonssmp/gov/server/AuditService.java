package com.horizonssmp.gov.server;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.horizonssmp.gov.common.AuditKeys;
import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.GovCapability;
import com.horizonssmp.gov.common.GovFields;
import com.horizonssmp.gov.common.enums.AuditOutcome;
import com.horizonssmp.gov.common.records.AuditEntry;

import net.minecraft.server.level.ServerPlayer;
/**
 * Writes and reads the audit log.
 *
 * <p>One place builds an audit line, so every line has the same shape and every field goes through
 * {@link Sanitizer} exactly once. A handler never assembles an entry itself: it calls
 * {@link #append} and gets back the entry that was stored.
 *
 * <p>Names are copied in, never referenced. An audit line must still read correctly after the actor
 * is renamed, changes job or is deleted, which is exactly when someone is reading it.
 *
 * <p>Reads here never append. {@code AUDIT_PAGE} and {@code AUDIT_INFO} are declared unaudited in
 * {@link com.horizonssmp.gov.common.OpCode}; this class only reads the list, so opening the tab
 * cannot grow the log it is reading (M3 tip t43). The retention ceiling stays at
 * {@link GovSavedData#MAX_AUDIT_ENTRIES}.
 */
public final class AuditService {

    private AuditService() {
    }
    /**
     * The filters one audit page request carries.
     *
     * @param actor a case-insensitive substring of the actor snapshot name, or empty
     * @param action an exact action verb, case-insensitive, or empty
     * @param target a case-insensitive substring of the target snapshot name, or empty
     * @param outcome the outcome to include, or {@code null} for every outcome
     * @param window the canonical window id
     * @param since epoch milliseconds lower bound, {@code 0} for no bound
     */
    public record Filter(
            String actor, String action, String target, AuditOutcome outcome,
            String window, long since) {

        public Filter {
            actor = actor == null ? "" : actor;
            action = action == null ? "" : action;
            target = target == null ? "" : target;
            window = window == null || window.isBlank() ? AuditKeys.DEFAULT_WINDOW : window;
        }

        /** True when the entry passes every filter. */
        public boolean matches(final AuditEntry entry) {
            if (entry == null) {
                return false;
            }
            if (since > 0L && entry.at() < since) {
                return false;
            }
            if (outcome != null && entry.outcome() != outcome) {
                return false;
            }
            if (!actor.isBlank()
                    && !entry.actorName().toLowerCase(Locale.ROOT).contains(actor)) {
                return false;
            }
            if (!target.isBlank()
                    && !entry.targetName().toLowerCase(Locale.ROOT).contains(target)) {
                return false;
            }
            return action.isBlank() || entry.action().equalsIgnoreCase(action);
        }
    }

    /** One page of the log with the true totals. */
    public record Page(List<AuditEntry> rows, int matching, int page, int pages) {

        public Page {
            rows = rows == null ? List.of() : List.copyOf(rows);
        }
    }

    /**
     * Builds a filter from raw payload values, tolerantly.
     *
     * <p>An unknown outcome reads as "all" and an unknown window reads as the default seven days.
     * Neither is an error: a newer client's filter must still produce a useful list. The action
     * and the names are sanitized and capped here, so a filter cannot carry a control character
     * into the field block.
     *
     * @param now the request time, for the window bound
     */
    public static Filter filter(
            final String rawActor, final String rawAction, final String rawTarget,
            final String rawOutcome, final String rawWindow, final long now) {
        final String actor = Sanitizer.name(rawActor).toLowerCase(Locale.ROOT);
        final String action = Sanitizer.singleLine(rawAction, Sanitizer.MAX_NAME);
        final String target = Sanitizer.name(rawTarget).toLowerCase(Locale.ROOT);
        final AuditOutcome outcome = parseOutcomeFilter(rawOutcome);
        final String window = canonicalWindow(rawWindow);
        final long since = switch (window) {
            case AuditKeys.WINDOW_ALL -> 0L;
            case AuditKeys.WINDOW_1H -> now - 60L * 60L * 1000L;
            case AuditKeys.WINDOW_24H -> now - 24L * 60L * 60L * 1000L;
            case AuditKeys.WINDOW_30D -> now - AuditKeys.MAX_WINDOW_MS;
            default -> now - 7L * 24L * 60L * 60L * 1000L;
        };
        return new Filter(actor, action, target, outcome, window, since);
    }

    /** The canonical window id: a known id, or {@link AuditKeys#DEFAULT_WINDOW}. */
    public static String canonicalWindow(final String raw) {
        if (raw == null || raw.isBlank()) {
            return AuditKeys.DEFAULT_WINDOW;
        }
        final String needle = raw.trim().toLowerCase(Locale.ROOT);
        for (final String window : AuditKeys.WINDOWS) {
            if (window.equals(needle)) {
                return window;
            }
        }
        return AuditKeys.DEFAULT_WINDOW;
    }

    /** The outcome filter: {@code all} and unknown values both mean every outcome. */
    public static AuditOutcome parseOutcomeFilter(final String raw) {
        if (raw == null || raw.isBlank()
                || AuditKeys.OUTCOME_ALL.equalsIgnoreCase(raw.trim())) {
            return null;
        }
        return AuditOutcome.byId(raw).orElse(null);
    }

    /**
     * One page of the audit log, newest first, after filtering.
     *
     * <p>Newest first because the question being asked of an audit log is almost always "what just
     * happened". {@code page} is clamped to the true page count, so a stale client asking for page
     * nine of a two-page answer gets the last page instead of an empty box.
     */
    public static Page page(final GovSavedData data, final Filter filter, final int page) {
        if (data == null) {
            return new Page(List.of(), 0, 0, 1);
        }
        final Filter safe = filter == null
                ? new Filter("", "", "", null, AuditKeys.DEFAULT_WINDOW, 0L) : filter;
        final List<AuditEntry> matching = new ArrayList<>();
        final List<AuditEntry> all = data.audit();
        for (int i = all.size() - 1; i >= 0; i--) {
            final AuditEntry entry = all.get(i);
            if (safe.matches(entry)) {
                matching.add(entry);
            }
        }
        final int pages = Math.max(1,
                (matching.size() + AuditKeys.PAGE_SIZE - 1) / AuditKeys.PAGE_SIZE);
        final int current = Math.max(0, Math.min(page, pages - 1));
        final int start = current * AuditKeys.PAGE_SIZE;
        final int end = Math.min(matching.size(), start + AuditKeys.PAGE_SIZE);
        final List<AuditEntry> rows = start >= matching.size()
                ? List.of() : List.copyOf(matching.subList(start, end));
        return new Page(rows, matching.size(), current, pages);
    }

    /** One entry by sequence number, or {@code null}. */
    public static AuditEntry bySeq(final GovSavedData data, final long seq) {
        if (data == null || seq <= 0L) {
            return null;
        }
        for (final AuditEntry entry : data.audit()) {
            if (entry.seq() == seq) {
                return entry;
            }
        }
        return null;
    }

    /**
     * The {@code AUDIT_PAGE} answer as a field map.
     *
     * <p>Compact by construction: eight rows of six short fields. The payload summary and reason
     * live only in the detail answer, because eight full audit lines would exceed
     * {@link GovFields#MAX_ENCODED} and the field encoder drops whole pairs (M3 tip t47).
     */
    public static Map<String, String> listFields(
            final GovSavedData data, final Filter filter, final int page,
            final Set<GovCapability> capabilities) {
        return AuditFields.listFields(data, filter, page, capabilities);
    }

    /** The {@code AUDIT_INFO} answer: every field of one line. */
    public static Map<String, String> detailFields(final AuditEntry entry) {
        return AuditFields.detailFields(entry);
    }

    /** A refusal for an audit id that does not parse; the command layer uses the same message. */
    public static String noSuchEntryMessage(final String raw) {
        return "There is no audit entry '" + Sanitizer.singleLine(raw, Sanitizer.MAX_ID) + "'.";
    }

    /**
     * Appends a line for an action taken by a player.
     *
     * @param data the government data
     * @param actor the acting player
     * @param action the action verb, normally the {@code OpCode} name
     * @param target the resolved target, or {@code null} when the action had no person target
     * @param payload the raw payload, summarized and sanitized by this method
     * @param reason the stated reason, sanitized by this method
     * @return the entry that was appended
     */
    public static AuditEntry append(
            final GovSavedData data,
            final ServerPlayer actor,
            final String action,
            final GovTargets.Resolved target,
            final java.util.Map<String, String> payload,
            final String reason) {
        return AuditAppender.append(data, actor, action, target, payload, reason);
    }

    /**
     * Appends a line for an action the server took by itself, such as seeding or a migration.
     *
     * @param data the government data
     * @param action the action verb
     * @param reason the stated reason, sanitized by this method
     * @return the entry that was appended
     */
    public static AuditEntry appendSystem(
            final GovSavedData data, final String action, final String reason) {
        return AuditAppender.appendSystem(data, action, reason);
    }

    /**
     * Appends a line with an explicit outcome, for a service that records its own refusals.
     *
     * <p>Only safety-critical services use this: an ordinary refusal is a DEBUG line, not an audit
     * entry, so a client in a loop cannot consume the retention ceiling.
     */
    public static AuditEntry appendOutcome(
            final GovSavedData data,
            final UUID actorUuid,
            final String actorName,
            final String actorJobId,
            final String action,
            final AuditOutcome outcome,
            final UUID targetUuid,
            final String targetName,
            final String payloadSummary,
            final String reason) {
        return AuditAppender.appendOutcome(data, actorUuid, actorName, actorJobId, action, outcome,
                targetUuid, targetName, payloadSummary, reason);
    }

    /** Appends a fully described line. Callers should prefer the overloads above. */
    public static AuditEntry append(
            final GovSavedData data,
            final UUID actorUuid,
            final String actorName,
            final String actorJobId,
            final String action,
            final UUID targetUuid,
            final String targetName,
            final String payloadSummary,
            final String reason) {
        return AuditAppender.append(data, actorUuid, actorName, actorJobId, action, targetUuid,
                targetName, payloadSummary, reason);
    }

    /**
     * One page of the audit log, newest first, without filters.
     *
     * <p>Retained for callers from earlier milestones and for diagnostics. New code should use
     * {@link #page(GovSavedData, Filter, int)}.
     */
    public static List<AuditEntry> page(final GovSavedData data, final int offset, final int size) {
        final List<AuditEntry> all = data.audit();
        if (all.isEmpty() || size <= 0 || offset >= all.size() || offset < 0) {
            return List.of();
        }
        final List<AuditEntry> newestFirst = new ArrayList<>(all);
        Collections.reverse(newestFirst);
        final int end = Math.min(newestFirst.size(), offset + size);
        return List.copyOf(newestFirst.subList(offset, end));
    }
}
