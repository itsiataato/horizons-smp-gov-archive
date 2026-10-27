package com.horizonssmp.gov.server.economy;

import com.horizonssmp.gov.config.GovConfig;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * The configured bank and town-hall regions, with their startup validation.
 *
 * <p>This class is the runtime half of economy specification section 5: {@link #isBank} and
 * {@link #isTownhall} answer whether a player is standing on a counter, and
 * {@link #validationErrors} is the list a startup check turns into a refusal to boot. The two are
 * deliberately separate. A malformed entry is never dropped into "nowhere" at runtime - it is
 * reported by {@code validationErrors()}, and the startup check is expected to stop the server
 * before a player ever reaches an empty list.
 *
 * <h2>What is checked</h2>
 *
 * <p>Per {@code docs/ECONOMY-SPEC.md} 5.2, {@link #validationErrors()} reports:
 *
 * <ul>
 *   <li>an entry that is blank or malformed (wrong field count, bad bounds, bad dimension);</li>
 *   <li>a blank dimension;</li>
 *   <li>a volume outside {@code 1..1_000_000} (only "too large" is reachable: a normalised box
 *       always has at least one block);</li>
 *   <li>two byte-identical entries in the same list; and</li>
 *   <li>a list longer than {@code gov.bank.maxRegions}.</li>
 * </ul>
 *
 * <p>An empty list is not an error here. "No bank configured while the bank is enabled" is a
 * startup-check rule, not a parsing rule, and keeping it out of this class lets the check phrase
 * the failure once.
 *
 * <h2>Loading and reloading</h2>
 *
 * <p>{@link #load()} reads {@code GovConfig} once. The values are not re-read on every region
 * check: a config reload takes effect only when the owner of this instance calls {@link #reload()},
 * so every action in a tick sees one consistent set of regions. {@link #of(List, List, int)} is
 * the headless constructor for a startup check or a test.
 *
 * <h2>Threading</h2>
 *
 * <p>Every method that takes a {@link ServerPlayer} reads live world state and MUST be called on
 * the server thread. There is no per-player cache and no level reference stored here: a region
 * removal or a config reload takes effect on the next call, which is the behaviour section 5.3
 * requires. There is no operator, creative or OP bypass.
 */
public final class BankRegions {

    /** Config key of the bank list; also the label used in validation messages. */
    public static final String BANK_KEY = "gov.bank.bankRegions";

    /** Config key of the town-hall list; also the label used in validation messages. */
    public static final String TOWNHALL_KEY = "gov.bank.townhallRegions";

    /** One immutable load of the configuration. Swapped wholesale by {@link #reload()}. */
    private record Snapshot(
            List<BankRegion> banks,
            List<BankRegion> townhalls,
            List<String> errors,
            int maxRegions) {
    }

    private volatile Snapshot snapshot;

    private BankRegions(final Snapshot snapshot) {
        this.snapshot = snapshot;
    }

    /** Loads the two lists from {@code GovConfig} with its configured per-list maximum. */
    public static BankRegions load() {
        return of(GovConfig.bankRegions(), GovConfig.townhallRegions(), GovConfig.bankMaxRegions());
    }

    private static volatile BankRegions cached;

    /** Returns a cached parsed instance, loading once from {@code GovConfig}. */
    public static BankRegions current() {
        BankRegions result = cached;
        if (result == null) {
            synchronized (BankRegions.class) {
                result = cached;
                if (result == null) {
                    result = load();
                    cached = result;
                }
            }
        }
        return result;
    }

    /**
     * Builds an instance from explicit lists.
     *
     * <p>Intended for the startup check, which wants to prove the rules against a candidate
     * configuration, and for tests. The lists are copied; a caller cannot mutate this instance.
     *
     * @param maxRegions per-list maximum; values below one are raised to one, the config floor
     */
    public static BankRegions of(
            final List<String> bankEntries,
            final List<String> townhallEntries,
            final int maxRegions) {
        return new BankRegions(build(bankEntries, townhallEntries, maxRegions));
    }

    /**
     * Re-reads {@code GovConfig} and replaces the snapshot atomically.
     *
     * <p>This is the only way a config reload reaches a live instance. It is an explicit call
     * because a region list is legal authority: it must change at a point the caller chose, not
     * in the middle of a transaction.
     */
    public void reload() {
        this.snapshot = build(
                GovConfig.bankRegions(), GovConfig.townhallRegions(), GovConfig.bankMaxRegions());
    }

    /** The parsed bank regions, in config order, capped at {@link #maxRegions()}. */
    public List<BankRegion> banks() {
        return snapshot.banks();
    }

    /** The parsed town-hall regions, in config order, capped at {@link #maxRegions()}. */
    public List<BankRegion> townhalls() {
        return snapshot.townhalls();
    }

    /** The effective per-list maximum. */
    public int maxRegions() {
        return snapshot.maxRegions();
    }

    /**
     * Every configuration error, empty when both lists are usable.
     *
     * <p>The list is immutable and stable until {@link #reload()}. Messages name the config key
     * and the one-based entry number, so an operator can fix the file without guessing. A
     * malformed entry produces one message; parsing stops for that entry, not for the list.
     */
    public List<String> validationErrors() {
        return snapshot.errors();
    }

    /**
     * Alias for {@link #validationErrors()}, named for the startup check that calls it.
     *
     * @return every configuration error, empty when both lists are usable
     */
    public List<String> validate() {
        return validationErrors();
    }

    /** True when {@link #validationErrors()} is empty. */
    public boolean valid() {
        return snapshot.errors().isEmpty();
    }

    /**
     * True when the player is inside a bank region in the same dimension.
     *
     * <p>The player's own {@code blockPosition()} is the authority; a client cannot send
     * coordinates that satisfy this check. Overlap with a town hall is allowed.
     */
    public boolean isBank(final ServerPlayer player) {
        if (player == null) {
            return false;
        }
        final Snapshot state = snapshot;
        return covered(state.banks(), dimension(player), player.blockPosition());
    }

    /**
     * True when the player is inside a town-hall region in the same dimension.
     *
     * <p>A town hall accepts a deposit only. Callers that need the stronger bank authority must
     * call {@link #isBank}; this method does not treat a bank as a town hall.
     */
    public boolean isTownhall(final ServerPlayer player) {
        if (player == null) {
            return false;
        }
        final Snapshot state = snapshot;
        return covered(state.townhalls(), dimension(player), player.blockPosition());
    }

    /**
     * True when the player is inside either list, which is the deposit-only gate.
     *
     * <p>Both lists are scanned once against one captured position and snapshot, so a reload
     * between the two lists cannot produce a half-answer. A bank match is not distinguished here;
     * callers that need the "bank wins" feedback string ask {@link #isBank} separately.
     */
    public boolean isBankOrTownhall(final ServerPlayer player) {
        if (player == null) {
            return false;
        }
        final Snapshot state = snapshot;
        final ResourceLocation dimension = dimension(player);
        final BlockPos pos = player.blockPosition();
        return covered(state.banks(), dimension, pos)
                || covered(state.townhalls(), dimension, pos);
    }

    private static ResourceLocation dimension(final ServerPlayer player) {
        return player.level().dimension().location();
    }

    private static boolean covered(
            final List<BankRegion> regions,
            final ResourceLocation dimension,
            final BlockPos pos) {
        for (final BankRegion region : regions) {
            if (region.contains(dimension, pos)) {
                return true;
            }
        }
        return false;
    }

    private static Snapshot build(
            final List<String> bankEntries,
            final List<String> townhallEntries,
            final int configuredMax) {
        final int max = Math.max(1, configuredMax);
        final List<String> errors = new ArrayList<>();
        // A server that turns the bank on without naming a counter still needs a working
        // economy. Fall back to the government reception block so deposits, withdrawals,
        // transfers and contract posting work out of the box instead of failing every action
        // with REGION_REQUIRED. An explicit, even malformed, entry is never replaced.
        final List<String> effectiveBanks = (bankEntries == null || bankEntries.isEmpty())
                && GovConfig.bankEnabled()
                ? List.of(BankRegionsSupport.defaultBankRegion())
                : bankEntries;
        final List<BankRegion> banks = BankRegionsSupport.parseList(BANK_KEY, effectiveBanks, max, errors);
        final List<BankRegion> townhalls = BankRegionsSupport.parseList(TOWNHALL_KEY, townhallEntries, max, errors);
        return new Snapshot(banks, townhalls, List.copyOf(errors), max);
    }

}
