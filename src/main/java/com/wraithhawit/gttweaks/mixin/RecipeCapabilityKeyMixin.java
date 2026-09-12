package com.wraithhawit.gttweaks.mixin;

import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.wraithhawit.gttweaks.CapabilityKeys;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.serialization.Codec;
import net.minecraft.core.Registry;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Makes GregTech's recipe content maps accept 7.x bare capability keys ({@code "item"} for
 * {@code gtceu:item}); see {@link CapabilityKeys}.
 *
 * <p>{@code RecipeCapability}'s static initializer builds {@code CODEC} and {@code INGREDIENT_CODEC}
 * as {@code DispatchedMapCodec}s keyed by {@code RECIPE_CAPABILITIES.byNameCodec()}. Those are the
 * codecs the recipe loader uses for {@code inputs}, {@code outputs}, {@code tickInputs} and
 * {@code tickOutputs}, so wrapping the key codec here fixes loading for every recipe at once.
 */
@Mixin(value = RecipeCapability.class, remap = false)
public class RecipeCapabilityKeyMixin {

    @WrapOperation(method = "<clinit>", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/core/Registry;byNameCodec()Lcom/mojang/serialization/Codec;"))
    private static Codec<?> gttweaks$acceptBareCapabilityKeys(Registry<?> registry, Operation<Codec<?>> original) {
        Codec<?> named = original.call(registry);
        return registry == GTRegistries.RECIPE_CAPABILITIES ? CapabilityKeys.acceptBareNames(named) : named;
    }
}
