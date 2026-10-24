package com.wraithhawit.gttweaks.mixin.rs;

import com.refinedmods.refinedstorage.api.resource.ResourceAmount;
import com.refinedmods.refinedstorage.common.autocrafting.ProcessingPatternState;
import com.refinedmods.refinedstorage.common.autocrafting.patterngrid.PatternGridBlockEntity;
import com.refinedmods.refinedstorage.common.content.DataComponents;
import com.refinedmods.refinedstorage.common.support.resource.ItemResource;

import com.wraithhawit.gttweaks.rs.circuit.PatternCircuit;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Every RS pattern grid (plain or advanced): a GregTech programmed circuit in a processing
 * pattern's inputs is stored on the pattern instead of being encoded as an input. Copying such a
 * pattern back into the grid puts the circuit back into the inputs, so re-encoding keeps it.
 */
// Priority 900: Cable Tiers injects at the same RETURN and calls setReturnValue, which cancels; a
// callback placed after it never runs. Applying first places ours first.
@Mixin(value = PatternGridBlockEntity.class, remap = false, priority = 900)
public abstract class PatternGridCircuitMixin {

    @Inject(method = "createProcessingPattern", at = @At("RETURN"))
    private void gttweaks$storeCircuit(CallbackInfoReturnable<ItemStack> cir) {
        ItemStack pattern = cir.getReturnValue();
        if (pattern == null) {
            return;
        }
        ProcessingPatternState state = pattern.get(DataComponents.INSTANCE.getProcessingPatternState());
        if (state == null) {
            return;
        }
        Integer circuit = null;
        List<Optional<ProcessingPatternState.ProcessingIngredient>> inputs = new ArrayList<>(state.ingredients().size());
        for (Optional<ProcessingPatternState.ProcessingIngredient> ingredient : state.ingredients()) {
            if (ingredient.isPresent()
                    && ingredient.get().input().resource() instanceof ItemResource item
                    && PatternCircuit.isCircuit(item)) {
                if (circuit == null) {
                    circuit = PatternCircuit.configuration(item);
                }
                inputs.add(Optional.empty());
            } else {
                inputs.add(ingredient);
            }
        }
        if (circuit == null) {
            return;
        }
        pattern.set(DataComponents.INSTANCE.getProcessingPatternState(),
                new ProcessingPatternState(inputs, state.outputs()));
        pattern.set(PatternCircuit.CIRCUIT.get(), circuit);
    }

    @WrapOperation(
            method = "copyPattern",
            at = @At(value = "INVOKE",
                    target = "Lcom/refinedmods/refinedstorage/common/autocrafting/patterngrid/PatternGridBlockEntity;"
                            + "copyProcessingPattern(Lcom/refinedmods/refinedstorage/common/autocrafting/ProcessingPatternState;)V"))
    private void gttweaks$restoreCircuit(PatternGridBlockEntity grid, ProcessingPatternState state,
                                         Operation<Void> original, @Local(argsOnly = true) ItemStack stack) {
        Integer circuit = PatternCircuit.get(stack);
        original.call(grid, circuit == null ? state : gttweaks$withCircuit(state, circuit));
    }

    /** The state with the circuit back in the first empty input slot (or appended). */
    @Unique
    private static ProcessingPatternState gttweaks$withCircuit(ProcessingPatternState state, int circuit) {
        ProcessingPatternState.ProcessingIngredient ingredient = new ProcessingPatternState.ProcessingIngredient(
                new ResourceAmount(ItemResource.ofItemStack(PatternCircuit.circuitStack(circuit)), 1), List.of());
        List<Optional<ProcessingPatternState.ProcessingIngredient>> inputs = new ArrayList<>(state.ingredients());
        int free = inputs.indexOf(Optional.<ProcessingPatternState.ProcessingIngredient>empty());
        if (free >= 0) {
            inputs.set(free, Optional.of(ingredient));
        } else {
            inputs.add(Optional.of(ingredient));
        }
        return new ProcessingPatternState(inputs, state.outputs());
    }
}
