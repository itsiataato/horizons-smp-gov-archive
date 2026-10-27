package com.horizonssmp.gov.server.economy;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

import com.horizonssmp.gov.config.GovConfig;
import com.horizonssmp.gov.common.economy.EconomyMath;
import com.horizonssmp.gov.server.Sanitizer;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Parses and validates a {@link StoreCatalog} from raw config lines.
 *
 * <p>Package-private helper for {@link StoreCatalog}; not part of the public API.
 */
final class StoreCatalogParsing {

    private StoreCatalogParsing() {
    }

    /**
     * Builds a catalogue from explicit lists and a precomputed gold-bearing item set.
     */
    static StoreCatalog parse(
            final List<String> catalogEntries,
            final List<String> marketFloorEntries,
            final boolean enabled,
            final Set<ResourceLocation> goldBearingItems) {
        final List<String> errors = new ArrayList<>();
        final List<String> catalog = catalogEntries == null ? List.of() : catalogEntries;
        final List<String> floors = marketFloorEntries == null ? List.of() : marketFloorEntries;
        final Set<ResourceLocation> gold = goldBearingItems == null
                ? Set.of()
                : new HashSet<>(goldBearingItems);

        if (catalog.size() > StoreCatalog.MAX_ENTRIES) {
            errors.add(StoreCatalog.CATALOG_KEY + " has " + catalog.size() + " entries; the maximum is "
                    + StoreCatalog.MAX_ENTRIES + ".");
        }

        final Map<String, Long> floorByItem = parseFloors(floors, errors);
        final List<StoreCatalog.Entry> entries = new ArrayList<>();
        final Map<String, StoreCatalog.Entry> byId = new LinkedHashMap<>();
        final Map<String, Item> items = new LinkedHashMap<>();
        final Set<String> seen = new HashSet<>();

        for (int index = 0; index < catalog.size(); index++) {
            final String label = StoreCatalog.CATALOG_KEY + " entry " + (index + 1);
            final String raw = catalog.get(index);
            if (raw == null || raw.isBlank()) {
                errors.add(label + " is blank; expected itemId,tier.");
                continue;
            }
            final String text = raw.strip();
            final int comma = text.indexOf(',');
            if (comma < 0 || text.indexOf(',', comma + 1) >= 0) {
                errors.add(label + " must be exactly itemId,tier, got " + quoted(text) + ".");
                continue;
            }
            final String rawId = text.substring(0, comma).strip();
            final String rawTier = text.substring(comma + 1).strip();
            final ResourceLocation id = parseId(rawId);
            if (id == null) {
                errors.add(label + " item id " + quoted(rawId)
                        + " is not a lower-case namespaced id.");
                continue;
            }
            final String itemId = id.toString();
            if (!seen.add(itemId)) {
                errors.add(label + " repeats " + itemId + "; duplicate catalogue ids are refused.");
                continue;
            }
            final Integer tier = parseTier(rawTier);
            if (tier == null) {
                errors.add(label + " tier " + quoted(rawTier) + " is not an integer "
                        + StoreCatalog.MIN_TIER + ".." + StoreCatalog.MAX_TIER + ".");
                continue;
            }
            final Optional<Item> item = BuiltInRegistries.ITEM.getOptional(id);
            if (item.isEmpty()) {
                errors.add(label + " names " + itemId + ", which is not a registered item.");
                continue;
            }
            if (StoreCatalogGoldScan.DIRECT_GOLD.contains(item.get())) {
                errors.add(label + " names " + itemId + "; gold ingots, nuggets and blocks are"
                        + " never stocked.");
                continue;
            }
            if (gold.contains(id)) {
                errors.add(label + " names " + itemId
                        + ", which is gold-bearing through a recipe and is never stocked.");
                continue;
            }
            if (!floorByItem.containsKey(itemId)) {
                errors.add(label + " names " + itemId + " but " + StoreCatalog.FLOOR_KEY
                        + " has no entry for it.");
                continue;
            }
            final long floor = floorByItem.get(itemId);
            if (tier < floor) {
                errors.add(label + " prices " + itemId + " at " + tier
                        + " but its market floor is " + floor + "; the store price may not be"
                        + " below market.");
                continue;
            }

            final ItemStack template = new ItemStack(item.get());
            final int stackSize = template.getMaxStackSize();
            String displayName = Sanitizer.name(template.getHoverName().getString());
            if (displayName.isBlank()) {
                displayName = itemId;
            }
            final StoreCatalog.Entry entry = new StoreCatalog.Entry(itemId, tier, tier, stackSize, displayName);
            entries.add(entry);
            byId.put(itemId, entry);
            items.put(itemId, item.get());
        }

        return new StoreCatalog(enabled, entries, byId, items, errors);
    }

    private static Map<String, Long> parseFloors(
            final List<String> rawEntries, final List<String> errors) {
        final Map<String, Long> floors = new LinkedHashMap<>();
        for (int index = 0; index < rawEntries.size(); index++) {
            final String label = StoreCatalog.FLOOR_KEY + " entry " + (index + 1);
            final String raw = rawEntries.get(index);
            if (raw == null || raw.isBlank()) {
                errors.add(label + " is blank; expected itemId=price.");
                continue;
            }
            final String text = raw.strip();
            final int equals = text.indexOf('=');
            if (equals < 0 || text.indexOf('=', equals + 1) >= 0) {
                errors.add(label + " must be exactly itemId=price, got " + quoted(text) + ".");
                continue;
            }
            final String rawId = text.substring(0, equals).strip();
            final String rawPrice = text.substring(equals + 1).strip();
            final ResourceLocation id = parseId(rawId);
            if (id == null) {
                errors.add(label + " item id " + quoted(rawId)
                        + " is not a lower-case namespaced id.");
                continue;
            }
            if (BuiltInRegistries.ITEM.getOptional(id).isEmpty()) {
                errors.add(label + " names " + id + ", which is not a registered item.");
                continue;
            }
            final OptionalLong price = EconomyMath.parseAmount(rawPrice);
            if (price.isEmpty()) {
                errors.add(label + " price " + quoted(rawPrice)
                        + " is not a whole number of gold ingots.");
                continue;
            }
            final String itemId = id.toString();
            if (floors.containsKey(itemId)) {
                errors.add(label + " repeats " + itemId + "; duplicate floors are refused.");
                continue;
            }
            floors.put(itemId, price.getAsLong());
        }
        return floors;
    }

    private static ResourceLocation parseId(final String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        final String text = raw.strip();
        if (!text.equals(text.toLowerCase(Locale.ROOT))) {
            return null;
        }
        final ResourceLocation id = ResourceLocation.tryParse(text);
        if (id == null || !id.toString().equals(text)) {
            return null;
        }
        return id;
    }

    private static Integer parseTier(final String raw) {
        if (raw == null || raw.isEmpty() || raw.length() > 2) {
            return null;
        }
        for (int index = 0; index < raw.length(); index++) {
            final char c = raw.charAt(index);
            if (c < '0' || c > '9') {
                return null;
            }
        }
        final int value = Integer.parseInt(raw);
        return value < StoreCatalog.MIN_TIER || value > StoreCatalog.MAX_TIER ? null : value;
    }

    private static String quoted(final String raw) {
        final String text = raw == null ? "" : raw;
        return "\"" + Sanitizer.singleLine(text, 80) + "\"";
    }
}