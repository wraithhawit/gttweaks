package com.wraithhawit.gttweaks.rs.slot;

import com.refinedmods.refinedstorage.api.core.Action;
import com.refinedmods.refinedstorage.api.network.storage.StorageNetworkComponent;
import com.refinedmods.refinedstorage.api.resource.ResourceAmount;
import com.refinedmods.refinedstorage.common.support.resource.ItemResource;

import com.wraithhawit.gttweaks.rs.network.RSNetworkPart;
import com.wraithhawit.gttweaks.rs.network.RSPartActor;

import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

/**
 * A slot that shows what the network holds without holding any of it.
 *
 * <p>The difference between a stocking bus and an input bus, in one class. An input bus pulls its
 * configured amount out of the network and keeps it; this reports the network's own contents as
 * though they were in the slot, and only takes them at the moment a recipe actually consumes them.
 * So {@code stock} here is a <em>mirror</em>, refreshed every sync, and never material this part
 * owns.
 *
 * <p>That makes {@link #extractItem} the one place the network is debited, and the {@code simulate}
 * flag is passed straight through to Refined Storage. GregTech's recipe logic runs a simulated pass
 * before a real one, and a stocking part that ignored the flag would consume the ingredients of
 * every recipe it merely considered.
 *
 * <p>This mirrors GregTech's {@code ExportOnlyAEStockingItemSlot} closely, including the detail that
 * matters most: the extraction asks for the <em>configured</em> resource, not the mirrored stock's,
 * so a mirror that has gone stale between syncs cannot authorise taking something else.
 */
public class RSStockingItemSlot extends RSConfiguredItemSlot {

    @Nullable
    private RSNetworkPart part;

    public RSStockingItemSlot() {
        super();
    }

    public RSStockingItemSlot(@Nullable ResourceAmount config, @Nullable ResourceAmount stock) {
        super(config, stock);
    }

    /**
     * The part this slot belongs to, which is how it reaches the network at extraction time.
     *
     * <p>Set after construction because the slots are built by the inventory, which is built by the
     * machine's constructor — there is no moment during construction when both exist.
     */
    public void setPart(@Nullable RSNetworkPart part) {
        this.part = part;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Takes from the network rather than from this slot, and only as much as the network actually
     * gives. Returns empty rather than the mirrored amount whenever the part cannot reach a network,
     * so an offline bus stalls its multiblock instead of appearing to supply it.
     */
    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (slot != 0 || amount <= 0 || part == null) {
            return ItemStack.EMPTY;
        }
        ResourceAmount configured = getConfig();
        if (configured == null || getStock() == null || !part.isOnline()) {
            return ItemStack.EMPTY;
        }
        if (!(configured.resource() instanceof ItemResource item)) {
            return ItemStack.EMPTY;
        }
        StorageNetworkComponent storage = part.getNodeHolder().storage();
        if (storage == null) {
            return ItemStack.EMPTY;
        }
        long extracted = storage.extract(configured.resource(), amount,
                simulate ? Action.SIMULATE : Action.EXECUTE, RSPartActor.INSTANCE);
        if (extracted <= 0L) {
            return ItemStack.EMPTY;
        }
        if (!simulate) {
            // Keeps the mirror honest until the next sync refreshes it anyway. Asked for an int, so
            // the network cannot have given more than one.
            debitStock(extracted);
        }
        return item.toItemStack((int) extracted);
    }

    @Override
    public RSStockingItemSlot copy() {
        ResourceAmount config = getConfig();
        ResourceAmount stock = getStock();
        RSStockingItemSlot copied = new RSStockingItemSlot(
                config == null ? null : copy(config),
                stock == null ? null : copy(stock));
        copied.setPart(part);
        return copied;
    }
}
