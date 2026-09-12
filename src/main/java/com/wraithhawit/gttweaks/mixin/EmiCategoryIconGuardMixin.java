package com.wraithhawit.gttweaks.mixin;

import com.wraithhawit.gttweaks.EmiIconReport;

import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.data.EmiRecipeCategoryProperties;
import net.minecraft.client.gui.GuiGraphics;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stops a recipe category with no icon from taking the client down.
 *
 * <p>Pressing U on an Electric Blast Furnace killed the client on 2026-09-09:
 *
 * <pre>
 *   NPE: EmiRenderable.render(...) because EmiRecipeCategoryProperties.getIcon(category) is null
 *     at dev.emi.emi.api.recipe.EmiRecipeCategory.render(EmiRecipeCategory.java:64)
 * </pre>
 *
 * <p>EMI's own method is two unguarded lines — resolve the icon, call {@code render} on it — and it
 * is reached every frame the category strip is drawn, so one bad category is a hard crash on a
 * screen you cannot avoid.
 *
 * <p><b>Where the null comes from.</b> Not EMI, and not GregTech's category construction: the
 * multiblock category passes a perfectly good {@code EmiStack.of(ELECTRIC_BLAST_FURNACE.getItem())}.
 * It is {@code CategoryIcon}, whose constructor is
 *
 * <pre>
 *   if (!GTCEu.isClientSide()) return;                  // wrappedValue stays null
 *   if (Mods.isEMILoaded())      wrappedValue = ...;
 *   else if (Mods.isJEILoaded()) wrappedValue = ...;    // neither branch → still null
 * </pre>
 *
 * and {@code GTRecipeCategory.getIcon()} builds one lazily and <b>caches it for the session</b>. So
 * whichever context first asks a category for its icon decides it permanently, and a first touch
 * from the wrong side leaves that category holding null for good. Sodium, Iris and colorwheel were
 * ruled out by running without them; the crash reproduced identically.
 *
 * <p>Cancelling the draw loses one 16x16 icon in the category strip and keeps the game. The id is
 * logged once so the real culprit can be fixed at its source — see {@link EmiIconReport}.
 */
@Mixin(EmiRecipeCategory.class)
public abstract class EmiCategoryIconGuardMixin {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void gttweaks$guardNullIcon(GuiGraphics graphics, int x, int y, float delta,
                                        CallbackInfo ci) {
        final EmiRecipeCategory self = (EmiRecipeCategory) (Object) this;
        if (EmiRecipeCategoryProperties.getIcon(self) == null) {
            EmiIconReport.nullIcon(self.getId(), "full");
            ci.cancel();
        }
    }

    @Inject(method = "renderSimplified", at = @At("HEAD"), cancellable = true)
    private void gttweaks$guardNullSimplifiedIcon(GuiGraphics graphics, int x, int y, float delta,
                                                  CallbackInfo ci) {
        final EmiRecipeCategory self = (EmiRecipeCategory) (Object) this;
        if (EmiRecipeCategoryProperties.getSimplifiedIcon(self) == null) {
            EmiIconReport.nullIcon(self.getId(), "simplified");
            ci.cancel();
        }
    }
}
