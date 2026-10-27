package com.horizonssmp.gov.server;

import java.util.Set;
import java.util.UUID;

import com.horizonssmp.gov.common.GovCapability;
import com.horizonssmp.gov.common.records.DetentionRecord;
import com.horizonssmp.gov.common.records.LocationRecord;
import com.horizonssmp.gov.common.records.PlayerRecord;
import com.horizonssmp.gov.config.GovConfig;

/**
 * Query helpers for detentions: lookups, visibility and cuff/confined state.
 */
public final class DetentionQueries {

    private DetentionQueries() {
    }

    public static java.util.List<LocationRecord> allCells(final GovSavedData data) {
        final java.util.List<LocationRecord> result = new java.util.ArrayList<>(GovConfig.jailCells());
        if (data != null) {
            result.addAll(data.runtimeCells().values());
        }
        return java.util.List.copyOf(result);
    }

    public static boolean canViewAll(final Set<GovCapability> capabilities) {
        return capabilities != null && (capabilities.contains(GovCapability.JAIL_COMMIT)
                || capabilities.contains(GovCapability.JAIL_RELEASE)
                || capabilities.contains(GovCapability.AUDIT_VIEW));
    }

    public static String noActiveDetentionMessage(final String name) {
        final String who = Sanitizer.name(name);
        return "There is no active detention for " + (who.isBlank() ? "that person" : who) + ".";
    }

    public static DetentionRecord find(final GovSavedData data, final String rawId) {
        if (data == null) {
            return null;
        }
        final String id = Sanitizer.id(rawId);
        return id.isEmpty() ? null : data.detention(id);
    }

    public static DetentionRecord activeRecord(final GovSavedData data, final UUID targetUuid) {
        if (data == null || targetUuid == null) {
            return null;
        }
        DetentionRecord newest = null;
        for (final DetentionRecord record : data.detentionsForTarget(targetUuid)) {
            if (!record.active()) {
                continue;
            }
            if (newest == null || record.startedAt() > newest.startedAt()) {
                newest = record;
            }
        }
        return newest;
    }

    public static DetentionRecord latestRecord(final GovSavedData data, final UUID targetUuid) {
        if (data == null || targetUuid == null) {
            return null;
        }
        DetentionRecord newest = null;
        for (final DetentionRecord record : data.detentionsForTarget(targetUuid)) {
            if (newest == null || record.startedAt() > newest.startedAt()) {
                newest = record;
            }
        }
        return newest;
    }

    public static DetentionRecord visibleRecord(
            final GovSavedData data, final UUID viewerUuid, final Set<GovCapability> capabilities,
            final UUID targetUuid) {
        if (data == null || targetUuid == null) {
            return null;
        }
        if (!canViewAll(capabilities) && !targetUuid.equals(viewerUuid)) {
            return null;
        }
        final DetentionRecord active = activeRecord(data, targetUuid);
        return active != null ? active : latestRecord(data, targetUuid);
    }

    public static DetentionRecord visibleRecord(
            final GovSavedData data, final UUID viewerUuid, final Set<GovCapability> capabilities,
            final String rawId) {
        final DetentionRecord record = find(data, rawId);
        if (record == null) {
            return null;
        }
        if (!canViewAll(capabilities) && !record.targetUuid().equals(viewerUuid)) {
            return null;
        }
        return record;
    }

    public static boolean isCuffed(
            final GovSavedData data, final UUID player, final long now, final boolean nativeCuffs) {
        if (!nativeCuffs) {
            return false;
        }
        final DetentionRecord record = activeRecord(data, player);
        return record != null && record.cuffed() && !record.expiredAt(now);
    }

    public static boolean isNativeConfined(
            final GovSavedData data, final UUID player, final long now, final DetentionService.Policy policy) {
        if (data == null || player == null || policy == null || !policy.nativeFallback()) {
            return false;
        }
        final DetentionRecord record = activeRecord(data, player);
        return record != null
                && DetentionRecord.ADAPTER_NATIVE.equals(record.jailAdapter())
                && record.cell().isSet()
                && !record.expiredAt(now);
    }
}
