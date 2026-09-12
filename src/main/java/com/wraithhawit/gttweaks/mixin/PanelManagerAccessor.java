package com.wraithhawit.gttweaks.mixin;

import brachy.modularui.screen.PanelManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = PanelManager.class, remap = false)
public interface PanelManagerAccessor {
    @Invoker("checkDirty")
    void gttweaks$flushPanels();
}
