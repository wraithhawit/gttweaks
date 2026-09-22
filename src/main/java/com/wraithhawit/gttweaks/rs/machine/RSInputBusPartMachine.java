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
import com.wraithhawit.gttweaks.rs.slot.RSConfiguredItemList;
import com.wraithhawit.gttweaks.rs.slot.RSConfiguredItemSlot;

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
 * Keeps a multiblock supplied with items from a Refined Storage network.
 *
 * <p>Sixteen slots, each with a resource and an amount the player configured. Every sync the part
 * tops each slot back up to its amount and hands back anything it should no longer be holding. The
 * multiblock then draws from those slots as it would from an ordinary input bus, which is the whole
 * trick: nothing in GregTech's recipe logic knows a network is involved.
 *
 * <p>This is GregTech's {@code MEInputBusPartMachine} with Refined Storage underneath, and it
 * departs from it in one place on purpose. When the ME bus has excess to return and the network
 * refuses it, the ME bus <em>deletes</em> the excess to free the slot. Here the stock stays put and
 * the slot retries on the next sync: a full or disconnected network should stall a bus, not eat what
 * it is holding. The cost is that a slot whose configuration changed while the network was full
 * keeps its old contents until there is room.
 */
public class RSInputBusPartMachine extends RSBusPartMachine {

    public static final int CONFIG_SIZE = 16;

    protected final RSConfiguredItemList itemHandler;

    public RSInputBusPartMachine(BlockEntityCreationInfo info) {
        this(info, new RSConfiguredItemList(CONFIG_SIZE));
    }

    protected RSInputBusPartMachine(BlockEntityCreationInfo info, RSConfiguredItemList inventory) {
        super(info, IO.IN, inventory);
        this.itemHandler = inventory;
    }

    public RSConfiguredItemList getItemHandler() {
        return itemHandler;
    }

    @Override
    protected void syncNetwork() {
        StorageNetworkComponent storage = nodeHolder.storage();
        if (storage == null) {
            return;
        }
        for (RSConfiguredItemSlot slot : itemHandler.getInventory()) {
            returnExcess(storage, slot);
            requestConfigured(storage, slot);
        }
    }

    private void returnExcess(StorageNetworkComponent storage, RSConfiguredItemSlot slot) {
        ResourceAmount excess = slot.exceedStack();
        if (excess == null) {
            return;
        }
        long inserted = storage.insert(excess.resource(), excess.amount(), Action.EXECUTE, RSPartActor.INSTANCE);
        // Exactly what the network took, in the resource's own units. See RSConfiguredSlot.debitStock
        // for why this must not go through the item handler.
        slot.debitStock(inserted);
    }

    private void requestConfigured(StorageNetworkComponent storage, RSConfiguredItemSlot slot) {
        ResourceAmount wanted = slot.requestStack();
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
     * the part was not connected stays in the slots and is lost with the block, the same as
     * GregTech's ME bus. Worth revisiting once the parts drop their contents properly.
     */
    @Override
    public void onMachineDestroyed() {
        super.onMachineDestroyed();
        flushStockToNetwork();
    }

    /**
     * Gives the network back everything the slots are holding.
     *
     * <p>Split out from {@link #onMachineDestroyed()} because the stocking bus must <em>not</em> do
     * it: its stock is a mirror of what the network already has rather than material this part is
     * holding, so handing it over would create items. That subclass overrides this to do nothing,
     * which is the same split GregTech makes with {@code flushInventory()}.
     */
    protected void flushStockToNetwork() {
        StorageNetworkComponent storage = nodeHolder.storage();
        if (storage == null) {
            return;
        }
        for (RSConfiguredItemSlot slot : itemHandler.getInventory()) {
            ResourceAmount stock = slot.getStock();
            if (stock == null) {
                continue;
            }
            long inserted = storage.insert(stock.resource(), stock.amount(), Action.EXECUTE, RSPartActor.INSTANCE);
            slot.debitStock(inserted);
        }
    }

    /**
     * The configuration screen: the slot grid, and whether the part is on the network at all.
     *
     * <p>{@link RSConfigWidget} is the whole of it. Everything the player can do to a slot is
     * registered here as a synced action and applied on the server by {@link RSConfigActions}, so
     * the widget never writes to the slots it draws.
     *
     * <p>The online line is worth the two rows it costs. A part that has not joined a network looks
     * exactly like one whose slots are simply empty, and before this there was no way to tell them
     * apart without a command.
     */
    @Override
    public void buildMainUI(ParentWidget<?> mainPanel, PosGuiData data, PanelSyncManager syncManager,
                            UISettings settings) {
        BooleanSyncValue online = new BooleanSyncValue(this::isOnline);
        syncManager.syncValue("rs_is_online", online);
        RSConfigActions.register(syncManager, itemHandler, false, this::updateInventorySubscription);

        Flow column = (Flow) Flow.col().coverChildren();
        column.child((IWidget) ((TextWidget) Text
                .dynamic(() -> Component.translatable(online.getBoolValue()
                        ? "gttweaks.rs.part.online"
                        : "gttweaks.rs.part.offline"))
                .asWidget()
                .marginTop(2))
                .marginBottom(4));
        column.child((IWidget) new RSConfigWidget(itemHandler, CONFIG_SIZE, false)
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
        RSStockComponent.collectInto(builder, itemHandler);
    }

    @Override
    protected void applyImplicitComponents(BlockEntity.DataComponentInput input) {
        super.applyImplicitComponents(input);
        RSStockComponent.applyFrom(input.get(RSStockComponent.HELD_STOCK.get()), itemHandler);
    }
}
