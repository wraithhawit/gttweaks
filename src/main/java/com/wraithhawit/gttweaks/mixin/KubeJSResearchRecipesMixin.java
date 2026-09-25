package com.wraithhawit.gttweaks.mixin;

import com.wraithhawit.gttweaks.KubeResearchRecipes;

import com.google.gson.JsonElement;
import dev.latvian.mods.kubejs.recipe.RecipesKubeEvent;
import net.minecraft.resources.ResourceLocation;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.stream.Stream;

/**
 * Makes KubeJS {@code scannerResearch}/{@code stationResearch} generate their research recipes; see
 * {@link KubeResearchRecipes}.
 *
 * <p>{@code applyChanges} replaces the datapack recipe map with every original and added recipe as
 * JSON, and {@code RecipeManager.apply} parses that map straight after. Its tail is the last point
 * where both the script-built recipes and the map are in hand.
 */
@Mixin(value = RecipesKubeEvent.class, remap = false)
public class KubeJSResearchRecipesMixin {

    @Inject(method = "applyChanges", at = @At("TAIL"))
    private void gttweaks$emitResearchRecipes(Map<ResourceLocation, JsonElement> map, CallbackInfo ci) {
        RecipesKubeEvent event = (RecipesKubeEvent) (Object) this;
        KubeResearchRecipes.emit(
                Stream.concat(event.originalRecipes.values().stream(), event.addedRecipes.stream()),
                event.registries.json(), map);
    }
}
