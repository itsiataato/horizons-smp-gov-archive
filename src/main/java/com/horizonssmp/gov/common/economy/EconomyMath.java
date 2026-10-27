package com.horizonssmp.gov.common.economy;

import java.util.OptionalLong;

import com.horizonssmp.gov.common.records.AccountRecord;

/**
 * The pure arithmetic of the economy: amount parsing, the 2% tax formula, exact addition and the
 * conservation identity.
 *
 * <p>This class has no Minecraft type, no saved data and no I/O. It is the part of the economy that
 * can be exercised on a bare JVM, so every rule that can be stated as arithmetic lives here rather
 * than in {@code AccountService} or a handler. All arithmetic is integer and exact: overflow is a
 * server error and is never clamped.
 *
 * <p><b>Amounts.</b> A transaction amount is a positive whole number of gold ingots up to
 * {@link #MAX_BALANCE}. Parsing is stricter than {@link Long#parseLong(String)} on purpose: only
 * ASCII digits are accepted, so {@code +1}, {@code -1}, {@code 1.0}, {@code 1,000}, whitespace and
 * blank strings are all rejected before a transaction can be built.
 *
 * <p><b>Tax.</b> The formula is the one in {@code docs/ECONOMY-SPEC.md} section 4:
 * {@code tax = max(1, ceil(amount * 2 / 100))} for a positive gross, and {@code 0} otherwise.
 * Rounding is always against the payer. A transfer whose net after tax is below one ingot is
 * refused by the caller with {@code AMOUNT_TOO_SMALL}; this class still returns the mathematical
 * net, including zero, so the refusal is made in one place.
 */
public final class EconomyMath {

    /** The largest balance one account may hold, in gold ingots. */
    public static final long MAX_BALANCE = AccountRecord.MAX_BALANCE;

    /** The shortest accepted request nonce. */
    public static final int MIN_NONCE_LENGTH = 16;

    /** The longest accepted request nonce. */
    public static final int MAX_NONCE_LENGTH = 64;

    /**
     * The most decimal digits a valid amount can have. {@link Long#MAX_VALUE} has nineteen digits,
     * so a longer string cannot fit in a {@code long} and is rejected before parsing.
     */
    private static final int MAX_AMOUNT_DIGITS = 19;

    private EconomyMath() {
    }

    /**
     * Parses a client or command amount.
     *
     * <p>Only non-empty ASCII digits are accepted. The length is checked before
     * {@link Long#parseLong(String)} so an over-long hostile string cannot reach the parser, and the
     * parsed value is then checked against {@link #MAX_BALANCE}. Zero parses successfully because
     * it is a valid stored amount; every mutation rejects a non-positive transaction amount itself
     * with {@code BAD_PAYLOAD}.
     *
     * @param raw the amount text, may be {@code null}
     * @return the amount, or empty when the text is not a valid in-bounds amount
     */
    public static OptionalLong parseAmount(final String raw) {
        if (raw == null || raw.isEmpty() || raw.length() > MAX_AMOUNT_DIGITS) {
            return OptionalLong.empty();
        }
        for (int i = 0; i < raw.length(); i++) {
            final char c = raw.charAt(i);
            if (c < '0' || c > '9') {
                return OptionalLong.empty();
            }
        }
        final long value;
        try {
            value = Long.parseLong(raw);
        } catch (NumberFormatException overflow) {
            return OptionalLong.empty();
        }
        if (value > MAX_BALANCE) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(value);
    }

    /**
     * The tax on a gross amount: {@code max(1, ceil(gross * 2 / 100))} for a positive amount, and
     * zero otherwise.
     *
     * <p>The multiplication and addition are exact. Every amount that can reach this method is at
     * most {@link #MAX_BALANCE}, so the arithmetic cannot overflow in normal use; a caller that
     * passes a larger value gets an {@link ArithmeticException}, which is the documented
     * overflow-is-a-server-error policy.
     */
    public static long tax(final long gross) {
        if (gross <= 0L) {
            return 0L;
        }
        final long doubled = Math.multiplyExact(gross, 2L);
        final long rounded = Math.addExact(doubled, 99L);
        return Math.max(1L, rounded / 100L);
    }

    /**
     * The amount a recipient receives after tax: {@code gross - tax(gross)}.
     *
     * <p>The result can be zero for a one-ingot gross; the transaction layer, not this method,
     * refuses that with {@code AMOUNT_TOO_SMALL}.
     */
    public static long net(final long gross) {
        return subtractExact(gross, tax(gross));
    }

    /**
     * Exact addition with a clear failure message.
     *
     * @throws ArithmeticException when the result does not fit in a {@code long}
     */
    public static long addExact(final long left, final long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException overflow) {
            throw new ArithmeticException(
                    "Economy amount overflow adding " + left + " and " + right);
        }
    }

    /**
     * Exact subtraction with a clear failure message.
     *
     * @throws ArithmeticException when the result does not fit in a {@code long}
     */
    public static long subtractExact(final long left, final long right) {
        try {
            return Math.subtractExact(left, right);
        } catch (ArithmeticException overflow) {
            throw new ArithmeticException(
                    "Economy amount overflow subtracting " + right + " from " + left);
        }
    }

    /**
     * True when a request nonce is well formed: 16 to 64 characters from {@code [A-Za-z0-9_-]}.
     *
     * <p>The nonce is a replay guard, not a display string. It is never trimmed, lower-cased or
     * otherwise normalised, because two spellings must remain two distinct requests.
     */
    public static boolean validNonce(final String nonce) {
        if (nonce == null) {
            return false;
        }
        final int length = nonce.length();
        if (length < MIN_NONCE_LENGTH || length > MAX_NONCE_LENGTH) {
            return false;
        }
        for (int i = 0; i < length; i++) {
            final char c = nonce.charAt(i);
            final boolean allowed = (c >= 'A' && c <= 'Z')
                    || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9')
                    || c == '_'
                    || c == '-';
            if (!allowed) {
                return false;
            }
        }
        return true;
    }

    /**
     * The conservation identity:
     * {@code sum(balances) + escrow == totalDeposited - totalWithdrawn}.
     *
     * <p>Returns false for a negative input and for arithmetic that would overflow, so a damaged
     * file reports a failed assertion instead of wrapping into an apparently valid identity.
     */
    public static boolean conservation(
            final long sumBalances,
            final long escrow,
            final long totalDeposited,
            final long totalWithdrawn) {
        if (sumBalances < 0L || escrow < 0L || totalDeposited < 0L || totalWithdrawn < 0L) {
            return false;
        }
        try {
            final long left = addExact(sumBalances, escrow);
            final long right = subtractExact(totalDeposited, totalWithdrawn);
            return left == right;
        } catch (ArithmeticException overflow) {
            return false;
        }
    }
}
