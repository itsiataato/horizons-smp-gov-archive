package com.horizonssmp.gov.common;

/**
 * The stable field keys of the licence actions and the licence part of the person card.
 *
 * <p>A licence is always addressed by holder plus class, never by a free-floating licence id: the
 * data file has exactly one record per {@code (uuid, class)} pair, so that pair is the natural key
 * a command and a screen can both know. The id exists on the record for audit readability, not as
 * a lookup argument.
 *
 * <p>Authority for every licence mutation is {@code vehicle.register}, the registrar authority in
 * Article 8.12. No separate {@code license.manage} id was added: a new capability id would not
 * exist in job sets seeded by an earlier build, which would make the registrar job silently lose
 * the power until a migration (see the write-once job seeding rule).
 */
public final class LicenseKeys {

    /** Payload: the licence class id. */
    public static final String KEY_CLASS = "licenseClass";
    /** Payload: the points to assess: 2, 4 or 6 under Article 6.09. */
    public static final String KEY_POINTS = "points";
    /** Payload: the recorded reason. Required for points and revocation. */
    public static final String KEY_REASON = "reason";

    /** Viewer flag: the actor may issue, revoke and assess points. */
    public static final String CAN_MANAGE = "canManageLicenses";

    /** Person card / detail: the effective state name, {@code NONE} when no licence is held. */
    public static final String STATE = "licenseState";
    /** Person card / detail: the active point total inside the 30-day window. */
    public static final String POINTS = "licensePoints";
    /** Person card / detail: epoch milliseconds the suspension ends, {@code 0} when none. */
    public static final String SUSPENDED_UNTIL = "licenseSuspendedUntil";
    /** Person card / detail: the recorded revocation reason, or empty. */
    public static final String REVOKE_REASON = "licenseRevokeReason";
    /** Person card / detail: the recorded suspension reason, or empty. */
    public static final String SUSPEND_REASON = "licenseSuspendReason";

    /** The effective-state value when the person holds no licence for the class. */
    public static final String STATE_NONE = "NONE";

    /** The points the law allows per assessment: minor, reckless, impaired. */
    public static final int[] ALLOWED_POINTS = {2, 4, 6};
    /** Article 6.09: twelve points in 30 in-game days suspends the licence. */
    public static final int SUSPENSION_THRESHOLD = 12;

    private LicenseKeys() {
    }

    /** True when the value is one of the three statutory point assessments. */
    public static boolean allowedPoints(final int points) {
        for (final int allowed : ALLOWED_POINTS) {
            if (allowed == points) {
                return true;
            }
        }
        return false;
    }
}
