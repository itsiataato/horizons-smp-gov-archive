package com.horizonssmp.gov.server;

import java.util.Optional;

import com.horizonssmp.gov.config.GovConfig;

public final class FineReception {

    private FineReception() {
    }

    public static Optional<FineService.ReceptionPoint> configuredReception() {
        if (!GovConfig.receptionConfigured()) {
            return Optional.empty();
        }
        final String dimension = GovConfig.receptionDimension();
        if (dimension == null || dimension.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new FineService.ReceptionPoint(
                dimension,
                GovConfig.receptionX(),
                GovConfig.receptionY(),
                GovConfig.receptionZ(),
                GovConfig.receptionRadius()));
    }

    public static String receptionMissingMessage() {
        return "No Government Building reception is configured, so a fine cannot be paid in person."
                + " An administrator must set gov.reception.configured=true and its coordinates.";
    }

    public static String notAtReceptionMessage() {
        return "A fine is paid only at the " + GovConfig.paymentPoint()
                + ". Go there with the gold ingots and try again.";
    }
}
