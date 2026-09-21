package com.wraithhawit.gttweaks.rs.pattern;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeHandlerGroupDistinctness;
import com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeHandlerList;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;

import org.jetbrains.annotations.Nullable;

/**
 * A second multiblock's view of a pattern buffer that lives somewhere else.
 *
 * <p>The proxy part holds nothing. When its multiblock asks whether a recipe can run, this hands the
 * whole question to the buffer's own {@link RSBufferRecipeHandlerList} and returns its answer, so a
 * recipe run through a proxy is satisfied from one buffer slot exactly as it would be if the buffer
 * were built into this structure. Delegating rather than reimplementing is deliberate: the rule about
 * a recipe coming from a single slot is subtle enough that a second copy of it would drift.
 *
 * <p>An unlinked proxy — or one whose buffer has been broken or is in an unloaded chunk — returns the
 * contents untouched, which reads to the multiblock as "this part supplies nothing". It does not
 * throw and it does not pretend, so a structure keeps working with the rest of its parts.
 *
 * <p>The handlers added to this list do no work of their own; they are here so the multiblock knows
 * the part can supply items and fluids at all. They report the buffer's contents when one is linked
 * and nothing when it is not, which is why they are built over a supplier rather than a fixed list.
 */
public class RSProxyRecipeHandlerList extends RecipeHandlerList {

    private final Supplier<RSBufferRecipeHandlerList> buffer;

    public RSProxyRecipeHandlerList(Supplier<RSBufferRecipeHandlerList> buffer,
            RSBufferAggregateItemHandler aggregateItems,
            RSBufferAggregateFluidHandler aggregateFluids) {
        super(IO.IN);
        this.buffer = buffer;
        addHandlers(aggregateItems, aggregateFluids);
        setGroup(RecipeHandlerGroupDistinctness.BUS_DISTINCT);
    }

    @Override
    public Map<RecipeCapability<?>, List<Object>> handleRecipe(IO io, GTRecipe recipe,
            Map<RecipeCapability<?>, List<Object>> contents, boolean simulate) {
        @Nullable
        RSBufferRecipeHandlerList target = buffer.get();
        if (target == null) {
            return contents;
        }
        return target.handleRecipe(io, recipe, contents, simulate);
    }

    @Override
    public boolean isDistinct() {
        return true;
    }

    @Override
    public void setDistinct(boolean ignored, boolean notify) {
        // Refused for the same reason the buffer refuses it: pooling the slots would defeat the part.
    }
}
