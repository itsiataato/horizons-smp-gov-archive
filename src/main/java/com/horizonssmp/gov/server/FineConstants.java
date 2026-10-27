package com.horizonssmp.gov.server;

/**
 * Constants for the fine rules. Kept here so {@link FineService} can stay a thin facade.
 */
public final class FineConstants {

    public static final long PETTY_GOLD = 8L;
    public static final long MISDEMEANOR_GOLD = 16L;
    public static final long FELONY_GOLD = 32L;
    public static final long AGGRAVATED_GOLD = 64L;
    public static final long MAX_RESTITUTION_GOLD = 64L;
    public static final long MAX_COMPLAINT_RESTITUTION_GOLD = 1_000_000L;
    public static final int MIN_EXILE_DAYS = 7;
    public static final int MAX_EXILE_DAYS = 30;
    public static final String DEFAULT_ARTICLE = "9.11";
    public static final String FINE_ID_PREFIX = "f-";
    public static final String RECEIPT_PREFIX = "R-";
    public static final long COMPLAINT_FINE_GOLD = AGGRAVATED_GOLD;

    private FineConstants() {
    }
}
