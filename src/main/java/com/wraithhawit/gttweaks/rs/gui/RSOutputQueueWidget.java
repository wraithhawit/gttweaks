package com.wraithhawit.gttweaks.rs.gui;

import com.gregtechceu.gtceu.common.mui.GTGuiTextures;
import com.gregtechceu.gtceu.integration.ae2.gui.AEGuiHelper;

import com.refinedmods.refinedstorage.api.resource.ResourceAmount;
import com.refinedmods.refinedstorage.api.resource.ResourceKey;
import com.refinedmods.refinedstorage.common.api.RefinedStorageClientApi;
import com.refinedmods.refinedstorage.common.api.support.resource.ResourceRendering;
import com.refinedmods.refinedstorage.common.support.resource.ResourceCodecs;

import com.wraithhawit.gttweaks.rs.slot.RSOutputBuffer;

import brachy.modularui.screen.viewport.ModularGuiContext;
import brachy.modularui.theme.WidgetThemeEntry;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.value.sync.SyncHandler;
import brachy.modularui.widget.Widget;

import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * A read-only view of what an RS Output Bus is waiting to send: GregTech's ME Output Bus "Sending
 * Queue", over Refined Storage's resources.
 *
 * <p>Shows the first {@link #CELLS} entries, oldest first, each with its amount; hovering one gives
 * the exact count. There is nothing to click — the buffer only empties into the network.
 */
public class RSOutputQueueWidget extends Widget<RSOutputQueueWidget> {

    private static final String SYNC_KEY = "rs_output_queue";
    private static final int CELL_SIZE = 18;
    private static final int COLUMNS = 8;
    private static final int ROWS = 3;
    /** How many entries the screen has room for; the rest still flush, they are just not drawn. */
    public static final int CELLS = COLUMNS * ROWS;
    public static final int WIDTH = COLUMNS * CELL_SIZE;
    public static final int HEIGHT = ROWS * CELL_SIZE;

    private final Supplier<RSOutputBuffer> buffer;
    @Nullable
    private QueueSyncHandler handler;

    public RSOutputQueueWidget(Supplier<RSOutputBuffer> buffer) {
        this.buffer = buffer;
    }

    public RSOutputQueueWidget syncManager(PanelSyncManager syncManager) {
        this.handler = new QueueSyncHandler(buffer);
        syncManager.syncValue(SYNC_KEY, handler);
        return this;
    }

    @Override
    public void draw(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
        List<ResourceAmount> shown = handler == null ? List.of() : handler.clientEntries;
        for (int i = 0; i < CELLS; i++) {
            int x = (i % COLUMNS) * CELL_SIZE;
            int y = (i / COLUMNS) * CELL_SIZE;
            GTGuiTextures.SLOT_DARK.draw(context, x, y, CELL_SIZE, CELL_SIZE);
            if (i < shown.size()) {
                ResourceAmount entry = shown.get(i);
                rendering(entry.resource()).render(entry.resource(), context.getGraphics(), x + 1, y + 1);
                AEGuiHelper.drawAmountOverlay(context.getGraphics(), entry.amount(), x + 1, y + 1);
            }
        }
    }

    @Override
    public void drawForeground(ModularGuiContext context) {
        if (handler == null) {
            return;
        }
        int mouseX = context.getMouseX();
        int mouseY = context.getMouseY();
        if (mouseX < 0 || mouseY < 0 || mouseX >= WIDTH || mouseY >= HEIGHT) {
            return;
        }
        int index = (mouseY / CELL_SIZE) * COLUMNS + mouseX / CELL_SIZE;
        if (index >= handler.clientEntries.size()) {
            return;
        }
        ResourceAmount entry = handler.clientEntries.get(index);
        List<Component> tooltip = new ArrayList<>(rendering(entry.resource()).getTooltip(entry.resource()));
        tooltip.add(Component.literal(AEGuiHelper.formatAmountFull(entry.amount())));
        context.getGraphics().renderComponentTooltip(Minecraft.getInstance().font, tooltip,
                context.getAbsMouseX(), context.getAbsMouseY());
    }

    private static ResourceRendering rendering(ResourceKey resource) {
        return RefinedStorageClientApi.INSTANCE.getResourceRendering(resource.getClass());
    }

    /**
     * Sends the first {@link #CELLS} buffer entries whenever they change.
     *
     * <p>The list is captured on the server thread and the packet writer only replays it: ModularUI
     * runs a writer at encode time, on a network thread, and can run it twice — see
     * {@link RSConfigSyncHandler#detectAndSendChanges} for the bug that caused.
     */
    private static final class QueueSyncHandler extends SyncHandler<QueueSyncHandler> {

        private static final int SYNC_ENTRIES = 1;

        private final Supplier<RSOutputBuffer> buffer;
        private List<ResourceAmount> lastSent = List.of();
        private List<ResourceAmount> clientEntries = List.of();

        private QueueSyncHandler(Supplier<RSOutputBuffer> buffer) {
            this.buffer = buffer;
        }

        @Override
        public void detectAndSendChanges(boolean init) {
            List<ResourceAmount> all = buffer.get().snapshot();
            List<ResourceAmount> now = List.copyOf(all.subList(0, Math.min(all.size(), CELLS)));
            if (!init && now.equals(lastSent)) {
                return;
            }
            lastSent = now;
            syncToClient(SYNC_ENTRIES, out -> {
                out.writeVarInt(now.size());
                for (ResourceAmount entry : now) {
                    ResourceCodecs.AMOUNT_STREAM_CODEC.encode(out, entry);
                }
            });
        }

        @Override
        public void readOnClient(int id, RegistryFriendlyByteBuf in) {
            if (id != SYNC_ENTRIES) {
                return;
            }
            int size = in.readVarInt();
            List<ResourceAmount> read = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                read.add(ResourceCodecs.AMOUNT_STREAM_CODEC.decode(in));
            }
            clientEntries = read;
        }

        @Override
        public void readOnServer(int id, RegistryFriendlyByteBuf in) {
        }
    }
}
