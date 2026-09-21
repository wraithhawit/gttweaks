package com.wraithhawit.gttweaks.rs.slot;

import com.gregtechceu.gtceu.api.transfer.fluid.CustomFluidTank;

import com.refinedmods.refinedstorage.api.resource.ResourceAmount;
import com.refinedmods.refinedstorage.common.support.resource.FluidResource;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.fluids.FluidStack;

import org.jetbrains.annotations.Nullable;

/**
 * A configured slot that a GregTech recipe can read as if it were a one-tank fluid handler.
 *
 * <p>The fluid twin of {@link RSConfiguredItemSlot}, and read-only from the machine's side for the
 * same reason: the recipe logic drains what it consumes, and nothing may be filled in. Stock
 * arrives from exactly one direction, {@link #addStock}, which is the network hand-off.
 *
 * <p>It extends {@link CustomFluidTank} because that is what GregTech's fluid recipe handling
 * expects to be handed, but <em>none of the tank's own storage is used</em>. Every accessor reads
 * the configured slot instead, and the inherited NBT is replaced as well, so there is never a second
 * copy of the contents that could drift out of step with the first — which is exactly what would
 * happen if the superclass were left to save its own empty tank over the real stock.
 */
public class RSConfiguredFluidSlot extends CustomFluidTank {

    private final Slot slot;

    public RSConfiguredFluidSlot() {
        this(null, null);
    }

    public RSConfiguredFluidSlot(@Nullable ResourceAmount config, @Nullable ResourceAmount stock) {
        super(Integer.MAX_VALUE);
        this.slot = new Slot(config, stock);
        this.slot.setOnContentsChanged(this::onContentsChanged);
    }

    /**
     * The configured slot behind this tank, for the network sync and for the command that stands in
     * for the configuration UI.
     */
    public RSConfiguredSlot slot() {
        return slot;
    }

    /**
     * Adds an amount of the configured resource, for the caller that already knows what it asked
     * for.
     */
    public void addStock(ResourceAmount stack) {
        slot.addStock(stack);
    }

    @Override
    public int getTanks() {
        return 1;
    }

    @Override
    public FluidStack getFluid() {
        return getFluidInTank(0);
    }

    @Override
    public FluidStack getFluidInTank(int tank) {
        ResourceAmount stock = slot.getStock();
        if (tank != 0 || stock == null) {
            return FluidStack.EMPTY;
        }
        if (!(stock.resource() instanceof FluidResource fluid)) {
            return FluidStack.EMPTY;
        }
        return RSFluids.toStack(fluid, stock.amount());
    }

    /**
     * {@inheritDoc}
     *
     * <p>{@link Integer#MAX_VALUE}, not a tier's tank size: the slot holds one resource in an amount
     * the player configured, and capping it at a hatch's capacity would make the amount field a lie.
     */
    @Override
    public int getTankCapacity(int tank) {
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack) {
        return false;
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        return 0;
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        FluidStack held = getFluidInTank(0);
        if (held.isEmpty() || !FluidStack.isSameFluidSameComponents(held, resource)) {
            return FluidStack.EMPTY;
        }
        return drain(resource.getAmount(), action);
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        ResourceAmount stock = slot.getStock();
        if (stock == null || maxDrain <= 0) {
            return FluidStack.EMPTY;
        }
        if (!(stock.resource() instanceof FluidResource fluid)) {
            return FluidStack.EMPTY;
        }
        int drained = (int) Math.min(stock.amount(), maxDrain);
        if (action.execute()) {
            slot.debitStock(drained);
        }
        return RSFluids.toStack(fluid, drained);
    }

    /**
     * Refused, like the item slot's {@code setStackInSlot}: the only legitimate source of stock is
     * the network sync.
     */
    @Override
    public void setFluidInTank(int tank, FluidStack stack) {
    }

    @Override
    public void setFluid(FluidStack stack) {
    }

    /**
     * Saves the configured slot, not the inherited tank.
     *
     * <p>{@link CustomFluidTank} would otherwise write its own — permanently empty — fluid, and
     * reading it back would wipe whatever the part was holding.
     */
    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        return slot.serializeNBT(provider);
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        slot.deserializeNBT(provider, tag);
    }

    public RSConfiguredFluidSlot copy() {
        ResourceAmount config = slot.getConfig();
        ResourceAmount stock = slot.getStock();
        return new RSConfiguredFluidSlot(
                config == null ? null : RSConfiguredSlot.copy(config),
                stock == null ? null : RSConfiguredSlot.copy(stock));
    }

    /**
     * The configured-slot half, kept as an inner class rather than a superclass.
     *
     * <p>{@link CustomFluidTank} is a class, so this slot cannot extend both it and
     * {@link RSConfiguredSlot}. The item side did not have to choose — {@code IItemHandlerModifiable}
     * is an interface — which is the only reason the two slots are not built the same way.
     */
    private static final class Slot extends RSConfiguredSlot {

        private Slot(@Nullable ResourceAmount config, @Nullable ResourceAmount stock) {
            super(config, stock);
        }

        /**
         * {@inheritDoc}
         *
         * <p>Fluids only. Nothing in this class can hand an item to a recipe, so a configuration
         * asking for one would sit there pulling a resource out of the network that the multiblock
         * could never use.
         */
        @Override
        public void setConfig(@Nullable ResourceAmount config) {
            if (config != null && !(config.resource() instanceof FluidResource)) {
                return;
            }
            super.setConfig(config);
        }

        @Override
        protected void addStack(ResourceAmount stack) {
            if (stock == null) {
                stock = stack;
            } else if (stock.resource().equals(stack.resource())) {
                stock = copy(stock, stock.amount() + stack.amount());
            } else {
                // Cannot happen while requestStack() refuses to ask for a second resource, and is
                // not worth a silent merge if it ever does: dropping the old stock would delete
                // fluid the network still believes it handed over.
                return;
            }
            onContentsChanged.run();
        }

        void addStock(ResourceAmount stack) {
            addStack(stack);
        }
    }
}
