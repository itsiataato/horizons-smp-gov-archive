package com.horizonssmp.gov.server;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.horizonssmp.gov.common.AuditKeys;
import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.GovFields;
import com.horizonssmp.gov.config.GovConfig;

/**
 * The two-step season reset.
 *
 * <p>Safety is the whole design:
 *
 * <ol>
 *   <li>{@link #begin} computes the exact plan and counts, generates a single-use token, and writes
 *       nothing. It is refused only for a genuine precondition.</li>
 *   <li>{@link #commit} checks the token (actor, data instance, expiry, single use), writes a full
 *       JSON backup, re-reads it and verifies its sha256, and only then calls
 *       {@link GovSavedData#clearSeason}. A token is consumed even when a later step refuses, so a
 *       failed attempt cannot be replayed.</li>
 * </ol>
 *
 * <p>The backup is written at commit, not at begin. That guarantees the backup describes the exact
 * state being cleared even if records changed between the two commands, and it avoids writing a
 * backup for a plan nobody ever committed. The backup is never deleted by this class.
 *
 * <p>The clear itself is one in-memory pass with no I/O. The vanilla {@code SavedData} save writes
 * a temporary file and moves it into place, so a crash before the next save leaves the previous
 * {@code gov.dat} intact. The service catches a runtime failure, logs it loudly and writes a REFUSED
 * audit line; it never deletes a file.
 */
public final class SeasonResetService {

    /** The synthetic actor used when the console or RCON runs a reset command. */
    public static final UUID CONSOLE_ACTOR =
            UUID.nameUUIDFromBytes("gov:console".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    /** The display name recorded for the console actor. */
    public static final String CONSOLE_NAME = "CONSOLE";

    /** The sections a reset keeps, for the status and begin answers. */
    public static final String KEEP_TEXT =
            "job definitions, player capabilities, audit log, warrants, plate scans, config";

    private SeasonResetService() {
    }

    /** Who is acting. The job id is read from the data when a line is audited. */
    public record Actor(UUID uuid, String name) {

        public Actor {
            name = name == null || name.isBlank() ? CONSOLE_NAME : name;
        }

        public static Actor console() {
            return new Actor(CONSOLE_ACTOR, CONSOLE_NAME);
        }
    }

    /**
     * The reset policy, copied out of config once per request.
     *
     * @param clearBusinesses whether business records are in scope
     * @param tokenTtlMillis how long a token stays valid
     */
    public record Policy(boolean clearBusinesses, long tokenTtlMillis) {

        public Policy {
            tokenTtlMillis = Math.max(1L, tokenTtlMillis);
        }

        public static Policy fromConfig() {
            return new Policy(
                    GovConfig.seasonClearBusinesses(),
                    Math.max(1, GovConfig.seasonResetTokenSeconds()) * 1000L);
        }

        /** The token lifetime in whole seconds, for display. */
        public int tokenSeconds() {
            return (int) Math.max(1L, tokenTtlMillis / 1000L);
        }
    }

    /**
     * The exact scope of a reset.
     *
     * @param clearBusinesses whether business records are included
     * @param counts section id to count, including {@code player-jobs}
     * @param sections the section ids that will be cleared, in display order
     */
    public record Plan(boolean clearBusinesses, Map<String, Integer> counts, List<String> sections) {

        public Plan {
            counts = counts == null ? Map.of() : Map.copyOf(counts);
            sections = sections == null ? List.of() : List.copyOf(sections);
        }
    }

    /**
     * The outcome of a begin or commit.
     *
     * @param plan the scope
     * @param token the token on a begin answer, otherwise empty
     * @param expiresAt epoch milliseconds the token expires, or {@code 0}
     * @param backup the verified backup on a commit answer, or {@code null}
     */
    public record Outcome(
            boolean ok, ErrorCode code, String message, Plan plan,
            String token, long expiresAt, GovExportService.Result backup) {

        public Outcome {
            message = message == null ? "" : message;
            token = token == null ? "" : token;
        }

        public static Outcome refused(final ErrorCode code, final String message) {
            return new Outcome(false, code, message, null, "", 0L, null);
        }

        public static Outcome begun(final Plan plan, final String token, final long expiresAt) {
            return new Outcome(true, null, "", plan, token, expiresAt, null);
        }

        public static Outcome committed(
                final Plan plan, final GovExportService.Result backup) {
            return new Outcome(true, null, "", plan, "", 0L, backup);
        }
    }

    // ------------------------------------------------------------------ begin

    public static Outcome begin(
            final GovSavedData data, final Actor actor,
            final Policy policy, final long now) {
        return SeasonResetOperations.begin(data, actor, policy, now);
    }

    // ----------------------------------------------------------------- commit

    public static Outcome commit(
            final GovSavedData data, final Path runDir, final Actor actor,
            final String rawToken, final long now) {
        return SeasonResetOperations.commit(data, runDir, actor, rawToken, now);
    }

    // ----------------------------------------------------------------- status

    public static Plan status(final GovSavedData data, final Actor actor, final Policy policy) {
        final Policy safe = policy == null ? Policy.fromConfig() : policy;
        return SeasonResetPlanning.plan(data, safe.clearBusinesses());
    }

    public static long pendingExpiry(
            final GovSavedData data, final Actor actor, final long now) {
        return SeasonResetState.pendingExpiry(data, actor, now);
    }

    public static Map<String, String> statusFields(
            final GovSavedData data, final Actor actor, final Policy policy, final long now) {
        final Policy safe = policy == null ? Policy.fromConfig() : policy;
        final Plan plan = SeasonResetPlanning.plan(data, safe.clearBusinesses());
        final long expiry = SeasonResetState.pendingExpiry(data, actor, now);
        final Map<String, String> fields = new LinkedHashMap<>();
        fields.put(AuditKeys.CAN_ADMIN, "true");
        fields.put(AuditKeys.SEASON_SECTIONS, String.join(",", plan.sections()));
        fields.put(AuditKeys.SEASON_COUNTS, countsText(plan.counts()));
        fields.put(AuditKeys.SEASON_COUNTS_TEXT, humanCounts(plan.counts()));
        fields.put(AuditKeys.SEASON_KEEP, KEEP_TEXT);
        fields.put(AuditKeys.SEASON_CLEAR_BUSINESSES, Boolean.toString(plan.clearBusinesses()));
        fields.put(AuditKeys.SEASON_TOKEN_SECONDS, Integer.toString(safe.tokenSeconds()));
        fields.put(AuditKeys.SEASON_PENDING, Boolean.toString(expiry > 0L));
        fields.put(AuditKeys.SEASON_EXPIRES_AT, Long.toString(expiry));
        fields.put(AuditKeys.SEASON_BACKUP_DIR, GovExportService.BACKUP_DIR_NAME + "/");
        fields.put(AuditKeys.SEASON_EXPORT_DIR, GovExportService.EXPORT_DIR_NAME + "/");
        return fields;
    }

    public static Map<String, String> beginFields(final Outcome outcome) {
        if (outcome == null || !outcome.ok()) {
            return Map.of();
        }
        final Map<String, String> fields = new LinkedHashMap<>();
        fields.put(AuditKeys.SEASON_TOKEN, outcome.token());
        fields.put(AuditKeys.SEASON_EXPIRES_AT, Long.toString(outcome.expiresAt()));
        fields.put(AuditKeys.SEASON_SECTIONS, String.join(",", outcome.plan().sections()));
        fields.put(AuditKeys.SEASON_COUNTS, countsText(outcome.plan().counts()));
        fields.put(AuditKeys.SEASON_COUNTS_TEXT, humanCounts(outcome.plan().counts()));
        fields.put(AuditKeys.SEASON_CLEAR_BUSINESSES,
                Boolean.toString(outcome.plan().clearBusinesses()));
        return fields;
    }

    public static Map<String, String> commitFields(final Outcome outcome) {
        if (outcome == null) {
            return Map.of();
        }
        final Map<String, String> fields = new LinkedHashMap<>();
        if (outcome.plan() != null) {
            fields.put(AuditKeys.SEASON_SECTIONS, String.join(",", outcome.plan().sections()));
            fields.put(AuditKeys.SEASON_COUNTS, countsText(outcome.plan().counts()));
            fields.put(AuditKeys.SEASON_COUNTS_TEXT, humanCounts(outcome.plan().counts()));
            fields.put(AuditKeys.SEASON_PLAYERS_RESET,
                    Integer.toString(outcome.plan().counts().getOrDefault("player-jobs", 0)));
        }
        if (outcome.backup() != null) {
            fields.put(AuditKeys.SEASON_BACKUP_PATH, outcome.backup().relativePath());
            fields.put(AuditKeys.SEASON_BACKUP_SHA256, outcome.backup().sha256());
            fields.put(AuditKeys.SEASON_BACKUP_BYTES, Long.toString(outcome.backup().bytes()));
        }
        return fields;
    }

    // ---------------------------------------------------------------- helpers

    public static Plan plan(final GovSavedData data, final boolean clearBusinesses) {
        return SeasonResetPlanning.plan(data, clearBusinesses);
    }

    public static String countsText(final Map<String, Integer> counts) {
        if (counts == null || counts.isEmpty()) {
            return "none";
        }
        final List<String> keys = new ArrayList<>(counts.keySet());
        keys.sort(String::compareTo);
        final StringBuilder out = new StringBuilder();
        for (final String key : keys) {
            if (out.length() > 0) {
                out.append(", ");
            }
            out.append(key).append('=').append(counts.getOrDefault(key, 0));
        }
        return out.toString();
    }

    public static String humanCounts(final Map<String, Integer> counts) {
        return countsText(counts);
    }

    public static String encode(final Map<String, String> fields) {
        return GovFields.encode(fields);
    }
}