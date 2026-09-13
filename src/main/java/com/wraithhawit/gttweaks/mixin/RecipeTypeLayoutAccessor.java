package com.wraithhawit.gttweaks.mixin;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.gui.GTRecipeTypeUILayout;

import brachy.modularui.widgets.layout.Flow;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.function.Function;

/**
 * Replaces the recipe-viewer layout function on an already-built {@link GTRecipeTypeUILayout}.
 *
 * <p>{@code customUIBuilder} is private and final, and the only public way to set it is a fresh
 * {@code GTRecipeTypeUILayout.Builder}. Rebuilding would silently drop whatever GregTech or KubeJS
 * already configured on the layout (slot overlays, UI modifiers, the progress bar), so
 * {@code AssemblyLineRecipeLayout} swaps just this one field instead.
 */
@Mixin(GTRecipeTypeUILayout.class)
public interface RecipeTypeLayoutAccessor {

    @Mutable
    @Accessor("customUIBuilder")
    void gttweaks$setCustomUIBuilder(Function<GTRecipe, Flow> builder);
}
