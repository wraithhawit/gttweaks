package com.wraithhawit.gttweaks.rs.slot;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;

import com.refinedmods.refinedstorage.api.resource.ResourceAmount;
import com.refinedmods.refinedstorage.common.support.resource.ResourceCodecs;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.neoforged.neoforge.common.util.INBTSerializable;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.Optional;

/**
 * One slot of a Refined Storage bus or hatch: what the player asked for, and what is here now.
 *
 * <p>This is GregTech's {@code ExportOnlyAESlot} re-expressed over Refined Storage's resources. The
 * pair is the whole idea — {@code config} is the standing order, {@code stock} is the part of it
 * currently held in the part — and {@link #requestStack()} and {@link #exceedStack()} are what turn
 * that pair into one pull from, or one push back to, the network each tick.
 *
 * <p>One thing does not carry over. AE2's {@code GenericStack} is happy to hold zero, and GregTech's
 * version leans on that: when stock already equals config it asks for a stack of zero and lets the
 * network no-op. {@link ResourceAmount} rejects a non-positive amount in its constructor, so the
 * "nothing to do" cases return null here instead, and the callers must treat null as "skip".
 */
public abstract class RSConfiguredSlot implements INBTSerializable<CompoundTag> {

    protected static final String CONFIG_TAG = "config";
    protected static final String STOCK_TAG = "stock";

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Refined Storage's own codec for an optional resource-and-amount, so a configured item keeps
     * its data components across a save without this mod knowing what components are.
     */
    private static final Codec<Optional<ResourceAmount>> CODEC = ResourceCodecs.AMOUNT_OPTIONAL_CODEC;

    protected Runnable onContentsChanged = () -> {
    };

    @Nullable
    protected ResourceAmount config;

    @Nullable
    protected ResourceAmount stock;

    protected RSConfiguredSlot() {
        this(null, null);
    }

    protected RSConfiguredSlot(@Nullable ResourceAmount config, @Nullable ResourceAmount stock) {
        this.config = config;
        this.stock = stock;
    }

    /**
     * What this slot wants pulled out of the network, or null if it wants nothing.
     */
    @Nullable
    public ResourceAmount requestStack() {
        if (config == null) {
            return null;
        }
        if (stock == null) {
            return copy(config);
        }
        if (!config.resource().equals(stock.resource())) {
            // Holding something else entirely. exceedStack() clears it out first; asking for the
            // configured resource on top of it would mix two resources into one slot.
            return null;
        }
        long missing = config.amount() - stock.amount();
        return missing > 0L ? copy(config, missing) : null;
    }

    /**
     * What this slot wants pushed back into the network, or null if it holds nothing it should not.
     *
     * <p>Covers both ways a slot can end up over-stocked: the player cleared or retargeted the
     * configuration while stock was held, and the configured amount was lowered.
     */
    @Nullable
    public ResourceAmount exceedStack() {
        if (stock == null) {
            return null;
        }
        if (config == null || !config.resource().equals(stock.resource())) {
            return copy(stock);
        }
        long excess = stock.amount() - config.amount();
        return excess > 0L ? copy(stock, excess) : null;
    }

    /**
     * Adds what the network just handed over to whatever this slot already holds.
     */
    protected abstract void addStack(ResourceAmount stack);

    /**
     * Removes exactly {@code amount} from the stock, in the resource's own units.
     *
     * <p>The counterpart to a network insert, and the only correct way to account for one. The
     * obvious alternative — debiting through the item handler's {@code extractItem} — is wrong twice,
     * and the first version of the bus was wrong both ways: that API takes an {@code int}, so an
     * insert above {@link Integer#MAX_VALUE} credited the network more than it debited the slot and
     * <em>created</em> items; and it refuses any resource that is not an item, so a fluid that
     * reached the slot would be inserted into the network over and over while the stock never went
     * down. Network accounting is in {@code long}s and does not care what kind of resource it moved,
     * so this is too.
     */
    public void debitStock(long amount) {
        if (stock == null || amount <= 0L) {
            return;
        }
        long left = stock.amount() - amount;
        stock = left > 0L ? copy(stock, left) : null;
        onContentsChanged.run();
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        RegistryOps<Tag> ops = provider.createSerializationContext(NbtOps.INSTANCE);
        write(ops, tag, CONFIG_TAG, config);
        write(ops, tag, STOCK_TAG, stock);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        RegistryOps<Tag> ops = provider.createSerializationContext(NbtOps.INSTANCE);
        config = read(ops, tag, CONFIG_TAG);
        stock = read(ops, tag, STOCK_TAG);
    }

    private static void write(RegistryOps<Tag> ops, CompoundTag tag, String key,
                              @Nullable ResourceAmount amount) {
        if (amount == null) {
            return;
        }
        CODEC.encodeStart(ops, Optional.of(amount))
                .resultOrPartial(error -> LOGGER.error("[gttweaks] could not save {}: {}", key, error))
                .ifPresent(written -> tag.put(key, written));
    }

    @Nullable
    private static ResourceAmount read(RegistryOps<Tag> ops, CompoundTag tag, String key) {
        if (!tag.contains(key)) {
            return null;
        }
        return CODEC.parse(ops, tag.get(key))
                .resultOrPartial(error -> LOGGER.error("[gttweaks] could not load {}: {}", key, error))
                .flatMap(read -> read)
                .orElse(null);
    }

    public static ResourceAmount copy(ResourceAmount stack) {
        return new ResourceAmount(stack.resource(), stack.amount());
    }

    public static ResourceAmount copy(ResourceAmount stack, long amount) {
        return new ResourceAmount(stack.resource(), amount);
    }

    @Nullable
    public ResourceAmount getConfig() {
        return config;
    }

    public void setConfig(@Nullable ResourceAmount config) {
        this.config = config;
        onContentsChanged.run();
    }

    @Nullable
    public ResourceAmount getStock() {
        return stock;
    }

    public void setStock(@Nullable ResourceAmount stock) {
        this.stock = stock;
        onContentsChanged.run();
    }

    public void setOnContentsChanged(Runnable onContentsChanged) {
        this.onContentsChanged = onContentsChanged;
    }

    public Runnable getOnContentsChanged() {
        return onContentsChanged;
    }
}
