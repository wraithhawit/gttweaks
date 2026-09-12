package com.wraithhawit.gttweaks.mixin;

import brachy.modularui.api.UIType;
import brachy.modularui.screen.ModularScreen;
import com.wraithhawit.gttweaks.EmbeddedScreens;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ModularScreen.class, remap = false)
public abstract class EmbeddedMenuDispatchMixin {
    // mousePressed first closes outside menus, then dispatches through getOpenPanels(). That is
    // a cached snapshot: refresh it so a panel just closed cannot swallow the new button's click.
    @Inject(method = "mousePressed", at = @At(value = "INVOKE",
            target = "Lbrachy/modularui/screen/viewport/ModularGuiContext;onMousePressed(I)Z"))
    private void gttweaks$discardClosedPanels(int button, CallbackInfoReturnable<Boolean> cir) {
        final ModularScreen screen = (ModularScreen) (Object) this;
        if (screen.getContext().getUItype() == UIType.EMBED && "gtceu".equals(screen.getOwner())) {
            EmbeddedScreens.flushPanels(screen);
        }
    }
}
