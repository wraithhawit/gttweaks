package com.wraithhawit.gttweaks.mixin;

import com.gregtechceu.gtceu.api.recipe.gui.RecipeUIModifier;
import com.gregtechceu.gtceu.common.recipe.condition.ResearchCondition;
import com.gregtechceu.gtceu.common.recipe.gui.GTRecipeUIModifiers;
import com.wraithhawit.gttweaks.AssemblyLineRecipeLayout;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps a research recipe's data item from being shown twice on the restored Assembly Line page.
 *
 * <p>{@code GTRecipeViewerWidget.buildAdditionalRecipeContent} asks every recipe condition for a UI
 * modifier, and {@code ResearchCondition} answers with {@code GTRecipeUIModifiers.RESEARCH_INFO}: a
 * {@code research_info} row of data-item slots below the recipe. {@code AssemblyLineRecipeLayout}
 * already puts those items in the 1.20 research slot, so for recipes it draws the row is skipped.
 * Every other recipe page still gets GregTech's row unchanged.
 */
@Mixin(value = ResearchCondition.class, remap = false)
public class ResearchInfoRowMixin {

    @Inject(method = "modifyUI", at = @At("HEAD"), cancellable = true)
    private void gttweaks$skipRowWhenSlotShowsResearch(CallbackInfoReturnable<RecipeUIModifier> cir) {
        cir.setReturnValue((recipe, widget) -> {
            if (!AssemblyLineRecipeLayout.drawsResearchSlot(recipe)) {
                GTRecipeUIModifiers.RESEARCH_INFO.buildRecipeUI(recipe, widget);
            }
        });
    }
}
