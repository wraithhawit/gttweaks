package com.wraithhawit.gttweaks.rs.machine;

import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.transfer.fluid.CustomFluidTank;

import com.refinedmods.refinedstorage.api.core.Action;
import com.refinedmods.refinedstorage.api.network.storage.StorageNetworkComponent;

import com.wraithhawit.gttweaks.rs.network.RSPartActor;
import com.wraithhawit.gttweaks.rs.slot.RSFluids;
import com.wraithhawit.gttweaks.rs.slot.RSOutputFluidBuffer;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * Empties a multiblock's fluid results into a Refined Storage network.
 *
 * <p>The fluid twin of {@link RSOutputBusPartMachine}: nothing to configure, the multiblock writes
 * its outputs into the hatch, and every sync this hands what is waiting to the network.
 *
 * <p>Like the bus, and like GregTech's {@code MEOutputHatchPartMachine}, the hatch holds its outputs
 * in an unbounded {@link RSOutputFluidBuffer} rather than tanks. Four fixed tanks made any recipe
 * producing more fluid at once than a tank holds impossible to start -- "insufficient outputs" with
 * an empty hatch. The trade is the bus's: a network that cannot take the fluid no longer stops the
 * multiblock, and what it refuses waits in the hatch, saved with it, until it can.
 */
public class RSOutputHatchPartMachine extends RSHatchPartMachine {

    /**
     * The four tanks the hatch had before the buffer. Kept so a hatch saved then loads into the
     * same slots; only the first is the buffer, see {@link RSOutputFluidBuffer}.
     */
    public static final int TANK_COUNT = 4;

    public RSOutputHatchPartMachine(BlockEntityCreationInfo info) {
        super(info, IO.OUT, RSOutputFluidBuffer.createTank(
                TANK_COUNT, getTankCapacity(INITIAL_TANK_CAPACITY_4X, HATCH_TIER)));
    }

    public RSOutputFluidBuffer buffer() {
        return (RSOutputFluidBuffer) tank.getStorages()[0];
    }

    @Override
    protected void syncNetwork() {
        StorageNetworkComponent storage = nodeHolder.storage();
        if (storage == null) {
            return;
        }
        flushTo(storage);
    }

    /**
     * Hands the buffer to the network, keeping whatever the network refuses, then drains whatever an
     * old save left in the other tanks, taking out only what the network accepted.
     *
     * <p>The tank drain is done in two steps on purpose. Asking the tank to drain and then offering the
     * result to the network would take the fluid out first and have nowhere to put it back if the
     * insert came up short; simulating first means the only non-simulated drain is for an amount
     * the network has already committed to.
     */
    private void flushTo(StorageNetworkComponent storage) {
        buffer().drainTo((resource, amount) ->
                storage.insert(resource, amount, Action.EXECUTE, RSPartActor.INSTANCE));
        // The buffer reports itself empty as a tank, so this loop only ever sees the old tanks.
        for (CustomFluidTank storageTank : tank.getStorages()) {
            FluidStack held = storageTank.getFluid();
            if (held.isEmpty()) {
                continue;
            }
            long inserted = storage.insert(
                    RSFluids.toResource(held), held.getAmount(), Action.EXECUTE, RSPartActor.INSTANCE);
            if (inserted <= 0L) {
                continue;
            }
            // Offered held.getAmount(), an int, so the network cannot have taken more.
            storageTank.drain((int) inserted, IFluidHandler.FluidAction.EXECUTE);
        }
    }

    /**
     * Flushes the tanks on the way out, so breaking a hatch that still holds product does not lose
     * it. What a full or disconnected network refuses stays behind.
     */
    @Override
    public void onMachineDestroyed() {
        super.onMachineDestroyed();
        StorageNetworkComponent storage = nodeHolder.storage();
        if (storage == null) {
            return;
        }
        flushTo(storage);
    }
}
