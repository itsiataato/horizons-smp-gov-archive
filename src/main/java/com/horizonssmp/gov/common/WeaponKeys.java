package com.horizonssmp.gov.common;

/**
 * The stable field keys of the Weapons tab, the weapon actions and the chat mirrors.
 *
 * <p>Keys are the wire contract between the server and the client. They live in the common package
 * so a panel never imports a server class to learn one. Row keys are indexed by position; the
 * identity is the serial (registry) or the permit/area id.
 *
 * <p>Every list answer carries the true total and the page bounds, so a short answer reads as a
 * page rather than as a complete registry. Reason and note text in list rows is clipped; the detail
 * block carries the full value.
 */
public final class WeaponKeys {

    // ------------------------------------------------------- request payloads

    /** Payload: the weapon serial, in any case ({@code AB-1234} and {@code ab-1234} are one). */
    public static final String KEY_SERIAL = "serial";
    /** Payload: the weapon class id: l, s, r, p or h. */
    public static final String KEY_CLASS = "weaponClass";
    /** Payload: the item id or officer's description stored as the registry type. */
    public static final String KEY_TYPE = "weaponType";
    /** Payload: the permit class id: l, s, r, p, h, d, g, blasting or mining. */
    public static final String KEY_PERMIT_CLASS = "permitClass";
    /** Payload: the list filter id. */
    public static final String KEY_FILTER = "filter";
    /** Payload: the zero-based page a list request asks for. */
    public static final String KEY_PAGE = "page";
    /** Payload: a recorded reason, required by revoke, loss, suspend and area create. */
    public static final String KEY_REASON = "reason";
    /** Payload: the item id to classify; empty means the actor's held item. */
    public static final String KEY_ITEM_ID = "itemId";
    /** Payload: suspension length in minutes. */
    public static final String KEY_MINUTES = "minutes";
    /** Payload: explosion-area dimension id. */
    public static final String KEY_DIMENSION = "dimension";
    /** Payload: explosion-area center X. */
    public static final String KEY_X = "x";
    /** Payload: explosion-area center Y. */
    public static final String KEY_Y = "y";
    /** Payload: explosion-area center Z. */
    public static final String KEY_Z = "z";
    /** Payload: explosion-area radius in blocks. */
    public static final String KEY_RADIUS = "radius";
    /** Payload: explosion-area validity in minutes. */
    public static final String KEY_VALID_MINUTES = "validMinutes";
    /** Payload: the explosion-area permit id. */
    public static final String KEY_AREA_ID = "areaId";

    // ---------------------------------------------------------------- filters

    /** Every weapon in scope. */
    public static final String FILTER_ALL = "all";
    /** Active registrations only. */
    public static final String FILTER_ACTIVE = "active";
    /** Revoked registrations only. */
    public static final String FILTER_REVOKED = "revoked";
    /** Weapons reported lost or stolen. */
    public static final String FILTER_LOST = "lost";

    // ----------------------------------------------------------- viewer flags

    /** Viewer flag: the actor holds {@code weapon.register}. */
    public static final String CAN_REGISTER = "canRegister";
    /** Viewer flag: the actor may see every registration, not only their own. */
    public static final String CAN_VIEW_ALL = "canViewAll";
    /** Viewer flag: the actor holds {@code military.authority}. */
    public static final String CAN_MILITARY = "canMilitary";
    /** The viewer's own UUID. */
    public static final String VIEWER_UUID = "viewerUuid";
    /** The viewer's own display name. */
    public static final String VIEWER_NAME = "viewerName";
    /** The configured season end as epoch milliseconds, {@code 0} when no season end is configured. */
    public static final String SEASON_END = "seasonEnd";
    /** Whether any town core is configured. */
    public static final String TOWN_CORES = "townCores";
    /** The configured town-core count. */
    public static final String TOWN_CORE_COUNT = "townCoreCount";

    // -------------------------------------------------------- registry rows

    /** The true number of weapons in the requested filter and scope. */
    public static final String TOTAL = "weapons";
    /** The zero-based page this answer carries. */
    public static final String PAGE = "page";
    /** The true number of pages at {@link #PAGE_SIZE}. */
    public static final String PAGES = "pages";
    /** The filter this answer was built for. */
    public static final String FILTER = "filter";
    /** Prefix of one weapon row: {@code weapon.<n>.serial}. */
    public static final String ROW_PREFIX = "weapon.";
    /** Row field: the stable weapon id. */
    public static final String ROW_ID = ".id";
    /** Row field: the canonical serial. */
    public static final String ROW_SERIAL = ".serial";
    /** Row field: the class id. */
    public static final String ROW_CLASS = ".class";
    /** Row field: the registry type or item id. */
    public static final String ROW_TYPE = ".type";
    /** Row field: the owner's display name. */
    public static final String ROW_OWNER = ".owner";
    /** Row field: the owner's UUID text, or empty. */
    public static final String ROW_OWNER_UUID = ".ownerUuid";
    /** Row field: the status name. */
    public static final String ROW_STATUS = ".status";
    /** Row field: the permit reference, or empty. */
    public static final String ROW_PERMIT = ".permit";
    /** Row field: true for a military-grade registration. */
    public static final String ROW_MILITARY = ".military";
    /** Row field: epoch milliseconds of registration. */
    public static final String ROW_REGISTERED_AT = ".registeredAt";

    // ---------------------------------------------------------- registry detail

    /** Detail: the stable weapon id. */
    public static final String ID = "id";
    /** Detail: the canonical serial. */
    public static final String SERIAL = "serial";
    /** Detail: the class id. */
    public static final String CLASS = "weaponClass";
    /** Detail: the registry type or item id. */
    public static final String TYPE = "weaponType";
    /** Detail: the recorded display name. */
    public static final String NAME = "name";
    /** Detail: the optional mod identifier, or empty. */
    public static final String MOD_ID = "taczId";
    /** Detail: the owner's display name, or empty. */
    public static final String OWNER = "owner";
    /** Detail: the owner's UUID text, or empty. */
    public static final String OWNER_UUID = "ownerUuid";
    /** Detail: the status name. */
    public static final String STATUS = "status";
    /** Detail: the permit reference, or empty. */
    public static final String PERMIT = "permit";
    /** Detail: true for a military-grade registration. */
    public static final String MILITARY = "military";
    /** Detail: epoch milliseconds of registration. */
    public static final String REGISTERED_AT = "registeredAt";
    /** Detail: the registering official's name, or empty. */
    public static final String REGISTERED_BY = "registeredBy";
    /** Detail: the dealer's name, or empty. */
    public static final String DEALER = "dealer";
    /** Detail: how the weapon entered the register. */
    public static final String SOURCE = "source";
    /** Detail: epoch milliseconds of a dealer sale, {@code 0} when not a sale. */
    public static final String SOLD_AT = "soldAt";
    /** Detail: the true number of recorded transfers. */
    public static final String TRANSFER_COUNT = "transfers";
    /** Prefix of one transfer row: {@code transfer.<n>.to}. */
    public static final String TRANSFER_PREFIX = "transfer.";
    /** Transfer row: the previous owner's name, or empty. */
    public static final String TRANSFER_FROM = ".from";
    /** Transfer row: the new owner's name. */
    public static final String TRANSFER_TO = ".to";
    /** Transfer row: epoch milliseconds requested. */
    public static final String TRANSFER_REQUESTED_AT = ".requestedAt";
    /** Transfer row: epoch milliseconds completed. */
    public static final String TRANSFER_COMPLETED_AT = ".completedAt";
    /** Transfer row: the official's name, or empty. */
    public static final String TRANSFER_BY = ".by";
    /** Transfer row: true when the stamps are inside the one-day window. */
    public static final String TRANSFER_IN_WINDOW = ".inWindow";

    // ------------------------------------------------------------ permits

    /** The true number of permits in the requested scope. */
    public static final String PERMIT_TOTAL = "permits";
    /** The zero-based permit page. */
    public static final String PERMIT_PAGE = "permitPage";
    /** The true permit page count. */
    public static final String PERMIT_PAGES = "permitPages";
    /** Prefix of one permit row: {@code permit.<n>.class}. */
    public static final String PERMIT_PREFIX = "permit.";
    /** Permit row: the stable record id. */
    public static final String PERMIT_ID = ".id";
    /** Permit row: the holder's display name. */
    public static final String PERMIT_HOLDER = ".holder";
    /** Permit row: the holder's UUID text. */
    public static final String PERMIT_HOLDER_UUID = ".holderUuid";
    /** Permit row: the class id. */
    public static final String PERMIT_CLASS = ".class";
    /** Permit row: the effective status name. */
    public static final String PERMIT_STATUS = ".status";
    /** Permit row: the issuing official's name, or empty. */
    public static final String PERMIT_ISSUED_BY = ".issuedBy";
    /** Permit row: epoch milliseconds issued. */
    public static final String PERMIT_ISSUED_AT = ".issuedAt";
    /** Permit row: epoch milliseconds expires, {@code 0} for none. */
    public static final String PERMIT_EXPIRES_AT = ".expiresAt";
    /** Permit row: the suspension or revocation reason, clipped. */
    public static final String PERMIT_REASON = ".reason";
    /** Permit row: true for a military-only class. */
    public static final String PERMIT_MILITARY = ".military";
    /** Permit row: true when the viewer may revoke or suspend it. */
    public static final String PERMIT_CAN_MANAGE = ".canManage";

    // ------------------------------------------------------- explosion areas

    /** The true number of explosion-area permits. */
    public static final String AREA_TOTAL = "areas";
    /** The zero-based area page. */
    public static final String AREA_PAGE = "areaPage";
    /** The true area page count. */
    public static final String AREA_PAGES = "areaPages";
    /** Prefix of one area row: {@code area.<n>.id}. */
    public static final String AREA_PREFIX = "area.";
    /** Area row: the stable permit id. */
    public static final String AREA_ID = ".id";
    /** Area row: the dimension id. */
    public static final String AREA_DIMENSION = ".dimension";
    /** Area row: the center X. */
    public static final String AREA_X = ".x";
    /** Area row: the center Y. */
    public static final String AREA_Y = ".y";
    /** Area row: the center Z. */
    public static final String AREA_Z = ".z";
    /** Area row: the radius. */
    public static final String AREA_RADIUS = ".radius";
    /** Area row: the effective status at read time. */
    public static final String AREA_STATUS = ".status";
    /** Area row: epoch milliseconds valid from. */
    public static final String AREA_VALID_FROM = ".validFrom";
    /** Area row: epoch milliseconds valid until. */
    public static final String AREA_VALID_UNTIL = ".validUntil";
    /** Area row: the reason, clipped. */
    public static final String AREA_REASON = ".reason";
    /** Area row: the issuing official's name. */
    public static final String AREA_ISSUED_BY = ".issuedBy";
    /** Area row: the box text. */
    public static final String AREA_BOX = ".box";

    // --------------------------------------------------------- classification

    /** Classification: the item id that was classified. */
    public static final String ITEM_ID = "itemId";
    /** Classification: the class id. */
    public static final String ITEM_CLASS = "itemClass";
    /** Classification: the human-readable class name. */
    public static final String ITEM_CLASS_LABEL = "itemClassLabel";
    /** Classification: where the answer came from: json, tag, tacz, heuristic, unclassified or none. */
    public static final String ITEM_SOURCE = "itemSource";
    /** Classification: true when the item is treated as weapon-like. */
    public static final String ITEM_WEAPON_LIKE = "itemWeaponLike";
    /** Classification: the permit class that authorizes it, or empty. */
    public static final String ITEM_PERMIT_CLASS = "itemPermitClass";
    /** Classification: true for the Class H military-grade list. */
    public static final String ITEM_MILITARY = "itemMilitary";
    /** Classification: true for an explosive. */
    public static final String ITEM_EXPLOSIVE = "itemExplosive";
    /** Classification: true for nuclear material. */
    public static final String ITEM_NUCLEAR = "itemNuclear";
    /** Classification: a one-line explanation safe to show in chat. */
    public static final String ITEM_EXPLANATION = "itemExplanation";

    // ------------------------------------------------------------- online picker

    /** The true number of online players the picker can offer. */
    public static final String ONLINE_COUNT = "online";
    /** Prefix of one online player name: {@code online.<n>}. */
    public static final String ONLINE_PREFIX = "online.";

    // ------------------------------------------------------------------ limits

    /** Weapon rows per page. */
    public static final int PAGE_SIZE = 8;
    /** Permit rows per page. */
    public static final int PERMIT_PAGE_SIZE = 8;
    /** Area rows per page. */
    public static final int AREA_PAGE_SIZE = 8;
    /** Most online names one answer carries for the pickers. */
    public static final int MAX_LISTED_ONLINE = 64;
    /** Longest reason or note carried in a list row. */
    public static final int LIST_REASON_CHARS = 96;
    /** Most transfers a detail block carries. */
    public static final int MAX_LISTED_TRANSFERS = 8;

    private WeaponKeys() {
    }
}
