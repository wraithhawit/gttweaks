package com.wraithhawit.gttweaks.rs.gui;

import brachy.modularui.value.sync.SyncHandler;

import com.refinedmods.refinedstorage.api.resource.ResourceAmount;
import com.refinedmods.refinedstorage.common.support.resource.ResourceCodecs;

import com.wraithhawit.gttweaks.rs.slot.RSConfigurableSlotList;
import com.wraithhawit.gttweaks.rs.slot.RSConfiguredSlot;

import net.minecraft.network.RegistryFriendlyByteBuf;

import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Carries a configured part's slots to the client so the widget has something to draw.
 *
 * <p>Neither half of a slot is reachable from the client on its own: the configuration lives in the
 * part's saved data and the stock is written by the network sync, which only ever runs on the
 * server. This is GregTech's {@code AEConfigSyncHandler} over Refined Storage's resources.
 *
 * <p>Only the server writes. Every change the player makes travels the other way as a synced action
 * ({@code rs_config_set} and friends), is applied to the real slot, and comes back through here —
 * so the client never holds a configuration the server has not agreed to. The cost is one tick of
 * latency on a click, which is also what GregTech's parts do.
 *
 * <p>The wire format is one boolean per slot — did this slot change — followed, only for the slots
 * that did, by the configuration and the stock. Refined Storage's own
 * {@link ResourceCodecs#AMOUNT_STREAM_OPTIONAL_CODEC} writes those, so a configured item keeps its
 * data components across the wire exactly as it keeps them across a save, and this mod never has to
 * know what a component is.
 */
public class RSConfigSyncHandler extends SyncHandler<RSConfigSyncHandler> {

    /** The one packet id this handler uses. */
    private static final int SYNC_SLOTS = 1;

    private final RSConfigurableSlotList slotList;
    private final int slotCount;

    /** What the client was last told, so an unchanged slot costs one bit. */
    private final ResourceAmount[] cachedConfig;
    private final ResourceAmount[] cachedStock;

    @Nullable
    private ResourceAmount[] clientConfig;
    @Nullable
    private ResourceAmount[] clientStock;

    public RSConfigSyncHandler(RSConfigurableSlotList slotList, int slotCount) {
        this.slotList = slotList;
        this.slotCount = slotCount;
        this.cachedConfig = new ResourceAmount[slotCount];
        this.cachedStock = new ResourceAmount[slotCount];
    }

    public void initClient() {
        this.clientConfig = new ResourceAmount[slotCount];
        this.clientStock = new ResourceAmount[slotCount];
    }

    @Nullable
    public ResourceAmount getClientConfig(int index) {
        if (clientConfig == null || index < 0 || index >= clientConfig.length) {
            return null;
        }
        return clientConfig[index];
    }

    @Nullable
    public ResourceAmount getClientStock(int index) {
        if (clientStock == null || index < 0 || index >= clientStock.length) {
            return null;
        }
        return clientStock[index];
    }

    /**
     * Works out which slots changed, and fixes what the packet will say, before the packet exists.
     *
     * <p>The comparison and the cache update have to happen here, on the server thread, and not in
     * the writer handed to {@code syncToClient}. ModularUI does not run that writer when the packet
     * is sent: it runs it when the packet is encoded, on a network thread, and it can run it more
     * than once for one packet — seen in single player, where every packet's writer ran twice. A
     * writer that decided "changed" against the cache and then updated the cache wrote the change
     * the first time and "nothing changed" the second, and the client got the second, so the screen
     * only caught up when it was reopened and the forced full sync went out. GregTech's
     * {@code AEConfigSyncHandler} has the same shape and the same bug; {@code AEConfigSyncHandlerMixin}
     * fixes the ME parts the same way.
     *
     * <p>So the writer only replays a snapshot taken here, and running it twice writes the same
     * bytes twice. The snapshot also keeps the network thread from reading the live slots, which
     * the server thread is writing.
     */
    @Override
    public void detectAndSendChanges(boolean init) {
        boolean[] changed = new boolean[slotCount];
        ResourceAmount[] configs = new ResourceAmount[slotCount];
        ResourceAmount[] stocks = new ResourceAmount[slotCount];
        boolean anyChanged = false;
        for (int i = 0; i < slotCount; i++) {
            RSConfiguredSlot slot = slotList.getConfigurableSlot(i);
            ResourceAmount config = slot.getConfig();
            ResourceAmount stock = slot.getStock();
            if (init || !areEqual(config, cachedConfig[i]) || !areEqual(stock, cachedStock[i])) {
                changed[i] = true;
                anyChanged = true;
                configs[i] = copy(config);
                stocks[i] = copy(stock);
                cachedConfig[i] = copy(config);
                cachedStock[i] = copy(stock);
            }
        }
        if (!anyChanged) {
            return;
        }
        syncToClient(SYNC_SLOTS, buffer -> {
            for (int i = 0; i < changed.length; i++) {
                buffer.writeBoolean(changed[i]);
                if (changed[i]) {
                    write(buffer, configs[i]);
                    write(buffer, stocks[i]);
                }
            }
        });
    }

    @Override
    public void readOnClient(int id, RegistryFriendlyByteBuf buffer) {
        if (id != SYNC_SLOTS) {
            return;
        }
        if (clientConfig == null || clientStock == null) {
            initClient();
        }
        for (int i = 0; i < slotCount; i++) {
            if (!buffer.readBoolean()) {
                continue;
            }
            clientConfig[i] = read(buffer);
            clientStock[i] = read(buffer);
        }
    }

    @Override
    public void readOnServer(int id, RegistryFriendlyByteBuf buffer) {
        // Nothing: every client-side change is a synced action on the machine, not a write here.
    }

    private static void write(RegistryFriendlyByteBuf buffer, @Nullable ResourceAmount amount) {
        ResourceCodecs.AMOUNT_STREAM_OPTIONAL_CODEC.encode(buffer, Optional.ofNullable(amount));
    }

    @Nullable
    private static ResourceAmount read(RegistryFriendlyByteBuf buffer) {
        return ResourceCodecs.AMOUNT_STREAM_OPTIONAL_CODEC.decode(buffer).orElse(null);
    }

    @Nullable
    private static ResourceAmount copy(@Nullable ResourceAmount amount) {
        return amount == null ? null : new ResourceAmount(amount.resource(), amount.amount());
    }

    /**
     * Compares resource and amount rather than trusting {@code equals}.
     *
     * <p>{@link ResourceAmount} is a record and its {@code equals} would do, but the cache exists to
     * decide whether to spend a packet, and getting that wrong in the quiet direction means a slot
     * that never updates on screen. Spelling the comparison out keeps it independent of what
     * Refined Storage decides a {@code ResourceAmount} is.
     */
    private static boolean areEqual(@Nullable ResourceAmount a, @Nullable ResourceAmount b) {
        if (a == null || b == null) {
            return a == b;
        }
        return a.amount() == b.amount() && a.resource().equals(b.resource());
    }
}
