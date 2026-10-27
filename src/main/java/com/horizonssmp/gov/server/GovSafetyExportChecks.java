package com.horizonssmp.gov.server;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.horizonssmp.gov.common.GovExportSection;

import com.horizonssmp.gov.common.records.PlayerRecord;

final class GovSafetyExportChecks {

    private GovSafetyExportChecks() {
    }

    /** Export and backup safety: fixed directory, no overwrite, hash, parse, traversal refusal. */
    static int checkExportSafety(final List<String> failures) {
        int checks = 0;
        final long now = System.currentTimeMillis();
        final GovSavedData data = new GovSavedData();
        data.seedIfNeeded(now, false);
        AuditService.appendSystem(data, "SEED_JOBS", "fixture");
        data.putPlayer(PlayerRecord.blank(new UUID(0L, 41L), "ExportTarget", now));

        final Path scratch = GovSafetyHelpers.scratchDirectory(failures, "export");
        if (scratch == null) {
            return 1;
        }
        final Path runDir = scratch.resolve("run");

        final GovExportService.Result first =
                GovExportService.exportSection(data, runDir, GovExportSection.AUDIT, now);
        checks++;
        if (!first.ok()) {
            failures.add("audit export failed: " + first.message());
            return checks;
        }
        final Path file = runDir.resolve(first.relativePath()).normalize();
        checks++;
        if (!Files.isRegularFile(file)
                || !file.startsWith(runDir.resolve(GovExportService.EXPORT_DIR_NAME))) {
            failures.add("audit export is not a file under gov-exports");
        }
        checks++;
        try {
            final byte[] bytes = Files.readAllBytes(file);
            if (bytes.length != first.bytes()
                    || !GovExportService.sha256(bytes).equals(first.sha256())) {
                failures.add("audit export size or sha256 does not match the file");
            }
            final JsonObject root = JsonParser.parseString(
                    new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
            final JsonArray audit = root.getAsJsonObject("data").getAsJsonArray("audit");
            if (audit == null || audit.size() != data.audit().size()) {
                failures.add("audit export JSON does not contain the whole log");
            }
        } catch (final IOException | RuntimeException e) {
            failures.add("audit export did not parse: " + e);
        }

        final GovExportService.Result second =
                GovExportService.exportSection(data, runDir, GovExportSection.AUDIT, now);
        checks++;
        if (!second.ok() || second.relativePath().equals(first.relativePath())
                || !Files.isRegularFile(runDir.resolve(second.relativePath()))) {
            failures.add("a second export overwrote or lost the first one");
        }

        checks++;
        if (GovExportSection.byId("../../etc/passwd").isPresent()
                || GovExportSection.byId("..\\..\\windows").isPresent()
                || GovExportSection.byId("audit/../players").isPresent()) {
            failures.add("export section parser accepted a path traversal");
        }
        checks++;
        if (!GovExportService.safeSlug("../../etc/passwd").isEmpty()
                || !GovExportService.safeSlug("a/b").isEmpty()
                || !GovExportService.safeSlug(".").isEmpty()
                || !GovExportService.safeSlug("").isEmpty()
                || !"audit".equals(GovExportService.safeSlug(" Audit "))) {
            failures.add("safeSlug did not refuse a path or accept a plain id");
        }
        checks++;
        if (GovExportService.resolveWithin(runDir, "../world/gov.dat") != null
                || GovExportService.resolveWithin(runDir, "/etc/passwd") != null
                || GovExportService.resolveWithin(runDir, "gov-exports/ok.json") == null) {
            failures.add("resolveWithin did not refuse an escaping path");
        }

        final GovExportService.Result backup = GovExportService.writeBackup(data, runDir, now);
        checks++;
        if (!backup.ok()) {
            failures.add("full backup failed: " + backup.message());
        } else {
            checks++;
            if (!Files.isRegularFile(runDir.resolve(backup.relativePath()))
                    || !backup.relativePath().startsWith(GovExportService.BACKUP_DIR_NAME + "/")) {
                failures.add("backup is not a file under gov-backups");
            }
            checks++;
            final GovExportService.Result verified = GovExportService.verifyBackup(
                    runDir, backup.relativePath(), backup.sha256(), backup.bytes());
            if (!verified.ok()) {
                failures.add("backup did not pass its own verification: " + verified.message());
            }
            checks++;
            if (GovExportService.verifyBackup(
                    runDir, backup.relativePath(), "0".repeat(64), backup.bytes()).ok()) {
                failures.add("backup verification accepted a wrong sha256");
            }
            checks++;
            try {
                final JsonObject root = JsonParser.parseString(
                        Files.readString(runDir.resolve(backup.relativePath()),
                                StandardCharsets.UTF_8)).getAsJsonObject();
                if (!root.get("format").getAsString().equals(GovExportService.BACKUP_FORMAT)
                        || !root.has("counts")
                        || !root.getAsJsonObject("data").has("players")
                        || !root.getAsJsonObject("data").has("audit")) {
                    failures.add("backup JSON is missing its format, counts or sections");
                }
            } catch (final IOException | RuntimeException e) {
                failures.add("backup JSON did not parse: " + e);
            }
        }
        return checks;
    }
}
