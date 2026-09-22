package com.wraithhawit.gttweaks.rs.slot;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

/**
 * The inventory a Refined Storage item bus presents to GregTech: a fixed run of configured slots.
 *
 * <p>Stands in the place a normal bus's {@code NotifiableItemStackHandler} would, so multiblock
 * recipe logic reads it without knowing anything about Refined Storage. Everything that would put
 * items <em>in</em> is refused — {@code insertItem} returns what it was given and
 * {@code setStackInSlot} does nothing — because the only legitimate source of stock is the network
 * sync in the machine. A hopper or a pipe pointed at this part must not be able to fill it.
 *
 * <p>Structurally this is GregTech's {@code ExportOnlyAEItemList} with Refined Storage's resources
 * underneath, including the delegate handler: {@code handleRecipe} wants a
 * {@link CustomItemStackHandler}, and the slots are separate objects rather than one backing array.
 */
public class RSConfiguredItemList extends NotifiableItemStackHandler implements RSConfigurableSlotList {

    @SaveField
    protected RSConfiguredItemSlot[] inventory;

    @Nullable
    private CustomItemStackHandler itemHandler;

    public RSConfiguredItemList(int slots) {
        this(slots, RSConfiguredItemSlot::new);
    }

    public RSConfiguredItemList(int slots, Supplier<RSConfiguredItemSlot> slotFactory) {
        super(0, IO.IN, IO.NONE);
        this.inventory = new RSConfiguredItemSlot[slots];
        for (int i = 0; i < slots; i++) {
            this.inventory[i] = slotFactory.get();
        }
        for (RSConfiguredItemSlot slot : this.inventory) {
            slot.setOnContentsChanged(this::onContentsChanged);
        }
    }

    public RSConfiguredItemSlot[] getInventory() {
        return inventory;
    }

    @Override
    public int getConfigurableSlotCount() {
        return inventory.length;
    }

    @Override
    public RSConfiguredSlot getConfigurableSlot(int index) {
        return inventory[index];
    }

    @Override
    public boolean isStocking() {
        return inventory.length > 0 && inventory[0] instanceof RSStockingItemSlot;
    }

    public CustomItemStackHandler getHandler() {
        if (itemHandler == null) {
            itemHandler = new SlotDelegate(inventory);
        }
        return itemHandler;
    }

    @Override
    public int getSlots() {
        return inventory.length;
    }

    @Override
    public int getSlotLimit(int slot) {
        return Integer.MAX_VALUE;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        if (slot < 0 || slot >= inventory.length) {
            return ItemStack.EMPTY;
        }
        return inventory[slot].getStackInSlot(0);
    }

    @Override
    public void setStackInSlot(int slot, ItemStack stack) {
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        return stack;
    }

    @Override
    public ItemStack extractItemInternal(int slot, int amount, boolean simulate) {
        if (slot < 0 || slot >= inventory.length) {
            return ItemStack.EMPTY;
        }
        return inventory[slot].extractItem(0, amount, simulate);
    }

    @Override
    public List<SizedIngredient> handleRecipeInner(IO io, GTRecipe recipe, List<SizedIngredient> left,
                                                   boolean simulate) {
        return NotifiableItemStackHandler.handleRecipe(io, recipe, left, simulate, handlerIO, getHandler(),
                getMachine());
    }

    /**
     * The view GregTech's recipe handling expects, over slots that are objects rather than an array
     * of stacks.
     */
    private static class SlotDelegate extends CustomItemStackHandler {

        private final RSConfiguredItemSlot[] inventory;

        SlotDelegate(RSConfiguredItemSlot[] inventory) {
            this.inventory = inventory;
        }

        @Override
        public int getSlots() {
            return inventory.length;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return inventory[slot].getStackInSlot(0);
        }

        @Override
        public void setStackInSlot(int slot, ItemStack stack) {
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (amount == 0) {
                return ItemStack.EMPTY;
            }
            validateSlotIndex(slot);
            return inventory[slot].extractItem(0, amount, simulate);
        }

        @Override
        protected void validateSlotIndex(int slot) {
            if (slot < 0 || slot >= getSlots()) {
                throw new RuntimeException("Slot " + slot + " not in valid range - [0," + getSlots() + ")");
            }
        }

        @Override
        public int getSlotLimit(int slot) {
            return Integer.MAX_VALUE;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return false;
        }
    }
}
