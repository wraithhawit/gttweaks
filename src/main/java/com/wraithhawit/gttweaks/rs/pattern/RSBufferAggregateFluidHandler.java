package com.wraithhawit.gttweaks.rs.pattern;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableRecipeHandlerTrait;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;

import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

/**
 * The fluid half of {@link RSBufferAggregateItemHandler}, with the same reasons for existing and the
 * same refusal to consume.
 */
public class RSBufferAggregateFluidHandler extends NotifiableRecipeHandlerTrait<SizedFluidIngredient> {

    private final Supplier<List<RSBufferSlot>> slots;

    public RSBufferAggregateFluidHandler(Supplier<List<RSBufferSlot>> slots) {
        this.slots = slots;
    }

    @Override
    public List<SizedFluidIngredient> handleRecipeInner(IO io, GTRecipe recipe,
            List<SizedFluidIngredient> left, boolean simulate) {
        return left;
    }

    @Override
    public List<Object> getContents() {
        List<Object> contents = new ArrayList<>();
        for (RSBufferSlot slot : slots.get()) {
            contents.addAll(slot.getFluids());
        }
        return contents;
    }

    @Override
    public double getTotalContentAmount() {
        double sum = 0.0;
        for (RSBufferSlot slot : slots.get()) {
            sum += slot.getTotalFluidAmount();
        }
        return sum;
    }

    @Override
    public RecipeCapability<SizedFluidIngredient> getCapability() {
        return FluidRecipeCapability.CAP;
    }

    @Override
    public IO getHandlerIO() {
        return IO.IN;
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
