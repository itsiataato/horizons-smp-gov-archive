package com.horizonssmp.gov.server.economy;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;


import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * One-pass recipe scan that collects items whose recipes tie them to gold.
 *
 * <p>Package-private helper for {@link StoreCatalog}; not part of the public API.
 */
final class StoreCatalogGoldScan {

    private StoreCatalogGoldScan() {
    }

    /** The items that are gold by definition and may never be stocked. */
    static final Set<Item> DIRECT_GOLD = Set.of(
            Items.GOLD_INGOT, Items.GOLD_NUGGET, Items.GOLD_BLOCK);

    /**
     * The items whose recipes tie them to gold, computed in one pass.
     */
    static Set<ResourceLocation> goldBearingItems(final MinecraftServer server) {
        if (server == null) {
            return Set.of();
        }
        final Set<ResourceLocation> banned = new HashSet<>();
        for (final RecipeHolder<?> holder : server.getRecipeManager().getRecipes()) {
            final Recipe<?> recipe = holder.value();
            final ItemStack result;
            try {
                result = recipe.getResultItem(server.registryAccess());
            } catch (final RuntimeException brokenRecipe) {
                continue;
            }
            if (result == null || result.isEmpty()) {
                continue;
            }
            final boolean goldResult = isGold(result);
            boolean goldInput = false;
            for (final Ingredient ingredient : recipe.getIngredients()) {
                if (ingredient == null || ingredient.isEmpty()) {
                    continue;
                }
                for (final ItemStack stack : ingredient.getItems()) {
                    if (isGold(stack)) {
                        goldInput = true;
                        break;
                    }
                }
                if (goldInput) {
                    break;
                }
            }
            if (goldResult) {
                for (final Ingredient ingredient : recipe.getIngredients()) {
                    if (ingredient == null) {
                        continue;
                    }
                    for (final ItemStack stack : ingredient.getItems()) {
                        addKey(banned, stack.getItem());
                    }
                }
            }
            if (goldInput) {
                addKey(banned, result.getItem());
            }
        }
        return Collections.unmodifiableSet(banned);
    }

    private static void addKey(final Set<ResourceLocation> banned, final Item item) {
        final ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
        if (key != null) {
            banned.add(key);
        }
    }

    private static boolean isGold(final ItemStack stack) {
        return stack != null
                && !stack.isEmpty()
                && (stack.is(Items.GOLD_INGOT)
                        || stack.is(Items.GOLD_NUGGET)
                        || stack.is(Items.GOLD_BLOCK));
    }

}