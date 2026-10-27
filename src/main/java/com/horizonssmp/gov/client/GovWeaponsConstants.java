package com.horizonssmp.gov.client;

import com.horizonssmp.gov.client.GovRect;

final class GovWeaponsConstants {

    private GovWeaponsConstants() {
    }

    static final GovRect EMPTY = new GovRect(0, 0, 0, 0);
    static final int SUBTABS = 4;
    static final int ROW_PITCH = 20;
    static final int ROW_PITCH_COMPACT = 14;
    static final int DETAIL_PITCH = 11;
    static final int DETAIL_PITCH_COMPACT = 10;
    static final int FORM_ROW = 16;
    static final int FORM_GAP = 2;
    static final int CLOSE_W = 11;
    static final int CLOSE_H = 9;
    static final int RETRY_W = 52;
    static final int RETRY_H = 14;
    static final long ARM_MS = 5_000L;
    static final long ARM_FLOOR_MS = 600L;
}
