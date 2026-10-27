package com.horizonssmp.gov.server;

import java.util.Map;
import java.util.UUID;

import com.horizonssmp.gov.common.enums.AuditOutcome;
import com.horizonssmp.gov.common.records.AuditEntry;
import com.horizonssmp.gov.common.records.PlayerRecord;

import net.minecraft.server.level.ServerPlayer;

/**
 * Append-side implementation for {@link AuditService}. Holds every overload that
 * writes an audit line so {@code AuditService} stays a thin public facade.
 * Behavior is identical to the previous in-class implementation.
 */
public final class AuditAppender {

    private AuditAppender() {
    }

    public static AuditEntry append(
            final GovSavedData data,
            final ServerPlayer actor,
            final String action,
            final GovTargets.Resolved target,
            final Map<String, String> payload,
            final String reason) {
        final UUID actorUuid = actor == null ? null : actor.getUUID();
        final String actorName = actor == null ? "" : Sanitizer.name(actor.getName().getString());
        final PlayerRecord actorRecord = actorUuid == null ? null : data.player(actorUuid);

        return append(
                data,
                actorUuid,
                actorName,
                actorRecord == null ? "" : Sanitizer.id(actorRecord.primaryJobId()),
                action,
                AuditOutcome.ACCEPTED,
                target == null ? null : target.uuid(),
                target == null ? "" : target.name(),
                Sanitizer.payloadSummary(payload),
                reason);
    }

    public static AuditEntry appendSystem(
            final GovSavedData data, final String action, final String reason) {
        return append(data, null, "", "", action, AuditOutcome.ACCEPTED,
                null, "", "", reason);
    }

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
        return append(data, actorUuid, actorName, actorJobId, action, outcome,
                targetUuid, targetName, payloadSummary, reason);
    }

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
        return append(data, actorUuid, actorName, actorJobId, action, AuditOutcome.ACCEPTED,
                targetUuid, targetName, payloadSummary, reason);
    }

    public static AuditEntry append(
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
        final AuditEntry entry = new AuditEntry(
                data.nextAuditSeq(),
                System.currentTimeMillis(),
                actorUuid,
                Sanitizer.name(actorName),
                Sanitizer.id(actorJobId),
                // The action verb keeps its case. It is the OpCode name verbatim, so a later reader
                // can map an audit line back with OpCode.valueOf, and a service verb such as
                // SEED_JOBS stays recognisable. Only the job id is normalized to lower case, which
                // is the form Sanitizer.id produced when the job was created.
                Sanitizer.singleLine(action, Sanitizer.MAX_NAME),
                outcome == null ? AuditOutcome.ACCEPTED : outcome,
                targetUuid,
                Sanitizer.name(targetName),
                Sanitizer.singleLine(payloadSummary, Sanitizer.MAX_PAYLOAD_SUMMARY),
                Sanitizer.text(reason));
        data.addAudit(entry);
        return entry;
    }
}