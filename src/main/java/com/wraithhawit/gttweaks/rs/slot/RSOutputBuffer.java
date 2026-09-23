package com.wraithhawit.gttweaks.rs.slot;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;

import com.mojang.logging.LogUtils;

import com.refinedmods.refinedstorage.api.resource.ResourceAmount;
import com.refinedmods.refinedstorage.common.support.resource.ItemResource;
import com.refinedmods.refinedstorage.common.support.resource.ResourceCodecs;

import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The RS Output Bus's holding area: takes any amount of anything a recipe produces, and hands it to
 * the network a sync at a time.
 *
 * <p>It used to be sixteen ordinary slots, which is at most 1,024 items. A recipe that produces
 * more than that in one go — a greenhouse recipe in ATM10(3) showed 163,840 of one item in Jade's
 * recipe-output readout — could never be
 * accepted, and the multiblock refused to start with "insufficient outputs". GregTech's ME Output Bus
 * has no such limit: its {@code InaccessibleInfiniteHandler} keeps a key-to-amount map instead of
 * slots, and this is the same design over Refined Storage's {@link ItemResource}.
 *
 * <p>The multiblock sees one handler whose storage claims 32,767 slots of unlimited size, so the
 * recipe logic's insertion lands in the first and is taken whole. Nothing can be pulled back out:
 * the only way items leave is {@link #drainTo}, called from the bus's network sync.
 */
public class RSOutputBuffer extends CustomItemStackHandler {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String BUFFER_TAG = "rs_buffer";

    /** Insertion order is kept so the screen lists what arrived first at the top. */
    private final Object2LongLinkedOpenHashMap<ItemResource> buffer = new Object2LongLinkedOpenHashMap<>();

    /** Builds the handler the bus attaches: one nominal slot whose storage is a buffer. */
    public static NotifiableItemStackHandler createHandler() {
        return new Handler();
    }

    @Override
    public int getSlots() {
        return Short.MAX_VALUE;
    }

    @Override
    public int getSlotLimit(int slot) {
        return Integer.MAX_VALUE;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public void setStackInSlot(int slot, ItemStack stack) {
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemResource resource = ItemResource.ofItemStack(stack);
        long held = buffer.getLong(resource);
        long accepted = Math.min(Long.MAX_VALUE - held, stack.getCount());
        if (accepted <= 0L) {
            return stack;
        }
        if (!simulate) {
            buffer.put(resource, held + accepted);
            onContentsChanged(0);
        }
        return stack.getCount() == accepted ? ItemStack.EMPTY : stack.copyWithCount((int) (stack.getCount() - accepted));
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        return ItemStack.EMPTY;
    }

    public boolean isBufferEmpty() {
        return buffer.isEmpty();
    }

    /** What is waiting to be sent, oldest first. */
    public List<ResourceAmount> snapshot() {
        List<ResourceAmount> out = new ArrayList<>(buffer.size());
        for (Object2LongMap.Entry<ItemResource> entry : buffer.object2LongEntrySet()) {
            out.add(new ResourceAmount(entry.getKey(), entry.getLongValue()));
        }
        return out;
    }

    /** Where the buffer drains to; {@code insert} returns how much was actually taken. */
    @FunctionalInterface
    public interface Sink {
        long insert(ItemResource resource, long amount);
    }

    /**
     * Offers everything held to {@code sink} and keeps whatever it refused.
     *
     * <p>Iterates a snapshot so the map can be changed while draining.
     */
    public void drainTo(Sink sink) {
        boolean changed = false;
        for (ResourceAmount entry : snapshot()) {
            ItemResource resource = (ItemResource) entry.resource();
            long inserted = sink.insert(resource, entry.amount());
            if (inserted <= 0L) {
                continue;
            }
            long left = entry.amount() - inserted;
            if (left > 0L) {
                buffer.put(resource, left);
            } else {
                buffer.removeLong(resource);
            }
            changed = true;
        }
        if (changed) {
            onContentsChanged(0);
        }
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        RegistryOps<Tag> ops = provider.createSerializationContext(NbtOps.INSTANCE);
        ListTag list = new ListTag();
        for (ResourceAmount entry : snapshot()) {
            ResourceCodecs.AMOUNT_CODEC.encodeStart(ops, entry)
                    .resultOrPartial(error -> LOGGER.error("[gttweaks] could not save output buffer entry: {}", error))
                    .ifPresent(list::add);
        }
        tag.put(BUFFER_TAG, list);
        return tag;
    }

    /**
     * Reads the buffer back, including a bus saved before the buffer existed.
     *
     * <p>An RS Output Bus placed with an earlier build saved sixteen ordinary slots, in
     * {@link ItemStackHandler}'s own {@code Items} format. Those items are taken into the buffer rather
     * than lost when the world is loaded with this build.
     */
    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        buffer.clear();
        if (tag.contains(BUFFER_TAG)) {
            RegistryOps<Tag> ops = provider.createSerializationContext(NbtOps.INSTANCE);
            for (Tag entryTag : tag.getList(BUFFER_TAG, Tag.TAG_COMPOUND)) {
                ResourceCodecs.AMOUNT_CODEC.parse(ops, entryTag)
                        .resultOrPartial(error -> LOGGER.error("[gttweaks] could not load output buffer entry: {}", error))
                        .ifPresent(entry -> {
                            if (entry.resource() instanceof ItemResource item && entry.amount() > 0L) {
                                buffer.merge(item, entry.amount(), Long::sum);
                            }
                        });
            }
            return;
        }
        if (tag.contains("Items")) {
            ItemStackHandler legacy = new ItemStackHandler();
            legacy.deserializeNBT(provider, tag);
            for (int slot = 0; slot < legacy.getSlots(); slot++) {
                ItemStack stack = legacy.getStackInSlot(slot);
                if (!stack.isEmpty()) {
                    buffer.merge(ItemResource.ofItemStack(stack), (long) stack.getCount(), Long::sum);
                }
            }
        }
    }

    /**
     * The trait the bus attaches. Reports no contents, as GregTech's ME version does, so the
     * multiblock never mistakes buffered output for something it could use.
     */
    private static final class Handler extends NotifiableItemStackHandler {

        private Handler() {
            super(1, IO.OUT, IO.NONE, slots -> new RSOutputBuffer());
        }

        @Override
        public List<Object> getContents() {
            return Collections.emptyList();
        }

        @Override
        public double getTotalContentAmount() {
            return 0;
        }

        @Override
        public boolean isEmpty() {
            return true;
        }
    }
}
