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

    @Override
    public void detectAndSendChanges(boolean init) {
        boolean anyChanged = init;
        if (!anyChanged) {
            for (int i = 0; i < slotCount; i++) {
                RSConfiguredSlot slot = slotList.getConfigurableSlot(i);
                if (!areEqual(slot.getConfig(), cachedConfig[i])
                        || !areEqual(slot.getStock(), cachedStock[i])) {
                    anyChanged = true;
                    break;
                }
            }
        }
        if (!anyChanged) {
            return;
        }
        syncToClient(SYNC_SLOTS, buffer -> {
            for (int i = 0; i < slotCount; i++) {
                RSConfiguredSlot slot = slotList.getConfigurableSlot(i);
                ResourceAmount config = slot.getConfig();
                ResourceAmount stock = slot.getStock();
                boolean changed = init
                        || !areEqual(config, cachedConfig[i])
                        || !areEqual(stock, cachedStock[i]);
                buffer.writeBoolean(changed);
                if (!changed) {
                    continue;
                }
                write(buffer, config);
                write(buffer, stock);
                cachedConfig[i] = copy(config);
                cachedStock[i] = copy(stock);
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
