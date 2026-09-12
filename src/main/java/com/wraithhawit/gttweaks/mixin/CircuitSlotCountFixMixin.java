package com.wraithhawit.gttweaks.mixin;

import com.mojang.logging.LogUtils;

import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Fixes "the programmed circuit panel opens, the numbers render, and clicking one does nothing" —
 * on multiblock input buses and on single-block machines alike, because both build the button from
 * {@link com.gregtechceu.gtceu.common.machine.trait.ProgrammableCircuitSlotTrait}.
 *
 * <p>The click reaches the server. What it writes there is an empty stack. The panel's setter is
 * {@code GTMuiWidgets.lambda$createCircuitSlotSyncValue$11}, which builds the new stack as
 * {@code IntCircuitBehaviour.stack(value, currentStack.getCount())} — it preserves the count of the
 * stack already in the slot. The slot starts empty, {@code ItemStack.EMPTY.getCount()} is 0, and
 * {@code stack(value, 0)} is {@code PROGRAMMED_CIRCUIT.asStack(0)}, which {@code isEmpty()}. So the
 * slot is set to nothing, the getter reads back -1, the button redraws unselected, and there is no
 * count from which the next click could recover. The circuit can never be set.
 *
 * <p>The trait's own {@code setCurrentCircuit(int)} does it correctly — {@code stack(value)}, count
 * 1 — but nothing in the UI path calls it. That is why a probe on {@code setCurrentCircuit} logged
 * nothing while the user clicked: it is a dead API in this GTCEu build, not evidence about the
 * click.
 *
 * <p>Clamping the count to at least 1 is the whole fix. A count of 0 is not a state the panel can
 * ever want; a non-empty slot keeps its real count and is untouched.
 */
@Mixin(value = com.gregtechceu.gtceu.common.mui.GTMuiWidgets.class, remap = false)
public class CircuitSlotCountFixMixin {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static boolean gttweaks$loggedOnce = false;

    @ModifyArg(
            method = "lambda$createCircuitSlotSyncValue$11",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/gregtechceu/gtceu/common/item/behavior/IntCircuitBehaviour;"
                            + "stack(II)Lnet/minecraft/world/item/ItemStack;"),
            index = 1)
    private static int gttweaks$circuitCountAtLeastOne(int count) {
        if (count >= 1) {
            return count;
        }
        if (!gttweaks$loggedOnce) {
            gttweaks$loggedOnce = true;
            LOGGER.info("[gttweaks] circuit slot setter was handed count {} - clamping to 1", count);
        }
        return 1;
    }
}
