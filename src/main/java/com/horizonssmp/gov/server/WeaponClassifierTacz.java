package com.horizonssmp.gov.server;

import java.util.Locale;
import java.util.Map;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import com.horizonssmp.gov.common.enums.WeaponClass;

/**
 * TACZ detection helpers for {@link WeaponClassifier}. Package-private.
 */
final class WeaponClassifierTacz {

    private WeaponClassifierTacz() {
    }

    static WeaponClass taczCategory(final String itemId, final String explicit,
            final Map<String, WeaponClass> taczCategories,
            final Map<String, WeaponClass> taczTokens) {
        if (explicit != null && !explicit.isBlank()) {
            final String needle = explicit.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
            final WeaponClass direct = taczCategories.get(needle);
            if (direct != null) {
                return direct;
            }
            final WeaponClass token = taczTokens.get(needle);
            if (token != null) {
                return token;
            }
        }
        for (final Map.Entry<String, WeaponClass> entry : taczTokens.entrySet()) {
            if (WeaponClassifierHeuristics.matchesPhrase(itemId, entry.getKey())) {
                return entry.getValue();
            }
        }
        for (final Map.Entry<String, WeaponClass> entry : taczCategories.entrySet()) {
            if (WeaponClassifierHeuristics.matchesPhrase(itemId, entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    static String taczCategoryFromComponents(final ItemStack stack,
            final Map<String, WeaponClass> taczCategories,
            final Map<String, WeaponClass> taczTokens) {
        if (!looksLikeTacz(stack)) {
            return null;
        }
        final String text;
        try {
            text = String.valueOf(stack.getComponents()).toLowerCase(Locale.ROOT);
        } catch (final RuntimeException e) {
            return null;
        }
        for (final String key : taczCategories.keySet()) {
            if (text.contains(key)) {
                return key;
            }
        }
        for (final String key : taczTokens.keySet()) {
            if (text.contains(key)) {
                return key;
            }
        }
        return null;
    }

    static boolean looksLikeTacz(final ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        final Item item = stack.getItem();
        return WeaponClassifierHeuristics.isTaczId(WeaponClassifier.itemId(stack))
                || item.getClass().getName().toLowerCase(Locale.ROOT).contains("tacz");
    }
}