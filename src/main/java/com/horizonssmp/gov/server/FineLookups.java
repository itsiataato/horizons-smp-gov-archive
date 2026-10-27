package com.horizonssmp.gov.server;

import java.util.Set;
import java.util.UUID;

import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.GovCapability;
import com.horizonssmp.gov.common.enums.FineStatus;
import com.horizonssmp.gov.common.records.FineRecord;
import com.horizonssmp.gov.common.records.PlayerRecord;

public final class FineLookups {

    private FineLookups() {
    }

    public static FineRecord find(final GovSavedData data, final String rawFineId) {
        final String id = Sanitizer.id(rawFineId);
        return data == null || id.isEmpty() ? null : data.fine(id);
    }

    public static String targetName(final GovSavedData data, final UUID uuid) {
        if (uuid == null) {
            return "";
        }
        final PlayerRecord record = data == null ? null : data.player(uuid);
        if (record != null && !record.lastName().isBlank()) {
            return record.lastName();
        }
        return uuid.toString();
    }

    public static String waivedByName(final GovSavedData data, final UUID uuid) {
        if (uuid == null) {
            return "";
        }
        final PlayerRecord record = data == null ? null : data.player(uuid);
        return record != null && !record.lastName().isBlank()
                ? record.lastName() : uuid.toString();
    }

    public static String noSuchFineMessage(final String rawFineId) {
        return "There is no fine '" + Sanitizer.singleLine(rawFineId, Sanitizer.MAX_ID) + "'.";
    }

    public static boolean canViewAll(final Set<GovCapability> capabilities) {
        return capabilities != null
                && (capabilities.contains(GovCapability.FINE_VIEW_ALL)
                        || capabilities.contains(GovCapability.FINE_WAIVE));
    }

    public static FineRecord visibleFine(
            final GovSavedData data, final UUID viewerUuid,
            final Set<GovCapability> capabilities, final String rawFineId) {
        final FineRecord fine = find(data, rawFineId);
        if (fine == null) {
            return null;
        }
        if (!canViewAll(capabilities) && (viewerUuid == null || !viewerUuid.equals(fine.targetUuid()))) {
            return null;
        }
        return fine;
    }

    public static FineService.Outcome finePrecheck(final GovSavedData data, final String rawFineId) {
        return find(data, rawFineId) == null
                ? FineService.Outcome.refused(ErrorCode.BAD_FINE, noSuchFineMessage(rawFineId))
                : null;
    }

    public static FineService.Outcome payPrecheck(
            final GovSavedData data, final UUID payerUuid, final String rawFineId) {
        final FineRecord fine = find(data, rawFineId);
        if (fine == null) {
            return FineService.Outcome.refused(ErrorCode.BAD_FINE, noSuchFineMessage(rawFineId));
        }
        if (payerUuid != null && !payerUuid.equals(fine.targetUuid())) {
            return FineService.Outcome.refused(ErrorCode.FORBIDDEN, "You can only pay your own fines.");
        }
        if (fine.status() == FineStatus.PAID) {
            return FineService.Outcome.refused(
                    ErrorCode.INVALID_STATE, "Fine " + fine.id() + " is already paid.");
        }
        if (fine.status() != FineStatus.UNPAID) {
            return FineService.Outcome.refused(
                    ErrorCode.INVALID_STATE, "Fine " + fine.id() + " is not payable.");
        }
        return null;
    }

    public static FineService.Outcome waivePrecheck(
            final GovSavedData data, final String rawFineId, final String rawReason) {
        final FineRecord fine = find(data, rawFineId);
        if (fine == null) {
            return FineService.Outcome.refused(ErrorCode.BAD_FINE, noSuchFineMessage(rawFineId));
        }
        if (fine.status() == FineStatus.PAID) {
            return FineService.Outcome.refused(
                    ErrorCode.INVALID_STATE, "Fine " + fine.id() + " is already paid.");
        }
        if (fine.status() != FineStatus.UNPAID) {
            return FineService.Outcome.refused(
                    ErrorCode.INVALID_STATE, "Fine " + fine.id() + " is not waivable.");
        }
        if (Sanitizer.text(rawReason).isBlank()) {
            return FineService.Outcome.refused(
                    ErrorCode.BAD_PAYLOAD, "A waiver needs a recorded reason.");
        }
        return null;
    }
}
