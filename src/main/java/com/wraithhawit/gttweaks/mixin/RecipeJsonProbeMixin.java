package com.wraithhawit.gttweaks.mixin;

import com.wraithhawit.gttweaks.RecipeProbe;

import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.crafting.RecipeManager;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

/**
 * First checkpoint of {@link RecipeProbe}: the recipe JSON exactly as datapacks provided it.
 *
 * <p>{@code RecipeManager} inherits {@code prepare} from {@code SimpleJsonResourceReloadListener},
 * which returns every recipe file keyed by id. Its return is the last point before KubeJS,
 * AlmostUnified and NeoForge's condition handling see the recipes in {@code apply}, so a recipe
 * present here and absent from the finished manager was dropped by that pipeline, not missing from
 * the packs. Does nothing unless probe ids are configured.
 */
@Mixin(value = SimpleJsonResourceReloadListener.class, remap = false)
public class RecipeJsonProbeMixin {

    @Inject(method = "prepare(Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)Ljava/util/Map;",
            at = @At("RETURN"))
    private void gttweaks$probeRecipeJson(ResourceManager manager, ProfilerFiller profiler,
                                          CallbackInfoReturnable<Map<ResourceLocation, JsonElement>> cir) {
        if ((Object) this instanceof RecipeManager) {
            RecipeProbe.reportRawJson(cir.getReturnValue());
        }
    }
}
