package com.wraithhawit.gttweaks.rs.machine;

import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.recipe.IO;

import com.refinedmods.refinedstorage.api.core.Action;
import com.refinedmods.refinedstorage.api.network.storage.StorageNetworkComponent;
import com.refinedmods.refinedstorage.api.resource.ResourceAmount;

import com.wraithhawit.gttweaks.rs.network.RSPartActor;
import com.wraithhawit.gttweaks.rs.slot.RSConfiguredFluidList;
import com.wraithhawit.gttweaks.rs.slot.RSConfiguredFluidSlot;
import com.wraithhawit.gttweaks.rs.slot.RSConfiguredSlot;

/**
 * Keeps a multiblock supplied with fluids from a Refined Storage network.
 *
 * <p>The fluid twin of {@link RSInputBusPartMachine}, slot for slot: each of the eight slots holds a
 * configured resource and an amount, every sync tops it back up and hands back anything it should no
 * longer be holding, and the multiblock drains it as it would an ordinary input hatch.
 *
 * <p>The same departure from GregTech's ME parts applies. When the network refuses the excess a slot
 * is handing back, the fluid stays and the slot retries on the next sync rather than being deleted
 * to free the slot.
 */
public class RSInputHatchPartMachine extends RSHatchPartMachine {

    /**
     * Eight, where the item bus has sixteen. GregTech's ME input hatch is the same shape, and a
     * multiblock asking for more than eight distinct fluids at once does not exist in this pack.
     */
    public static final int CONFIG_SIZE = 8;

    protected final RSConfiguredFluidList fluidHandler;

    public RSInputHatchPartMachine(BlockEntityCreationInfo info) {
        this(info, new RSConfiguredFluidList(CONFIG_SIZE));
    }

    protected RSInputHatchPartMachine(BlockEntityCreationInfo info, RSConfiguredFluidList tank) {
        super(info, IO.IN, tank);
        this.fluidHandler = tank;
    }

    public RSConfiguredFluidList getFluidHandler() {
        return fluidHandler;
    }

    @Override
    protected void syncNetwork() {
        StorageNetworkComponent storage = nodeHolder.storage();
        if (storage == null) {
            return;
        }
        for (RSConfiguredFluidSlot slot : fluidHandler.getInventory()) {
            returnExcess(storage, slot);
            requestConfigured(storage, slot);
        }
    }

    private void returnExcess(StorageNetworkComponent storage, RSConfiguredFluidSlot slot) {
        RSConfiguredSlot configured = slot.slot();
        ResourceAmount excess = configured.exceedStack();
        if (excess == null) {
            return;
        }
        long inserted = storage.insert(excess.resource(), excess.amount(), Action.EXECUTE, RSPartActor.INSTANCE);
        // Exactly what the network took, in the resource's own units. See RSConfiguredSlot.debitStock
        // for why this must not go through the tank.
        configured.debitStock(inserted);
    }

    private void requestConfigured(StorageNetworkComponent storage, RSConfiguredFluidSlot slot) {
        ResourceAmount wanted = slot.slot().requestStack();
        if (wanted == null) {
            return;
        }
        long extracted = storage.extract(wanted.resource(), wanted.amount(), Action.EXECUTE, RSPartActor.INSTANCE);
        if (extracted > 0L) {
            slot.addStock(new ResourceAmount(wanted.resource(), extracted));
        }
    }

    /**
     * Hands the held stock back to the network when the part is broken.
     *
     * <p>Best effort, and only what the network will take: whatever is left when storage is full or
     * the part was not connected stays in the slots and is lost with the block, the same as the
     * input bus and as GregTech's ME parts.
     */
    @Override
    public void onMachineDestroyed() {
        super.onMachineDestroyed();
        flushStockToNetwork();
    }

    /**
     * Gives the network back everything the slots are holding.
     *
     * <p>Split out from {@link #onMachineDestroyed()} because the stocking hatch must <em>not</em>
     * do it: its stock is a mirror of what the network already has rather than fluid this part is
     * holding, so handing it over would create fluid.
     */
    protected void flushStockToNetwork() {
        StorageNetworkComponent storage = nodeHolder.storage();
        if (storage == null) {
            return;
        }
        for (RSConfiguredFluidSlot slot : fluidHandler.getInventory()) {
            RSConfiguredSlot configured = slot.slot();
            ResourceAmount stock = configured.getStock();
            if (stock == null) {
                continue;
            }
            long inserted = storage.insert(stock.resource(), stock.amount(), Action.EXECUTE, RSPartActor.INSTANCE);
            configured.debitStock(inserted);
        }
    }
}
