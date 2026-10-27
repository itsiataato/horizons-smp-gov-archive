package com.horizonssmp.gov.server;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import com.horizonssmp.gov.common.enums.WeaponClass;

/**
 * Classification orchestration helpers for {@link WeaponClassifier}. Package-private.
 */
final class WeaponClassifierEngine {

    private WeaponClassifierEngine() {
    }

    static WeaponClassifier.Classification classifyId(
            final WeaponClassifier wc, final String rawId,
            final Collection<String> tags, final String explicitTaczCategory) {
        final String itemId = WeaponClassifierHeuristics.normalizeId(rawId);
        if (itemId.isEmpty()) {
            return new WeaponClassifier.Classification("", WeaponClass.NONE, "none", false);
        }

        final WeaponClass exact = wc.itemClasses().get(itemId);
        if (exact != null) {
            return new WeaponClassifier.Classification(itemId, exact, "json", exact != WeaponClass.NONE);
        }

        WeaponClass tagged = null;
        if (tags != null) {
            for (final String rawTag : tags) {
                final WeaponClass candidate = wc.tagClasses().get(WeaponClassifierHeuristics.normalizeId(rawTag));
                if (candidate != null && WeaponClassifierHeuristics.severity(candidate) > WeaponClassifierHeuristics.severity(tagged)) {
                    tagged = candidate;
                }
            }
        }
        if (tagged != null) {
            return new WeaponClassifier.Classification(itemId, tagged, "tag", tagged != WeaponClass.NONE);
        }

        final boolean tacz = WeaponClassifierHeuristics.isTacz(itemId, wc.taczNamespaces())
                || (explicitTaczCategory != null && !explicitTaczCategory.isBlank());
        if (tacz) {
            final WeaponClass category = WeaponClassifierTacz.taczCategory(
                    itemId, explicitTaczCategory, wc.taczCategories(), wc.taczTokens());
            if (category != null) {
                return new WeaponClassifier.Classification(itemId, category, "tacz", true);
            }
            // A TACZ item with no category is weapon-like but unlisted; Article 5.13 makes it H.
            return new WeaponClassifier.Classification(itemId, WeaponClass.UNCLASSIFIED, "unclassified", true);
        }

        if (WeaponClassifierHeuristics.matchesAny(itemId, wc.nuclearTokens())) {
            return new WeaponClassifier.Classification(itemId, WeaponClass.NUCLEAR, "heuristic", true);
        }
        if (WeaponClassifierHeuristics.matchesAny(itemId, wc.explosiveTokens())) {
            return new WeaponClassifier.Classification(itemId, WeaponClass.EXPLOSIVE, "heuristic", true);
        }
        return new WeaponClassifier.Classification(itemId, WeaponClass.NONE, "none", false);
    }

    static WeaponClassifier.Classification classify(final WeaponClassifier wc, final ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return new WeaponClassifier.Classification("", WeaponClass.NONE, "none", false);
        }
        final ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        final String itemId = key == null ? "" : key.toString();
        final Set<String> tags = new LinkedHashSet<>();
        stack.getTags().forEach(tag -> tags.add(tag.location().toString()));
        final String explicit = WeaponClassifierTacz.taczCategoryFromComponents(
                stack, wc.taczCategories(), wc.taczTokens());
        return classifyId(wc, itemId, tags, explicit);
    }
}