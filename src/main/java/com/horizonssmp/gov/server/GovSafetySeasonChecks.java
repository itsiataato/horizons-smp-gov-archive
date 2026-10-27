package com.horizonssmp.gov.server;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.GovCapabilities;
import com.horizonssmp.gov.common.GovCapability;
import com.horizonssmp.gov.common.enums.AuditOutcome;
import com.horizonssmp.gov.common.enums.DutyState;

import com.horizonssmp.gov.common.records.PlayerRecord;

final class GovSafetySeasonChecks {

    private GovSafetySeasonChecks() {
    }

    /** Begin/commit on scratch data: refusal without a token, backup first, exact clear scope. */
    static int checkSeasonReset(final List<String> failures) {
        int checks = 0;
        final long now = System.currentTimeMillis();
        final GovSavedData data = GovSafetyHelpers.seasonFixture(now);
        final Path scratch = GovSafetyHelpers.scratchDirectory(failures, "season");
        if (scratch == null) {
            return 1;
        }
        final Path runDir = scratch.resolve("run");
        final SeasonResetService.Actor admin = new SeasonResetService.Actor(
                new UUID(0L, 51L), "SeasonAdmin");
        final SeasonResetService.Actor other = new SeasonResetService.Actor(
                new UUID(0L, 52L), "Other");
        final SeasonResetService.Policy policy =
                new SeasonResetService.Policy(false, 60_000L);

        final int finesBefore = data.fines().size();
        final int notesBefore = data.notes().size();
        final int businessesBefore = data.businesses().size();
        final int jobsBefore = data.jobs().size();

        final SeasonResetService.Outcome noToken =
                SeasonResetService.commit(data, runDir, admin, "", now);
        checks++;
        if (noToken.ok() || noToken.code() != ErrorCode.BAD_TOKEN
                || data.fines().size() != finesBefore) {
            failures.add("season reset without a token was not refused cleanly");
        }

        final SeasonResetService.Outcome begin =
                SeasonResetService.begin(data, admin, policy, now);
        checks++;
        if (!begin.ok() || begin.token().length() < 16
                || begin.plan().sections().contains("businesses")
                || begin.plan().counts().getOrDefault("fines", 0) != 1
                || begin.plan().counts().getOrDefault("notes", 0) != 1) {
            failures.add("season begin did not return a token and the exact plan");
        }
        final String token = begin.token();

        final SeasonResetService.Outcome wrongActor =
                SeasonResetService.commit(data, runDir, other, token, now + 1L);
        checks++;
        if (wrongActor.ok() || data.fines().size() != finesBefore) {
            failures.add("season token was not tied to its actor");
        }
        checks++;
        if (SeasonResetService.commit(data, runDir, admin, token, now + 2L).ok()) {
            failures.add("a consumed season token was accepted a second time");
        }

        final SeasonResetService.Outcome expiredBegin =
                SeasonResetService.begin(data, admin, policy, now + 10L);
        checks++;
        if (!expiredBegin.ok()) {
            failures.add("season begin failed before the expiry case");
        } else {
            final SeasonResetService.Outcome expired = SeasonResetService.commit(
                    data, runDir, admin, expiredBegin.token(), now + 10L + 60_001L);
            checks++;
            if (expired.ok() || expired.code() != ErrorCode.BAD_TOKEN) {
                failures.add("an expired season token was accepted");
            }
        }

        final SeasonResetService.Outcome commitBegin =
                SeasonResetService.begin(data, admin, policy, now + 20L);
        checks++;
        if (!commitBegin.ok()) {
            failures.add("season begin failed before the commit case");
            return checks;
        }
        final SeasonResetService.Outcome commit = SeasonResetService.commit(
                data, runDir, admin, commitBegin.token(), now + 21L);
        checks++;
        if (!commit.ok() || commit.backup() == null) {
            failures.add("season commit did not succeed with a verified backup: "
                    + commit.message());
            return checks;
        }
        checks++;
        if (!Files.isRegularFile(runDir.resolve(commit.backup().relativePath()))
                || !GovExportService.verifyBackup(
                        runDir, commit.backup().relativePath(),
                        commit.backup().sha256(), commit.backup().bytes()).ok()) {
            failures.add("season backup is missing or does not verify after the commit");
        }
        checks++;
        if (!data.fines().isEmpty() || !data.notes().isEmpty() || !data.licenses().isEmpty()
                || !data.vehicles().isEmpty() || !data.weapons().isEmpty()
                || !data.weaponPermits().isEmpty() || !data.areaPermits().isEmpty()
                || !data.detentions().isEmpty()) {
            failures.add("season commit did not clear every default section");
        }
        checks++;
        if (data.businesses().size() != businessesBefore) {
            failures.add("season commit cleared businesses under the default keep policy");
        }
        checks++;
        if (data.jobs().size() != jobsBefore) {
            failures.add("season commit removed job definitions");
        }
        checks++;
        final PlayerRecord player = data.player(new UUID(0L, 53L));
        if (player == null
                || !GovCapabilities.CITIZEN_JOB_ID.equals(player.primaryJobId())
                || player.duty() != DutyState.N_A
                || player.jail().active()) {
            failures.add("season commit did not reset the player to citizen and clear duty/jail");
        }
        checks++;
        if (player != null && !player.capabilities().contains(GovCapability.AUDIT_VIEW)) {
            failures.add("season commit removed a personal capability grant");
        }
        checks++;
        if (data.audit().stream().noneMatch(entry -> "SEASON_BEGIN".equals(entry.action()))
                || data.audit().stream().noneMatch(entry -> "SEASON_COMMIT".equals(entry.action())
                        && entry.outcome() == AuditOutcome.ACCEPTED)) {
            failures.add("season reset did not audit begin and commit");
        }
        checks++;
        if (SeasonResetService.commit(data, runDir, admin, commitBegin.token(), now + 22L).ok()) {
            failures.add("the committed token was reusable");
        }

        // A second scratch set proves the configured business-clearing policy is honoured.
        final GovSavedData businessData = GovSafetyHelpers.seasonFixture(now + 100L);
        final SeasonResetService.Outcome businessBegin = SeasonResetService.begin(
                businessData, admin, new SeasonResetService.Policy(true, 60_000L), now + 100L);
        checks++;
        if (!businessBegin.ok() || !businessBegin.plan().sections().contains("businesses")) {
            failures.add("season begin did not include businesses when configured");
        } else {
            final SeasonResetService.Outcome businessCommit = SeasonResetService.commit(
                    businessData, runDir, admin, businessBegin.token(), now + 101L);
            checks++;
            if (!businessCommit.ok() || !businessData.businesses().isEmpty()) {
                failures.add("season commit did not clear businesses when configured");
            }
        }
        return checks;
    }
}
