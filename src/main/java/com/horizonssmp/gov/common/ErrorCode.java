package com.horizonssmp.gov.common;

/**
 * Why an action was refused.
 *
 * <p>The codes are stable and are the only thing a client may branch on. {@code errorText} is for
 * humans and may change wording between builds, so a future screen must never match on it.
 *
 * <p>Codes are persisted verbatim in the audit log when a rejection is recorded. Do not rename
 * constants without a schema migration.
 */
public enum ErrorCode {

    /** The actor's effective capabilities do not cover this action. */
    FORBIDDEN("You are not authorized for that action."),
    /** The actor sent too many actions in the rate-limit window. */
    RATE_LIMITED("Too many actions. Wait a moment and try again."),
    /** The payload was too large or malformed. */
    BAD_PAYLOAD("That request was malformed."),
    /** The named target could not be resolved on the server. */
    BAD_TARGET("That target could not be found."),
    /** The referenced law article does not exist. */
    BAD_ARTICLE("That law article does not exist."),
    /** The referenced job does not exist. */
    BAD_JOB("That job does not exist."),
    /** The referenced note does not exist, or is not on the named person's file. */
    BAD_NOTE("That note does not exist."),
    /** The referenced fine does not exist, or is not visible to the actor. */
    BAD_FINE("That fine does not exist."),
    /** The referenced vehicle or plate does not exist in the registry. */
    BAD_VEHICLE("That vehicle is not in the registry."),
    /** The referenced business does not exist in the registry. */
    BAD_BUSINESS("That business is not in the registry."),
    /** The referenced licence does not exist for that person and class. */
    BAD_LICENSE("That licence does not exist."),
    /** The referenced weapon serial is not in the registry. */
    BAD_WEAPON("That weapon is not in the registry."),
    /** Another weapon already uses that serial, case-insensitively. */
    SERIAL_TAKEN("That serial is already registered."),
    /** The referenced permit does not exist for that person and class. */
    BAD_PERMIT("That permit does not exist."),
    /** The referenced audit line does not exist in the retained log. */
    BAD_AUDIT("That audit entry does not exist."),
    /** The season-reset token is unknown, expired, already used or belongs to another actor. */
    BAD_TOKEN("That confirmation token is invalid or has expired."),
    /** An export or backup could not be written and verified safely. */
    EXPORT_FAILED("The export could not be written safely. See the server log."),
    /** The referenced explosion-area permit does not exist. */
    BAD_AREA_PERMIT("That explosion-area permit does not exist."),
    /** The record exists but is not in a state that allows the action. */
    INVALID_STATE("That action is not possible in the current state."),
    /** No handler is registered for the action yet. */
    UNSUPPORTED("That action is not available yet."),
    /** A detention, search or seizure lacked a legal basis. */
    NO_LEGAL_BASIS("That action needs consent, a violent offence or a warrant."),
    /** The plate is already registered to another vehicle. */
    PLATE_TAKEN("That plate is already registered."),
    /** Another business already uses that trading name, case-insensitively. */
    NAME_TAKEN("That name is already registered."),
    /** The action would remove the last holder of a required role. */
    LAST_EXEC("That would leave the government without an executive."),
    /** The actor tried to grant themselves a capability or rank. */
    SELF_PROMOTION("You cannot change your own rank or capabilities."),

    // ------------------------------------------------------------------ economy
    //
    // Neutral default text, no colour codes, no account ids, balances or internal keys. A service
    // may pass a more specific message, but the code is what a client branches on and what the
    // audit log stores.

    /** A debit would take an account below zero. */
    INSUFFICIENT_FUNDS("That account does not have enough gold."),
    /** The player carries fewer physical ingots than the deposit asks for. */
    INSUFFICIENT_ITEMS("You do not carry that much gold."),
    /** The delivery would not fit in the player's inventory. */
    INVENTORY_FULL("Your inventory cannot hold that many items."),
    /** A credit would take an account above its configured cap. */
    LIMIT_EXCEEDED("That would exceed the account limit."),
    /** The tax leaves a net below one ingot, so the transfer is refused. */
    AMOUNT_TOO_SMALL("That amount is too small after tax."),
    /** The request nonce was already accepted for this actor. */
    DUPLICATE_REQUEST("That request was already processed."),
    /** Another economy transaction holds this actor's lock. */
    BUSY("Another transaction is already running for that account."),
    /** The action needs a bank or town-hall region and the actor is outside every one. */
    REGION_REQUIRED("You must be inside a bank or town hall to do that."),
    /** Both sides of a transfer resolve to the same account. */
    SELF_TRANSFER("You cannot transfer to the same account."),
    /** The economy flag is off, so every mutation is refused. */
    ECONOMY_DISABLED("The economy is not enabled on this server."),
    /** The account exists but its status does not allow the requested action. */
    ACCOUNT_FROZEN("That account is not open for that action."),
    /** The contract cap, state or counterparty rule refuses the transition. */
    CONTRACT_LIMIT("That contract cannot be created in the current state."),
    /** The item is absent from the configured store catalogue. */
    STORE_UNAVAILABLE("That item is not available in the store.");

    private final String defaultText;

    ErrorCode(final String defaultText) {
        this.defaultText = defaultText;
    }

    /** Human-readable default message. Callers may override it with a more specific one. */
    public String defaultText() {
        return defaultText;
    }
}
