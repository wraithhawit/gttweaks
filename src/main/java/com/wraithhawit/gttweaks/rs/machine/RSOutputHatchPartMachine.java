package com.wraithhawit.gttweaks.rs.machine;

import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableFluidTank;
import com.gregtechceu.gtceu.api.transfer.fluid.CustomFluidTank;

import com.refinedmods.refinedstorage.api.core.Action;
import com.refinedmods.refinedstorage.api.network.storage.StorageNetworkComponent;

import com.wraithhawit.gttweaks.rs.network.RSPartActor;
import com.wraithhawit.gttweaks.rs.slot.RSFluids;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * Empties a multiblock's fluid results into a Refined Storage network.
 *
 * <p>The fluid twin of {@link RSOutputBusPartMachine}: nothing to configure, the multiblock fills
 * the tanks, and every sync this hands what is in them to the network. When the network will not
 * take it the fluid stays put and the multiblock stalls, which is the same no-voiding rule the rest
 * of these parts follow.
 */
public class RSOutputHatchPartMachine extends RSHatchPartMachine {

    /**
     * Matches GregTech's four-tank output hatches, so a multiblock producing several fluids is not
     * worse off for using this one.
     */
    public static final int TANK_COUNT = 4;

    public RSOutputHatchPartMachine(BlockEntityCreationInfo info) {
        super(info, IO.OUT, new NotifiableFluidTank(
                TANK_COUNT, getTankCapacity(INITIAL_TANK_CAPACITY_4X, HATCH_TIER), IO.OUT, IO.NONE));
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
     * Drains each tank into the network, taking out only what the network accepted.
     *
     * <p>The drain is done in two steps on purpose. Asking the tank to drain and then offering the
     * result to the network would take the fluid out first and have nowhere to put it back if the
     * insert came up short; simulating first means the only non-simulated drain is for an amount
     * the network has already committed to.
     */
    private void flushTo(StorageNetworkComponent storage) {
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
