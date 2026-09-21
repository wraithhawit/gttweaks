package com.wraithhawit.gttweaks.rs.machine;

import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableFluidTank;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;
import com.gregtechceu.gtceu.common.machine.multiblock.part.FluidHatchPartMachine;

import com.wraithhawit.gttweaks.rs.network.RSNetworkPart;
import com.wraithhawit.gttweaks.rs.network.RSNodeHolder;

import net.minecraft.core.Direction;

/**
 * Shared behaviour of the Refined Storage fluid hatches.
 *
 * <p>The fluid counterpart of {@link RSBusPartMachine}, and deliberately the same shape: hold a
 * network node, stay subscribed whenever the part is working, and ask about the network inside the
 * tick rather than relying on a callback. Refined Storage sets a node's network from the outside
 * with nothing to call back into, so a hatch that unsubscribed while offline would never notice the
 * network returning.
 *
 * <p>It cannot share code with the bus by inheritance — that one extends GregTech's
 * {@code ItemBusPartMachine} and this one {@code FluidHatchPartMachine}, which are siblings — so the
 * duplication here is structural rather than an oversight. {@link RSNetworkPart} is what the rest of
 * the mod uses to treat the two alike, which is why {@code RSCapabilities} asks through that
 * interface instead of casting.
 */
public abstract class RSHatchPartMachine extends FluidHatchPartMachine implements RSNetworkPart {

    /**
     * How often the part talks to the network, in ticks. Matches the buses.
     */
    private static final int SYNC_INTERVAL = 20;

    /**
     * The tier passed to the hatch, matching what GregTech gives its ME parts.
     */
    protected static final int HATCH_TIER = 6;

    protected final RSNodeHolder nodeHolder = attachTrait(new RSNodeHolder());

    @SyncToClient
    protected boolean isOnline;

    protected RSHatchPartMachine(BlockEntityCreationInfo info, IO io, NotifiableFluidTank tank) {
        super(info, HATCH_TIER, io, tank);
    }

    @Override
    public RSNodeHolder getNodeHolder() {
        return nodeHolder;
    }

    public boolean isOnline() {
        return isOnline;
    }

    public void setOnline(boolean online) {
        this.isOnline = online;
        syncDataHolder.markClientSyncFieldDirty("isOnline");
    }

    /**
     * {@inheritDoc}
     *
     * <p>Replaces the normal hatch behaviour rather than adding to it, for the same reason the buses
     * do: these parts move fluid between the multiblock and the storage network only, and a hatch
     * that also pushed into the tank behind it would be a surprise.
     */
    @Override
    protected void updateTankSubscription() {
        if (isWorkingEnabled()) {
            autoIOSubs = subscribeServerTick(autoIOSubs, this::autoIO);
        } else if (autoIOSubs != null) {
            autoIOSubs.unsubscribe();
            autoIOSubs = null;
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Overridden alongside the no-argument form because the base class calls this one directly on
     * rotation and on a neighbour change, and its condition includes "there is a fluid handler on
     * the facing side" — which for a multiblock part facing open air is usually false.
     */
    @Override
    protected void updateTankSubscription(Direction newFacing) {
        updateTankSubscription();
    }

    @Override
    protected void autoIO() {
        if (getLevel() == null || getLevel().isClientSide() || !isWorkingEnabled()) {
            return;
        }
        nodeHolder.refreshActive();
        boolean online = nodeHolder.isOnline();
        if (online != isOnline) {
            setOnline(online);
        }
        if (!online || getOffsetTimer() % SYNC_INTERVAL != 0L) {
            return;
        }
        syncNetwork();
    }

    /**
     * Moves what this part owes the network, and takes what it is owed.
     */
    protected abstract void syncNetwork();
}
