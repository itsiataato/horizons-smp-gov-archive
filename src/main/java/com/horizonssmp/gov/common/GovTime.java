package com.horizonssmp.gov.common;

/**
 * The one time conversion the government rules share.
 *
 * <p>A Minecraft in-game day is 20 real minutes at the default 24000-tick cycle and 20 ticks per
 * second: 24,000 / 20 = 1,200 seconds = 1,200,000 milliseconds. The law states its windows in
 * in-game days (30 days for points, 30 days for an unclaimed impound), so every deadline is
 * computed from this constant rather than from a wall-clock day.
 *
 * <p>If a server changes its daylight cycle the law's in-game day is still the nominal 20-minute
 * day used by the record; a deadline is a legal term, not a rendering detail.
 */
public final class GovTime {

    /** Milliseconds in one nominal in-game day: 20 minutes. */
    public static final long IN_GAME_DAY_MS = 1_200_000L;

    private GovTime() {
    }

    /** Milliseconds in {@code days} in-game days, refusing a negative count. */
    public static long days(final int days) {
        return Math.max(0, days) * IN_GAME_DAY_MS;
    }
}
