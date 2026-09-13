package com.wraithhawit.gttweaks.mixin;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.gui.GTRecipeTypeUILayout;
import com.gregtechceu.gtceu.api.recipe.gui.GTRecipeViewerWidget;
import com.wraithhawit.gttweaks.AssemblyLineRecipeLayout;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Puts the EU "Total / Usage" lines back on the restored Assembly Line page.
 *
 * <p>The {@code GTRecipeViewerWidget} constructor builds the recipe area with either
 * {@code buildDefaultLayout} or the layout's custom builder, then calls {@code loadContentIntoSlots},
 * which fills every widget named after a capability. The EU text block is one of those widgets, but
 * only {@code buildDefaultLayout} creates it, so on a custom page GregTech has nowhere to put the
 * numbers. Creating it just before the fill, and only for pages {@code AssemblyLineRecipeLayout}
 * draws, gives the same text in the same position as on a default page.
 */
@Mixin(value = GTRecipeViewerWidget.class, remap = false)
public abstract class RecipeViewerTextCapabilitiesMixin {

    @Shadow
    @Final
    private GTRecipeTypeUILayout uiLayout;

    @Inject(method = "<init>", at = @At(value = "INVOKE",
            target = "Lcom/gregtechceu/gtceu/api/recipe/gui/GTRecipeViewerWidget;loadContentIntoSlots()V"))
    private void gttweaks$addTextCapabilities(GTRecipe recipe, CallbackInfo ci) {
        if (AssemblyLineRecipeLayout.drawsLayout(uiLayout)) {
            AssemblyLineRecipeLayout.addTextCapabilities(uiLayout, (GTRecipeViewerWidget) (Object) this);
        }
    }
}
