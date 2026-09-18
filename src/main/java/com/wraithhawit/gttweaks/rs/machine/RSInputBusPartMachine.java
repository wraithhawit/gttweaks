package com.wraithhawit.gttweaks.rs.machine;

import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.recipe.IO;

import com.refinedmods.refinedstorage.api.network.storage.StorageNetworkComponent;
import com.refinedmods.refinedstorage.api.resource.ResourceAmount;
import com.refinedmods.refinedstorage.api.core.Action;

import com.wraithhawit.gttweaks.rs.network.RSPartActor;
import com.wraithhawit.gttweaks.rs.slot.RSConfiguredItemList;
import com.wraithhawit.gttweaks.rs.slot.RSConfiguredItemSlot;

/**
 * Keeps a multiblock supplied with items from a Refined Storage network.
 *
 * <p>Sixteen slots, each with a resource and an amount the player configured. Every sync the part
 * tops each slot back up to its amount and hands back anything it should no longer be holding. The
 * multiblock then draws from those slots as it would from an ordinary input bus, which is the whole
 * trick: nothing in GregTech's recipe logic knows a network is involved.
 *
 * <p>This is GregTech's {@code MEInputBusPartMachine} with Refined Storage underneath, and it
 * departs from it in one place on purpose. When the ME bus has excess to return and the network
 * refuses it, the ME bus <em>deletes</em> the excess to free the slot. Here the stock stays put and
 * the slot retries on the next sync: a full or disconnected network should stall a bus, not eat what
 * it is holding. The cost is that a slot whose configuration changed while the network was full
 * keeps its old contents until there is room.
 */
public class RSInputBusPartMachine extends RSBusPartMachine {

    public static final int CONFIG_SIZE = 16;

    protected final RSConfiguredItemList itemHandler;

    public RSInputBusPartMachine(BlockEntityCreationInfo info) {
        this(info, new RSConfiguredItemList(CONFIG_SIZE));
    }

    protected RSInputBusPartMachine(BlockEntityCreationInfo info, RSConfiguredItemList inventory) {
        super(info, IO.IN, inventory);
        this.itemHandler = inventory;
    }

    public RSConfiguredItemList getItemHandler() {
        return itemHandler;
    }

    @Override
    protected void syncNetwork() {
        StorageNetworkComponent storage = nodeHolder.storage();
        if (storage == null) {
            return;
        }
        for (RSConfiguredItemSlot slot : itemHandler.getInventory()) {
            returnExcess(storage, slot);
            requestConfigured(storage, slot);
        }
    }

    private void returnExcess(StorageNetworkComponent storage, RSConfiguredItemSlot slot) {
        ResourceAmount excess = slot.exceedStack();
        if (excess == null) {
            return;
        }
        long inserted = storage.insert(excess.resource(), excess.amount(), Action.EXECUTE, RSPartActor.INSTANCE);
        if (inserted > 0L) {
            // Through the slot's own extract, so the stock and the change notification stay in step.
            slot.extractItem(0, (int) Math.min(Integer.MAX_VALUE, inserted), false);
        }
    }

    private void requestConfigured(StorageNetworkComponent storage, RSConfiguredItemSlot slot) {
        ResourceAmount wanted = slot.requestStack();
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
     * the part was not connected stays in the slots and is lost with the block, the same as
     * GregTech's ME bus. Worth revisiting once the parts drop their contents properly.
     */
    @Override
    public void onMachineDestroyed() {
        super.onMachineDestroyed();
        StorageNetworkComponent storage = nodeHolder.storage();
        if (storage == null) {
            return;
        }
        for (RSConfiguredItemSlot slot : itemHandler.getInventory()) {
            ResourceAmount stock = slot.getStock();
            if (stock == null) {
                continue;
            }
            long inserted = storage.insert(stock.resource(), stock.amount(), Action.EXECUTE, RSPartActor.INSTANCE);
            if (inserted >= stock.amount()) {
                slot.setStock(null);
            } else if (inserted > 0L) {
                slot.extractItem(0, (int) Math.min(Integer.MAX_VALUE, inserted), false);
            }
        }
    }
}
