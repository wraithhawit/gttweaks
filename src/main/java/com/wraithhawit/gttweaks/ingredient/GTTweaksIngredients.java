package com.wraithhawit.gttweaks.ingredient;

import com.wraithhawit.gttweaks.GTTweaks;

import net.neoforged.neoforge.common.crafting.IngredientType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * The mod's ingredient types. One so far — see {@link PartialDataIngredient} for why it has to
 * exist at all.
 *
 * <p>{@code INGREDIENT_TYPES} is a synced registry, so the id below appears in recipes sent to
 * clients. Renaming it invalidates every recipe that uses it, which here means every ported
 * Apiary recipe; treat {@code gttweaks:partial_data} as fixed.
 */
public final class GTTweaksIngredients {

    public static final DeferredRegister<IngredientType<?>> REGISTRY =
            DeferredRegister.create(NeoForgeRegistries.Keys.INGREDIENT_TYPES, GTTweaks.MOD_ID);

    public static final DeferredHolder<IngredientType<?>, IngredientType<PartialDataIngredient>> PARTIAL_DATA =
            REGISTRY.register("partial_data", () -> new IngredientType<>(PartialDataIngredient.CODEC));

    private GTTweaksIngredients() {}
}
