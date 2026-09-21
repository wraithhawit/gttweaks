package com.wraithhawit.gttweaks.rs.slot;

import com.refinedmods.refinedstorage.common.support.resource.FluidResource;

import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Converts between NeoForge's {@link FluidStack} and Refined Storage's {@link FluidResource}.
 *
 * <p>This exists only because Refined Storage does not provide it. {@code ItemResource} carries
 * {@code ofItemStack} and {@code toItemStack} on the class itself, and the fluid side has neither,
 * so every fluid part would otherwise open with the same two lines of unpacking. Getting the
 * component patch wrong in one of those copies would be a quiet bug — a fluid that round-trips
 * through the network and comes back having lost whatever a mod stored on it.
 *
 * <p>The amount is deliberately not carried across. A {@code FluidStack} counts in an {@code int}
 * and a network resource counts in a {@code long}, and every caller here already has the amount it
 * means to move, in the units it means to move it in.
 */
public final class RSFluids {

    private RSFluids() {
    }

    /**
     * The resource a fluid stack names, ignoring how much of it there is.
     */
    public static FluidResource toResource(FluidStack stack) {
        return new FluidResource(stack.getFluid(), stack.getComponentsPatch());
    }

    /**
     * A fluid stack of {@code amount} of this resource.
     *
     * <p>The amount is clamped to an {@code int}, since that is all a {@code FluidStack} can hold.
     * Callers moving fluid must account for what actually moved rather than what they asked for —
     * the same rule as {@link RSConfiguredSlot#debitStock(long)}, and for the same reason.
     */
    public static FluidStack toStack(FluidResource resource, long amount) {
        int clamped = (int) Math.min(amount, Integer.MAX_VALUE);
        return new FluidStack(
                BuiltInRegistries.FLUID.wrapAsHolder(resource.fluid()), clamped, resource.components());
    }
}
