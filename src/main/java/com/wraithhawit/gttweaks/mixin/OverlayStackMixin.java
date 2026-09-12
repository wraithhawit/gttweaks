package com.wraithhawit.gttweaks.mixin;

import brachy.modularui.ModularUIConfig;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * ModularUI 3.3.1-SNAPSHOT cannot survive its own first screen.
 *
 * <p>Vanilla opens a screen from inside {@code Minecraft.<init>}, before
 * NeoForge has loaded mod configs. {@code OverlayStack.onOpenScreen} runs on
 * that, and reads config twice: once indirectly, by posting
 * {@code OpenScreenEvent} to ModularUI's own always-registered
 * {@code TestHandler}, and once directly at the end via
 * {@code ModularUIConfig$Dev.debugUI()}. Either throws
 * "Cannot get config value before config is loaded" and the client dies before
 * a title screen. No config file avoids it — the failure is that nothing has
 * loaded yet, whatever the file eventually says.
 *
 * <p>Guarding here rather than at either call site is deliberate. Both reads
 * live downstream of this one method, so a single check covers them, and it
 * keeps working if ModularUI adds a third. Cancelling only while the spec is
 * unloaded means normal behaviour resumes the moment configs land — overlays
 * and the debug overlay work exactly as shipped. The only call this skips is
 * the one during client construction, where there are no overlays registered
 * to open and nothing to close.
 */
@Mixin(targets = "brachy.modularui.overlay.OverlayStack", remap = false)
public class OverlayStackMixin {

    @Inject(method = "onOpenScreen", at = @At("HEAD"), cancellable = true)
    private static void gttweaks$skipBeforeConfigLoad(CallbackInfo ci) {
        if (!ModularUIConfig.CONFIG.isLoaded()) {
            ci.cancel();
        }
    }
}
