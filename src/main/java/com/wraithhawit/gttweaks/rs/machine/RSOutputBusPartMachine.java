package com.wraithhawit.gttweaks.rs.machine;

import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;

import com.refinedmods.refinedstorage.api.core.Action;
import com.refinedmods.refinedstorage.api.network.storage.StorageNetworkComponent;
import com.refinedmods.refinedstorage.common.support.resource.ItemResource;

import com.wraithhawit.gttweaks.rs.network.RSPartActor;

import net.minecraft.world.item.ItemStack;

/**
 * Empties a multiblock's item results into a Refined Storage network.
 *
 * <p>The mirror image of {@link RSInputBusPartMachine}, and much the simpler of the two: there is
 * nothing to configure. The multiblock writes its outputs into an ordinary bus inventory, and every
 * sync this hands whatever is sitting there to the network.
 *
 * <p>It departs from GregTech's {@code MEOutputBusPartMachine} in one structural way. That one
 * keeps an unbounded {@code KeyStorage} as its buffer, so a multiblock can never be held up by a
 * full or missing network — the items go into the buffer regardless and the buffer flushes later.
 * This one buffers in a real sixteen-slot inventory instead, so when the network cannot take what
 * the bus is holding the slots fill and the multiblock stalls, exactly as it would against a
 * conventional output bus that nobody is emptying.
 *
 * <p>That is the same choice made for the input bus, for the same reason: a storage network that is
 * full or offline should stop the line, not quietly absorb an unbounded amount of material whose
 * only record is one part's NBT. The cost is that a network outage stops production after sixteen
 * slots rather than never, which is the behaviour a GregTech player already expects from a bus.
 */
public class RSOutputBusPartMachine extends RSBusPartMachine {

    /**
     * Matches the input bus's slot count, so the pair reads as a pair. Nothing depends on the two
     * being equal.
     */
    public static final int BUFFER_SIZE = 16;

    public RSOutputBusPartMachine(BlockEntityCreationInfo info) {
        super(info, IO.OUT, new NotifiableItemStackHandler(BUFFER_SIZE, IO.OUT, IO.NONE));
    }

    /**
     * Hands the buffer to the network, slot by slot, keeping whatever the network refuses.
     *
     * <p>Each slot is accounted for by what the network actually accepted, never by what it was
     * offered: a partial insert has to leave the remainder in the slot, or the difference is items
     * that existed a moment ago and now do not.
     */
    @Override
    protected void syncNetwork() {
        StorageNetworkComponent storage = nodeHolder.storage();
        if (storage == null) {
            return;
        }
        flushTo(storage);
    }

    private void flushTo(StorageNetworkComponent storage) {
        CustomItemStackHandler handler = getInventory().storage;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            long inserted = storage.insert(
                    ItemResource.ofItemStack(stack), stack.getCount(), Action.EXECUTE, RSPartActor.INSTANCE);
            if (inserted <= 0L) {
                continue;
            }
            // The insert was offered stack.getCount(), an int, so it cannot have taken more.
            handler.extractItem(slot, (int) inserted, false);
        }
    }

    /**
     * Flushes the buffer on the way out, so breaking a bus that still holds results does not lose
     * them.
     *
     * <p>Only best effort, and deliberately so: what a full or disconnected network will not take
     * stays in the slots. Unlike the input bus, that is not a loss — GregTech drops nothing here
     * either, but the items in an output buffer are the multiblock's own recent work rather than
     * something the network already believed it owned.
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
