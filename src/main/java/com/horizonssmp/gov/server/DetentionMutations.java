package com.horizonssmp.gov.server;

import java.util.List;
import java.util.UUID;

import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.GovCapability;
import com.horizonssmp.gov.common.records.DetentionRecord;
import com.horizonssmp.gov.common.records.LocationRecord;

/**
 * Detention mutations: cell placement, release, extension, cuffs.
 */
public final class DetentionMutations {

    private DetentionMutations() {
    }

    public static DetentionService.Outcome putInCell(
            final GovSavedData data,
            final DetentionService.Actor actor,
            final UUID targetUuid,
            final String targetName,
            final int rawCell,
            final JailGateway gateway,
            final DetentionService.Policy policy,
            final long now) {
        if (data == null || actor == null || targetUuid == null || policy == null) {
            return DetentionService.Outcome.refused(ErrorCode.INVALID_STATE, "No detention to update.");
        }
        if (!actor.can(GovCapability.JAIL_COMMIT)) {
            return DetentionService.Outcome.refused(
                    ErrorCode.FORBIDDEN,
                    "You need " + GovCapability.JAIL_COMMIT.id() + " to move a detainee.");
        }
        DetentionTimer.expireDue(data, gateway == null ? JailGateway.NONE : gateway, policy, now);
        final DetentionRecord active = DetentionQueries.activeRecord(data, targetUuid);
        if (active == null) {
            return DetentionService.Outcome.refused(ErrorCode.INVALID_STATE, Sanitizer.name(targetName) + " is not detained.");
        }
        final List<LocationRecord> cells = DetentionQueries.allCells(data);
        if (cells.isEmpty()) {
            return DetentionService.Outcome.refused(ErrorCode.INVALID_STATE, "No jail cell is configured.");
        }
        if (rawCell < 1 || rawCell > cells.size()) {
            return DetentionService.Outcome.refused(ErrorCode.BAD_PAYLOAD,
                    "Cell must be between 1 and " + cells.size() + ".");
        }
        final LocationRecord cell = cells.get(rawCell - 1);
        final DetentionRecord updated = active.withCell(cell);
        data.putDetention(updated);
        DetentionSupport.syncPlayerSummary(data, updated, now);
        final JailGateway facts = gateway == null ? JailGateway.NONE : gateway;
        if (facts.present(targetUuid)) {
            facts.teleport(targetUuid, cell);
            facts.applyConfinement(targetUuid, true);
            if (updated.cuffed() && policy.nativeCuffs()) {
                facts.applyCuffs(targetUuid, true);
            }
        }
        facts.message(targetUuid, "You have been placed in cell " + rawCell + ".");
        return DetentionService.Outcome.ok("placed " + Sanitizer.name(targetName) + " in cell " + rawCell + ".", updated.id());
    }

    public static DetentionService.Outcome release(
            final GovSavedData data,
            final DetentionService.Actor actor,
            final UUID targetUuid,
            final String targetName,
            final String rawReason,
            final JailGateway gateway,
            final DetentionService.Policy policy,
            final long now) {
        if (data == null || actor == null || targetUuid == null || policy == null) {
            return DetentionService.Outcome.refused(ErrorCode.INVALID_STATE, "No detention to release.");
        }
        if (!actor.can(GovCapability.JAIL_RELEASE)) {
            return DetentionService.Outcome.refused(
                    ErrorCode.FORBIDDEN,
                    "You need " + GovCapability.JAIL_RELEASE.id() + " to release a player.");
        }
        final DetentionRecord record = DetentionQueries.activeRecord(data, targetUuid);
        if (record == null) {
            return DetentionService.Outcome.refused(
                    ErrorCode.INVALID_STATE, DetentionQueries.noActiveDetentionMessage(targetName));
        }
        final String reason = Sanitizer.text(rawReason);
        final String text = reason.isBlank()
                ? "released by " + Sanitizer.name(actor.name()) : reason;
        final DetentionRecord released = record.released(
                actor.uuid(), Sanitizer.name(actor.name()), text, now);
        data.putDetention(released);
        DetentionSupport.syncPlayerSummary(data, released, now);
        HistoryService.append(data, targetUuid, com.horizonssmp.gov.common.enums.HistoryKind.DETENTION_RELEASED,
                "Released from detention: " + text,
                actor.uuid(), actor.name(), now, record.id());

        if (DetentionRecord.ADAPTER_NATIVE.equals(record.jailAdapter())
                && record.from().isSet() && gateway != null) {
            gateway.teleport(targetUuid, record.from());
        }
        DetentionSupport.cleanLiveState(gateway, record);
        final JailAdapter adapter = DetentionSupport.adapterForRecord(policy, record);
        try {
            adapter.release(released, gateway == null ? JailGateway.NONE : gateway, now);
        } catch (final RuntimeException e) {
            com.horizonssmp.gov.Gov.LOGGER.warn("[gov] jail adapter '{}' failed to release {}: {}",
                    adapter.id(), targetName, e.toString());
        }
        if (gateway != null) {
            gateway.message(targetUuid,
                    "You have been released from detention. Reason: " + text);
        }
        return DetentionService.Outcome.ok(
                "released " + Sanitizer.name(targetName) + ": " + text, record.id());
    }

    public static DetentionService.Outcome extend(
            final GovSavedData data,
            final DetentionService.Actor actor,
            final UUID targetUuid,
            final String targetName,
            final String rawMinutes,
            final String rawReason,
            final JailGateway gateway,
            final DetentionService.Policy policy,
            final long now) {
        if (data == null || actor == null || targetUuid == null || policy == null) {
            return DetentionService.Outcome.refused(ErrorCode.INVALID_STATE, "No detention to extend.");
        }
        if (!actor.can(GovCapability.JAIL_COMMIT)) {
            return DetentionService.Outcome.refused(
                    ErrorCode.FORBIDDEN,
                    "You need " + GovCapability.JAIL_COMMIT.id() + " to extend a detention.");
        }
        final DetentionRecord record = DetentionQueries.activeRecord(data, targetUuid);
        if (record == null) {
            return DetentionService.Outcome.refused(
                    ErrorCode.INVALID_STATE, DetentionQueries.noActiveDetentionMessage(targetName));
        }
        if (record.expiredAt(now)) {
            return DetentionService.Outcome.refused(
                    ErrorCode.INVALID_STATE,
                    "The detention term of " + Sanitizer.name(targetName) + " has already ended.");
        }
        if (record.legalBasis() != com.horizonssmp.gov.common.enums.LegalBasis.WARRANT || record.warrantRef().isBlank()) {
            return DetentionService.Outcome.refused(
                    ErrorCode.NO_LEGAL_BASIS,
                    "Only a warrant-backed detention can be extended (Article "
                            + DetentionService.ARTICLE_DETENTION + ").");
        }
        final int minutes = DetentionSupport.parseMinutes(rawMinutes);
        if (minutes <= 0) {
            return DetentionService.Outcome.refused(
                    ErrorCode.BAD_PAYLOAD, "The extension must be a positive number of minutes.");
        }
        final long total = (long) record.maxDurationMinutes() + minutes;
        if (total > policy.warrantMinutes()) {
            return DetentionService.Outcome.refused(
                    ErrorCode.BAD_PAYLOAD,
                    "The total warrant term may not exceed " + policy.warrantMinutes()
                            + " minutes on this server.");
        }
        final String reason = Sanitizer.text(rawReason);
        if (reason.isBlank()) {
            return DetentionService.Outcome.refused(ErrorCode.BAD_PAYLOAD, "An extension needs a recorded reason.");
        }
        final DetentionRecord extended = record.extended(
                minutes, actor.uuid(), Sanitizer.name(actor.name()), reason, now);
        data.putDetention(extended);
        DetentionSupport.syncPlayerSummary(data, extended, now);
        final JailAdapter adapter = DetentionSupport.adapterForRecord(policy, record);
        try {
            adapter.extend(extended, gateway == null ? JailGateway.NONE : gateway, now);
        } catch (final RuntimeException e) {
            com.horizonssmp.gov.Gov.LOGGER.warn("[gov] jail adapter '{}' failed to extend {}: {}",
                    adapter.id(), targetName, e.toString());
        }
        if (gateway != null) {
            gateway.message(targetUuid,
                    "Your detention was extended by " + minutes + " minutes: " + reason);
        }
        return DetentionService.Outcome.ok(
                "extended the detention of " + Sanitizer.name(targetName) + " by " + minutes
                        + " minutes: " + reason,
                record.id());
    }

    public static DetentionService.Outcome cuff(
            final GovSavedData data,
            final DetentionService.Actor actor,
            final UUID targetUuid,
            final String targetName,
            final JailGateway gateway,
            final DetentionService.Policy policy,
            final long now) {
        if (data == null || actor == null || targetUuid == null || policy == null) {
            return DetentionService.Outcome.refused(ErrorCode.INVALID_STATE, "No detention to cuff.");
        }
        if (!actor.can(GovCapability.JAIL_COMMIT)) {
            return DetentionService.Outcome.refused(
                    ErrorCode.FORBIDDEN,
                    "You need " + GovCapability.JAIL_COMMIT.id() + " to cuff a player.");
        }
        if (!policy.nativeCuffs()) {
            return DetentionService.Outcome.refused(
                    ErrorCode.INVALID_STATE,
                    "Native cuffs are disabled in the server config; no cuff state was recorded.");
        }
        final DetentionRecord record = DetentionQueries.activeRecord(data, targetUuid);
        if (record == null) {
            return DetentionService.Outcome.refused(
                    ErrorCode.INVALID_STATE,
                    "Cuffing is part of a detention. Detain the player first.");
        }
        if (record.expiredAt(now)) {
            return DetentionService.Outcome.refused(
                    ErrorCode.INVALID_STATE,
                    "The detention term of " + Sanitizer.name(targetName) + " has already ended.");
        }
        if (record.cuffed()) {
            return DetentionService.Outcome.refused(
                    ErrorCode.INVALID_STATE, Sanitizer.name(targetName) + " is already cuffed.");
        }
        final DetentionRecord cuffed = record.withCuffed(true);
        data.putDetention(cuffed);
        DetentionSupport.syncPlayerSummary(data, cuffed, now);
        if (gateway != null) {
            gateway.applyCuffs(targetUuid, true);
            gateway.message(targetUuid,
                    "You are cuffed: no walking, item use, attacks or block breaking until uncuffed.");
        }
        return DetentionService.Outcome.ok("cuffed " + Sanitizer.name(targetName) + ".", record.id());
    }

    public static DetentionService.Outcome uncuff(
            final GovSavedData data,
            final DetentionService.Actor actor,
            final UUID targetUuid,
            final String targetName,
            final JailGateway gateway,
            final DetentionService.Policy policy,
            final long now) {
        if (data == null || actor == null || targetUuid == null || policy == null) {
            return DetentionService.Outcome.refused(ErrorCode.INVALID_STATE, "No detention to uncuff.");
        }
        if (!actor.can(GovCapability.JAIL_RELEASE)) {
            return DetentionService.Outcome.refused(
                    ErrorCode.FORBIDDEN,
                    "You need " + GovCapability.JAIL_RELEASE.id() + " to uncuff a player.");
        }
        final DetentionRecord record = DetentionQueries.activeRecord(data, targetUuid);
        if (record == null) {
            return DetentionService.Outcome.refused(
                    ErrorCode.INVALID_STATE, DetentionQueries.noActiveDetentionMessage(targetName));
        }
        if (!record.cuffed()) {
            return DetentionService.Outcome.refused(
                    ErrorCode.INVALID_STATE, Sanitizer.name(targetName) + " is not cuffed.");
        }
        final DetentionRecord uncuffed = record.withCuffed(false);
        data.putDetention(uncuffed);
        DetentionSupport.syncPlayerSummary(data, uncuffed, now);
        if (gateway != null) {
            gateway.applyCuffs(targetUuid, false);
            gateway.message(targetUuid, "Your cuffs have been removed.");
        }
        return DetentionService.Outcome.ok("uncuffed " + Sanitizer.name(targetName) + ".", record.id());
    }
}
