package com.wraithhawit.gttweaks.mixin;

import com.gregtechceu.gtceu.integration.ae2.machine.MEStockingBusPartMachine;
import com.gregtechceu.gtceu.integration.ae2.machine.MEStockingHatchPartMachine;
import com.gregtechceu.gtceu.integration.ae2.machine.feature.multiblock.IMEStockingPart;
import com.gregtechceu.gtceu.integration.ae2.slot.IConfigurableSlot;
import com.gregtechceu.gtceu.integration.ae2.slot.IConfigurableSlotList;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashSet;
import java.util.Set;

/**
 * Stops an ME stocking bus or hatch counting the same resource twice.
 *
 * <p>GregTech 8.0 lets one stocking part hold the same item in two config slots, and its
 * {@code syncME} then mirrors the network's whole amount into both: 320 Inferium in the network
 * showed as 320 in each slot, so a multiblock saw 640. A recipe needing more than the network holds
 * could pass the simulated pass slot by slot and then come up short when it actually ran. GregTech
 * only removes a duplicate that sits in a <em>different</em> part on the same multiblock
 * ({@code IMEStockingPart.validateConfig}).
 *
 * <p>After each sync this keeps the first slot configured with a resource and clears the mirrored
 * stock of every later one, which GregTech's stocking slots already treat as "nothing here" — their
 * {@code extractItem} and {@code drain} both return empty when the stock is null. The duplicate's
 * configuration is left alone, so nothing the player set disappears; it just supplies nothing.
 * gttweaks' Refined Storage stocking parts do the same in their own {@code syncNetwork}.
 */
@Mixin(value = { MEStockingBusPartMachine.class, MEStockingHatchPartMachine.class }, remap = false)
public abstract class MEStockingDuplicateMixin {

    @Inject(method = "syncME", at = @At("RETURN"))
    private void gttweaks$clearDuplicateMirrors(CallbackInfo ci) {
        IConfigurableSlotList slots = ((IMEStockingPart) this).getSlotList();
        Set<AEKey> seen = new HashSet<>();
        for (int i = 0; i < slots.getConfigurableSlots(); i++) {
            IConfigurableSlot slot = slots.getConfigurableSlot(i);
            GenericStack config = slot.getConfig();
            if (config == null) {
                continue;
            }
            if (!seen.add(config.what()) && slot.getStock() != null) {
                slot.setStock(null);
            }
        }
    }
}
