package com.wraithhawit.gttweaks.rs.pattern;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.gregtechceu.gtceu.utils.GTMath;

import com.mojang.serialization.Codec;

import com.refinedmods.refinedstorage.api.core.Action;
import com.refinedmods.refinedstorage.api.network.storage.StorageNetworkComponent;
import com.refinedmods.refinedstorage.api.resource.ResourceAmount;
import com.refinedmods.refinedstorage.api.resource.ResourceKey;
import com.refinedmods.refinedstorage.common.support.resource.FluidResource;
import com.refinedmods.refinedstorage.common.support.resource.ItemResource;
import com.refinedmods.refinedstorage.common.support.resource.ResourceCodecs;

import com.wraithhawit.gttweaks.rs.network.RSPartActor;
import com.wraithhawit.gttweaks.rs.slot.RSFluids;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.common.util.INBTSerializable;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The ingredients of one pushed pattern, held until the multiblock consumes them.
 *
 * <p>This is GregTech's {@code MEPatternBufferPartMachine.InternalSlot} re-expressed over Refined
 * Storage's resources. A pattern buffer owns several of these, and each is offered to the recipe
 * logic as its <em>own</em> set of inputs rather than as part of one pool — that distinctness is the
 * whole point of the part, and it lives in {@link RSBufferRecipeHandlerList}, not here. What this
 * class owns is the contents of a single slot and the two questions asked of them: can you satisfy
 * this ingredient list, and give it back to the network.
 *
 * <p><b>Resources, not stacks.</b> GregTech keys its maps by {@code ItemStack} with a hash strategy
 * that ignores count. Here the key is Refined Storage's own {@link ItemResource} or
 * {@link FluidResource}, which is already a countless identity with components and is what
 * {@code accept} hands over, so nothing has to be converted on the way in and nothing has to agree
 * about what counts as the same item. A stack is built only when something outside asks for one.
 *
 * <p><b>Amounts are {@code long}.</b> They arrive as {@code long} from the network and are kept that
 * way, for the reason recorded against the buses: an amount that round-trips through an {@code int}
 * somewhere in the accounting is an amount that can be silently created or destroyed. The narrowing
 * happens once, at the edge, where a recipe ingredient is matched — that side is an {@code int} in
 * GregTech's own API and cannot be anything else — and it is a saturating cast, so a slot holding
 * more than {@link Integer#MAX_VALUE} satisfies as much as it can rather than wrapping.
 */
public class RSBufferSlot implements INBTSerializable<CompoundTag> {

    private static final Logger LOGGER = LoggerFactory.getLogger(RSBufferSlot.class);

    private static final Codec<Optional<ResourceAmount>> CODEC = ResourceCodecs.AMOUNT_OPTIONAL_CODEC;

    private static final String ITEMS_TAG = "items";
    private static final String FLUIDS_TAG = "fluids";

    /**
     * Insertion-ordered so that what a pattern pushed first is matched first, and so that a slot
     * round-trips through NBT in the order it was filled. Neither is required for correctness; both
     * make a slot's contents reproducible when reading a log or a {@code /data get}.
     */
    private final Map<ItemResource, Long> items = new LinkedHashMap<>();

    private final Map<FluidResource, Long> fluids = new LinkedHashMap<>();

    private Runnable onContentsChanged = () -> {};

    /**
     * Rebuilt lazily, because the recipe logic asks for the contents far more often than they change
     * and {@link GTMath#splitStacks} allocates a stack per full stack held. Both are dropped by
     * {@link #onContentsChanged()}.
     */
    @Nullable
    private List<ItemStack> itemStacks;

    @Nullable
    private List<FluidStack> fluidStacks;

    @Nullable
    private Set<Item> itemTypes;

    @Nullable
    private Set<Fluid> fluidTypes;

    public void setOnContentsChanged(Runnable onContentsChanged) {
        this.onContentsChanged = onContentsChanged;
    }

    public boolean isItemEmpty() {
        return items.isEmpty();
    }

    public boolean isFluidEmpty() {
        return fluids.isEmpty();
    }

    public boolean isEmpty() {
        return isItemEmpty() && isFluidEmpty();
    }

    public void onContentsChanged() {
        itemStacks = null;
        fluidStacks = null;
        itemTypes = null;
        fluidTypes = null;
        onContentsChanged.run();
    }

    /**
     * Takes one resource of a pushed pattern.
     *
     * <p>A resource that is neither an item nor a fluid is dropped rather than stored. Refined
     * Storage's resource type is open — another mod can add one — and a slot that accepted a
     * resource it cannot hand to a recipe would hold it forever, invisible to the multiblock and to
     * the refund path alike. Refusing it here is what lets {@code accept} answer honestly.
     *
     * @return whether the resource was one this slot can hold
     */
    public boolean add(ResourceAmount amount) {
        return add(amount.resource(), amount.amount());
    }

    public boolean add(ResourceKey resource, long amount) {
        if (amount <= 0L) {
            // Not an error: Refined Storage's own ResourceAmount rejects a non-positive amount, so
            // this only guards a caller that built the pair some other way.
            return true;
        }
        if (resource instanceof ItemResource item) {
            items.merge(item, amount, Long::sum);
            onContentsChanged();
            return true;
        }
        if (resource instanceof FluidResource fluid) {
            fluids.merge(fluid, amount, Long::sum);
            onContentsChanged();
            return true;
        }
        return false;
    }

    /**
     * Whether every resource in a pushed pattern is one this slot could hold.
     *
     * <p>Asked before anything is stored, so that a pattern carrying a resource type this part does
     * not understand is refused whole rather than half-accepted. GregTech's {@code checkInput} does
     * the same thing against AE2's key types.
     */
    public static boolean canHold(Iterable<ResourceAmount> resources) {
        for (ResourceAmount amount : resources) {
            ResourceKey resource = amount.resource();
            if (!(resource instanceof ItemResource) && !(resource instanceof FluidResource)) {
                return false;
            }
        }
        return true;
    }

    public List<ItemStack> getItems() {
        if (itemStacks == null) {
            itemStacks = new ArrayList<>();
            items.forEach((resource, amount) ->
                    itemStacks.addAll(GTMath.splitStacks(resource.toItemStack(1L), amount)));
        }
        return itemStacks;
    }

    public List<FluidStack> getFluids() {
        if (fluidStacks == null) {
            fluidStacks = new ArrayList<>();
            fluids.forEach((resource, amount) ->
                    fluidStacks.addAll(GTMath.splitFluidStacks(RSFluids.toStack(resource, 1L), amount)));
        }
        return fluidStacks;
    }

    /**
     * The distinct item types this slot holds, for the cheap reject in
     * {@link RSBufferRecipeHandlerList}.
     *
     * <p>Components are dropped on purpose: this answers "could this slot possibly matter to a
     * recipe mentioning this item", and a slot holding an enchanted pickaxe should still be looked
     * at for a recipe wanting a plain one. Anything finer is the ingredient's own job, a few lines
     * later and against the real resource.
     */
    public Set<Item> getItemTypes() {
        if (itemTypes == null) {
            itemTypes = new HashSet<>();
            items.keySet().forEach(resource -> itemTypes.add(resource.item()));
        }
        return itemTypes;
    }

    public Set<Fluid> getFluidTypes() {
        if (fluidTypes == null) {
            fluidTypes = new HashSet<>();
            fluids.keySet().forEach(resource -> fluidTypes.add(resource.fluid()));
        }
        return fluidTypes;
    }

    public long getTotalItemAmount() {
        return items.values().stream().mapToLong(Long::longValue).sum();
    }

    public long getTotalFluidAmount() {
        return fluids.values().stream().mapToLong(Long::longValue).sum();
    }

    /**
     * Satisfies what it can of an item ingredient list, and returns what is left over.
     *
     * <p>The contract is GregTech's: the list is consumed in place, an ingredient that was fully
     * satisfied is removed, and one that was partly satisfied is replaced by a copy asking for the
     * remainder. An empty returned list means this slot alone can run the recipe, which is what the
     * distinct handling upstream is looking for.
     *
     * <p>{@code simulate} must leave the slot exactly as it found it. The recipe logic runs a
     * simulated pass before every real one, so a slot that consumed during simulation would empty
     * itself against recipes that never ran — the same failure the stocking parts were built to rule
     * out, and the reason nothing here writes to the maps while simulating.
     */
    public List<SizedIngredient> handleItemInternal(List<SizedIngredient> left, boolean simulate) {
        if (items.isEmpty()) {
            return left;
        }
        boolean changed = false;
        var it = left.listIterator();
        while (it.hasNext()) {
            SizedIngredient ingredient = it.next();
            if (ingredient.ingredient().hasNoItems()) {
                it.remove();
                continue;
            }
            ItemStack[] candidates = ingredient.getItems();
            if (candidates.length == 0 || candidates[0].isEmpty()) {
                it.remove();
                continue;
            }
            int wanted = candidates[0].getCount();
            var held = items.entrySet().iterator();
            while (held.hasNext() && wanted > 0) {
                Map.Entry<ItemResource, Long> entry = held.next();
                long count = entry.getValue();
                if (count <= 0L) {
                    held.remove();
                    continue;
                }
                if (!ingredient.test(entry.getKey().toItemStack(1L))) {
                    continue;
                }
                int taken = Math.min(GTMath.saturatedCast(count), wanted);
                if (taken <= 0) {
                    continue;
                }
                if (!simulate) {
                    changed = true;
                    long remaining = count - taken;
                    if (remaining == 0L) {
                        held.remove();
                    } else {
                        entry.setValue(remaining);
                    }
                }
                wanted -= taken;
            }
            if (wanted <= 0) {
                it.remove();
            } else if (wanted != candidates[0].getCount()) {
                it.set(new SizedIngredient(ingredient.ingredient(), wanted));
            }
        }
        if (changed) {
            onContentsChanged();
        }
        return left;
    }

    /**
     * The fluid half of {@link #handleItemInternal}, with the same contract and the same rule about
     * {@code simulate}.
     */
    public List<SizedFluidIngredient> handleFluidInternal(List<SizedFluidIngredient> left, boolean simulate) {
        if (fluids.isEmpty()) {
            return left;
        }
        boolean changed = false;
        var it = left.listIterator();
        while (it.hasNext()) {
            SizedFluidIngredient ingredient = it.next();
            FluidStack[] candidates = ingredient.getFluids();
            if (candidates.length == 0 || candidates[0].isEmpty()) {
                it.remove();
                continue;
            }
            int wanted = candidates[0].getAmount();
            var held = fluids.entrySet().iterator();
            while (held.hasNext() && wanted > 0) {
                Map.Entry<FluidResource, Long> entry = held.next();
                long count = entry.getValue();
                if (count <= 0L) {
                    held.remove();
                    continue;
                }
                if (!ingredient.test(RSFluids.toStack(entry.getKey(), 1L))) {
                    continue;
                }
                int taken = Math.min(GTMath.saturatedCast(count), wanted);
                if (taken <= 0) {
                    continue;
                }
                if (!simulate) {
                    changed = true;
                    long remaining = count - taken;
                    if (remaining == 0L) {
                        held.remove();
                    } else {
                        entry.setValue(remaining);
                    }
                }
                wanted -= taken;
            }
            if (wanted <= 0) {
                it.remove();
            } else if (wanted != candidates[0].getAmount()) {
                it.set(new SizedFluidIngredient(ingredient.ingredient(), wanted));
            }
        }
        if (changed) {
            onContentsChanged();
        }
        return left;
    }

    /**
     * Hands the whole slot back to the network, keeping whatever the network refuses.
     *
     * <p>The same two rules the buses follow. Nothing is voided: a network with no room stalls the
     * slot rather than eating what it holds. And the debit is the amount the network actually
     * accepted, in the resource's own units — never a count taken from a stack — because a slot can
     * legitimately hold more than an {@code int} and an insert accounted through a stack would
     * credit the network more than it debits here.
     */
    public void returnToNetwork(StorageNetworkComponent storage) {
        boolean changed = returnAll(storage, items) | returnAll(storage, fluids);
        if (changed) {
            onContentsChanged();
        }
    }

    private static <T extends ResourceKey> boolean returnAll(StorageNetworkComponent storage,
                                                             Map<T, Long> held) {
        boolean changed = false;
        var it = held.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<T, Long> entry = it.next();
            long amount = entry.getValue();
            if (amount <= 0L) {
                it.remove();
                changed = true;
                continue;
            }
            long inserted = storage.insert(entry.getKey(), amount, Action.EXECUTE, RSPartActor.INSTANCE);
            if (inserted <= 0L) {
                continue;
            }
            changed = true;
            long remaining = amount - inserted;
            if (remaining <= 0L) {
                it.remove();
            } else {
                entry.setValue(remaining);
            }
        }
        return changed;
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        RegistryOps<Tag> ops = provider.createSerializationContext(NbtOps.INSTANCE);
        tag.put(ITEMS_TAG, write(ops, items));
        tag.put(FLUIDS_TAG, write(ops, fluids));
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        RegistryOps<Tag> ops = provider.createSerializationContext(NbtOps.INSTANCE);
        items.clear();
        fluids.clear();
        for (ResourceAmount amount : read(ops, tag, ITEMS_TAG)) {
            add(amount);
        }
        for (ResourceAmount amount : read(ops, tag, FLUIDS_TAG)) {
            add(amount);
        }
        onContentsChanged();
    }

    private static <T extends ResourceKey> ListTag write(RegistryOps<Tag> ops, Map<T, Long> held) {
        ListTag list = new ListTag();
        held.forEach((resource, amount) -> {
            if (amount <= 0L) {
                return;
            }
            CODEC.encodeStart(ops, Optional.of(new ResourceAmount(resource, amount)))
                    .resultOrPartial(error -> LOGGER.error("[gttweaks] could not save buffer slot: {}", error))
                    .ifPresent(list::add);
        });
        return list;
    }

    private static List<ResourceAmount> read(RegistryOps<Tag> ops, CompoundTag tag, String key) {
        List<ResourceAmount> out = new ArrayList<>();
        if (!tag.contains(key)) {
            return out;
        }
        for (Tag entry : tag.getList(key, Tag.TAG_COMPOUND)) {
            CODEC.parse(ops, entry)
                    .resultOrPartial(error -> LOGGER.error("[gttweaks] could not load buffer slot: {}", error))
                    .flatMap(read -> read)
                    .ifPresent(out::add);
        }
        return out;
    }
}
