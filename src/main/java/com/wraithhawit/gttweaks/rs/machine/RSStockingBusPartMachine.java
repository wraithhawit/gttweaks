package com.wraithhawit.gttweaks.rs.machine;

import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;

import com.refinedmods.refinedstorage.api.core.Action;
import com.refinedmods.refinedstorage.api.network.storage.StorageNetworkComponent;
import com.refinedmods.refinedstorage.api.resource.ResourceAmount;

import com.wraithhawit.gttweaks.rs.network.RSPartActor;
import com.wraithhawit.gttweaks.rs.slot.RSConfiguredItemList;
import com.wraithhawit.gttweaks.rs.slot.RSConfiguredItemSlot;
import com.wraithhawit.gttweaks.rs.slot.RSStockingItemSlot;

/**
 * Makes a Refined Storage network's items visible to a multiblock without moving them into the part.
 *
 * <p>An input bus keeps a fixed amount stocked; this keeps nothing. Each sync it asks the network
 * how much of each configured resource exists and records that as the slot's stock — a mirror — and
 * the multiblock reads it as though the items were sitting in the bus. They are taken only when a
 * recipe actually consumes them, inside {@link RSStockingItemSlot#extractItem}.
 *
 * <p>Two consequences follow from the stock being a mirror, and both would be item duplication bugs
 * if missed. The sync must not hand "excess" back when the configuration changes, because there is
 * nothing to hand back — so {@link #syncNetwork()} replaces the input bus's version rather than
 * extending it. And {@link #flushStockToNetwork()} must do nothing when the part is broken, for the
 * same reason. GregTech makes the same two exceptions, the second by emptying
 * {@code flushInventory()}.
 *
 * <p>The configured <em>amount</em> is ignored here; only the resource matters. A stocking bus is a
 * window onto the network, so the amount it shows is whatever the network has, which is what
 * GregTech does too — its mirror asks for {@link Long#MAX_VALUE}.
 */
public class RSStockingBusPartMachine extends RSInputBusPartMachine {

    /**
     * The least the network must hold before a slot reports anything at all.
     *
     * <p>GregTech makes this adjustable with a screwdriver, to stop a multiblock starting a recipe
     * on a trickle. There is no UI here yet, so one is the effective setting: report whatever is
     * there.
     */
    public static final long MIN_AVAILABLE = 1L;

    public RSStockingBusPartMachine(BlockEntityCreationInfo info) {
        super(info, new RSConfiguredItemList(RSInputBusPartMachine.CONFIG_SIZE, RSStockingItemSlot::new));
        for (RSConfiguredItemSlot slot : getItemHandler().getInventory()) {
            ((RSStockingItemSlot) slot).setPart(this);
        }
    }

    /**
     * Refreshes each slot's mirror from the network, taking nothing.
     *
     * <p>A simulated extraction is how the network is asked "how much of this could I have". The
     * amount asked for is the whole of it, not the configured amount, because the mirror is meant to
     * show what is available rather than what was requested.
     */
    @Override
    protected void syncNetwork() {
        StorageNetworkComponent storage = nodeHolder.storage();
        if (storage == null) {
            return;
        }
        for (RSConfiguredItemSlot slot : getItemHandler().getInventory()) {
            ResourceAmount configured = slot.getConfig();
            if (configured == null) {
                slot.setStock(null);
                continue;
            }
            long available = storage.extract(
                    configured.resource(), Long.MAX_VALUE, Action.SIMULATE, RSPartActor.INSTANCE);
            slot.setStock(available >= MIN_AVAILABLE
                    ? new ResourceAmount(configured.resource(), available)
                    : null);
        }
    }

    /**
     * Deliberately empty. The stock is a mirror of what the network already holds, so giving it to
     * the network would create items out of nothing.
     */
    @Override
    protected void flushStockToNetwork() {
    }
}
