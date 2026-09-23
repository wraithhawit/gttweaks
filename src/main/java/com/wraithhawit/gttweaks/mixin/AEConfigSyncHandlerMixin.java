package com.wraithhawit.gttweaks.mixin;

import com.gregtechceu.gtceu.integration.ae2.gui.AEConfigSyncHandler;
import com.gregtechceu.gtceu.integration.ae2.slot.IConfigurableSlot;
import com.gregtechceu.gtceu.integration.ae2.slot.IConfigurableSlotList;

import appeng.api.stacks.GenericStack;

import brachy.modularui.value.sync.SyncHandler;

import net.minecraft.network.RegistryFriendlyByteBuf;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Makes an ME bus or hatch's config screen update while it is open, not only when it is reopened.
 *
 * <p>{@code AEConfigSyncHandler.detectAndSendChanges} decides which slots changed, and updates its
 * cache of what the client was told, inside the writer it hands to {@code syncToClient}. ModularUI
 * runs that writer when the packet is encoded, on a network thread, and can run it more than once
 * for one packet: in single player every writer ran twice. The first run wrote the change and
 * updated the cache; the second found nothing different from the cache and wrote "no slot changed",
 * and that is what the client received. The forced full sync on opening ignores the cache, which is
 * why reopening the screen was the only way to see a change. Confirmed by tracing the identical
 * handler in gttweaks' Refined Storage parts, {@code RSConfigSyncHandler}, which had copied the
 * shape and has the same fix.
 *
 * <p>This does the comparison and the cache update on the server thread and hands the packet a
 * writer that only replays that snapshot, so a second run writes the same bytes again. The wire
 * format is GregTech's, unchanged, so {@code readOnClient} is left alone.
 */
@Mixin(value = AEConfigSyncHandler.class, remap = false)
public abstract class AEConfigSyncHandlerMixin extends SyncHandler<AEConfigSyncHandler> {

    @Shadow
    @Final
    private static int SYNC_SLOTS;

    @Shadow
    @Final
    private IConfigurableSlotList slotList;

    @Shadow
    @Final
    private int slotCount;

    @Shadow
    @Final
    private GenericStack[] cachedConfig;

    @Shadow
    @Final
    private GenericStack[] cachedStock;

    @Shadow
    private static GenericStack copy(GenericStack stack) {
        throw new AssertionError();
    }

    @Shadow
    private static void writeStack(RegistryFriendlyByteBuf buffer, GenericStack stack) {
        throw new AssertionError();
    }

    @Shadow
    private static boolean areEqual(GenericStack a, GenericStack b) {
        throw new AssertionError();
    }

    @Inject(method = "detectAndSendChanges", at = @At("HEAD"), cancellable = true)
    private void gttweaks$snapshotBeforeSending(boolean init, CallbackInfo ci) {
        ci.cancel();
        boolean[] changed = new boolean[slotCount];
        GenericStack[] configs = new GenericStack[slotCount];
        GenericStack[] stocks = new GenericStack[slotCount];
        boolean anyChanged = false;
        for (int i = 0; i < slotCount; i++) {
            IConfigurableSlot slot = slotList.getConfigurableSlot(i);
            GenericStack config = slot.getConfig();
            GenericStack stock = slot.getStock();
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
                    writeStack(buffer, configs[i]);
                    writeStack(buffer, stocks[i]);
                }
            }
        });
    }
}
