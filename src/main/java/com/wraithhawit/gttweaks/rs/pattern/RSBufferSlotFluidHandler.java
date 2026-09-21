package com.wraithhawit.gttweaks.rs.pattern;

import java.util.ArrayList;
import java.util.List;

import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.IRecipeHandler;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;

import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

/**
 * The fluid half of {@link RSBufferSlotItemHandler}, wrapping the same slot.
 *
 * <p>Two handlers rather than one because GregTech keys a handler by a single
 * {@link RecipeCapability}, and items and fluids are different capabilities. They are the same slot
 * underneath, which is what lets a pattern carrying both be satisfied from one place.
 */
public class RSBufferSlotFluidHandler implements IRecipeHandler<SizedFluidIngredient> {

    private final RSBufferSlot slot;

    public RSBufferSlotFluidHandler(RSBufferSlot slot) {
        this.slot = slot;
    }

    @Override
    public List<SizedFluidIngredient> handleRecipeInner(IO io, GTRecipe recipe,
            List<SizedFluidIngredient> left, boolean simulate) {
        if (io != IO.IN || slot.isFluidEmpty()) {
            return left;
        }
        return slot.handleFluidInternal(left, simulate);
    }

    @Override
    public List<Object> getContents() {
        return new ArrayList<>(slot.getFluids());
    }

    @Override
    public double getTotalContentAmount() {
        return slot.getTotalFluidAmount();
    }

    @Override
    public RecipeCapability<SizedFluidIngredient> getCapability() {
        return FluidRecipeCapability.CAP;
    }

    @Override
    public boolean isDistinct() {
        return true;
    }

    @Override
    public int getPriority() {
        return RSBufferSlotItemHandler.BUFFER_PRIORITY;
    }
}
