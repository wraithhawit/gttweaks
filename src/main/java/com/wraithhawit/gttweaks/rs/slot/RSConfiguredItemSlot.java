package com.wraithhawit.gttweaks.rs.slot;

import com.refinedmods.refinedstorage.api.resource.ResourceAmount;
import com.refinedmods.refinedstorage.common.support.resource.ItemResource;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

import org.jetbrains.annotations.Nullable;

/**
 * A configured slot that a GregTech recipe can read as if it were a one-slot inventory.
 *
 * <p>Read-only from the machine's side, deliberately: the recipe logic extracts what it consumes and
 * nothing may be put in. Stock arrives from exactly one direction, {@link #addStack}, which is the
 * network hand-off. That is what {@code insertItem} refusing everything and {@code setStackInSlot}
 * doing nothing are for — the same shape as GregTech's {@code ExportOnlyAEItemSlot}, for the same
 * reason.
 */
public class RSConfiguredItemSlot extends RSConfiguredSlot implements IItemHandlerModifiable {

    public RSConfiguredItemSlot() {
        super();
    }

    public RSConfiguredItemSlot(@Nullable ResourceAmount config, @Nullable ResourceAmount stock) {
        super(config, stock);
    }

    @Override
    protected void addStack(ResourceAmount stack) {
        if (stock == null) {
            stock = stack;
        } else if (stock.resource().equals(stack.resource())) {
            stock = copy(stock, stock.amount() + stack.amount());
        } else {
            // Cannot happen while requestStack() refuses to ask for a second resource, and is not
            // worth a silent merge if it ever does: dropping the old stock would delete items the
            // network still believes it handed over.
            return;
        }
        onContentsChanged.run();
    }

    /**
     * Adds an amount of the configured resource, for the caller that already knows what it asked for.
     */
    public void addStock(ResourceAmount stack) {
        addStack(stack);
    }

    @Override
    public int getSlots() {
        return 1;
    }

    @Override
    public void setStackInSlot(int slot, ItemStack stack) {
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        if (slot != 0 || stock == null) {
            return ItemStack.EMPTY;
        }
        if (!(stock.resource() instanceof ItemResource item)) {
            return ItemStack.EMPTY;
        }
        return item.toItemStack(stock.amount());
    }

    /**
     * {@inheritDoc}
     *
     * <p>{@link Integer#MAX_VALUE}, not the item's stack size: the slot holds one resource in an
     * amount the player configured, and a bus that could only stock 64 of anything would make the
     * amount field pointless.
     */
    @Override
    public int getSlotLimit(int slot) {
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return false;
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        return stack;
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (slot != 0 || stock == null || amount <= 0) {
            return ItemStack.EMPTY;
        }
        if (!(stock.resource() instanceof ItemResource item)) {
            return ItemStack.EMPTY;
        }
        int extracted = (int) Math.min(stock.amount(), amount);
        ItemStack result = item.toItemStack(extracted);
        if (!simulate) {
            long left = stock.amount() - extracted;
            stock = left > 0L ? copy(stock, left) : null;
            onContentsChanged.run();
        }
        return result;
    }

    public RSConfiguredItemSlot copy() {
        return new RSConfiguredItemSlot(
                config == null ? null : copy(config),
                stock == null ? null : copy(stock));
    }
}
