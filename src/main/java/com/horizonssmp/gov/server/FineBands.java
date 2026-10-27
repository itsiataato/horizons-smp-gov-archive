package com.horizonssmp.gov.server;

import java.util.Locale;

import com.horizonssmp.gov.common.enums.FineBand;

public final class FineBands {

    private FineBands() {
    }

    public static long bandGold(final FineBand band) {
        if (band == null) {
            return FineConstants.PETTY_GOLD;
        }
        return switch (band) {
            case PETTY -> FineConstants.PETTY_GOLD;
            case MISDEMEANOR -> FineConstants.MISDEMEANOR_GOLD;
            case FELONY -> FineConstants.FELONY_GOLD;
            case AGGRAVATED -> FineConstants.AGGRAVATED_GOLD;
        };
    }

    public static FineBand parseBand(final String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return FineBand.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException e) {
            return null;
        }
    }
}
