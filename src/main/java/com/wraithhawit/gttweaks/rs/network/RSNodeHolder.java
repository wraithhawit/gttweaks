package com.wraithhawit.gttweaks.rs.network;

import com.gregtechceu.gtceu.api.machine.trait.MachineTrait;

import com.refinedmods.refinedstorage.api.network.Network;
import com.refinedmods.refinedstorage.api.network.energy.EnergyNetworkComponent;
import com.refinedmods.refinedstorage.api.network.impl.node.SimpleNetworkNode;
import com.refinedmods.refinedstorage.api.network.storage.StorageNetworkComponent;
import com.refinedmods.refinedstorage.common.api.RefinedStorageApi;
import com.refinedmods.refinedstorage.common.api.support.network.NetworkNodeContainerProvider;

import org.jetbrains.annotations.Nullable;

/**
 * Puts a GregTech part on a Refined Storage network, and answers what that network holds.
 *
 * <p>The Applied Energistics equivalent, GregTech's {@code GridNodeHolder}, is also a
 * {@link MachineTrait}: the part owns a node, creates it when the machine loads and destroys it when
 * the machine unloads. The two mods differ in who does the finding. AE2 nodes announce themselves
 * from a position; Refined Storage instead looks for a <em>block capability</em> on the block entity
 * at each neighbouring position, so the node here is only half the wiring — see
 * {@code RSCapabilities}, without which a cable laid against this part sees nothing at all.
 *
 * <p>The other half of that difference is why this class never assumes it is off the network just
 * because it has not joined one itself. A neighbouring cable's own initialise discovers us through
 * the capability and merges us in without asking, so {@link #node}'s network is the only honest
 * answer to "are we connected", and every removal path checks it rather than a flag of our own.
 */
public class RSNodeHolder extends MachineTrait {

    /**
     * Named for the debug output RS prints when it walks a network; it has no other effect.
     */
    private static final String CONTAINER_NAME = "gttweaks_part";

    /**
     * Refined Storage charges each node per tick. Zero until the parts have a tier-based cost of
     * their own: a bus that quietly drains a network the player did not size for it is worse than
     * one that is free, and the number is easier to add than to take back.
     */
    private static final long ENERGY_USAGE = 0L;

    private final SimpleNetworkNode node = new SimpleNetworkNode(ENERGY_USAGE);

    @Nullable
    private NetworkNodeContainerProvider containerProvider;

    /**
     * Whether this part has already asked to be taken off the network.
     *
     * <p>Not a claim about network membership — {@link #node}'s own network is the honest answer to
     * that. This only stops a second removal being asked for, which Refined Storage treats as an
     * error rather than a no-op, and which the node's network cannot rule out because the removal is
     * queued and lands a tick later.
     */
    private boolean left;

    /**
     * Whether the machine has unloaded since the join was scheduled.
     *
     * <p>{@link #join()} runs a tick after {@code onMachineLoad}, and the task that carries it is
     * held by the server, not by the machine: breaking or unloading the part in that one-tick window
     * does not cancel it. Without this, a part that is already gone would build a container, hand it
     * to Refined Storage, and leave it on the network with nothing behind it — and because
     * {@link #leave()} had already run and found no network to leave, nothing would ever take it off
     * again.
     */
    private boolean unloaded;

    /**
     * Built on demand rather than in the constructor, because the container wants a block entity
     * that already knows where it is and traits are attached while the machine is still being
     * constructed.
     */
    public NetworkNodeContainerProvider containerProvider() {
        if (containerProvider == null) {
            containerProvider = buildContainerProvider();
        }
        return containerProvider;
    }

    private NetworkNodeContainerProvider buildContainerProvider() {
        NetworkNodeContainerProvider provider = RefinedStorageApi.INSTANCE.createNetworkNodeContainerProvider();
        provider.addContainer(RefinedStorageApi.INSTANCE
                .createNetworkNodeContainer(getMachine(), node)
                .name(CONTAINER_NAME)
                .build());
        return provider;
    }

    public SimpleNetworkNode getNode() {
        return node;
    }

    /**
     * Whether this part can move resources right now: on a network, and that network has power.
     *
     * <p>Refined Storage runs a node's work only while it is active, so this is the same question
     * {@link #node}'s own flag answers, asked from the machine side for the UI line.
     */
    public boolean isOnline() {
        return node.getNetwork() != null && node.isActive();
    }

    /**
     * The network's storage, or null when this part is not on a powered network.
     */
    @Nullable
    public StorageNetworkComponent storage() {
        Network network = node.getNetwork();
        if (network == null || !node.isActive()) {
            return null;
        }
        return network.getComponent(StorageNetworkComponent.class);
    }

    @Override
    public void onMachineLoad() {
        super.onMachineLoad();
        if (isRemote()) {
            return;
        }
        unloaded = false;
        // Next tick, not now, for the reason GregTech's AE2 holder defers too: a block entity that
        // is still being placed into the level is not yet something a network walk can find. Refined
        // Storage queues the join either way -- ServerListener.queue drains at the next tick's Pre --
        // so this costs nothing beyond making the level non-null first.
        getMachine().scheduleForNextServerTick(this::join);
    }

    /**
     * {@inheritDoc}
     *
     * <p>The only place the part leaves the network, and it covers a broken block as well as an
     * unloading chunk: {@code MetaMachine.setRemoved()} is final and calls {@code onUnload()}, which
     * is what runs this.
     *
     * <p>Also leaving from {@code onMachineDestroyed} — the obvious second place, since that is where
     * a broken machine cleans up — throws {@code IllegalArgumentException: The removed container
     * should be present in the removed entries, but isn't} out of breaking the block, which is what
     * the first version did. Note the order, which is the opposite of what the names suggest:
     * {@code MetaMachineBlock.onRemove} calls {@code onMachineDestroyed()} and only then
     * {@code level.removeBlockEntity(pos)}, so the destroy hook is the <em>earlier</em> of the two
     * and the failing call was the first, not a duplicate of this one. Why Refined Storage accepts
     * the removal from here and not from there is not established — at both points the block state
     * at that position has already been replaced — so this is an observation, not a mechanism.
     * {@link #left} exists so that a second attempt cannot be made whatever the reason, since a
     * queued removal leaves {@code node.getNetwork()} set for another tick.
     */
    @Override
    public void onMachineUnload() {
        super.onMachineUnload();
        unloaded = true;
        leave();
    }

    private void join() {
        if (isRemote() || unloaded || getLevel() == null || getMachine().isRemoved()) {
            return;
        }
        refreshActive();
        containerProvider().initialize(getLevel(), null);
        left = false;
    }

    private void leave() {
        if (isRemote() || containerProvider == null || left) {
            return;
        }
        // Gated on the node's own network, not on whether we joined: asking Refined Storage to
        // remove a container it never received throws out of NetworkBuilderImpl.remove, and being
        // joined by a neighbour rather than by ourselves is an ordinary thing here.
        if (node.getNetwork() != null) {
            containerProvider.remove(getLevel());
            left = true;
        }
    }

    /**
     * Brings the node's active flag in line with the network's power.
     *
     * <p>Called from the part's tick rather than on a schedule of its own: a network that browns out
     * has to stop the bus, and a node nobody updates stays whatever it was when it joined.
     */
    public void refreshActive() {
        boolean active = hasEnergy();
        if (node.isActive() != active) {
            node.setActive(active);
        }
    }

    private boolean hasEnergy() {
        if (!RefinedStorageApi.INSTANCE.isEnergyRequired()) {
            return true;
        }
        Network network = node.getNetwork();
        return network != null && network.getComponent(EnergyNetworkComponent.class).getStored() > 0L;
    }
}
