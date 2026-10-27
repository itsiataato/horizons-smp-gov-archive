package com.horizonssmp.gov.server;

import java.util.UUID;

import com.horizonssmp.gov.Gov;
import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.GovCapability;
import com.horizonssmp.gov.common.JailKeys;
import com.horizonssmp.gov.common.enums.LegalBasis;
import com.horizonssmp.gov.common.records.DetentionRecord;
import com.horizonssmp.gov.common.records.LocationRecord;
import com.horizonssmp.gov.server.GovSavedData;

/**
 * Detention request: detain.
 */
public final class DetentionDetain {

    private DetentionDetain() {
    }

    public static DetentionService.Outcome detain(
            final GovSavedData data,
            final DetentionService.Actor actor,
            final UUID targetUuid,
            final String targetName,
            final String rawBasis,
            final String rawWarrantRef,
            final String rawMinutes,
            final String rawReason,
            final String rawConsent,
            final JailGateway gateway,
            final DetentionService.Policy policy,
            final long now) {
        if (data == null || actor == null || targetUuid == null || policy == null) {
            return DetentionService.Outcome.refused(ErrorCode.INVALID_STATE, "No detention to record.");
        }
        if (!actor.can(GovCapability.JAIL_COMMIT)) {
            return DetentionService.Outcome.refused(
                    ErrorCode.FORBIDDEN,
                    "You need " + GovCapability.JAIL_COMMIT.id() + " to detain a player.");
        }
        final LegalBasis basis = DetentionSupport.parseBasis(rawBasis);
        if (basis == null) {
            return DetentionService.Outcome.refused(
                    ErrorCode.BAD_PAYLOAD,
                    "Choose a legal basis: consent, violent or warrant (Article "
                            + DetentionService.ARTICLE_DETENTION + ").");
        }

        final String reason = Sanitizer.text(rawReason);
        if (reason.isBlank()) {
            return DetentionService.Outcome.refused(ErrorCode.BAD_PAYLOAD, "A detention needs a recorded reason.");
        }

        DetentionTimer.expireDue(data, gateway == null ? JailGateway.NONE : gateway, policy, now);
        final DetentionRecord existing = DetentionQueries.activeRecord(data, targetUuid);
        if (existing != null) {
            return DetentionService.Outcome.refused(
                    ErrorCode.INVALID_STATE,
                    Sanitizer.name(targetName) + " is already detained ("
                            + existing.id() + "). Release that record first.");
        }

        final int requested = DetentionSupport.parseMinutes(rawMinutes);
        if (requested == Integer.MIN_VALUE) {
            return DetentionService.Outcome.refused(
                    ErrorCode.BAD_PAYLOAD, "The length must be a whole number of minutes.");
        }

        final int minutes;
        final String warrantRef;
        switch (basis) {
            case WARRANT -> {
                warrantRef = Sanitizer.singleLine(
                        rawWarrantRef, JailKeys.MAX_WARRANT_REF_CHARS);
                if (warrantRef.isBlank()) {
                    return DetentionService.Outcome.refused(
                            ErrorCode.BAD_PAYLOAD,
                            "A warrant detention needs the warrant reference.");
                }
                if (requested <= 0) {
                    return DetentionService.Outcome.refused(
                            ErrorCode.BAD_PAYLOAD,
                            "A warrant detention needs its stated duration in minutes.");
                }
                if (requested > policy.warrantMinutes()) {
                    return DetentionService.Outcome.refused(
                            ErrorCode.BAD_PAYLOAD,
                            "A warrant detention may not exceed " + policy.warrantMinutes()
                                    + " minutes on this server.");
                }
                minutes = requested;
            }
            case CONSENT -> {
                if (!DetentionSupport.confirmed(rawConsent)) {
                    return DetentionService.Outcome.refused(
                            ErrorCode.NO_LEGAL_BASIS,
                            "Consent must be explicitly confirmed before the detention is recorded.");
                }
                warrantRef = "";
                minutes = DetentionSupport.boundedMinutes(requested, policy);
                if (minutes <= 0) {
                    return DetentionSupport.tooLong(policy);
                }
            }
            case VIOLENT_OFFENSE -> {
                warrantRef = "";
                minutes = DetentionSupport.boundedMinutes(requested, policy);
                if (minutes <= 0) {
                    return DetentionSupport.tooLong(policy);
                }
            }
            default -> {
                return DetentionService.Outcome.refused(ErrorCode.NO_LEGAL_BASIS, "That basis is not legal here.");
            }
        }

        final JailGateway effectiveGateway = gateway == null ? JailGateway.NONE : gateway;
        final JailGateway.Position officerPosition = effectiveGateway.position(actor.uuid());
        final JailGateway.Position detaineePosition = effectiveGateway.position(targetUuid);
        if (!officerPosition.set() || !detaineePosition.set()
                || !officerPosition.dimension().equals(detaineePosition.dimension())) {
            return DetentionService.Outcome.refused(
                    ErrorCode.INVALID_STATE,
                    "The officer must be within 8 blocks of the person to detain them.");
        }
        final double dx = officerPosition.x() - detaineePosition.x();
        final double dy = officerPosition.y() - detaineePosition.y();
        final double dz = officerPosition.z() - detaineePosition.z();
        if (dx * dx + dy * dy + dz * dz > 64.0d) {
            return DetentionService.Outcome.refused(
                    ErrorCode.INVALID_STATE,
                    "The officer must be within 8 blocks of the person to detain them.");
        }

        final java.util.List<LocationRecord> availableCells = DetentionQueries.allCells(data);
        final JailAdapter adapter = policy.pluginCommand().isBlank()
                ? JailAdapter.create(policy.pluginCommand(), policy.nativeFallback(),
                        !availableCells.isEmpty(), policy.leashRadius())
                : policy.adapter();
        final boolean nativeCell = adapter.kind() == JailAdapter.Kind.NATIVE
                && !availableCells.isEmpty();
        final LocationRecord cell = nativeCell
                ? chooseCell(availableCells, data, officerPosition, now)
                : LocationRecord.NONE;
        final LocationRecord from = nativeCell ? DetentionSupport.locationOf(gateway, targetUuid) : LocationRecord.NONE;
        final String id = DetentionSupport.newDetentionId(data);
        final DetentionRecord record = new DetentionRecord(
                id,
                targetUuid,
                Sanitizer.name(targetName),
                actor.uuid(),
                Sanitizer.name(actor.name()),
                basis,
                reason,
                warrantRef,
                now,
                minutes,
                0L,
                null,
                "",
                "",
                com.horizonssmp.gov.common.enums.DetentionStatus.ACTIVE,
                cell,
                from,
                false,
                adapter.id(),
                0,
                0L,
                null,
                "",
                "");
        data.putDetention(record);
        DetentionSupport.syncPlayerSummary(data, record, now);
        HistoryService.append(data, targetUuid, com.horizonssmp.gov.common.enums.HistoryKind.DETENTION_STARTED,
                "Detained for " + minutes + " minutes under " + DetentionSupport.basisLabel(basis)
                        + ": " + reason,
                actor.uuid(), actor.name(), now, id);

        try {
            adapter.confine(record, gateway == null ? JailGateway.NONE : gateway, now);
        } catch (final RuntimeException e) {
            Gov.LOGGER.warn("[gov] jail adapter '{}' failed to confine {}: {}",
                    adapter.id(), targetName, e.toString());
        }
        if (gateway != null) {
            gateway.message(targetUuid,
                    "Detained for " + minutes + " minutes under " + DetentionSupport.basisLabel(basis)
                            + ". Reason: " + reason);
        }
        return DetentionService.Outcome.ok(
                "detained " + Sanitizer.name(targetName) + " for " + minutes + " minutes under "
                        + DetentionSupport.basisLabel(basis) + (warrantRef.isBlank() ? "" : " (" + warrantRef + ")")
                        + ": " + reason,
                id);
    }

    /** Picks the closest unoccupied cell to the officer, preferring the same dimension. */
    private static LocationRecord chooseCell(
            final java.util.List<LocationRecord> cells,
            final GovSavedData data,
            final JailGateway.Position officerPosition,
            final long now) {
        final java.util.Set<LocationRecord> occupied = new java.util.HashSet<>();
        if (data != null) {
            for (final DetentionRecord record : data.detentions().values()) {
                if (record != null && record.active() && !record.expiredAt(now)
                        && record.cell().isSet()) {
                    occupied.add(record.cell());
                }
            }
        }
        LocationRecord best = null;
        double bestDistance = Double.MAX_VALUE;
        // Prefer cells in the officer's dimension.
        for (final LocationRecord cell : cells) {
            if (cell == null || !cell.isSet() || occupied.contains(cell)
                    || !cell.dimension().equals(officerPosition.dimension())) {
                continue;
            }
            final double dx = officerPosition.x() - cell.x();
            final double dy = officerPosition.y() - cell.y();
            final double dz = officerPosition.z() - cell.z();
            final double distance = dx * dx + dy * dy + dz * dz;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = cell;
            }
        }
        if (best != null) {
            return best;
        }
        // Fall back to any free cell if none share the officer's dimension.
        for (final LocationRecord cell : cells) {
            if (cell == null || !cell.isSet() || occupied.contains(cell)) {
                continue;
            }
            final double dx = officerPosition.x() - cell.x();
            final double dy = officerPosition.y() - cell.y();
            final double dz = officerPosition.z() - cell.z();
            final double distance = dx * dx + dy * dy + dz * dz;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = cell;
            }
        }
        return best == null ? LocationRecord.NONE : best;
    }
}
