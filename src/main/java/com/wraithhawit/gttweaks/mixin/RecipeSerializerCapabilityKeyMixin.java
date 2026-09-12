package com.wraithhawit.gttweaks.mixin;

import com.gregtechceu.gtceu.api.recipe.GTRecipeSerializer;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.wraithhawit.gttweaks.CapabilityKeys;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.serialization.Codec;
import net.minecraft.core.Registry;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Gives the recipe serializer's per-capability chance-logic map the same bare-key tolerance as the
 * content maps; see {@link CapabilityKeys}.
 *
 * <p>{@code GTRecipeSerializer}'s static initializer calls {@code byNameCodec()} on three registries:
 * recipe types, recipe capabilities (the keys of {@code CHANCE_LOGIC_MAP_CODEC}) and chance logics.
 * Only the capability registry is wrapped; the other two pass through unchanged.
 */
@Mixin(value = GTRecipeSerializer.class, remap = false)
public class RecipeSerializerCapabilityKeyMixin {

    @WrapOperation(method = "<clinit>", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/core/Registry;byNameCodec()Lcom/mojang/serialization/Codec;"))
    private static Codec<?> gttweaks$acceptBareCapabilityKeys(Registry<?> registry, Operation<Codec<?>> original) {
        Codec<?> named = original.call(registry);
        return registry == GTRegistries.RECIPE_CAPABILITIES ? CapabilityKeys.acceptBareNames(named) : named;
    }
}
