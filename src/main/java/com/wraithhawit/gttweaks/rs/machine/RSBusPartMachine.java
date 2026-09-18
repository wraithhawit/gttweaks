package com.wraithhawit.gttweaks.rs.machine;

import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;
import com.gregtechceu.gtceu.common.machine.multiblock.part.ItemBusPartMachine;

import com.wraithhawit.gttweaks.rs.network.RSNodeHolder;

import net.minecraft.core.Direction;

/**
 * Shared behaviour of the Refined Storage item buses: hold a network node, and sync on a tick.
 *
 * <p>The Applied Energistics counterpart, {@code MEBusPartMachine}, only subscribes its tick while
 * the node is online, and relies on AE2 calling it back through {@code onMainNodeStateChanged} to
 * subscribe again. Refined Storage has no equivalent callback — a node's network is set on it from
 * the outside, quietly — so a bus that unsubscribed while offline would have nothing left to notice
 * the network coming back. This one stays subscribed whenever it is working, and asks about the
 * network inside {@link #autoIO()} instead.
 */
public abstract class RSBusPartMachine extends ItemBusPartMachine {

    /**
     * How often the part talks to the network, in ticks.
     *
     * <p>Every tick would be correct and wasteful: each sync walks every configured slot and asks
     * the network's storage about each one. GregTech's ME buses gate their own sync the same way.
     */
    private static final int SYNC_INTERVAL = 20;

    /**
     * The tier passed to the bus, matching what GregTech gives its ME parts.
     *
     * <p>It does not size this inventory — the configured slot list does that — but the base class
     * still reads it for the part's own behaviour, so leaving it at a normal bus tier would be a
     * quiet inconsistency with the ME parts these sit beside.
     */
    protected static final int BUS_TIER = 6;

    protected final RSNodeHolder nodeHolder = attachTrait(new RSNodeHolder());

    @SyncToClient
    protected boolean isOnline;

    protected RSBusPartMachine(BlockEntityCreationInfo info, IO io, NotifiableItemStackHandler inventory) {
        super(info, BUS_TIER, io, inventory);
    }

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
     * <p>Replaces the normal bus behaviour rather than adding to it. A plain item bus pushes into
     * whatever inventory it faces; these parts move items only between the multiblock and the
     * storage network, and an RS bus quietly feeding the chest behind it would be a surprise.
     */
    @Override
    protected void updateInventorySubscription() {
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
     * rotation and on a neighbour change, and its condition includes "there is an item handler on
     * the facing side". For a normal bus that is the point; for this one it would unsubscribe the
     * part whenever it faces open air, which is most of the time, and the network sync would stop.
     */
    @Override
    protected void updateInventorySubscription(Direction newFacing) {
        updateInventorySubscription();
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
