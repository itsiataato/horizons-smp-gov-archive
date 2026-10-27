package com.horizonssmp.gov.server;

import com.horizonssmp.gov.common.AuditKeys;
import com.horizonssmp.gov.common.enums.AuditOutcome;
import com.horizonssmp.gov.common.records.PlayerRecord;

/**
 * Audit helper for {@link SeasonResetService}.
 */
public final class SeasonResetAudit {

    private SeasonResetAudit() {
    }

    public static void audit(
            final GovSavedData data, final SeasonResetService.Actor actor, final String action,
            final AuditOutcome outcome, final String payload, final String reason) {
        final PlayerRecord record = data.player(actor.uuid());
        AuditService.appendOutcome(
                data,
                actor.uuid().equals(SeasonResetService.CONSOLE_ACTOR) ? null : actor.uuid(),
                actor.name(),
                record == null ? "" : record.primaryJobId(),
                action,
                outcome,
                null,
                "",
                payload,
                reason);
    }
}