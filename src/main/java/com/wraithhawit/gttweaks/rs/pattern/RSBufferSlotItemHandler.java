package com.wraithhawit.gttweaks.rs.pattern;

import java.util.ArrayList;
import java.util.List;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.IRecipeHandler;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;

import net.neoforged.neoforge.common.crafting.SizedIngredient;

/**
 * One buffer slot's items, offered to the recipe logic as a set of inputs in their own right.
 *
 * <p>Deliberately not a {@code MachineTrait}: a machine trait is attached to the machine and is
 * therefore one of the part's inventories, which is exactly what these must not be. The buffer holds
 * several slots and the recipe logic has to be able to try each one alone — see
 * {@link RSBufferRecipeHandlerList}, which owns that trying. This class only answers for the slot it
 * wraps.
 *
 * <p>{@link #getPriority()} matches GregTech's own buffer handlers. It is high enough that a formed
 * multiblock prefers the buffer over an ordinary bus, which is what makes a pattern's ingredients
 * get used by the recipe they were pushed for.
 */
public class RSBufferSlotItemHandler implements IRecipeHandler<SizedIngredient> {

    /**
     * GregTech's value for the same handler, not a round number by accident: it sits just under
     * {@link Integer#MAX_VALUE} so that anything wanting to outrank a pattern buffer still can.
     */
    static final int BUFFER_PRIORITY = 0x3FFFFFFF;

    private final RSBufferSlot slot;

    public RSBufferSlotItemHandler(RSBufferSlot slot) {
        this.slot = slot;
    }

    @Override
    public List<SizedIngredient> handleRecipeInner(IO io, GTRecipe recipe, List<SizedIngredient> left,
            boolean simulate) {
        if (io != IO.IN || slot.isItemEmpty()) {
            return left;
        }
        return slot.handleItemInternal(left, simulate);
    }

    @Override
    public List<Object> getContents() {
        return new ArrayList<>(slot.getItems());
    }

    @Override
    public double getTotalContentAmount() {
        return slot.getTotalItemAmount();
    }

    @Override
    public RecipeCapability<SizedIngredient> getCapability() {
        return ItemRecipeCapability.CAP;
    }

    @Override
    public boolean isDistinct() {
        return true;
    }

    @Override
    public int getPriority() {
        return BUFFER_PRIORITY;
    }
}
