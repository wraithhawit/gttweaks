package com.wraithhawit.gttweaks.rs.slot;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableFluidTank;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.ingredient.IntProviderFluidIngredient;
import com.gregtechceu.gtceu.api.transfer.fluid.CustomFluidTank;

import com.mojang.logging.LogUtils;

import com.refinedmods.refinedstorage.api.resource.ResourceAmount;
import com.refinedmods.refinedstorage.common.support.resource.FluidResource;
import com.refinedmods.refinedstorage.common.support.resource.ResourceCodecs;

import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;

/**
 * The RS Output Hatch's holding area: takes any amount of any fluid a recipe produces, and hands it
 * to the network a sync at a time.
 *
 * <p>The fluid twin of {@link RSOutputBuffer}, for the same reason: the hatch used to hold its
 * output in four fixed tanks, so a recipe producing more fluid in one go than a tank holds could
 * never start, and the multiblock reported "insufficient outputs" with the hatch empty. GregTech's
 * ME Output Hatch has no such limit, and this follows it: its {@code InaccessibleInfiniteTank}
 * answers the recipe logic directly from {@code handleRecipeInner} and keeps a key-to-amount map,
 * so a recipe's output never passes through a tank's {@code int} capacity at all.
 *
 * <p>This is tank 0 of the hatch. Tanks 1 to 3 stay ordinary tanks only so that a hatch saved with
 * the four-tank build loads into the same four slots it was saved from; recipe output never reaches
 * them, and whatever an old save left in them is drained to the network like everything else. Fluid
 * an old save left in tank 0 is taken into the buffer on load.
 */
public class RSOutputFluidBuffer extends CustomFluidTank {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String BUFFER_TAG = "rs_buffer";

    /** Insertion order is kept, as the item buffer's is. */
    private final Object2LongLinkedOpenHashMap<FluidResource> buffer = new Object2LongLinkedOpenHashMap<>();

    public RSOutputFluidBuffer() {
        super(Integer.MAX_VALUE);
    }

    /**
     * Builds the tank the hatch attaches: this buffer as tank 0, then ordinary tanks so the whole
     * has the count and capacities the hatch was saved with before.
     */
    public static NotifiableFluidTank createTank(int tanks, int legacyCapacity) {
        List<CustomFluidTank> storages = new ArrayList<>(tanks);
        storages.add(new RSOutputFluidBuffer());
        for (int i = 1; i < tanks; i++) {
            storages.add(new CustomFluidTank(legacyCapacity));
        }
        return new Tank(storages);
    }

    // Nothing reaches the buffer as an ordinary tank: not in, not out, and it shows as empty.

    @Override
    public FluidStack getFluid() {
        return FluidStack.EMPTY;
    }

    @Override
    public int getFluidAmount() {
        return 0;
    }

    @Override
    public void setFluid(FluidStack stack) {
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        return 0;
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        return FluidStack.EMPTY;
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        return FluidStack.EMPTY;
    }

    /** Takes a recipe's output whole. */
    public void add(FluidStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        FluidResource resource = RSFluids.toResource(stack);
        long held = buffer.getLong(resource);
        buffer.put(resource, held + Math.min(Long.MAX_VALUE - held, stack.getAmount()));
        onContentsChanged();
    }

    public boolean isBufferEmpty() {
        return buffer.isEmpty();
    }

    /** What is waiting to be sent, oldest first. */
    public List<ResourceAmount> snapshot() {
        List<ResourceAmount> out = new ArrayList<>(buffer.size());
        for (Object2LongMap.Entry<FluidResource> entry : buffer.object2LongEntrySet()) {
            out.add(new ResourceAmount(entry.getKey(), entry.getLongValue()));
        }
        return out;
    }

    /** Where the buffer drains to; {@code insert} returns how much was actually taken. */
    @FunctionalInterface
    public interface Sink {
        long insert(FluidResource resource, long amount);
    }

    /**
     * Offers everything held to {@code sink} and keeps whatever it refused, accounting by what was
     * accepted rather than what was offered.
     */
    public void drainTo(Sink sink) {
        boolean changed = false;
        for (ResourceAmount entry : snapshot()) {
            FluidResource resource = (FluidResource) entry.resource();
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
            onContentsChanged();
        }
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        RegistryOps<Tag> ops = provider.createSerializationContext(NbtOps.INSTANCE);
        ListTag list = new ListTag();
        for (ResourceAmount entry : snapshot()) {
            ResourceCodecs.AMOUNT_CODEC.encodeStart(ops, entry)
                    .resultOrPartial(error -> LOGGER.error("[gttweaks] could not save fluid buffer entry: {}", error))
                    .ifPresent(list::add);
        }
        tag.put(BUFFER_TAG, list);
        return tag;
    }

    /**
     * Reads the buffer back, including a hatch saved before the buffer existed. That one saved an
     * ordinary tank here, which is read with an ordinary tank so this does not depend on its format,
     * and its fluid is taken into the buffer.
     */
    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        buffer.clear();
        if (tag.contains(BUFFER_TAG)) {
            RegistryOps<Tag> ops = provider.createSerializationContext(NbtOps.INSTANCE);
            for (Tag entryTag : tag.getList(BUFFER_TAG, Tag.TAG_COMPOUND)) {
                ResourceCodecs.AMOUNT_CODEC.parse(ops, entryTag)
                        .resultOrPartial(error -> LOGGER.error("[gttweaks] could not load fluid buffer entry: {}", error))
                        .ifPresent(entry -> {
                            if (entry.resource() instanceof FluidResource fluid && entry.amount() > 0L) {
                                buffer.merge(fluid, entry.amount(), Long::sum);
                            }
                        });
            }
            return;
        }
        CustomFluidTank legacy = new CustomFluidTank(Integer.MAX_VALUE);
        legacy.deserializeNBT(provider, tag);
        FluidStack held = legacy.getFluid();
        if (!held.isEmpty()) {
            buffer.merge(RSFluids.toResource(held), (long) held.getAmount(), Long::sum);
        }
    }

    /**
     * The trait the hatch attaches. Recipe output goes straight into the buffer, never into a
     * tank, as GregTech's {@code InaccessibleInfiniteTank} does; and like it, this reports no
     * contents, so the multiblock never mistakes buffered output for something it could use.
     */
    private static final class Tank extends NotifiableFluidTank {

        private final RSOutputFluidBuffer buffer;

        private Tank(List<CustomFluidTank> storages) {
            super(storages, IO.OUT, IO.NONE);
            this.buffer = (RSOutputFluidBuffer) storages.get(0);
        }

        /**
         * Accepts every fluid output whole. A simulated pass adds nothing; a ranged output is
         * simulated at its largest possible amount and executed at the amount rolled, as GregTech's
         * does.
         */
        @Override
        public List<SizedFluidIngredient> handleRecipeInner(IO io, GTRecipe recipe,
                                                            List<SizedFluidIngredient> left, boolean simulate) {
            if (io != IO.OUT) {
                return left;
            }
            ListIterator<SizedFluidIngredient> it = left.listIterator();
            while (it.hasNext()) {
                SizedFluidIngredient ingredient = it.next();
                it.remove();
                if (ingredient.ingredient().hasNoFluids()) {
                    continue;
                }
                FluidStack[] fluids = simulate && ingredient.ingredient() instanceof IntProviderFluidIngredient ranged
                        ? new FluidStack[] { ranged.getMaxSizeStack() }
                        : ingredient.getFluids();
                if (fluids.length == 0 || fluids[0].isEmpty()) {
                    continue;
                }
                if (!simulate) {
                    buffer.add(fluids[0]);
                }
            }
            return left;
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
