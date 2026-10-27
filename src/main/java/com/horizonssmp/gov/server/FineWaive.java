package com.horizonssmp.gov.server;

import java.util.Locale;
import java.util.UUID;

import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.enums.FineStatus;
import com.horizonssmp.gov.common.records.FineRecord;

public final class FineWaive {

    private FineWaive() {
    }

    public static FineService.Outcome waive(
            final GovSavedData data, final FineService.Actor actor, final String rawFineId,
            final String rawReason, final long now) {
        if (data == null || actor == null || actor.uuid() == null) {
            return FineService.Outcome.refused(ErrorCode.INVALID_STATE, "No fine to waive.");
        }
        if (!actor.capabilities().contains(com.horizonssmp.gov.common.GovCapability.FINE_WAIVE)) {
            return FineService.Outcome.refused(
                    ErrorCode.FORBIDDEN,
                    "You need " + com.horizonssmp.gov.common.GovCapability.FINE_WAIVE.id() + " to waive a fine.");
        }
        final FineRecord fine = FineLookups.find(data, rawFineId);
        if (fine == null) {
            return FineService.Outcome.refused(ErrorCode.BAD_FINE, FineLookups.noSuchFineMessage(rawFineId));
        }
        if (fine.status() == FineStatus.PAID) {
            return FineService.Outcome.refused(
                    ErrorCode.INVALID_STATE,
                    "Fine " + fine.id() + " is already paid; a paid fine cannot be waived.");
        }
        if (fine.status() != FineStatus.UNPAID) {
            return FineService.Outcome.refused(
                    ErrorCode.INVALID_STATE, "Fine " + fine.id() + " is " + fine.status().name().toLowerCase(Locale.ROOT)
                            + " and cannot be waived.");
        }
        final String reason = Sanitizer.text(rawReason);
        if (reason.isBlank()) {
            return FineService.Outcome.refused(
                    ErrorCode.BAD_PAYLOAD,
                    "A waiver needs a recorded reason of at most " + Sanitizer.MAX_TEXT
                            + " characters.");
        }
        data.putFine(fine.waived(actor.uuid(), reason));
        return FineService.Outcome.ok(
                "waived fine " + fine.id() + " for " + FineLookups.targetName(data, fine.targetUuid())
                        + ": " + reason,
                fine.id());
    }
}
