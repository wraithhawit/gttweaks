package com.wraithhawit.gttweaks.rs.pattern;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableRecipeHandlerTrait;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;

import net.neoforged.neoforge.common.crafting.SizedIngredient;

/**
 * What the buffer says it holds, across every slot at once.
 *
 * <p>It reports and it does not consume: {@link #handleRecipeInner} hands the ingredient list back
 * untouched, exactly as GregTech's equivalent does. The consuming is
 * {@link RSBufferRecipeHandlerList}'s, because only that has the right to decide which single slot
 * runs a recipe, and a handler that also consumed would let a recipe draw half its inputs from one
 * slot and half from another — which is precisely the pooling this part exists to avoid.
 *
 * <p>So why have it at all? Because a machine advertises which capabilities it can supply through
 * its handlers. Without an item handler in the list the multiblock would not believe this part
 * offers item inputs, and the recipe logic would never ask. This is the advertisement; the list is
 * the shop.
 */
public class RSBufferAggregateItemHandler extends NotifiableRecipeHandlerTrait<SizedIngredient> {

    private final Supplier<List<RSBufferSlot>> slots;

    /**
     * What else the buffer offers every slot: its programmed circuit. Reported here because GregTech
     * finds candidate recipes from the handlers' contents, so a circuit no handler reports would
     * leave every circuit recipe unfindable, however it was consumed.
     */
    private final Supplier<List<Object>> shared;

    public RSBufferAggregateItemHandler(Supplier<List<RSBufferSlot>> slots) {
        this(slots, List::of);
    }

    public RSBufferAggregateItemHandler(Supplier<List<RSBufferSlot>> slots, Supplier<List<Object>> shared) {
        this.slots = slots;
        this.shared = shared;
    }

    @Override
    public List<SizedIngredient> handleRecipeInner(IO io, GTRecipe recipe, List<SizedIngredient> left,
            boolean simulate) {
        return left;
    }

    @Override
    public List<Object> getContents() {
        List<Object> contents = new ArrayList<>();
        for (RSBufferSlot slot : slots.get()) {
            contents.addAll(slot.getItems());
        }
        contents.addAll(shared.get());
        return contents;
    }

    @Override
    public double getTotalContentAmount() {
        double sum = 0.0;
        for (RSBufferSlot slot : slots.get()) {
            sum += slot.getTotalItemAmount();
        }
        return sum;
    }

    @Override
    public RecipeCapability<SizedIngredient> getCapability() {
        return ItemRecipeCapability.CAP;
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
