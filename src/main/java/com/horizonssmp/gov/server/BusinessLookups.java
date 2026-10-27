package com.horizonssmp.gov.server;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import com.horizonssmp.gov.common.GovCapability;
import com.horizonssmp.gov.common.records.BusinessRecord;
import com.horizonssmp.gov.common.records.JobRecord;
import com.horizonssmp.gov.common.records.PlayerRecord;
import com.horizonssmp.gov.server.GovSavedData;

/**
 * Helper for {@link BusinessService}: lookups and authority/visibility checks.
 */
public final class BusinessLookups {

    private BusinessLookups() {
    }

    public static BusinessRecord find(final GovSavedData data, final String raw) {
        if (data == null || raw == null || raw.isBlank()) {
            return null;
        }
        final String trimmed = raw.trim();
        final String asId = trimmed.toLowerCase(Locale.ROOT);
        final BusinessRecord byId = data.business(asId);
        if (byId != null) {
            return byId;
        }
        return byName(data, trimmed);
    }

    public static BusinessRecord byName(final GovSavedData data, final String name) {
        if (data == null || name == null || name.isBlank()) {
            return null;
        }
        final String needle = name.trim();
        for (final BusinessRecord business : data.businesses().values()) {
            if (business.name().equalsIgnoreCase(needle)) {
                return business;
            }
        }
        return null;
    }

    public static String noSuchBusinessMessage(final String raw) {
        return "There is no business called '"
                + Sanitizer.singleLine(raw == null ? "" : raw, Sanitizer.MAX_NAME) + "'.";
    }

    public static String ownerName(final GovSavedData data, final UUID uuid) {
        if (uuid == null) {
            return "";
        }
        final PlayerRecord player = data == null ? null : data.player(uuid);
        if (player != null && !player.lastName().isBlank()) {
            return player.lastName();
        }
        return uuid.toString();
    }

    public static String jobName(final GovSavedData data, final String jobId) {
        if (jobId == null || jobId.isBlank()) {
            return "";
        }
        final JobRecord job = data == null ? null : data.job(jobId);
        return job == null ? jobId : job.displayName();
    }

    public static boolean canViewAll(final Set<GovCapability> capabilities) {
        if (capabilities == null) {
            return false;
        }
        return capabilities.contains(GovCapability.BUSINESS_MANAGE)
                || capabilities.contains(GovCapability.BUSINESS_REGISTER)
                || capabilities.contains(GovCapability.PAYROLL_RUN);
    }

    public static boolean canManage(final BusinessService.Actor actor) {
        return actor != null && actor.capabilities().contains(GovCapability.BUSINESS_MANAGE);
    }

    // Note: canManage(Actor) above; keep an overload taking capabilities? Original canManage(Actor). 
    public static boolean isOwnerOrManager(final BusinessRecord business, final BusinessService.Actor actor) {
        if (business == null || actor == null) {
            return false;
        }
        return (actor.uuid() != null && actor.uuid().equals(business.ownerUuid()))
                || canManage(actor);
    }

    public static boolean isOwner(final BusinessRecord business, final BusinessService.Actor actor) {
        if (business == null || actor == null || actor.uuid() == null) {
            return false;
        }
        return actor.uuid().equals(business.ownerUuid());
    }

    public static boolean visibleTo(
            final GovSavedData data, final UUID viewerUuid, final String businessId) {
        final BusinessRecord business = data == null ? null : data.business(businessId);
        if (business == null || viewerUuid == null) {
            return false;
        }
        return viewerUuid.equals(business.ownerUuid())
                || business.employs(viewerUuid)
                || business.activeContractFor(viewerUuid) != null;
    }
}