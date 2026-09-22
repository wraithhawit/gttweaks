package com.wraithhawit.gttweaks.rs.gui;

import com.refinedmods.refinedstorage.api.resource.ResourceAmount;
import com.refinedmods.refinedstorage.api.resource.ResourceKey;
import com.refinedmods.refinedstorage.common.support.resource.ItemResource;

import com.wraithhawit.gttweaks.rs.slot.RSConfigurableSlotList;
import com.wraithhawit.gttweaks.rs.slot.RSConfiguredSlot;
import com.wraithhawit.gttweaks.rs.slot.RSFluids;

import brachy.modularui.value.sync.PanelSyncManager;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;

import org.jetbrains.annotations.Nullable;

/**
 * The server half of {@link RSConfigWidget}: everything a click on the grid is allowed to do.
 *
 * <p>Shared by the input bus, the input hatch and both stocking parts, since the widget is one
 * widget and the five actions it can send are the same five whatever kind of resource is underneath.
 * Only the way a resource is read off an item — {@link ItemResource} or the fluid a container holds
 * — differs, and that is what {@code isFluid} selects.
 *
 * <p>Every action re-checks what the widget already refused. The widget runs on a client and a
 * client can be made to send anything, so an index out of range, an amount of zero, and above all a
 * pickup or an amount change on a stocking part are all refused again here. The last of those is
 * the one that matters: a stocking slot's stock is a reading of the network, not material this part
 * holds, so handing it to a player would create items out of nothing.
 */
public final class RSConfigActions {

    private RSConfigActions() {
    }

    /**
     * Registers the five actions on a panel, against one part's slots.
     *
     * @param onChanged what to run after a slot is edited, so the machine re-reads its own
     *                  inventory — {@code updateInventorySubscription} on a bus,
     *                  {@code updateTankSubscription} on a hatch.
     */
    public static void register(PanelSyncManager syncManager, RSConfigurableSlotList slotList,
                                boolean isFluid, Runnable onChanged) {
        syncManager.registerServerSyncedAction(RSConfigWidget.ACTION_CONFIG_SET, buffer -> {
            RSConfiguredSlot slot = slotAt(slotList, buffer);
            if (slot == null) {
                return;
            }
            ItemStack carried = syncManager.getPlayer().containerMenu.getCarried();
            if (carried.isEmpty()) {
                return;
            }
            setConfig(slot, carried, isFluid);
            onChanged.run();
        });

        syncManager.registerServerSyncedAction(RSConfigWidget.ACTION_CONFIG_CLEAR, buffer -> {
            RSConfiguredSlot slot = slotAt(slotList, buffer);
            if (slot == null) {
                return;
            }
            slot.setConfig(null);
            onChanged.run();
        });

        syncManager.registerServerSyncedAction(RSConfigWidget.ACTION_CONFIG_AMOUNT, buffer -> {
            RSConfiguredSlot slot = slotAt(slotList, buffer);
            long amount = buffer.readVarLong();
            if (slot == null || slotList.isStocking() || amount <= 0L) {
                return;
            }
            ResourceAmount config = slot.getConfig();
            if (config == null) {
                return;
            }
            slot.setConfig(RSConfiguredSlot.copy(config, amount));
            onChanged.run();
        });

        syncManager.registerServerSyncedAction(RSConfigWidget.ACTION_STOCK_PICKUP, buffer -> {
            RSConfiguredSlot slot = slotAt(slotList, buffer);
            if (slot == null || slotList.isStocking() || isFluid) {
                return;
            }
            takeStock(slot, syncManager.getPlayer());
            onChanged.run();
        });

        syncManager.registerServerSyncedAction(RSConfigWidget.ACTION_CONFIG_SET_GHOST, buffer -> {
            RSConfiguredSlot slot = slotAt(slotList, buffer);
            if (isFluid) {
                FluidStack fluid = FluidStack.OPTIONAL_STREAM_CODEC.decode(buffer);
                if (slot == null || fluid.isEmpty()) {
                    return;
                }
                setConfig(slot, RSFluids.toResource(fluid), Math.max(fluid.getAmount(), 1));
            } else {
                ItemStack stack = ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer);
                if (slot == null || stack.isEmpty()) {
                    return;
                }
                setConfig(slot, ItemResource.ofItemStack(stack), Math.max(stack.getCount(), 1));
            }
            onChanged.run();
        });
    }

    /**
     * Reads a slot index off the packet and returns that slot, or null if it does not name one.
     *
     * <p>The index is always read, even when it turns out to be nonsense, because the actions that
     * carry more than an index read it from the same buffer straight afterwards.
     */
    @Nullable
    private static RSConfiguredSlot slotAt(RSConfigurableSlotList slotList,
                                           RegistryFriendlyByteBuf buffer) {
        int index = buffer.readVarInt();
        if (index < 0 || index >= slotList.getConfigurableSlotCount()) {
            return null;
        }
        return slotList.getConfigurableSlot(index);
    }

    /**
     * Configures a slot from an item on the cursor.
     *
     * <p>On a fluid part the item is a container and the fluid inside it is what gets configured, so
     * a water bucket asks for 1000 mB. Dropping an item that holds no fluid on a hatch does nothing
     * rather than configuring the bucket itself, which would be unfillable.
     */
    private static void setConfig(RSConfiguredSlot slot, ItemStack carried, boolean isFluid) {
        if (isFluid) {
            FluidUtil.getFluidContained(carried)
                    .filter(fluid -> !fluid.isEmpty())
                    .ifPresent(fluid -> setConfig(slot, RSFluids.toResource(fluid),
                            Math.max(fluid.getAmount(), 1)));
            return;
        }
        setConfig(slot, ItemResource.ofItemStack(carried), Math.max(carried.getCount(), 1));
    }

    private static void setConfig(RSConfiguredSlot slot, ResourceKey resource, long amount) {
        slot.setConfig(new ResourceAmount(resource, amount));
    }

    /**
     * Hands the player one stack of what a slot is holding, and debits the slot by exactly that.
     *
     * <p>Only onto an empty cursor, and only as much as the item stacks to, so a slot holding
     * hundreds of thousands gives them up one stack per click rather than as one impossible stack.
     * The debit goes through {@code debitStock}, which is the only accounting this part trusts — see
     * {@link RSConfiguredSlot#debitStock(long)} for why the item handler is the wrong way round.
     */
    private static void takeStock(RSConfiguredSlot slot, Player player) {
        ResourceAmount stock = slot.getStock();
        if (stock == null || !(stock.resource() instanceof ItemResource item)) {
            return;
        }
        if (!player.containerMenu.getCarried().isEmpty()) {
            return;
        }
        ItemStack stack = item.toItemStack();
        int taken = (int) Math.min(stock.amount(), stack.getMaxStackSize());
        if (taken <= 0) {
            return;
        }
        stack.setCount(taken);
        player.containerMenu.setCarried(stack);
        slot.debitStock(taken);
    }
}
