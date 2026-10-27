package com.horizonssmp.gov.server;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.horizonssmp.gov.common.ErrorCode;

final class GovExportFiles {

    private GovExportFiles() {
    }

    static String sha256(final byte[] bytes) {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            final byte[] hash = digest.digest(bytes);
            final StringBuilder out = new StringBuilder(hash.length * 2);
            for (final byte b : hash) {
                out.append(String.format(Locale.ROOT, "%02x", b));
            }
            return out.toString();
        } catch (final NoSuchAlgorithmException e) {
            // SHA-256 is required of every JRE; this cannot happen on a supported runtime.
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    static GovExportService.Result writeUnique(
            final Path directory, final String base, final byte[] bytes, final String format)
            throws IOException {
        Files.createDirectories(directory);
        for (int attempt = 0; attempt < GovExportService.MAX_NAME_ATTEMPTS; attempt++) {
            final String suffix = GovExportService.randomSuffix();
            final Path target = directory.resolve(base + "-" + suffix + ".json").normalize();
            if (!target.startsWith(directory) || Files.exists(target)) {
                continue;
            }
            final Path temp = directory.resolve(base + "-" + suffix + ".json.tmp-" + attempt).normalize();
            if (!temp.startsWith(directory)) {
                continue;
            }
            try {
                try (FileChannel channel = FileChannel.open(
                        temp, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                    final ByteBuffer buffer = ByteBuffer.wrap(bytes);
                    while (buffer.hasRemaining()) {
                        channel.write(buffer);
                    }
                    // A season backup is the gate before the reset. Force it to disk before the
                    // rename, so a power loss cannot leave the verified-looking file empty while
                    // the reset itself survives in the page cache.
                    channel.force(true);
                }
                try {
                    Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
                } catch (final AtomicMoveNotSupportedException e) {
                    // The filesystem cannot promise atomic rename. The temp file is already
                    // complete and forced, and the target name was chosen not to exist.
                    Files.move(temp, target);
                }
            } catch (final IOException e) {
                try {
                    Files.deleteIfExists(temp);
                } catch (final IOException ignored) {
                    // The temp file is ours; leaving it is safer than masking the original error.
                }
                throw e;
            }
            final String relative = relativize(directory.getParent(), target);
            final String digest = sha256(bytes);
            return verify(target, relative, digest, bytes.length, format);
        }
        return GovExportService.Result.refused(
                ErrorCode.EXPORT_FAILED, "Could not choose a unique export filename.");
    }

    static GovExportService.Result verify(
            final Path file, final String relativePath, final String expectedSha,
            final long expectedBytes, final String formatPrefix) {
        if (file == null || !Files.isRegularFile(file)) {
            return GovExportService.Result.refused(ErrorCode.EXPORT_FAILED, "The written file is missing.");
        }
        try {
            final byte[] bytes = Files.readAllBytes(file);
            if (expectedBytes >= 0L && bytes.length != expectedBytes) {
                return GovExportService.Result.refused(ErrorCode.EXPORT_FAILED, "The written file has the wrong size.");
            }
            final String actual = sha256(bytes);
            if (!actual.equalsIgnoreCase(expectedSha)) {
                return GovExportService.Result.refused(ErrorCode.EXPORT_FAILED, "The written file has the wrong sha256.");
            }
            final JsonElement parsed = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) {
                return GovExportService.Result.refused(ErrorCode.EXPORT_FAILED, "The written file is not a JSON object.");
            }
            final JsonObject root = parsed.getAsJsonObject();
            final JsonElement format = root.get("format");
            if (format == null || !format.isJsonPrimitive()
                    || !format.getAsString().startsWith(formatPrefix)) {
                return GovExportService.Result.refused(ErrorCode.EXPORT_FAILED, "The written file has the wrong format.");
            }
            return GovExportService.Result.written(relativePath, bytes.length, actual, file, format.getAsString());
        } catch (final IOException | RuntimeException e) {
            return GovExportService.Result.refused(
                    ErrorCode.EXPORT_FAILED, "The written file could not be verified: " + e.getMessage());
        }
    }

    static String relativize(final Path runDir, final Path file) {
        final Path base = runDir == null ? file.getParent() : runDir.toAbsolutePath().normalize();
        return base.relativize(file.toAbsolutePath().normalize()).toString().replace('\\', '/');
    }
}