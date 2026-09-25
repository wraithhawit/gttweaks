package com.wraithhawit.gttweaks.rs.machine;

import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.recipe.IO;

import com.refinedmods.refinedstorage.api.core.Action;
import com.refinedmods.refinedstorage.api.network.storage.StorageNetworkComponent;

import com.wraithhawit.gttweaks.rs.gui.RSOutputQueueWidget;
import com.wraithhawit.gttweaks.rs.network.RSPartActor;
import com.wraithhawit.gttweaks.rs.slot.RSOutputBuffer;

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

/**
 * Empties a multiblock's item results into a Refined Storage network.
 *
 * <p>The mirror image of {@link RSInputBusPartMachine}, and much the simpler of the two: there is
 * nothing to configure. The multiblock writes its outputs into the bus, and every sync this hands
 * whatever is waiting to the network.
 *
 * <p>Like GregTech's {@code MEOutputBusPartMachine}, the bus holds its outputs in an unbounded
 * {@link RSOutputBuffer} rather than slots. An earlier version used sixteen ordinary slots so that a
 * full or offline network would stall the line, but that also made any recipe producing more than
 * 1,024 items at once impossible to start — the multiblock reported "insufficient outputs" with an
 * empty bus. The trade is GregTech's: a network that cannot take the items no longer stops the
 * multiblock, and what it refuses waits in the bus, saved with it, until it can.
 */
public class RSOutputBusPartMachine extends RSBusPartMachine {

    public RSOutputBusPartMachine(BlockEntityCreationInfo info) {
        super(info, IO.OUT, RSOutputBuffer.createHandler());
    }

    private RSOutputBuffer buffer() {
        return (RSOutputBuffer) getInventory().storage;
    }

    /**
     * Hands the buffer to the network, keeping whatever the network refuses.
     *
     * <p>Each resource is accounted for by what the network actually accepted, never by what it was
     * offered: a partial insert has to leave the remainder in the buffer, or the difference is items
     * that existed a moment ago and now do not.
     */
    @Override
    protected void syncNetwork() {
        StorageNetworkComponent storage = nodeHolder.storage();
        if (storage == null) {
            return;
        }
        flushTo(storage);
    }

    private void flushTo(StorageNetworkComponent storage) {
        buffer().drainTo((resource, amount) ->
                storage.insert(resource, amount, Action.EXECUTE, RSPartActor.INSTANCE));
    }

    /**
     * Flushes the buffer on the way out, so breaking a bus that still holds results does not lose
     * them to a network that could have taken them.
     *
     * <p>Only best effort: what a full or disconnected network will not take is lost with the bus,
     * as it is with GregTech's ME Output Bus.
     */
    @Override
    public void onMachineDestroyed() {
        super.onMachineDestroyed();
        StorageNetworkComponent storage = nodeHolder.storage();
        if (storage == null) {
            return;
        }
        flushTo(storage);
    }

    /**
     * The screen: whether the bus is on a network, and what is waiting to be sent.
     *
     * <p>GregTech's item bus screen cannot be reused: it draws one slot per slot its tier would have,
     * 49 at this tier, and building it against this bus threw "invalid index 16" and opened nothing.
     * This follows GregTech's ME Output Bus instead.
     */
    @Override
    public void buildMainUI(ParentWidget<?> mainPanel, PosGuiData data, PanelSyncManager syncManager,
                            UISettings settings) {
        BooleanSyncValue online = new BooleanSyncValue(this::isOnline);
        syncManager.syncValue("rs_is_online", online);

        Flow column = (Flow) Flow.col().coverChildren();
        column.child((IWidget) ((TextWidget) Text
                .dynamic(() -> Component.translatable(online.getBoolValue()
                        ? "gttweaks.rs.part.online"
                        : "gttweaks.rs.part.offline"))
                .asWidget()
                .marginTop(2))
                .marginBottom(2));
        column.child((IWidget) ((TextWidget) Text
                .dynamic(() -> Component.translatable("gttweaks.rs.output.queue"))
                .asWidget())
                .marginBottom(2));
        column.child((IWidget) new RSOutputQueueWidget(this::buffer)
                .syncManager(syncManager)
                .size(RSOutputQueueWidget.WIDTH, RSOutputQueueWidget.HEIGHT));
        mainPanel.child((IWidget) column.center());
    }
}
