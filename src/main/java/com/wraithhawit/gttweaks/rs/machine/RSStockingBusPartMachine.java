package com.wraithhawit.gttweaks.rs.machine;

import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;

import com.refinedmods.refinedstorage.api.core.Action;
import com.refinedmods.refinedstorage.api.network.storage.StorageNetworkComponent;
import com.refinedmods.refinedstorage.api.resource.ResourceAmount;
import com.refinedmods.refinedstorage.api.resource.ResourceKey;

import com.wraithhawit.gttweaks.rs.network.RSPartActor;
import com.wraithhawit.gttweaks.rs.slot.RSConfiguredItemList;
import com.wraithhawit.gttweaks.rs.slot.RSConfiguredItemSlot;
import com.wraithhawit.gttweaks.rs.slot.RSStockingItemSlot;

import java.util.HashSet;
import java.util.Set;

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
 * GregTech does too — its mirror asks AE2 for {@link Long#MAX_VALUE}. See
 * {@link #MIRROR_REQUEST} for why this one cannot.
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

    /**
     * The most a mirror will ask the network for.
     *
     * <p>GregTech asks AE2 for {@link Long#MAX_VALUE} and gets an honest answer. Refined Storage's
     * external storage cannot give one. {@code ItemHandlerExtractableStorage.extract} works out the
     * amount still wanted as a {@code long} and then narrows it with {@code l2i} before calling
     * {@code extractSlot(..., int, int)}, so {@code Long.MAX_VALUE} arrives as {@code -1} and every
     * slot yields nothing: a chest behind an external storage mirrored as permanently empty. The
     * fluid side truncates the same way and is no safer -- {@code VariantUtil.toFluidStack} logs
     * "Truncating too large amount" and then casts anyway, so the drain is asked for {@code -1} mB
     * and yields nothing too. Both sides therefore ask for the same bounded amount.
     *
     * <p>The cost is a mirror that cannot show more than about 2.1 billion of one resource, which
     * nothing can draw in one call regardless: {@code extractItem} and {@code drain} both take an
     * {@code int}.
     */
    public static final long MIRROR_REQUEST = Integer.MAX_VALUE;

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
     *
     * <p>Only the first slot configured with a resource mirrors it. A later slot asking for the same
     * one is left with no stock, which its extraction treats as nothing to take, because mirroring
     * the whole network into both would show a multiblock twice what exists: 320 Inferium read as
     * 640, and a recipe needing more than 320 could pass the simulated pass and fall short when it
     * ran. GregTech's ME stocking parts had the same gap; {@code MEStockingDuplicateMixin} closes it.
     */
    @Override
    protected void syncNetwork() {
        StorageNetworkComponent storage = nodeHolder.storage();
        if (storage == null) {
            return;
        }
        Set<ResourceKey> seen = new HashSet<>();
        for (RSConfiguredItemSlot slot : getItemHandler().getInventory()) {
            ResourceAmount configured = slot.getConfig();
            if (configured == null || !seen.add(configured.resource())) {
                slot.setStock(null);
                continue;
            }
            long available = storage.extract(
                    configured.resource(), MIRROR_REQUEST, Action.SIMULATE, RSPartActor.INSTANCE);
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
