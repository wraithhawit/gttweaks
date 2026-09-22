package com.wraithhawit.gttweaks.rs.machine;

import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.recipe.IO;

import com.refinedmods.refinedstorage.api.core.Action;
import com.refinedmods.refinedstorage.api.network.storage.StorageNetworkComponent;
import com.refinedmods.refinedstorage.api.resource.ResourceAmount;

import com.wraithhawit.gttweaks.rs.gui.RSConfigActions;
import com.wraithhawit.gttweaks.rs.gui.RSConfigWidget;
import com.wraithhawit.gttweaks.rs.network.RSPartActor;
import com.wraithhawit.gttweaks.rs.slot.RSStockComponent;
import com.wraithhawit.gttweaks.rs.slot.RSConfiguredFluidList;
import com.wraithhawit.gttweaks.rs.slot.RSConfiguredFluidSlot;
import com.wraithhawit.gttweaks.rs.slot.RSConfiguredSlot;

import brachy.modularui.api.drawable.Text;
import brachy.modularui.api.widget.IWidget;
import brachy.modularui.factory.PosGuiData;
import brachy.modularui.screen.UISettings;
import brachy.modularui.value.sync.BooleanSyncValue;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.widget.ParentWidget;
import brachy.modularui.widgets.TextWidget;
import brachy.modularui.widgets.layout.Flow;

import net.minecraft.network.chat.Component;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;

/**
 * Keeps a multiblock supplied with fluids from a Refined Storage network.
 *
 * <p>The fluid twin of {@link RSInputBusPartMachine}, slot for slot: each of the eight slots holds a
 * configured resource and an amount, every sync tops it back up and hands back anything it should no
 * longer be holding, and the multiblock drains it as it would an ordinary input hatch.
 *
 * <p>The same departure from GregTech's ME parts applies. When the network refuses the excess a slot
 * is handing back, the fluid stays and the slot retries on the next sync rather than being deleted
 * to free the slot.
 */
public class RSInputHatchPartMachine extends RSHatchPartMachine {

    /**
     * Eight, where the item bus has sixteen. GregTech's ME input hatch is the same shape, and a
     * multiblock asking for more than eight distinct fluids at once does not exist in this pack.
     */
    public static final int CONFIG_SIZE = 8;

    protected final RSConfiguredFluidList fluidHandler;

    public RSInputHatchPartMachine(BlockEntityCreationInfo info) {
        this(info, new RSConfiguredFluidList(CONFIG_SIZE));
    }

    protected RSInputHatchPartMachine(BlockEntityCreationInfo info, RSConfiguredFluidList tank) {
        super(info, IO.IN, tank);
        this.fluidHandler = tank;
    }

    public RSConfiguredFluidList getFluidHandler() {
        return fluidHandler;
    }

    @Override
    protected void syncNetwork() {
        StorageNetworkComponent storage = nodeHolder.storage();
        if (storage == null) {
            return;
        }
        for (RSConfiguredFluidSlot slot : fluidHandler.getInventory()) {
            returnExcess(storage, slot);
            requestConfigured(storage, slot);
        }
    }

    private void returnExcess(StorageNetworkComponent storage, RSConfiguredFluidSlot slot) {
        RSConfiguredSlot configured = slot.slot();
        ResourceAmount excess = configured.exceedStack();
        if (excess == null) {
            return;
        }
        long inserted = storage.insert(excess.resource(), excess.amount(), Action.EXECUTE, RSPartActor.INSTANCE);
        // Exactly what the network took, in the resource's own units. See RSConfiguredSlot.debitStock
        // for why this must not go through the tank.
        configured.debitStock(inserted);
    }

    private void requestConfigured(StorageNetworkComponent storage, RSConfiguredFluidSlot slot) {
        ResourceAmount wanted = slot.slot().requestStack();
        if (wanted == null) {
            return;
        }
        long extracted = storage.extract(wanted.resource(), wanted.amount(), Action.EXECUTE, RSPartActor.INSTANCE);
        if (extracted > 0L) {
            slot.addStock(new ResourceAmount(wanted.resource(), extracted));
        }
    }

    /**
     * Hands the held stock back to the network when the part is broken.
     *
     * <p>Best effort, and only what the network will take: whatever is left when storage is full or
     * the part was not connected stays in the slots and is lost with the block, the same as the
     * input bus and as GregTech's ME parts.
     */
    @Override
    public void onMachineDestroyed() {
        super.onMachineDestroyed();
        flushStockToNetwork();
    }

    /**
     * Gives the network back everything the slots are holding.
     *
     * <p>Split out from {@link #onMachineDestroyed()} because the stocking hatch must <em>not</em>
     * do it: its stock is a mirror of what the network already has rather than fluid this part is
     * holding, so handing it over would create fluid.
     */
    protected void flushStockToNetwork() {
        StorageNetworkComponent storage = nodeHolder.storage();
        if (storage == null) {
            return;
        }
        for (RSConfiguredFluidSlot slot : fluidHandler.getInventory()) {
            RSConfiguredSlot configured = slot.slot();
            ResourceAmount stock = configured.getStock();
            if (stock == null) {
                continue;
            }
            long inserted = storage.insert(stock.resource(), stock.amount(), Action.EXECUTE, RSPartActor.INSTANCE);
            configured.debitStock(inserted);
        }
    }

    /**
     * The configuration screen, the fluid twin of the input bus's.
     *
     * <p>Same widget, same five synced actions; only the resource kind differs, and that is the one
     * flag {@link RSConfigActions#register} takes. A fluid cannot be dropped onto a slot directly,
     * so a configured fluid comes from the container the player is holding or drags in — a water
     * bucket configures 1000 mB of water.
     */
    @Override
    public void buildMainUI(ParentWidget<?> mainPanel, PosGuiData data, PanelSyncManager syncManager,
                            UISettings settings) {
        BooleanSyncValue online = new BooleanSyncValue(this::isOnline);
        syncManager.syncValue("rs_is_online", online);
        RSConfigActions.register(syncManager, fluidHandler, true, this::updateTankSubscription);

        Flow column = (Flow) Flow.col().coverChildren();
        column.child((IWidget) ((TextWidget) Text
                .dynamic(() -> Component.translatable(online.getBoolValue()
                        ? "gttweaks.rs.part.online"
                        : "gttweaks.rs.part.offline"))
                .asWidget()
                .marginTop(2))
                .marginBottom(4));
        column.child((IWidget) new RSConfigWidget(fluidHandler, CONFIG_SIZE, true)
                .syncManager(syncManager)
                .size(RSConfigWidget.widthFor(CONFIG_SIZE), RSConfigWidget.heightFor(CONFIG_SIZE)));
        mainPanel.child((IWidget) column.center());
    }

    /**
     * Hands the stock back to the network at the moment the part is being broken.
     *
     * <p>The flush in {@link #onMachineDestroyed()} is too late to help the dropped item, because
     * GregTech builds the drops first: {@code MetaMachineBlock.getDrops} calls this and only then
     * {@code saveToItem}, which reaches {@link #collectImplicitComponents}. Flushing here is what
     * makes the ordering right — the network gets everything it will take, and only what it refused
     * is left for the item to carry.
     *
     * <p>Harmless if {@code getDrops} is ever called without the block actually being broken: the
     * slots are emptied into the network and the next sync pulls the same amount back.
     */
    @Override
    public void modifyDrops(List<ItemStack> drops) {
        super.modifyDrops(drops);
        flushStockToNetwork();
    }

    /**
     * Carries whatever the network would not take onto the dropped machine item.
     *
     * <p>See {@link RSStockComponent} for why it rides on the item rather than being dropped into
     * the world or simply lost, and why a stocking part is excluded.
     */
    @Override
    public void collectImplicitComponents(DataComponentMap.Builder builder) {
        super.collectImplicitComponents(builder);
        RSStockComponent.collectInto(builder, fluidHandler);
    }

    @Override
    protected void applyImplicitComponents(BlockEntity.DataComponentInput input) {
        super.applyImplicitComponents(input);
        RSStockComponent.applyFrom(input.get(RSStockComponent.HELD_STOCK.get()), fluidHandler);
    }
}
