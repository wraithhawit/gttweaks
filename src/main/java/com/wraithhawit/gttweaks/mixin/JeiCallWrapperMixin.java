package com.wraithhawit.gttweaks.mixin;

import com.gregtechceu.gtceu.integration.recipeviewer.jei.GTJEIPlugin;

import mezz.jei.api.gui.drawable.IDrawable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The first domino in GregTech 8.0's failure to load in this pack.
 *
 * <p>{@code CategoryIcon}'s constructor picks a recipe-viewer backend. The EMI
 * path just builds a texture object and is safe at any time. The JEI path is
 * not — it reaches for JEI's <em>runtime</em>:
 *
 * <pre>
 *   GTJEIPlugin.getRuntime().getJeiHelpers().getGuiHelper()...
 * </pre>
 *
 * and that runtime only exists once JEI has started, long after mod loading.
 * On a JEI-only client the call NPEs inside
 * {@code GTRecipeCategories.<clinit>}, which is reached from
 * {@code CommonProxy.onRegister} — the handler where GregTech does all of its
 * content init on the first RegisterEvent, having already set its
 * {@code didRunRegistration} flag. So the throw is doubly destructive: the
 * remainder of that method never runs, including
 * {@code GTDataComponents.DATA_COMPONENTS.register(modBus)}, and the flag stops
 * it ever being retried. {@code gtceu:data_item} is left unbound, building
 * {@code data_stick} then NPEs, NeoForge rolls the registry back to vanilla and
 * marks mod loading broken, and both config-load passes are silently skipped —
 * 27 of 576 configs, after which mods start dying on unloaded configs.
 *
 * <p>Returning null instead of throwing lets that whole chain complete. Null is
 * an already-supported state for this field: the constructor leaves it null on
 * a dedicated server, which is the branch directly above the JEI one.
 *
 * <p>This is deliberately the smaller of two fixes. If JEI turns out to need a
 * non-null icon, the follow-up is to capture the constructor argument and
 * resolve it lazily in {@code CategoryIcon.get()}, which runs during category
 * registration when the runtime does exist. No point writing that until we know
 * it is needed.
 */
@Mixin(targets = "com.gregtechceu.gtceu.integration.recipeviewer.CategoryIcon$JeiCallWrapper", remap = false)
public class JeiCallWrapperMixin {

    @Inject(method = "getRenderable(Lnet/minecraft/resources/ResourceLocation;)Lmezz/jei/api/gui/drawable/IDrawable;",
            at = @At("HEAD"), cancellable = true)
    private static void gttweaks$textureBeforeRuntime(ResourceLocation location,
                                                      CallbackInfoReturnable<IDrawable> cir) {
        if (GTJEIPlugin.getRuntime() == null) {
            cir.setReturnValue(null);
        }
    }

    @Inject(method = "getRenderable(Lnet/minecraft/world/item/ItemStack;)Lmezz/jei/api/gui/drawable/IDrawable;",
            at = @At("HEAD"), cancellable = true)
    private static void gttweaks$stackBeforeRuntime(ItemStack stack,
                                                    CallbackInfoReturnable<IDrawable> cir) {
        if (GTJEIPlugin.getRuntime() == null) {
            cir.setReturnValue(null);
        }
    }
}
