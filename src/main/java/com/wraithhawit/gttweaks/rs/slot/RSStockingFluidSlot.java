package com.wraithhawit.gttweaks.rs.slot;

import com.refinedmods.refinedstorage.api.core.Action;
import com.refinedmods.refinedstorage.api.network.storage.StorageNetworkComponent;
import com.refinedmods.refinedstorage.api.resource.ResourceAmount;
import com.refinedmods.refinedstorage.common.support.resource.FluidResource;

import com.wraithhawit.gttweaks.rs.network.RSNetworkPart;
import com.wraithhawit.gttweaks.rs.network.RSPartActor;

import net.neoforged.neoforge.fluids.FluidStack;

import org.jetbrains.annotations.Nullable;

/**
 * A fluid slot that shows what the network holds without holding any of it.
 *
 * <p>The fluid twin of {@link RSStockingItemSlot}, and the same idea: {@code stock} is a mirror of
 * the network's own contents, refreshed every sync, and the fluid is taken only when a recipe
 * actually drains the slot.
 *
 * <p>{@link #drain(int, FluidAction)} is the one place the network is debited, and
 * {@link FluidAction#simulate()} is passed straight through. GregTech runs a simulated recipe pass
 * before a real one, and a stocking part that ignored it would consume the inputs of every recipe it
 * merely considered.
 */
public class RSStockingFluidSlot extends RSConfiguredFluidSlot {

    @Nullable
    private RSNetworkPart part;

    public RSStockingFluidSlot() {
        super();
    }

    public RSStockingFluidSlot(@Nullable ResourceAmount config, @Nullable ResourceAmount stock) {
        super(config, stock);
    }

    /**
     * The part this slot belongs to, which is how it reaches the network at drain time. Set after
     * construction, since the slots are built by the tank, which is built by the machine.
     */
    public void setPart(@Nullable RSNetworkPart part) {
        this.part = part;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Drains the network rather than this slot, and only as much as the network actually gives.
     * Returns empty whenever the part cannot reach a network, so an offline hatch stalls its
     * multiblock instead of appearing to supply it.
     *
     * <p>The extraction asks for the <em>configured</em> resource, never the mirrored stock's, so a
     * mirror that has gone stale between syncs cannot authorise draining something else.
     */
    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        if (maxDrain <= 0 || part == null) {
            return FluidStack.EMPTY;
        }
        ResourceAmount configured = slot().getConfig();
        if (configured == null || slot().getStock() == null || !part.isOnline()) {
            return FluidStack.EMPTY;
        }
        if (!(configured.resource() instanceof FluidResource fluid)) {
            return FluidStack.EMPTY;
        }
        StorageNetworkComponent storage = part.getNodeHolder().storage();
        if (storage == null) {
            return FluidStack.EMPTY;
        }
        long drained = storage.extract(configured.resource(), maxDrain,
                action.simulate() ? Action.SIMULATE : Action.EXECUTE, RSPartActor.INSTANCE);
        if (drained <= 0L) {
            return FluidStack.EMPTY;
        }
        if (action.execute()) {
            // Keeps the mirror honest until the next sync refreshes it anyway. Asked for an int, so
            // the network cannot have given more than one.
            slot().debitStock(drained);
        }
        return RSFluids.toStack(fluid, drained);
    }

    @Override
    public RSStockingFluidSlot copy() {
        ResourceAmount config = slot().getConfig();
        ResourceAmount stock = slot().getStock();
        RSStockingFluidSlot copied = new RSStockingFluidSlot(
                config == null ? null : RSConfiguredSlot.copy(config),
                stock == null ? null : RSConfiguredSlot.copy(stock));
        copied.setPart(part);
        return copied;
    }
}
