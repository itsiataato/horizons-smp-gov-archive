package com.horizonssmp.gov.server;

import java.nio.file.Path;
import java.util.Map;

import com.horizonssmp.gov.Gov;
import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.enums.AuditOutcome;

/**
 * Begin/commit orchestration for {@link SeasonResetService}.
 */
public final class SeasonResetOperations {

    private SeasonResetOperations() {
    }

    public static SeasonResetService.Outcome begin(
            final GovSavedData data, final SeasonResetService.Actor actor,
            final SeasonResetService.Policy policy, final long now) {
        if (data == null || actor == null || actor.uuid() == null) {
            return SeasonResetService.Outcome.refused(
                    ErrorCode.INVALID_STATE, "No data or actor for a reset.");
        }
        final SeasonResetService.Policy safe = policy == null
                ? SeasonResetService.Policy.fromConfig() : policy;
        final SeasonResetService.Plan plan = SeasonResetPlanning.plan(data, safe.clearBusinesses());
        SeasonResetState.pruneExpired(now);
        final long expiresAt = now + safe.tokenTtlMillis();
        String token = "";
        for (int attempt = 0; attempt < 8; attempt++) {
            token = SeasonResetState.newToken();
            if (!SeasonResetState.PENDING.containsKey(token)) {
                break;
            }
        }
        if (token.isEmpty()) {
            final String message = "A reset token could not be generated.";
            SeasonResetAudit.audit(data, actor, "SEASON_BEGIN", AuditOutcome.REFUSED,
                    "sections=" + String.join(",", plan.sections()) + "; "
                            + SeasonResetService.countsText(plan.counts()), message);
            return SeasonResetService.Outcome.refused(ErrorCode.INVALID_STATE, message);
        }
        if (SeasonResetState.PENDING.containsKey(token)) {
            final String message = "A reset token could not be generated.";
            SeasonResetAudit.audit(data, actor, "SEASON_BEGIN", AuditOutcome.REFUSED,
                    "sections=" + String.join(",", plan.sections()) + "; "
                            + SeasonResetService.countsText(plan.counts()), message);
            return SeasonResetService.Outcome.refused(ErrorCode.INVALID_STATE, message);
        }
        SeasonResetState.PENDING.put(token, new SeasonResetState.Pending(
                actor.uuid(), data.instanceId(), safe.clearBusinesses(),
                plan, expiresAt));
        Gov.LOGGER.info(
                "[gov] season reset begun by {}: {} (token expires in {}s)",
                actor.name(), SeasonResetService.countsText(plan.counts()), safe.tokenSeconds());
        SeasonResetAudit.audit(data, actor, "SEASON_BEGIN", AuditOutcome.ACCEPTED,
                "sections=" + String.join(",", plan.sections()) + "; "
                        + SeasonResetService.countsText(plan.counts()),
                "token expires at " + expiresAt);
        return SeasonResetService.Outcome.begun(plan, token, expiresAt);
    }

    public static SeasonResetService.Outcome commit(
            final GovSavedData data, final Path runDir, final SeasonResetService.Actor actor,
            final String rawToken, final long now) {
        if (data == null || actor == null || actor.uuid() == null) {
            return SeasonResetService.Outcome.refused(
                    ErrorCode.INVALID_STATE, "No data or actor for a reset.");
        }
        final String token = rawToken == null ? "" : rawToken.trim();
        SeasonResetState.pruneExpired(now);
        if (token.isEmpty()) {
            final String message = "A season reset needs a token from 'season reset begin'.";
            SeasonResetAudit.audit(data, actor, "SEASON_COMMIT", AuditOutcome.REFUSED,
                    "counts=" + SeasonResetPlanning.currentCounts(data), message);
            return SeasonResetService.Outcome.refused(ErrorCode.BAD_TOKEN, message);
        }
        final SeasonResetState.Pending pending = SeasonResetState.PENDING.remove(token);
        if (pending == null) {
            final String message = "That token is unknown, already used or expired.";
            SeasonResetAudit.audit(data, actor, "SEASON_COMMIT", AuditOutcome.REFUSED,
                    "token=invalid; counts=" + SeasonResetPlanning.currentCounts(data), message);
            return SeasonResetService.Outcome.refused(ErrorCode.BAD_TOKEN, message);
        }
        if (!actor.uuid().equals(pending.actor())) {
            final String message = "That token belongs to another actor.";
            SeasonResetAudit.audit(data, actor, "SEASON_COMMIT", AuditOutcome.REFUSED,
                    "token=other-actor; counts=" + SeasonResetPlanning.currentCounts(data), message);
            return SeasonResetService.Outcome.refused(ErrorCode.BAD_TOKEN, message);
        }
        if (!pending.dataId().equals(data.instanceId())) {
            final String message = "That token was issued for a different data set.";
            SeasonResetAudit.audit(data, actor, "SEASON_COMMIT", AuditOutcome.REFUSED,
                    "token=other-data; counts=" + SeasonResetPlanning.currentCounts(data), message);
            return SeasonResetService.Outcome.refused(ErrorCode.BAD_TOKEN, message);
        }
        if (now > pending.expiresAt()) {
            final String message = "That token has expired; begin again.";
            SeasonResetAudit.audit(data, actor, "SEASON_COMMIT", AuditOutcome.REFUSED,
                    "token=expired; counts=" + SeasonResetPlanning.currentCounts(data), message);
            return SeasonResetService.Outcome.refused(ErrorCode.BAD_TOKEN, message);
        }

        final GovExportService.Result backup =
                GovExportService.writeBackup(data, runDir, now);
        if (!backup.ok()) {
            final String message = "Reset refused: the full backup could not be written and verified.";
            Gov.LOGGER.warn("[gov] season reset refused for {}: {}", actor.name(), backup.message());
            SeasonResetAudit.audit(data, actor, "SEASON_COMMIT", AuditOutcome.REFUSED,
                    "backup=failed; " + SeasonResetService.countsText(pending.plan().counts()), message);
            return SeasonResetService.Outcome.refused(ErrorCode.EXPORT_FAILED, message);
        }
        final GovExportService.Result verified = GovExportService.verifyBackup(
                runDir, backup.relativePath(), backup.sha256(), backup.bytes());
        if (!verified.ok()) {
            final String message = "Reset refused: the backup failed its second verification.";
            Gov.LOGGER.error("[gov] season reset refused for {}: {}", actor.name(), verified.message());
            SeasonResetAudit.audit(data, actor, "SEASON_COMMIT", AuditOutcome.REFUSED,
                    "backup=" + backup.relativePath() + "; verification=failed", message);
            return SeasonResetService.Outcome.refused(ErrorCode.EXPORT_FAILED, message);
        }

        final Map<String, Integer> cleared;
        try {
            cleared = data.clearSeason(pending.clearBusinesses(), now);
        } catch (final RuntimeException e) {
            Gov.LOGGER.error("[gov] season reset failed while clearing in memory", e);
            final String message = "The reset failed while clearing; the backup is " + backup.relativePath();
            SeasonResetAudit.audit(data, actor, "SEASON_COMMIT", AuditOutcome.REFUSED,
                    "backup=" + backup.relativePath() + "; clear=failed", message);
            return SeasonResetService.Outcome.refused(ErrorCode.INVALID_STATE, message);
        }

        final SeasonResetService.Plan actual = new SeasonResetService.Plan(
                pending.clearBusinesses(), cleared, SeasonResetPlanning.planSections(pending.clearBusinesses()));
        Gov.LOGGER.info(
                "[gov] season reset committed by {}: {} (backup {})",
                actor.name(), SeasonResetService.countsText(cleared), backup.relativePath());
        SeasonResetAudit.audit(data, actor, "SEASON_COMMIT", AuditOutcome.ACCEPTED,
                "backup=" + backup.relativePath() + "; sha256=" + backup.sha256()
                        + "; " + SeasonResetService.countsText(cleared),
                "season reset committed");
        return SeasonResetService.Outcome.committed(actual, verified);
    }
}