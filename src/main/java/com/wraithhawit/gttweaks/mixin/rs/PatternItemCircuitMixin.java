package com.wraithhawit.gttweaks.mixin.rs;

import com.refinedmods.refinedstorage.api.autocrafting.Pattern;
import com.refinedmods.refinedstorage.common.autocrafting.PatternItem;

import com.wraithhawit.gttweaks.rs.circuit.PatternCircuit;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * RS's pattern objects carry only an id, so note each resolved pattern's circuit by id. Pattern
 * providers resolve their pattern items through here (Refined Fluid Substitution's pattern item
 * delegates to it too).
 */
@Mixin(value = PatternItem.class, remap = false, priority = 900)
public abstract class PatternItemCircuitMixin {

    @Inject(method = "getPattern", at = @At("RETURN"))
    private void gttweaks$rememberCircuit(ItemStack stack, Level level, CallbackInfoReturnable<Optional<Pattern>> cir) {
        cir.getReturnValue().ifPresent(pattern -> PatternCircuit.remember(pattern, stack));
    }
}
