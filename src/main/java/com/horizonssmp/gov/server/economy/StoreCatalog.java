package com.horizonssmp.gov.server.economy;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.horizonssmp.gov.config.GovConfig;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The configured Government Store catalogue, parsed and validated once.
 *
 * <p>Section 7.2 of {@code docs/ECONOMY-SPEC.md} makes the catalogue a configuration artefact,
 * never a runtime command: only a listed item is purchasable, there is no registry fallback, and a
 * malformed entry must fail startup rather than disappear. This class is the parser and the
 * immutable snapshot a {@link StoreService} sells from. It owns no player state and never mutates
 * an inventory.
 *
 * <p>Parsing and validation live in {@link StoreCatalogParsing}; the recipe-derived gold scan lives
 * in {@link StoreCatalogGoldScan}. This class keeps the public API and the immutable snapshot.
 */
public final class StoreCatalog {

    /** The largest catalogue the specification permits, in entries. */
    public static final int MAX_ENTRIES = 256;

    /** Slots on one store page: nine columns by four rows. */
    public static final int PAGE_SIZE = 36;

    /** The lowest accepted tier and price, in gold ingots per stack. */
    public static final int MIN_TIER = 1;

    /** The highest accepted tier and price, in gold ingots per stack. */
    public static final int MAX_TIER = 4;

    /** The smallest accepted purchase, in stacks. */
    public static final int MIN_STACKS = 1;

    /** The largest accepted purchase, in stacks. */
    public static final int MAX_STACKS = 10;

    /** Config key of the catalogue list; also the label used in validation messages. */
    public static final String CATALOG_KEY = "gov.store.catalog";

    /** Config key of the floor list; also the label used in validation messages. */
    public static final String FLOOR_KEY = "gov.store.marketFloor";

    /**
     * One purchasable row.
     */
    public record Entry(String itemId, int tier, int price, int stackSize, String displayName) {

        public Entry {
            if (itemId == null || itemId.isBlank()) {
                throw new IllegalArgumentException("StoreCatalog.Entry requires an item id");
            }
            if (tier < MIN_TIER || tier > MAX_TIER) {
                throw new IllegalArgumentException(
                        "StoreCatalog.Entry tier must be " + MIN_TIER + ".." + MAX_TIER
                                + ", got " + tier);
            }
            if (price != tier) {
                throw new IllegalArgumentException(
                        "StoreCatalog.Entry price must equal the tier, got price " + price
                                + " and tier " + tier);
            }
            if (stackSize < 1) {
                throw new IllegalArgumentException(
                        "StoreCatalog.Entry stackSize must be positive, got " + stackSize);
            }
            itemId = itemId.strip();
            displayName = displayName == null || displayName.isBlank() ? itemId : displayName;
        }

        /** The total price of {@code stacks} whole stacks, in gold ingots. */
        public long totalPrice(final int stacks) {
            return (long) price * (long) stacks;
        }
    }

    private final boolean enabled;
    private final List<Entry> entries;
    private final Map<String, Entry> byId;
    private final Map<String, Item> items;
    private final List<String> errors;

    StoreCatalog(
            final boolean enabled,
            final List<Entry> entries,
            final Map<String, Entry> byId,
            final Map<String, Item> items,
            final List<String> errors) {
        this.enabled = enabled;
        this.entries = List.copyOf(entries);
        this.byId = Collections.unmodifiableMap(new LinkedHashMap<>(byId));
        this.items = Collections.unmodifiableMap(new LinkedHashMap<>(items));
        this.errors = List.copyOf(errors);
    }

    /**
     * Loads the catalogue from {@code GovConfig} and scans the recipe manager once.
     */
    public static StoreCatalog load(final MinecraftServer server) {
        final boolean enabled = GovConfig.bankEnabled() && GovConfig.storeEnabled();
        return of(
                GovConfig.storeCatalog(),
                GovConfig.storeMarketFloor(),
                enabled,
                StoreCatalogGoldScan.goldBearingItems(server));
    }

    /**
     * Builds a catalogue from explicit lists, with no recipe scan.
     */
    public static StoreCatalog of(
            final List<String> catalogEntries,
            final List<String> marketFloorEntries,
            final boolean enabled) {
        return of(catalogEntries, marketFloorEntries, enabled, Set.of());
    }

    /**
     * Builds a catalogue from explicit lists and a precomputed gold-bearing item set.
     */
    public static StoreCatalog of(
            final List<String> catalogEntries,
            final List<String> marketFloorEntries,
            final boolean enabled,
            final Set<ResourceLocation> goldBearingItems) {
        return StoreCatalogParsing.parse(catalogEntries, marketFloorEntries, enabled, goldBearingItems);
    }

    /** True when both {@code gov.bank.enabled} and {@code gov.store.enabled} were on at load. */
    public boolean enabled() {
        return enabled;
    }

    /** The purchasable rows, in config order, immutable. */
    public List<Entry> entries() {
        return entries;
    }

    /** The number of purchasable rows. */
    public int size() {
        return entries.size();
    }

    /** The row for a canonical item id, or {@code null} when it is not sold. */
    public Entry entry(final String itemId) {
        if (itemId == null) {
            return null;
        }
        return byId.get(itemId.strip());
    }

    /** A fresh plain template for a row, or {@link ItemStack#EMPTY} when the row is not sold. */
    public ItemStack template(final Entry entry) {
        if (entry == null) {
            return ItemStack.EMPTY;
        }
        final Item item = items.get(entry.itemId());
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    /**
     * Every configuration error, empty when the catalogue is usable.
     */
    public List<String> validationErrors() {
        return errors;
    }

    /** True when {@link #validationErrors()} is empty. */
    public boolean valid() {
        return errors.isEmpty();
    }
}