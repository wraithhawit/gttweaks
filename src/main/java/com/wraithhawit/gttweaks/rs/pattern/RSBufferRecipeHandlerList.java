package com.wraithhawit.gttweaks.rs.pattern;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.IRecipeHandler;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeHandlerGroupDistinctness;
import com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeHandlerList;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;


/**
 * Where the buffer's slots become <em>separate</em> sets of inputs rather than one pool.
 *
 * <p>Without this, a recipe wanting two iron and a bucket of water would happily take the iron from
 * the slot one pattern filled and the water from the slot another filled, run once, and leave both
 * patterns half-consumed and unable to complete. That is the failure the ME pattern buffer exists to
 * prevent, and the rule that prevents it is simple: **a recipe runs from one slot or not at all.**
 *
 * <p>{@link #handleRecipe} is that rule. It walks the slots and, for each, asks whether that slot
 * alone could satisfy the entire remaining recipe — by running the whole consume pass in simulation
 * and checking that nothing is left over. Only a slot that passes is allowed to run, and then the
 * same pass is repeated for real. A slot that could satisfy most of a recipe is no use and is
 * skipped, which is the opposite of how a pooled handler behaves.
 *
 * <p>Note that this overrides the list-level {@code handleRecipe} rather than relying on the
 * handlers inside it. The per-slot handlers do the consuming when asked, but nothing in the ordinary
 * handler chain knows that two handlers belong to the same slot and must succeed or fail together,
 * so that decision is taken here.
 */
public class RSBufferRecipeHandlerList extends RecipeHandlerList {

    private final Supplier<List<RSBufferSlot>> slots;
    private final Supplier<List<RSBufferSlotItemHandler>> itemHandlers;
    private final Supplier<List<RSBufferSlotFluidHandler>> fluidHandlers;

    public RSBufferRecipeHandlerList(Supplier<List<RSBufferSlot>> slots,
            Supplier<List<RSBufferSlotItemHandler>> itemHandlers,
            Supplier<List<RSBufferSlotFluidHandler>> fluidHandlers,
            RSBufferAggregateItemHandler aggregateItems,
            RSBufferAggregateFluidHandler aggregateFluids) {
        super(IO.IN);
        this.slots = slots;
        this.itemHandlers = itemHandlers;
        this.fluidHandlers = fluidHandlers;
        addHandlers(aggregateItems, aggregateFluids);
        setGroup(RecipeHandlerGroupDistinctness.BUS_DISTINCT);
    }

    @Override
    public Map<RecipeCapability<?>, List<Object>> handleRecipe(IO io, GTRecipe recipe,
            Map<RecipeCapability<?>, List<Object>> contents, boolean simulate) {
        if (io != IO.IN || contents.isEmpty()) {
            return contents;
        }
        List<RSBufferSlot> current = slots.get();
        for (int index = 0; index < current.size(); index++) {
            RSBufferSlot slot = current.get(index);
            if (slot.isEmpty() || !couldMatch(slot, contents)) {
                continue;
            }
            Map<RecipeCapability<?>, List<Object>> left = consume(index, io, recipe, contents, true);
            if (!left.isEmpty()) {
                // This slot cannot run the whole recipe on its own, so it does not run any of it.
                continue;
            }
            return simulate ? left : consume(index, io, recipe, contents, false);
        }
        return contents;
    }

    /**
     * Runs one slot's handlers over the whole remaining recipe.
     *
     * <p>The map is copied before anything touches it, because this is run in simulation first and a
     * simulation that edited the caller's map would leave the real pass with a recipe already half
     * struck out.
     */
    private Map<RecipeCapability<?>, List<Object>> consume(int index, IO io, GTRecipe recipe,
            Map<RecipeCapability<?>, List<Object>> contents, boolean simulate) {
        Map<RecipeCapability<?>, List<Object>> copy = new HashMap<>(contents);
        Iterator<Map.Entry<RecipeCapability<?>, List<Object>>> it = copy.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<RecipeCapability<?>, List<Object>> entry = it.next();
            boolean satisfied = false;
            for (IRecipeHandler<?> handler : handlersFor(entry.getKey(), index)) {
                if (io == IO.IN && handler.getTotalContentAmount() == 0.0
                        && !handler.getCapability().skipEmptyContentCheck()) {
                    continue;
                }
                List<?> handlerLeft = handler.handleRecipe(io, recipe, cast(entry.getValue()), simulate);
                if (handlerLeft.isEmpty()) {
                    satisfied = true;
                    break;
                }
                entry.setValue(new ArrayList<>(handlerLeft));
            }
            if (satisfied) {
                it.remove();
            }
        }
        return copy;
    }

    @SuppressWarnings("unchecked")
    private static <T> List<T> cast(List<Object> contents) {
        return (List<T>) contents;
    }

    /**
     * The handlers that may answer for one capability on behalf of one slot.
     *
     * <p>Only that slot's own handler, which is what keeps the slots apart. GregTech additionally
     * offers its circuit slot and a shared inventory and tank here, so a programmed circuit or a
     * commonly-needed input does not have to be duplicated into every pattern. Neither exists on this
     * part yet, and a GregTech recipe that needs a circuit therefore cannot be run from this buffer —
     * recorded in the design doc as the first thing to add.
     */
    private List<IRecipeHandler<?>> handlersFor(RecipeCapability<?> capability, int index) {
        if (capability == ItemRecipeCapability.CAP) {
            List<RSBufferSlotItemHandler> handlers = itemHandlers.get();
            return index < handlers.size() ? List.of(handlers.get(index)) : List.of();
        }
        if (capability == FluidRecipeCapability.CAP) {
            List<RSBufferSlotFluidHandler> handlers = fluidHandlers.get();
            return index < handlers.size() ? List.of(handlers.get(index)) : List.of();
        }
        return List.of();
    }

    /**
     * A cheap reject before the full simulated pass.
     *
     * <p>Answers only "does this slot hold any item or fluid this recipe so much as mentions", from
     * cached type sets. A slot that fails this cannot possibly satisfy the recipe, and skipping it
     * here avoids building and walking a copy of the contents map for every slot on every recipe
     * check — which, on a multiblock ticking with a full buffer, is the difference that matters.
     */
    private static boolean couldMatch(RSBufferSlot slot, Map<RecipeCapability<?>, List<Object>> contents) {
        List<Object> items = contents.get(ItemRecipeCapability.CAP);
        if (items != null && !slot.isItemEmpty()) {
            for (Object entry : items) {
                if (!(entry instanceof SizedIngredient ingredient) || ingredient.ingredient().isEmpty()) {
                    continue;
                }
                for (ItemStack stack : ingredient.getItems()) {
                    if (slot.getItemTypes().contains(stack.getItem())) {
                        return true;
                    }
                }
            }
        }
        List<Object> fluids = contents.get(FluidRecipeCapability.CAP);
        if (fluids != null && !slot.isFluidEmpty()) {
            for (Object entry : fluids) {
                if (!(entry instanceof SizedFluidIngredient ingredient)) {
                    continue;
                }
                for (FluidStack stack : ingredient.getFluids()) {
                    if (!stack.isEmpty() && slot.getFluidTypes().contains(stack.getFluid())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public boolean isDistinct() {
        return true;
    }

    @Override
    public void setDistinct(boolean ignored, boolean notify) {
        // Not configurable. A pooled pattern buffer would let one recipe take its inputs from two
        // patterns at once, which is the bug this class exists to prevent, so the setter is refused
        // rather than honoured.
    }
}
