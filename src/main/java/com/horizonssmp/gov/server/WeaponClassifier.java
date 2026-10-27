package com.horizonssmp.gov.server;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.horizonssmp.gov.Gov;
import com.horizonssmp.gov.common.enums.PermitClass;
import com.horizonssmp.gov.common.enums.WeaponClass;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * The data-driven weapon classifier.
 *
 * <p>There is no compile-time dependency on TACZ or any gun mod. The answer comes from, in order:
 *
 * <ol>
 *   <li>the bundled mapping {@code data/gov/weapons/classes.json}, keyed by exact item registry
 *       id;</li>
 *   <li>the same file's item-tag mapping, for a mod or data pack that marks a family of items;</li>
 *   <li>soft TACZ detection by registry namespace and item class name, with the category table in
 *       the same JSON ({@code pistol}/{@code handgun} to L, {@code shotgun} to S,
 *       {@code rifle}/{@code smg} to R, {@code sniper}/{@code marksman} to P, and
 *       {@code machine gun}/{@code rpg}/{@code launcher}/{@code grenade}/{@code explosive} to H).
 *       The table also names the law's example families, so a TACZ item id such as
 *       {@code tacz:m4a1} classifies without any TACZ class on the compile path;</li>
 *   <li>conservative token heuristics for uranium/nuclear material and explosives. Tokens are
 *       matched on whole path components, never as substrings, so {@code granite} is not a
 *       grenade and {@code firework_rocket} is not a missile;</li>
 *   <li>anything else is {@link WeaponClass#NONE} unless the JSON marks it weapon-like, in which
 *       case law 5.13 makes it Class H until the council classifies it.</li>
 * </ol>
 *
 * <p>The JSON is a bundled resource, not server config.
 */
public final class WeaponClassifier {

    /** The bundled resource path. */
    public static final String RESOURCE = "/data/gov/weapons/classes.json";

    private static volatile WeaponClassifier instance;

    private final int version;
    private final Map<String, WeaponClass> itemClasses;
    private final Map<String, WeaponClass> tagClasses;
    private final Map<String, WeaponClass> taczCategories;
    private final Map<String, WeaponClass> taczTokens;
    private final List<String> taczNamespaces;
    private final List<String> tntItems;
    private final List<String> ignitionItems;
    private final List<String> nuclearTokens;
    private final List<String> explosiveTokens;

    /**
     * One classification answer.
     */
    public record Classification(
            String itemId, WeaponClass weaponClass, String source, boolean weaponLike) {

        public Classification {
            itemId = itemId == null ? "" : itemId;
            weaponClass = weaponClass == null ? WeaponClass.NONE : weaponClass;
            source = source == null || source.isBlank() ? "none" : source;
        }

        /** The class the enforcement path applies: unclassified weapon-like items are Class H. */
        public WeaponClass enforcedClass() {
            return weaponClass == WeaponClass.UNCLASSIFIED ? WeaponClass.HEAVY : weaponClass;
        }

        /** The permit a carrier needs, or {@code null} for an ordinary item. */
        public PermitClass permitClass() {
            return enforcedClass().permitClass();
        }

        /** True when the item needs any permit, military authority or area permit at all. */
        public boolean regulated() {
            return weaponClass != WeaponClass.NONE;
        }

        /** True when the item is treated as Class H until classified. */
        public boolean unclassifiedWeapon() {
            return weaponClass == WeaponClass.UNCLASSIFIED;
        }

        /** A one-line explanation for chat or a detail block. */
        public String explanation() {
            return switch (weaponClass) {
                case NONE -> "Not a regulated weapon.";
                case UNCLASSIFIED -> "Unlisted weapon-like item: Class H until classified (Article 5.13).";
                default -> weaponClass.label() + " (" + weaponClass.id() + "), source " + source + ".";
            };
        }
    }

    private WeaponClassifier(final JsonObject root) {
        this.version = WeaponClassifierJson.intValue(root, "version", 1);
        this.itemClasses = WeaponClassifierJson.readClassMap(root, "itemClasses");
        this.tagClasses = WeaponClassifierJson.readClassMap(root, "tagClasses");
        this.taczCategories = WeaponClassifierJson.readClassMap(root, "taczCategories");
        this.taczTokens = WeaponClassifierJson.readClassMap(root, "taczTokenClasses");
        this.taczNamespaces = WeaponClassifierJson.readStringList(root, "taczNamespaces");
        this.tntItems = WeaponClassifierJson.readStringList(root, "tntItems");
        this.ignitionItems = WeaponClassifierJson.readStringList(root, "ignitionItems");
        this.nuclearTokens = WeaponClassifierJson.readStringList(root, "nuclearTokens");
        this.explosiveTokens = WeaponClassifierJson.readStringList(root, "explosiveTokens");
    }

    /** The loaded classifier. Throws when the bundled resource is missing or malformed. */
    public static WeaponClassifier get() {
        WeaponClassifier local = instance;
        if (local == null) {
            synchronized (WeaponClassifier.class) {
                local = instance;
                if (local == null) {
                    local = loadBundled();
                    instance = local;
                }
            }
        }
        return local;
    }

    /** For a deliberate reload in a test; the shipped server loads once. */
    public static synchronized WeaponClassifier reload() {
        instance = loadBundled();
        return instance;
    }

    private static WeaponClassifier loadBundled() {
        try (InputStream stream = WeaponClassifier.class.getResourceAsStream(RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("missing bundled weapon classifier " + RESOURCE);
            }
            final String text = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            return fromJson(text);
        } catch (final IOException e) {
            throw new IllegalStateException("could not read " + RESOURCE, e);
        }
    }

    /** Parses a classifier from JSON text. Used by the bundled loader and the startup check. */
    public static WeaponClassifier fromJson(final String text) {
        try {
            final JsonElement parsed = JsonParser.parseString(text == null ? "" : text);
            if (!parsed.isJsonObject()) {
                throw new IllegalArgumentException("classifier JSON is not an object");
            }
            return new WeaponClassifier(parsed.getAsJsonObject());
        } catch (final RuntimeException e) {
            throw new IllegalStateException("malformed weapon classifier JSON: " + e.getMessage(), e);
        }
    }

    public int version() {
        return version;
    }

    /** The exact item-id mapping, read-only. */
    public Map<String, WeaponClass> itemClasses() {
        return itemClasses;
    }

    /** The item-tag mapping, read-only. */
    public Map<String, WeaponClass> tagClasses() {
        return tagClasses;
    }

    /** The TACZ category mapping, read-only. */
    public Map<String, WeaponClass> taczCategories() {
        return taczCategories;
    }

    /** The TACZ example-family mapping, read-only. */
    public Map<String, WeaponClass> taczTokens() {
        return taczTokens;
    }

    /** The configured TACZ namespaces. */
    public List<String> taczNamespaces() {
        return taczNamespaces;
    }

    /** The item ids the Blasting exception treats as TNT. */
    public List<String> tntItems() {
        return tntItems;
    }

    /** The item ids that can ignite TNT. */
    public List<String> ignitionItems() {
        return ignitionItems;
    }

    /** True when the id is one of the configured TNT items. */
    public boolean isTnt(final String rawId) {
        final String itemId = WeaponClassifierHeuristics.normalizeId(rawId);
        for (final String candidate : tntItems) {
            if (candidate.equals(itemId)) {
                return true;
            }
        }
        return false;
    }

    /** True when the id is one of the configured ignition items. */
    public boolean isIgnitionItem(final String rawId) {
        final String itemId = WeaponClassifierHeuristics.normalizeId(rawId);
        for (final String candidate : ignitionItems) {
            if (candidate.equals(itemId)) {
                return true;
            }
        }
        return false;
    }

    /** The classification of the first configured TNT item, for the enforcement path. */
    public Classification tntClassification() {
        if (!tntItems.isEmpty()) {
            return classifyId(tntItems.get(0));
        }
        return new Classification("", WeaponClass.BLASTING, "json", true);
    }

    /** The uranium/nuclear heuristic tokens. */
    public List<String> nuclearTokens() {
        return nuclearTokens;
    }

    /** The explosive heuristic tokens. */
    public List<String> explosiveTokens() {
        return explosiveTokens;
    }

    // --------------------------------------------------------------- classify

    /** Classifies an item id with no tag or TACZ category context. */
    public Classification classifyId(final String itemId) {
        return classifyId(itemId, List.of(), null);
    }

    /** Classifies an item id with its tags. */
    public Classification classifyId(final String itemId, final Collection<String> tags) {
        return classifyId(itemId, tags, null);
    }

    /**
     * Classifies an item id.
     */
    public Classification classifyId(
            final String rawId, final Collection<String> tags, final String explicitTaczCategory) {
        return WeaponClassifierEngine.classifyId(this, rawId, tags, explicitTaczCategory);
    }

    /**
     * Classifies a live stack. This is the only method that touches a Minecraft item, and it is
     * still a pure read: it never changes the stack.
     */
    public Classification classify(final ItemStack stack) {
        return WeaponClassifierEngine.classify(this, stack);
    }

    /** The registry id of a live stack, or empty. */
    public static String itemId(final ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        final ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return key == null ? "" : key.toString();
    }

    /** True when the stack carries a TACZ-looking registry id or class name. */
    public static boolean looksLikeTacz(final ItemStack stack) {
        return WeaponClassifierTacz.looksLikeTacz(stack);
    }

    /** True when an item tag id maps to a known weapon class in this build's class table. */
    public boolean mapsTag(final String rawTag) {
        return tagClasses.containsKey(WeaponClassifierHeuristics.normalizeId(rawTag));
    }
}