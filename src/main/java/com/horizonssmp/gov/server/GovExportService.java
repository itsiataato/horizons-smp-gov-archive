package com.horizonssmp.gov.server;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import com.horizonssmp.gov.Gov;
import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.GovExportSection;

/**
 * The safe file writer behind audit exports and season backups.
 *
 * <h2>Why the path rules are here</h2>
 *
 * <p>The caller never supplies a path or a filename. It supplies a {@link GovExportSection} (an
 * enum) and the service derives everything else: the fixed directory, a timestamped filename and a
 * short random suffix. A raw request string cannot become a path segment, so {@code ../../world}
 * has no representation and is refused by the section parser before this class is reached.
 * {@link #safeSlug(String)} and {@link #resolveWithin(Path, String)} are the belt-and-braces
 * checks, and the startup check exercises both.
 *
 * <p>Every file is written to a uniquely named temporary file in the destination directory and
 * moved into place without {@code REPLACE_EXISTING}. An existing export is never overwritten: a
 * collision gets another random suffix. The result reports the path relative to the server run
 * directory (always with {@code /}), the exact byte size and the lowercase sha256.
 *
 * <p>Both directories are under the server run directory: {@code run/gov-exports/} and
 * {@code run/gov-backups/}. Nothing is ever written to {@code /tmp}; the startup self-check passes
 * a scratch directory under the build tree instead.
 */
public final class GovExportService {

    /** The fixed export directory name under the server run directory. */
    public static final String EXPORT_DIR_NAME = "gov-exports";
    /** The fixed backup directory name under the server run directory. */
    public static final String BACKUP_DIR_NAME = "gov-backups";
    /** The export format id. */
    public static final String EXPORT_FORMAT = "gov-export-v1";
    /** The backup format id. */
    public static final String BACKUP_FORMAT = "gov-backup-v1";

    private static final DateTimeFormatter FILE_STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT).withZone(ZoneOffset.UTC);
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();
    static final int MAX_NAME_ATTEMPTS = 12;

    private GovExportService() {
    }

    /**
     * The outcome of one export or backup.
     *
     * @param relativePath the path relative to the run directory, or empty on failure
     * @param bytes the exact byte size, or {@code 0} on failure
     * @param sha256 the lowercase hex digest, or empty on failure
     * @param absolutePath the absolute path for server-side verification, or {@code null}
     * @param format the format id written into the JSON root
     */
    public record Result(
            boolean ok, ErrorCode code, String message,
            String relativePath, long bytes, String sha256, Path absolutePath, String format) {

        public Result {
            message = message == null ? "" : message;
            relativePath = relativePath == null ? "" : relativePath;
            sha256 = sha256 == null ? "" : sha256;
            format = format == null ? "" : format;
        }

        public static Result refused(final ErrorCode code, final String message) {
            return new Result(false, code, message, "", 0L, "", null, "");
        }

        public static Result written(
                final String relativePath, final long bytes, final String sha256,
                final Path absolutePath, final String format) {
            return new Result(true, null, "", relativePath, bytes, sha256, absolutePath, format);
        }
    }

    /** The export directory for a run directory. */
    public static Path exportsDir(final Path runDir) {
        return runDir.resolve(EXPORT_DIR_NAME).toAbsolutePath().normalize();
    }

    /** The backup directory for a run directory. */
    public static Path backupsDir(final Path runDir) {
        return runDir.resolve(BACKUP_DIR_NAME).toAbsolutePath().normalize();
    }

    /**
     * Writes one registry section as JSON.
     *
     * @param data the government data
     * @param runDir the server run directory
     * @param section the section, already parsed from the allow-list
     * @param now epoch milliseconds used in the filename and metadata
     */
    public static Result exportSection(
            final GovSavedData data, final Path runDir,
            final GovExportSection section, final long now) {
        if (data == null || runDir == null || section == null) {
            return Result.refused(ErrorCode.EXPORT_FAILED, "No data or section to export.");
        }
        try {
            final JsonObject root = GovExportJson.metadata(EXPORT_FORMAT, now);
            root.addProperty("section", section.id());
            final JsonArray records = GovExportJson.sectionArray(data, section);
            final JsonObject dataObject = new JsonObject();
            dataObject.add(section.jsonKey(), records);
            root.add("data", dataObject);

            final JsonObject counts = new JsonObject();
            counts.addProperty(section.id(), records.size());
            root.add("counts", counts);

            final byte[] bytes = GSON.toJson(root).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            final String base = "gov-" + section.id() + "-" + FILE_STAMP.format(Instant.ofEpochMilli(now));
            return GovExportFiles.writeUnique(exportsDir(runDir), base, bytes, EXPORT_FORMAT);
        } catch (final IOException | RuntimeException e) {
            Gov.LOGGER.warn("[gov] export of section {} failed: {}", section.id(), e.toString());
            return Result.refused(
                    ErrorCode.EXPORT_FAILED, "The export could not be written safely: " + e.getMessage());
        }
    }

    /**
     * Writes a full backup of every section, including the audit log.
     *
     * <p>This is the file a season reset must verify before it clears anything. It is never deleted
     * by the mod, and a failed or partial write never becomes the reported backup: the result is
     * returned only after the file exists, parses and its digest has been recomputed.
     */
    public static Result writeBackup(
            final GovSavedData data, final Path runDir, final long now) {
        if (data == null || runDir == null) {
            return Result.refused(ErrorCode.EXPORT_FAILED, "No data to back up.");
        }
        try {
            final JsonObject root = GovExportJson.metadata(BACKUP_FORMAT, now);
            root.addProperty("schemaVersion", data.schemaVersion());
            root.addProperty("templatesSeeded", data.templatesSeeded());
            root.addProperty("auditSeq", data.auditSeq());

            final JsonObject dataObject = GovExportJson.fullData(data);
            root.add("data", dataObject);

            final JsonObject counts = new JsonObject();
            for (final Map.Entry<String, com.google.gson.JsonElement> entry : dataObject.entrySet()) {
                counts.addProperty(entry.getKey(), entry.getValue().getAsJsonArray().size());
            }
            root.add("counts", counts);

            final byte[] bytes = GSON.toJson(root).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            final String base = "gov-backup-" + FILE_STAMP.format(Instant.ofEpochMilli(now));
            final Result written = GovExportFiles.writeUnique(backupsDir(runDir), base, bytes, BACKUP_FORMAT);
            if (!written.ok()) {
                return written;
            }
            return GovExportFiles.verify(written.absolutePath(), written.relativePath(), written.sha256(),
                    written.bytes(), BACKUP_FORMAT);
        } catch (final IOException | RuntimeException e) {
            Gov.LOGGER.warn("[gov] season backup failed: {}", e.toString());
            return Result.refused(
                    ErrorCode.EXPORT_FAILED, "The backup could not be written safely: " + e.getMessage());
        }
    }

    /**
     * Re-reads a written backup and verifies existence, size, digest and parseability.
     *
     * <p>The commit path calls this even though {@link #writeBackup} already verified the file
     * once: a file can be removed or changed between two statements, and the whole point of the
     * gate is that the destructive step never runs on an unverified backup.
     */
    public static Result verifyBackup(
            final Path runDir, final String relativePath, final String sha256, final long bytes) {
        if (runDir == null || relativePath == null || relativePath.isBlank()
                || sha256 == null || sha256.isBlank()) {
            return Result.refused(ErrorCode.EXPORT_FAILED, "The backup record is incomplete.");
        }
        final Path file = resolveWithin(runDir, relativePath);
        if (file == null || !file.startsWith(backupsDir(runDir))) {
            return Result.refused(ErrorCode.EXPORT_FAILED, "The backup path is not inside gov-backups.");
        }
        return GovExportFiles.verify(file, relativePath, sha256, bytes, BACKUP_FORMAT);
    }

    /**
     * Resolves a relative name inside a fixed root, refusing anything that escapes it.
     *
     * <p>Returns {@code null} rather than throwing, so a caller can turn a hostile path into a
     * clean {@code EXPORT_FAILED} refusal. Absolute paths and names containing {@code ..} are
     * refused outright, before normalization.
     */
    public static Path resolveWithin(final Path root, final String relativeName) {
        if (root == null || relativeName == null || relativeName.isBlank()) {
            return null;
        }
        final String name = relativeName.replace('\\', '/');
        if (name.startsWith("/") || name.contains("..") || name.indexOf('\0') >= 0) {
            return null;
        }
        final Path normalizedRoot = root.toAbsolutePath().normalize();
        final Path candidate = normalizedRoot.resolve(name).normalize();
        return candidate.startsWith(normalizedRoot) ? candidate : null;
    }

    /**
     * A filename-safe slug: lowercase letters, digits, dot, underscore and hyphen only.
     *
     * <p>Empty when the input is empty, too long, or contains a separator, a parent segment or a
     * control character. The section allow-list is the primary defence; this is the independent
     * second check the startup corpus exercises.
     */
    public static String safeSlug(final String raw) {
        if (raw == null) {
            return "";
        }
        final String text = raw.trim().toLowerCase(Locale.ROOT);
        if (text.isEmpty() || text.equals(".") || text.length() > 64 || text.contains("..")) {
            return "";
        }
        final StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            final char c = text.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '.' || c == '_' || c == '-') {
                out.append(c);
            } else {
                return "";
            }
        }
        return out.toString();
    }

    /** The lowercase hex sha256 of a byte array. */
    public static String sha256(final byte[] bytes) {
        return GovExportFiles.sha256(bytes);
    }

    // ------------------------------------------------------------------ write

    /**
     * An eight-character lowercase hex suffix for a unique filename.
     *
     * <p>{@link Long#toHexString(long)} does not pad: a draw of 0 is {@code "0"}, and the old
     * {@code substring(0, 8)} then threw {@link StringIndexOutOfBoundsException}, turning a rare
     * random draw into a failed export. Left-padding first makes the length guaranteed.
     */
    static String randomSuffix() {
        final String hex = Long.toHexString(ThreadLocalRandom.current().nextLong());
        return hex.length() >= 8 ? hex.substring(0, 8) : "0".repeat(8 - hex.length()) + hex;
    }

    /** Section counts for a status answer, keyed by section id. */
    public static Map<String, Integer> counts(final GovSavedData data, final boolean includeBusinesses) {
        final Map<String, Integer> counts = new LinkedHashMap<>();
        if (data == null) {
            return counts;
        }
        counts.put(GovExportSection.PLAYERS.id(), data.players().size());
        counts.put(GovExportSection.JOBS.id(), data.jobs().size());
        counts.put(GovExportSection.VEHICLES.id(), data.vehicles().size());
        counts.put(GovExportSection.WEAPONS.id(), data.weapons().size());
        counts.put(GovExportSection.PERMITS.id(), data.weaponPermits().size());
        counts.put(GovExportSection.AREA_PERMITS.id(), data.areaPermits().size());
        counts.put(GovExportSection.DETENTIONS.id(), data.detentions().size());
        counts.put(GovExportSection.FINES.id(), data.fines().size());
        counts.put(GovExportSection.NOTES.id(), data.notes().size());
        if (includeBusinesses) {
            counts.put(GovExportSection.BUSINESSES.id(), data.businesses().size());
        }
        counts.put("licenses", data.licenses().size());
        return counts;
    }
}