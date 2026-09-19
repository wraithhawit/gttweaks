package com.wraithhawit.gttweaks.mixin;

import com.gregtechceu.gtceu.api.recipe.ShapedEnergyTransferRecipe;

import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Reads the charge of an energy-transfer recipe's ingredient from a copy of each ingredient stack.
 *
 * <p>{@code ShapedEnergyTransferRecipe.getResultItem} loops over {@code chargeIngredient.getItems()} and
 * calls {@code GTCapabilityHelper.getElectricItem} on those stacks to sum their max charge and charge.
 * Building that capability runs {@code ElectricItem.<init>}, which always calls
 * {@code setMaxChargeOverride} and so writes {@code gtceu:energy_content} onto the stack. The stacks
 * are the ingredient's own. AllTheLeaks 1.1.12 (ATM10 8.1) locks the stacks of interned ingredients
 * and throws {@code ATLUnsupportedOperation} on that write, and Cooking for Blockheads asks every
 * crafting recipe for its result when the server starts, so the dedicated server crashed right after
 * "Done". Only {@code getMaxCharge} and {@code getCharge} are read back, so a copy gives the same sums
 * without touching the ingredient.
 */
@Mixin(value = ShapedEnergyTransferRecipe.class, remap = false)
public class EnergyTransferResultCopyMixin {

    @ModifyArg(
            method = "getResultItem(Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/world/item/ItemStack;",
            at = @At(value = "INVOKE",
                     target = "Lcom/gregtechceu/gtceu/api/capability/GTCapabilityHelper;getElectricItem(Lnet/minecraft/world/item/ItemStack;)Lcom/gregtechceu/gtceu/api/capability/IElectricItem;"))
    private ItemStack gttweaks$readChargeFromCopy(ItemStack ingredientStack) {
        return ingredientStack.copy();
    }
}
