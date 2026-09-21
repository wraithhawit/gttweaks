package com.wraithhawit.gttweaks.rs.machine;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeHandlerList;
import com.gregtechceu.gtceu.common.machine.multiblock.part.ItemBusPartMachine;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;

import com.refinedmods.refinedstorage.api.autocrafting.Pattern;
import com.refinedmods.refinedstorage.api.autocrafting.task.ExternalPatternSink;
import com.refinedmods.refinedstorage.api.core.Action;
import com.refinedmods.refinedstorage.api.network.autocrafting.PatternProviderExternalPatternSink;
import com.refinedmods.refinedstorage.api.network.impl.node.patternprovider.PatternProviderNetworkNode;
import com.refinedmods.refinedstorage.api.network.storage.StorageNetworkComponent;
import com.refinedmods.refinedstorage.api.resource.ResourceAmount;
import com.refinedmods.refinedstorage.api.resource.ResourceKey;
import com.refinedmods.refinedstorage.common.api.RefinedStorageApi;
import com.refinedmods.refinedstorage.common.api.autocrafting.PatternProviderItem;

import com.wraithhawit.gttweaks.rs.network.RSNetworkPart;
import com.wraithhawit.gttweaks.rs.network.RSNodeHolder;
import com.wraithhawit.gttweaks.rs.pattern.RSBufferAggregateFluidHandler;
import com.wraithhawit.gttweaks.rs.pattern.RSBufferAggregateItemHandler;
import com.wraithhawit.gttweaks.rs.pattern.RSBufferRecipeHandlerList;
import com.wraithhawit.gttweaks.rs.pattern.RSBufferSlot;
import com.wraithhawit.gttweaks.rs.pattern.RSBufferSlotFluidHandler;
import com.wraithhawit.gttweaks.rs.pattern.RSBufferSlotItemHandler;

import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

/**
 * A Refined Storage pattern buffer: the network crafts, and the ingredients land here for the
 * multiblock to use.
 *
 * <p>This is GregTech's ME pattern buffer against a Refined Storage network. The part holds encoded
 * patterns, publishes them to the network's autocrafting so the network believes it can make those
 * things, and when a task runs the ingredients are pushed into one of this part's internal slots.
 * The multiblock then reads those slots as recipe inputs, so a GregTech machine becomes something an
 * RS autocrafting request can drive end to end.
 *
 * <p><b>Why the slots are separate.</b> Each pushed pattern fills one slot, and a recipe may only be
 * satisfied from a single slot — see {@link RSBufferRecipeHandlerList}. Pooling them would let one
 * recipe take half its inputs from one pattern and half from another, completing neither.
 *
 * <p><b>What the two mods each do here.</b> Refined Storage owns the deciding: it works out that a
 * pattern is needed, reserves the ingredients and hands them over through
 * {@link PatternProviderExternalPatternSink#accept}. This part owns the holding, and GregTech owns
 * the consuming. Nothing here schedules or plans a craft.
 */
public class RSPatternBufferPartMachine extends ItemBusPartMachine implements RSNetworkPart {

    /**
     * How many encoded patterns the part can hold, matching GregTech's ME pattern buffer.
     */
    public static final int PATTERN_COUNT = 27;

    /**
     * Matches the other RS parts, which sit at the tier GregTech gives its ME equivalents.
     */
    private static final int BUFFER_TIER = 6;

    /**
     * The part's own bus inventory, which is not offered to recipes.
     *
     * <p>{@link #getRecipeHandlers()} returns only the buffer's handler list, so this inventory is
     * invisible to the recipe logic. It exists because the bus base class expects one. GregTech's
     * pattern buffer carries the same vestigial nine slots for the same reason.
     */
    private static final int BASE_INVENTORY_SIZE = 9;

    /**
     * Refined Storage charges a node per tick; zero here for the reason recorded on {@link
     * RSNodeHolder}, so that a part cannot quietly drain a network the player did not size for it.
     */
    private static final long ENERGY_USAGE = 0L;

    private final PatternProviderNetworkNode patternNode =
            new PatternProviderNetworkNode(ENERGY_USAGE, PATTERN_COUNT);

    private final RSNodeHolder nodeHolder = attachTrait(new RSNodeHolder(patternNode));

    @SaveField
    @SyncToClient
    private final CustomItemStackHandler patternInventory = new CustomItemStackHandler(PATTERN_COUNT);

    /**
     * Every slot this part could ever use, allocated once and persisted as a fixed array.
     *
     * <p>Not a {@link List}, and the difference is not cosmetic. GregTech's save system restored a
     * {@code @SaveField List<RSBufferSlot>} as <em>empty</em>, so the part came back from a restart
     * with no slots at all, refusing every delivery with "this part has 0". An array of
     * {@code INBTSerializable} round-trips; a list of them silently does not. GregTech's own buffer
     * persists {@code InternalSlot[]} for the same reason, and derives its live workers from it.
     */
    @SaveField
    private final RSBufferSlot[] internalSlots = new RSBufferSlot[PATTERN_COUNT];

    /**
     * The slots actually in use, a view onto the head of {@link #internalSlots}. Rebuilt on load
     * rather than saved, because its length is decided by the part's shape, not by what was stored.
     */
    private final List<RSBufferSlot> slots = new ArrayList<>();

    private final List<RSBufferSlotItemHandler> slotItemHandlers = new ArrayList<>();

    private final List<RSBufferSlotFluidHandler> slotFluidHandlers = new ArrayList<>();

    private final RSBufferAggregateItemHandler aggregateItems;

    private final RSBufferAggregateFluidHandler aggregateFluids;

    private final RSBufferRecipeHandlerList bufferHandler;

    private final List<RecipeHandlerList> bufferHandlers;

    @SyncToClient
    private boolean isOnline;

    /**
     * The proxies pointing at this buffer, by position.
     *
     * <p>Each one earns the buffer another slot, so a buffer feeding three multiblocks through three
     * proxies has four slots and can hold four patterns' ingredients at once. That is GregTech's
     * rule, and without it a second multiblock would have to wait for the first to finish.
     *
     * <p>Positions rather than machines, because a proxy in an unloaded chunk still counts: losing
     * its slot while it sleeps would shuffle every other proxy's slot underneath it.
     */
    @SaveField
    private final Set<BlockPos> proxies = new HashSet<>();

    public RSPatternBufferPartMachine(BlockEntityCreationInfo info) {
        super(info, BUFFER_TIER, IO.IN,
                new NotifiableItemStackHandler(BASE_INVENTORY_SIZE, IO.IN, IO.NONE));
        // Only a real, encoded Refined Storage pattern may go in. PatternProviderItem.isValid asks
        // the item itself whether it decodes, so this filter covers any mod's pattern item rather
        // than a hardcoded list of RS's own.
        patternInventory.setFilter(stack -> getLevel() != null
                && PatternProviderItem.isValid(stack, getLevel()));
        patternInventory.setOnContentsChanged(this::onPatternsChanged);
        for (int i = 0; i < internalSlots.length; i++) {
            internalSlots[i] = new RSBufferSlot();
        }
        addSlot();
        aggregateItems = attachTrait(new RSBufferAggregateItemHandler(() -> slots));
        aggregateFluids = attachTrait(new RSBufferAggregateFluidHandler(() -> slots));
        bufferHandler = new RSBufferRecipeHandlerList(
                () -> slots, () -> slotItemHandlers, () -> slotFluidHandlers,
                aggregateItems, aggregateFluids);
        bufferHandlers = List.of(bufferHandler);
        patternNode.setSink(sink());
    }

    /**
     * Where Refined Storage delivers a pattern's ingredients.
     *
     * <p>Three refusals, each deliberate. A pattern carrying a resource that is neither an item nor
     * a fluid is refused whole rather than partly stored, because a slot cannot hand such a resource
     * to a recipe and would hold it out of reach forever. A part that is off the network or unpowered
     * refuses, so a task does not commit ingredients to somewhere that cannot use them. And when
     * every slot is busy the answer is {@code REJECTED} rather than a wait, which leaves Refined
     * Storage holding the ingredients and free to try again — the buffer never takes more than it
     * has somewhere to put.
     *
     * <p>{@link Action#SIMULATE} must store nothing. Refined Storage asks whether the delivery would
     * be accepted before making it, and a sink that filled a slot during that question would consume
     * the network's reservation for a craft that never ran.
     */
    private PatternProviderExternalPatternSink sink() {
        return (resources, action) -> {
            if (!nodeHolder.isOnline()) {
                return ExternalPatternSink.Result.REJECTED;
            }
            if (!RSBufferSlot.canHold(resources)) {
                return ExternalPatternSink.Result.REJECTED;
            }
            RSBufferSlot free = freeSlot();
            if (free == null) {
                return ExternalPatternSink.Result.REJECTED;
            }
            if (action == Action.SIMULATE) {
                return ExternalPatternSink.Result.ACCEPTED;
            }
            for (ResourceAmount amount : resources) {
                free.add(amount);
            }
            return ExternalPatternSink.Result.ACCEPTED;
        };
    }

    @Nullable
    private RSBufferSlot freeSlot() {
        for (RSBufferSlot slot : slots) {
            if (slot.isEmpty()) {
                return slot;
            }
        }
        return null;
    }

    /**
     * Adds one internal slot and the pair of handlers that speak for it.
     *
     * <p>A lone buffer has exactly one, which is GregTech's behaviour too: its worker count is the
     * number of attached proxies plus one. So one pattern is in flight at a time until the proxy part
     * exists, and a second delivery is refused rather than pooled into the first.
     */
    private void addSlot() {
        int index = slots.size();
        if (index >= internalSlots.length) {
            return;
        }
        RSBufferSlot slot = internalSlots[index];
        slot.setOnContentsChanged(this::onSlotChanged);
        slots.add(slot);
        slotItemHandlers.add(new RSBufferSlotItemHandler(slot));
        slotFluidHandlers.add(new RSBufferSlotFluidHandler(slot));
    }

    /**
     * Wakes the multiblock when a slot's contents change.
     *
     * <p>This is not optional and its absence does not look like a bug. A multiblock subscribes its
     * recipe logic to the tick through {@code RecipeHandlerList.subscribe}, and that subscription is
     * driven by the handlers' own listeners. A buffer that filled a slot without notifying would sit
     * there holding a perfectly good set of ingredients while the controller stayed {@code IDLE},
     * because nothing ever told it to look again — which is exactly what happened the first time this
     * part was put in a formed structure.
     */
    private void onSlotChanged() {
        aggregateItems.notifyListeners();
        aggregateFluids.notifyListeners();
    }

    /**
     * Republishes every pattern to the node.
     *
     * <p>Run whenever the pattern inventory changes and once on load. A slot holding something that
     * does not decode publishes null, which is how a pattern is withdrawn.
     */
    private void onPatternsChanged() {
        if (getLevel() == null || getLevel().isClientSide()) {
            return;
        }
        for (int slot = 0; slot < patternInventory.getSlots(); slot++) {
            ItemStack stack = patternInventory.getStackInSlot(slot);
            Pattern pattern = stack.isEmpty() ? null
                    : RefinedStorageApi.INSTANCE.getPattern(stack, getLevel()).orElse(null);
            patternNode.setPattern(slot, pattern);
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        // The slots were rebuilt by the constructor, but against the array as it was *before* NBT
        // was read back into it, so the live list and its handlers have to be pointed at the
        // restored objects. Cheap, and it keeps the two in step whatever the save system does.
        rebuildSlots();
        if (getLevel() != null && !getLevel().isClientSide()) {
            onPatternsChanged();
        }
    }

    private void rebuildSlots() {
        int wanted = Math.max(1, Math.min(proxies.size() + 1, internalSlots.length));
        slots.clear();
        slotItemHandlers.clear();
        slotFluidHandlers.clear();
        for (int i = 0; i < wanted; i++) {
            addSlot();
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Only the buffer's own list, so the vestigial bus inventory is never offered to a recipe.
     */
    @Override
    public List<RecipeHandlerList> getRecipeHandlers() {
        return bufferHandlers;
    }

    @Override
    public RSNodeHolder getNodeHolder() {
        return nodeHolder;
    }

    @Override
    public boolean isOnline() {
        return isOnline;
    }

    public CustomItemStackHandler getPatternInventory() {
        return patternInventory;
    }

    public List<RSBufferSlot> getSlots() {
        return slots;
    }

    public RSBufferRecipeHandlerList getBufferHandler() {
        return bufferHandler;
    }

    /**
     * Registers a proxy and gives the buffer a slot for it.
     *
     * <p>Idempotent, because a proxy re-resolves its buffer on every load and on every relink.
     */
    public void addProxy(BlockPos pos) {
        if (proxies.add(pos)) {
            syncSlotCount();
        }
    }

    public void removeProxy(BlockPos pos) {
        if (proxies.remove(pos)) {
            syncSlotCount();
        }
    }

    public Set<BlockPos> getProxies() {
        return proxies;
    }

    /**
     * Brings the live slot count in line with the number of proxies.
     *
     * <p>One slot for the buffer itself plus one per proxy, capped at the pattern count. Slots are
     * only ever added or dropped from the end, and a dropped slot's contents go back to the network
     * rather than vanishing with it.
     */
    private void syncSlotCount() {
        int target = Math.max(1, Math.min(proxies.size() + 1, internalSlots.length));
        while (slots.size() < target) {
            addSlot();
        }
        while (slots.size() > target) {
            removeLastSlot();
        }
    }

    private void removeLastSlot() {
        int last = slots.size() - 1;
        if (last <= 0) {
            return;
        }
        RSBufferSlot slot = slots.get(last);
        if (!slot.isEmpty()) {
            StorageNetworkComponent storage = nodeHolder.storage();
            if (storage != null) {
                slot.returnToNetwork(storage);
            }
        }
        slots.remove(last);
        slotItemHandlers.remove(last);
        slotFluidHandlers.remove(last);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Replaced rather than extended, for the reason the buses give: a plain bus pushes into
     * whatever it faces, and a part that moves things only between the network and the multiblock
     * should not quietly feed the chest behind it.
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

    @Override
    protected void updateInventorySubscription(Direction newFacing) {
        updateInventorySubscription();
    }

    /**
     * {@inheritDoc}
     *
     * <p>{@code doWork} every tick, not on the buses' twenty-tick sync interval. The buses are
     * keeping stock in line with a network and can afford to be late; this is driving Refined
     * Storage's own task stepping, and slowing it down would slow every craft that runs through the
     * part.
     */
    @Override
    protected void autoIO() {
        if (getLevel() == null || getLevel().isClientSide() || !isWorkingEnabled()) {
            return;
        }
        nodeHolder.refreshActive();
        boolean online = nodeHolder.isOnline();
        if (online != isOnline) {
            isOnline = online;
        }
        if (!online) {
            return;
        }
        patternNode.doWork();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Gives every slot's contents back to the network, for the reason the buses record: a slot's
     * amount is a {@code long} and dropping it as entities would be a far worse problem than the
     * loss. What an offline or full network refuses is still lost with the block, which is the same
     * open gap the buses have.
     */
    @Override
    public void onMachineDestroyed() {
        super.onMachineDestroyed();
        StorageNetworkComponent storage = nodeHolder.storage();
        if (storage == null) {
            return;
        }
        for (RSBufferSlot slot : slots) {
            slot.returnToNetwork(storage);
        }
    }
}
