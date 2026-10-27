package com.horizonssmp.gov.common;

/**
 * The stable field keys of the Vehicles tab, the vehicle actions and the vehicle chat mirrors.
 *
 * <p>The keys are the wire contract: the server writes them into a {@link GovFields} block and the
 * client reads them. They live in the common package so the client never imports a server class to
 * learn a key.
 *
 * <p>List rows are indexed: {@code vehicle.<n>.plate}, {@code vehicle.<n>.class},
 * {@code vehicle.<n>.owner}, {@code vehicle.<n>.status}, and so on. The index is a position, not an
 * identity; {@code vehicle.<n>.id} is the identity. {@code vehicles} always carries the true total
 * for the filter and scope, and {@code page}/{@code pages} describe the page bounds, so a short
 * answer reads as a page rather than a complete registry.
 *
 * <p>The plate is stored and sent in one canonical form: three uppercase letters followed by three
 * or four digits, no hyphen. The law accepts {@code ABC-123} on input; the registry stores
 * {@code ABC123}, and every lookup canonicalizes before it compares. A client that displays the
 * plate therefore shows exactly what the registry key is.
 */
public final class VehicleKeys {

    // ------------------------------------------------------- request payloads

    /** Payload: the plate, in any law-valid form ({@code ABC-123} or {@code ABC123}). */
    public static final String KEY_PLATE = "plate";
    /** Payload: the vehicle class id: quad, car, truck, helicopter or plane. */
    public static final String KEY_CLASS = "vehClass";
    /** Payload: the stated impound or licence reason. Required for both mutations. */
    public static final String KEY_REASON = "reason";
    /** Payload: the list filter, one of {@link #FILTER_ALL}, {@link #FILTER_VALID}, ... */
    public static final String KEY_FILTER = "filter";
    /** Payload: the zero-based page a {@code VEH_LIST} asks for. */
    public static final String KEY_PAGE = "page";
    /** Payload: the licence class for a licence action; also echoed in vehicle detail. */
    public static final String KEY_LICENSE_CLASS = "licenseClass";
    /** Payload: the points to assess: 2, 4 or 6 under Article 6.09. */
    public static final String KEY_POINTS = "points";

    // ---------------------------------------------------------------- filters

    /** Every vehicle in scope. */
    public static final String FILTER_ALL = "all";
    /** Vehicles whose registration is valid and not impounded. */
    public static final String FILTER_VALID = "valid";
    /** Vehicles currently held in impound. */
    public static final String FILTER_IMPOUNDED = "impounded";
    /** Impounds past 30 in-game days, marked for staff disposal. */
    public static final String FILTER_FLAGGED = "flagged";

    // ----------------------------------------------------------- viewer flags

    /** Viewer flag: the actor's effective set holds {@code vehicle.register}. */
    public static final String CAN_REGISTER = "canRegister";
    /** Viewer flag: the actor's effective set holds {@code vehicle.impound}. */
    public static final String CAN_IMPOUND = "canImpound";
    /** Viewer flag: the actor's effective set holds {@code vehicle.release}. */
    public static final String CAN_RELEASE = "canRelease";
    /** Viewer flag: the actor's effective set holds {@code plate.scan}. */
    public static final String CAN_SCAN = "canScan";
    /**
     * Viewer flag: the actor's effective set holds {@code vehicle.register}, which is the
     * registrar authority for licences too (Article 8.12). No separate licence capability exists.
     */
    public static final String CAN_MANAGE_LICENSES = "canManageLicenses";
    /** The viewer's own UUID, so the client can mark which rows are theirs. */
    public static final String VIEWER_UUID = "viewerUuid";
    /** The viewer's own display name, so the register picker can default to the actor. */
    public static final String VIEWER_NAME = "viewerName";
    /** Whether a reception location is configured. Release in person is refused when it is not. */
    public static final String RECEPTION_CONFIGURED = "receptionConfigured";
    /** The configured payment point's display name. */
    public static final String PAYMENT_POINT = "paymentPoint";
    /** The configured release fee in gold ingots, copied onto a new impound. */
    public static final String RELEASE_FEE = "releaseFee";

    // -------------------------------------------------------- VEH_LIST block

    /** The true number of vehicles in the requested filter and scope. */
    public static final String TOTAL = "vehicles";
    /** The zero-based page this answer carries. */
    public static final String PAGE = "page";
    /** The true number of pages at {@link #PAGE_SIZE}. */
    public static final String PAGES = "pages";
    /** The filter this answer was built for, echoed as the canonical filter id. */
    public static final String FILTER = "filter";
    /** Prefix of one vehicle row: {@code vehicle.<n>.id}. */
    public static final String ROW_PREFIX = "vehicle.";
    /** Row field: the stable vehicle id. */
    public static final String ROW_ID = ".id";
    /** Row field: the canonical plate. */
    public static final String ROW_PLATE = ".plate";
    /** Row field: the vehicle class id. */
    public static final String ROW_CLASS = ".class";
    /** Row field: the owner's display name. */
    public static final String ROW_OWNER = ".owner";
    /** Row field: the owner's UUID, as text. */
    public static final String ROW_OWNER_UUID = ".ownerUuid";
    /** Row field: the registration status name. */
    public static final String ROW_STATUS = ".status";
    /** Row field: true when an impound is active. */
    public static final String ROW_IMPOUND = ".impound";
    /** Row field: the impound reason text, clipped to {@link #LIST_REASON_CHARS}. */
    public static final String ROW_IMPOUND_REASON = ".impoundReason";
    /** Row field: the owner's effective licence state for this vehicle's class. */
    public static final String ROW_LICENSE = ".license";
    /** Row field: the owner's active points for this vehicle's class. */
    public static final String ROW_POINTS = ".points";
    /** Row field: true when the impound is flagged for disposal. */
    public static final String ROW_DISPOSAL = ".disposal";
    /** The true number of online players the owner picker can offer. */
    public static final String ONLINE_COUNT = "online";
    /** Prefix of one online player name: {@code online.<n>}. */
    public static final String ONLINE_PREFIX = "online.";

    // -------------------------------------------------------- VEH_INFO block

    /** Detail: the stable vehicle id. */
    public static final String ID = "id";
    /** Detail: the canonical plate. */
    public static final String PLATE = "plate";
    /** Detail: the vehicle class id. */
    public static final String CLASS = "vehClass";
    /** Detail: the owner's display name, or empty when unowned. */
    public static final String OWNER = "owner";
    /** Detail: the owner's UUID, as text, or empty. */
    public static final String OWNER_UUID = "ownerUuid";
    /** Detail: the registration status name. */
    public static final String STATUS = "status";
    /** Detail: epoch milliseconds the vehicle was registered. */
    public static final String REGISTERED_AT = "registeredAt";
    /** Detail: the registering official's display name, or empty. */
    public static final String REGISTERED_BY = "registeredBy";
    /** Detail: epoch milliseconds the registration expires, {@code 0} for none. */
    public static final String EXPIRES_AT = "expiresAt";
    /** Detail: true when an impound is active. */
    public static final String IMPOUND_ACTIVE = "impoundActive";
    /** Detail: the impound's typed ground name. */
    public static final String IMPOUND_GROUND = "impoundGround";
    /** Detail: the impound's free-text reason, in full. */
    public static final String IMPOUND_REASON = "impoundReason";
    /** Detail: epoch milliseconds of the impound, {@code 0} when never. */
    public static final String IMPOUNDED_AT = "impoundedAt";
    /** Detail: the impounding official's display name, or empty. */
    public static final String IMPOUNDED_BY = "impoundedBy";
    /** Detail: the recorded impound location, or empty. */
    public static final String IMPOUND_LOCATION = "impoundLocation";
    /** Detail: the fee in gold ingots that applies to this impound. */
    public static final String FEE = "releaseFee";
    /** Detail: epoch milliseconds of the release, {@code 0} when still held. */
    public static final String RELEASED_AT = "releasedAt";
    /** Detail: the releasing official's display name, or empty. */
    public static final String RELEASED_BY = "releasedBy";
    /** Detail: true when the 30-day disposal mark is set. */
    public static final String DISPOSAL = "disposal";
    /** Detail: epoch milliseconds the disposal mark was set, {@code 0} when not. */
    public static final String DISPOSAL_AT = "disposalAt";
    /** Detail: the owner's effective licence state for this vehicle's class: VALID/SUSPENDED/REVOKED/NONE. */
    public static final String LICENSE_STATE = "licenseState";
    /** Detail: the owner's active point total for this vehicle's class. */
    public static final String LICENSE_POINTS = "licensePoints";
    /** Detail: epoch milliseconds the owner's suspension ends, {@code 0} when none. */
    public static final String LICENSE_SUSPENDED_UNTIL = "licenseSuspendedUntil";
    /** Detail flag: the viewer may impound this vehicle now. */
    public static final String CAN_IMPOUND_THIS = "canImpoundThis";
    /** Detail flag: the viewer may release this vehicle now. */
    public static final String CAN_RELEASE_THIS = "canReleaseThis";

    // --------------------------------------------------------- PLATE_SCAN block

    /** Scan: the stable scan id, empty for a console read that was not written to the log. */
    public static final String SCAN_ID = "scanId";
    /** Scan: the canonical plate that was looked up. */
    public static final String SCAN_PLATE = "scanPlate";
    /** Scan: the {@code ScanResult} name. */
    public static final String SCAN_RESULT = "scanResult";
    /** Scan: the registered owner's display name, or empty. */
    public static final String SCAN_OWNER = "scanOwner";
    /** Scan: the vehicle class id, or empty when unregistered. */
    public static final String SCAN_CLASS = "scanClass";
    /** Scan: the vehicle status name, or empty when unregistered. */
    public static final String SCAN_STATUS = "scanStatus";
    /** Scan: true when an impound is active. */
    public static final String SCAN_IMPOUND = "scanImpound";
    /** Scan: the impound reason, clipped. */
    public static final String SCAN_IMPOUND_REASON = "scanImpoundReason";
    /** Scan: the owner's licence state for the scanned vehicle's class. */
    public static final String SCAN_LICENSE_STATE = "scanLicenseState";
    /** Scan: the owner's active points for the scanned vehicle's class. */
    public static final String SCAN_LICENSE_POINTS = "scanLicensePoints";
    /** Scan: epoch milliseconds of the scan. */
    public static final String SCAN_AT = "scanAt";
    /**
     * Scan: where the physical-vehicle information came from, for example {@code registry-only}.
     * A future vehicle mod supplies a richer adapter; the field lets the reader know which one.
     */
    public static final String SCAN_SOURCE = "scanSource";

    // ------------------------------------------------------------------ limits

    /** Vehicle rows per {@code VEH_LIST} page. */
    public static final int PAGE_SIZE = 8;
    /** Most online names one {@code VEH_LIST} answer carries for the owner picker. */
    public static final int MAX_LISTED_ONLINE = 64;
    /** Longest impound reason a list or scan row carries; the detail block carries the full one. */
    public static final int LIST_REASON_CHARS = 96;

    private VehicleKeys() {
    }
}
