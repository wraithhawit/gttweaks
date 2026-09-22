package com.wraithhawit.gttweaks.mixin;

import com.gregtechceu.gtceu.common.data.item.GTDataComponents;
import com.gregtechceu.gtceu.common.item.datacomponents.DataItem;
import com.gregtechceu.gtceu.utils.ResearchManager;

import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lets a data access hatch read data sticks and data orbs again.
 *
 * <p>{@code ResearchManager.isStackDataItem(stack, isDataBank)} decides whether a hatch may use a
 * research item. GregTech 7.x (ATM9's 7.2.0, read off its bytecode) answers
 * {@code !requireDataBank() || isDataBank}: sticks and orbs work in any data access hatch, and only
 * an item that requires a data bank is refused outside one. The 8.0 snapshot's bytecode answers
 * {@code requireDataBank() || isDataBank} — one branch flipped — so in a plain data access hatch on
 * an assembly line every stick and orb is ignored, and only the items that were supposed to need a
 * data bank count. {@code DataAccessHatchMachine.rebuildData} then finds nothing, and every
 * research-gated assembly line recipe stays locked however correct the stick is.
 *
 * <p>This restores 7.x's rule, with its handling of a stack that is not a data item at all: false,
 * where 8.0 returns {@code isDataBank}. The scanner's stick-copy logic is the only other caller and
 * always passes {@code true}, so for real data items it is unaffected.
 */
@Mixin(value = ResearchManager.class, remap = false)
public class DataItemBankCheckMixin {

    @Inject(method = "isStackDataItem", at = @At("HEAD"), cancellable = true)
    private static void gttweaks$sevenXDataItemRule(ItemStack stack, boolean isDataBank,
                                                    CallbackInfoReturnable<Boolean> cir) {
        DataItem dataItem = stack.get(GTDataComponents.DATA_ITEM);
        cir.setReturnValue(dataItem != null && (!dataItem.requireDataBank() || isDataBank));
    }
}
