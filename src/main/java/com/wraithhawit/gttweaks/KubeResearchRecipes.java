package com.wraithhawit.gttweaks;

import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.data.recipe.builder.GTRecipeBuilder.ResearchRecipeEntry;
import com.gregtechceu.gtceu.integration.kjs.recipe.GTRecipeSchema.GTKubeRecipe;
import com.gregtechceu.gtceu.utils.ResearchManager;

import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.DynamicOps;
import dev.latvian.mods.kubejs.recipe.KubeRecipe;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.neoforged.neoforge.common.conditions.ICondition;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Emits the scanner and research station recipes that KubeJS {@code scannerResearch} /
 * {@code stationResearch} calls ask for.
 *
 * <p>{@code GTKubeRecipe.scannerResearch} puts a {@code ResearchCondition} on the assembly line recipe
 * and collects a {@code ResearchRecipeEntry} into {@code researchRecipeEntries()}, but nothing in the
 * 8.0 snapshot reads that list back: GregTech's 1.20.1 port did it from KubeJS's
 * {@code injectRuntimeRecipes} hook, which KubeJS 2101 no longer has. So every such recipe asks for a
 * data stick or module nothing can make — thirteen of ATM10's own scripts as well as ours.
 *
 * <p>This does the missing step with GregTech's own {@code ResearchManager.createDefaultResearchRecipe},
 * the same call its datagen makes, so the data item component and the recipe ids match GregTech's.
 * The recipe id is derived from the research id alone, so an id already in the map is research
 * GregTech or another script already provides, and is left as it is.
 */
public final class KubeResearchRecipes {

    private static final Logger LOGGER = LogUtils.getLogger();

    private KubeResearchRecipes() {}

    /**
     * Adds a research recipe for every research entry on a surviving GregTech KubeJS recipe.
     *
     * @param recipes every recipe the KubeJS event knows, removed ones included
     * @param ops     registry-aware JSON ops, as KubeJS serializes with
     * @param map     the finished recipe JSON map the recipe manager is about to parse
     */
    public static void emit(Stream<KubeRecipe> recipes, DynamicOps<JsonElement> ops,
                            Map<ResourceLocation, JsonElement> map) {
        Map<ResourceLocation, Recipe<?>> generated = new LinkedHashMap<>();
        int[] entries = {0};
        recipes.forEach(kubeRecipe -> {
            if (!(kubeRecipe instanceof GTKubeRecipe gtRecipe) || gtRecipe.removed
                    || gtRecipe.researchRecipeEntries().isEmpty()) {
                return;
            }
            // The vanilla registry, as GregTech's own recipe codec reads it: GTRegistries.RECIPE_TYPES
            // returned null for gtceu:assembly_line here.
            if (!(BuiltInRegistries.RECIPE_TYPE.get(gtRecipe.type.id) instanceof GTRecipeType type)) {
                LOGGER.warn("[gttweaks] {} asks for research but {} is not a GregTech recipe type",
                        gtRecipe.getOrCreateId(), gtRecipe.type.id);
                return;
            }
            for (ResearchRecipeEntry entry : gtRecipe.researchRecipeEntries()) {
                entries[0]++;
                try {
                    ResearchManager.createDefaultResearchRecipe(type, entry.researchId(),
                            entry.researchItem(), entry.researchFluid(),
                            // Copied: this call writes the research component onto the stack.
                            entry.dataStack().copy(), entry.duration(), entry.EUt(), entry.CWUt(),
                            collectInto(generated));
                } catch (RuntimeException e) {
                    LOGGER.error("[gttweaks] Could not build research '{}' for {}",
                            entry.researchId(), gtRecipe.getOrCreateId(), e);
                }
            }
        });

        int added = 0;
        int present = 0;
        for (Map.Entry<ResourceLocation, Recipe<?>> recipe : generated.entrySet()) {
            if (map.containsKey(recipe.getKey())) {
                present++;
                continue;
            }
            var json = Recipe.CODEC.encodeStart(ops, recipe.getValue());
            if (json.isSuccess()) {
                map.put(recipe.getKey(), json.getOrThrow());
                LOGGER.debug("[gttweaks] Research recipe {}: {}", recipe.getKey(), json.getOrThrow());
                added++;
            } else {
                LOGGER.error("[gttweaks] Could not encode research recipe {}: {}", recipe.getKey(),
                        json.error().map(Object::toString).orElse("?"));
            }
        }
        if (entries[0] > 0) {
            LOGGER.info("[gttweaks] KubeJS research: {} entries, {} recipes added, {} already present",
                    entries[0], added, present);
        }
    }

    private static RecipeOutput collectInto(Map<ResourceLocation, Recipe<?>> generated) {
        return new RecipeOutput() {
            @Override
            public Advancement.Builder advancement() {
                return Advancement.Builder.recipeAdvancement();
            }

            @Override
            public void accept(ResourceLocation id, Recipe<?> recipe, @Nullable AdvancementHolder advancement,
                               ICondition... conditions) {
                generated.putIfAbsent(id, recipe);
            }
        };
    }
}
