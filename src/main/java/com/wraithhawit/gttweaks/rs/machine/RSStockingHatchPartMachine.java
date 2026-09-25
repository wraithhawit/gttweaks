package com.wraithhawit.gttweaks.rs.machine;

import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.machine.multiblock.part.MultiblockPartMachine;

import com.refinedmods.refinedstorage.api.core.Action;
import com.refinedmods.refinedstorage.api.network.storage.StorageNetworkComponent;
import com.refinedmods.refinedstorage.api.resource.ResourceAmount;
import com.refinedmods.refinedstorage.api.resource.ResourceKey;

import com.wraithhawit.gttweaks.rs.network.RSPartActor;
import com.wraithhawit.gttweaks.rs.slot.RSConfigurableSlotList;
import com.wraithhawit.gttweaks.rs.slot.RSConfiguredFluidList;
import com.wraithhawit.gttweaks.rs.slot.RSConfiguredFluidSlot;
import com.wraithhawit.gttweaks.rs.slot.RSStockingFluidSlot;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Makes a Refined Storage network's fluids visible to a multiblock without moving them into the
 * part.
 *
 * <p>The fluid twin of {@link RSStockingBusPartMachine}, with the same two exceptions to the input
 * hatch's behaviour, both of which would be duplication bugs if missed: the sync does not hand
 * "excess" back, because there is nothing to hand back, and {@link #flushStockToNetwork()} does
 * nothing when the part is broken.
 */
public class RSStockingHatchPartMachine extends RSInputHatchPartMachine implements RSStockingPart {

    /**
     * The least the network must hold before a slot reports anything. One until there is a UI to
     * change it; see {@link RSStockingBusPartMachine#MIN_AVAILABLE}.
     */
    public static final long MIN_AVAILABLE = 1L;

    public RSStockingHatchPartMachine(BlockEntityCreationInfo info) {
        super(info, new RSConfiguredFluidList(
                RSInputHatchPartMachine.CONFIG_SIZE, RSStockingFluidSlot::new));
        for (RSConfiguredFluidSlot slot : getFluidHandler().getInventory()) {
            ((RSStockingFluidSlot) slot).setPart(this);
        }
    }

    /**
     * Refreshes each slot's mirror from the network, taking nothing. Asks for the whole of what is
     * available rather than the configured amount, because the mirror shows what the network has.
     */
    @Override
    protected void syncNetwork() {
        StorageNetworkComponent storage = nodeHolder.storage();
        if (storage == null) {
            return;
        }
        // First slot per fluid, and first part per fluid in a pool; see
        // RSStockingBusPartMachine.syncNetwork for why.
        List<RSStockingPart> partners = RSStockingPart.poolPartners(this);
        Set<ResourceKey> seen = new HashSet<>();
        for (RSConfiguredFluidSlot slot : getFluidHandler().getInventory()) {
            ResourceAmount configured = slot.slot().getConfig();
            if (configured == null || !seen.add(configured.resource())
                    || !RSStockingPart.claimMirror(this, partners, configured.resource())) {
                slot.slot().setStock(null);
                continue;
            }
            long available = storage.extract(
                    configured.resource(),
                    RSStockingBusPartMachine.MIRROR_REQUEST, Action.SIMULATE, RSPartActor.INSTANCE);
            slot.slot().setStock(available >= MIN_AVAILABLE
                    ? new ResourceAmount(configured.resource(), available)
                    : null);
        }
    }

    /**
     * Deliberately empty. The stock is a mirror of what the network already holds, so giving it to
     * the network would create fluid out of nothing.
     */
    @Override
    protected void flushStockToNetwork() {
    }

    @Override
    public MultiblockPartMachine part() {
        return this;
    }

    @Override
    public RSConfigurableSlotList stockingSlots() {
        return getFluidHandler();
    }
}
