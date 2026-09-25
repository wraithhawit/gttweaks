package com.wraithhawit.gttweaks.mixin;

import com.gregtechceu.gtceu.integration.ae2.gui.AEConfigWidget;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stops an ME stocking bus or hatch opening the amount editor on a configured slot.
 *
 * <p>A stocking slot mirrors whatever the network holds and never reads its configured amount, and
 * GregTech's widget knows it: the scroll wheel and the stock row both check its private
 * {@code isStocking()}. The empty-hand click on a configured slot does not, so it opens an editor
 * for a number nothing uses. gttweaks' Refined Storage widget refuses that click on a stocking
 * part; this makes the ME one match. The click then does nothing, as it does on the RS parts.
 */
@Mixin(value = AEConfigWidget.class, remap = false)
public abstract class AEConfigWidgetStockingAmountMixin {

    @Shadow
    private boolean isStocking() {
        throw new AssertionError();
    }

    @Inject(method = "openAmountEditor", at = @At("HEAD"), cancellable = true)
    private void gttweaks$noAmountOnStocking(int index, CallbackInfo ci) {
        if (isStocking()) {
            ci.cancel();
        }
    }
}
