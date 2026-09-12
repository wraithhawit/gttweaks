package com.wraithhawit.gttweaks.mixin;

import brachy.modularui.integration.emi.recipe.ModularUIEmiRecipe;
import brachy.modularui.screen.ModularScreen;
import com.google.common.cache.LoadingCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = ModularUIEmiRecipe.class, remap = false)
public interface EmiScreenCacheAccessor {
    @Accessor("SCREEN_CACHE")
    static LoadingCache<ModularUIEmiRecipe, ModularScreen> gttweaks$screenCache() {
        throw new AssertionError("Mixin accessor was not applied");
    }
}
