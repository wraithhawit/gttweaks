package com.wraithhawit.gttweaks.mixin;

import com.gregtechceu.gtceu.common.data.item.GTDataComponents;
import com.gregtechceu.gtceu.data.recipe.builder.ShapedEnergyTransferRecipeBuilder;

import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Lets energy-transfer recipes accept their electric input at any charge.
 *
 * <p>{@code ToolRecipeHandler.addElectricToolRecipe} takes a fresh power unit stack, calls
 * {@code GTCapabilityHelper.getElectricItem} on it to read the max charge, and then uses that same stack
 * as the pattern key. Building the capability runs {@code ElectricItem.<init>}, which writes
 * {@code gtceu:energy_content} (charge 0) onto the stack. {@code ShapedEnergyTransferRecipeBuilder.define}
 * turns any stack with a component patch into {@code DataComponentIngredient.of(true, stack)}, a strict
 * match, so the recipe only accepted a completely empty power unit and ignored charged ones - the
 * opposite of what a charge-transfer recipe is for.
 *
 * <p>When energy content is the only data on the key stack, this hands {@code define} a plain stack of
 * the same item, which takes its {@code Ingredient.of(stack)} branch and matches the item at any charge.
 * A key with any other data keeps its strict match. {@code assemble} already reads the charge from the
 * stack actually in the grid, so the transfer itself is unchanged. The builder runs when GTCEu generates
 * its recipes on datapack load.
 */
@Mixin(value = ShapedEnergyTransferRecipeBuilder.class, remap = false)
public class EnergyTransferChargeKeyMixin {

    @ModifyVariable(
            method = "define(CLnet/minecraft/world/item/ItemStack;)Lcom/gregtechceu/gtceu/data/recipe/builder/ShapedEnergyTransferRecipeBuilder;",
            at = @At("HEAD"), argsOnly = true)
    private ItemStack gttweaks$ignoreChargeOnKey(ItemStack stack) {
        final DataComponentPatch patch = stack.getComponentsPatch();
        if (patch.isEmpty()) {
            return stack;
        }
        final DataComponentType<?> energy = GTDataComponents.ENERGY_CONTENT.get();
        if (!patch.forget(type -> type == energy).isEmpty()) {
            return stack;
        }
        return new ItemStack(stack.getItem(), stack.getCount());
    }
}
