package com.wraithhawit.gttweaks.mixin;

import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.integration.kjs.recipe.components.RecipeCapabilityComponent;
import com.wraithhawit.gttweaks.CapabilityKeys;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.serialization.Codec;
import net.minecraft.core.Registry;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Lets KubeJS read 7.x bare capability keys the same way the recipe loader now does; see
 * {@link CapabilityKeys}.
 *
 * <p>KubeJS parses every GregTech recipe through its own schema before the vanilla loader sees it,
 * and GregTech's {@code RecipeCapabilityComponent.codec()} returns the unwrapped
 * {@code RECIPE_CAPABILITIES.byNameCodec()}. Without this, each such recipe still logs "Failed to
 * read component ... falling back to default value" even though it loads.
 */
@Mixin(value = RecipeCapabilityComponent.class, remap = false)
public class KubeJSCapabilityKeyMixin {

    @WrapOperation(method = "codec", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/core/Registry;byNameCodec()Lcom/mojang/serialization/Codec;"))
    private Codec<?> gttweaks$acceptBareCapabilityKeys(Registry<?> registry, Operation<Codec<?>> original) {
        Codec<?> named = original.call(registry);
        return registry == GTRegistries.RECIPE_CAPABILITIES ? CapabilityKeys.acceptBareNames(named) : named;
    }
}
