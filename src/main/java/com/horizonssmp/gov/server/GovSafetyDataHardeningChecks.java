package com.horizonssmp.gov.server;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.GovExportSection;
import com.horizonssmp.gov.common.GovCapabilities;
import com.horizonssmp.gov.common.GovCapability;

import com.horizonssmp.gov.common.GovExportSection;
import com.horizonssmp.gov.common.records.JobRecord;
import com.horizonssmp.gov.common.records.NbtSupport;
import com.horizonssmp.gov.common.records.PlayerRecord;
import com.horizonssmp.gov.config.GovConfig;

import net.minecraft.nbt.CompoundTag;

final class GovSafetyDataHardeningChecks {

    private GovSafetyDataHardeningChecks() {
    }

    /**
     * Forward-compatibility and corruption boundaries that are cheap to prove and expensive to
     * discover in production: integer saturation, unknown-section retention, audit retention on
     * load, sequence overflow, random filename suffixes, data-instance token binding, null names,
     * config bounds and a failed export that must not replace the run directory with a file.
     */
    static int checkDataHardening(final List<String> failures) {
        int checks = 0;
        final long now = System.currentTimeMillis();

        final CompoundTag numeric = new CompoundTag();
        numeric.putLong("high", Long.MAX_VALUE);
        numeric.putLong("low", Long.MIN_VALUE);
        checks++;
        if (NbtSupport.getInt(numeric, "high", 0) != Integer.MAX_VALUE) {
            failures.add("a long above the int range did not saturate at Integer.MAX_VALUE");
        }
        checks++;
        if (NbtSupport.getInt(numeric, "low", 0) != Integer.MIN_VALUE) {
            failures.add("a long below the int range did not saturate at Integer.MIN_VALUE");
        }

        // An unknown root section written by a newer build must survive load -> save unchanged.
        final CompoundTag future = new CompoundTag();
        future.putString("marker", "keep-me");
        final CompoundTag root = new CompoundTag();
        root.putInt("schemaVersion", GovSavedData.SCHEMA_VERSION);
        root.put("futureSection", future);
        final GovSavedData loaded = GovSavedData.load(root, null);
        final CompoundTag saved = loaded.save(new CompoundTag(), null);
        checks++;
        if (!saved.contains("futureSection")) {
            failures.add("an unknown gov.dat root section was dropped on round trip");
        }
        checks++;
        if (!"keep-me".equals(saved.getCompound("futureSection").getString("marker"))) {
            failures.add("an unknown gov.dat root section was not preserved verbatim");
        }

        final GovSavedData trim = new GovSavedData();
        for (int i = 1; i <= 5; i++) {
            trim.addAudit(new com.horizonssmp.gov.common.records.AuditEntry(i, 1_000L + i, null, "Tester", "", "TEST",
                    com.horizonssmp.gov.common.enums.AuditOutcome.ACCEPTED, null, "", "", ""));
        }
        checks++;
        final int dropped = trim.trimAuditTo(3);
        if (dropped != 2 || trim.audit().size() != 3 || trim.audit().get(0).seq() != 3L) {
            failures.add("trimAuditTo did not keep the newest entries and report the drop");
        }

        checks++;
        if (GovSavedData.nextAuditSequence(Long.MAX_VALUE) != 1L) {
            failures.add("the audit sequence did not restart after Long.MAX_VALUE");
        }
        checks++;
        if (GovSavedData.nextAuditSequence(0L) != 1L
                || GovSavedData.nextAuditSequence(7L) != 8L) {
            failures.add("the audit sequence did not advance from zero or a normal value");
        }

        boolean suffixOk = true;
        for (int i = 0; i < 512; i++) {
            if (!GovExportService.randomSuffix().matches("[0-9a-f]{8}")) {
                suffixOk = false;
                break;
            }
        }
        checks++;
        if (!suffixOk) {
            failures.add("an export filename suffix was not eight lowercase hex characters");
        }

        // Empty immutable sets are a legal caller input. EnumSet.copyOf(Set.of()) throws, so the
        // record canonical constructors must go through GovCapabilities.copyOf instead.
        checks++;
        try {
            final JobRecord emptyJob = new JobRecord(
                    "empty-job", "Empty Job", "", "", "", false, Set.of(), now, null);
            if (!emptyJob.capabilities().isEmpty()) {
                failures.add("an empty capability set became non-empty in JobRecord");
            }
        } catch (final RuntimeException e) {
            failures.add("JobRecord rejected an empty immutable capability set: " + e);
        }
        checks++;
        try {
            final PlayerRecord emptyPlayer = new PlayerRecord(
                    new UUID(0L, 64L), "Empty Player", now, now, "", 0L, null, "",
                    Set.of(), null, null, null, null, false);
            if (!emptyPlayer.capabilities().isEmpty()) {
                failures.add("an empty capability set became non-empty in PlayerRecord");
            }
        } catch (final RuntimeException e) {
            failures.add("PlayerRecord rejected an empty immutable capability set: " + e);
        }

        final GovSavedData one = GovSafetyHelpers.seasonFixture(now);
        final GovSavedData two = GovSafetyHelpers.seasonFixture(now);
        checks++;
        if (one.instanceId().equals(two.instanceId())) {
            failures.add("two government data instances share a season-token identity");
        }
        final SeasonResetService.Actor admin =
                new SeasonResetService.Actor(new UUID(0L, 61L), "DataAdmin");
        final SeasonResetService.Outcome begin = SeasonResetService.begin(
                one, admin, new SeasonResetService.Policy(false, 60_000L), now);
        checks++;
        if (!begin.ok()) {
            failures.add("season begin failed before the cross-data token case");
        } else {
            final int finesBefore = one.fines().size();
            final SeasonResetService.Outcome cross = SeasonResetService.commit(
                    two, Path.of("."), admin, begin.token(), now + 1L);
            checks++;
            if (cross.ok() || cross.code() != ErrorCode.BAD_TOKEN) {
                failures.add("a season token was accepted by a different data instance");
            }
            checks++;
            if (one.fines().size() != finesBefore) {
                failures.add("a cross-data season token changed the issuing data set");
            }
        }

        checks++;
        if (!SeasonResetService.CONSOLE_NAME.equals(
                new SeasonResetService.Actor(new UUID(0L, 62L), " ").name())) {
            failures.add("a blank actor name was not normalized to CONSOLE");
        }
        checks++;
        if (!PlayerRecord.blank(new UUID(0L, 63L), null, now).lastName().isEmpty()) {
            failures.add("a null player name was not stored as an empty name");
        }

        checks++;
        if (GovConfig.rateLimitActionsPer10s() < 1
                || GovConfig.rateLimitActionsPer10s() > 1000) {
            failures.add("rateLimitActionsPer10s is outside its documented range");
        }
        checks++;
        if (GovConfig.maxRequestsPerTick() < 4 || GovConfig.maxRequestsPerTick() > 4096) {
            failures.add("maxRequestsPerTick is outside its documented range");
        }
        checks++;
        if (GovConfig.seasonResetTokenSeconds() < 30
                || GovConfig.seasonResetTokenSeconds() > 3600) {
            failures.add("resetTokenSeconds is outside its documented range");
        }
        checks++;
        if (GovConfig.maxDetentionMinutesNoWarrant() < 1
                || GovConfig.maxDetentionMinutesNoWarrant() > 10) {
            failures.add("maxDetentionMinutesNoWarrant is outside its hard cap");
        }
        checks++;
        if (GovConfig.maxDetentionMinutesWithWarrant() < 1
                || GovConfig.maxDetentionMinutesWithWarrant() > 10_080) {
            failures.add("maxDetentionMinutesWithWarrant is outside its hard cap");
        }

        final Path scratch = GovSafetyHelpers.scratchDirectory(failures, "hardening");
        if (scratch != null) {
            final Path blocked = scratch.resolve("blocked-run");
            try {
                Files.writeString(blocked, "not a directory", StandardCharsets.UTF_8);
                final GovExportService.Result refused = GovExportService.exportSection(
                        one, blocked, GovExportSection.AUDIT, now);
                checks++;
                if (refused.ok()) {
                    failures.add("an export succeeded when the run directory was a regular file");
                }
                checks++;
                if (!Files.isRegularFile(blocked)) {
                    failures.add("a failed export replaced the run-directory file");
                }
            } catch (final IOException e) {
                failures.add("could not prepare the blocked-export fixture: " + e);
            }
        }
        return checks;
    }
}
