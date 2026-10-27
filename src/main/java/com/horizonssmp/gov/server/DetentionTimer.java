package com.horizonssmp.gov.server;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.horizonssmp.gov.Gov;
import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.enums.HistoryKind;
import com.horizonssmp.gov.common.records.DetentionRecord;

import net.minecraft.server.level.ServerPlayer;

/**
 * Timer, expiry and join reapplication for detentions.
 */
public final class DetentionTimer {

    private DetentionTimer() {
    }

    public static List<DetentionRecord> tick(
            final GovSavedData data, final JailGateway gateway, final DetentionService.Policy policy, final long now) {
        final List<DetentionRecord> expired = expireDue(data, gateway, policy, now);
        if (data == null || policy == null) {
            return expired;
        }
        final JailGateway facts = gateway == null ? JailGateway.NONE : gateway;
        for (final DetentionRecord record : List.copyOf(data.detentions().values())) {
            if (!record.active()) {
                continue;
            }
            try {
                if (record.cuffed() && policy.nativeCuffs()) {
                    facts.applyCuffs(record.targetUuid(), true);
                } else if (!policy.nativeCuffs()) {
                    facts.applyCuffs(record.targetUuid(), false);
                }
                DetentionSupport.adapterForRecord(policy, record).leash(record, facts);
            } catch (final RuntimeException e) {
                Gov.LOGGER.warn("[gov] detention tick failed for {}: {}",
                        record.id(), e.toString());
            }
        }
        return expired;
    }

    public static List<DetentionRecord> expireDue(
            final GovSavedData data, final JailGateway gateway, final DetentionService.Policy policy, final long now) {
        final List<DetentionRecord> expired = new ArrayList<>();
        if (data == null) {
            return expired;
        }
        final JailGateway facts = gateway == null ? JailGateway.NONE : gateway;
        for (final DetentionRecord record : List.copyOf(data.detentions().values())) {
            if (!record.active() || now < record.expiresAt()) {
                continue;
            }
            try {
                final DetentionRecord ended = record.expired(
                        "term ended after " + record.maxDurationMinutes() + " minutes", now);
                data.putDetention(ended);
                DetentionSupport.syncPlayerSummary(data, ended, now);
                HistoryService.append(data, record.targetUuid(),
                        HistoryKind.DETENTION_RELEASED,
                        "Detention term ended after " + record.maxDurationMinutes() + " minutes.",
                        null, "", now, record.id());
                if (DetentionRecord.ADAPTER_NATIVE.equals(record.jailAdapter())
                        && record.from().isSet()) {
                    facts.teleport(record.targetUuid(), record.from());
                }
                DetentionSupport.cleanLiveState(facts, record);
                DetentionSupport.adapterForRecord(policy, record).release(ended, facts, now);
                facts.message(record.targetUuid(),
                        "Your detention term has ended after " + record.maxDurationMinutes()
                                + " minutes. You are released.");
                DetentionSupport.notifyOfficer(facts, record,
                        "The detention of " + record.targetName() + " expired after "
                                + record.maxDurationMinutes() + " minutes.");
                com.horizonssmp.gov.server.AuditService.appendSystem(
                        data,
                        "JAIL_EXPIRED",
                        "detention " + record.id() + " of " + record.targetName()
                                + " expired after " + record.maxDurationMinutes() + " minutes");
                expired.add(ended);
            } catch (final RuntimeException e) {
                Gov.LOGGER.warn("[gov] could not expire detention {}: {}",
                        record.id(), e.toString());
            }
        }
        return expired;
    }

    public static void reapplyOnJoin(
            final ServerPlayer player, final GovSavedData data, final JailGateway gateway,
            final DetentionService.Policy policy, final long now) {
        if (player == null || data == null || policy == null) {
            return;
        }
        final JailGateway facts = gateway == null ? JailGateway.NONE : gateway;
        expireDue(data, facts, policy, now);
        final DetentionRecord record = DetentionQueries.activeRecord(data, player.getUUID());
        if (record == null) {
            facts.applyCuffs(player.getUUID(), false);
            facts.applyConfinement(player.getUUID(), false);
            final com.horizonssmp.gov.common.records.PlayerRecord stored = data.player(player.getUUID());
            if (stored != null && stored.jail().active()) {
                data.putPlayer(stored.withJail(com.horizonssmp.gov.common.records.JailRecord.NONE));
            }
            return;
        }
        DetentionSupport.syncPlayerSummary(data, record, now);
        if (record.cuffed() && policy.nativeCuffs()) {
            facts.applyCuffs(player.getUUID(), true);
        } else {
            facts.applyCuffs(player.getUUID(), false);
        }
        if (record.cell().isSet() && DetentionRecord.ADAPTER_NATIVE.equals(record.jailAdapter())
                && policy.nativeFallback()) {
            facts.applyConfinement(player.getUUID(), true);
            DetentionSupport.adapterForRecord(policy, record).leash(record, facts);
        } else {
            facts.applyConfinement(player.getUUID(), false);
        }
        facts.message(player.getUUID(),
                "You are still detained: " + record.reason()
                        + " (" + DetentionSupport.remainingText(record.remainingMs(now)) + ").");
    }

    public static DetentionService.Outcome releasePrecheck(
            final GovSavedData data, final UUID targetUuid, final String targetName) {
        if (data == null || targetUuid == null) {
            return DetentionService.Outcome.refused(
                    ErrorCode.INVALID_STATE,
                    "There is no active detention for "
                            + (targetName == null || targetName.isBlank()
                                    ? "that person" : Sanitizer.name(targetName)) + ".");
        }
        final DetentionRecord record = DetentionQueries.activeRecord(data, targetUuid);
        if (record == null) {
            return DetentionService.Outcome.refused(
                    ErrorCode.INVALID_STATE, DetentionQueries.noActiveDetentionMessage(targetName));
        }
        return new DetentionService.Outcome(true, null, "", record.id());
    }
}
