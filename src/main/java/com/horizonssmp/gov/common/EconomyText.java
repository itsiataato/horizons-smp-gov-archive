package com.horizonssmp.gov.common;

import java.util.Locale;

/**
 * Canonical neutral, colourless feedback for the economy commands and GUI.
 *
 * <p>Every string is taken verbatim from the economy specification. There is no section sign, no
 * colour code and no exclamation mark: failure is stated in the same calm register as success.
 * Both sides of the feature read these constants or helpers, so the command output and the
 * {@code economy.message} field can never drift apart.
 *
 * <p>Amounts are whole gold ingots. The helpers format with {@link Locale#ROOT} so a grouped
 * number reads identically on every server.
 */
public final class EconomyText {

    // ------------------------------------------------ failure feedback (3.7)

    /** Failure: the debit side cannot cover the amount. */
    public static final String INSUFFICIENT_FUNDS = "That account does not have enough gold.";
    /** Failure: the player does not carry the physical ingots. */
    public static final String INSUFFICIENT_ITEMS = "You do not carry that much gold.";
    /** Failure: the inventory cannot accept the delivered stacks. */
    public static final String INVENTORY_FULL = "Your inventory cannot hold that many items.";
    /** Failure: a credit would pass the per-account cap. */
    public static final String LIMIT_EXCEEDED = "That would exceed the account limit.";
    /** Failure: the net after tax would be below one ingot. */
    public static final String AMOUNT_TOO_SMALL = "That amount is too small after tax.";
    /** Failure: the nonce was already accepted for this actor. */
    public static final String DUPLICATE_REQUEST = "That request was already processed.";
    /** Failure: another transaction holds the account lock. */
    public static final String BUSY = "Another transaction is already running for that account.";
    /** Failure: the physical action needs a bank or town hall. */
    public static final String REGION_REQUIRED = "You must be inside a bank or town hall to do that.";
    /** Failure: source and destination resolve to the same account. */
    public static final String SELF_TRANSFER = "You cannot transfer to the same account.";
    /** Failure: the economy is switched off in configuration. */
    public static final String ECONOMY_DISABLED = "The economy is not enabled on this server.";
    /** Failure: the account exists but is not open for this action. */
    public static final String ACCOUNT_FROZEN = "That account is not open for that action.";
    /** Failure: a contract cannot be posted in the current state. */
    public static final String CONTRACT_LIMIT = "That contract cannot be created in the current state.";
    /** Failure: the item is not in the configured store catalogue. */
    public static final String STORE_UNAVAILABLE = "That item is not available in the store.";

    // --------------------------------------------- canonical formats (6.5)

    /** Format for a completed deposit: gross, tax and the resulting balance. */
    public static final String DEPOSITED_FORMAT =
            "Deposited %,d gold ingots. Tax %,d. Balance %,d.";
    /** Format for a completed pay: gross, recipient, tax and the net received. */
    public static final String PAID_FORMAT =
            "Paid %,d gold ingots to %s. Tax %,d. They received %,d.";

    private EconomyText() {
    }

    /**
     * The canonical deposit line, for example {@code Deposited 64 gold ingots. Tax 2.
     * Balance 1,234.}
     */
    public static String deposited(final long gross, final long tax, final long balance) {
        return String.format(Locale.ROOT, DEPOSITED_FORMAT, gross, tax, balance);
    }

    /**
     * The canonical pay line, for example {@code Paid 64 gold ingots to Alex. Tax 2. They
     * received 62.} A blank recipient reads as {@code that player}, so a missing name never
     * produces a malformed sentence.
     */
    public static String paid(
            final long gross, final String recipient, final long tax, final long net) {
        final String name = recipient == null || recipient.isBlank() ? "that player" : recipient;
        return String.format(Locale.ROOT, PAID_FORMAT, gross, name, tax, net);
    }
}
